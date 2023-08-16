package no.cantara.realestate.metasys.cloudconnector.automationserver.stream;


import java.time.Instant;

public class MetasysObservedValueEvent extends StreamEvent {
    public static final String name = "object.values.update";
    private ObservedValue observedValue;

    public MetasysObservedValueEvent(String id, String comment, String data) {
        super(id, name, comment, data);
        this.observedValue = StreamEventMapper.mapFromJson(data);
        observedValue.setReceivedAt(getReceivedAt());
        if (observedValue.getObservedAt() == null) {
            if (observedValue.getReceivedAt() == null) {
                observedValue.setObservedAt(Instant.now());
            } else {
                observedValue.setObservedAt(observedValue.getReceivedAt());
            }
            observedValue.setObservedAt(Instant.now());
        }
    }

    public MetasysObservedValueEvent(String id, String comment, String data, ObservedValue observedValue) {
        super(id, name, comment, data);
        this.observedValue = observedValue;
    }

    public ObservedValue getObservedValue() {
        return observedValue;
    }

    @Override
    public String toString() {
        return "MetasysObservedValueEvent{" +
                "observedValue=" + observedValue +
                "} " + super.toString();
    }
}
