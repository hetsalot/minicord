package com.minicord.lb;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * Reverse proxy HTTP handler routing requests to backend nodes.
 * DC Topic 7: Load Balancer
 */
public class ProxyHandler implements HttpHandler {
    private static final Gson gson = new Gson();

    private final List<BackendNode> nodes;
    private final AtomicReference<LoadBalancerAlgorithm> algorithmRef;
    private final HttpClient httpClient;

    public ProxyHandler(List<BackendNode> nodes, LoadBalancerAlgorithm initialAlgorithm) {
        this.nodes = nodes;
        this.algorithmRef = new AtomicReference<>(initialAlgorithm);
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build();
    }

    public void setAlgorithm(String algoName) {
        switch (algoName.toUpperCase()) {
            case "LEAST_CONNECTIONS":
                algorithmRef.set(new LeastConnections());
                break;
            case "WEIGHTED_ROUND_ROBIN":
                algorithmRef.set(new WeightedRoundRobin());
                break;
            case "ROUND_ROBIN":
            default:
                algorithmRef.set(new RoundRobin());
                break;
        }
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String method = exchange.getRequestMethod();

        // Handle CORS preflight
        if ("OPTIONS".equalsIgnoreCase(method)) {
            sendCorsHeaders(exchange);
            exchange.sendResponseHeaders(204, -1);
            return;
        }

        // 1. Check if this is the LB stats endpoint: GET /api/admin/lb/stats
        if ("/api/admin/lb/stats".equals(path) && "GET".equalsIgnoreCase(method)) {
            handleStats(exchange);
            return;
        }

        // 2. Check if this is the LB switch algorithm endpoint: POST /api/admin/lb/algorithm
        if ("/api/admin/lb/algorithm".equals(path) && "POST".equalsIgnoreCase(method)) {
            handleSwitchAlgorithm(exchange);
            return;
        }

        // 3. Otherwise, forward request to backend node
        forwardRequest(exchange);
    }

    private void handleStats(HttpExchange exchange) throws IOException {
        Map<String, Object> data = new HashMap<>();
        data.put("algorithm", algorithmRef.get().getName());

        List<Map<String, Object>> serverStats = new ArrayList<>();
        int totalActiveConn = 0;
        double totalRps = 0;

        for (BackendNode n : nodes) {
            Map<String, Object> s = new HashMap<>();
            s.put("id", n.getId());
            s.put("status", n.getStatus());
            s.put("activeConnections", n.getActiveConnections());
            s.put("totalRequests", n.getTotalRequests());
            s.put("requestsPerSecond", Math.round(n.getTotalRequests() * 0.1 * 10.0) / 10.0);
            s.put("avgResponseTimeMs", n.getAvgResponseTimeMs());
            s.put("healthCheckStatus", n.getHealthCheckStatus());
            s.put("failedChecks", n.getFailedChecks());
            s.put("lastHealthCheck", n.getLastHealthCheck());
            serverStats.add(s);

            totalActiveConn += n.getActiveConnections();
            totalRps += (Double) s.get("requestsPerSecond");
        }

        data.put("servers", serverStats);
        data.put("totalRequestsPerSecond", Math.round(totalRps * 10.0) / 10.0);
        data.put("totalActiveConnections", totalActiveConn);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("data", data);
        response.put("error", null);

        sendJsonResponse(exchange, 200, gson.toJson(response));
    }

    private void handleSwitchAlgorithm(HttpExchange exchange) throws IOException {
        byte[] bodyBytes = readBody(exchange.getRequestBody());
        String bodyStr = new String(bodyBytes, StandardCharsets.UTF_8);
        try {
            JsonObject json = JsonParser.parseString(bodyStr).getAsJsonObject();
            String algo = json.get("algorithm").getAsString();
            setAlgorithm(algo);

            Map<String, Object> res = Map.of(
                    "success", true,
                    "data", Map.of("algorithm", algorithmRef.get().getName()),
                    "error", null
            );
            sendJsonResponse(exchange, 200, gson.toJson(res));
        } catch (Exception e) {
            Map<String, Object> res = Map.of(
                    "success", false,
                    "data", null,
                    "error", Map.of("code", "BAD_REQUEST", "message", e.getMessage())
            );
            sendJsonResponse(exchange, 400, gson.toJson(res));
        }
    }

    private void forwardRequest(HttpExchange exchange) throws IOException {
        List<BackendNode> healthyNodes = nodes.stream()
                .filter(BackendNode::isHealthy)
                .collect(Collectors.toList());

        // If no nodes marked healthy, try any node that is not permanently down
        if (healthyNodes.isEmpty()) {
            healthyNodes = nodes.stream()
                    .filter(n -> "ACTIVE".equals(n.getStatus()))
                    .collect(Collectors.toList());
        }

        // If still empty, fall back to all nodes (best effort)
        if (healthyNodes.isEmpty()) {
            healthyNodes = new ArrayList<>(nodes);
        }

        BackendNode targetNode = algorithmRef.get().selectNode(healthyNodes);
        if (targetNode == null) {
            sendJsonResponse(exchange, 503, "{\"success\":false,\"error\":\"No backend available\"}");
            return;
        }

        targetNode.incrementConnections();
        long start = System.currentTimeMillis();

        try {
            URI requestUri = exchange.getRequestURI();
            String targetUrl = targetNode.getBaseUrl() + requestUri.getRawPath()
                    + (requestUri.getRawQuery() != null ? "?" + requestUri.getRawQuery() : "");

            byte[] requestBody = readBody(exchange.getRequestBody());

            HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(targetUrl))
                    .timeout(Duration.ofSeconds(10));

            // Copy request headers
            exchange.getRequestHeaders().forEach((name, values) -> {
                if (!name.equalsIgnoreCase("Host") && !name.equalsIgnoreCase("Content-Length")) {
                    for (String val : values) {
                        try {
                            reqBuilder.header(name, val);
                        } catch (Exception ignored) {}
                    }
                }
            });

            String method = exchange.getRequestMethod();
            if ("POST".equalsIgnoreCase(method) || "PUT".equalsIgnoreCase(method) || "PATCH".equalsIgnoreCase(method)) {
                reqBuilder.method(method, HttpRequest.BodyPublishers.ofByteArray(requestBody));
            } else if ("DELETE".equalsIgnoreCase(method)) {
                reqBuilder.DELETE();
            } else {
                reqBuilder.GET();
            }

            HttpResponse<byte[]> response = httpClient.send(reqBuilder.build(), HttpResponse.BodyHandlers.ofByteArray());

            // Forward response
            sendCorsHeaders(exchange);
            response.headers().map().forEach((k, vals) -> {
                if (!k.equalsIgnoreCase("Transfer-Encoding") && !k.equalsIgnoreCase("Content-Length")) {
                    for (String v : vals) {
                        exchange.getResponseHeaders().add(k, v);
                    }
                }
            });

            byte[] respBytes = response.body();
            exchange.sendResponseHeaders(response.statusCode(), respBytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(respBytes);
            }
        } catch (Exception e) {
            sendCorsHeaders(exchange);
            byte[] errBytes = ("{\"success\":false,\"error\":{\"message\":\"" + e.getMessage() + "\"}}").getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(502, errBytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(errBytes);
            }
        } finally {
            targetNode.decrementConnections(System.currentTimeMillis() - start);
        }
    }

    private void sendCorsHeaders(HttpExchange exchange) {
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, Authorization");
    }

    private void sendJsonResponse(HttpExchange exchange, int status, String json) throws IOException {
        sendCorsHeaders(exchange);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private byte[] readBody(InputStream is) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] data = new byte[4096];
        int nRead;
        while ((nRead = is.read(data, 0, data.length)) != -1) {
            buffer.write(data, 0, nRead);
        }
        return buffer.toByteArray();
    }
}
