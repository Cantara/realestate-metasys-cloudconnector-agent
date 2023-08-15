package no.cantara.realestate.metasys.cloudconnector.automationserver.stream;

import java.time.Instant;

public class MetasysHeartbeatStreamEvent extends StreamEvent {
    public static final String name = "heartbeat";

    public MetasysHeartbeatStreamEvent(String id, String data) {
        super(id, name, null, data);
    }
    public Instant getTimestamp() {
        return Instant.parse(getData());
    }

    @Override
    public String toString() {
        return "MetasysHeartbeatStreamEvent{} " + super.toString();
    }
}
