package no.cantara.realestate.zaphire.cloudconnector.automationserver;

import org.mockserver.integration.ClientAndServer;
import org.mockserver.matchers.Times;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

/**
 * Mock server som simulerer Zaphire SiteService API.
 * <p>
 * Simulerer endepunktene:
 * <ul>
 *   <li>GET  /site/{site}/Tag/Values?name={tagName} - live tag-verdier</li>
 *   <li>POST /site/{site}/Tag/Values - live tag-verdier (batch)</li>
 *   <li>GET  /site/{site}/Tags/History/Records?name={tagName}&from={from}&to={to} - historiske records</li>
 * </ul>
 */
public class ZaphireApiSimulator implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(ZaphireApiSimulator.class);

    public static final String DEFAULT_SITE = "test-site";
    public static final String TAG_TEMPERATURE = "building1/floor2/room201/temperature";
    public static final String TAG_HUMIDITY = "building1/floor2/room201/humidity";
    public static final String TAG_ENERGY = "building1/energy/meter1";
    public static final String TAG_NOT_FOUND = "nonexistent/tag";
    public static final String TAG_NO_LOGGING = "building1/floor2/room201/setpoint";

    private final ClientAndServer mockServer;
    private final int port;
    private boolean apiDown = false;
    private Instant apiDownSince;

    /**
     * Konstruktør som automatisk finner en ledig port.
     */
    public ZaphireApiSimulator() {
        this.mockServer = ClientAndServer.startClientAndServer();
        this.port = mockServer.getLocalPort();
        setupAllEndpoints();
    }

    /**
     * Konstruktør med spesifisert port.
     */
    public ZaphireApiSimulator(int port) {
        this.port = port;
        this.mockServer = ClientAndServer.startClientAndServer(port);
        setupAllEndpoints();
    }

    public void start() {
        log.info("ZaphireApiSimulator started on port {}", port);
    }

    public void stop() {
        if (mockServer != null && mockServer.isRunning()) {
            mockServer.stop();
            log.info("ZaphireApiSimulator stopped");
        }
    }

    @Override
    public void close() {
        stop();
    }

    public int getPort() {
        return port;
    }

    public boolean isRunning() {
        return mockServer != null && mockServer.isRunning();
    }

    public ClientAndServer getMockServer() {
        return mockServer;
    }

    // --- Setup all endpoints ---

    private void setupAllEndpoints() {
        mockServer.reset();
        setupTagValuesGetEndpoint();
        setupTagValuesPostEndpoint();
        setupHistoryRecordsEndpoint();
        setupTagNotFoundEndpoint();
        setupTagNoLoggingEndpoint();
        log.info("ZaphireApiSimulator: all endpoints configured on port {}", port);
    }

    // --- GET /site/{site}/Tag/Values?name={tagName} ---

    private void setupTagValuesGetEndpoint() {
        // Temperature tag
        mockServer
                .when(
                        request()
                                .withMethod("GET")
                                .withPath("/site/" + DEFAULT_SITE + "/Tag/Values")
                                .withQueryStringParameter("name", TAG_TEMPERATURE)
                                .withHeader("Authorization", "Bearer .*")
                )
                .respond(
                        response()
                                .withStatusCode(200)
                                .withHeader("Content-Type", "application/json")
                                .withBody("[\n" +
                                        "  {\n" +
                                        "    \"Name\": \"" + TAG_TEMPERATURE + "\",\n" +
                                        "    \"Units\": \"DegreesCelsius\",\n" +
                                        "    \"UnitsDisplay\": \"°C\",\n" +
                                        "    \"Value\": 21.5\n" +
                                        "  }\n" +
                                        "]")
                );

        // Humidity tag
        mockServer
                .when(
                        request()
                                .withMethod("GET")
                                .withPath("/site/" + DEFAULT_SITE + "/Tag/Values")
                                .withQueryStringParameter("name", TAG_HUMIDITY)
                                .withHeader("Authorization", "Bearer .*")
                )
                .respond(
                        response()
                                .withStatusCode(200)
                                .withHeader("Content-Type", "application/json")
                                .withBody("[\n" +
                                        "  {\n" +
                                        "    \"Name\": \"" + TAG_HUMIDITY + "\",\n" +
                                        "    \"Units\": \"PercentRelativeHumidity\",\n" +
                                        "    \"UnitsDisplay\": \"%RH\",\n" +
                                        "    \"Value\": 45.3\n" +
                                        "  }\n" +
                                        "]")
                );

        // Energy tag
        mockServer
                .when(
                        request()
                                .withMethod("GET")
                                .withPath("/site/" + DEFAULT_SITE + "/Tag/Values")
                                .withQueryStringParameter("name", TAG_ENERGY)
                                .withHeader("Authorization", "Bearer .*")
                )
                .respond(
                        response()
                                .withStatusCode(200)
                                .withHeader("Content-Type", "application/json")
                                .withBody("[\n" +
                                        "  {\n" +
                                        "    \"Name\": \"" + TAG_ENERGY + "\",\n" +
                                        "    \"Units\": \"KilowattHours\",\n" +
                                        "    \"UnitsDisplay\": \"kWh\",\n" +
                                        "    \"Value\": 12487.6\n" +
                                        "  }\n" +
                                        "]")
                );

        // Tag with error
        mockServer
                .when(
                        request()
                                .withMethod("GET")
                                .withPath("/site/" + DEFAULT_SITE + "/Tag/Values")
                                .withQueryStringParameter("name", TAG_NOT_FOUND)
                                .withHeader("Authorization", "Bearer .*")
                )
                .respond(
                        response()
                                .withStatusCode(200)
                                .withHeader("Content-Type", "application/json")
                                .withBody("[\n" +
                                        "  {\n" +
                                        "    \"Name\": \"" + TAG_NOT_FOUND + "\",\n" +
                                        "    \"ErrorMessage\": \"Not found.\",\n" +
                                        "    \"Value\": null\n" +
                                        "  }\n" +
                                        "]")
                );

        // Health check tag (returns 200 with empty-ish result)
        mockServer
                .when(
                        request()
                                .withMethod("GET")
                                .withPath("/site/" + DEFAULT_SITE + "/Tag/Values")
                                .withQueryStringParameter("name", "__healthcheck__")
                )
                .respond(
                        response()
                                .withStatusCode(200)
                                .withHeader("Content-Type", "application/json")
                                .withBody("[\n" +
                                        "  {\n" +
                                        "    \"Name\": \"__healthcheck__\",\n" +
                                        "    \"ErrorMessage\": \"Not found.\",\n" +
                                        "    \"Value\": null\n" +
                                        "  }\n" +
                                        "]")
                );
    }

    // --- POST /site/{site}/Tag/Values ---

    private void setupTagValuesPostEndpoint() {
        mockServer
                .when(
                        request()
                                .withMethod("POST")
                                .withPath("/site/" + DEFAULT_SITE + "/Tag/Values")
                                .withHeader("Authorization", "Bearer .*")
                                .withHeader("Content-Type", "application/json")
                )
                .respond(
                        response()
                                .withStatusCode(200)
                                .withHeader("Content-Type", "application/json")
                                .withBody("[\n" +
                                        "  {\n" +
                                        "    \"Name\": \"" + TAG_TEMPERATURE + "\",\n" +
                                        "    \"Units\": \"DegreesCelsius\",\n" +
                                        "    \"UnitsDisplay\": \"°C\",\n" +
                                        "    \"Value\": 21.5\n" +
                                        "  },\n" +
                                        "  {\n" +
                                        "    \"Name\": \"" + TAG_HUMIDITY + "\",\n" +
                                        "    \"Units\": \"PercentRelativeHumidity\",\n" +
                                        "    \"UnitsDisplay\": \"%RH\",\n" +
                                        "    \"Value\": 45.3\n" +
                                        "  },\n" +
                                        "  {\n" +
                                        "    \"Name\": \"" + TAG_ENERGY + "\",\n" +
                                        "    \"Units\": \"KilowattHours\",\n" +
                                        "    \"UnitsDisplay\": \"kWh\",\n" +
                                        "    \"Value\": 12487.6\n" +
                                        "  }\n" +
                                        "]")
                );
    }

    // --- GET /site/{site}/Tags/History/Records ---

    private void setupHistoryRecordsEndpoint() {
        // Temperature history records
        String temperatureRecordsJson = "[\n" +
                "  {\n" +
                "    \"Name\": \"" + TAG_TEMPERATURE + "\",\n" +
                "    \"Timestamp\": \"2024-01-15T08:00:00.0000000+00:00\",\n" +
                "    \"Value\": 20.1\n" +
                "  },\n" +
                "  {\n" +
                "    \"Name\": \"" + TAG_TEMPERATURE + "\",\n" +
                "    \"Timestamp\": \"2024-01-15T09:00:00.0000000+00:00\",\n" +
                "    \"Value\": 21.3\n" +
                "  },\n" +
                "  {\n" +
                "    \"Name\": \"" + TAG_TEMPERATURE + "\",\n" +
                "    \"Timestamp\": \"2024-01-15T10:00:00.0000000+00:00\",\n" +
                "    \"Value\": 22.0\n" +
                "  },\n" +
                "  {\n" +
                "    \"Name\": \"" + TAG_TEMPERATURE + "\",\n" +
                "    \"Timestamp\": \"2024-01-15T11:00:00.0000000+00:00\",\n" +
                "    \"Value\": 21.8\n" +
                "  },\n" +
                "  {\n" +
                "    \"Name\": \"" + TAG_TEMPERATURE + "\",\n" +
                "    \"Timestamp\": \"2024-01-15T12:00:00.0000000+00:00\",\n" +
                "    \"Value\": 22.5\n" +
                "  }\n" +
                "]";

        mockServer
                .when(
                        request()
                                .withMethod("GET")
                                .withPath("/site/" + DEFAULT_SITE + "/Tags/History/Records")
                                .withQueryStringParameter("name", TAG_TEMPERATURE)
                                .withQueryStringParameter("from", ".*")
                                .withQueryStringParameter("to", ".*")
                                .withHeader("Authorization", "Bearer .*")
                )
                .respond(
                        response()
                                .withStatusCode(200)
                                .withHeader("Content-Type", "application/json")
                                .withBody(temperatureRecordsJson)
                );

        // Energy history records
        String energyRecordsJson = "[\n" +
                "  {\n" +
                "    \"Name\": \"" + TAG_ENERGY + "\",\n" +
                "    \"Timestamp\": \"2024-01-15T00:00:00.0000000+00:00\",\n" +
                "    \"Value\": 12400.0\n" +
                "  },\n" +
                "  {\n" +
                "    \"Name\": \"" + TAG_ENERGY + "\",\n" +
                "    \"Timestamp\": \"2024-01-15T06:00:00.0000000+00:00\",\n" +
                "    \"Value\": 12425.5\n" +
                "  },\n" +
                "  {\n" +
                "    \"Name\": \"" + TAG_ENERGY + "\",\n" +
                "    \"Timestamp\": \"2024-01-15T12:00:00.0000000+00:00\",\n" +
                "    \"Value\": 12487.6\n" +
                "  }\n" +
                "]";

        mockServer
                .when(
                        request()
                                .withMethod("GET")
                                .withPath("/site/" + DEFAULT_SITE + "/Tags/History/Records")
                                .withQueryStringParameter("name", TAG_ENERGY)
                                .withQueryStringParameter("from", ".*")
                                .withQueryStringParameter("to", ".*")
                                .withHeader("Authorization", "Bearer .*")
                )
                .respond(
                        response()
                                .withStatusCode(200)
                                .withHeader("Content-Type", "application/json")
                                .withBody(energyRecordsJson)
                );
    }

    // --- Error endpoints ---

    private void setupTagNotFoundEndpoint() {
        mockServer
                .when(
                        request()
                                .withMethod("GET")
                                .withPath("/site/" + DEFAULT_SITE + "/Tags/History/Records")
                                .withQueryStringParameter("name", TAG_NOT_FOUND)
                                .withHeader("Authorization", "Bearer .*")
                )
                .respond(
                        response()
                                .withStatusCode(404)
                                .withHeader("Content-Type", "application/json")
                                .withBody("{\n" +
                                        "  \"ErrorMessage\": \"No tag found with name \\\"" + TAG_NOT_FOUND + "\\\".\"\n" +
                                        "}")
                );
    }

    private void setupTagNoLoggingEndpoint() {
        mockServer
                .when(
                        request()
                                .withMethod("GET")
                                .withPath("/site/" + DEFAULT_SITE + "/Tags/History/Records")
                                .withQueryStringParameter("name", TAG_NO_LOGGING)
                                .withHeader("Authorization", "Bearer .*")
                )
                .respond(
                        response()
                                .withStatusCode(422)
                                .withHeader("Content-Type", "application/json")
                                .withBody("{\n" +
                                        "  \"ErrorMessage\": \"Tag \\\"" + TAG_NO_LOGGING + "\\\" doesn't have logging enabled.\"\n" +
                                        "}")
                );
    }

    // --- Failure simulation ---

    /**
     * Simulerer at Zaphire API er nede i en gitt periode.
     * Alle endepunkter returnerer 502 Bad Gateway.
     */
    public void simulateApiDown(Duration downtime) {
        apiDown = true;
        apiDownSince = Instant.now();
        log.warn("Simulating Zaphire API down for {} seconds", downtime.getSeconds());

        setupFailureEndpoints(502);

        CompletableFuture.delayedExecutor(downtime.getSeconds(), TimeUnit.SECONDS)
                .execute(() -> {
                    apiDown = false;
                    log.info("Zaphire API simulator recovered after {} seconds", downtime.getSeconds());
                    setupAllEndpoints();
                });
    }

    /**
     * Simulerer spesifikke 50x feil for alle endepunkter.
     */
    public void simulate50xError(int statusCode) {
        if (statusCode < 500 || statusCode > 599) {
            throw new IllegalArgumentException("Status code must be in 50x range");
        }
        log.warn("Simulating {} error for all Zaphire endpoints", statusCode);
        setupFailureEndpoints(statusCode);
    }

    /**
     * Simulerer timeout med forsinket svar.
     */
    public void simulateTimeout(Duration delay) {
        mockServer.reset();
        log.warn("Simulating Zaphire timeout with {}s delay", delay.getSeconds());

        mockServer
                .when(request().withMethod("GET").withPath("/site/.*/Tag/Values"))
                .respond(response()
                        .withDelay(TimeUnit.MILLISECONDS, delay.toMillis())
                        .withStatusCode(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("[]"));

        mockServer
                .when(request().withMethod("GET").withPath("/site/.*/Tags/History/Records"))
                .respond(response()
                        .withDelay(TimeUnit.MILLISECONDS, delay.toMillis())
                        .withStatusCode(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("[]"));
    }

    /**
     * Simulerer intermitterende feil - første N kall feiler, deretter OK.
     */
    public void simulateIntermittentFailure(int failCount) {
        mockServer.reset();
        log.warn("Simulating intermittent 503 failure for first {} calls", failCount);

        // Første N kall feiler
        mockServer
                .when(
                        request()
                                .withMethod("GET")
                                .withPath("/site/.*/Tag/Values"),
                        Times.exactly(failCount)
                )
                .respond(response().withStatusCode(503));

        mockServer
                .when(
                        request()
                                .withMethod("GET")
                                .withPath("/site/.*/Tags/History/Records"),
                        Times.exactly(failCount)
                )
                .respond(response().withStatusCode(503));

        // Deretter normale svar
        setupTagValuesGetEndpoint();
        setupHistoryRecordsEndpoint();
    }

    /**
     * Simulerer 401 Unauthorized (ugyldig/utløpt token).
     */
    public void simulateUnauthorized() {
        mockServer.reset();
        log.warn("Simulating 401 Unauthorized for all Zaphire endpoints");

        mockServer
                .when(request().withMethod("GET").withPath("/site/.*/Tag/Values"))
                .respond(response()
                        .withStatusCode(401)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"ErrorMessage\": \"User doesn't have access.\"}"));

        mockServer
                .when(request().withMethod("POST").withPath("/site/.*/Tag/Values"))
                .respond(response()
                        .withStatusCode(401)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"ErrorMessage\": \"User doesn't have access.\"}"));

        mockServer
                .when(request().withMethod("GET").withPath("/site/.*/Tags/History/Records"))
                .respond(response()
                        .withStatusCode(401)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"ErrorMessage\": \"User doesn't have access.\"}"));
    }

    /**
     * Tilbakestiller til normale svar.
     */
    public void resetToNormal() {
        apiDown = false;
        setupAllEndpoints();
        log.info("ZaphireApiSimulator reset to normal operation");
    }

    private void setupFailureEndpoints(int statusCode) {
        mockServer.reset();

        mockServer
                .when(request().withMethod("GET").withPath("/site/.*/Tag/Values"))
                .respond(response().withStatusCode(statusCode));

        mockServer
                .when(request().withMethod("POST").withPath("/site/.*/Tag/Values"))
                .respond(response().withStatusCode(statusCode));

        mockServer
                .when(request().withMethod("GET").withPath("/site/.*/Tags/History/Records"))
                .respond(response().withStatusCode(statusCode));
    }

    public boolean isApiDown() {
        return apiDown;
    }

    public Duration getDowntime() {
        return apiDown ? Duration.between(apiDownSince, Instant.now()) : Duration.ZERO;
    }
}
