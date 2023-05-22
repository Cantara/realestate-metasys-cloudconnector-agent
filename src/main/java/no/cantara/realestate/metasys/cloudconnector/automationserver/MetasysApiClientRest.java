package no.cantara.realestate.metasys.cloudconnector.automationserver;

import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import org.slf4j.Logger;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static no.cantara.realestate.mappingtable.Main.getConfigValue;
import static no.cantara.realestate.metasys.cloudconnector.status.TemporaryHealthResource.*;
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
        log.trace("Logon: {}", username);
        try {
            String jsonBody = "{ \"username\": \"" + username + "\",\"password\": \"" + password + "\"}";
            userToken = logonService.logon(jsonBody);
            log.info("UserToken: {}", userToken);
            setHealthy();
        } catch (ProcessingException e) {
            setUnhealthy();
            if (e.getCause() != null && e.getCause() instanceof UnknownHostException) {
                UnknownHostException uhe = (UnknownHostException) e.getCause();
                throw new SdLogonFailedException("Failed to logon to " + uhe.getMessage() + ". Host is unknown.");
            } else {
                log.warn("Failed to logon to {}. Using username: {}. . Reason: {}", apiUri, username, e.getMessage());
                throw new SdLogonFailedException("Failed to logon to " + apiUri + ", username: " + username, e );
            }

        } catch (WebApplicationException e) {
            setUnhealthy();
            if (e != null) {
                Response failedResponse = e.getResponse();
                log.warn("Failed to logon to {}. Using username: {}. Response: {}. Reason: {}", apiUri,username, failedResponse, e.getMessage());
                addRegisteredError("Failed to logon to "+apiUri+". Using username: "+username+". Response: "+failedResponse+". Reason: "+e.getMessage());
                throw new SdLogonFailedException("Failed to logon to " + apiUri + ", username: " + username + ". FailedResponse: " + failedResponse);
            } else {
                log.warn("Failed to logon to {}. Using username: {}. Reason: {}", apiUri,username, e);
                addRegisteredError("Failed to logon to "+apiUri+". Using username: "+username);
                throw new SdLogonFailedException("Failed to logon to " + apiUri + ", username: " + username );
            }
        } catch (Exception e) {
            setUnhealthy();
            e.printStackTrace();
            log.warn("Failed to logon to {}. Using username: {}. . Reason: {}", apiUri, username, e.getMessage());
            throw new SdLogonFailedException("Failed to logon to " + apiUri + ", username: " + username, e );
        }
    }
}
