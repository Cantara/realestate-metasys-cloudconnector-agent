package no.cantara.realestate.metasys.cloudconnector.automationserver.stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class MetasysStreamClientTest {

    private MetasysStreamClient metasysStreamClient;

    @BeforeEach
    void setUp() {
        metasysStreamClient = new MetasysStreamClient();
    }

    @Test
    void hasRecievedMessagesLatelyCheck() {
        long now = System.currentTimeMillis();
        metasysStreamClient.setLastEventReceievedAt(now);
        assertTrue(metasysStreamClient.hasReceivedMessagesRecently());
    }
}