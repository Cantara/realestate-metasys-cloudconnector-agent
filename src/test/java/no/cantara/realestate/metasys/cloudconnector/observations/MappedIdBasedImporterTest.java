package no.cantara.realestate.metasys.cloudconnector.observations;

import no.cantara.config.ApplicationProperties;
import no.cantara.realestate.distribution.ObservationDistributionClient;
import no.cantara.realestate.mappingtable.repository.MappedIdQuery;
import no.cantara.realestate.mappingtable.repository.MappedIdRepository;
import no.cantara.realestate.metasys.cloudconnector.automationserver.SdClient;
import no.cantara.realestate.metasys.cloudconnector.distribution.MetricsDistributionClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class MappedIdBasedImporterTest {

    private ApplicationProperties config;
    private MappedIdBasedImporter importer;

    @BeforeEach
    void setUp() {
        config = ApplicationProperties.builder().buildAndSetStaticSingleton();
        MappedIdQuery mockIdQuery = mock(MappedIdQuery.class);
        SdClient mockBasClient = mock(SdClient.class);
        ObservationDistributionClient mockDistributionClient = mock(ObservationDistributionClient.class);
        MetricsDistributionClient mockMetricsClient = mock(MetricsDistributionClient.class);
        MappedIdRepository mockRepository = mock(MappedIdRepository.class);
        importer = new MappedIdBasedImporter(mockIdQuery, mockBasClient, mockDistributionClient, mockMetricsClient, mockRepository);
    }

    @Test
    void getImportFromDateTime() {
        Instant expectedTime = Instant.now().minusSeconds(60);
        Instant importFrom = importer.getImportFromDateTime();
        assertTrue(expectedTime.compareTo(importFrom) < 10);
    }
}