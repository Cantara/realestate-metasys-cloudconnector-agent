package no.cantara.realestate.metasys.cloudconnector.automationserver;

import no.cantara.realestate.cloudconnector.notifications.NotificationService;
import no.cantara.realestate.security.UserToken;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

public class MetasysApiLoginTest {
    private static final Logger log = LoggerFactory.getLogger(MetasysApiLoginTest.class);

    private MetasysApiLoginSimulator simulator;
    private NotificationService notificationService;
    private URI apiUri;

    @BeforeEach
    void setUp() throws URISyntaxException {
        // Reset MetasysClient singleton before each test
        MetasysClient.stopInstance4Testing();

        // Mock notification service
        notificationService = mock(NotificationService.class);

        // Start simulator with dynamic port - INGEN hardkodet port!
        simulator = new MetasysApiLoginSimulator();
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
    void loginOk(){
//        URI apiUri = new URI("http://localhost:" + simulator.getPort() + "/api/v4/");

        MetasysClient client = MetasysClient.getInstance("testuser", "testpass", apiUri, notificationService);
        assertTrue(client.isLoggedIn(), "Client should be logged in initially");
    }

    @Test
    @Timeout(value = 1, unit = TimeUnit.MINUTES)
    void loginWrongPassword() throws Exception {
        try {
            MetasysClient.getInstance("testuser", "wrongpass", apiUri, notificationService);
            fail("Expected exception due to wrong password");
        } catch (MetasysApiException e) {
            assertTrue(e.getMessage().contains("Login failed"), "Should throw exception for wrong password");
        }
    }

    @Test
    @Timeout(value = 1, unit = TimeUnit.MINUTES)
    void refreshTokenOk(){
        MetasysClient client = MetasysClient.getInstance("testuser", "testpass", apiUri, notificationService);
        assertTrue(client.isLoggedIn(), "Client should be logged in initially");
        client.refreshTokenSilently();
        assertTrue(client.isLoggedIn(), "Client should still be logged in after token refresh");
    }

    @Test
    @Timeout(value = 1, unit = TimeUnit.MINUTES)
    void refreshTokenWitWrongAccessToken()  {
        try {
            MetasysClient client = MetasysClient.getInstance("testuser", "wrongpass", apiUri, notificationService);
            UserToken userToken = client.getUserToken();
            assertFalse(client.isLoggedIn(), "Client should not be logged in with wrong password");
            assertNotNull(userToken, "User token should not be null");
            userToken.setAccessToken("timedout access token");
            client.setUserToken(userToken);

            //Test refresh with wrong access token
            client.refreshTokenSilently();
            fail("Expected exception due to wrong access token");
        } catch (MetasysApiException e) {
            assertTrue(e.getMessage().contains("Login failed"), "Should throw exception for wrong password");
        }
    }



}