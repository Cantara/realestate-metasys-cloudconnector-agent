package no.cantara.realestate.metasys.cloudconnector.automationserver;

import no.cantara.realestate.automationserver.TrendNotFoundException;
import no.cantara.realestate.metasys.cloudconnector.MetasysCloudConnectorException;

import java.time.Instant;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Fetch the trended presentValue samples of one Metasys object within a time window.
 * <p>
 * {@code to} is part of the payload rather than computed here, so the request is reproducible in
 * tests. {@link MetasysClientCB} passes now + 60 s, as {@link MetasysClient} does today.
 * <p>Example:
 * <pre>{@code
 * Set<MetasysTrendSample> samples = executor.execute(
 *         new FindTrendSamplesCommand(objectId, lastSeen, Instant.now().plusSeconds(60)));
 * }</pre>
 *
 * @param objectId Metasys object id; also stamped as trendId on every returned sample
 * @param from     start of the window, inclusive
 * @param to       end of the window
 */
public record FindTrendSamplesCommand(String objectId, Instant from, Instant to)
        implements MetasysCommand<Set<MetasysTrendSample>> {

    // Same paging as MetasysClient: one page of up to 1000 samples per poll.
    private static final String PAGE = "1";
    private static final String PAGE_SIZE = "1000";
    private static final String SKIP = "0";

    @Override
    public MetasysRequest request() {
        Map<String, String> query = new LinkedHashMap<>();
        query.put("startTime", from.toString());
        query.put("endTime", to.toString());
        query.put("page", PAGE);
        query.put("pageSize", PAGE_SIZE);
        query.put("skip", SKIP);
        return MetasysRequest.get("objects/" + objectId + "/trendedAttributes/presentValue/samples", query);
    }

    @Override
    public Set<MetasysTrendSample> onSuccess(String body) {
        MetasysTrendSampleResult result = TrendSamplesMapper.mapFromJson(body);
        if (result == null) {
            throw new MetasysCloudConnectorException("Unable to parse trend samples for objectId " + objectId);
        }
        List<MetasysTrendSample> items = result.getItems();
        if (items == null) {
            return Set.of();
        }
        for (MetasysTrendSample sample : items) {
            sample.setTrendId(objectId);
        }
        return new HashSet<>(items);
    }

    @Override
    public Set<MetasysTrendSample> onError(int status, String body) {
        if (status == 404) {
            throw new TrendNotFoundException("Failed to fetch trendsamples for objectId " + objectId
                    + ". Status: 404. Body: " + body, objectId);
        }
        return MetasysCommand.super.onError(status, body);
    }
}
