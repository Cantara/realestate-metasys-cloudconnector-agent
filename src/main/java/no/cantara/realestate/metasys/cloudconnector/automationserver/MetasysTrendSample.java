package no.cantara.realestate.metasys.cloudconnector.automationserver;

import javax.json.bind.annotation.JsonbProperty;
import java.time.Instant;
import java.util.Objects;

/*
    {
		"value": {
			"value": 9398.001,
			"units": "https://gp-sxd9e-113/api/v2/enumSets/507/members/19"
		},
		"timestamp": "2020-09-16T05:20:00Z",
		"isReliable": true
	},
 */
public class MetasysTrendSample {
    private String trendId = null;

    private Boolean isReliable;
    @JsonbProperty("timestamp")
    private Instant sampleDate;
    private Value value;

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

    public Instant getSampleDate() {
        return sampleDate;
    }

//    public void setSampleDate(Instant sampleDate) {
//        this.sampleDate = sampleDate;
//    }

    public void setTimestamp(String timestamp) {
//        DateTimeFormatter formatter = DateTimeFormatter.ISO_LOCAL_DATE;
//        LocalDate parsedDate = LocalDate.parse(timestamp, formatter);
        this.sampleDate = Instant.parse(timestamp);
    }

    public boolean isValid() {
        return true; //FIXME validate MetasysTrendSample
    }

    public Number getValue() {
        Number valNum = null;
        if (value != null) {
            valNum = value.getValue();
        }
        return valNum;
    }
    public void setValueDeep(Integer valueDeep) {
        if (value == null) {
            value = new Value();
        }
        value.setValue(valueDeep);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MetasysTrendSample that = (MetasysTrendSample) o;
        return Objects.equals(getTrendId(), that.getTrendId()) &&
                Objects.equals(isReliable, that.isReliable) &&
                Objects.equals(getSampleDate(), that.getSampleDate()) &&
                Objects.equals(getValue(), that.getValue());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getTrendId(), isReliable, getSampleDate(), getValue());
    }

    @Override
    public String toString() {
        return "MetasysTrendSample{" +
                "trendId='" + trendId + '\'' +
                ", isReliable=" + isReliable +
                ", sampleDate=" + sampleDate +
                ", value=" + value +
                '}';
    }
}
