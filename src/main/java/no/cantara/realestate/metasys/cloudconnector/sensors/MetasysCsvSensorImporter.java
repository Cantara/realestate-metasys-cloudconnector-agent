package no.cantara.realestate.metasys.cloudconnector.sensors;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import no.cantara.config.ApplicationProperties;
import no.cantara.realestate.csv.CsvCollection;
import no.cantara.realestate.csv.CsvReader;
import no.cantara.realestate.rec.RecTags;
import no.cantara.realestate.sensors.metasys.MetasysSensorId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MetasysCsvSensorImporter {
    private static final Logger log = LoggerFactory.getLogger(MetasysCsvSensorImporter.class);

    public static List<MetasysSensorId> importSensorIdsFromFile(Path filepath) {
        List<MetasysSensorId> sensorIds = new ArrayList();
        CsvReader csvReader = new CsvReader();
        CsvCollection collection = CsvReader.parse(filepath.toString());
        log.debug("ColumnNames: {}", collection.getColumnNames());

        for(Map<String, String> record : collection.getRecords()) {
            String twinId = record.get("DigitalTwinId");
            if (twinId == null || twinId.isEmpty()) {
                twinId = record.get("RecId");
            }
            String metasysObjectId = record.get("MetasysObjectId");
            String metasysObjectReference = record.get("MetasysObjectReference");
            MetasysSensorId sensorId = new MetasysSensorId(twinId, metasysObjectId, metasysObjectReference);
            sensorIds.add(sensorId);
        }
        return sensorIds;
    }

    public static List<MetasysSensorId> importSensorIdsFromDirectory(String directoryName, String prefix) {
        List<MetasysSensorId> sensorIds = new ArrayList<>();
        List<Path> files = findFilesInDirectory(directoryName, prefix);
        for (Path file : files) {
            log.info("Importing sensors from file: {}", file);
            List<MetasysSensorId> collection = importSensorIdsFromFile(file);
            sensorIds.addAll(collection);
        }
        return sensorIds;
    }

    public static List<RecTags> importRecTagsFromDirectory(String directoryName, String prefix) {
        //FIXME
        return List.of();
    }

    public static List<Path> findFilesInDirectory(String directoryName, String prefix) {
        File importDirectory = new File(directoryName);
        if (importDirectory == null || !importDirectory.isDirectory() || !importDirectory.canRead()) {
            log.warn("Cannot find files in directory {}", importDirectory);
            throw new IllegalArgumentException("Directory is not readable: " + importDirectory);
        }
        List<Path> files = new ArrayList<>();
        File[] fileList = importDirectory.listFiles((dir, name) -> name.startsWith(prefix) && name.endsWith(".csv"));
        if (fileList != null) {
            for (File file : fileList) {
                files.add(file.toPath());
            }
        } else {
            log.warn("No files found in directory {}", importDirectory);
        }
        return files;
    }

}

