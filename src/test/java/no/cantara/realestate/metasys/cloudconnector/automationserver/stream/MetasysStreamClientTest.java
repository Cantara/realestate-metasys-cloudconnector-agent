package no.cantara.realestate.metasys.cloudconnector.automationserver.stream;

import no.cantara.config.ApplicationProperties;
import no.cantara.config.testsupport.ApplicationPropertiesTestHelper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.verify.VerificationTimes;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockserver.integration.ClientAndServer.startClientAndServer;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

class MetasysStreamClientTest {

    private MetasysStreamClient metasysStreamClient;
    private static ClientAndServer mockServer;

    private static int HTTP_PORT = 1082;

    @BeforeAll
    static void beforeAll() {
        mockServer = startClientAndServer(HTTP_PORT);
    }

    @BeforeEach
    void setUp() {
        ApplicationPropertiesTestHelper.enableMutableSingleton();
        ApplicationProperties.builder().buildAndSetStaticSingleton();
        metasysStreamClient = new MetasysStreamClient();
    }

    @Test
    void hasReceivedMessagesLatelyCheck() {
        long now = System.currentTimeMillis();
        metasysStreamClient.setLastEventReceievedAt(Instant.ofEpochMilli(now));
        assertTrue(metasysStreamClient.hasReceivedMessagesRecently());
    }

    @Test
    void serverReply204whenOpenStream() {
        mockServer
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
            metasysStreamClient.openStream("http://localhost:" + HTTP_PORT + "/api/v4/stream", "dummyToken", null, null);
            metasysStreamClient.close();
        } catch (Exception e) {
            System.out.println(e);
        }
        mockServer.verify(
                request()
                        .withMethod("GET")
                        .withPath("/api/v4/stream")
                        .withHeader("Authorization", "Bearer dummyToken"),
                VerificationTimes.atLeast(1));
    }

    @AfterAll
    static void afterAll() {
//        mockServer.retrieveRecordedRequestsAndResponses(request().withPath("/api/v4/stream"));
        mockServer.stop();
    }
}