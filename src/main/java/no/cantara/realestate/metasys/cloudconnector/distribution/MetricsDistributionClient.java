package no.cantara.realestate.metasys.cloudconnector.distribution;

import no.cantara.realestate.mappingtable.MappedSensorId;
import no.cantara.realestate.metasys.cloudconnector.automationserver.MetasysTrendSample;

import java.util.Set;

public interface MetricsDistributionClient {
    void sendMetrics(Metric metric);

    void openDb();

    void flush();

    void closeDb();

    void populate(Set<MetasysTrendSample> trendSamples, MappedSensorId mappedSensorId);
}
