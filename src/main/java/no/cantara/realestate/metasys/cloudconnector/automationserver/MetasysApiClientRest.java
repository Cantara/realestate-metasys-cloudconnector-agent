package no.cantara.realestate.metasys.cloudconnector.automationserver;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import jakarta.ws.rs.core.HttpHeaders;
import no.cantara.realestate.automationserver.BasClient;
import no.cantara.realestate.json.RealEstateObjectMapper;
import no.cantara.realestate.metasys.cloudconnector.MetasysCloudConnectorException;
import no.cantara.realestate.metasys.cloudconnector.notifications.NotificationService;
import no.cantara.realestate.metasys.cloudconnector.notifications.SlackNotificationService;
import no.cantara.realestate.metasys.cloudconnector.status.TemporaryHealthResource;
import no.cantara.realestate.observations.PresentValue;
import no.cantara.realestate.security.InvalidTokenException;
import no.cantara.realestate.security.LogonFailedException;
import no.cantara.realestate.security.UserToken;
import no.cantara.realestate.sensors.SensorId;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.NameValuePair;
import org.apache.hc.core5.http.ParseException;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.message.BasicNameValuePair;
import org.apache.hc.core5.net.URIBuilder;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static io.opentelemetry.api.common.AttributeKey.longKey;
import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static no.cantara.realestate.mappingtable.Main.getConfigValue;
import static no.cantara.realestate.metasys.cloudconnector.MetasysCloudconnectorApplication.INSTRUMENTATION_SCOPE_NAME_VALUE;
import static no.cantara.realestate.metasys.cloudconnector.utils.UrlEncoder.urlEncode;
import static org.slf4j.LoggerFactory.getLogger;

public class MetasysApiClientRest implements BasClient {
    private static final Logger log = getLogger(MetasysApiClientRest.class);
    private static final String METASYS_SUBSCRIBE_HEADER = "METASYS-SUBSCRIBE";
    private final URI apiUri;

    //FIXME Implement Client https://github.com/Cantara/stingray/blob/main/samples/greeter/src/main/java/no/cantara/stingray/sample/greeter/HttpRandomizerClient.java
    public static final String METASYS_API = "Metasys";
    public static final String HOST_UNREACHABLE = "HOST_UNREACHABLE";
    public static final String LOGON_FAILED = "Logon to Metasys Api Failed";
    public static final String SERVICE_FAILED = "Metasys Api is failing.";
    public static final String UNKNOWN_HOST = "UNKNOWN_HOST";

    private static final String LATEST_BY_DATE = "SampleDateDescending";
    private final NotificationService notificationService;
    private MetasysUserToken userToken = null;
    private long numberOfTrendSamplesReceived = 0;
    private Instant whenLastTrendSampleReceived = null;
    private boolean isHealthy = true;
    final Tracer tracer;
    final Meter meter;

    public MetasysApiClientRest(URI apiUri, NotificationService notificationService) {
        this.apiUri = apiUri;
        this.notificationService = notificationService;
        tracer = GlobalOpenTelemetry.getTracer(INSTRUMENTATION_SCOPE_NAME_VALUE);
        meter = GlobalOpenTelemetry.getMeter(INSTRUMENTATION_SCOPE_NAME_VALUE);
    }

    public static void main(String[] args) throws URISyntaxException, LogonFailedException {

        String trendId = getConfigValue("trend.id");
        String apiUrl = getConfigValue("sd.api.url");
        URI apiUri = new URI(apiUrl);

        MetasysApiClientRest apiClient = new MetasysApiClientRest(apiUri, new SlackNotificationService());
//        String bearerToken = apiClient.findAccessToken();
        Set<MetasysTrendSample> trends = apiClient.findTrendSamplesByDate(trendId, 10, 0,null);
        for (MetasysTrendSample trend : trends) {
            if (trend != null) {
                log.info("Trend id={}, value={}, valid={}", trend.getTrendId(), trend.getValue(), trend.isValid());
            } else {
                log.info("Trend is null");
            }
        }
    }

//    @Override
//    public Set<MetasysTrendSample> findTrendSamples(String bearerToken, String trendId) throws URISyntaxException {
//        Span span = tracer.spanBuilder("findTrendSamples").setSpanKind(SpanKind.CLIENT).startSpan();
//        String apiUrl = getConfigValue("sd.api.url"); //getConfigProperty("sd.api.url");
//
//        Set<MetasysTrendSample> trendSamples;
//
//        try (Scope ignored = span.makeCurrent()) {
//            URI apiUri = new URI(apiUrl);
//            TrendSampleService trendSampleService = RestClientBuilder.newBuilder()
//                    .baseUri(apiUri)
//                    .build(TrendSampleService.class);
//            trendSamples =  trendSampleService.findTrendSamples("Bearer " + bearerToken, trendId.toString());
//        } catch (URISyntaxException e) {
//            RealEstateException re = new RealEstateException("Failed to fetch trendsamples for trendId " + trendId
//                    + ". URI to BAS/SD server failed: " + apiUrl, e);
//            log.warn(re.getMessage(), re);
//            span.recordException(re);
//            span.addEvent("Failed to fetch trendsamples");
//            throw e;
//        } catch (Exception e) {
//            RealEstateException re = new RealEstateException("Failed to fetch trendsamples for trendId " + trendId
//                    + ". Reason: " + e.getMessage(), e);
//            span.recordException(re);
//            log.debug("Failed to fetch trendsamples for trendId: {}. Reason: {}", trendId, e.getMessage());
//            span.addEvent("Failed to fetch trendsamples");
//            throw re;
//        } finally {
//            span.end();
//        }
//        updateWhenLastTrendSampleReceived();
//        return trendSamples;
//    }
//
//    @Override
//    public Set<MetasysTrendSample> findTrendSamples(String trendId, int take, int skip) throws URISyntaxException, LogonFailedException {
//        Span span = tracer.spanBuilder("findTrendSamplesPaged").setSpanKind(SpanKind.CLIENT).startSpan();
//        Set<MetasysTrendSample> trendSamples;
//        String apiUrl = getConfigValue("sd.api.url");
//        try (Scope ignored = span.makeCurrent()) {
//            String prefixedUrlEncodedTrendId = encodeAndPrefix(trendId);
//            String bearerToken = findAccessToken();
//            URI apiUri = new URI(apiUrl);
//            TrendSampleService trendSampleService = RestClientBuilder.newBuilder()
//                    .baseUri(apiUri)
//                    .build(TrendSampleService.class);
//            trendSamples = trendSampleService.findTrendSamples("Bearer " + bearerToken, prefixedUrlEncodedTrendId, take, skip);
//        } catch (URISyntaxException e) {
//            RealEstateException re = new RealEstateException("Failed to fetch trendsamples for trendId " + trendId
//                    + ". URI to BAS/SD server failed: " + apiUrl, e);
//            log.warn(re.getMessage(), re);
//            span.recordException(re);
//            span.addEvent("Failed to fetch trendsamples");
//            throw e;
//        } catch (Exception e) {
//            RealEstateException re = new RealEstateException("Failed to fetch trendsamples for trendId " + trendId
//                    + ". Reason: " + e.getMessage(), e);
//            span.recordException(re);
//            log.debug("Failed to fetch trendsamples for trendId: {}. Reason: {}", trendId, e.getMessage());
//            span.addEvent("Failed to fetch trendsamples");
//            throw re;
//        } finally {
//            span.end();
//        }
//        updateWhenLastTrendSampleReceived();
//        return trendSamples;
//    }

    @Override
    public Set<MetasysTrendSample> findTrendSamplesByDate(String objectId, int take, int skip, Instant onAndAfterDateTime) throws URISyntaxException, InvalidTokenException {
        if (onAndAfterDateTime == null) {
            throw new IllegalArgumentException("onAndAfterDateTime cannot be null");
        }
        Span span = tracer.spanBuilder("findTrendSamplesByDate").setSpanKind(SpanKind.CLIENT).startSpan();
        String apiUrl = getConfigValue("sd.api.url");
//        String prefixedUrlEncodedTrendId = encodeAndPrefix(objectId);
        String bearerToken = findAccessToken();
        URI samplesUri = new URI(apiUrl + "objects/" + objectId+"/trendedAttributes/presentValue/samples");
        CloseableHttpClient httpClient = HttpClients.createDefault();
        HttpGet request = null;
        List<MetasysTrendSample> trendSamples = new ArrayList<>();
        try (Scope ignored = span.makeCurrent()) {

            String startTime = onAndAfterDateTime.toString();
            int page=1;
            int pageSize=1000;
            String endTime = Instant.now().plusSeconds(60).toString();

//        MetasysTrendSampleResult trendSampleResult = trendSampleService.findTrendSamplesByDate("Bearer " + bearerToken, prefixedUrlEncodedTrendId, pageSize, page, startTime, endTime);
            log.trace("findTrendSamplesByDate. trendId: {}. From date: {}. To date: {}. Page: {}. PageSize: {}. Take: {}. Skip: {}",
                    objectId, onAndAfterDateTime, endTime, page, pageSize, take, skip);
            List<NameValuePair> nvps = new ArrayList<>();
            // GET Query Parameters
            nvps.add(new BasicNameValuePair("startTime", startTime));
            nvps.add(new BasicNameValuePair("endTime", endTime));
            nvps.add(new BasicNameValuePair("page", "1"));
            nvps.add(new BasicNameValuePair("pageSize", "1000"));
            nvps.add(new BasicNameValuePair("skip", "0"));

            URI uri = new URIBuilder(samplesUri)
                    .addParameters(nvps)
                    .build();
            request = new HttpGet(uri);
            request.addHeader(HttpHeaders.CONTENT_TYPE, "application/json");
            request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + bearerToken);

            try (CloseableHttpResponse response = httpClient.execute(request)) {
                int httpCode = response.getCode();
                HttpEntity entity = null;
                String reason = null;
                String body = "";
                Attributes attributes = null;
                switch (httpCode) {
                    case 200:
                        entity = response.getEntity();
                        if (entity != null) {
                            body = EntityUtils.toString(entity);
                            log.trace("Received body: {}", body);
                            MetasysTrendSampleResult trendSampleResult = TrendSamplesMapper.mapFromJson(body);
                            log.trace("Found: {} trends from trendId: {}", trendSampleResult.getTotal(), objectId);
                            trendSamples = trendSampleResult.getItems();
                            if (trendSamples != null) {
                                for (MetasysTrendSample trendSample : trendSamples) {
                                    trendSample.setTrendId(objectId);
                                    log.trace("imported trendSample: {}", trendSample);
                                }
                            }
                            Long size = 0L;
                            if (trendSamples != null) {
                                size = (long) trendSamples.size();
                            }
                            attributes = Attributes.of(stringKey("objectId"), objectId, longKey("trendSamples.size"), size);
                            span.addEvent("Fetched trendsamples", attributes);
                        }
                        break;
                    case 401:
                    case 403:
                        reason = response.getReasonPhrase();
                        log.debug("AccessToken not valid. Not able to get trendsamples for objectId: {}. Status: {}. Reason: {}", objectId, httpCode, reason);

                        attributes = Attributes.of(stringKey("objectId"), objectId,
                                stringKey("http.status_code"), Integer.valueOf(httpCode).toString(),
                                stringKey("http.reason"), reason);
                        span.addEvent("AccessToken not valid.", attributes);
                        invalidateUserToken();
                        throw new InvalidTokenException(reason);
                    case 404:
                        reason = response.getReasonPhrase();
                        log.debug("Failed to fetch trendsamples for objectId: {}. Status: {}. Reason: {}", objectId, httpCode, reason);

                        attributes = Attributes.of(stringKey("objectId"), objectId,
                                stringKey("http.status_code"), Integer.valueOf(httpCode).toString(),
                                stringKey("http.reason"), reason);
                        span.addEvent("Failed to fetch trendsamples", attributes);
                        break;
                    case 500:
                        reason = response.getReasonPhrase();
                        body = EntityUtils.toString(response.getEntity());
                        log.warn("Metasys Error while trying to fetch trendsamples for objectId: {}. Status: {}. Reason: {}", objectId, httpCode, reason);

                        attributes = Attributes.of(stringKey("objectId"), objectId,
                                stringKey("http.status_code"), Integer.valueOf(httpCode).toString(),
                                stringKey("http.reason"), reason);
                        span.addEvent("Metasys Error trying to fetch trendsamples", attributes);
                        throw new MetasysCloudConnectorException("Metasys Error trying to fetch trendsamples for objectId " + objectId + ". Status: " + httpCode
                                + ". Reason: " + reason + ". Body: " + body);
                    default:
                        reason = response.getReasonPhrase();
                        try {
                            entity = response.getEntity();
                            if (entity != null) {
                                body = EntityUtils.toString(entity);
                            }
                        } catch (Exception e) {
                            span.recordException(e);
                            //log.warn("Failed to read body from response. Reason: {}", e.getMessage());
                        }
                        log.debug("Failed to fetch trendsamples for objectId: {}. Status: {}. Reason: {}, Body: {}", objectId, httpCode, reason, body);

                        attributes = Attributes.of(stringKey("objectId"), objectId,
                                stringKey("http.status_code"), Integer.valueOf(httpCode).toString(),
                                stringKey("http.reason"), reason,
                                stringKey("http.body"), body);
                        span.addEvent("Failed to fetch trendsamples", attributes);
                        throw new MetasysCloudConnectorException("Failed to fetch trendsamples for objectId " + objectId + ". Status: " + httpCode
                                + ". Reason: " + reason + ". Body: " + body);
                }
            } catch (Exception e) {
                setUnhealthy();
                MetasysCloudConnectorException mce =  new MetasysCloudConnectorException("Failed to fetch trendsamples for objectId " + objectId
                        + ", after date "+ onAndAfterDateTime + ". Reason: " + e.getMessage(), e);
                Attributes attributes = Attributes.of(stringKey("objectId"), objectId);
                span.recordException(mce, attributes);
                log.debug("Failed to fetch trendsamples for objectId: {}. Reason: {}", objectId, e.getMessage());
                throw mce;
            }
        } catch (Exception e) {
            setUnhealthy();
            MetasysCloudConnectorException mce = new MetasysCloudConnectorException("Failed to fetch trendsamples for objectId " + objectId
                    + ", after date "+ onAndAfterDateTime + ". Reason: " + e.getMessage(), e);
            span.recordException(mce);
            log.debug("Failed to fetch trendsamples for objectId: {}. Reason: {}", objectId, e.getMessage());
            throw mce;
        }  finally {
            span.end();
        }

        /*
        String startTime = onAndAfterDateTime.toString();
        //FIXME make dynamic
        int page=1;
        int pageSize=1000;
        String endTime = Instant.now().plusSeconds(60).toString();



//        MetasysTrendSampleResult trendSampleResult = trendSampleService.findTrendSamplesByDate("Bearer " + bearerToken, prefixedUrlEncodedTrendId, pageSize, page, startTime, endTime);
        log.trace("findTrendSamplesByDate. trendId: {}. From date: {}. To date: {}. Page: {}. PageSize: {}. Take: {}. Skip: {}",
                objectId, onAndAfterDateTime, endTime, page, pageSize, take, skip);
        String trendSamplesJson = trendSampleService.findTrendSamplesByDateJson("Bearer " + bearerToken, prefixedUrlEncodedTrendId, pageSize, page, startTime, endTime);


        MetasysTrendSampleResult trendSampleResult = TrendSamplesMapper.mapFromJson(trendSamplesJson);
        log.trace("Found: {} trends from trendId: {}", trendSampleResult.getTotal(), objectId);
        List<MetasysTrendSample> trendSamples = trendSampleResult.getItems();
        if (trendSamples != null) {
            for (MetasysTrendSample trendSample : trendSamples) {
                trendSample.setTrendId(objectId);
                log.trace("imported trendSample: {}", trendSample);
            }
        }

         */
        isHealthy = true;
        updateWhenLastTrendSampleReceived();
        return new HashSet<>(trendSamples);
    }

    @Override
    public Integer subscribePresentValueChange(String subscriptionId, String objectId) throws URISyntaxException, LogonFailedException {
        Integer statusCode = null;
        String apiUrl = getConfigValue("sd.api.url"); //getConfigProperty("sd.api.url");

        String bearerToken = findAccessToken();
        URI subscribeUri = new URI(apiUrl + "objects/" + objectId+"/attributes/presentValue?includeSchema=false");
        CloseableHttpClient httpClient = HttpClients.createDefault();
        HttpGet request = null;
        try {
            request = new HttpGet(subscribeUri);
            request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + bearerToken);
            request.addHeader(METASYS_SUBSCRIBE_HEADER, subscriptionId);
            CloseableHttpResponse response = httpClient.execute(request);
            try {
                HttpEntity entity = response.getEntity();
                statusCode = response.getCode();
                if (statusCode == 202) {
                    log.trace("Subscribing ok for objectId: {}", objectId);
                } else {
                    String body = "";
                    if (entity != null) {
                        body = EntityUtils.toString(entity);
                    }
                    log.trace("Could not subscribe to subscription {} for objectId {} using URL: {}. Status: {}. Body text: {}", subscriptionId, objectId, subscribeUri, statusCode, body);
                }
            } catch (Exception e) {
                setUnhealthy();
                throw new MetasysCloudConnectorException("Failed to subscribe to objectId " + objectId
                        + ". Reason: " + e.getMessage(), e);
            }
        } catch (Exception e) {
            setUnhealthy();
            throw new MetasysCloudConnectorException("Failed to subscribe to objectId " + objectId
                    + ". Reason: " + e.getMessage(), e);
        }
        return statusCode;

    }

    public String findObjectId(String metasysDbReference) throws LogonFailedException, URISyntaxException {
        String encodedDbReference = urlEncode(metasysDbReference);
        String bearerToken = findAccessToken();
        ObjectIdentifiersService objectIdentifiersService = RestClientBuilder.newBuilder()
                .baseUri(apiUri)
                .build(ObjectIdentifiersService.class);
        String objectId = objectIdentifiersService.findObjectId("Bearer " + bearerToken, encodedDbReference);
        return objectId;
    }

    String encodeAndPrefix(String trendId) {
        if (trendId != null) {
            return urlEncode(trendId);
        } else {
            return null;
        }
    }

    protected synchronized String findAccessToken() throws LogonFailedException {
        UserToken activeUserToken = getUserToken();
        try {
            String accessToken = null;
            if (activeUserToken == null || tokenNeedRefresh()) {
                logon();
            }
            if (activeUserToken != null) {
                accessToken = activeUserToken.getAccessToken();
            } else {
                notificationService.clearService(METASYS_API);
            }

            return accessToken;
        } catch (LogonFailedException e){
            notificationService.sendAlarm(METASYS_API,LOGON_FAILED);
            isHealthy = false;
            throw e;
        }
    }

    boolean tokenNeedRefresh() {
        UserToken activeUserToken = getUserToken();
        if (activeUserToken == null) {
            return true;
        }
        Instant now = Instant.now();
        boolean willSoonExpire = activeUserToken.getExpires().isBefore(now.plusSeconds(30));
        if (willSoonExpire) {
            log.debug("AccessToken will soon expire. Need refreshing. Expires: {}", activeUserToken.getExpires().toString());
        }
        return willSoonExpire;
    }

    public synchronized MetasysUserToken refreshToken() throws LogonFailedException {
//        invalidateUserToken();
        MetasysUserToken refreshedUserToken = null;
        CloseableHttpClient httpClient = HttpClients.createDefault();
        String refreshTokenUrl = apiUri + "refreshToken";
        HttpGet request = null;
        String truncatedAccessToken = null;
        try {

            request = new HttpGet(refreshTokenUrl);
            UserToken activeUserToken = getUserToken();
            if (activeUserToken == null) {
                log.debug("Try to refresh when accessToken is null. Logon instead.");
                logon();
                refreshedUserToken = (MetasysUserToken) getUserToken();
                if (refreshedUserToken == null) {
                    log.warn("Failed to refresh usertoken. UserToken after logon is null.");
                } else {
                    log.debug("Refreshed token that expires: {}", truncateAccessToken(refreshedUserToken));
                }
            } else {
                log.info("Try to refresh userToken");
                String accessToken = activeUserToken.getAccessToken();
                truncatedAccessToken = truncateAccessToken(activeUserToken);
                log.info("Try to refresh userToken: {}", truncatedAccessToken);
                request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken);

                CloseableHttpResponse response = httpClient.execute(request);
                try {
                    int httpCode = response.getCode();
                    if (httpCode == 200) {
                        HttpEntity entity = response.getEntity();
                        if (entity != null) {
                            String body = EntityUtils.toString(entity);
                            log.trace("Received body: {}", body);
                            refreshedUserToken = RealEstateObjectMapper.getInstance().getObjectMapper().readValue(body, MetasysUserToken.class);
                            log.trace("Converted http body to userToken: {}", refreshedUserToken);
//                        refreshedUserToken = updatedUserToken;
                            setHealthy();
                            log.debug("Refreshed userToken: {}", truncateAccessToken(refreshedUserToken));
                        }
                    } else {
                        String msg = "Failed to refresh userToken to Metasys at uri: " + request.getRequestUri() +
                                ". accessToken: " + truncatedAccessToken +
                                ". ResponseCode: " + httpCode +
                                ". ReasonPhrase: " + response.getReasonPhrase();
                        LogonFailedException logonFailedException = new LogonFailedException(msg);
                        log.warn("Failed to refresh accessToken on Metasys. Reason {}", logonFailedException.getMessage());
                        setUnhealthy();
                        TemporaryHealthResource.addRegisteredError("Failed to refresh accessToken on Metasys. Reason: " + logonFailedException.getMessage());
                        throw logonFailedException;
                    }

                } finally {
                    response.close();
                }
            }
        } catch (IOException e) {
            notificationService.sendAlarm(METASYS_API,HOST_UNREACHABLE);
            String msg = "Failed to refresh accessToken on Metasys at uri: " + refreshTokenUrl + ", with accessToken: " + truncatedAccessToken;
            LogonFailedException logonFailedException = new LogonFailedException(msg, e);
            log.warn(msg);
            setUnhealthy();
            TemporaryHealthResource.addRegisteredError(msg + " Reason: " + logonFailedException.getMessage());
            throw logonFailedException;
        } catch (ParseException e) {
            notificationService.sendWarning(METASYS_API,"Parsing of AccessToken information failed.");
            String msg = "Failed to refresh accessToken on Metasys at uri: " + refreshTokenUrl + ", with accessToken: " + truncatedAccessToken +
                    ". Failure parsing the response.";
            LogonFailedException logonFailedException = new LogonFailedException(msg, e);
            log.warn(msg);
            setUnhealthy();
            TemporaryHealthResource.addRegisteredError(msg + " Reason: " + logonFailedException.getMessage());
            throw logonFailedException;
        } finally {
            try {
                httpClient.close();
            } catch (IOException e) {
                //Do nothing
            }
        }
        return refreshedUserToken;
    }

    @Nullable
    private static String truncateAccessToken(UserToken userToken) {
        String truncatedAccessToken = null;
        if (userToken != null) {
            String accessToken = userToken.getAccessToken();
            if (accessToken != null && accessToken.length() > 11) {
                truncatedAccessToken = accessToken.substring(0, 10) + "...";
            } else {
                truncatedAccessToken = accessToken;
            }
            truncatedAccessToken = truncatedAccessToken + " expires: " + userToken.getExpires();
        }
        return truncatedAccessToken;
    }

    protected synchronized void updateUserToken(MetasysUserToken userToken) {
        this.userToken = userToken;
    }

    protected synchronized void invalidateUserToken() {
        updateUserToken(null);
    }

    @Override
    public synchronized void logon() throws LogonFailedException {
        //FIXME ensure multiple instances of this class does not logon multiple times
        String username = getConfigValue("sd.api.username");
        String password = getConfigValue("sd.api.password");
        logon(username, password);
    }
    protected synchronized void logon(String username, String password) throws LogonFailedException {
        invalidateUserToken();
        log.trace("Logon: {}", username);
        String jsonBody = "{ \"username\": \"" + username + "\",\"password\": \"" + password + "\"}";
        CloseableHttpClient httpClient = HttpClients.createDefault();
        String loginUri = apiUri + "login";
        HttpPost request = null;
        try {
            request = new HttpPost(loginUri);
            request.addHeader(HttpHeaders.CONTENT_TYPE, "application/json");
            request.setEntity(new StringEntity(jsonBody));

            CloseableHttpResponse response = httpClient.execute(request);
            try {
                int httpCode = response.getCode();
                if (httpCode == 200) {
                    HttpEntity entity = response.getEntity();
                    if (entity != null) {
                        String body = EntityUtils.toString(entity);
                        log.trace("Received body: {}", body);
                        MetasysUserToken updatedUserToken = RealEstateObjectMapper.getInstance().getObjectMapper().readValue(body, MetasysUserToken.class);
                        log.trace("Converted http body to userToken: {}", updatedUserToken);
                        updateUserToken(updatedUserToken);
                        setHealthy();
                        notificationService.clearService(METASYS_API);
                    }
                } else {
                    String msg = "Failed to logon to Metasys at uri: " + request.getRequestUri() +
                            ". Username: " + username +
                            ". Password: " + password +
                            ". ResponseCode: " + httpCode +
                            ". ReasonPhrase: " + response.getReasonPhrase();
                    LogonFailedException logonFailedException = new LogonFailedException(msg);
                    log.warn("Failed to logon to Metasys. Reason {}", logonFailedException.getMessage());
                    setUnhealthy();
                    notificationService.sendWarning(METASYS_API,LOGON_FAILED);
                    TemporaryHealthResource.addRegisteredError("Failed to logon to Metasys. Reason: " + logonFailedException.getMessage());
                    throw logonFailedException;
                }

            } finally {
                response.close();
            }
        } catch (IOException e) {
            notificationService.sendAlarm(METASYS_API,HOST_UNREACHABLE);
            String msg = "Failed to logon to Metasys at uri: " + loginUri + ", with username: " + username;
            LogonFailedException logonFailedException = new LogonFailedException(msg, e);
            log.warn(msg);
            setUnhealthy();
            TemporaryHealthResource.addRegisteredError(msg + " Reason: " + logonFailedException.getMessage());
            throw logonFailedException;
        } catch (ParseException e) {
            notificationService.sendWarning(METASYS_API,"Parsing of login information failed.");
            String msg = "Failed to logon to Metasys at uri: " + loginUri + ", with username: " + username +
                    ". Failure parsing the response.";
            LogonFailedException logonFailedException = new LogonFailedException(msg, e);
            log.warn(msg);
            setUnhealthy();
            TemporaryHealthResource.addRegisteredError(msg + " Reason: " + logonFailedException.getMessage());
            throw logonFailedException;
        } finally {
            try {
                httpClient.close();
            } catch (IOException e) {
                //Do nothing
            }
        }
    }

    @Override
    public boolean isLoggedIn() {
        UserToken activeUserToken = getUserToken();
        if (activeUserToken == null || activeUserToken.tokenNeedRefresh()) {
            return false;
        }
        return true;
    }

    @Override
    public String getName() {
        return "MetasysApiClientRest";
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


    public boolean isHealthy() {
        return isHealthy;
    }

    @Override
    public long getNumberOfTrendSamplesReceived() {
        return numberOfTrendSamplesReceived;
    }

     void addNumberOfTrendSamplesReceived() {
        if (numberOfTrendSamplesReceived < Long.MAX_VALUE) {
            numberOfTrendSamplesReceived ++;
        } else {
            numberOfTrendSamplesReceived = 1;
        }
    }

    @Override
    public PresentValue findPresentValue(SensorId sensorId) throws URISyntaxException, LogonFailedException {
        //FIXME Implement findPresentValue
        throw new UnsupportedOperationException("findPresentValue not implemented");
    }

    @Override
    public synchronized UserToken getUserToken() {
        return userToken;
    }

    protected  void updateWhenLastTrendSampleReceived() {
        whenLastTrendSampleReceived = Instant.ofEpochMilli(System.currentTimeMillis());
    }
    public Instant getWhenLastTrendSampleReceived() {
        return whenLastTrendSampleReceived;
    }
}
