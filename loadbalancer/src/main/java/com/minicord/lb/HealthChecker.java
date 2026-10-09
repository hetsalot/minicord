package com.minicord.lb;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Background health check worker probing /health on all backend nodes.
 * DC Topic 7: Load Balancer (Health Checks)
 */
public class HealthChecker implements Runnable {
    private final List<BackendNode> nodes;
    private final HttpClient httpClient;
    private volatile boolean running = true;

    public HealthChecker(List<BackendNode> nodes) {
        this.nodes = nodes;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .build();
    }

    public void stop() {
        running = false;
    }

    @Override
    public void run() {
        while (running) {
            try {
                for (BackendNode node : nodes) {
                    checkNode(node);
                }
                Thread.sleep(3000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception ignored) {}
        }
    }

    private void checkNode(BackendNode node) {
        node.setLastHealthCheck(Instant.now().toString());
        String url = node.getBaseUrl() + "/health";
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(2))
                    .GET()
                    .build();

            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200) {
                node.setFailedChecks(0);
                node.setStatus("ACTIVE");
                node.setHealthCheckStatus("HEALTHY");
            } else {
                handleFailure(node);
            }
        } catch (Exception e) {
            handleFailure(node);
        }
    }

    private void handleFailure(BackendNode node) {
        int failed = node.getFailedChecks() + 1;
        node.setFailedChecks(failed);
        // After 3 failed checks, mark as DOWN / UNHEALTHY
        if (failed >= 3) {
            node.setStatus("DOWN");
            node.setHealthCheckStatus("UNHEALTHY");
        }
    }
}
