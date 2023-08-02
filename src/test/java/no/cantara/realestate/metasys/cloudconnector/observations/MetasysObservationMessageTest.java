package no.cantara.realestate.metasys.cloudconnector.observations;

import no.cantara.realestate.mappingtable.MappedSensorId;
import no.cantara.realestate.mappingtable.SensorId;
import no.cantara.realestate.mappingtable.rec.SensorRecObject;
import no.cantara.realestate.metasys.cloudconnector.automationserver.MetasysTrendSample;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MetasysObservationMessageTest {

    @Test
    void setSensorIdWhenEmpty() {
        MetasysTrendSample trendSample = mock(MetasysTrendSample.class);
        MappedSensorId mappedSensorId = mock(MappedSensorId.class);
        SensorRecObject rec = mock(SensorRecObject.class);
        SensorId sensorId = mock(SensorId.class);
        when(mappedSensorId.getSensorId()).thenReturn(sensorId);
        when(mappedSensorId.getRec()).thenReturn(rec);
        when(sensorId.getId()).thenReturn(null);
        when(rec.getRecId()).thenReturn("rec1");
        MetasysObservationMessage message = new MetasysObservationMessage(trendSample, mappedSensorId);
        assertEquals("rec1", message.getSensorId());
    }
}