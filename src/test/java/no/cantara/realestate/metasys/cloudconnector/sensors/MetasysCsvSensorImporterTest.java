package no.cantara.realestate.metasys.cloudconnector.sensors;

import no.cantara.realestate.sensors.SensorId;
import no.cantara.realestate.sensors.metasys.MetasysSensorId;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MetasysCsvSensorImporterTest {


    @Test
    void findFilesTest() {
        List<Path> files = MetasysCsvSensorImporter.findFilesInDirectory("./src/test/resources/test-import-data","Metasys");
        assertNotNull(files);
        assertEquals(1, files.size(), "Should find one file in test-import-data directory");
    }

    @Test
    void importSensorIdsFromFileTest() {
        Path filePath = Path.of("./src/test/resources/test-import-data/MetasysRecImported.csv");
        List<MetasysSensorId> sensorIds = MetasysCsvSensorImporter.importSensorIdsFromFile(filePath);
        assertNotNull(sensorIds);
        assertEquals(2, sensorIds.size(), "Should import two sensor IDs from the CSV file");
        assertFalse(sensorIds.isEmpty(), "Should import sensor IDs from the CSV file");
        SensorId sensorId = sensorIds.get(1);
        assertEquals("Sensor-9f5f0d65-5dab-495a-93b4-123456", sensorId.getTwinId(), "First sensor ID should match expected value");
        assertEquals("d1d75efd-0652-5dd4-bc5c-72bba7b33ff2", sensorId.getIdentifier(MetasysSensorId.METASYS_OBJECT_ID));
        assertEquals("anObjectReference", sensorId.getIdentifier(MetasysSensorId.METASYS_OBJECT_REFERENCE));
    }

    @Test
    void importSensorIdsFromDirectoryTest() {
        List<MetasysSensorId> sensorIds = MetasysCsvSensorImporter.importSensorIdsFromDirectory("./src/test/resources/test-import-data", "Metasys");
        assertNotNull(sensorIds);
        assertEquals(2, sensorIds.size(), "Should import two sensor IDs from the CSV file");
        assertFalse(sensorIds.isEmpty(), "Should import sensor IDs from the CSV file");
        assertEquals("Sensor-640f4b48-4f87-4337-93e4-123456", sensorIds.get(0).getTwinId(), "First sensor ID should match expected value");
    }
}