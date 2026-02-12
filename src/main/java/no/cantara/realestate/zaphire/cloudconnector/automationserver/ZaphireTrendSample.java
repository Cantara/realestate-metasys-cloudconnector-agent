package no.cantara.realestate.zaphire.cloudconnector.automationserver;

import com.fasterxml.jackson.annotation.JsonProperty;
import no.cantara.realestate.observations.TrendSample;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Objects;

/**
 * TrendSample for Zaphire Tags/History/Records API.
 * Maps from JSON: {"Name": "example/tag", "Timestamp": "2022-05-04T01:00:00.0000000+00:00", "Value": 874.4}
 */
public class ZaphireTrendSample extends TrendSample {

    private String name;

    public ZaphireTrendSample() {
    }

    @JsonProperty("Name")
    public String getName() {
        return name;
    }

    @JsonProperty("Name")
    public void setName(String name) {
        this.name = name;
        setTrendId(name);
    }

    @JsonProperty("Timestamp")
    public void setTimestamp(String timestamp) {
        if (timestamp != null) {
            try {
                Instant instant = OffsetDateTime.parse(timestamp).toInstant();
                setObservedAt(instant);
            } catch (Exception e) {
                setObservedAt(Instant.parse(timestamp));
            }
        }
    }

    @JsonProperty("Value")
    public void setZaphireValue(Number value) {
        setValue(value);
        setReliable(value != null);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ZaphireTrendSample that = (ZaphireTrendSample) o;
        return Objects.equals(getTrendId(), that.getTrendId()) &&
                Objects.equals(getObservedAt(), that.getObservedAt()) &&
                Objects.equals(getValue(), that.getValue());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getTrendId(), getObservedAt(), getValue());
    }

    @Override
    public String toString() {
        return "ZaphireTrendSample{" +
                "name='" + name + '\'' +
                ", trendId='" + getTrendId() + '\'' +
                ", observedAt=" + getObservedAt() +
                ", value=" + getValue() +
                ", isReliable=" + getReliable() +
                '}';
    }
}
