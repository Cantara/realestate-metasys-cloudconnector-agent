package no.cantara.realestate.metasys.cloudconnector.automationserver;

import java.net.URISyntaxException;
import java.time.Instant;
import java.util.Set;

public interface SdClient {
    Set<MetasysTrendSample> findTrendSamples(String bearerToken, String trendId) throws URISyntaxException;

    Set<MetasysTrendSample> findTrendSamples(String trendId, int take, int skip) throws URISyntaxException, SdLogonFailedException;

    Set<MetasysTrendSample> findTrendSamplesByDate(String trendId, int take, int skip, Instant onAndAfterDateTime) throws URISyntaxException, SdLogonFailedException;

    Integer subscribePresentValueChange(String subscriptionId, String objectId) throws URISyntaxException, SdLogonFailedException;

    void logon() throws SdLogonFailedException;

    boolean isLoggedIn();

    String getName();

    boolean isHealthy();

    long getNumberOfTrendSamplesReceived();

    UserToken getUserToken();

    UserToken refreshToken() throws SdLogonFailedException;

    Instant getWhenLastTrendSampleReceived();
}
