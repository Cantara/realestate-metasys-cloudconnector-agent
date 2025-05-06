package no.cantara.realestate.metasys.cloudconnector.automationserver.stream;

import no.cantara.config.ApplicationProperties;
import no.cantara.config.testsupport.ApplicationPropertiesTestHelper;
import no.cantara.realestate.metasys.cloudconnector.automationserver.MetasysClient;
import no.cantara.realestate.security.UserToken;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.verify.VerificationTimes;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockserver.integration.ClientAndServer.startClientAndServer;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

class MetasysStreamClientTest {

    private MetasysStreamClient metasysStreamClient;
    private static ClientAndServer mockServer;

    private static int HTTP_PORT = 1082;

    private MetasysClient metasysClientMock;

    @BeforeAll
    static void beforeAll() {
        mockServer = startClientAndServer(HTTP_PORT);
    }

    @BeforeEach
    void setUp() throws Exception{
        ApplicationPropertiesTestHelper.enableMutableSingleton();
        ApplicationProperties.builder().buildAndSetStaticSingleton();
        metasysClientMock = mock(MetasysClient.class);
        metasysStreamClient = new MetasysStreamClient(metasysClientMock);
    }


    @Test
    void hasReceivedMessagesLatelyCheck() {
        long now = System.currentTimeMillis();
        metasysStreamClient.setLastEventReceievedAt(Instant.ofEpochMilli(now));
        assertTrue(metasysStreamClient.hasReceivedMessagesRecently());
    }

    @Test
    void serverReply204whenOpenStream() {
        String accessToken = "dummyToken";
        when(metasysClientMock.getUserToken())
                .thenReturn(new UserToken(accessToken,Instant.now().plusSeconds(60),"refreshingtoken"));
        mockServer
                .when(
                        request()
                                .withMethod("GET")
                                .withHeader("Authorization", "Bearer " + accessToken)
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