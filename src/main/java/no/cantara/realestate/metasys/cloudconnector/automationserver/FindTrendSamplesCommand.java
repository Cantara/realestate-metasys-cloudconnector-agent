package no.cantara.realestate.metasys.cloudconnector.automationserver;

import java.time.Instant;
import java.util.Set;

public record FindTrendSamplesCommand(String objectId, Instant from, Instant to)
        implements MetasysCommand<Set<MetasysTrendSample>> {

    @Override
    public MetasysRequest request() {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @Override
    public Set<MetasysTrendSample> onSuccess(String body) {
        throw new UnsupportedOperationException("Not implemented yet");
    }
}
