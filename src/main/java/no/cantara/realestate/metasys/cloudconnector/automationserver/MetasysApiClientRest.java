package no.cantara.realestate.metasys.cloudconnector.automationserver;

import jakarta.ws.rs.core.HttpHeaders;
import no.cantara.realestate.metasys.cloudconnector.CloudConnectorObjectMapper;
import no.cantara.realestate.metasys.cloudconnector.status.TemporaryHealthResource;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.ParseException;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.slf4j.Logger;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static no.cantara.realestate.mappingtable.Main.getConfigValue;
import static no.cantara.realestate.metasys.cloudconnector.status.TemporaryHealthResource.setHealthy;
import static no.cantara.realestate.metasys.cloudconnector.status.TemporaryHealthResource.setUnhealthy;
import static no.cantara.realestate.metasys.cloudconnector.utils.UrlEncoder.urlEncode;
import static org.slf4j.LoggerFactory.getLogger;

public class MetasysApiClientRest implements SdClient {
    private static final Logger log = getLogger(MetasysApiClientRest.class);
    private final URI apiUri;

    //FIXME Implement Client https://github.com/Cantara/stingray/blob/main/samples/greeter/src/main/java/no/cantara/stingray/sample/greeter/HttpRandomizerClient.java
    public static final String HOST_UNREACHABLE = "HOST_UNREACHABLE";
    public static final String LOGON_FAILED = "Logon to Metasys Api Failed";
    public static final String SERVICE_FAILED = "Metasys Api is failing.";
    public static final String UNKNOWN_HOST = "UNKNOWN_HOST";

    private static final String LATEST_BY_DATE = "SampleDateDescending";
    private UserToken userToken = null;
    private final MetasysApiLogonService logonService;

    public MetasysApiClientRest(URI apiUri) {
        this.apiUri = apiUri;
        logonService = null;
        /*RestClientBuilder.newBuilder()
                .baseUri(apiUri)
                .build(MetasysApiLogonService.class);

         */
    }

    protected MetasysApiClientRest(URI apiUri, MetasysApiLogonService logonService) {
        this.apiUri = apiUri;
        this.logonService = logonService;
    }

    public static void main(String[] args) throws URISyntaxException, SdLogonFailedException {

        String trendId = getConfigValue("trend.id");
        String apiUrl = getConfigValue("sd.api.url");
        URI apiUri = new URI(apiUrl);
        MetasysApiClientRest apiClient = new MetasysApiClientRest(apiUri);
        String bearerToken = apiClient.findAccessToken();
        Set<MetasysTrendSample> trends = apiClient.findTrendSamples(bearerToken, trendId);
        for (MetasysTrendSample trend : trends) {
            if (trend != null) {
                log.info("Trend id={}, value={}, valid={}", trend.getTrendId(), trend.getValue(), trend.isValid());
            } else {
                log.info("Trend is null");
            }
        }
    }

    @Override
    public Set<MetasysTrendSample> findTrendSamples(String bearerToken, String trendId) throws URISyntaxException {
        String apiUrl = getConfigValue("sd.api.url"); //getConfigProperty("sd.api.url");

        URI apiUri = new URI(apiUrl);
        TrendSampleService trendSampleService = RestClientBuilder.newBuilder()
                .baseUri(apiUri)
                .build(TrendSampleService.class);
        return trendSampleService.findTrendSamples("Bearer " + bearerToken, trendId.toString());
    }

    @Override
    public Set<MetasysTrendSample> findTrendSamples(String trendId, int take, int skip) throws URISyntaxException, SdLogonFailedException {
        String apiUrl = getConfigValue("sd.api.url");
        String prefixedUrlEncodedTrendId = encodeAndPrefix(trendId);
        String bearerToken = findAccessToken();
        URI apiUri = new URI(apiUrl);
        TrendSampleService trendSampleService = RestClientBuilder.newBuilder()
                .baseUri(apiUri)
                .build(TrendSampleService.class);
        return trendSampleService.findTrendSamples("Bearer " + bearerToken, prefixedUrlEncodedTrendId, take, skip);
    }

    @Override
    public Set<MetasysTrendSample> findTrendSamplesByDate(String trendId, int take, int skip, Instant onAndAfterDateTime) throws URISyntaxException, SdLogonFailedException {

        String apiUrl = getConfigValue("sd.api.url");
        String prefixedUrlEncodedTrendId = encodeAndPrefix(trendId);
        String bearerToken = findAccessToken();
        URI apiUri = new URI(apiUrl);
        TrendSampleService trendSampleService = RestClientBuilder.newBuilder()
                .baseUri(apiUri)
                .build(TrendSampleService.class);
        String startTime = onAndAfterDateTime.toString();
        //FIXME make dynamic
        int page=1;
        int pageSize=1000;
        String endTime = Instant.now().plusSeconds(60).toString();
//        MetasysTrendSampleResult trendSampleResult = trendSampleService.findTrendSamplesByDate("Bearer " + bearerToken, prefixedUrlEncodedTrendId, pageSize, page, startTime, endTime);
        log.trace("findTrendSamplesByDate. trendId: {}. From date: {}. To date: {}. Page: {}. PageSize: {}. Take: {}. Skip: {}",
                trendId, onAndAfterDateTime, endTime, page, pageSize, take, skip);
        String trendSamplesJson = trendSampleService.findTrendSamplesByDateJson("Bearer " + bearerToken, prefixedUrlEncodedTrendId, pageSize, page, startTime, endTime);
        MetasysTrendSampleResult trendSampleResult = TrendSamplesMapper.mapFromJson(trendSamplesJson);
        log.trace("Found: {} trends from trendId: {}", trendSampleResult.getTotal(), trendId);
        List<MetasysTrendSample> trendSamples = trendSampleResult.getItems();
        if (trendSamples != null) {
            for (MetasysTrendSample trendSample : trendSamples) {
                trendSample.setTrendId(trendId);
                log.trace("imported trendSample: {}", trendSample);
            }
        }
        return new HashSet<>(trendSamples);
    }

    public String findObjectId(String metasysDbReference) throws SdLogonFailedException, URISyntaxException {
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

    private String findAccessToken() throws SdLogonFailedException {
        try {
            String accessToken = null;
            if (userToken == null || tokenNeedRefresh()) {
                logon();
            }
            if (userToken != null) {
                accessToken = userToken.getAccessToken();
            } else {
//                SlackNotificationService.clearService(METASYS_API);
            }

            return accessToken;
        } catch (SdLogonFailedException e){
//            SlackNotificationService.sendAlarm(METASYS_API,LOGON_FAILED);
            throw e;
        }
    }

    boolean tokenNeedRefresh() {
        if (userToken == null) {
            return true;
        }
        Instant now = Instant.now();
        boolean willSoonExpire = userToken.getExpires().isBefore(now.plusSeconds(30));
        if (willSoonExpire) {
            log.debug("AccessToken will soon expire. Need refreshing. Expires: {}", userToken.getExpires().toString());
        }
        return willSoonExpire;
    }

    @Override
    public void logon() throws SdLogonFailedException {
        String username = getConfigValue("sd.api.username");
        String password = getConfigValue("sd.api.password");
        logon(username, password);
    }
    protected void logon(String username, String password) throws SdLogonFailedException {
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
                        userToken = CloudConnectorObjectMapper.getInstance().getObjectMapper().readValue(body, UserToken.class);
                        log.trace("Converted to userToken: {}", userToken);
                        setHealthy();
                    }
                } else {
                    String msg = "Failed to logon to Metasys at uri: " + request.getRequestUri() +
                            ". Username: " + username +
                            ". ResponseCode: " + httpCode +
                            ". ReasonPhrase: " + response.getReasonPhrase();
                    SdLogonFailedException logonFailedException = new SdLogonFailedException(msg);
                    log.warn("Failed to logon to Metasys. Reason {}", logonFailedException.getMessage());
                    setUnhealthy();
                    throw logonFailedException;
                }

            } finally {
                response.close();
            }
        } catch (IOException e) {
            String msg = "Failed to logon to Metasys at uri: " + loginUri + ", with username: " + username;
            SdLogonFailedException logonFailedException = new SdLogonFailedException(msg, e);
            log.warn(msg);
            throw logonFailedException;
        } catch (ParseException e) {
            String msg = "Failed to logon to Metasys at uri: " + loginUri + ", with username: " + username +
                    ". Failure parsing the response.";
            SdLogonFailedException logonFailedException = new SdLogonFailedException(msg, e);
            log.warn(msg);
            throw logonFailedException;
        } finally {
            try {
                httpClient.close();
            } catch (IOException e) {
                //Do nothing
            }
        }
    }

    public boolean isHealthy() {
        return TemporaryHealthResource.getStatus();
    }
}
