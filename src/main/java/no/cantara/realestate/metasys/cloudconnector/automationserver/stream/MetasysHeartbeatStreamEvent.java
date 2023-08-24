package no.cantara.realestate.metasys.cloudconnector.automationserver.stream;

import org.slf4j.Logger;

import java.time.Instant;

import static org.slf4j.LoggerFactory.getLogger;

public class MetasysHeartbeatStreamEvent extends StreamEvent {
    private static final Logger log = getLogger(MetasysHeartbeatStreamEvent.class);
    public static final String name = "object.values.heartbeat";

    public MetasysHeartbeatStreamEvent(String id, String data) {
        super(id, name, null, data);
    }
    /**
     * Returns the timestamp of the heartbeat event.
     * @return the timestamp of the heartbeat event. Will return NULL if the timestamp is unparsable.
     */
    public Instant getTimestamp() {
        if (getData() == null) {
            return null;
        }
        String timestampString = getData().replace("\"", "");
        try {
            return Instant.parse(timestampString);
        } catch (Exception e) {
            log.trace("Could not parse timestamp \"{}\" to Instant", timestampString);
            return null;
        }
    }

    @Override
    public String toString() {
        return "MetasysHeartbeatStreamEvent{} " + super.toString();
    }
}
