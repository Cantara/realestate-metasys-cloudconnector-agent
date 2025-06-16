package no.cantara.realestate.metasys.cloudconnector.metrics;

import no.cantara.realestate.mappingtable.MappedSensorId;
import no.cantara.realestate.observations.TrendSample;
import no.cantara.realestate.rec.RecTags;

import java.util.Set;

@Deprecated // This class is deprecated and will be removed in a future release.
public interface MetasysMetricsDistributionClient {
    void sendMetrics(Metric metric);

    void openDb();

    void flush();

    void closeDb();

    void populate(Set<TrendSample> trendSamples, RecTags recTags);
    void populate(Set<TrendSample> trendSamples, RecTags recTags, String metricName);

    void sendValue(String metricName, long value);
    void sendDoubleValue(String metricName, double value);
}
