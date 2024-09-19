package no.cantara.realestate.metasys.cloudconnector.metrics;

import no.cantara.realestate.mappingtable.MappedSensorId;
import no.cantara.realestate.observations.TrendSample;

import java.util.Set;

public class MetricsDistributionServiceStub implements MetasysMetricsDistributionClient {
    public MetricsDistributionServiceStub(String measurementName) {
    }

    @Override
    public void sendMetrics(Metric metric) {

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

    }
}
