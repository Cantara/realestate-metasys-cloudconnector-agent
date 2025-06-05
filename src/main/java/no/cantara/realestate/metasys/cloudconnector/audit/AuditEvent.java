package no.cantara.realestate.metasys.cloudconnector.audit;

import java.time.Instant;

public class AuditEvent {
    public enum Type {
        FOUND, MISSING, QUEUED, PUBLISHED, SUBSCRIBED, OBSERVED, CREATED, INNITIALIZED, FAILED
    }

    private final Instant timestamp;
    private final String sensorId;
    private final Type type;
    private final String detail;

    public AuditEvent(String sensorId, Type type, String detail) {
        this.timestamp = Instant.now();
        this.sensorId = sensorId;
        this.type = type;
        this.detail = detail;
    }

    public String getDetail() {
        return detail;
    }

    public String getSensorId() {
        return sensorId;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public Type getType() {
        return type;
    }
}

