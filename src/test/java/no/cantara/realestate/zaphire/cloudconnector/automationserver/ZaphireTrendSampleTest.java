package no.cantara.realestate.zaphire.cloudconnector.automationserver;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ZaphireTrendSampleTest {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void deserializeHistoryRecord() throws Exception {
        String json = "{\"Name\": \"example/tag\", \"Timestamp\": \"2024-01-15T08:00:00.0000000+00:00\", \"Value\": 20.1}";

        ZaphireTrendSample sample = objectMapper.readValue(json, ZaphireTrendSample.class);

        assertEquals("example/tag", sample.getName());
        assertEquals("example/tag", sample.getTrendId());
        assertNotNull(sample.getObservedAt());
        assertEquals(20.1, sample.getValue().doubleValue(), 0.01);
        assertTrue(sample.getReliable());
    }

    @Test
    void deserializeHistoryRecordWithNullValue() throws Exception {
        String json = "{\"Name\": \"example/tag\", \"Timestamp\": \"2024-01-15T08:00:00.0000000+00:00\", \"Value\": null}";

        ZaphireTrendSample sample = objectMapper.readValue(json, ZaphireTrendSample.class);

        assertEquals("example/tag", sample.getName());
        assertNull(sample.getValue());
        assertFalse(sample.getReliable());
    }

    @Test
    void deserializeHistoryRecordArray() throws Exception {
        String json = "[\n" +
                "  {\"Name\": \"example/tag\", \"Timestamp\": \"2024-01-15T08:00:00.0000000+00:00\", \"Value\": 20.1},\n" +
                "  {\"Name\": \"example/tag\", \"Timestamp\": \"2024-01-15T09:00:00.0000000+00:00\", \"Value\": 21.3},\n" +
                "  {\"Name\": \"example/tag\", \"Timestamp\": \"2024-01-15T10:00:00.0000000+00:00\", \"Value\": 22.0}\n" +
                "]";

        List<ZaphireTrendSample> samples = objectMapper.readValue(json, new TypeReference<List<ZaphireTrendSample>>() {});

        assertEquals(3, samples.size());
        assertEquals(20.1, samples.get(0).getValue().doubleValue(), 0.01);
        assertEquals(21.3, samples.get(1).getValue().doubleValue(), 0.01);
        assertEquals(22.0, samples.get(2).getValue().doubleValue(), 0.01);

        // Verify all have the same trendId
        for (ZaphireTrendSample sample : samples) {
            assertEquals("example/tag", sample.getTrendId());
        }
    }

    @Test
    void deserializeWithIsoTimestamp() throws Exception {
        String json = "{\"Name\": \"sensor/temp\", \"Timestamp\": \"2022-05-04T01:00:00Z\", \"Value\": 874.4}";

        ZaphireTrendSample sample = objectMapper.readValue(json, ZaphireTrendSample.class);

        assertNotNull(sample.getObservedAt());
        assertEquals(874.4, sample.getValue().doubleValue(), 0.01);
    }

    @Test
    void equalsAndHashCode() throws Exception {
        String json1 = "{\"Name\": \"tag1\", \"Timestamp\": \"2024-01-15T08:00:00.0000000+00:00\", \"Value\": 20.1}";
        String json2 = "{\"Name\": \"tag1\", \"Timestamp\": \"2024-01-15T08:00:00.0000000+00:00\", \"Value\": 20.1}";
        String json3 = "{\"Name\": \"tag1\", \"Timestamp\": \"2024-01-15T09:00:00.0000000+00:00\", \"Value\": 21.0}";

        ZaphireTrendSample sample1 = objectMapper.readValue(json1, ZaphireTrendSample.class);
        ZaphireTrendSample sample2 = objectMapper.readValue(json2, ZaphireTrendSample.class);
        ZaphireTrendSample sample3 = objectMapper.readValue(json3, ZaphireTrendSample.class);

        assertEquals(sample1, sample2);
        assertEquals(sample1.hashCode(), sample2.hashCode());
        assertNotEquals(sample1, sample3);
    }

    @Test
    void toStringContainsFields() throws Exception {
        String json = "{\"Name\": \"my/tag\", \"Timestamp\": \"2024-01-15T08:00:00.0000000+00:00\", \"Value\": 42.0}";

        ZaphireTrendSample sample = objectMapper.readValue(json, ZaphireTrendSample.class);

        String str = sample.toString();
        assertTrue(str.contains("my/tag"));
        assertTrue(str.contains("42.0"));
    }
}
