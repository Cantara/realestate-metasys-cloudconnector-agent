package no.cantara.realestate.metasys.cloudconnector.metrics;

import com.microsoft.applicationinsights.TelemetryClient;
import no.cantara.realestate.mappingtable.MappedSensorId;
import no.cantara.realestate.observations.TrendSample;
import org.slf4j.Logger;

import java.util.Set;

import static org.slf4j.LoggerFactory.getLogger;

@Deprecated
public class MetricsDistributionServiceStub implements MetasysMetricsDistributionClient {
    private static final Logger log = getLogger(MetricsDistributionServiceStub.class);
    TelemetryClient telemetryClient;

    public MetricsDistributionServiceStub(String measurementName) {
        telemetryClient = new TelemetryClient();
    }

    @Override
    public void sendMetrics(Metric metric) {
        String key = metric.getMeasurementName();
        try {
            Number value = metric.getValue();
            if (value != null) {
                telemetryClient.trackMetric(key, value.doubleValue());
            }
        } catch (Exception e) {
            log.trace("Failed to send metric: {}. Reason: {}", metric, e.getMessage());
        }
    }

    @Override
    public void openDb() {

    }

    @Override
    public void flush() {

    }

    @Override
    public void closeDb() {

    }

    @Override
    public void populate(Set<TrendSample> trendSamples, MappedSensorId mappedSensorId) {
        int count = 0;
        if (trendSamples != null) {
            count = trendSamples.size();
        }
        telemetryClient.trackMetric("metasys_trendsamples_received", count);
    }
}
