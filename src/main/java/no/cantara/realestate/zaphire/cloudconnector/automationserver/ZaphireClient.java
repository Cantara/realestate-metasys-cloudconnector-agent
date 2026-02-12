package no.cantara.realestate.zaphire.cloudconnector.automationserver;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import no.cantara.realestate.automationserver.BasClient;
import no.cantara.realestate.cloudconnector.RealestateCloudconnectorException;
import no.cantara.realestate.cloudconnector.StatusType;
import no.cantara.realestate.cloudconnector.notifications.NotificationService;
import no.cantara.realestate.metasys.cloudconnector.status.TemporaryHealthResource;
import no.cantara.realestate.observations.PresentValue;
import no.cantara.realestate.security.LogonFailedException;
import no.cantara.realestate.security.UserToken;
import no.cantara.realestate.sensors.SensorId;
import no.cantara.realestate.zaphire.cloudconnector.ZaphireCloudConnectorException;
import org.slf4j.Logger;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

import static io.opentelemetry.api.common.AttributeKey.longKey;
import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static no.cantara.realestate.metasys.cloudconnector.MetasysCloudconnectorApplication.INSTRUMENTATION_SCOPE_NAME_VALUE;
import static org.slf4j.LoggerFactory.getLogger;

/**
 * Singleton klient for Zaphire BAS API med Bearer token autentisering.
 * Thread-safe implementasjon som følger samme mønster som MetasysClient.
 */
public class ZaphireClient implements BasClient {
    private static final Logger log = getLogger(ZaphireClient.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration TOKEN_REFRESH_MARGIN = Duration.ofMinutes(5);

    public static final String ZAPHIRE_API = "Zaphire";
    public static final String HOST_UNREACHABLE = "HOST_UNREACHABLE";
    public static final String ZAPHIRE_API_UNAVAILABLE = "Zaphire API is unavailable";
    public static final String LOGON_FAILED = "Logon to Zaphire Api Failed";

    private static ZaphireClient instance;

    private final HttpClient httpClient;
    private final URI apiUri;
    private final String defaultSite;
    private final NotificationService notificationService;
    private long numberOfTrendSamplesReceived = 0;
    private Instant whenLastTrendSampleReceived = null;
    private boolean isHealthy = true;
    final Tracer tracer;
    final Meter meter;
    private final RateLimiter rateLimiter;

    private UserToken userToken;
    private String accessToken;
    private Instant tokenExpiryTime;
    private final ReentrantLock authLock = new ReentrantLock();

    private final ScheduledExecutorService apiHealthChecker;
    private volatile boolean apiAvailable = true;
    private volatile Instant lastSuccessfulApiCall;
    private volatile Instant lastFailedApiCall;
    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);

    /**
     * Privat konstruktør for singleton-mønsteret.
     */
    private ZaphireClient(URI apiUri, String defaultSite, NotificationService notificationService) {
        this.apiUri = apiUri;
        this.defaultSite = defaultSite;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(REQUEST_TIMEOUT)
                .build();
        this.notificationService = notificationService;
        tracer = GlobalOpenTelemetry.getTracer(INSTRUMENTATION_SCOPE_NAME_VALUE);
        meter = GlobalOpenTelemetry.getMeter(INSTRUMENTATION_SCOPE_NAME_VALUE);

        RateLimiterConfig config = RateLimiterConfig.custom()
                .limitForPeriod(2)
                .limitRefreshPeriod(Duration.ofMillis(200))
                .timeoutDuration(Duration.ofSeconds(10))
                .build();
        RateLimiterRegistry registry = RateLimiterRegistry.of(config);
        this.rateLimiter = registry.rateLimiter("zaphireObservationsLimiter");

        this.apiHealthChecker = Executors.newScheduledThreadPool(1);
        startPeriodicApiHealthCheck();
    }

    /**
     * Henter singleton-instansen av ZaphireClient.
     * Initialiserer klienten med gitte detaljer hvis den ikke allerede eksisterer.
     */
    public static synchronized ZaphireClient getInstance(URI apiUri, String defaultSite, NotificationService notificationService) {
        if (instance == null) {
            instance = new ZaphireClient(apiUri, defaultSite, notificationService);
        }
        return instance;
    }

    /**
     * Henter singleton-instansen av ZaphireClient.
     * Kaster en IllegalStateException hvis klienten ikke er initialisert.
     */
    public static synchronized ZaphireClient getInstance() {
        if (instance == null) {
            throw new IllegalStateException("ZaphireClient har ikke blitt initialisert. Bruk getInstance(apiUri, defaultSite, notificationService) først.");
        }
        return instance;
    }

    /**
     * Used only for testing
     */
    static void stopInstance4Testing() {
        log.warn("ZaphireClient is reset. Must only be used for testing.");
        if (instance != null && instance.apiHealthChecker != null) {
            instance.apiHealthChecker.shutdown();
            try {
                if (!instance.apiHealthChecker.awaitTermination(5, TimeUnit.SECONDS)) {
                    instance.apiHealthChecker.shutdownNow();
                }
            } catch (InterruptedException e) {
                instance.apiHealthChecker.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        instance = null;
    }

    // --- Authentication (placeholder) ---

    @Override
    public void logon() throws LogonFailedException {
        log.info("Zaphire logon - placeholder. Bearer token must be configured externally.");
        // Placeholder: In a real implementation, this would authenticate against the Zaphire API
        // and obtain a bearer token.
        if (accessToken == null) {
            log.warn("No access token configured for Zaphire. Set token via setAccessToken().");
        }
    }

    @Override
    public boolean isLoggedIn() {
        if (accessToken == null) {
            return false;
        }
        if (tokenExpiryTime != null && tokenExpiryTime.isBefore(Instant.now())) {
            log.debug("Zaphire token expired at: {}", tokenExpiryTime);
            return false;
        }
        return true;
    }

    @Override
    public UserToken getUserToken() {
        return userToken;
    }

    @Override
    public UserToken refreshToken() throws LogonFailedException {
        log.info("Zaphire refreshToken - placeholder.");
        // Placeholder for token refresh logic
        if (userToken != null) {
            return userToken;
        }
        throw new LogonFailedException("Zaphire token refresh not implemented yet.");
    }

    /**
     * Set the bearer token for Zaphire API access.
     * Used until proper authentication is implemented.
     */
    public void setAccessToken(String accessToken, Instant expiresAt) {
        this.accessToken = accessToken;
        this.tokenExpiryTime = expiresAt;
        this.userToken = new UserToken(accessToken, expiresAt, null);
        log.debug("Zaphire access token set, expires: {}", expiresAt);
    }

    void ensureValidToken() throws ZaphireApiException {
        if (accessToken == null) {
            authLock.lock();
            try {
                if (accessToken == null) {
                    log.warn("No Zaphire access token available.");
                    throw new ZaphireApiException("No Zaphire access token configured.", 401);
                }
            } finally {
                authLock.unlock();
            }
        } else if (tokenExpiryTime != null && tokenExpiryTime.minus(TOKEN_REFRESH_MARGIN).isBefore(Instant.now())) {
            log.debug("Zaphire token is about to expire or has expired.");
        }
    }

    // --- BasClient interface methods ---

    @Override
    public String getName() {
        return "ZaphireApiClient";
    }

    @Override
    public boolean isHealthy() {
        return isHealthy;
    }

    @Override
    public long getNumberOfTrendSamplesReceived() {
        return numberOfTrendSamplesReceived;
    }

    @Override
    public PresentValue findPresentValue(SensorId sensorId) {
        String tagName = sensorId.getId();
        return executeWithTokenHandling(() -> {
            Span span = tracer.spanBuilder("findPresentValue").setSpanKind(SpanKind.CLIENT).startSpan();
            try (Scope ignored = span.makeCurrent()) {
                boolean permission = rateLimiter.acquirePermission();
                if (!permission) {
                    throw new RealestateCloudconnectorException("Rate limit exceeded for findPresentValue", StatusType.RETRY_MAY_FIX_ISSUE);
                }

                List<ZaphireTagValue> tagValues = fetchTagValues(defaultSite, tagName);
                if (tagValues == null || tagValues.isEmpty()) {
                    log.debug("No tag values returned for tag: {}", tagName);
                    return null;
                }

                ZaphireTagValue tagValue = tagValues.get(0);
                if (tagValue.hasError()) {
                    log.warn("Zaphire returned error for tag {}: {}", tagName, tagValue.getErrorMessage());
                    return null;
                }

                PresentValue presentValue = new PresentValue();
                presentValue.setSensorId(tagName);
                Number numericValue = tagValue.getNumericValue();
                if (numericValue != null) {
                    presentValue.setValue(numericValue);
                }
                presentValue.setObservedAt(Instant.now());
                presentValue.setReliable(numericValue != null);

                Attributes attributes = Attributes.of(stringKey("tagName"), tagName);
                span.addEvent("Fetched present value", attributes);
                markApiHealthy();
                return presentValue;
            } catch (ZaphireApiException e) {
                throw e;
            } catch (Exception e) {
                span.recordException(e);
                throw new ZaphireApiException("Failed to find present value for tag: " + tagName + ". Reason: " + e.getMessage(), e);
            } finally {
                span.end();
            }
        }, "findPresentValue");
    }

    @Override
    public Set<ZaphireTrendSample> findTrendSamplesByDate(String trendId, int take, int skip, Instant onAndAfterDateTime) {
        return executeWithTokenHandling(() -> {
            if (onAndAfterDateTime == null) {
                throw new IllegalArgumentException("onAndAfterDateTime cannot be null");
            }

            Span span = tracer.spanBuilder("findTrendSamplesByDate").setSpanKind(SpanKind.CLIENT).startSpan();
            Attributes attributes = Attributes.of(stringKey("trendId"), trendId);

            boolean permitted = rateLimiter.acquirePermission();
            if (!permitted) {
                log.debug("Rate limit exceeded for findTrendSamplesByDate. trendId: {}, onAndAfterDateTime: {}",
                        trendId, onAndAfterDateTime);
                throw new RealestateCloudconnectorException("Rate limit exceeded. trendId: " + trendId, StatusType.RETRY_MAY_FIX_ISSUE);
            }

            List<ZaphireTrendSample> trendSamples = new ArrayList<>();
            try (Scope ignored = span.makeCurrent()) {
                Instant toTime = Instant.now().plusSeconds(60);
                trendSamples = fetchHistoryRecords(defaultSite, trendId, onAndAfterDateTime, toTime);

                if (trendSamples != null) {
                    for (ZaphireTrendSample trendSample : trendSamples) {
                        log.trace("received trendSample: {}", trendSample);
                        addNumberOfTrendSamplesReceived();
                    }
                }

                long size = trendSamples != null ? trendSamples.size() : 0;
                attributes = Attributes.of(stringKey("trendId"), trendId, longKey("trendSamples.size"), size);
                span.addEvent("Fetched trendsamples", attributes);
                log.trace("Found: {} trends from trendId: {}", size, trendId);
            } catch (ZaphireApiException e) {
                throw e;
            } catch (ZaphireCloudConnectorException e) {
                throw e;
            } catch (Exception e) {
                ZaphireCloudConnectorException zce = new ZaphireCloudConnectorException("Failed to fetch trendsamples for trendId " + trendId
                        + ", after date " + onAndAfterDateTime + ". Reason: " + e.getMessage(), e);
                attributes = Attributes.of(stringKey("trendId"), trendId);
                span.recordException(zce, attributes);
                throw zce;
            } finally {
                span.end();
            }

            isHealthy = true;
            updateWhenLastTrendSampleReceived();
            markApiHealthy();
            return new HashSet<>(trendSamples);
        }, "findTrendSamplesByDate");
    }

    @Override
    public Integer subscribePresentValueChange(String subscriptionId, String objectId) {
        log.debug("subscribePresentValueChange is not supported by Zaphire API. subscriptionId: {}, objectId: {}", subscriptionId, objectId);
        return -1;
    }

    // --- Zaphire-specific methods ---

    /**
     * Henter live verdier for flere tags via POST body.
     * POST /site/{site}/Tag/Values
     */
    public List<ZaphireTagValue> findTagValues(String site, List<String> tagNames) {
        return executeWithTokenHandling(() -> {
            try {
                String jsonBody = OBJECT_MAPPER.writeValueAsString(tagNames);
                URI uri = URI.create(apiUri + "site/" + site + "/Tag/Values");

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(uri)
                        .header("Authorization", "Bearer " + accessToken)
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                        .timeout(REQUEST_TIMEOUT)
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                int statusCode = response.statusCode();

                switch (statusCode) {
                    case 200:
                        String body = response.body();
                        log.trace("findTagValues response: {}", body != null && body.length() > 120 ? body.substring(0, 120) : body);
                        List<ZaphireTagValue> tagValues = OBJECT_MAPPER.readValue(body, new TypeReference<List<ZaphireTagValue>>() {});
                        markApiHealthy();
                        return tagValues;
                    case 400:
                        throw new ZaphireApiException("Bad request for findTagValues on site: " + site + ". Body: " + response.body(), statusCode);
                    case 401:
                    case 403:
                        throw new ZaphireApiException("Unauthorized access to findTagValues on site: " + site, statusCode);
                    default:
                        throw new ZaphireApiException("Error in findTagValues on site: " + site + ". Status: " + statusCode + ". Body: " + response.body(), statusCode);
                }
            } catch (ZaphireApiException e) {
                throw e;
            } catch (Exception e) {
                throw new ZaphireApiException("Error in findTagValues: " + e.getMessage(), e);
            }
        }, "findTagValues");
    }

    /**
     * Henter historiske records med eksplisitt site-parameter.
     * GET /site/{site}/Tags/History/Records?name={tagName}&from={from}&to={to}
     */
    public List<ZaphireTrendSample> findHistoryRecords(String site, String tagName, Instant from, Instant to) {
        return executeWithTokenHandling(() -> fetchHistoryRecords(site, tagName, from, to), "findHistoryRecords");
    }

    // --- Internal HTTP methods ---

    private List<ZaphireTagValue> fetchTagValues(String site, String tagName) {
        try {
            URI uri = URI.create(apiUri + "site/" + site + "/Tag/Values?name=" + tagName);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(uri)
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Content-Type", "application/json")
                    .GET()
                    .timeout(REQUEST_TIMEOUT)
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            int statusCode = response.statusCode();

            switch (statusCode) {
                case 200:
                    String body = response.body();
                    log.trace("fetchTagValues response: {}", body != null && body.length() > 120 ? body.substring(0, 120) : body);
                    return OBJECT_MAPPER.readValue(body, new TypeReference<List<ZaphireTagValue>>() {});
                case 401:
                case 403:
                    throw new ZaphireApiException("Unauthorized access to Tag/Values for tag: " + tagName, statusCode);
                case 404:
                    log.debug("Tag not found: {}", tagName);
                    return new ArrayList<>();
                default:
                    throw new ZaphireApiException("Error fetching tag value for tag: " + tagName + ". Status: " + statusCode, statusCode);
            }
        } catch (ZaphireApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ZaphireApiException("Error fetching tag value for tag: " + tagName + ". Reason: " + e.getMessage(), e);
        }
    }

    private List<ZaphireTrendSample> fetchHistoryRecords(String site, String tagName, Instant from, Instant to) {
        try {
            String fromStr = from.toString();
            String toStr = to.toString();
            URI uri = URI.create(apiUri + "site/" + site + "/Tags/History/Records?name=" + tagName + "&from=" + fromStr + "&to=" + toStr);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(uri)
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Content-Type", "application/json")
                    .GET()
                    .timeout(REQUEST_TIMEOUT)
                    .build();

            log.trace("fetchHistoryRecords. tagName: {}. From: {}. To: {}", tagName, fromStr, toStr);
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            int statusCode = response.statusCode();
            String body = response.body();

            switch (statusCode) {
                case 200:
                    log.trace("fetchHistoryRecords response: {}", body != null && body.length() > 120 ? body.substring(0, 120) : body);
                    List<ZaphireTrendSample> samples = OBJECT_MAPPER.readValue(body, new TypeReference<List<ZaphireTrendSample>>() {});
                    return samples != null ? samples : new ArrayList<>();
                case 401:
                case 403:
                    throw new ZaphireApiException("Unauthorized access to History/Records for tag: " + tagName, statusCode);
                case 404:
                    log.debug("Tag not found for history records: {}. Body: {}", tagName, body);
                    throw new ZaphireApiException("Tag not found: " + tagName, statusCode);
                case 422:
                    log.debug("Logging not enabled for tag: {}. Body: {}", tagName, body);
                    throw new ZaphireApiException("Logging not enabled for tag: " + tagName + ". Body: " + body, statusCode);
                default:
                    throw new ZaphireApiException("Error fetching history records for tag: " + tagName + ". Status: " + statusCode + ". Body: " + body, statusCode);
            }
        } catch (ZaphireApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ZaphireApiException("Error fetching history records for tag: " + tagName + ". Reason: " + e.getMessage(), e);
        }
    }

    // --- Token handling and retry ---

    protected <T> T executeWithTokenHandling(Supplier<T> apiCall, String operationName) throws ZaphireApiException {
        try {
            ensureValidToken();
            try {
                return apiCall.get();
            } catch (ZaphireApiException e) {
                if (e.getStatusCode() == 401 || e.getStatusCode() == 403) {
                    log.debug("Received {} during {}, token may be invalid", e.getStatusCode(), operationName);
                    throw e;
                } else if (e.getStatusCode() == 500) {
                    return retryOnServerError(apiCall, operationName, 3);
                } else {
                    throw e;
                }
            }
        } catch (ZaphireApiException e) {
            int effectiveStatusCode = e.getStatusCode();
            if (effectiveStatusCode == 0 && e.getCause() instanceof ZaphireApiException) {
                effectiveStatusCode = ((ZaphireApiException) e.getCause()).getStatusCode();
            }
            if (effectiveStatusCode >= 500) {
                markApiUnhealthy();
                notificationService.sendAlarm(ZAPHIRE_API, ZAPHIRE_API_UNAVAILABLE);
            }
            throw e;
        } catch (Exception e) {
            if (e instanceof ZaphireApiException) {
                throw (ZaphireApiException) e;
            }
            throw new ZaphireApiException("Error in " + operationName + ": " + e.getMessage(), e);
        }
    }

    protected <T> T retryOnServerError(Supplier<T> apiCall, String operationName, int maxRetries) throws ZaphireApiException {
        int retries = 0;
        ZaphireApiException lastException = null;

        while (retries < maxRetries) {
            try {
                long waitTime = (long) Math.pow(2, retries) * 1000;
                log.debug("Retry {}/{} for {} after {}ms", retries + 1, maxRetries, operationName, waitTime);

                try {
                    Thread.sleep(waitTime);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new ZaphireApiException("Retry interrupted", ie);
                }

                return apiCall.get();
            } catch (ZaphireApiException e) {
                lastException = e;
                if (e.getStatusCode() != 500) {
                    throw e;
                }
                retries++;
            }
        }

        throw new ZaphireApiException("Max retries reached for " + operationName, lastException);
    }

    // --- Health management ---

    void setHealthy() {
        this.isHealthy = true;
        log.debug("Zaphire is Healthy");
        TemporaryHealthResource.setHealthy();
    }

    void setUnhealthy() {
        log.warn("Zaphire is Unhealthy");
        this.isHealthy = false;
        TemporaryHealthResource.setUnhealthy();
    }

    private void startPeriodicApiHealthCheck() {
        apiHealthChecker.scheduleWithFixedDelay(() -> {
            try {
                performApiHealthCheck();
            } catch (Exception e) {
                log.error("Error during periodic Zaphire API health check", e);
            }
        }, 30, 60, TimeUnit.SECONDS);
    }

    private void performApiHealthCheck() {
        if (!apiAvailable || consecutiveFailures.get() > 0) {
            log.info("Performing Zaphire API health check. ConsecutiveFailures: {}, API available: {}",
                    consecutiveFailures.get(), apiAvailable);

            if (accessToken != null && defaultSite != null) {
                try {
                    // Use a lightweight GET Tag/Values call as health check
                    URI uri = URI.create(apiUri + "site/" + defaultSite + "/Tag/Values?name=__healthcheck__");
                    HttpRequest request = HttpRequest.newBuilder()
                            .uri(uri)
                            .header("Authorization", "Bearer " + accessToken)
                            .GET()
                            .timeout(Duration.ofSeconds(10))
                            .build();

                    HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                    int statusCode = response.statusCode();
                    if (statusCode == 200 || statusCode == 400) {
                        // 200 = OK, 400 = bad argument but API is reachable
                        markApiHealthy();
                        log.info("Zaphire API health check successful - API recovered");
                    } else if (statusCode == 401 || statusCode == 403) {
                        log.warn("Zaphire API health check: authentication issue ({})", statusCode);
                        // API is reachable but token is invalid
                        apiAvailable = true;
                    } else {
                        markApiUnhealthy();
                    }
                } catch (Exception e) {
                    log.warn("Zaphire API health check failed: {}", e.getMessage());
                    markApiUnhealthy();
                }
            }
        }
    }

    private void markApiHealthy() {
        if (!apiAvailable) {
            log.info("Zaphire API recovered after {} consecutive failures", consecutiveFailures.get());
            notificationService.clearService(ZAPHIRE_API);
        }
        apiAvailable = true;
        lastSuccessfulApiCall = Instant.now();
        consecutiveFailures.set(0);
        setHealthy();
    }

    private void markApiUnhealthy() {
        apiAvailable = false;
        lastFailedApiCall = Instant.now();
        int failures = consecutiveFailures.incrementAndGet();
        log.warn("Zaphire API marked as unhealthy. Consecutive failures: {}", failures);
        setUnhealthy();
    }

    // --- Counters and accessors ---

    void addNumberOfTrendSamplesReceived() {
        if (numberOfTrendSamplesReceived < Long.MAX_VALUE) {
            numberOfTrendSamplesReceived++;
        } else {
            numberOfTrendSamplesReceived = 1;
        }
    }

    protected void updateWhenLastTrendSampleReceived() {
        whenLastTrendSampleReceived = Instant.ofEpochMilli(System.currentTimeMillis());
    }

    public Instant getWhenLastTrendSampleReceived() {
        return whenLastTrendSampleReceived;
    }

    public URI getApiUri() {
        return apiUri;
    }

    public String getDefaultSite() {
        return defaultSite;
    }

    public boolean isApiAvailable() {
        return apiAvailable;
    }

    public int getConsecutiveFailures() {
        return consecutiveFailures.get();
    }

    public Instant getLastSuccessfulApiCall() {
        return lastSuccessfulApiCall;
    }

    public Instant getLastFailedApiCall() {
        return lastFailedApiCall;
    }

    //Used for testing only
    protected void setUserToken(UserToken userToken) {
        this.userToken = userToken;
    }
}
