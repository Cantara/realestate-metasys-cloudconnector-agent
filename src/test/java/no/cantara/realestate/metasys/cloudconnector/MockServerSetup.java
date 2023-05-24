package no.cantara.realestate.metasys.cloudconnector;

import org.mockserver.client.MockServerClient;
import org.mockserver.model.MediaType;

import java.time.Instant;

import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.JsonBody.json;

public class MockServerSetup {

    public static void main(String[] args) {
        clearAndSetLoginMock();
    }

    public static void clearAndSetLoginMock() {
        new MockServerClient("localhost", 1080)
                .clear(request().withPath("/api/v4/login"));
        new MockServerClient("localhost", 1080)
                .when(
                        request()
                                .withMethod("POST")
                                .withPath("/api/v4/login")
                                .withContentType(MediaType.APPLICATION_JSON)
                                .withBody(json("{\"username\": \"jane-doe\", \"password\": \"strongPassword\"}"))
                )
                .respond(
                        response()
                                .withStatusCode(200)
                                .withBody("{\n" +
                                        "  \"accessToken\": \"eyJ0eXAiOiJKV1QiLCJhbGciOiJSUzI1...\",\n" +
                                        "  \"expires\": \"" + Instant.now().plusSeconds(60 * 60).toString() + "\"\n" +
                                        "}\n")
                                .withHeader(
                                        "Content-Type", "application/vnd.metasysapi.v4+json"
                                )
                );
    }

    //"/api/v4/objects/208540b1-ab8a-566a-8a41-8b4cee515baf/trendedAttributes/presentValue/samples"
    //
    public static void clearAndSetSensorMockData(String objectId) {
        new MockServerClient("localhost", 1080)
                .clear(request().withPath("/api/v4/objects/" + objectId + "/trendedAttributes/presentValue/samples"));
        new MockServerClient("localhost", 1080)
                .when(
                        request()
                                .withMethod("GET")
                                .withPath("/api/v4/objects/" + objectId + "/trendedAttributes/presentValue/samples")
                                .withContentType(MediaType.APPLICATION_JSON)
                )
                .respond(
                        response()
                                .withStatusCode(200)
                                .withBody("{\n" +
                                        "  \"accessToken\": \"eyJ0eXAiOiJKV1QiLCJhbGciOiJSUzI1...\",\n" +
                                        "  \"expires\": \"" + Instant.now().plusSeconds(60 * 10).toString() + "\"\n" +
                                        "}\n")
                                .withBody("{\n" +
                                        "  \"total\": 1,\n" +
                                        "  \"items\": [\n" +
                                        "    {\n" +
                                        "      \"value\": {\n" +
                                        "        \"value\": 20.9,\n" +
                                        "        \"units\": \"unitEnumSet.degC\"\n" +
                                        "      },\n" +
                                        "      \"timestamp\":  \"" + Instant.now().minusSeconds(60 * 10).toString() + "\",\n" +
                                        "      \"isReliable\": true\n" +
                                        "    }\n" +
                                        "  ],\n" +
                                        "  \"next\": \"http://localhost:1080/api/v4/objects/"+ objectId + "/trendedAttributes/presentValue/samples?startTime=2020-05-12T16:46:47.000Z&endTime=2021-01-19T18:13:58.007Z&page=2&pageSize=1\",\n" +
                                        "  \"previous\": null,\n" +
                                        "  \"attribute\": \"attributeEnumSet.presentValue\",\n" +
                                        "  \"self\": \"http://localhost:1080/api/v4/objects/"+ objectId + "/trendedAttributes/presentValue/samples?startTime=2020-05-12T16:46:47.000Z&endTime=2021-01-19T18:13:58.007Z&pageSize=1\",\n" +
                                        "  \"objectUrl\": \"http://localhost:1080/api/v4/objects/"+ objectId + "\"\n" +
                                        "}")
                                .withHeader(
                                        "Content-Type", "application/vnd.metasysapi.v4+json"
                                )
                );
    }
}

/*
import static org.mockserver.integration.ClientAndServer.startClientAndServer;
private ClientAndServer mockServer;
@Before
public void startMockServer() {
    mockServer = startClientAndServer(1080);
}
@After
public void stopMockServer() {
    mockServer.stop();
}
 */
