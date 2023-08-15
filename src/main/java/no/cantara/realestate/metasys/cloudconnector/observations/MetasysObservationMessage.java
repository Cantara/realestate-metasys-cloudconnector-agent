package no.cantara.realestate.metasys.cloudconnector.observations;

import no.cantara.realestate.mappingtable.MappedSensorId;
import no.cantara.realestate.mappingtable.SensorId;
import no.cantara.realestate.mappingtable.rec.SensorRecObject;
import no.cantara.realestate.metasys.cloudconnector.automationserver.MetasysTrendSample;
import no.cantara.realestate.metasys.cloudconnector.automationserver.stream.ObservedValue;
import no.cantara.realestate.metasys.cloudconnector.sensors.MeasurementUnit;
import no.cantara.realestate.metasys.cloudconnector.sensors.SensorType;
import no.cantara.realestate.observations.ObservationMessage;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

public class MetasysObservationMessage extends ObservationMessage {

    private final MetasysTrendSample trendSample;
    private final MappedSensorId mappedSensorId;

    private final ObservedValue observedValue;

    public MetasysObservationMessage(MetasysTrendSample trendSample, MappedSensorId mappedSensorId) {
        this.trendSample = trendSample;
        this.mappedSensorId = mappedSensorId;
        observedValue = null;
        buildObservation();
    }

    public MetasysObservationMessage(ObservedValue observedValue, MappedSensorId mappedSensorId) {
        this.observedValue = observedValue;
        this.mappedSensorId = mappedSensorId;
        trendSample = null;
        buildObservation();
    }

    protected void buildObservation() {
        SensorRecObject rec = mappedSensorId.getRec();
        SensorId sensorId = mappedSensorId.getSensorId();
        if (sensorId.getId() != null && sensorId.getId().isEmpty()) {
            setSensorId(sensorId.getId());
        } else {
            setSensorId(rec.getRecId());
        }
        if (rec.getTfm() != null) {
            setTfm(rec.getTfm().getTfm());
        }
        setRealEstate(rec.getRealEstate());
        setBuilding(rec.getBuilding());
        setFloor(rec.getFloor());
        setSection(rec.getSection());
        setServesRoom(rec.getServesRoom());
        setPlacementRoom(rec.getPlacementRoom());
        setSensorType(rec.getSensorType());
        setClimateZone(rec.getClimateZone());
        setElectricityZone(rec.getElectricityZone());
        if (rec.getSensorType() != null) {
            SensorType sensorType = SensorType.from(rec.getSensorType());
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
            observedAt = trendSample.getSampleDate();
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
}
