package no.cantara.realestate.metasys.cloudconnector.automationserver;

import com.fasterxml.jackson.annotation.JsonSetter;
import no.cantara.realestate.observations.TrendSample;
import no.cantara.realestate.observations.Value;

import javax.json.bind.annotation.JsonbProperty;
import java.time.Instant;
import java.util.Objects;

/*
    {
		"value": {
			"value": 9398.001,
			"units": "https://metasysserver/api/v4/enumSets/507/members/19"
		},
		"timestamp": "2020-09-16T05:20:00Z",
		"isReliable": true
	},
 */
public class MetasysTrendSample extends TrendSample {
    private String trendId = null;

    private Boolean isReliable;
    @JsonbProperty("timestamp")
    private Instant observedAt;
    @JsonbProperty("value")
    private MetasysValue valueObject;
    private String objectId;

    public MetasysTrendSample() {
    }

    public String getTrendId() {
        return trendId;
    }

    public void setTrendId(String trendId) {
        this.trendId = trendId;
    }


    public Boolean getReliable() {
        return isReliable;
    }

    public void setReliable(Boolean reliable) {
        isReliable = reliable;
    }

    public void setIsReliable(Boolean reliable) {
        isReliable = reliable;
    }


    public void setTimestamp(String timestamp) {
       super.setObservedAt(timestamp);
    }

    public boolean isValid() {
        return true; //FIXME validate MetasysTrendSample
    }

    public Value getValueObject() {
        return valueObject;
    }

    @JsonSetter("value")
    public void setValueObject(MetasysValue valueObject) {
        this.valueObject = valueObject;
        if (valueObject != null && valueObject.getValue() != null) {
            if (valueObject.getValue() instanceof Number)
            setValue((Number) valueObject.getValue());
        }
    }

    public void setValue(Number value) {
        super.setValue(value);
    }

    public void setObjectId(String objectId) {
        this.objectId = objectId;
    }

    public String getObjectId() {
        return objectId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MetasysTrendSample that = (MetasysTrendSample) o;
        return Objects.equals(getTrendId(), that.getTrendId()) &&
                Objects.equals(objectId, that.objectId) &&
                Objects.equals(isReliable, that.isReliable) &&
                Objects.equals(getObservedAt(), that.getObservedAt()) &&
                Objects.equals(getValue(), that.getValue());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getTrendId(), isReliable, getObservedAt(), getValue());
    }

    @Override
    public String toString() {
        return "MetasysTrendSample{" +
                "trendId='" + trendId + '\'' +
                ", objectId='" + objectId + '\'' +
                ", isReliable=" + isReliable +  '\'' +
                ", observedAt=" + getObservedAt() +
                ", value=" + getValue() +
                '}';
    }
}
