package no.cantara.realestate.metasys.cloudconnector.observations;

import no.cantara.realestate.metasys.cloudconnector.status.TemporaryHealthResource;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

public class InMemoryLastImportedObservationTypes implements LastImportedObservationTypes {
    private final Map<String, Instant> lastImported;

    public InMemoryLastImportedObservationTypes() {
        lastImported = new HashMap<>();
    }

    @Override
    public int loadLastUpdatedStatus() {
        return -1;
    }

    @Override
    public Map<String, Instant> getLastImportedObservationTypes() {
        return lastImported;
    }

    @Override
    public void updateLastImported(String observationType, Instant lastUpdatedDateTime) {
        lastImported.put(observationType, lastUpdatedDateTime);
        TemporaryHealthResource.lastImportedObservationTypes = this;
    }

    @Override
    public void persistLastUpdatedStatus() {

    }

    @Override
    public String toString() {
        return "InMemoryLastImportedObservationTypes{" +
                "lastImported=" + lastImported +
                '}';
    }
}
