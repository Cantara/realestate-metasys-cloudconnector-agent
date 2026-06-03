package no.cantara.realestate.metasys.cloudconnector.trends;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;

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
}
