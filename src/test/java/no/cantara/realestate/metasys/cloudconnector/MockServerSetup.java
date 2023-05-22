package no.cantara.realestate.metasys.cloudconnector;

import org.mockserver.client.MockServerClient;
import org.mockserver.model.MediaType;

import java.time.Instant;

import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.JsonBody.json;

public class MockServerSetup {

    public static void main(String[] args) {
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
                                        "  \"expires\": \""+ Instant.now().plusSeconds(60*10).toString() +"\"\n" +
                                        "}\n")
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
