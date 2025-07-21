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
import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

public class MetasysApiFailureSlowTest {
    private static final Logger log = LoggerFactory.getLogger(MetasysApiFailureSlowTest.class);

    private MetasysApiFailureSimulator simulator;
    private NotificationService notificationService;
    private URI apiUri;

    @BeforeEach
    void setUp() throws URISyntaxException {
        // Reset MetasysClient singleton before each test
        MetasysClient.stopInstance4Testing();

        // Mock notification service
        notificationService = mock(NotificationService.class);

        // Start simulator with dynamic port - INGEN hardkodet port!
        simulator = new MetasysApiFailureSimulator();
        simulator.start();

        // Give MockServer time to fully start
        try {
            Thread.sleep(100);
            apiUri = new URI("http://localhost:" + simulator.getPort() + "/api/v4/");
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

    @Test
    @Timeout(value = 2, unit = TimeUnit.MINUTES)
    public void testApi500InternalServerError() throws Exception {
        log.info("Testing 500 Internal Server Error handling");

        // Simuler 500 feil
        simulator.simulate50xError(500);

        // Forsøk login - skal feile
        MetasysApiException exception = assertThrows(MetasysApiException.class, () -> {
            MetasysClient.getInstance("testuser", "testpass", apiUri, notificationService);
        });

        assertTrue(exception.getMessage().contains("500"),
                "Exception should mention 500 error");

        log.info("500 error test completed successfully");
    }

    @Test
    @Timeout(value = 2, unit = TimeUnit.MINUTES)
    public void testApi502BadGateway() throws Exception {
        log.info("Testing 502 Bad Gateway handling");

        // Simuler 502 feil
        simulator.simulate50xError(502);

        MetasysApiException exception = assertThrows(MetasysApiException.class, () -> {
            MetasysClient.getInstance("testuser", "testpass", apiUri, notificationService);
        });

        assertTrue(exception.getStatusCode() == 502 || exception.getMessage().contains("502"));

        log.info("502 error test completed successfully");
    }

    @Test
    @Timeout(value = 2, unit = TimeUnit.MINUTES)
    public void testApi503ServiceUnavailable() throws Exception {
        log.info("Testing 503 Service Unavailable handling");

        // Simuler 503 feil
        simulator.simulate50xError(503);

        MetasysApiException exception = assertThrows(MetasysApiException.class, () -> {
            MetasysClient.getInstance("testuser", "testpass", apiUri, notificationService);
        });

        assertTrue(exception.getStatusCode() == 503 || exception.getMessage().contains("503"));

        log.info("503 error test completed successfully");
    }

    @Test
    @Timeout(value = 45, unit = TimeUnit.SECONDS)
    public void testApiTimeout() throws Exception {
        log.info("Testing API timeout handling");

        // Simuler timeout på 35 sekunder (lengre enn REQUEST_TIMEOUT i MetasysClient)
        simulator.simulateTimeout(Duration.ofSeconds(35));

        // Login skal feile med timeout
        assertThrows(Exception.class, () -> {
            MetasysClient.getInstance("testuser", "testpass", apiUri, notificationService);
        });

        log.info("Timeout test completed successfully");
    }

    @Test
    @Timeout(value = 2, unit = TimeUnit.MINUTES)
    public void testIntermittentFailure() throws Exception {
        log.info("Testing intermittent API failures");

        // Simuler intermitterende feil
        simulator.simulateIntermittentFailure();

        // Første forsøk skal feile
        assertThrows(MetasysApiException.class, () -> {
            MetasysClient.getInstance("testuser", "testpass", apiUri, notificationService);
        });

        // Reset client for nytt forsøk
        MetasysClient.stopInstance4Testing();

        // Andre forsøk skal også feile
        assertThrows(MetasysApiException.class, () -> {
            MetasysClient.getInstance("testuser", "testpass", apiUri, notificationService);
        });

        // Reset client for tredje forsøk
        MetasysClient.stopInstance4Testing();

        // Tredje forsøk skal lykkes
        MetasysClient client = MetasysClient.getInstance("testuser", "testpass", apiUri, notificationService);
        assertTrue(client.isLoggedIn(), "Client should eventually succeed after intermittent failures");

        log.info("Intermittent failure test completed successfully");
    }

    @Test
    @Timeout(value = 2, unit = TimeUnit.MINUTES)
    public void testRefreshTokenServerError() throws Exception {
        log.info("Testing refresh token with server error");

        // Start med normal login
        MetasysClient client = MetasysClient.getInstance("testuser", "testpass", apiUri, notificationService);
        assertTrue(client.isLoggedIn(), "Initial login should succeed");

        // Så simuler 502 feil for refresh token
        simulator.simulate50xError(502);

        // Refresh token skal feile
        assertThrows(MetasysApiException.class, () -> {
            client.refreshTokenSilently();
        });

        assertFalse(client.isApiAvailable(), "API should be marked as unavailable after 502");
        assertFalse(client.isHealthy(), "Client should be unhealthy after server error");

        log.info("Refresh token server error test completed successfully");
    }

}