package no.cantara.realestate.metasys.cloudconnector.automationserver;

import com.fasterxml.jackson.databind.node.ObjectNode;
import no.cantara.realestate.automationserver.BasClient;
import no.cantara.realestate.cloudconnector.notifications.NotificationService;
import no.cantara.realestate.json.RealEstateObjectMapper;
import no.cantara.realestate.observations.PresentValue;
import no.cantara.realestate.security.LogonFailedException;
import no.cantara.realestate.security.UserToken;
import no.cantara.realestate.sensors.SensorId;
import org.slf4j.Logger;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

import static no.cantara.realestate.metasys.cloudconnector.automationserver.MetasysClient.METASYS_API;
import static org.slf4j.LoggerFactory.getLogger;

/**
 * Metasys client that stops calling Metasys when the session is dead, and recovers by login within
 * the limits Metasys tolerates.
 * <p>
 * Proposal for issue #578; not wired into the application. It differs from {@link MetasysClient} in
 * three ways:
 * <ul>
 *   <li>Every data call goes through a {@link MetasysCommandExecutor}. A 401/403 opens a
 *       {@link MetasysCircuitBreaker}; while it is open, calls fail locally with
 *       {@link CircuitOpenException} and nothing is sent.</li>
 *   <li>Login is never done in the call path. A periodic check (every 15 s) logs in while the breaker
 *       is open and closes it on success.</li>
 *   <li>The login rule is explicit code here: see {@link #attemptLogin()}.</li>
 * </ul>
 * Consumers may ask {@link #openForBusiness()} before a round of calls to skip it cheaply; the
 * executor enforces the breaker either way.
 * <p>Example:
 * <pre>{@code
 * MetasysClientCB client = MetasysClientCB.getInstance(username, password, apiUri, notificationService);
 * if (client.openForBusiness()) {
 *     Set<MetasysTrendSample> samples = client.findTrendSamplesByDate(objectId, 1000, 0, lastSeen);
 * }
 * }</pre>
 */
public class MetasysClientCB implements BasClient, AutoCloseable {
    private static final Logger log = getLogger(MetasysClientCB.class);

    /** Metasys rejects or locks out clients that log in more often than this. */
    static final Duration MIN_TIME_BETWEEN_LOGIN_ATTEMPTS = Duration.ofSeconds(60);
    /** Metasys tolerates at most this many successful logins within one token lifetime. */
    static final int MAX_SUCCESSFUL_LOGINS_PER_TOKEN_LIFETIME = 5;
    static final Duration TOKEN_REFRESH_MARGIN = Duration.ofMinutes(5);
    static final Duration DEFAULT_RECOVERY_CHECK_INTERVAL = Duration.ofSeconds(15);
    /** Same look-ahead as MetasysClient, so samples stamped slightly in the future are included. */
    static final Duration TREND_SAMPLES_END_TIME_MARGIN = Duration.ofSeconds(60);
    static final String STARTUP_LOGIN_FAILED = "startup login failed";

    private static MetasysClientCB instance;

    private final String username;
    private final String password;
    private final URI apiUri;
    private final NotificationService notificationService;
    private final Clock clock;
    private final HttpClient httpClient;
    private final Duration recoveryCheckInterval;
    private final MetasysCircuitBreaker breaker;
    private final MetasysCommandExecutor executor;

    private final ReentrantLock loginLock = new ReentrantLock();
    private final ReentrantLock refreshLock = new ReentrantLock();
    // Guarded by loginLock
    private Instant lastLoginAttemptAt;
    // Guarded by loginLock. Each successful login occupies a slot until its token would have expired.
    private final Deque<SuccessfulLogin> successfulLogins = new ArrayDeque<>();

    private volatile MetasysUserToken userToken;
    private volatile boolean healthy = false;
    private volatile Instant whenLastTrendSampleReceived;
    private final AtomicLong numberOfTrendSamplesReceived = new AtomicLong();
    private ScheduledExecutorService recoveryScheduler;

    private record SuccessfulLogin(Instant at, Instant windowEnd) {
    }

    /**
     * Create the client without logging in or starting the recovery check. Used by tests; production
     * code uses {@link #getInstance(String, String, URI, NotificationService)}.
     *
     * @param recoveryCheckInterval how often the periodic check runs while the breaker is open
     */
    MetasysClientCB(String username, String password, URI apiUri, NotificationService notificationService,
                    Clock clock, HttpClient httpClient, Duration recoveryCheckInterval) {
        this.username = username;
        this.password = password;
        this.apiUri = Objects.requireNonNull(apiUri, "apiUri");
        this.notificationService = Objects.requireNonNull(notificationService, "notificationService");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
        this.recoveryCheckInterval = Objects.requireNonNull(recoveryCheckInterval, "recoveryCheckInterval");
        this.breaker = new MetasysCircuitBreaker(clock);
        this.breaker.addListener(this::onCircuitTransition);
        this.executor = new MetasysCommandExecutor(apiUri, httpClient, this::currentAccessToken, breaker);
    }

    /**
     * The shared client. The first call creates it, attempts a login and starts the periodic check.
     * <p>
     * A failed startup login does not fail this call: the breaker opens with reason
     * {@value #STARTUP_LOGIN_FAILED} and the periodic check keeps trying within the login rule.
     *
     * @return the single instance; later calls ignore the arguments
     */
    public static synchronized MetasysClientCB getInstance(String username, String password, URI apiUri,
                                                          NotificationService notificationService) {
        if (instance == null) {
            HttpClient httpClient = HttpClient.newBuilder()
                    .connectTimeout(MetasysCommandExecutor.REQUEST_TIMEOUT)
                    .build();
            instance = new MetasysClientCB(username, password, apiUri, notificationService, Clock.systemUTC(),
                    httpClient, DEFAULT_RECOVERY_CHECK_INTERVAL);
            instance.start();
        }
        return instance;
    }

    /**
     * Log in once and start the periodic recovery check.
     */
    void start() {
        loginAtStartup();
        startRecoveryCheck();
    }

    void loginAtStartup() {
        if (!attemptLogin()) {
            breaker.open(STARTUP_LOGIN_FAILED);
        }
    }

    private synchronized void startRecoveryCheck() {
        if (recoveryScheduler != null) {
            return;
        }
        recoveryScheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "metasys-cb-recovery");
            thread.setDaemon(true);
            return thread;
        });
        long intervalMillis = recoveryCheckInterval.toMillis();
        recoveryScheduler.scheduleWithFixedDelay(() -> {
            try {
                recoveryCheck();
            } catch (RuntimeException e) {
                log.warn("Metasys recovery check failed", e);
            }
        }, intervalMillis, intervalMillis, TimeUnit.MILLISECONDS);
    }

    /**
     * The body of the periodic check: while the breaker is open, log in if the rule allows it, and
     * close the breaker on success. Closing needs only a successful login, no data probe, because a
     * fresh login is what revives a session that died server-side.
     */
    void recoveryCheck() {
        if (!breaker.isOpen()) {
            return;
        }
        if (attemptLogin()) {
            breaker.close();
        }
    }

    /**
     * Log in to Metasys, if the login rule allows an attempt now.
     * <p>
     * The rule, as Metasys tolerates it:
     * <ol>
     *   <li>At least 60 s between login attempts. Failed attempts count, and the attempt is recorded
     *       before the HTTP call, so a slow or failing login cannot be retried in a tight loop.</li>
     *   <li>At most 5 successful logins within a rolling window as long as the token lifetime. The
     *       window of each login is read from its response: it lasts from the login until its
     *       {@code expires}.</li>
     * </ol>
     * A denied attempt returns {@code false} at once, makes no HTTP call, and never waits for an
     * attempt in progress on another thread. {@code /refreshToken} and data calls are not counted.
     *
     * @return {@code true} if a login was made and succeeded
     */
    boolean attemptLogin() {
        if (!loginLock.tryLock()) {
            log.debug("Metasys login denied: another login attempt is in progress");
            return false;
        }
        try {
            Instant now = clock.instant();
            String deniedBecause = loginDeniedReason(now);
            if (deniedBecause != null) {
                log.debug("Metasys login denied: {}", deniedBecause);
                return false;
            }
            lastLoginAttemptAt = now;
            MetasysUserToken token = postLogin();
            if (token == null) {
                return false;
            }
            successfulLogins.addLast(new SuccessfulLogin(now, token.getExpires()));
            userToken = token;
            log.info("Metasys login successful. Token expires {}", token.getExpires());
            return true;
        } finally {
            loginLock.unlock();
        }
    }

    // Returns null when an attempt is allowed. Caller holds loginLock.
    private String loginDeniedReason(Instant now) {
        if (lastLoginAttemptAt != null && now.isBefore(lastLoginAttemptAt.plus(MIN_TIME_BETWEEN_LOGIN_ATTEMPTS))) {
            return "last attempt at " + lastLoginAttemptAt + " is less than "
                    + MIN_TIME_BETWEEN_LOGIN_ATTEMPTS.toSeconds() + " s ago";
        }
        successfulLogins.removeIf(login -> !login.windowEnd().isAfter(now));
        if (successfulLogins.size() >= MAX_SUCCESSFUL_LOGINS_PER_TOKEN_LIFETIME) {
            return successfulLogins.size() + " successful logins since " + successfulLogins.peekFirst().at()
                    + "; next allowed at " + successfulLogins.peekFirst().windowEnd();
        }
        return null;
    }

    // Returns the token, or null when the login failed. Never logs the password.
    private MetasysUserToken postLogin() {
        URI loginUri = URI.create(apiUri + "login");
        ObjectNode body = RealEstateObjectMapper.getInstance().getObjectMapper().createObjectNode();
        body.put("username", username);
        body.put("password", password);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(loginUri)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .timeout(MetasysCommandExecutor.REQUEST_TIMEOUT)
                .build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.warn("Metasys login to {} as {} failed with status {}", loginUri, username, response.statusCode());
                return null;
            }
            return parseToken(response.body());
        } catch (IOException e) {
            log.warn("Metasys login to {} as {} failed: {}", loginUri, username, e.getMessage());
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (RuntimeException e) {
            log.warn("Metasys login to {} as {} returned an unreadable token: {}", loginUri, username, e.getMessage());
            return null;
        }
    }

    /**
     * Token source for the executor, asked before every send. Refreshes proactively when the token is
     * within {@link #TOKEN_REFRESH_MARGIN} of expiry and the breaker is closed.
     */
    private String currentAccessToken() {
        MetasysUserToken token = userToken;
        if (token == null) {
            return null;
        }
        if (!breaker.isOpen() && isWithinRefreshMargin(token)) {
            refreshTokenProactively();
        }
        return userToken.getAccessToken();
    }

    private boolean isWithinRefreshMargin(MetasysUserToken token) {
        return !clock.instant().isBefore(token.getExpires().minus(TOKEN_REFRESH_MARGIN));
    }

    /*
     * Only one thread refreshes; others continue with the current token, which is still valid for
     * up to TOKEN_REFRESH_MARGIN. 401/403 opens the breaker: the session is gone and only a login,
     * made by the periodic check, can revive it. Other failures keep the current token and are
     * retried on the next call.
     */
    private void refreshTokenProactively() {
        if (!refreshLock.tryLock()) {
            return;
        }
        try {
            MetasysUserToken token = userToken;
            if (breaker.isOpen() || !isWithinRefreshMargin(token)) {
                return;
            }
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiUri + "refreshToken"))
                    .header("Authorization", "Bearer " + token.getAccessToken())
                    .GET()
                    .timeout(MetasysCommandExecutor.REQUEST_TIMEOUT)
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            int status = response.statusCode();
            if (status == 200) {
                userToken = parseToken(response.body());
                log.debug("Metasys token refreshed. Expires {}", userToken.getExpires());
            } else if (status == 401 || status == 403) {
                breaker.open("HTTP " + status + " on refreshToken");
            } else {
                log.warn("Metasys token refresh failed with status {}. Keeping current token", status);
            }
        } catch (IOException | MetasysApiException e) {
            log.warn("Metasys token refresh failed: {}. Keeping current token", e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            refreshLock.unlock();
        }
    }

    private static MetasysUserToken parseToken(String body) {
        try {
            return RealEstateObjectMapper.getInstance().getObjectMapper().readValue(body, MetasysUserToken.class);
        } catch (IOException e) {
            throw new MetasysApiException("Unable to parse Metasys token response", e);
        }
    }

    private void onCircuitTransition(MetasysCircuitBreaker.Transition transition) {
        switch (transition.state()) {
            case OPEN -> {
                healthy = false;
                log.warn("Metasys circuit OPEN reason={}", transition.reason());
                notificationService.sendAlarm(METASYS_API, "Metasys circuit open: " + transition.reason());
            }
            case CLOSED -> {
                log.info("Metasys circuit CLOSED after {}", transition.previousStateDuration());
                notificationService.clearService(METASYS_API);
            }
        }
    }

    /**
     * @return {@code false} while the circuit is open; calls made now would be rejected locally
     */
    public boolean openForBusiness() {
        return !breaker.isOpen();
    }

    /**
     * Fetch the trend samples of one object from {@code onAndAfterDateTime} until now + 60 s.
     *
     * @param objectId           Metasys object id
     * @param take               ignored; paging is fixed to one page of 1000 samples, as in MetasysClient
     * @param skip               ignored, as in MetasysClient
     * @param onAndAfterDateTime start of the window
     * @return the samples, each with trendId set to {@code objectId}
     * @throws CircuitOpenException if the circuit is open; nothing was sent
     * @throws no.cantara.realestate.automationserver.TrendNotFoundException on 404
     * @throws MetasysApiException  on 401/403 (the circuit is now open), other error statuses, or network failure
     */
    @Override
    public Set<MetasysTrendSample> findTrendSamplesByDate(String objectId, int take, int skip, Instant onAndAfterDateTime) {
        if (onAndAfterDateTime == null) {
            throw new IllegalArgumentException("onAndAfterDateTime cannot be null");
        }
        Instant to = clock.instant().plus(TREND_SAMPLES_END_TIME_MARGIN);
        Set<MetasysTrendSample> samples = executor.execute(new FindTrendSamplesCommand(objectId, onAndAfterDateTime, to));
        numberOfTrendSamplesReceived.addAndGet(samples.size());
        whenLastTrendSampleReceived = clock.instant();
        healthy = true;
        return samples;
    }

    @Override
    public PresentValue findPresentValue(SensorId sensorId) {
        throw new UnsupportedOperationException("findPresentValue is not implemented in MetasysClientCB (nor in MetasysClient)");
    }

    @Override
    public Integer subscribePresentValueChange(String subscriptionId, String objectId) {
        throw new UnsupportedOperationException("subscribePresentValueChange is not yet supported by MetasysClientCB; use MetasysClient");
    }

    @Override
    public void logon() throws LogonFailedException {
        throw new UnsupportedOperationException("MetasysClientCB logs in by itself, within the Metasys login rule");
    }

    @Override
    public UserToken refreshToken() throws LogonFailedException {
        throw new UnsupportedOperationException("MetasysClientCB refreshes its token by itself before expiry");
    }

    @Override
    public boolean isLoggedIn() {
        MetasysUserToken token = userToken;
        return token != null && token.getExpires().isAfter(clock.instant());
    }

    @Override
    public UserToken getUserToken() {
        return userToken;
    }

    @Override
    public String getName() {
        return "MetasysApiClientCB";
    }

    /**
     * @return {@code true} after a successful data call, {@code false} from startup and whenever the circuit opens
     */
    @Override
    public boolean isHealthy() {
        return healthy;
    }

    @Override
    public long getNumberOfTrendSamplesReceived() {
        return numberOfTrendSamplesReceived.get();
    }

    public Instant getWhenLastTrendSampleReceived() {
        return whenLastTrendSampleReceived;
    }

    /**
     * Stop the periodic recovery check. A closed singleton is released, so the next
     * {@code getInstance} creates a fresh client.
     */
    @Override
    public void close() {
        synchronized (this) {
            if (recoveryScheduler != null) {
                recoveryScheduler.shutdownNow();
                recoveryScheduler = null;
            }
        }
        synchronized (MetasysClientCB.class) {
            if (instance == this) {
                instance = null;
            }
        }
    }
}
