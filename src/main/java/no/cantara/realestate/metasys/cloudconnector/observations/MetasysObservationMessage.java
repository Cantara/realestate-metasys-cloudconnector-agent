package no.cantara.realestate.metasys.cloudconnector.observations;

import no.cantara.realestate.metasys.cloudconnector.automationserver.MetasysTrendSample;
import no.cantara.realestate.metasys.cloudconnector.automationserver.stream.ObservedValueNumber;
import no.cantara.realestate.metasys.cloudconnector.sensors.MeasurementUnit;
import no.cantara.realestate.metasys.cloudconnector.sensors.SensorType;
import no.cantara.realestate.observations.ObservationMessage;
import no.cantara.realestate.rec.RecTags;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Objects;

public class MetasysObservationMessage extends ObservationMessage {

    private final MetasysTrendSample trendSample;
    private final RecTags recTags;

    private final ObservedValueNumber observedValue;

    public MetasysObservationMessage(MetasysTrendSample trendSample, RecTags recTags) {
        this.trendSample = trendSample;
        this.recTags = recTags;
        observedValue = null;
        buildObservation();
    }

    public MetasysObservationMessage(ObservedValueNumber observedValue, RecTags recTags) {
        this.observedValue = observedValue;
        this.recTags = recTags;
        trendSample = null;
        buildObservation();
    }

    protected void buildObservation() {
            setSensorId(recTags.getTwinId());
        if (recTags.getTfm() != null) {
            setTfm(recTags.getTfm());
        }
        setRealEstate(recTags.getRealEstate());
        setBuilding(recTags.getBuilding());
        setFloor(recTags.getFloor());
        setSection(recTags.getSection());
        setServesRoom(recTags.getServesRoom());
        setPlacementRoom(recTags.getPlacementRoom());
        setSensorType(recTags.getSensorType());
        setClimateZone(recTags.getClimateZone());
        setElectricityZone(recTags.getElectricityZone());
        if (recTags.getSensorType() != null) {
            SensorType sensorType = SensorType.from(recTags.getSensorType());
            MeasurementUnit measurementUnit = MeasurementUnit.mapFromSensorType(sensorType);
            setMeasurementUnit(measurementUnit.name());
        }

        Number value = null;
        Instant observedAt = null;
        if (trendSample != null) {
            value = trendSample.getValue();
            if (value instanceof BigDecimal) {
                value = ((BigDecimal) value).setScale(2, RoundingMode.CEILING);
            }
            observedAt = trendSample.getObservedAt();
        } else if (observedValue != null) {
            value = observedValue.getValue();
            if (value instanceof BigDecimal) {
                value = ((BigDecimal) value).setScale(2, RoundingMode.CEILING);
            }
            observedAt = observedValue.getObservedAt();
        }
        setObservationTime(observedAt);
        Instant receivedAt = Instant.now();
        setValue(value);
        setReceivedAt(receivedAt);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MetasysObservationMessage that = (MetasysObservationMessage) o;
        return Objects.equals(trendSample, that.trendSample) && Objects.equals(recTags, that.recTags) && Objects.equals(observedValue, that.observedValue);
    }

    @Override
    public int hashCode() {
        return Objects.hash(trendSample, recTags, observedValue);
    }

    @Override
    public String toString() {
        return "MetasysObservationMessage{" +
                "trendSample=" + trendSample +
                ", mappedSensorId=" + recTags +
                ", observedValue=" + observedValue +
                "} " + super.toString();
    }
}
