package no.cantara.realestate.metasys.cloudconnector.sensors;

import no.cantara.realestate.cloudconnector.audit.AuditTrail;
import no.cantara.realestate.mappingtable.MappedSensorId;
import no.cantara.realestate.mappingtable.importer.CsvSensorImporter;
import no.cantara.realestate.mappingtable.metasys.MetasysCsvSensorImporter;
import no.cantara.realestate.mappingtable.repository.MappedIdRepository;
import no.cantara.realestate.sensors.metasys.MetasysSensorId;
import org.slf4j.Logger;

import java.io.File;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.slf4j.LoggerFactory.getLogger;

public class MetasysConfigImporter {
    private static final Logger log = getLogger(MetasysConfigImporter.class);

    public static List<MetasysSensorId> importSensorIds(String configDirectory) {
        File importDirectory = new File(configDirectory);
        CsvSensorImporter csvImporter = new MetasysCsvSensorImporter(importDirectory);
        List<MetasysSensorId> sensorIds = new ArrayList<>();

        log.info("Imported {} Metasys Sensor configs from directory {}", sensorIds.size(), importDirectory);
        return sensorIds;
    }



    public long importMetasysConfig(String configDirectory, MappedIdRepository mappedIdRepository, AuditTrail auditTrail) {
        File importDirectory = new File(configDirectory);
        CsvSensorImporter csvImporter = new MetasysCsvSensorImporter(importDirectory);
        List<MappedSensorId> mappedSensorIds = csvImporter.importMappedId("Metasys");
        log.info("Imported {} Metasys Sensor configs from directory {}", mappedSensorIds.size(), importDirectory);
        for (MappedSensorId mappedSensorId : mappedSensorIds) {
            mappedIdRepository.add(mappedSensorId);
            String id = mappedSensorId.getSensorId().getId();
            if (id == null || id.isBlank()) {
                id = mappedSensorId.getRec().getRecId();
                if (id == null || id.isBlank()) {
                    auditTrail.logFailed("Failed" + Instant.now(), "Neider ID or RecId for MappedSensorId: " + mappedSensorId);
                } else {
                    auditTrail.logCreated(id, "MetasysConfigImporter::RecIdFallback");
                }
            } else {
                auditTrail.logCreated(id, "MetasysConfigImporter::SensorId");
            }
        }
        return mappedSensorIds.size();
    }
}
