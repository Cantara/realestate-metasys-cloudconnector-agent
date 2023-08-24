package no.cantara.realestate.metasys.cloudconnector.automationserver.stream;

import no.cantara.config.ApplicationProperties;
import no.cantara.config.testsupport.ApplicationPropertiesTestHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.junit.jupiter.MockServerExtension;
import org.mockserver.junit.jupiter.MockServerSettings;
import org.mockserver.verify.VerificationTimes;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

@ExtendWith(MockServerExtension.class)
@MockServerSettings(ports = {8787, 8888})
class MetasysStreamClientMockserverTest {

    private MetasysStreamClient metasysStreamClient;
    private final ClientAndServer mockServer;

    private int httpPort = -1;

    public MetasysStreamClientMockserverTest(ClientAndServer client) {
        this.mockServer = client;
    }


    @BeforeEach
    void setUp() {
        ApplicationPropertiesTestHelper.enableMutableSingleton();
        ApplicationProperties.builder().buildAndSetStaticSingleton();
        metasysStreamClient = new MetasysStreamClient();
        httpPort = mockServer.getPort();
    }

    @Test
    void hasReceivedMessagesLatelyCheck() {
        long now = System.currentTimeMillis();
        metasysStreamClient.setLastEventReceievedAt(now);
        assertTrue(metasysStreamClient.hasReceivedMessagesRecently());
    }

    @Test
    void serverReply204whenOpenStream() {
        mockServer
//        new MockServerClient("localhost", HTTP_PORT)
                .when(
                        request()
                                .withMethod("GET")
                                .withHeader("Authorization", "Bearer dummyToken")
                                .withPath("/api/v4/stream")
                )
                .respond(
                        response()
                                .withStatusCode(204)
                );


        try {
            metasysStreamClient.openStream("http://localhost:" + httpPort + "/api/v4/stream", "dummyToken", null, null);
            Thread.sleep(200);
            metasysStreamClient.close();
        } catch (Exception e) {
            System.out.println(e);
        }
//        new MockServerClient("localhost", HTTP_PORT).verify(
        mockServer.verify(
                request()
                        .withMethod("GET")
                        .withPath("/api/v4/stream")
                        .withHeader("Authorization", "Bearer dummyToken"),
                VerificationTimes.atLeast(1));
    }


}