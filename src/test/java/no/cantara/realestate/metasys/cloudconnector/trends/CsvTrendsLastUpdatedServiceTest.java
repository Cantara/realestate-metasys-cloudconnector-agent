package no.cantara.realestate.metasys.cloudconnector.trends;

import no.cantara.realestate.sensors.metasys.MetasysSensorId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CsvTrendsLastUpdatedServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void constructor_withExistingDirectory_succeeds() {
        String dir = tempDir.toString();

        CsvTrendsLastUpdatedService service = new CsvTrendsLastUpdatedService(
                dir, "last_updated.csv", "last_failed.csv");

        assertNotNull(service);
        assertTrue(new File(dir, "last_updated.csv").exists(), "last_updated.csv should be created");
        assertTrue(new File(dir, "last_failed.csv").exists(), "last_failed.csv should be created");
    }

    @Test
    void constructor_withMissingDirectory_createsItAutomatically() {
        // Regression test for issue #527: first start should not fail when status dir is missing
        Path missing = tempDir.resolve("status");
        assertFalse(missing.toFile().exists(), "Directory should not exist yet");

        CsvTrendsLastUpdatedService service = new CsvTrendsLastUpdatedService(
                missing.toString(), "last_updated.csv", "last_failed.csv");

        assertNotNull(service);
        assertTrue(missing.toFile().exists(), "Directory should have been created automatically");
        assertTrue(missing.resolve("last_updated.csv").toFile().exists());
        assertTrue(missing.resolve("last_failed.csv").toFile().exists());
    }

    @Test
    void constructor_withNestedMissingDirectories_createsThemAutomatically() {
        Path nested = tempDir.resolve("a/b/status");
        assertFalse(nested.toFile().exists());

        CsvTrendsLastUpdatedService service = new CsvTrendsLastUpdatedService(
                nested.toString(), "last_updated.csv", "last_failed.csv");

        assertNotNull(service);
        assertTrue(nested.toFile().exists());
    }

    @Test
    void constructor_csvFilesAreCreatedWithHeaders() {
        Path statusDir = tempDir.resolve("status");

        new CsvTrendsLastUpdatedService(statusDir.toString(), "last_updated.csv", "last_failed.csv");

        File updatedFile = statusDir.resolve("last_updated.csv").toFile();
        File failedFile = statusDir.resolve("last_failed.csv").toFile();

        assertTrue(updatedFile.exists());
        assertTrue(failedFile.exists());
        assertTrue(updatedFile.length() > 0, "last_updated.csv should contain the CSV header");
        assertTrue(failedFile.length() > 0, "last_failed.csv should contain the CSV header");
    }

    // Regression for #535: the lastUpdatedAt must survive a persist/restart round-trip even though
    // metasysObjectReference is not persisted. Identity is the SensorId, not the full MetasysSensorId.
    @Test
    void lastUpdated_survivesRestart_evenWhenSensorHasObjectReference() {
        String dir = tempDir.toString();
        MetasysSensorId sensor = new MetasysSensorId("Sensor-aaa", "obj-1", "METASYS1:RE1-NAE7/some.reference");
        Instant t1 = Instant.parse("2026-06-10T10:00:00Z");

        CsvTrendsLastUpdatedService before = new CsvTrendsLastUpdatedService(dir, "last_updated.csv", "last_failed.csv");
        before.setLastUpdatedAt(sensor, t1);
        before.persistLastUpdated(List.of(sensor));

        // Simulate restart: a fresh instance reads from disk (reference is lost -> reconstructed as null).
        CsvTrendsLastUpdatedService after = new CsvTrendsLastUpdatedService(dir, "last_updated.csv", "last_failed.csv");
        after.readLastUpdated();

        assertEquals(t1, after.getLastUpdatedAt(sensor),
                "lastUpdatedAt must be found after restart regardless of metasysObjectReference");
    }

    // Regression for #535: lookups match on sensorId only; metasysObjectReference is irrelevant.
    @Test
    void getLastUpdatedAt_matchesById_ignoringObjectReference() {
        String dir = tempDir.toString();
        Instant t1 = Instant.parse("2026-06-10T10:00:00Z");

        CsvTrendsLastUpdatedService service = new CsvTrendsLastUpdatedService(dir, "last_updated.csv", "last_failed.csv");
        service.setLastUpdatedAt(new MetasysSensorId("Sensor-aaa", "obj-1", "ref-A"), t1);

        // Query with the same id but a different (or absent) reference.
        assertEquals(t1, service.getLastUpdatedAt(new MetasysSensorId("Sensor-aaa", "obj-1", null)));
        assertEquals(t1, service.getLastUpdatedAt(new MetasysSensorId("Sensor-aaa", "obj-1", "ref-B")));
    }

    // Regression for #535: persist must merge, not drop, sensors that were not touched this cycle.
    @Test
    void persistLastUpdated_retainsQuietSensors() {
        String dir = tempDir.toString();
        MetasysSensorId quiet = new MetasysSensorId("Sensor-quiet", "obj-q", "ref-q");
        MetasysSensorId active = new MetasysSensorId("Sensor-active", "obj-a", "ref-a");
        Instant tQuiet = Instant.parse("2026-06-10T08:00:00Z");
        Instant tActive = Instant.parse("2026-06-10T09:00:00Z");

        CsvTrendsLastUpdatedService service = new CsvTrendsLastUpdatedService(dir, "last_updated.csv", "last_failed.csv");
        service.setLastUpdatedAt(quiet, tQuiet);
        service.setLastUpdatedAt(active, tActive);

        // A cycle where only 'active' was processed must not drop 'quiet'.
        service.persistLastUpdated(List.of(active));

        CsvTrendsLastUpdatedService reloaded = new CsvTrendsLastUpdatedService(dir, "last_updated.csv", "last_failed.csv");
        reloaded.readLastUpdated();
        assertEquals(tQuiet, reloaded.getLastUpdatedAt(quiet), "quiet sensor must be retained across persist");
        assertEquals(tActive, reloaded.getLastUpdatedAt(active));
    }

    // Regression for #535: a newer observation advances the stored timestamp; an older one does not.
    @Test
    void setLastUpdatedAt_keepsTheLatestTimestamp() {
        String dir = tempDir.toString();
        MetasysSensorId sensor = new MetasysSensorId("Sensor-aaa", "obj-1", "ref-A");
        Instant older = Instant.parse("2026-06-10T10:00:00Z");
        Instant newer = Instant.parse("2026-06-10T11:00:00Z");

        CsvTrendsLastUpdatedService service = new CsvTrendsLastUpdatedService(dir, "last_updated.csv", "last_failed.csv");
        service.setLastUpdatedAt(sensor, newer);
        service.setLastUpdatedAt(sensor, older); // out-of-order/late sample must not roll back

        assertEquals(newer, service.getLastUpdatedAt(sensor));
    }
}
