package no.cantara.realestate.metasys.cloudconnector.audit;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class AuditState {

    List<AuditEvent> events = new ArrayList<>();
    private AuditEvent lastObservedTrendEvent = null;
    private AuditEvent lastObservedStreamEvent = null;
    private AuditEvent lastObservedPresentValueEvent = null;
    private volatile Instant lastObservedTimestamp = null;

    public AuditState(String sensorId) {
        // Initialize with a FOUND event to indicate the sensorId is being tracked
        addEvent(new AuditEvent(sensorId, AuditEvent.Type.INNITIALIZED, "Tracking started for sensorId: " + sensorId));
    }
    public AuditState(String sensorId, AuditEvent.Type type, String detail) {
        AuditEvent event = new AuditEvent(sensorId, type, detail);
        addEvent(event);
    }

    public void addEvent(AuditEvent event) {
        events.add( event);
    }

    public void setSubscribed(String sensorId, String comment) {
        addEvent(new AuditEvent(sensorId, AuditEvent.Type.SUBSCRIBED, comment));
    }


    public void setCreated(String sensorId, String comment) {
        addEvent(new AuditEvent(sensorId, AuditEvent.Type.CREATED, comment));
    }

    public List<AuditEvent> allEventsByTimestamp() {
        List<AuditEvent> allEvents = new ArrayList<>(events);
        allEvents.addAll(events);
        if (lastObservedTrendEvent != null) {
            allEvents.add(lastObservedTrendEvent);
        }
        if (lastObservedStreamEvent != null) {
            allEvents.add(lastObservedStreamEvent);
        }
        if (lastObservedPresentValueEvent != null) {
            allEvents.add(lastObservedPresentValueEvent);
        }
        return allEvents.stream().sorted(Comparator.comparing(AuditEvent::getTimestamp)).collect(Collectors.toList());
    }

    public AuditEvent getLastObservedPresentValueEvent() {
        return lastObservedPresentValueEvent;
    }

    public void setLastObservedPresentValueEvent(AuditEvent lastObservedPresentValueEvent) {
        this.lastObservedPresentValueEvent = lastObservedPresentValueEvent;
        setLastObservedTimestamp(lastObservedPresentValueEvent.getTimestamp());
    }

    public AuditEvent getLastObservedStreamEvent() {
        return lastObservedStreamEvent;
    }

    public void setLastObservedStreamEvent(AuditEvent lastObservedStreamEvent) {
        this.lastObservedStreamEvent = lastObservedStreamEvent;
        setLastObservedTimestamp(lastObservedStreamEvent.getTimestamp());
    }

    public Instant getLastObservedTimestamp() {
        return lastObservedTimestamp;
    }

    public void setLastObservedTimestamp(Instant lastObservedTimestamp) {
        this.lastObservedTimestamp = lastObservedTimestamp;
    }

    public AuditEvent getLastObservedTrendEvent() {
        return lastObservedTrendEvent;
    }

    public void setLastObservedTrendEvent(AuditEvent lastObservedTrendEvent) {
        this.lastObservedTrendEvent = lastObservedTrendEvent;
        setLastObservedTimestamp(lastObservedTrendEvent.getTimestamp());
    }
}

