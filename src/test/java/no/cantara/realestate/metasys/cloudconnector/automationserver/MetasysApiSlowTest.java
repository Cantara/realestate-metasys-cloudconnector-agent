package no.cantara.realestate.metasys.cloudconnector.automationserver;

import no.cantara.realestate.cloudconnector.notifications.NotificationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

public class MetasysApiSlowTest {
    private static final Logger log = LoggerFactory.getLogger(MetasysApiSlowTest.class);

    private MetasysApiFailureSimulator simulator;
    private NotificationService notificationService;

    @BeforeEach
    void setUp() {
        // Reset MetasysClient singleton before each test
        MetasysClient.stopInstance4Testing();

        // Mock notification service
        notificationService = mock(NotificationService.class);

        // Start simulator with dynamic port - INGEN hardkodet port!
        simulator = new MetasysApiFailureSimulator();
        simulator.start();

        // Give MockServer time to fully start
        try {
            Thread.sleep(1000);
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
    public void testApiRecoveryAfter502Error() throws Exception {
        log.info("Starting API recovery test with MockServer");

        // Start MetasysClient - bruk dynamisk port!
        URI apiUri = new URI("http://localhost:" + simulator.getPort() + "/api/v4/");

        MetasysClient client = MetasysClient.getInstance("testuser", "testpass", apiUri, notificationService);

        // Verifiser normal operation
        assertTrue(client.isLoggedIn(), "Client should be logged in initially");
        assertTrue(client.isApiAvailable(), "API should be available initially");
        log.info("Initial setup verified - client logged in and API available");

        // Simuler API down i 2 minutter
        log.info("Simulating API down for 2 seconds");
        simulator.simulateApiDown(Duration.ofSeconds(2));
        assertThrows(MetasysApiException.class, () -> client.refreshTokenSilently());
        assertFalse( client.isApiAvailable(), "API should be unavailable");
        assertFalse(client.isHealthy(), "MetasysClient is not healthy during API outage");
        assertFalse(client.isLoggedIn(), "Client is not logged in during API outage");
        log.info("Waiting for health check to detect problem...");
        Thread.sleep(3000);
        client.refreshTokenSilently();
        assertTrue(client.isApiAvailable(), "API should be marked as available");
    }

    /*
    @Test
    @Timeout(value = 2, unit = TimeUnit.MINUTES)
    public void testShortApiOutage() throws Exception {
        log.info("Starting short API outage test");

        URI apiUri = new URI("http://localhost:" + simulator.getPort() + "/api/v4/");
        MetasysClient client = MetasysClient.getInstance("testuser", "testpass", apiUri, notificationService);

        // Verifiser normal operation
        assertTrue(client.isLoggedIn(), "Client should be logged in initially");
        assertTrue(client.isApiAvailable(), "API should be available initially");

        // Simuler kort API outage (30 sekunder)
        log.info("Simulating short API outage for 3 seconds");
        simulator.simulateApiDown(Duration.ofSeconds(3));

        // Vent litt
        Thread.sleep(4000);

        // API skal være tilgjengelig igjen
        // Gi litt tid for health check
        Thread.sleep(30000);

        assertTrue(client.isApiAvailable(), "API should recover from short outage");
        assertTrue(client.isLoggedIn(), "Client should be logged in after short outage");

        log.info("Short outage test completed successfully");
    }

    @Test
    public void testBasicMockServerFunctionality() throws Exception {
        log.info("Testing basic MockServer functionality");

        assertTrue(simulator.isRunning(), "MockServer should be running");

        URI apiUri = new URI("http://localhost:" + simulator.getPort() + "/api/v4/");
        MetasysClient client = MetasysClient.getInstance("testuser", "testpass", apiUri, notificationService);

        assertTrue(client.isLoggedIn(), "Should be able to login with MockServer");

        log.info("Basic MockServer functionality test passed");
    }

     */
}