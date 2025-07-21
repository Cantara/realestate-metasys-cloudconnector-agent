package no.cantara.realestate.metasys.cloudconnector.automationserver;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Simplified mock server using Java's built-in HTTP server
 */
public class SimpleMetasysApiFailureSimulator implements AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(SimpleMetasysApiFailureSimulator.class);
    private HttpServer server;
    private volatile boolean apiDown = false;
    private Instant apiDownSince;
    private final int port;

    public SimpleMetasysApiFailureSimulator(int port) {
        this.port = port;
    }

    public void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(port), 0);

        // Login endpoint
        server.createContext("/api/v4/login", new LoginHandler());

        // Refresh token endpoint
        server.createContext("/api/v4/refreshToken", new RefreshTokenHandler());

        server.setExecutor(null);
        server.start();
        log.info("SimpleMetasysApiFailureSimulator started on port {}", port);
    }

    public void simulateApiDown(Duration downtime) {
        apiDown = true;
        apiDownSince = Instant.now();
        log.warn("Simulating Metasys API down for {} seconds", downtime.getSeconds());

        // Schedule API recovery
        CompletableFuture.delayedExecutor(downtime.getSeconds(), TimeUnit.SECONDS)
                .execute(() -> {
                    apiDown = false;
                    log.info("Metasys API simulator recovered after {} seconds", downtime.getSeconds());
                });
    }

    private class LoginHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (apiDown) {
                // Return 502 Bad Gateway
                exchange.sendResponseHeaders(502, 0);
                exchange.close();
                return;
            }

            // Normal response
            String response = "{\n" +
                    "  \"accessToken\": \"eyJ0eXAiOiJKV1QiLCJhbGciOiJSUzI1NiJ9...\",\n" +
                    "  \"expires\": \"" + Instant.now().plus(Duration.ofHours(2)) + "\"\n" +
                    "}";

            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length());
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response.getBytes());
            }
        }
    }

    private class RefreshTokenHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (apiDown) {
                // Return 502 Bad Gateway
                exchange.sendResponseHeaders(502, 0);
                exchange.close();
                return;
            }

            // Normal response
            String response = "{\n" +
                    "  \"accessToken\": \"eyJ0eXAiOiJKV1QiLCJhbGciOiJSUzI1NiJ9...\",\n" +
                    "  \"expires\": \"" + Instant.now().plus(Duration.ofHours(2)) + "\"\n" +
                    "}";

            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length());
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response.getBytes());
            }
        }
    }

    @Override
    public void close() {
        if (server != null) {
            server.stop(1);
            log.info("SimpleMetasysApiFailureSimulator stopped");
        }
    }

    public boolean isApiDown() {
        return apiDown;
    }

    public Duration getDowntime() {
        return apiDown ? Duration.between(apiDownSince, Instant.now()) : Duration.ZERO;
    }
}