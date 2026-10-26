package no.cantara.realestate.metasys.cloudconnector.automationserver;

import no.cantara.realestate.cloudconnector.notifications.NotificationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import javax.net.ssl.SSLSession;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Behaviour of MetasysClientCB with a controllable clock and a scripted HTTP layer.
 * The recovery scheduler is not started here; tests call the periodic check directly.
 */
class MetasysClientCBTest {

    private static final URI API_URI = URI.create("https://metasys.example.com/api/v4/");
    private static final Instant T0 = Instant.parse("2026-09-05T10:00:00Z");
    private static final String OBJECT_ID = "05ccd193-a3f9-5db7-9c72-61987ca3d8dd";
    private static final String SAMPLES_JSON = """
            {
              "total": 1,
              "items": [
                {
                  "value": { "value": 21.5, "units": "https://metasysserver/api/v4/enumSets/507/members/62" },
                  "timestamp": "2026-09-05T09:59:00Z",
                  "isReliable": true
                }
              ],
              "objectUrl": "https://metasys.example.com/api/v4/objects/05ccd193-a3f9-5db7-9c72-61987ca3d8dd"
            }
            """;

    private MutableClock clock;
    private ScriptedMetasys metasys;
    private NotificationService notificationService;
    private MetasysClientCB client;

    @BeforeEach
    void setUp() throws Exception {
        clock = new MutableClock(T0);
        metasys = new ScriptedMetasys(clock);
        notificationService = mock(NotificationService.class);
        client = new MetasysClientCB("user", "secret", API_URI, notificationService, clock, metasys.httpClient(),
                Duration.ofSeconds(15));
    }

    @AfterEach
    void tearDown() {
        client.close();
    }

    @Nested
    class LoginRule {

        @Test
        void secondAttemptWithin60SecondsIsDeniedWithoutHttpCall() {
            assertTrue(client.attemptLogin());

            clock.advance(Duration.ofSeconds(59));
            assertFalse(client.attemptLogin());

            assertEquals(1, metasys.loginCount());
        }

        @Test
        void attemptIsAllowedAgainAfter60Seconds() {
            assertTrue(client.attemptLogin());

            clock.advance(Duration.ofSeconds(60));

            assertTrue(client.attemptLogin());
            assertEquals(2, metasys.loginCount());
        }

        @Test
        void failedAttemptCountsTowardsThe60SecondRule() {
            metasys.loginStatus = 500;
            assertFalse(client.attemptLogin());

            metasys.loginStatus = 200;
            clock.advance(Duration.ofSeconds(30));
            assertFalse(client.attemptLogin(), "A failed attempt also starts the 60 s wait");
            assertEquals(1, metasys.loginCount());

            clock.advance(Duration.ofSeconds(30));
            assertTrue(client.attemptLogin());
            assertEquals(2, metasys.loginCount());
        }

        @Test
        void attemptThatFailsOnNetworkCountsTowardsThe60SecondRule() {
            metasys.loginFailsWithIOException = true;
            assertFalse(client.attemptLogin());

            metasys.loginFailsWithIOException = false;
            clock.advance(Duration.ofSeconds(10));

            assertFalse(client.attemptLogin());
            assertEquals(1, metasys.loginCount());
        }

        @Test
        void attemptIsRecordedBeforeTheHttpCall() {
            AtomicBoolean nestedAttemptResult = new AtomicBoolean(true);
            metasys.duringLogin = () -> nestedAttemptResult.set(client.attemptLogin());

            assertTrue(client.attemptLogin());

            assertFalse(nestedAttemptResult.get(), "An attempt started while the first is in flight must be denied");
            assertEquals(1, metasys.loginCount());
        }

        @Test
        @Timeout(value = 10, unit = TimeUnit.SECONDS)
        void concurrentAttemptIsDeniedImmediatelyWithoutBlocking() throws Exception {
            CountDownLatch loginInFlight = new CountDownLatch(1);
            CountDownLatch releaseLogin = new CountDownLatch(1);
            metasys.duringLogin = () -> {
                loginInFlight.countDown();
                await(releaseLogin);
            };
            CompletableFuture<Boolean> first = CompletableFuture.supplyAsync(client::attemptLogin);
            assertTrue(loginInFlight.await(5, TimeUnit.SECONDS));

            boolean second = CompletableFuture.supplyAsync(client::attemptLogin).get(1, TimeUnit.SECONDS);

            assertFalse(second);
            releaseLogin.countDown();
            assertTrue(first.get(5, TimeUnit.SECONDS));
            assertEquals(1, metasys.loginCount());
        }

        @Test
        void atMostFiveSuccessfulLoginsWithinTheTokenLifetime() {
            metasys.tokenLifetime = Duration.ofMinutes(30);
            for (int i = 0; i < 5; i++) {
                assertTrue(client.attemptLogin(), "Login " + (i + 1));
                clock.advance(Duration.ofSeconds(60));
            }

            assertFalse(client.attemptLogin(), "Sixth successful login within 30 min must be denied");
            assertEquals(5, metasys.loginCount());
        }

        @Test
        void windowLengthIsReadFromExpiresInTheLoginResponse() {
            metasys.tokenLifetime = Duration.ofMinutes(10);
            for (int i = 0; i < 5; i++) {
                assertTrue(client.attemptLogin());
                clock.advance(Duration.ofSeconds(60));
            }
            // First login was at T0; its window ends at T0 + 10 min.
            clock.advance(Duration.between(clock.instant(), T0.plus(Duration.ofMinutes(10)).minusSeconds(1)));
            assertFalse(client.attemptLogin());

            clock.advance(Duration.ofSeconds(1));
            assertTrue(client.attemptLogin());
            assertEquals(6, metasys.loginCount());
        }

        @Test
        void failedLoginsDoNotCountTowardsTheFiveSuccessfulLimit() {
            metasys.tokenLifetime = Duration.ofMinutes(30);
            metasys.loginStatus = 500;
            for (int i = 0; i < 5; i++) {
                assertFalse(client.attemptLogin());
                clock.advance(Duration.ofSeconds(60));
            }

            metasys.loginStatus = 200;
            assertTrue(client.attemptLogin());
        }

        @Test
        void refreshAndDataCallsDoNotCountAgainstTheLoginRule() {
            metasys.tokenLifetime = Duration.ofMinutes(6);
            assertTrue(client.attemptLogin());

            // Inside the 5 min refresh margin: the data call triggers a refresh first.
            clock.advance(Duration.ofSeconds(90));
            client.findTrendSamplesByDate(OBJECT_ID, 100, 0, T0);
            assertEquals(1, metasys.refreshCount());

            assertTrue(client.attemptLogin(), "Refresh and data calls must not be counted as login attempts");
        }
    }

    @Nested
    class CircuitAndRecovery {

        @Test
        void startupLoginFailureOpensTheBreakerAndKeepsTheInstance() {
            metasys.loginStatus = 500;

            client.loginAtStartup();

            assertFalse(client.openForBusiness());
            verify(notificationService).sendAlarm(eq(MetasysClient.METASYS_API), contains("startup login failed"));
        }

        @Test
        void dataCallWhileOpenIsRejectedWithoutHttpCall() {
            metasys.loginStatus = 500;
            client.loginAtStartup();

            assertThrows(CircuitOpenException.class, () -> client.findTrendSamplesByDate(OBJECT_ID, 100, 0, T0));
            assertEquals(0, metasys.samplesCount());
        }

        @Test
        void forbiddenDataCallOpensBreakerAndMarksUnhealthy() {
            client.loginAtStartup();
            client.findTrendSamplesByDate(OBJECT_ID, 100, 0, T0);
            assertTrue(client.isHealthy());

            metasys.samplesStatus = 403;
            MetasysApiException e = assertThrows(MetasysApiException.class,
                    () -> client.findTrendSamplesByDate(OBJECT_ID, 100, 0, T0));

            assertEquals(403, e.getStatusCode());
            assertFalse(client.openForBusiness());
            assertFalse(client.isHealthy());
            assertEquals(1, metasys.loginCount(), "No login in the call path");
            assertEquals(0, metasys.refreshCount(), "No refresh in the call path");
        }

        @Test
        void periodicCheckDoesNothingWhileClosed() {
            client.loginAtStartup();
            clock.advance(Duration.ofMinutes(2));

            client.recoveryCheck();

            assertEquals(1, metasys.loginCount());
        }

        @Test
        void periodicCheckRespectsTheLoginRuleWhileOpen() {
            client.loginAtStartup();
            metasys.samplesStatus = 403;
            assertThrows(MetasysApiException.class, () -> client.findTrendSamplesByDate(OBJECT_ID, 100, 0, T0));

            clock.advance(Duration.ofSeconds(15));
            client.recoveryCheck();

            assertFalse(client.openForBusiness());
            assertEquals(1, metasys.loginCount());
        }

        @Test
        void successfulLoginInPeriodicCheckClosesTheBreaker() {
            client.loginAtStartup();
            metasys.samplesStatus = 403;
            assertThrows(MetasysApiException.class, () -> client.findTrendSamplesByDate(OBJECT_ID, 100, 0, T0));

            clock.advance(Duration.ofSeconds(60));
            client.recoveryCheck();

            assertTrue(client.openForBusiness());
            assertEquals(2, metasys.loginCount());
            verify(notificationService).clearService("Metasys");
        }

        @Test
        void failedLoginInPeriodicCheckKeepsTheBreakerOpen() {
            client.loginAtStartup();
            metasys.samplesStatus = 403;
            assertThrows(MetasysApiException.class, () -> client.findTrendSamplesByDate(OBJECT_ID, 100, 0, T0));

            metasys.loginStatus = 500;
            clock.advance(Duration.ofSeconds(60));
            client.recoveryCheck();

            assertFalse(client.openForBusiness());
            verify(notificationService, never()).clearService(anyString());
        }

        @Test
        void alarmIsSentOncePerOpening() {
            client.loginAtStartup();
            metasys.samplesStatus = 403;
            assertThrows(MetasysApiException.class, () -> client.findTrendSamplesByDate(OBJECT_ID, 100, 0, T0));
            assertThrows(CircuitOpenException.class, () -> client.findTrendSamplesByDate(OBJECT_ID, 100, 0, T0));
            assertThrows(CircuitOpenException.class, () -> client.findTrendSamplesByDate(OBJECT_ID, 100, 0, T0));

            verify(notificationService, times(1)).sendAlarm(eq(MetasysClient.METASYS_API), anyString());
        }
    }

    @Nested
    class TokenRefresh {

        @Test
        void noRefreshOutsideTheMargin() {
            metasys.tokenLifetime = Duration.ofMinutes(30);
            client.loginAtStartup();
            clock.advance(Duration.ofMinutes(24));

            client.findTrendSamplesByDate(OBJECT_ID, 100, 0, T0);

            assertEquals(0, metasys.refreshCount());
        }

        @Test
        void refreshFiveMinutesBeforeExpiryAndUseTheNewToken() {
            metasys.tokenLifetime = Duration.ofMinutes(30);
            client.loginAtStartup();
            clock.advance(Duration.ofMinutes(25).plusSeconds(1));

            client.findTrendSamplesByDate(OBJECT_ID, 100, 0, T0);

            assertEquals(1, metasys.refreshCount());
            assertEquals(Optional.of("Bearer token-2"), metasys.lastSamplesRequest().headers().firstValue("Authorization"));
        }

        @Test
        void forbiddenRefreshOpensTheBreakerWithoutLogin() {
            metasys.tokenLifetime = Duration.ofMinutes(30);
            client.loginAtStartup();
            metasys.refreshStatus = 403;
            clock.advance(Duration.ofMinutes(26));

            assertThrows(CircuitOpenException.class, () -> client.findTrendSamplesByDate(OBJECT_ID, 100, 0, T0));

            assertFalse(client.openForBusiness());
            assertEquals(1, metasys.loginCount());
            assertEquals(0, metasys.samplesCount());
        }
    }

    @Nested
    class TrendSamples {

        @Test
        void samplesAreCountedAndStampedWithTrendId() {
            client.loginAtStartup();

            Set<MetasysTrendSample> samples = client.findTrendSamplesByDate(OBJECT_ID, 100, 0, T0);

            assertEquals(1, samples.size());
            assertEquals(OBJECT_ID, samples.iterator().next().getTrendId());
            assertEquals(1, client.getNumberOfTrendSamplesReceived());
            assertEquals(T0, client.getWhenLastTrendSampleReceived());
        }

        @Test
        void endTimeIsNowPlus60Seconds() {
            client.loginAtStartup();
            clock.advance(Duration.ofSeconds(5));

            client.findTrendSamplesByDate(OBJECT_ID, 100, 0, T0);

            String query = metasys.lastSamplesRequest().uri().getRawQuery();
            assertTrue(query.contains("endTime=2026-09-05T10%3A01%3A05Z"), query);
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Answers login, refreshToken and samples requests with scripted statuses and records every request.
     */
    private static final class ScriptedMetasys {
        private final MutableClock clock;
        private final HttpClient httpClient = mock(HttpClient.class);
        private final List<HttpRequest> requests = new CopyOnWriteArrayList<>();
        private int tokenCounter = 0;

        volatile int loginStatus = 200;
        volatile boolean loginFailsWithIOException = false;
        volatile int refreshStatus = 200;
        volatile int samplesStatus = 200;
        volatile Duration tokenLifetime = Duration.ofMinutes(30);
        volatile Runnable duringLogin = () -> {
        };

        ScriptedMetasys(MutableClock clock) throws Exception {
            this.clock = clock;
            when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                    .thenAnswer(invocation -> answer(invocation.getArgument(0)));
        }

        HttpClient httpClient() {
            return httpClient;
        }

        private HttpResponse<String> answer(HttpRequest request) throws IOException {
            requests.add(request);
            String path = request.uri().getPath();
            if (path.endsWith("/login")) {
                duringLogin.run();
                if (loginFailsWithIOException) {
                    throw new IOException("Connection refused");
                }
                return loginStatus == 200 ? new StubResponse(request, 200, tokenJson()) : new StubResponse(request, loginStatus, "");
            }
            if (path.endsWith("/refreshToken")) {
                return refreshStatus == 200 ? new StubResponse(request, 200, tokenJson()) : new StubResponse(request, refreshStatus, "");
            }
            if (path.endsWith("/samples")) {
                return samplesStatus == 200 ? new StubResponse(request, 200, SAMPLES_JSON) : new StubResponse(request, samplesStatus, "");
            }
            return new StubResponse(request, 404, "");
        }

        private synchronized String tokenJson() {
            tokenCounter++;
            return "{\"accessToken\":\"token-" + tokenCounter + "\",\"expires\":\"" + clock.instant().plus(tokenLifetime) + "\"}";
        }

        long loginCount() {
            return count("/login");
        }

        long refreshCount() {
            return count("/refreshToken");
        }

        long samplesCount() {
            return count("/samples");
        }

        HttpRequest lastSamplesRequest() {
            return requests.stream().filter(r -> r.uri().getPath().endsWith("/samples")).reduce((a, b) -> b).orElseThrow();
        }

        private long count(String pathSuffix) {
            return requests.stream().filter(r -> r.uri().getPath().endsWith(pathSuffix)).count();
        }
    }

    private record StubResponse(HttpRequest request, int statusCode, String body) implements HttpResponse<String> {
        @Override
        public Optional<HttpResponse<String>> previousResponse() {
            return Optional.empty();
        }

        @Override
        public HttpHeaders headers() {
            return HttpHeaders.of(Map.of(), (a, b) -> true);
        }

        @Override
        public Optional<SSLSession> sslSession() {
            return Optional.empty();
        }

        @Override
        public URI uri() {
            return request.uri();
        }

        @Override
        public HttpClient.Version version() {
            return HttpClient.Version.HTTP_1_1;
        }
    }
}
