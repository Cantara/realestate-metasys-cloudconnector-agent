package no.cantara.realestate.metasys.cloudconnector.ingestion;

import no.cantara.realestate.observations.TrendSample;
import no.cantara.realestate.sensors.metasys.MetasysSensorId;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashSet;
import java.util.Set;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Test data builder for MetasysTrendsIngestionService tests
 */
public class MetasysTestDataBuilder {

    public static MetasysSensorId createSensorId(String sensorId, String twinId, String metasysObjectId) {
        MetasysSensorId mockSensorId = mock(MetasysSensorId.class);
        when(mockSensorId.getId()).thenReturn(sensorId);
        when(mockSensorId.getTwinId()).thenReturn(twinId != null ? twinId : sensorId);
        when(mockSensorId.getMetasysObjectId()).thenReturn(metasysObjectId);
        return mockSensorId;
    }

    public static Set<TrendSample> createTrendSamples(int count) {
        return createTrendSamples(count, "testValue", Instant.now());
    }

    public static Set<TrendSample> createTrendSamples(int count, String valuePrefix, Instant baseTime) {
        Set<TrendSample> samples = new LinkedHashSet<>();
        for (int i = 0; i < count; i++) {
            TrendSample sample = mock(TrendSample.class);
            when(sample.getValue()).thenReturn(i);
            when(sample.getObservedAt()).thenReturn(baseTime.minus(i, ChronoUnit.MINUTES));
            samples.add(sample);
        }
        return samples;
    }

    public static TrendSample createTrendSample(Number value, Instant observedAt) {
        TrendSample sample = mock(TrendSample.class);
        when(sample.getValue()).thenReturn(value);
        when(sample.getObservedAt()).thenReturn(observedAt);
        return sample;
    }
}