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
 * Mock server som simulerer Metasys API for trend samples
 */
public class MetasysApiTrendSamplesSimulator implements AutoCloseable {
    public static final String METASYS_OBJECT_ID = "05ccd193-a3f9-5db7-9c72-61987ca3d8dd";
    private static final Logger log = LoggerFactory.getLogger(MetasysApiTrendSamplesSimulator.class);
    protected static final String EXPECTED_ACCESS_TOKEN = "eyJ0eXAiOiJKV1QiLCJhbGciOiJSUzI1NiJ9...";
    private final ClientAndServer mockServer;
    private final int port;

    public MetasysApiTrendSamplesSimulator(int port) {
        this.port = port;
        this.mockServer = ClientAndServer.startClientAndServer(port);
        setupLoginEndpoints();
    }

    /**
     * Konstruktør som automatisk finner en ledig port
     */
    public MetasysApiTrendSamplesSimulator() {
        // La MockServer finne en ledig port automatisk
        this.mockServer = ClientAndServer.startClientAndServer();
        this.port = mockServer.getLocalPort();
        setupLoginEndpoints();
        setupTrendSamplesEndpoint();
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
                                        "  \"expires\": \"" + Instant.now().plus(Duration.ofMinutes(20)) + "\"\n" +
                                        "}")
                );
    }

    /**
     * Setter opp mock endpoints for trend samples
     */
    private void setupTrendSamplesEndpoint() {
        // Successful trend samples request with proper authentication
        String trendSampleJson = """
                {
                  "total": 1,
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
                  "next": "http://localhost:1080/api/v4/objects/05ccd193-a3f9-5db7-9c72-61987ca3d8dd/trendedAttributes/presentValue/samples?startTime=2023-05-24T00:46:47.000Z&endTime=2023-05-24T23:13:58.007Z&page=2&pageSize=1",
                  "attribute": "attributeEnumSet.presentValue",
                  "previous": null,
                  "self": "http://localhost:1080/api/v4/objects/05ccd193-a3f9-5db7-9c72-61987ca3d8dd/trendedAttributes/presentValue/samples?startTime=2023-05-24T00:46:47.000Z&endTime=2023-05-24T23:13:58.007Z&pageSize=1",
                  "objectUrl": "http://localhost:1080/api/v4/objects/05ccd193-a3f9-5db7-9c72-61987ca3d8dd"
                }
                
                """;
        mockServer
                .when(
                        request()
                                .withMethod("GET")
                                .withPath("/api/v4/objects/"+ METASYS_OBJECT_ID +"/trendedAttributes/presentValue/samples")
                                .withHeader("Authorization", "Bearer " + EXPECTED_ACCESS_TOKEN)
                                .withQueryStringParameters(
                                        org.mockserver.model.Parameter.param("startTime", ".*"),
                                        org.mockserver.model.Parameter.param("endTime", ".*"),
                                        org.mockserver.model.Parameter.param("page", ".*"),
                                        org.mockserver.model.Parameter.param("pageSize", ".*"),
                                        org.mockserver.model.Parameter.param("skip", ".*")
                                )
                )
                .respond(
                        response()
                                .withStatusCode(200)
                                .withHeader("Content-Type", "application/json")
                                .withBody(trendSampleJson)

                );

        // Unauthorized request (wrong or missing token)
        mockServer
                .when(
                        request()
                                .withMethod("GET")
                                .withPath("/api/v4/objects/.*/trendedAttributes/presentValue/samples")
                                .withHeader("Authorization", "Bearer " + EXPECTED_ACCESS_TOKEN)
                )
                .respond(
                        response()
                                .withStatusCode(404)
                                .withHeader("Content-Type", "application/json")
                                .withBody("{\n" +
                                        "  \"error\": \"Not Found\",\n" +
                                        "  \"message\": \"TrendSamplesNotFound\"\n" +
                                        "}")
                );
    }


    public boolean isRunning() {
        return mockServer != null && mockServer.isRunning();
    }

    public ClientAndServer getMockServer() {
        return mockServer;
    }
}