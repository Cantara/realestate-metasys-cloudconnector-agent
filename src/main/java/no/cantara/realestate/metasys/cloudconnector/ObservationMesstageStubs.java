package no.cantara.realestate.metasys.cloudconnector;

import no.cantara.realestate.observations.ObservationMessage;
import no.cantara.realestate.observations.ObservationMessageBuilder;

import java.time.Instant;

public class ObservationMesstageStubs {

    public static ObservationMessage buildStubObservation() {
        Instant observedAt = Instant.now().minusSeconds(10);
        Instant receivedAt = Instant.now();
        ObservationMessage observationMessage = new ObservationMessageBuilder()
                .withSensorId("rec1")
                .withRealEstate("RE1")
                .withBuilding("Building1")
                .withFloor("04")
                .withSection("Section West")
                .withServesRoom("Room1")
                .withPlacementRoom("Room21")
                .withClimateZone("air1")
                .withElectricityZone("light")
                .withSensorType("temp")
                .withMeasurementUnit("C")
                .withValue(22)
                .withObservationTime(observedAt)
                .withReceivedAt(receivedAt)
                .withTfm("TFM12345")
                .build();
        return observationMessage;
    }
}
