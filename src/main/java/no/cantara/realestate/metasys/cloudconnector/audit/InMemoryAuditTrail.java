package no.cantara.realestate.metasys.cloudconnector.audit;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryAuditTrail {
    private final Map<String, AuditState> states = new ConcurrentHashMap<>();

    public void logCreated(String sensorId, String detail) {
        AuditState sensorIdState = getNullSafeState(sensorId);
        sensorIdState.setCreated(sensorId, detail);
    }

    public void logSubscribed(String sensorId, String detail) {
        AuditState sensorIdState = getNullSafeState(sensorId);
        sensorIdState.setSubscribed(sensorId, detail);
    }

    public void logObserved(String sensorId, String detail) {
        AuditState sensorIdState = getNullSafeState(sensorId);
        sensorIdState.setObserved(sensorId, detail);
    }

    public Optional<AuditState> getState(String sensorId) {
        return Optional.ofNullable(states.get(sensorId));
    }
    private AuditState getNullSafeState(String sensorId) {
        if (sensorId == null || sensorId.isBlank()) {
            throw new IllegalArgumentException("Sensor ID cannot be null or blank");
        }
        AuditState sensorIdState = states.get(sensorId);
        if (sensorIdState == null) {
            sensorIdState = new AuditState(sensorId);
            states.put(sensorId, sensorIdState);
        }
        return sensorIdState;
    }

    public Map<String, AuditState> getAll() {
        return Collections.unmodifiableMap(states);
    }

    public void logFailed(String sensorId, String comment) {
        AuditState sensorIdState = getNullSafeState(sensorId);
        sensorIdState.addEvent(new AuditEvent(sensorId, AuditEvent.Type.FAILED, comment));
    }
}

