package no.cantara.realestate.metasys.cloudconnector.automationserver.stream;

import jakarta.ws.rs.sse.InboundSseEvent;
import no.cantara.realestate.metasys.cloudconnector.automationserver.streampoc.ServerSentEvent;
import org.glassfish.jersey.media.sse.InboundEvent;
import org.slf4j.Logger;

import static org.slf4j.LoggerFactory.getLogger;

public class EventInputMapper {
    private static final Logger log = getLogger(EventInputMapper.class);


    public static StreamEvent toStreamEvent(InboundEvent inboundEvent) {
        StreamEvent streamEvent = null;
        String name = inboundEvent.getName();
        if (name == null) {
            streamEvent = new StreamEvent(inboundEvent.getId(), name, inboundEvent.getComment(), inboundEvent.readData());
        } else {
            switch (name) {
                case MetasysOpenStreamEvent.name:
                    streamEvent = new MetasysOpenStreamEvent(inboundEvent.getId(), inboundEvent.readData());
                    break;
                case MetasysObservedValueEvent.name:
                    streamEvent = new MetasysObservedValueEvent(inboundEvent.getId(), inboundEvent.getComment(), inboundEvent.readData());
                    break;
                case MetasysHeartbeatStreamEvent.name:
                    streamEvent = new MetasysHeartbeatStreamEvent(inboundEvent.getId(), inboundEvent.readData());
                    break;
                default:
                    streamEvent = new StreamEvent(inboundEvent.getId(), name, inboundEvent.getComment(), inboundEvent.readData());
            }
        }
        log.trace("Received Event: id: {}, name: {}, comment: {}, data: {}", streamEvent.getId(), streamEvent.getName(), streamEvent.getComment(), streamEvent.getData());
        return streamEvent;
    }

    public static StreamEvent toStreamEvent(InboundSseEvent inboundEvent) {
        StreamEvent streamEvent = null;
        String name = inboundEvent.getName();
        if (name == null) {
            streamEvent = new StreamEvent(inboundEvent.getId(), name, inboundEvent.getComment(), inboundEvent.readData());
        } else {
            switch (name) {
                case MetasysOpenStreamEvent.name:
                    streamEvent = new MetasysOpenStreamEvent(inboundEvent.getId(), inboundEvent.readData());
                    break;
                case MetasysObservedValueEvent.name:
                    streamEvent = new MetasysObservedValueEvent(inboundEvent.getId(), inboundEvent.getComment(), inboundEvent.readData());
                    break;
                case MetasysHeartbeatStreamEvent.name:
                    streamEvent = new MetasysHeartbeatStreamEvent(inboundEvent.getId(), inboundEvent.readData());
                    break;
                default:
                    streamEvent = new StreamEvent(inboundEvent.getId(), name, inboundEvent.getComment(), inboundEvent.readData());
            }
        }
        log.trace("Received Event: id: {}, name: {}, comment: {}, data: {}", streamEvent.getId(), streamEvent.getName(), streamEvent.getComment(), streamEvent.getData());
        return streamEvent;
    }

    public static StreamEvent toStreamEvent(ServerSentEvent serverSentEvent) {
        StreamEvent streamEvent = null;
        String name = serverSentEvent.getEvent();
        if (name == null) {
            streamEvent = new StreamEvent(serverSentEvent.getId(), name, null, serverSentEvent.getData());
        } else {
            switch (name) {
                case MetasysOpenStreamEvent.name:
                    streamEvent = new MetasysOpenStreamEvent(serverSentEvent.getId(), serverSentEvent.getData());
                    break;
                case MetasysObservedValueEvent.name:
                    streamEvent = new MetasysObservedValueEvent(serverSentEvent.getId(), null, serverSentEvent.getData());
                    break;
                case MetasysHeartbeatStreamEvent.name:
                    streamEvent = new MetasysHeartbeatStreamEvent(serverSentEvent.getId(), serverSentEvent.getData());
                    break;
                default:
                    streamEvent = new StreamEvent(serverSentEvent.getId(), name, null, serverSentEvent.getData());
            }
        }
        log.trace("Received Event: id: {}, name: {}, comment: {}, data: {}", streamEvent.getId(), streamEvent.getName(), streamEvent.getComment(), streamEvent.getData());
        return streamEvent;
    }
}
