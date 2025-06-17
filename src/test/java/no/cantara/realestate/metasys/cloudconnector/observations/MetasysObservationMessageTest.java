package no.cantara.realestate.metasys.cloudconnector.observations;

import no.cantara.realestate.mappingtable.MappedSensorId;
import no.cantara.realestate.mappingtable.SensorId;
import no.cantara.realestate.mappingtable.rec.SensorRecObject;
import no.cantara.realestate.mappingtable.tfm.Tfm;
import no.cantara.realestate.metasys.cloudconnector.automationserver.MetasysTrendSample;
import no.cantara.realestate.rec.RecTags;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MetasysObservationMessageTest {

    private MetasysTrendSample trendSample;
    private RecTags rec;
    private String sensorId;

    @BeforeEach
    void setUp() {
        trendSample = mock(MetasysTrendSample.class);
        rec = mock(RecTags.class);
        sensorId = "Sensor-rec1";
        when(rec.getSensorId()).thenReturn(null);
        when(rec.getTwinId()).thenReturn(sensorId);
    }

    @Test
    void setSensorIdWhenEmpty() {

        when(rec.getSensorId()).thenReturn(null);
        when(rec.getTwinId()).thenReturn(sensorId);
        MetasysObservationMessage message = new MetasysObservationMessage(trendSample, rec);
        assertEquals(sensorId, message.getSensorId());
    }

    @Test
    void verifyTfmIsSet() {
        String tfm = "testTfm";
        when(rec.getTfm()).thenReturn(tfm);
        MetasysObservationMessage message = new MetasysObservationMessage(trendSample, rec);
        assertEquals(tfm, message.getTfm());

    }
}