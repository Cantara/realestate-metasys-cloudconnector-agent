package no.cantara.realestate.metasys.cloudconnector.automationserver;

import org.mockserver.integration.ClientAndServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

/**
 * Mock server som simulerer Metasys API med 502 feil i perioder
 */
public class MetasysApiFailureSimulator implements AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(MetasysApiFailureSimulator.class);
    private final ClientAndServer mockServer;
    private final int port;
    private boolean apiDown = false;
    private Instant apiDownSince;

    public MetasysApiFailureSimulator(int port) {
        this.port = port;
        this.mockServer = ClientAndServer.startClientAndServer(port);
        setupMockEndpoints();
    }

    public void start() {
        // MockServer startes automatisk i konstruktøren
        log.info("MetasysApiFailureSimulator started on port {}", port);
    }

    public void stop() {
        if (mockServer != null && mockServer.isRunning()) {
            mockServer.stop();
            log.info("MetasysApiFailureSimulator stopped");
        }
    }

    @Override
    public void close() {
        stop();
    }

    public void simulateApiDown(Duration downtime) {
        apiDown = true;
        apiDownSince = Instant.now();
        log.warn("Simulating Metasys API down for {} seconds", downtime.getSeconds());

        // Oppdater mock responses til 502
        setupMockEndpointsWithFailure();

        // Schedule API recovery
        CompletableFuture.delayedExecutor(downtime.getSeconds(), TimeUnit.SECONDS)
                .execute(() -> {
                    apiDown = false;
                    log.info("Metasys API simulator recovered after {} seconds", downtime.getSeconds());
                    // Reset til normale responses
                    setupMockEndpoints();
                });
    }

    private void setupMockEndpoints() {
        // Clear existing expectations
        mockServer.reset();

        // Normal login endpoint
        mockServer
                .when(
                        request()
                                .withMethod("POST")
                                .withPath("/api/v4/login")
                )
                .respond(
                        response()
                                .withStatusCode(200)
                                .withHeader("Content-Type", "application/json")
                                .withBody("{\n" +
                                        "  \"accessToken\": \"eyJ0eXAiOiJKV1QiLCJhbGciOiJSUzI1NiJ9...\",\n" +
                                        "  \"expires\": \"" + Instant.now().plus(Duration.ofSeconds(20)) + "\"\n" +
                                        "}")
                );

        // Normal refresh token endpoint
        mockServer
                .when(
                        request()
                                .withMethod("GET")
                                .withPath("/api/v4/refreshToken")
                )
                .respond(
                        response()
                                .withStatusCode(200)
                                .withHeader("Content-Type", "application/json")
                                .withBody("{\n" +
                                        "  \"accessToken\": \"eyJ0eXAiOiJKV1QiLCJhbGciOiJSUzI1NiJ9...\",\n" +
                                        "  \"expires\": \"" + Instant.now().plus(Duration.ofHours(2)) + "\"\n" +
                                        "}")
                );
    }

    private void setupMockEndpointsWithFailure() {
        // Clear existing expectations
        mockServer.reset();

        // 502 Bad Gateway for login
        mockServer
                .when(
                        request()
                                .withMethod("POST")
                                .withPath("/api/v4/login")
                )
                .respond(
                        response()
                                .withStatusCode(502)
                );

        // 502 Bad Gateway for refresh token
        mockServer
                .when(
                        request()
                                .withMethod("GET")
                                .withPath("/api/v4/refreshToken")
                )
                .respond(
                        response()
                                .withStatusCode(502)
                );
    }

    public boolean isApiDown() {
        return apiDown;
    }

    public Duration getDowntime() {
        return apiDown ? Duration.between(apiDownSince, Instant.now()) : Duration.ZERO;
    }

    public boolean isRunning() {
        return mockServer != null && mockServer.isRunning();
    }

    public ClientAndServer getMockServer() {
        return mockServer;
    }
}