package no.cantara.realestate.metasys.cloudconnector.automationserver;

import no.cantara.realestate.cloudconnector.notifications.NotificationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

public class MetasysApiTrendSamplesTest {
    private static final Logger log = LoggerFactory.getLogger(MetasysApiTrendSamplesTest.class);

    private MetasysApiTrendSamplesSimulator simulator;
    private NotificationService notificationService;
    private URI apiUri;
    private MetasysClient client;

    @BeforeEach
    void setUp() throws URISyntaxException {
        // Reset MetasysClient singleton before each test
        MetasysClient.stopInstance4Testing();

        // Mock notification service
        notificationService = mock(NotificationService.class);

        // Start simulator with dynamic port - INGEN hardkodet port!
        simulator = new MetasysApiTrendSamplesSimulator();
        simulator.start();

        // Give MockServer time to fully start
        try {
            Thread.sleep(100);
            apiUri = new URI("http://localhost:" + simulator.getPort() + "/api/v4/");
            client = MetasysClient.getInstance("testuser", "testpass", apiUri, notificationService);
            assertTrue(client.isLoggedIn(), "Client should be logged in initially");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @AfterEach
    void tearDown() {
        // Clean up simulator
        if (simulator != null) {
            simulator.close();
        }

        // Reset MetasysClient singleton after test
        MetasysClient.stopInstance4Testing();
    }

    @Test
    @Timeout(value = 1, unit = TimeUnit.MINUTES)
    void fetchTrendSamplesOk() throws URISyntaxException {
        assertTrue(client.isLoggedIn(), "Client should be logged in initially");
        assertEquals(0,client.getNumberOfTrendSamplesReceived(), "Number of trend samples received");
        String metasysObjectId = simulator.METASYS_OBJECT_ID;
        Instant sinceDateTime = Instant.now().minusSeconds(60 * 60); // 1 hour ago
        Set<MetasysTrendSample> trendSamples = client.findTrendSamplesByDate(metasysObjectId, 100, 0, sinceDateTime);
        assertNotNull(trendSamples, "Trend samples should not be null");
        assertEquals(2, trendSamples.size(), "Expected 2 trend samples");
        assertEquals(2, client.getNumberOfTrendSamplesReceived(), "Number of trend samples received after fetch");
    }

    @Test
    @Timeout(value = 1, unit = TimeUnit.MINUTES)
    void fetchTrendSamplesNotFound() throws URISyntaxException {
        assertTrue(client.isLoggedIn(), "Client should be logged in initially");
        assertEquals(0,client.getNumberOfTrendSamplesReceived(), "Number of trend samples received");
        String metasysObjectId = "metasysObjectMissingTrendSamples";
        Instant sinceDateTime = Instant.now().minusSeconds(60 * 60); // 1 hour ago
        Set<MetasysTrendSample> trendSamples = client.findTrendSamplesByDate(metasysObjectId, 100, 0, sinceDateTime);
        assertNotNull(trendSamples, "Trend samples should not be null");
        assertEquals(0, trendSamples.size(), "Expected 2 trend samples");
        assertEquals(0, client.getNumberOfTrendSamplesReceived(), "Number of trend samples received after fetch");
    }

    @Test
    @Timeout(value = 1, unit = TimeUnit.MINUTES)
    void metasysObjecIdIsNull() throws URISyntaxException {
        assertTrue(client.isLoggedIn(), "Client should be logged in initially");
        assertEquals(0,client.getNumberOfTrendSamplesReceived(), "Number of trend samples received");
        String metasysObjectId = null;
        Instant sinceDateTime = Instant.now().minusSeconds(60 * 60); // 1 hour ago
        Set<MetasysTrendSample> trendSamples = client.findTrendSamplesByDate(metasysObjectId, 100, 0, sinceDateTime);
        assertNotNull(trendSamples, "Trend samples should not be null");
        assertEquals(0, trendSamples.size(), "Expected 2 trend samples");
        assertEquals(0, client.getNumberOfTrendSamplesReceived(), "Number of trend samples received after fetch");
    }

}