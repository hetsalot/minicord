package com.minicord.lb;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;

/**
 * Custom Java Load Balancer server for MiniCord.
 * DC Topic 7: Load Balancer
 */
public class LoadBalancerServer {

    public static void main(String[] args) throws IOException {
        int port = Integer.parseInt(System.getenv().getOrDefault("LB_PORT", "8080"));
        String backendsConfig = System.getenv().getOrDefault("BACKENDS",
                "1=localhost:9001:3,2=localhost:9002:2,3=localhost:9003:1");

        List<BackendNode> nodes = new ArrayList<>();
        for (String part : backendsConfig.split(",")) {
            String[] tokens = part.trim().split("=");
            if (tokens.length == 2) {
                int id = Integer.parseInt(tokens[0]);
                String[] hostPortWeight = tokens[1].split(":");
                String host = hostPortWeight[0];
                int bPort = Integer.parseInt(hostPortWeight[1]);
                int weight = hostPortWeight.length >= 3 ? Integer.parseInt(hostPortWeight[2]) : 1;
                nodes.add(new BackendNode(id, "minicord-server-" + id, host, bPort, weight));
            }
        }

        if (nodes.isEmpty()) {
            nodes.add(new BackendNode(1, "minicord-server-1", "localhost", 9001, 3));
            nodes.add(new BackendNode(2, "minicord-server-2", "localhost", 9002, 2));
            nodes.add(new BackendNode(3, "minicord-server-3", "localhost", 9003, 1));
        }

        System.out.println("╔══════════════════════════════════════════════════╗");
        System.out.println("║          MiniCord Custom Load Balancer           ║");
        System.out.println("║  Port:           " + port + "                            ║");
        System.out.println("║  Default Algo:   ROUND_ROBIN                     ║");
        System.out.println("║  Backend Nodes:  " + nodes.size() + "                               ║");
        for (BackendNode n : nodes) {
            System.out.println("║    • Node " + n.getId() + ": " + n.getBaseUrl() + " (weight " + n.getWeight() + ")    ║");
        }
        System.out.println("╚══════════════════════════════════════════════════╝");

        // Start health checker in background
        HealthChecker healthChecker = new HealthChecker(nodes);
        Thread hcThread = new Thread(healthChecker, "lb-health-checker");
        hcThread.setDaemon(true);
        hcThread.start();

        // Create HTTP Server
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        ProxyHandler proxyHandler = new ProxyHandler(nodes, new RoundRobin());
        server.createContext("/", proxyHandler);
        server.setExecutor(Executors.newFixedThreadPool(20));

        server.start();
        System.out.println("Load Balancer is running on http://0.0.0.0:" + port);
    }
}
