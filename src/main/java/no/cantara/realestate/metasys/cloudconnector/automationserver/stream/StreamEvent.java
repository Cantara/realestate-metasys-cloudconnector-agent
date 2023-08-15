package no.cantara.realestate.metasys.cloudconnector.automationserver.stream;

import java.time.Instant;

public class StreamEvent {
    private final String id;
    private final String name;
    private final String comment;
    private final String data;

    private Instant observedAt;

    private Instant receivedAt;

    public StreamEvent(String id, String name) {
        this(id, name, null, null);
    }

    public StreamEvent(String id, String name, String comment, String data) {
        this.id = id;
        this.name = name;
        this.comment = comment;
        this.data = data;
        this.receivedAt = Instant.now();
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getComment() {
        return comment;
    }

    public String getData() {
        return data;
    }

    public Instant getObservedAt() {
        return observedAt;
    }

    public void setObservedAt(Instant observedAt) {
        this.observedAt = observedAt;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }

    public void setReceivedAt(Instant receivedAt) {
        this.receivedAt = receivedAt;
    }

    @Override
    public String toString() {
        return "StreamEvent{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", comment='" + comment + '\'' +
                ", data='" + data + '\'' +
                ", observedAt=" + observedAt +
                ", receivedAt=" + receivedAt +
                '}';
    }
}
