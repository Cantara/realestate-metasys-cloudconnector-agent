package no.cantara.realestate.metasys.cloudconnector.automationserver;

import no.cantara.realestate.cloudconnector.notifications.NotificationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.mockserver.model.HttpRequest.request;

/**
 * Replays the 2026-09-05 incident against a Metasys simulator: the session dies server-side,
 * {@code /refreshToken} would still answer 200, and data calls return 403.
 * <p>
 * Session lifetime is real time (simulator); the login rule runs on a MutableClock, so the 60 s
 * wait between login attempts is skipped by advancing the clock instead of sleeping.
 */
class MetasysClientCBDeadSessionTest {

    private static final Duration SESSION_LIFETIME = Duration.ofMillis(1500);
    private static final Duration RECOVERY_CHECK_INTERVAL = Duration.ofMillis(100);
    private static final String OBJECT_ID = MetasysApiDeadSessionSimulator.METASYS_OBJECT_ID;

    private MetasysApiDeadSessionSimulator simulator;
    private MutableClock clock;
    private NotificationService notificationService;
    private MetasysClientCB client;

    @BeforeEach
    void setUp() {
        simulator = new MetasysApiDeadSessionSimulator(SESSION_LIFETIME);
        simulator.start();
        clock = new MutableClock(Instant.now());
        notificationService = mock(NotificationService.class);
        URI apiUri = URI.create("http://localhost:" + simulator.getPort() + "/api/v4/");
        client = new MetasysClientCB("testuser", "testpass", apiUri, notificationService, clock,
                HttpClient.newHttpClient(), RECOVERY_CHECK_INTERVAL);
    }

    @AfterEach
    void tearDown() {
        client.close();
        simulator.close();
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void deadSessionOpensCircuitAndPeriodicLoginClosesIt() throws InterruptedException {
        client.start();
        assertTrue(client.openForBusiness(), "Startup login should succeed");
        assertEquals(1, loginRequests());

        Set<MetasysTrendSample> samples = client.findTrendSamplesByDate(OBJECT_ID, 100, 0, Instant.now().minusSeconds(3600));
        assertEquals(2, samples.size());
        assertTrue(client.isHealthy());

        waitUntil(() -> !simulator.isSessionAlive(), Duration.ofSeconds(5));

        MetasysApiException forbidden = assertThrows(MetasysApiException.class,
                () -> client.findTrendSamplesByDate(OBJECT_ID, 100, 0, Instant.now().minusSeconds(3600)));
        assertEquals(403, forbidden.getStatusCode());
        assertFalse(client.openForBusiness());
        assertFalse(client.isHealthy());
        verify(notificationService, times(1)).sendAlarm(eq(MetasysClient.METASYS_API), anyString());

        int samplesRequestsWhenOpened = samplesRequests();
        assertThrows(CircuitOpenException.class,
                () -> client.findTrendSamplesByDate(OBJECT_ID, 100, 0, Instant.now().minusSeconds(3600)));
        assertEquals(samplesRequestsWhenOpened, samplesRequests(), "An open circuit must not reach Metasys");

        // Several periodic checks run, but the login rule denies them until 60 s have passed on the clock.
        Thread.sleep(RECOVERY_CHECK_INTERVAL.multipliedBy(5).toMillis());
        assertFalse(client.openForBusiness());
        assertEquals(1, loginRequests());

        clock.advance(Duration.ofSeconds(60));
        waitUntil(client::openForBusiness, Duration.ofSeconds(5));
        verify(notificationService, timeout(1000)).clearService("Metasys");

        samples = client.findTrendSamplesByDate(OBJECT_ID, 100, 0, Instant.now().minusSeconds(3600));
        assertEquals(2, samples.size());
        assertTrue(client.isHealthy());
        assertEquals(2, loginRequests());
        assertEquals(0, refreshRequests(), "Recovery is by login; refresh cannot revive a dead session");
    }

    private int loginRequests() {
        return recorded("POST", "/api/v4/login");
    }

    private int refreshRequests() {
        return recorded("GET", "/api/v4/refreshToken");
    }

    private int samplesRequests() {
        return recorded("GET", "/api/v4/objects/.*/trendedAttributes/presentValue/samples");
    }

    private int recorded(String method, String path) {
        return simulator.getMockServer().retrieveRecordedRequests(request().withMethod(method).withPath(path)).length;
    }

    private static void waitUntil(BooleanSupplier condition, Duration max) throws InterruptedException {
        long deadline = System.nanoTime() + max.toNanos();
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() > deadline) {
                fail("Condition not met within " + max);
            }
            Thread.sleep(20);
        }
    }
}
