package no.cantara.realestate.metasys.cloudconnector.automationserver.stream;

import org.glassfish.jersey.media.sse.InboundEvent;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EventInputMapperTest {


    @Test
    void toHeartbeatEvent() {
        //StreamEvent{id='e62caefc-bd44-4bfb-b4ef-3599666ee726:76', name='object.values.heartbeat', comment='null', data='"2023-08-23T05:23:27.1474987Z"', observedAt=null, receivedAt=2023-08-23T05:23:27.222922986Z}
        InboundEvent inboundEvent = createInboundEvent("e62caefc-bd44-4bfb-b4ef-3599666ee726:76", "object.values.heartbeat", null, "\"2023-08-23T05:23:27.1474987Z\"");
        assertEquals("e62caefc-bd44-4bfb-b4ef-3599666ee726:76", inboundEvent.getId());
        StreamEvent streamEvent = EventInputMapper.toStreamEvent(inboundEvent);
        assertEquals("e62caefc-bd44-4bfb-b4ef-3599666ee726:76", streamEvent.getId());
        assertEquals("object.values.heartbeat", streamEvent.getName());
        assertEquals(null, streamEvent.getComment());
        assertEquals("\"2023-08-23T05:23:27.1474987Z\"", streamEvent.getData());
        assertTrue(streamEvent instanceof MetasysHeartbeatStreamEvent);
        MetasysHeartbeatStreamEvent metasysHeartbeatStreamEvent = (MetasysHeartbeatStreamEvent) streamEvent;
        Instant excpectedInstant = Instant.parse("2023-08-23T05:23:27.1474987Z");
        assertEquals(excpectedInstant, metasysHeartbeatStreamEvent.getTimestamp());
    }
    @Test
    void toHeartbeatEventUnparsableTimestamp() {
        InboundEvent inboundEvent = createInboundEvent("anything", "object.values.heartbeat", null, "\"unparsable:Z\"");
        StreamEvent streamEvent = EventInputMapper.toStreamEvent(inboundEvent);
        assertEquals("\"unparsable:Z\"", streamEvent.getData());
        assertTrue(streamEvent instanceof MetasysHeartbeatStreamEvent);
        assertEquals(null, ((MetasysHeartbeatStreamEvent)streamEvent).getTimestamp());
    }

    @Test
    void toHeartbeatEventTimestampIsNull() {
        InboundEvent inboundEvent = createInboundEvent("anything", "object.values.heartbeat", null, null);
        StreamEvent streamEvent = EventInputMapper.toStreamEvent(inboundEvent);
        assertEquals(null, streamEvent.getData());
        assertTrue(streamEvent instanceof MetasysHeartbeatStreamEvent);
        assertEquals(null, ((MetasysHeartbeatStreamEvent)streamEvent).getTimestamp());
    }

    protected InboundEvent createInboundEvent(String id, String name, String comment, String data) {
        InboundEvent inboundEvent =  mock(InboundEvent.class);
        when(inboundEvent.getId()).thenReturn(id);
        when(inboundEvent.getName()).thenReturn(name);
        when(inboundEvent.getComment()).thenReturn(comment);
        when(inboundEvent.readData()).thenReturn(data);
                return inboundEvent;
    }

}