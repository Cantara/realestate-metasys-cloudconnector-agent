package no.cantara.realestate.metasys.cloudconnector;

import no.cantara.config.ApplicationProperties;
import no.cantara.realestate.mappingtable.repository.MappedIdRepository;
import no.cantara.realestate.mappingtable.repository.MappedIdRepositoryImpl;
import no.cantara.realestate.metasys.cloudconnector.sensors.MetasysConfigImporter;
import org.slf4j.Logger;

import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.slf4j.LoggerFactory.getLogger;

class RepositoryWiringManualTest {
    private static final Logger log = getLogger(RepositoryWiringManualTest.class);
    private final ApplicationProperties config;
    MappedIdRepositoryImpl mappedIdRepository;

    public RepositoryWiringManualTest() {
        this.config = ApplicationProperties.builder().defaults().buildAndSetStaticSingleton();
    }

    public static void main(String[] args) {
        RepositoryWiringManualTest repositoryTest = new RepositoryWiringManualTest();
        repositoryTest.createMappedIdRepository(true);
        assertTrue(repositoryTest.mappedIdRepository.size() > 0);

    }

    protected MappedIdRepository createMappedIdRepository(boolean doImportData) {
        mappedIdRepository = new MappedIdRepositoryImpl();
        if (doImportData) {
            String configDirectory = config.get("importdata.directory");
            if (!Paths.get(configDirectory).toFile().exists()) {
                throw new MetasysCloudConnectorException("Import of data from " + configDirectory + " failed. Directory does not exist.");
            }
            new MetasysConfigImporter().importMetasysConfig(configDirectory, mappedIdRepository);
        }
        return mappedIdRepository;
    }

}