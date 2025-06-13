package no.cantara.realestate.metasys.cloudconnector.automationserver.streampoc;

import no.cantara.realestate.metasys.cloudconnector.automationserver.MetasysClient;
import no.cantara.realestate.security.UserToken;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.time.Instant;

import static no.cantara.realestate.metasys.cloudconnector.automationserver.streampoc.StreamPocClient.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.slf4j.LoggerFactory.getLogger;

public class ServerSentEventsTest {
    private static final Logger log = getLogger(ServerSentEventsTest.class);

    private static MockServerSentEventsRunner sseMockServer;
    private static MetasysClient metasysClient;
    private static int port;
    private static URI sdApiUri;
    private static UserToken stubUserToken;
    private static Instant tokenExpires;
    private static StreamPocClient streamPocClient;

    @BeforeAll
    static void beforeAll() throws IOException {
        port = findFreePort();
        sseMockServer = new MockServerSentEventsRunner();
        metasysClient = mock(MetasysClient.class);
        sdApiUri = URI.create(String.format("http://localhost:%d/", port));
        when(metasysClient.getApiUri()).thenReturn(sdApiUri);
        tokenExpires = Instant.now().plusSeconds(600);
        stubUserToken = new UserToken("accessToken12345", tokenExpires, "refreshToken67890");
        when(metasysClient.getUserToken()).thenReturn(stubUserToken);
        streamPocClient = new StreamPocClient(metasysClient);
    }

    @AfterEach
    void tearDown() {
        if (sseMockServer != null) {
            sseMockServer.stop();
        }
    }

    @Disabled("Disabled due to unstable test environment")
    @Test
    void receiveHello() throws InterruptedException {
        sseMockServer.start(port, MockServerSentEventsRunner.Scenario.HELLO);
        streamPocClient.createStream();
        //Wait for response from the stream.
        Thread.sleep(100);
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
