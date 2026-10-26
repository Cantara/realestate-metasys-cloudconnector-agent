package no.cantara.realestate.metasys.cloudconnector.automationserver;

import org.mockserver.integration.ClientAndServer;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;

import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

/**
 * Mock server som simulerer en Metasys-server der sesjonen bak tokenet dør,
 * mens {@code /refreshToken} fortsatt svarer 200.
 *
 * Scenario:
 * <ol>
 *   <li>{@code POST /api/v4/login} svarer 200 og starter en ny sesjon.</li>
 *   <li>{@code GET /api/v4/refreshToken} svarer alltid 200 med nytt token.</li>
 *   <li>{@code GET /api/v4/objects/{id}/trendedAttributes/presentValue/samples} svarer 200
 *       så lenge sesjonen lever (default 5 minutter etter siste login), deretter 403.</li>
 * </ol>
 *
 * Hypotesen som skal verifiseres: MetasysClient vil etter sesjonsutløp gjøre refresh (som lykkes)
 * og få 403 på nytt, uten noen gang å gjøre full login.
 *
 * Kjør manuelt mot agenten:
 * <pre>
 *   java -cp ... MetasysApiDeadSessionSimulator [port] [sesjonslevetidSekunder]
 *   sd.api.url=http://localhost:1080/api/v4/
 * </pre>
 */
public class MetasysApiDeadSessionSimulator implements AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(MetasysApiDeadSessionSimulator.class);
    public static final String METASYS_OBJECT_ID = "05ccd193-a3f9-5db7-9c72-61987ca3d8dd";
    public static final Duration DEFAULT_SESSION_LIFETIME = Duration.ofMinutes(5);
    private static final Duration TOKEN_VALIDITY = Duration.ofMinutes(30);
    private static final int DEFAULT_PORT = 1080;

    private final ClientAndServer mockServer;
    private final int port;
    private final Duration sessionLifetime;
    private volatile Instant sessionStartedAt = null;
    private int tokenCounter = 0;

    public MetasysApiDeadSessionSimulator(int port, Duration sessionLifetime) {
        this.port = port;
        this.sessionLifetime = sessionLifetime;
        this.mockServer = ClientAndServer.startClientAndServer(port);
        setupEndpoints();
    }

    public MetasysApiDeadSessionSimulator(Duration sessionLifetime) {
        this.sessionLifetime = sessionLifetime;
        this.mockServer = ClientAndServer.startClientAndServer();
        this.port = mockServer.getLocalPort();
        setupEndpoints();
    }

    public MetasysApiDeadSessionSimulator() {
        this(DEFAULT_SESSION_LIFETIME);
    }

    public void start() {
        log.info("MetasysApiDeadSessionSimulator started on port {}. Session lifetime: {}", port, sessionLifetime);
    }

    public void stop() {
        if (mockServer != null && mockServer.isRunning()) {
            mockServer.stop();
            log.info("MetasysApiDeadSessionSimulator stopped");
        }
    }

    @Override
    public void close() {
        stop();
    }

    public int getPort() {
        return port;
    }

    public boolean isSessionAlive() {
        return sessionStartedAt != null && Instant.now().isBefore(sessionStartedAt.plus(sessionLifetime));
    }

    public Instant getSessionStartedAt() {
        return sessionStartedAt;
    }

    private void setupEndpoints() {
        mockServer.reset();

        // 1. Login: alltid OK, starter ny sesjon
        mockServer
                .when(request().withMethod("POST").withPath("/api/v4/login"))
                .respond(this::handleLogin);

        // 2. Refresh: alltid OK, rører ikke sesjonen
        mockServer
                .when(request().withMethod("GET").withPath("/api/v4/refreshToken"))
                .respond(this::handleRefresh);

        // 3./4. Trend samples: 200 mens sesjonen lever, 403 etterpå
        mockServer
                .when(request().withMethod("GET").withPath("/api/v4/objects/.*/trendedAttributes/presentValue/samples"))
                .respond(this::handleSamples);
    }

    private HttpResponse handleLogin(HttpRequest httpRequest) {
        sessionStartedAt = Instant.now();
        String token = nextToken();
        log.info("LOGIN ok. New session started at {}. Session dies at {}. Token: {}",
                sessionStartedAt, sessionStartedAt.plus(sessionLifetime), token);
        return tokenResponse(token);
    }

    private HttpResponse handleRefresh(HttpRequest httpRequest) {
        String token = nextToken();
        log.info("REFRESH ok. Session alive: {}. Token: {}", isSessionAlive(), token);
        return tokenResponse(token);
    }

    private HttpResponse handleSamples(HttpRequest httpRequest) {
        if (isSessionAlive()) {
            log.info("SAMPLES 200 for {}", httpRequest.getPath());
            return response()
                    .withStatusCode(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(TREND_SAMPLES_JSON);
        }
        log.warn("SAMPLES 403 for {}. Session started at {}, died at {}",
                httpRequest.getPath(), sessionStartedAt,
                sessionStartedAt == null ? null : sessionStartedAt.plus(sessionLifetime));
        return response()
                .withStatusCode(403)
                .withHeader("Content-Type", "application/json")
                .withBody("{\n" +
                        "  \"error\": \"Forbidden\",\n" +
                        "  \"message\": \"Access denied\"\n" +
                        "}");
    }

    private synchronized String nextToken() {
        tokenCounter++;
        return "session-token-" + tokenCounter;
    }

    private HttpResponse tokenResponse(String token) {
        return response()
                .withStatusCode(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\n" +
                        "  \"accessToken\": \"" + token + "\",\n" +
                        "  \"expires\": \"" + Instant.now().plus(TOKEN_VALIDITY) + "\"\n" +
                        "}");
    }

    private static final String TREND_SAMPLES_JSON = """
            {
              "total": 2,
              "items": [
                {
                  "value": {
                    "value": 9398.001,
                    "units": "https://metasysserver/api/v4/enumSets/507/members/19"
                  },
                  "timestamp": "2020-09-16T05:20:00Z",
                  "isReliable": true
                },
                {
                  "value": {
                    "value": 9400.001,
                    "units": "https://metasysserver/api/v4/enumSets/507/members/19"
                  },
                  "timestamp": "2020-09-16T05:21:00Z",
                  "isReliable": true
                }
              ],
              "next": null,
              "attribute": "attributeEnumSet.presentValue",
              "previous": null,
              "self": "http://localhost:1080/api/v4/objects/05ccd193-a3f9-5db7-9c72-61987ca3d8dd/trendedAttributes/presentValue/samples",
              "objectUrl": "http://localhost:1080/api/v4/objects/05ccd193-a3f9-5db7-9c72-61987ca3d8dd"
            }
            """;

    public boolean isRunning() {
        return mockServer != null && mockServer.isRunning();
    }

    public ClientAndServer getMockServer() {
        return mockServer;
    }

    /**
     * Starter simulatoren frittstående, slik at agenten kan kjøres mot den.
     * Argumenter: [port] [sesjonslevetid i sekunder]. Default: 1080 og 300 (5 minutter).
     */
    public static void main(String[] args) throws InterruptedException {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : DEFAULT_PORT;
        Duration lifetime = args.length > 1 ? Duration.ofSeconds(Long.parseLong(args[1])) : DEFAULT_SESSION_LIFETIME;
        MetasysApiDeadSessionSimulator simulator = new MetasysApiDeadSessionSimulator(port, lifetime);
        simulator.start();
        log.info("Point the agent at sd.api.url=http://localhost:{}/api/v4/ and watch for 403 after {}", port, lifetime);
        Runtime.getRuntime().addShutdownHook(new Thread(simulator::stop));
        Thread.currentThread().join();
    }
}
