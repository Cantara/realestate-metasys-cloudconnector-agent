package no.cantara.realestate.metasys.cloudconnector.observations;

import java.time.Instant;
import java.util.Map;

public interface LastImportedObservationTypes {
    int loadLastUpdatedStatus();

    Map<String, Instant> getLastImportedObservationTypes();

    void updateLastImported(String observationType, Instant lastUpdatedDateTime);

    void persistLastUpdatedStatus();
}
