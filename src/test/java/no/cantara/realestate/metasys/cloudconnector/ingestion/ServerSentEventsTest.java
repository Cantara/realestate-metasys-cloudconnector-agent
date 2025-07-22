package no.cantara.realestate.metasys.cloudconnector.ingestion;

import no.cantara.realestate.metasys.cloudconnector.automationserver.stream.MetasysStreamClient;
import no.cantara.realestate.metasys.cloudconnector.automationserver.streampoc.MockServerSentEventsRunner;
import no.cantara.realestate.security.UserToken;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;
import org.slf4j.Logger;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.time.Instant;

import static no.cantara.realestate.metasys.cloudconnector.ingestion.StreamPocClient.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.slf4j.LoggerFactory.getLogger;

@DisabledIfEnvironmentVariable(named = "JENKINS_URL", matches = ".*")
public class ServerSentEventsTest {
    private static final Logger log = getLogger(ServerSentEventsTest.class);

    private static MockServerSentEventsRunner sseMockServer;
    private static int port;
    private static StreamPocClient streamPocClient;

    @BeforeAll
    static void beforeAll() throws IOException {
        port = findFreePort();
        sseMockServer = new MockServerSentEventsRunner();
        MetasysStreamClient metasysStreamClient = mock(MetasysStreamClient.class);
        URI sdApiUri = URI.create(String.format("http://localhost:%d/", port));
        when(metasysStreamClient.getApiUri()).thenReturn(sdApiUri);
        Instant tokenExpires = Instant.now().plusSeconds(600);
        UserToken stubUserToken = new UserToken("accessToken12345", tokenExpires, "refreshToken67890");
        when(metasysStreamClient.getUserToken()).thenReturn(stubUserToken);
        streamPocClient = new StreamPocClient(metasysStreamClient);
    }

    @AfterEach
    void tearDown() {
        if (sseMockServer != null) {
            sseMockServer.stop();
        }
    }


    @Test
    void receiveHello() throws InterruptedException {
        sseMockServer.start(port, MockServerSentEventsRunner.Scenario.HELLO);
        streamPocClient.createStream();
        //Wait for response from the stream.
        Thread.sleep(200);
        assertEquals(MockServerSentEventsRunner.SUBSCRIPTION_ID, streamPocClient.getSubscriptionId());
        assertFalse(streamPocClient.isStreamThreadInterrupted());

    }

    @Test
    void receive204() throws InterruptedException {
        sseMockServer.start(port, MockServerSentEventsRunner.Scenario.HTTP_204);
        streamPocClient.createStream();
        //Wait for response from the stream.
        Thread.sleep(100);
        if (!streamPocClient.getClosingStreamReason().get().contains(RECONNECT_WITH_LAST_KNOWN_EVENT_ID_FAILED)){
            log.debug("TestResult: {}", streamPocClient.getClosingStreamReason().get());
        }
        assertTrue(streamPocClient.getClosingStreamReason().get().contains(RECONNECT_WITH_LAST_KNOWN_EVENT_ID_FAILED));
    }

    @Test
    void serverClosedStream() throws InterruptedException {
        sseMockServer.start(port, MockServerSentEventsRunner.Scenario.SERVER_CLOSE);
        streamPocClient.createStream();
        //Wait for response from the stream.
        Thread.sleep(100);
        assertFalse(streamPocClient.isStreamOpen());
        if (!streamPocClient.getClosingStreamReason().get().contains(STREAM_ENDED_WITH_NULL)){
            log.debug("TestResult: {}", streamPocClient.getClosingStreamReason().get()+ ", got: \n" + streamPocClient.getClosingStreamReason().get());
        }
        assertTrue(streamPocClient.getClosingStreamReason().get().contains(STREAM_ENDED_WITH_NULL));
    }

    @Test
    void networkDisconnect() throws InterruptedException {
        sseMockServer.start(port, MockServerSentEventsRunner.Scenario.NETWORK_HICKUP);
        streamPocClient.createStream();
        //Wait for response from the stream.
        Thread.sleep(100);
        assertFalse(streamPocClient.isStreamOpen());
        if (!streamPocClient.getClosingStreamReason().get().contains(NETWORK_INTERRUPTED)){
            log.debug("TestResult: {}", streamPocClient.getClosingStreamReason().get());
        }
        assertTrue(streamPocClient.getClosingStreamReason().get().contains(NETWORK_INTERRUPTED));
    }



    private static int findFreePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            socket.setReuseAddress(true);
            return socket.getLocalPort();
        }
    }

}
