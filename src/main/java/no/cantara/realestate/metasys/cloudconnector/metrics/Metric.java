package no.cantara.realestate.metasys.cloudconnector.metrics;

import java.util.HashMap;
import java.util.Map;

public class Metric {
    private String field;
    private Number value;
    private long timestamp;
    private Map<String, String> tags;
    private String measurementName;

    /**
     * Default value = 1
     * @param measurementName measurement
     */
    public Metric(String measurementName) {
        tags = new HashMap<>();
        timestamp = System.currentTimeMillis();
        field = "value";
        value = Integer.valueOf(1);
        this.measurementName = measurementName;
    }

    /**
     *
     * @param measurementName measurement
     * @param value value of metric
     */
    public Metric(String measurementName, Number value) {
        this(measurementName);
        this.value = value;
    }

    public String getField() {
        return field;
    }

    public Number getValue() {
        return value;
    }

    public void setValue(Number value) {
        this.value = value;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public Map<String, String> getTags() {
        return tags;
    }

    public void setTags(Map<String, String> tags) {
        this.tags = tags;
    }

    public void addTag(String key, String value) {
        tags.put(key, value);
    }

    public String getMeasurementName() {
        return measurementName;
    }

    public void setMeasurementName(String measurementName) {
        this.measurementName = measurementName;
    }
}
