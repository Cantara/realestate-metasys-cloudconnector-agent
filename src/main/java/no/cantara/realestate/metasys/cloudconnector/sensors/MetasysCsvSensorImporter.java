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
        List<RecTags> recTagsList = new ArrayList<>();
        List<Path> files = findFilesInDirectory(directoryName, prefix);
        for (Path file : files) {
            log.info("Importing RecTags from file: {}", file);
            List<RecTags> fileRecTags = importRecTagsFromFile(file);
            recTagsList.addAll(fileRecTags);
        }
        return recTagsList;
    }

    private static List<RecTags> importRecTagsFromFile(Path file) {
        //RecId,Tfm,MetasysObjectReference,MetasysObjectId,Name,Description,RealEstate,Building,Section,Floor,ServesRoom,PlacementRoom,SensorType,MeasurementUnit,Interval
        List<RecTags> recTagsList = new ArrayList<>();
        CsvCollection collection = CsvReader.parse(file.toString());
        log.debug("ColumnNames: {}", collection.getColumnNames());
        for (Map<String, String> record : collection.getRecords()) {
            String recId = record.get("RecId");
            if (recId == null || recId.isEmpty()) {
                log.warn("RecId is missing in record: {}", record);
                continue;
            }
            RecTags recTags = new RecTags(recId);
            recTags.setSensorSystem("Metasys");
            String tfm = record.get("Tfm");
            recTags.setTfm(tfm);
            String name = record.get("Name");
            recTags.setName(name);
//            String description = record.get("Description");
//            recTags.setDescription(description);
            String realEstate = record.get("RealEstate");
            recTags.setRealEstate(realEstate);
            String building = record.get("Building");
            recTags.setBuilding(building);
            String section = record.get("Section");
            recTags.setSection(section);
            String floor = record.get("Floor");
            recTags.setFloor(floor);
            String servesRoom = record.get("ServesRoom");
            recTags.setServesRoom(servesRoom);
            String placementRoom = record.get("PlacementRoom");
            recTags.setPlacementRoom(placementRoom);
            String sensorType = record.get("SensorType");
            recTags.setSensorType(sensorType);
            String measurementUnit = record.get("MeasurementUnit");
            recTags.setMeasurementUnit(measurementUnit);
            recTagsList.add(recTags);
        }
     return  recTagsList;
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

