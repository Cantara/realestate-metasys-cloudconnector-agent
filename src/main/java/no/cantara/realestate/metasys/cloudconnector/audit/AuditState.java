package no.cantara.realestate.metasys.cloudconnector.audit;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class AuditState {

    Map<AuditEvent.Type, AuditEvent> events = new ConcurrentHashMap<>();

    public AuditState(String sensorId) {
        // Initialize with a FOUND event to indicate the sensorId is being tracked
        addEvent(new AuditEvent(sensorId, AuditEvent.Type.INNITIALIZED, "Tracking started for sensorId: " + sensorId));
    }
    public AuditState(String sensorId, AuditEvent.Type type, String detail) {
        AuditEvent event = new AuditEvent(sensorId, type, detail);
        addEvent(event);
    }

    public void addEvent(AuditEvent event) {
        events.put(event.getType(), event);
    }

    public void setSubscribed(String sensorId, String comment) {
        addEvent(new AuditEvent(sensorId, AuditEvent.Type.SUBSCRIBED, comment));
    }

    public void setObserved(String sensorId, String comment) {
        addEvent(new AuditEvent(sensorId, AuditEvent.Type.OBSERVED, comment));
    }

    public void setCreated(String sensorId, String comment) {
        addEvent(new AuditEvent(sensorId, AuditEvent.Type.CREATED, comment));
    }

    public List<AuditEvent> getall() {
        return List.copyOf(events.values());
    }

    public Instant getLastUpdated() {
        Instant lastUpdated = null;
        AuditEvent lastObservedEvent = events.get(AuditEvent.Type.OBSERVED);
        if (lastObservedEvent != null) {
            lastUpdated = lastObservedEvent.getTimestamp();
        }
        return lastUpdated;
    }
}

