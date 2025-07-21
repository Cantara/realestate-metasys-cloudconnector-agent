package no.cantara.realestate.metasys.cloudconnector.automationserver;

import org.mockserver.integration.ClientAndServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;

import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.JsonBody.json;

/**
 * Mock server som simulerer Metasys API med 502 feil i perioder
 */
public class MetasysApiLoginSimulator implements AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(MetasysApiLoginSimulator.class);
    protected static final String EXPECTED_ACCESS_TOKEN = "eyJ0eXAiOiJKV1QiLCJhbGciOiJSUzI1NiJ9...";
    private final ClientAndServer mockServer;
    private final int port;

    public MetasysApiLoginSimulator(int port) {
        this.port = port;
        this.mockServer = ClientAndServer.startClientAndServer(port);
        setupLoginEndpoints();
    }

    /**
     * Konstruktør som automatisk finner en ledig port
     */
    public MetasysApiLoginSimulator() {
        // La MockServer finne en ledig port automatisk
        this.mockServer = ClientAndServer.startClientAndServer();
        this.port = mockServer.getLocalPort();
        setupLoginEndpoints();
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

    /**
     * Returnerer porten som MockServer bruker
     */
    public int getPort() {
        return port;
    }


    private void setupLoginEndpoints() {
        // Clear existing expectations
        mockServer.reset();

        // Normal login endpoint
        mockServer
                .when(
                        request()
                                .withMethod("POST")
                                .withPath("/api/v4/login")
                                .withHeader("Content-Type", "application/json")
                                .withBody(json("{\"username\":\"testuser\",\"password\":\"testpass\" }"))
                )
                .respond(
                        response()
                                .withStatusCode(200)
                                .withHeader("Content-Type", "application/json")
                                .withBody("{\n" +
                                        "  \"accessToken\": \"" + EXPECTED_ACCESS_TOKEN + "\",\n" +
                                        "  \"expires\": \"" + Instant.now().plus(Duration.ofSeconds(20)) + "\"\n" +
                                        "}")
                );
        // Catch-all for andre login forsøk - returnerer 403 Forbidden
        mockServer
                .when(
                        request()
                                .withMethod("POST")
                                .withPath("/api/v4/login")
                                .withHeader("Content-Type", "application/json")
                )
                .respond(
                        response()
                                .withStatusCode(403)
                                .withHeader("Content-Type", "application/json")
                                .withBody("{\n" +
                                        "  \"error\": \"Forbidden\",\n" +
                                        "  \"message\": \"Access denied\"\n" +
                                        "}")
                );

        // Normal refresh token endpoint
        mockServer
                .when(
                        request()
                                .withMethod("GET")
                                .withPath("/api/v4/refreshToken")
                                .withHeader("Authorization", "Bearer " + EXPECTED_ACCESS_TOKEN)
                )
                .respond(
                        response()
                                .withStatusCode(200)
                                .withHeader("Content-Type", "application/json")
                                .withBody("{\n" +
                                        "  \"accessToken\": \"" + EXPECTED_ACCESS_TOKEN + "\",\n" +
                                        "  \"expires\": \"" + Instant.now().plus(Duration.ofMinutes(30)) + "\"\n" +
                                        "}")
                );
        // Catch-all for andre refresh forsøk - returnerer 403 Forbidden
        mockServer
                .when(
                        request()
                                .withMethod("GET")
                                .withPath("/api/v4/refreshToken")
                                .withHeader("Content-Type", "application/json")
                )
                .respond(
                        response()
                                .withStatusCode(403)
                                .withHeader("Content-Type", "application/json")
                                .withBody("{\n" +
                                        "  \"error\": \"Forbidden\",\n" +
                                        "  \"message\": \"Wrong Access Token\"\n" +
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
                                .withHeader("Content-Type", "application/json")
                                .withBody("{ \"username\": \"testuser\", \"password\": \"testpass\" }")
                )
                .respond(
                        response("{\"accessToken\":\"new_token\",\"expires\":\"2023-12-31T23:59:59Z\"}")
                                .withStatusCode(200)
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


    public boolean isRunning() {
        return mockServer != null && mockServer.isRunning();
    }

    public ClientAndServer getMockServer() {
        return mockServer;
    }
}