package no.cantara.realestate.metasys.cloudconnector.automationserver.stream;

import java.time.Instant;

public abstract class ObservedValue<T> {
    private String id;
    protected final T value;

    private Instant observedAt;
    private Instant receivedAt;

    private String itemReference;

    public ObservedValue(String id, T value, String itemReference) {
        this.id = id;
        this.value = value;
        this.itemReference = itemReference;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public abstract T getValue();

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

    public String getItemReference() {
        return itemReference;
    }

    public void setItemReference(String itemReference) {
        this.itemReference = itemReference;
    }

    @Override
    public String toString() {
        return "ObservedValue{" +
                "id='" + id + '\'' +
                ", value=" + value +
                ", observedAt=" + observedAt +
                ", receivedAt=" + receivedAt +
                ", itemReference='" + itemReference + '\'' +
                '}';
    }
}
