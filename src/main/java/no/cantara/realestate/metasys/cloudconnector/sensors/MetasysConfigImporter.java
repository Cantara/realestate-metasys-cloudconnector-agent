package no.cantara.realestate.metasys.cloudconnector.sensors;

import no.cantara.realestate.mappingtable.MappedSensorId;
import no.cantara.realestate.mappingtable.importer.CsvSensorImporter;
import no.cantara.realestate.mappingtable.metasys.MetasysCsvSensorImporter;
import no.cantara.realestate.mappingtable.repository.MappedIdRepository;
import org.slf4j.Logger;

import java.io.File;
import java.util.List;

import static org.slf4j.LoggerFactory.getLogger;

public class MetasysConfigImporter {
    private static final Logger log = getLogger(MetasysConfigImporter.class);


    public long importMetasysConfig(String configDirectory, MappedIdRepository mappedIdRepository) {
        File importDirectory = new File(configDirectory);
        CsvSensorImporter csvImporter = new MetasysCsvSensorImporter(importDirectory);
        List<MappedSensorId> mappedSensorIds = csvImporter.importMappedId("Metasys");
        log.debug("metasysSensors: {}", mappedSensorIds.size());
        for (MappedSensorId mappedSensorId : mappedSensorIds) {
            mappedIdRepository.add(mappedSensorId);
        }
        return mappedSensorIds.size();
    }
}
