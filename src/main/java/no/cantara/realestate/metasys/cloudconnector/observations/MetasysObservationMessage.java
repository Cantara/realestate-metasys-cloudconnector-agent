package no.cantara.realestate.metasys.cloudconnector.observations;

import no.cantara.realestate.mappingtable.MappedSensorId;
import no.cantara.realestate.mappingtable.SensorId;
import no.cantara.realestate.mappingtable.rec.SensorRecObject;
import no.cantara.realestate.metasys.cloudconnector.automationserver.MetasysTrendSample;
import no.cantara.realestate.metasys.cloudconnector.sensors.MeasurementUnit;
import no.cantara.realestate.metasys.cloudconnector.sensors.SensorType;
import no.cantara.realestate.observations.ObservationMessage;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

public class MetasysObservationMessage extends ObservationMessage {

    private final MetasysTrendSample trendSample;
    private final MappedSensorId mappedSensorId;

    public MetasysObservationMessage(MetasysTrendSample trendSample, MappedSensorId mappedSensorId) {
        this.trendSample = trendSample;
        this.mappedSensorId = mappedSensorId;
        buildObservation();
    }

    protected void buildObservation() {
        SensorRecObject rec = mappedSensorId.getRec();
        SensorId sensorId = mappedSensorId.getSensorId();
        setRecId(sensorId.getId());
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

        Number value = trendSample.getValue();
        if (value instanceof BigDecimal) {
            value = ((BigDecimal) value).setScale(2, RoundingMode.CEILING);
        }
        setObservedValue(value);
        Instant observedAt = trendSample.getSampleDate();
        setObservedAt(observedAt);
        Instant receivedAt = Instant.now();
        setReceivedAt(receivedAt);
    }
}
