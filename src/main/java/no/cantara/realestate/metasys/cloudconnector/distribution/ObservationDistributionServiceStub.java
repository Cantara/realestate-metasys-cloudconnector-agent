package no.cantara.realestate.metasys.cloudconnector.distribution;

import no.cantara.realestate.distribution.ObservationDistributionClient;
import no.cantara.realestate.mappingtable.MappedSensorId;
import no.cantara.realestate.mappingtable.rec.SensorRecObject;
import no.cantara.realestate.metasys.cloudconnector.automationserver.MetasysTrendSample;
import no.cantara.realestate.metasys.cloudconnector.sensors.MeasurementUnit;
import no.cantara.realestate.metasys.cloudconnector.sensors.SensorType;
import no.cantara.realestate.observations.ObservationMessage;
import no.cantara.realestate.utils.LimitedArrayList;
import org.slf4j.Logger;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.slf4j.LoggerFactory.getLogger;

public class ObservationDistributionServiceStub implements ObservationDistributionClient {
    private static final Logger log = getLogger(ObservationDistributionServiceStub.class);

    public static final int DEFAULT_MAX_SIZE = 10000;
    private final List<ObservationMessage> observedMessages;
    private boolean isConnected = false;
    private long numberOfMessagesObserved = 0;

    public ObservationDistributionServiceStub() {
        this(DEFAULT_MAX_SIZE);
    }

    public ObservationDistributionServiceStub(int maxSize) {
        observedMessages = new LimitedArrayList(maxSize);
    }

    @Override
    public String getName() {
        return "ObservationDistributionServiceStub";
    }

    @Override
    public void openConnection() {
        log.info("Opening Connection to Stub");
        isConnected = true;
    }

    @Override
    public void closeConnection() {
        log.info("Closing Connection to Stub");
        isConnected = false;
    }

    @Override
    public boolean isConnectionEstablished() {
        return isConnected;
    }

    @Override
    public void publish(ObservationMessage message) {
        boolean added = observedMessages.add(message);
        if (added) {
            log.trace("Added 1 message");
            numberOfMessagesObserved ++;
        } else {
            observedMessages.remove(0);
            publish(message);
        }
    }

    @Override
    public long getNumberOfMessagesObserved() {
        return numberOfMessagesObserved;
    }


    public void publishAll(Set<MetasysTrendSample> trendSamples, MappedSensorId mappedSensorId) {
        ObservationMessage messageTemplate;
        SensorRecObject sensorRecObject = mappedSensorId.getRec();
        String recId = sensorRecObject.getRecId();
        String tfm = null;
        if (sensorRecObject.getTfm() != null) {
            tfm = sensorRecObject.getTfm().getTfm();
        };
        String realEstate = sensorRecObject.getRealEstate();
        String building = sensorRecObject.getBuilding();
        String floor = sensorRecObject.getFloor();
        String section = sensorRecObject.getSection();
        String servesRoom = sensorRecObject.getServesRoom();
        String placementRoom = sensorRecObject.getPlacementRoom();
        String climateZone = sensorRecObject.getClimateZone();
        String electrcityZone = sensorRecObject.getElectricityZone();
        String name = sensorRecObject.getName();
        String sensorTypeString = sensorRecObject.getSensorType();
        SensorType sensorType = SensorType.from(sensorTypeString);
        MeasurementUnit measurementUnit = MeasurementUnit.mapFromSensorType(sensorType);
        messageTemplate = new ObservationMessage(recId, tfm, realEstate, building, floor, section, servesRoom, placementRoom, climateZone, electrcityZone, name, sensorType.name(), measurementUnit.name());
        messageTemplate.setClimateZone(sensorRecObject.getClimateZone());
        messageTemplate.setElectricityZone(sensorRecObject.getElectricityZone());
        for (MetasysTrendSample trendSample : trendSamples) {
            try {
                ObservationMessage observationMessage = (ObservationMessage) messageTemplate.clone();

                Number value = trendSample.getValue();
                if (value instanceof BigDecimal) {
                    value = ((BigDecimal) value).setScale(2, RoundingMode.CEILING);
                }
                observationMessage.setValue(value);
                Instant observedAt = trendSample.getSampleDate();
                observationMessage.setObservationTime(observedAt);
                Instant receivedAt = Instant.now();
                observationMessage.setReceivedAt(receivedAt);
                publish(observationMessage);
            } catch (CloneNotSupportedException e) {
                log.warn("{} should be clonable.", messageTemplate);
            }
        }
    }

    @Override
    public List<ObservationMessage> getObservedMessages() {
        return observedMessages;
    }
}
