package no.cantara.realestate.metasys.cloudconnector.automationserver;

import org.slf4j.Logger;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Instant;
import java.util.Set;

import static org.slf4j.LoggerFactory.getLogger;

public class MetasysApiClientRest implements SdClient {
    private static final Logger log = getLogger(MetasysApiClientRest.class);
    private final URI apiUri;

    public MetasysApiClientRest(URI apiUri) {
        this.apiUri = apiUri;
    }

    @Override
    public Set<MetasysTrendSample> findTrendSamples(String bearerToken, String trendId) throws URISyntaxException {
        return null;
    }

    @Override
    public Set<MetasysTrendSample> findTrendSamples(String trendId, int take, int skip) throws URISyntaxException, SdLogonFailedException {
        return null;
    }

    @Override
    public Set<MetasysTrendSample> findTrendSamplesByDate(String trendId, int take, int skip, Instant onAndAfterDateTime) throws URISyntaxException, SdLogonFailedException {
        return null;
    }

    @Override
    public void logon() throws SdLogonFailedException {

    }
}
