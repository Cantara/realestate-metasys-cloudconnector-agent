package no.cantara.realestate.metasys.cloudconnector.metrics;

import com.microsoft.applicationinsights.TelemetryClient;
import no.cantara.realestate.mappingtable.MappedSensorId;
import no.cantara.realestate.observations.TrendSample;
import no.cantara.realestate.rec.RecTags;
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
                log.trace("sendMetrics called with metric: {}, value: {}", key, value);
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
    public void populate(Set<TrendSample> trendSamples, RecTags recTags) {
        int count = 0;
        if (trendSamples != null) {
            count = trendSamples.size();
        }
        telemetryClient.trackMetric("metasys_trendsamples_received", count);
    }

    @Override
    public void populate(Set<TrendSample> trendSamples, RecTags recTags, String metricName) {
        int count = 0;
        if (trendSamples != null) {
            count = trendSamples.size();
        }
        telemetryClient.trackMetric(metricName, count);
    }

    @Override
    public void sendValue(String metricName, long value) {
        if (metricName != null && !metricName.isEmpty() ) {
            log.trace("sendValue(String,long) called with metricName: {}, value: {}", metricName, value);
            telemetryClient.trackMetric(metricName, value);
        } else {
            log.trace("sendValue(String,long) called with null metricName value: {}", metricName, value);
        }
    }

    @Override
    public void sendDoubleValue(String metricName, double value) {
        if (metricName != null && !metricName.isEmpty() ) {
            log.trace("sendValue(String,double) called with metricName: {}, value: {}", metricName, value);
            telemetryClient.trackMetric(metricName, value);
        } else {
            log.trace("sendValue(String,double) called with null metricName value: {}", metricName, value);
        }
    }
}
