package no.cantara.realestate.metasys.cloudconnector.observations;

import no.cantara.config.ApplicationProperties;
import no.cantara.config.testsupport.ApplicationPropertiesTestHelper;
import no.cantara.realestate.automationserver.BasClient;
import no.cantara.realestate.distribution.ObservationDistributionClient;
import no.cantara.realestate.mappingtable.MappedSensorId;
import no.cantara.realestate.mappingtable.metasys.MetasysSensorId;
import no.cantara.realestate.mappingtable.rec.SensorRecObject;
import no.cantara.realestate.mappingtable.repository.MappedIdQuery;
import no.cantara.realestate.mappingtable.repository.MappedIdRepository;
import no.cantara.realestate.mappingtable.tfm.Tfm;
import no.cantara.realestate.metasys.cloudconnector.automationserver.SdLogonFailedException;
import no.cantara.realestate.metasys.cloudconnector.metrics.MetasysMetricsDistributionClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URISyntaxException;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class MappedIdBasedImporterTest {

    private ApplicationProperties config;
    private MappedIdBasedImporter importer;
    private BasClient mockBasClient;

    @BeforeEach
    void setUp() {
        ApplicationPropertiesTestHelper.enableMutableSingleton();
        config = ApplicationProperties.builder().buildAndSetStaticSingleton();
        MappedIdQuery mockIdQuery = mock(MappedIdQuery.class);
        mockBasClient = mock(BasClient.class);
        ObservationDistributionClient mockDistributionClient = mock(ObservationDistributionClient.class);
        MetasysMetricsDistributionClient mockMetricsClient = mock(MetasysMetricsDistributionClient.class);
        MappedIdRepository mockRepository = mock(MappedIdRepository.class);
        importer = new MappedIdBasedImporter(mockIdQuery, mockBasClient, mockDistributionClient, mockMetricsClient, mockRepository);
    }

    @Test
    void getImportFromDateTime() {
        Instant expectedTime = Instant.now().minusSeconds(60);
        Instant importFrom = importer.getImportFromDateTime();
        assertTrue(expectedTime.compareTo(importFrom) < 10);
    }

    @Test
    void trendSamplesIsNull() throws SdLogonFailedException, URISyntaxException {
        MappedSensorId mappedSensorStub = buildMetasysMappedId("moId", "moR","recId1", "tfm2");
        importer.addImportableTrendId(mappedSensorStub);
        when(mockBasClient.findTrendSamplesByDate(anyString(),anyInt(),anyInt(),any(Instant.class))).thenReturn(null);
        importer.importAllAfterDateTime(Instant.now());
        verify(mockBasClient, times(1)).findTrendSamplesByDate(anyString(),anyInt(),anyInt(),any());
    }

    MappedSensorId buildMetasysMappedId(String metasysObjectId, String metasysObjectReference, String recId, String tfm) {
        MetasysSensorId sensorId = new MetasysSensorId(metasysObjectId, metasysObjectReference);
        SensorRecObject recObject = new SensorRecObject(recId);
        recObject.setTfm(new Tfm(tfm));
        return new MappedSensorId(sensorId, recObject);
    }
}