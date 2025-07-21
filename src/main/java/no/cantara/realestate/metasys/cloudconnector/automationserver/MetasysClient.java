package no.cantara.realestate.metasys.cloudconnector.automationserver;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
import no.cantara.realestate.cloudconnector.notifications.SlackNotificationService;
import no.cantara.realestate.json.RealEstateObjectMapper;
import no.cantara.realestate.metasys.cloudconnector.MetasysCloudConnectorException;
import no.cantara.realestate.metasys.cloudconnector.status.TemporaryHealthResource;
import no.cantara.realestate.observations.PresentValue;
import no.cantara.realestate.security.InvalidTokenException;
import no.cantara.realestate.security.LogonFailedException;
import no.cantara.realestate.security.UserToken;
import no.cantara.realestate.sensors.SensorId;
import org.apache.hc.core5.http.NameValuePair;
import org.apache.hc.core5.http.message.BasicNameValuePair;
import org.apache.hc.core5.net.URIBuilder;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

import static io.opentelemetry.api.common.AttributeKey.longKey;
import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static no.cantara.realestate.mappingtable.Main.getConfigValue;
import static no.cantara.realestate.metasys.cloudconnector.MetasysCloudconnectorApplication.INSTRUMENTATION_SCOPE_NAME_VALUE;
import static no.cantara.realestate.metasys.cloudconnector.automationserver.MetasysApiClientRest.METASYS_SUBSCRIBE_HEADER;
import static no.cantara.realestate.utils.StringUtils.hasValue;
import static org.slf4j.LoggerFactory.getLogger;

/**
 * Singleton klient for Metasys API med automatisk token-fornyelse.
 * Thread-safe implementasjon som unngår for mange login-forespørsler.
 */
public class MetasysClient implements BasClient {
    private static final Logger log = getLogger(MetasysClient.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String BASE_URL = "https://metasys-server-url/api"; // Bytt ut med din Metasys server URL
    private static final Duration TOKEN_REFRESH_MARGIN = Duration.ofMinutes(5);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);

    public static final String METASYS_API = "Metasys";
    public static final String HOST_UNREACHABLE = "HOST_UNREACHABLE";
    public static final String LOGON_FAILED = "Logon to Metasys Api Failed";

    private static MetasysClient instance;

    private final HttpClient httpClient;
    private final String username;
    private final String password;
    private final URI apiUri;
    private final NotificationService notificationService;
    private long numberOfTrendSamplesReceived = 0;
    private Instant whenLastTrendSampleReceived = null;
    private boolean isHealthy = true;
    final Tracer tracer;
    final Meter meter;
    private final RateLimiter rateLimiter;
    private final RateLimiter logonRateLimiter;

    private UserToken userToken;
    private String accessToken;
    private Instant tokenExpiryTime;
    private final ReentrantLock authLock = new ReentrantLock();

    /**
     * Privat konstruktør for singleton-mønsteret.
     */
    private MetasysClient(String username, String password, URI apiUri, NotificationService notificationService) {
        this.username = username;
        this.password = password;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(REQUEST_TIMEOUT)
                .build();
        this.apiUri = apiUri;
        this.notificationService = notificationService;
        tracer = GlobalOpenTelemetry.getTracer(INSTRUMENTATION_SCOPE_NAME_VALUE);
        meter = GlobalOpenTelemetry.getMeter(INSTRUMENTATION_SCOPE_NAME_VALUE);
        RateLimiterConfig config = RateLimiterConfig.custom()
                .limitForPeriod(2)                          // 1 kall per periode
                .limitRefreshPeriod(Duration.ofMillis(200)) // periode på 500 ms
                .timeoutDuration(Duration.ofSeconds(10))    // vent inntil 10 sek for tillatelse
                .build();

        RateLimiterRegistry registry = RateLimiterRegistry.of(config);
        this.rateLimiter = registry.rateLimiter("observationsLimiter");
        RateLimiterConfig logonConfig = RateLimiterConfig.custom()
                .limitForPeriod(1)                          // 1 kall per periode
                .limitRefreshPeriod(Duration.ofMillis(60000)) // max one call pr minute
                .timeoutDuration(Duration.ofSeconds(10))    // vent inntil 10 sek for tillatelse
                .build();

        RateLimiterRegistry logonRegistry = RateLimiterRegistry.of(logonConfig);
        this.logonRateLimiter = logonRegistry.rateLimiter("logonLimiter");

    }

    /**
     * Henter singleton-instansen av MetasysClient.
     * Initialiserer klienten med gitte brukerdetaljer hvis den ikke allerede eksisterer.
     */
    public static synchronized MetasysClient getInstance(String username, String password, URI apiUri, NotificationService notificationService) {
        if (instance == null) {
            instance = new MetasysClient(username, password, apiUri, notificationService);
            instance.login();
        }
        return instance;
    }

    /**
     * Henter singleton-instansen av MetasysClient.
     * Kaster en IllegalStateException hvis klienten ikke er initialisert.
     */
    public static synchronized MetasysClient getInstance() {
        if (instance == null) {
            throw new IllegalStateException("MetasysClient har ikke blitt initialisert. Bruk getInstance(username, password, apiUri, notificationService) først.");
        }
        return instance;
    }

    /**
     * Used only for testing
     */
    static void stopInstance4Testing() {
        log.warn("MetasysClient is reset. Must only be used for testing.");
        instance = null;
    }

    /**
     * Utfører login til Metasys API og lagrer access token.
     * Thread-safe implementasjon som håndterer samtidige forespørsler.
     */
    synchronized void login() throws MetasysApiException {
        URI loginUri = null;
        try {
            ObjectNode requestBody = OBJECT_MAPPER.createObjectNode();
            requestBody.put("username", username);
            requestBody.put("password", password);
            loginUri = URI.create(apiUri + "login");
            log.trace("Logon: {} to {}", username, loginUri);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(loginUri)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody.toString()))
                    .timeout(REQUEST_TIMEOUT)
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            int statusCode = response.statusCode();
            switch (statusCode) {
                case 200:
                    String body = response.body();
                    log.trace("Login Received body: {}", body);
                    MetasysUserToken userToken = RealEstateObjectMapper.getInstance().getObjectMapper().readValue(body, MetasysUserToken.class);
                    log.trace("Converted http body to userToken: {}", userToken);
                    this.userToken = userToken;
                    accessToken = userToken.getAccessToken();
                    tokenExpiryTime = userToken.getExpires();
                    notificationService.clearService("Metasys");
                    log.debug("Metasys login successful, token expires: " + tokenExpiryTime);
                    setHealthy();
                    break;
                default:
                    String msg = "Failed to logon to Metasys at uri: " + loginUri +
                            ". Username: " + username +
                            ". Password: " + password +
                            ". ResponseCode: " + statusCode;
                    LogonFailedException logonFailedException = new LogonFailedException(msg);
                    log.warn("Failed to logon to Metasys. Reason {}", logonFailedException.getMessage());
                    setUnhealthy();
                    notificationService.sendWarning(METASYS_API,LOGON_FAILED);
                    TemporaryHealthResource.addRegisteredError("Failed to logon to Metasys. Reason: " + logonFailedException.getMessage());
                    throw logonFailedException;
            }
        } catch (JsonProcessingException e) {
            notificationService.sendWarning(METASYS_API, "Parsing of AccessToken information failed.");
            String msg = "Failed to login on Metasys at uri: " + loginUri + ", with username: " + username +
                    ". Failure parsing the response.";
            LogonFailedException logonFailedException = new LogonFailedException(msg, e);
            log.warn(msg);
            setUnhealthy();
            TemporaryHealthResource.addRegisteredError(msg + " Reason: " + logonFailedException.getMessage());
            throw logonFailedException;
        } catch (IOException e) {
            notificationService.sendAlarm(METASYS_API, HOST_UNREACHABLE);
            String msg = "Failed to login on Metasys at uri: " + loginUri + ", with username: " + username;
            LogonFailedException logonFailedException = new LogonFailedException(msg, e);
            log.warn(msg);
            setUnhealthy();
            TemporaryHealthResource.addRegisteredError(msg + " Reason: " + logonFailedException.getMessage());
            throw logonFailedException;
        } catch (Exception e) {
            throw new MetasysApiException("Login failed: " + e.getMessage(), e);
        }
    }

    /**
     * Fornyer access token ved å bruke refresh token endepunktet.
     * Thread-safe implementasjon som håndterer samtidige forespørsler.
     */
    synchronized void refreshTokenSilently() throws MetasysApiException {
        String shortenedAccessToken = null;
        URI refreshTokenUri = null;
        if (accessToken == null) {
            log.warn("Access token param is null. Will use UserToken to get access token.");
            accessToken = userToken.getAccessToken();
        }
        try {
            refreshTokenUri = URI.create(apiUri + "refreshToken");
            log.trace("RefreshToken: {} to {}", truncateAccessToken(accessToken), refreshTokenUri);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(refreshTokenUri)
                    .header("Authorization", "Bearer " + accessToken)
                    .GET()
                    .timeout(REQUEST_TIMEOUT)
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                String body = response.body();
                log.trace("RefreshToken Received body: {}", body);
                MetasysUserToken userToken = RealEstateObjectMapper.getInstance().getObjectMapper().readValue(body, MetasysUserToken.class);
                log.trace("RefreshToken userToken: {}", userToken);
                this.userToken = userToken;
                accessToken = userToken.getAccessToken(); //jsonResponse.get("accessToken").asText();
                shortenedAccessToken = truncateUserToken(userToken);
                tokenExpiryTime = userToken.getExpires(); // Instant.now().plusSeconds(expiresIn);

                log.debug("Metasys token refreshed, new expiry: " + tokenExpiryTime);
                setHealthy();
            } else if (response.statusCode() == 401 || response.statusCode() == 403) {
                // Token refresh failed, try login again
                log.info("Metasys token refresh failed, performing full login");
                login();
            } else if (response.statusCode() >= 500 && response.statusCode() < 600) {
                // Server error during token refresh, try full login as fallback
                log.info("Metasys token refresh failed with server error ({}), attempting full login as fallback", response.statusCode());
                login();
            } else {
                String errorMessage = "Metsys token refresh failed with status code: " + response.statusCode();
                log.warn(errorMessage);
                setUnhealthy();
                throw new MetasysApiException(errorMessage, response.statusCode());
            }
        } catch (JsonProcessingException e) {
            notificationService.sendWarning(METASYS_API, "Metasys token refresh. Parsing of AccessToken information failed.");
            String msg = "Metasys token refresh failed on Metasys at uri: " + refreshTokenUri + ", with accessToken: " + shortenedAccessToken +
                    ". Failure parsing the response.";
            LogonFailedException logonFailedException = new LogonFailedException(msg, e);
            log.warn(msg);
            setUnhealthy();
            TemporaryHealthResource.addRegisteredError(msg + " Reason: " + logonFailedException.getMessage());
            throw logonFailedException;
        } catch (IOException e) {
            notificationService.sendAlarm(METASYS_API,HOST_UNREACHABLE);
            String msg = "Metasys token refresh failed on Metasys. Host unreachable at uri: " + refreshTokenUri + ", with accessToken: " + shortenedAccessToken;
            LogonFailedException logonFailedException = new LogonFailedException(msg, e);
            log.warn(msg);
            setUnhealthy();
            TemporaryHealthResource.addRegisteredError(msg + " Reason: " + logonFailedException.getMessage());
            throw logonFailedException;
        } catch (Exception e) {
            if (!(e instanceof MetasysApiException)) {
                throw new MetasysApiException("Metasys token refresh failed. Generic error: " + e.getMessage(), e);
            } else {
                throw (MetasysApiException) e;
            }
        }
    }

    /**
     * Sikrer at vi har et gyldig token før API-forespørsler utføres.
     * Håndterer forespørsler fra flere tråder ved hjelp av en lås.
     */
    void ensureValidToken() throws MetasysApiException {
        if (accessToken == null) {
            // Vi trenger å logge inn for første gang
            authLock.lock();
            try {
                if (accessToken == null) {
                    log.trace("Access token is null, logging in");
                    login();
                }
            } finally {
                authLock.unlock();
            }
        } else if (tokenExpiryTime.minus(TOKEN_REFRESH_MARGIN).isBefore(Instant.now())) {
            // Token nærmer seg utløp, prøv å fornye
            authLock.lock();
            try {
                boolean permission = logonRateLimiter.acquirePermission();
                if (!permission) {
                    log.debug("Rate limit exceeded for token refresh");
                } else {
                    if (tokenExpiryTime.minus(TOKEN_REFRESH_MARGIN).isBefore(Instant.now())) {
                        log.trace("Token is about to expire, refreshing silently");
                        refreshTokenSilently();
                    }
                }
            } finally {
                authLock.unlock();
            }
        }
    }

    /**
     * Generisk metode for å utføre API-forespørsler med automatisk tokenhåndtering og retry.
     * Håndterer 401/403 ved å fornye token, samt 500 med retry-logikk.
     */
    protected <T> T executeWithTokenHandling(Supplier<T> apiCall, String operationName) throws MetasysApiException {
        try {
            ensureValidToken();

            try {
                return apiCall.get();
            } catch (MetasysApiException e) {
                if (e.getStatusCode() == 401 || e.getStatusCode() == 403) {
                    // Prøv å fornye token og gjenta forespørselen en gang
                    log.debug("Received 401/403 during " + operationName + ", refreshing token and retrying");

                    authLock.lock();
                    try {
                        refreshTokenSilently();
                    } finally {
                        authLock.unlock();
                    }

                    return apiCall.get();
                } else if (e.getStatusCode() == 500) {
                    // Server error - vent litt og prøv igjen (maks 3 forsøk)
                    return retryOnServerError(apiCall, operationName, 3);
                } else {
                    throw e;
                }
            }
        } catch (Exception e) {
            if (e instanceof MetasysApiException) {
                throw (MetasysApiException) e;
            } else {
                throw new MetasysApiException("Error in " + operationName + ": " + e.getMessage(), e);
            }
        }
    }

    /**
     * Hjelpemetode for å håndtere retry ved server-feil (500).
     */
    protected <T> T retryOnServerError(Supplier<T> apiCall, String operationName, int maxRetries) throws MetasysApiException {
        int retries = 0;
        MetasysApiException lastException = null;

        while (retries < maxRetries) {
            try {
                // Eksponentiell backoff
                long waitTime = (long) Math.pow(2, retries) * 1000;
                log.debug("Retry " + (retries + 1) + "/" + maxRetries + " for " + operationName +
                        " after " + waitTime + "ms");

                try {
                    Thread.sleep(waitTime);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new MetasysApiException("Retry interrupted", ie);
                }

                return apiCall.get();
            } catch (MetasysApiException e) {
                lastException = e;
                if (e.getStatusCode() != 500) {
                    throw e;  // Bare retry på 500 feil
                }
                retries++;
            }
        }

        throw new MetasysApiException("Max retries reached for " + operationName, lastException);
    }

    /**
     * Henter trend-verdier fra Metasys API.
     */
    public String getTrendedValues(String objectId, String startTime, String endTime) throws MetasysApiException {
        return executeWithTokenHandling(() -> {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(BASE_URL + "/objects/" + objectId + "/trendedValues?startTime=" +
                                startTime + "&endTime=" + endTime))
                        .header("Authorization", "Bearer " + accessToken)
                        .GET()
                        .timeout(REQUEST_TIMEOUT)
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    return response.body();
                } else if (response.statusCode() == 401 || response.statusCode() == 403) {
                    throw new MetasysApiException("Unauthorized access to getTrendedValues", response.statusCode());
                } else if (response.statusCode() == 500) {
                    throw new MetasysApiException("Server error in getTrendedValues", response.statusCode());
                } else {
                    throw new MetasysApiException("Error in getTrendedValues: " + response.statusCode(), response.statusCode());
                }
            } catch (Exception e) {
                if (e instanceof MetasysApiException) {
                    throw (MetasysApiException) e;
                } else {
                    throw new MetasysApiException("Error in getTrendedValues: " + e.getMessage(), e);
                }
            }
        }, "getTrendedValues");
    }

    /**
     * Utfører subscription til present value changes.
     */
    public String subscribeToPresentValueChanged(String objectId) throws MetasysApiException {
        return executeWithTokenHandling(() -> {
            try {
                ObjectNode requestBody = OBJECT_MAPPER.createObjectNode();
                requestBody.put("objectId", objectId);

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(BASE_URL + "/subscriptions/presentValueChanged"))
                        .header("Authorization", "Bearer " + accessToken)
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(requestBody.toString()))
                        .timeout(REQUEST_TIMEOUT)
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200 || response.statusCode() == 201) {
                    return response.body();
                } else if (response.statusCode() == 401 || response.statusCode() == 403) {
                    throw new MetasysApiException("Unauthorized access to subscribePresentValueChanged", response.statusCode());
                } else if (response.statusCode() == 500) {
                    throw new MetasysApiException("Server error in subscribePresentValueChanged", response.statusCode());
                } else {
                    throw new MetasysApiException("Error in subscribePresentValueChanged: " + response.statusCode(),
                            response.statusCode());
                }
            } catch (Exception e) {
                if (e instanceof MetasysApiException) {
                    throw (MetasysApiException) e;
                } else {
                    throw new MetasysApiException("Error in subscribePresentValueChanged: " + e.getMessage(), e);
                }
            }
        }, "subscribePresentValueChanged");
    }

    /**
     * Utfører asynkrone API-kall med samme token-fornyelseslogikk.
     */
    public CompletableFuture<String> getTrendedValuesAsync(String objectId, String startTime, String endTime) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return getTrendedValues(objectId, startTime, endTime);
            } catch (MetasysApiException e) {
                throw new RuntimeException(e);
            }
        });
    }

    /**
     * Utfører asynkrone subscription-kall med samme token-fornyelseslogikk.
     */
    public CompletableFuture<String> subscribeToPresentValueChangedAsync(String objectId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return subscribeToPresentValueChanged(objectId);
            } catch (MetasysApiException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Override
    public UserToken refreshToken() throws LogonFailedException {
        log.warn("refreshToken() must not be used in MetasysClient");
        throw new RealestateCloudconnectorException("Must not be used in MetasysClient");
    }


    @Override
    public Set<MetasysTrendSample> findTrendSamplesByDate(String objectId, int take, int skip, Instant onAndAfterDateTime) throws URISyntaxException, InvalidTokenException {
        return executeWithTokenHandling(() -> {
            if (onAndAfterDateTime == null) {
                throw new IllegalArgumentException("onAndAfterDateTime cannot be null");
            }

            Span span = tracer.spanBuilder("findTrendSamplesByDate").setSpanKind(SpanKind.CLIENT).startSpan();
            Attributes attributes = Attributes.of(stringKey("objectId"), objectId);
            boolean permitted = rateLimiter.acquirePermission();
            if (!permitted) {
                log.debug("Rate limit exceeded for findTrendSamplesByDate. objectId: {}, onAndAfterDateTime: {}",
                        objectId, onAndAfterDateTime);
                throw new RealestateCloudconnectorException("Rate limit exceeded. objectId: " + objectId, StatusType.RETRY_MAY_FIX_ISSUE);
            }

            List<MetasysTrendSample> trendSamples = new ArrayList<>();
            try (Scope ignored = span.makeCurrent()) {

                boolean permission = rateLimiter.acquirePermission();  //getPermission(Duration.ofSeconds(10));
                if (!permission) {
                    span.addEvent("RateLimitExceded-trendSamples", attributes);
                    span.end();
                    throw new RealestateCloudconnectorException("RateLimit exceeded", StatusType.RETRY_MAY_FIX_ISSUE);
                }

                String startTime = onAndAfterDateTime.toString();
                int page = 1;
                int pageSize = 1000;
                String endTime = Instant.now().plusSeconds(60).toString();

                // GET Query Parameters
                List<NameValuePair> nvps = new ArrayList<>();
                nvps.add(new BasicNameValuePair("startTime", startTime));
                nvps.add(new BasicNameValuePair("endTime", endTime));
                nvps.add(new BasicNameValuePair("page", "1"));
                nvps.add(new BasicNameValuePair("pageSize", "1000"));
                nvps.add(new BasicNameValuePair("skip", "0"));
                URI queryUri = new URIBuilder(apiUri + "objects/" + objectId + "/trendedAttributes/presentValue/samples")
                        .addParameters(nvps)
                        .build();
                final HttpRequest request = HttpRequest.newBuilder()
                        .uri(queryUri)
                        .header("Authorization", "Bearer " + accessToken)
                        .header("Content-Type", "application/json")
                        .GET()
                        .timeout(REQUEST_TIMEOUT)
                        .build();
                log.trace("findTrendSamplesByDate. trendId: {}. From date: {}. To date: {}. Page: {}. PageSize: {}. Take: {}. Skip: {}",
                        objectId, onAndAfterDateTime, endTime, page, pageSize, take, skip);
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                int httpCode = response.statusCode();
                String body = response.body();
                String reason = null;
                attributes = Attributes.of(stringKey("objectId"), objectId, longKey("http.status_code"), Long.valueOf(httpCode));

                switch (httpCode) {
                    case 200:
                        String bodyLog = body != null && body.length() > 120 ? body.substring(0,120): body;
                        log.trace("Received body: {}", bodyLog);
                        MetasysTrendSampleResult trendSampleResult = TrendSamplesMapper.mapFromJson(body);
                        log.trace("Found: {} trends from trendId: {}", trendSampleResult.getTotal(), objectId);
                        trendSamples = trendSampleResult.getItems();
                        if (trendSamples != null) {
                            for (MetasysTrendSample trendSample : trendSamples) {
                                trendSample.setTrendId(objectId);
                                log.trace("received trendSample: {}", trendSample);
                                addNumberOfTrendSamplesReceived();
                            }
                        }
                        Long size = 0L;
                        if (trendSamples != null) {
                            size = (long) trendSamples.size();
                        }
                        attributes = Attributes.of(stringKey("objectId"), objectId, longKey("trendSamples.size"), size);
                        span.addEvent("Fetched trendsamples", attributes);
                        break;
                    case 401:
                        reason = "Unauthorized";
                        log.debug("Unauthorized trying to fetch trend samples for objectId: {}. Status: {}. Reason: {}", objectId, httpCode, reason);
                        span.addEvent("Unauthorized", attributes);
                    case 403:
                        reason = "Forbidden";
                        log.debug("AccessToken not valid. Not able to get trendsamples for objectId: {}. Status: {}. Reason: {}", objectId, httpCode, reason);
                        span.addEvent("AccessToken not valid.", attributes);
                        throw new MetasysApiException("AccessToken not valid. Not able to get trendsamples for objectId: " + objectId
                                + ". Status: " + httpCode + ". Reason: " + reason, 403);
                    case 404:
                        reason = "Not Found";
                        log.debug("Failed to fetch trendsamples for objectId: {}. Status: {}. Reason: {}", objectId, httpCode, reason);
                        span.addEvent("Failed to fetch trendsamples", attributes);
                        break;
                    case 500:
                        reason = "Metasys API: Internal Server Error";
                        log.warn("Metasys Error while trying to fetch trendsamples for objectId: {}. Status: {}. Reason: {}", objectId, httpCode, reason);
                        span.addEvent("Metasys Error trying to fetch trendsamples", attributes);
                        throw new MetasysCloudConnectorException("Metasys Error trying to fetch trendsamples for objectId " + objectId + ". Status: " + httpCode
                                + ". Reason: " + reason + ". Body: " + body);
                    default:
                        log.debug("Failed to fetch trendsamples for objectId: {}. Status: {}. Reason: {}, Body: {}", objectId, httpCode, reason, body);
                        span.addEvent("Failed to fetch trendsamples", attributes);
                        throw new MetasysCloudConnectorException("Failed to fetch trendsamples for objectId " + objectId + ". Status: " + httpCode
                                + ". Reason: " + reason + ". Body: " + body);
                }
            } catch (MetasysApiException mae) {
                throw mae;
            } catch (MetasysCloudConnectorException e) {
                throw e;
            } catch (Exception e) {
                MetasysCloudConnectorException mce = new MetasysCloudConnectorException("Failed to fetch trendsamples for objectId " + objectId
                        + ", after date " + onAndAfterDateTime + ". Reason: " + e.getMessage(), e);
                attributes = Attributes.of(stringKey("objectId"), objectId);
                span.recordException(mce, attributes);
                log.debug("Failed to fetch trendsamples for objectId: {}. Reason: {}", objectId, e.getMessage());
                span.recordException(mce);
                throw mce;
            } catch (Throwable e) {
                MetasysCloudConnectorException mce = new MetasysCloudConnectorException("Failed to fetch trendsamples for objectId " + objectId
                        + ", after date " + onAndAfterDateTime + ". Reason: " + e.getMessage(), e);
                attributes = Attributes.of(stringKey("objectId"), objectId);
                span.recordException(mce, attributes);
                log.debug("Fetch trendsamples threw an exception. objectId: {}. Reason: {}", objectId, e.getMessage());
                span.recordException(mce);
                throw mce;
            } finally {
                span.end();
            }

            isHealthy = true;
            updateWhenLastTrendSampleReceived();
            return new HashSet<>(trendSamples);
        }, "findTrendSamplesByDate");
    }


    @Override
    public PresentValue findPresentValue(SensorId sensorId) throws URISyntaxException, LogonFailedException {
        return null;
    }

    @Override
    public Integer subscribePresentValueChange(String subscriptionId, String objectId) throws LogonFailedException {
        if (!hasValue(objectId)) {
            throw new IllegalArgumentException("ObjectId must be provided");
        }
        final String validSubscriptionId;
        if (hasValue(subscriptionId)) {
            validSubscriptionId = subscriptionId;
        } else {
            validSubscriptionId = "not-set";
        }
        return executeWithTokenHandling(() -> {

            Attributes attributes = Attributes.of(stringKey("objectId"), objectId);

            Span span = tracer.spanBuilder("subscribePresentValueChange").setSpanKind(SpanKind.CLIENT).startSpan();
            Integer statusCode = null;
            try (Scope ignored = span.makeCurrent()) {

                boolean permission = rateLimiter.acquirePermission();  //getPermission(Duration.ofSeconds(10));
                if (!permission) {
                    throw new RealestateCloudconnectorException("RateLimit exceeded - subscribePresentValueChange", StatusType.RETRY_MAY_FIX_ISSUE);
                }
                List<NameValuePair> queryParams = new ArrayList<>();
                queryParams.add(new BasicNameValuePair("includeSchema", "false"));
                URI subscribeUri = new URIBuilder(apiUri + "objects/" + objectId + "/attributes/presentValue")
                        .addParameters(queryParams)
                        .build();
                final HttpRequest request = HttpRequest.newBuilder()
                        .uri(subscribeUri)
                        .header("Authorization", "Bearer " + accessToken)
                        .header("Content-Type", "application/json")
                        .header(METASYS_SUBSCRIBE_HEADER, validSubscriptionId)
                        .GET()
                        .timeout(REQUEST_TIMEOUT)
                        .build();
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                statusCode = response.statusCode();
                String body = response.body();
                attributes = Attributes.of(stringKey("objectId"), objectId, longKey("http.status_code"), Long.valueOf(statusCode));

                switch (statusCode) {
                    case 200:
                    case 202:
                        log.trace("Subscribing ok for objectId: {}", objectId);
                        span.addEvent("Subscribed PresentValueChange.", attributes);
                        break;
                    default:
                        span.addEvent("Failed Subscription PresentValueChange.", attributes);
                        log.trace("Could not subscribe to subscription {} for objectId {} using URL: {}. Status: {}. Body text: {}", subscriptionId, objectId, subscribeUri, statusCode, body);
                }
            } catch (Exception e) {
                MetasysCloudConnectorException mce = new MetasysCloudConnectorException("Failed to subscribe to present value change for objectId " + objectId
                        + ". Reason: " + e.getMessage(), e);
//                e.printStackTrace();
                attributes = Attributes.of(stringKey("objectId"), objectId);
                span.recordException(mce, attributes);
                log.debug("Failed to subscribe to present value change for objectId: {}. Reason: {}", objectId, e.getMessage());
                span.recordException(mce);
                throw mce;
            } finally {
                span.end();
            }
            return statusCode;
        }, "subscribePresentValueChange");
    }

    @Override
    public void logon() throws LogonFailedException {
        log.warn("logon() must not be used in MetasysClient");
        throw new RealestateCloudconnectorException("Must not be used in MetasysClient");
    }

    @Override
    public boolean isLoggedIn() {
        boolean isLoggedIn = false;
        if (userToken != null) {
            if (userToken.getExpires().isAfter(Instant.now())) {
                isLoggedIn = true;
            } else {
                log.debug("UserToken expired: {}", truncateUserToken(userToken));
            }
        }
        return isLoggedIn;
    }

    @Override
    public String getName() {
        return "MetasysApiClient";
    }

    @Override
    public boolean isHealthy() {
        return isHealthy;
    }

    void setHealthy() {
        this.isHealthy = true;
        log.debug("Metasys is Healthy");
        TemporaryHealthResource.setHealthy();
    }

    void setUnhealthy() {
        log.warn("Metasys is Unhealthy");
        this.isHealthy = false;
        TemporaryHealthResource.setUnhealthy();
    }


    @Override
    public long getNumberOfTrendSamplesReceived() {
        return numberOfTrendSamplesReceived;
    }

    void addNumberOfTrendSamplesReceived() {
        if (numberOfTrendSamplesReceived < Long.MAX_VALUE) {
            numberOfTrendSamplesReceived++;
        } else {
            numberOfTrendSamplesReceived = 1;
        }
    }

    @Override
    public UserToken getUserToken() {
        ensureValidToken();
        return userToken;
    }

    public URI getApiUri() {
        return apiUri;
    }

    protected void updateWhenLastTrendSampleReceived() {
        whenLastTrendSampleReceived = Instant.ofEpochMilli(System.currentTimeMillis());
    }

    public Instant getWhenLastTrendSampleReceived() {
        return whenLastTrendSampleReceived;
    }

    @Nullable
    private static String truncateUserToken(UserToken userToken) {
        String shortenedAccessToken = null;
        if (userToken != null) {
            String accessToken = userToken.getAccessToken();
            shortenedAccessToken = truncateAccessToken(accessToken);
            shortenedAccessToken = shortenedAccessToken + " expires: " + userToken.getExpires();
        }
        return shortenedAccessToken;
    }
    public static String truncateAccessToken(String accessToken) {
        String shortenedAccessToken = null;
        if (accessToken != null) {
            if (accessToken != null && accessToken.length() > 11) {
                shortenedAccessToken = accessToken.substring(0, 10) + "...";
            } else {
                shortenedAccessToken = accessToken;
            }
        }
        return shortenedAccessToken;
    }

    public static void main(String[] args) throws URISyntaxException, LogonFailedException {

        String trendId = getConfigValue("trend.id");
        String apiUrl = getConfigValue("sd.api.url");
        String username = getConfigValue("sd.api.username");
        String password = getConfigValue("sd.api.password");
        URI apiUri = new URI(apiUrl);

        MetasysClient apiClient = MetasysClient.getInstance(username, password, apiUri, new SlackNotificationService());
//        String bearerToken = apiClient.findAccessToken();
        Set<MetasysTrendSample> trends = apiClient.findTrendSamplesByDate(trendId, 10, 0, null);
        for (MetasysTrendSample trend : trends) {
            if (trend != null) {
                log.info("Trend id={}, value={}, valid={}", trend.getTrendId(), trend.getValue(), trend.isValid());
            } else {
                log.info("Trend is null");
            }
        }
    }

}
