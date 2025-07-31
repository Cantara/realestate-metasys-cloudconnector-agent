package no.cantara.realestate.metasys.cloudconnector.trends;

import no.cantara.realestate.csv.CsvCollection;
import no.cantara.realestate.csv.CsvReader;
import no.cantara.realestate.sensors.SensorId;
import no.cantara.realestate.sensors.metasys.MetasysSensorId;
import org.slf4j.Logger;

import java.io.BufferedWriter;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.slf4j.LoggerFactory.getLogger;

@Deprecated //Use no.cantara.realestate.cloudconnector.trends.CsvTrendsLastUpdatedService instead
public class CsvTrendsLastUpdatedService implements TrendsLastUpdatedService {
    private static final Logger log = getLogger(CsvTrendsLastUpdatedService.class);
    Map<MetasysSensorId, Instant> lastUpdated;
    Map<MetasysSensorId, Instant> lastFailed;

    // CSV file headers
    // lastUpdatedFile: sensorId, metasysObjectId, lastUpdatedAt
    // lastFailedFile: sensorId, metasysObjectId, lastFailedAt
    private File lastUpdatedFile;
    private File lastFailedFile;

    public CsvTrendsLastUpdatedService() {
        lastUpdated = new HashMap<>();
        lastFailed = new HashMap<>();
    }

    public CsvTrendsLastUpdatedService(Map<MetasysSensorId, Instant> lastUpdated) {
        this.lastUpdated = lastUpdated;
    }

    public CsvTrendsLastUpdatedService(String lastUpdatedDirectory, String lastUpdatedFile, String lastFailedFile) {
        this();
        Path directoryPath = Paths.get(lastUpdatedDirectory);

        if (!Files.exists(directoryPath)) {
            throw new IllegalArgumentException("Directory does not exist: " + lastUpdatedDirectory + ". Please create the directory before restarting.");
        }
        this.lastUpdatedFile = directoryPath.resolve(lastUpdatedFile).toFile();
        if (!this.lastUpdatedFile.exists()) {
            try (BufferedWriter writer = Files.newBufferedWriter(this.lastUpdatedFile.toPath())) {
                writer.write("sensorId,metasysObjectId,lastUpdatedAt");
            } catch (Exception e) {
                throw new RuntimeException("Failed to create last updated file: " + this.lastUpdatedFile.getAbsolutePath(), e);
            }
        }
        this.lastFailedFile = directoryPath.resolve(lastFailedFile).toFile();
        if (!this.lastFailedFile.exists()) {
            try (BufferedWriter writer = Files.newBufferedWriter(this.lastFailedFile.toPath())) {
                writer.write("sensorId,metasysObjectId,lastFailedAt");
            } catch (Exception e) {
                throw new RuntimeException("Failed to create last failed file: " + this.lastFailedFile.getAbsolutePath(), e);
            }
        }
    }

    @Override
    public void readLastUpdated() {
        CsvCollection collection = CsvReader.parse(lastUpdatedFile.toString());
        log.debug("LastUpdated ColumnNames: {}", collection.getColumnNames());
        for(Map<String, String> record : collection.getRecords()) {
            String sensorId = record.get("sensorId");
            String metasysObjectId = record.get("metasysObjectId");
            String lastUpdatedAtStr = record.get("lastUpdatedAt");

            if (sensorId != null && metasysObjectId != null && lastUpdatedAtStr != null) {
                MetasysSensorId id = new MetasysSensorId(sensorId, metasysObjectId, null);
                Instant lastUpdatedAt = Instant.parse(lastUpdatedAtStr);
                lastUpdated.put(id, lastUpdatedAt);
            }
        }
        log.info("Read {} last updated records from {}", lastUpdated.size(), lastUpdatedFile.getAbsolutePath());
        collection = CsvReader.parse(lastFailedFile.toString());
        log.debug("LastFailed ColumnNames: {}", collection.getColumnNames());
        for(Map<String, String> record : collection.getRecords()) {
            String sensorId = record.get("sensorId");
            String metasysObjectId = record.get("metasysObjectId");
            String lastFailedAtStr = record.get("lastFailedAt");

            if (sensorId != null && metasysObjectId != null && lastFailedAtStr != null) {
                MetasysSensorId id = new MetasysSensorId(sensorId, metasysObjectId, null);
                Instant lastFailedAt = Instant.parse(lastFailedAtStr);
                lastFailed.put(id, lastFailedAt);
            }
        }
    }

    @Override
    public Instant getLastUpdatedAt(SensorId sensorId) {
        Instant matchedLastUpdatedAt = lastUpdated.entrySet().stream()
                .filter(entry -> entry.getKey().getId().equals(sensorId.getId()))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);
        return matchedLastUpdatedAt;
    }


    @Override
    public <T extends SensorId>  void setLastUpdatedAt(SensorId sensorId, Instant lastUpdatedAt) {
        if ( sensorId instanceof  MetasysSensorId && sensorId != null && lastUpdatedAt != null) {
            Instant currentLastUpdatedAt = lastUpdated.get(sensorId);
            if (currentLastUpdatedAt == null || currentLastUpdatedAt.isBefore(lastUpdatedAt)) {
                lastUpdated.put((MetasysSensorId) sensorId, lastUpdatedAt);
            }
        }
    }

    @Override
    public <T extends SensorId> void setLastFailedAt(SensorId sensorId, Instant lastFailedAt) {
        if(sensorId instanceof MetasysSensorId && sensorId != null && lastFailedAt != null) {
            lastFailed.put((MetasysSensorId) sensorId, lastFailedAt);
        }
    }

    @Override
    public <T extends SensorId> void persistLastFailed(List<T> sensorIds) {
        log.info("Persisting last failed for {} sensors", sensorIds.size());
        try (BufferedWriter writer = Files.newBufferedWriter(lastFailedFile.toPath())) {
            writer.write("sensorId,metasysObjectId,lastFailedAt\n");
            for (T sensorId : sensorIds) {
                if (sensorId instanceof MetasysSensorId) {
                    MetasysSensorId id = (MetasysSensorId) sensorId;
                    Instant lastFailedAt = lastFailed.get(id);
                    if (lastFailedAt != null) {
                        writer.write(String.format("%s,%s,%s\n", id.getId(), id.getMetasysObjectId(), lastFailedAt));
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to persist last failed data to file: {}", lastFailedFile.getAbsolutePath(), e);
        }
    }

    @Override
    public <T extends SensorId> void persistLastUpdated(List<T> sensorIds) {
        log.info("Persisting last updated for {} sensors", sensorIds.size());
        try (BufferedWriter writer = Files.newBufferedWriter(lastUpdatedFile.toPath())) {
            writer.write("sensorId,metasysObjectId,lastUpdatedAt\n");
            for (T sensorId : sensorIds) {
                if (sensorId instanceof MetasysSensorId) {
                    MetasysSensorId id = (MetasysSensorId) sensorId;
                    Instant lastUpdatedAt = lastUpdated.get(id);
                    if (lastUpdatedAt != null) {
                        writer.write(String.format("%s,%s,%s\n", id.getId(), id.getMetasysObjectId(), lastUpdatedAt));
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to persist last updated data to file: {}", lastUpdatedFile.getAbsolutePath(), e);
        }
    }

    @Override
    public boolean isHealthy() {
        return true;
    }

    @Override
    public List<String> getErrors() {
        return new ArrayList<>();
    }
}
