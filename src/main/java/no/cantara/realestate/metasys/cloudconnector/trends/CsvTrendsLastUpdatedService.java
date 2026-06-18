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

    // Identity is the SensorId (the "Sensor-..." twinId). metasysObjectId is extra info, kept only
    // as an output column for readability - it is intentionally NOT part of the key. Keying by the
    // full MetasysSensorId would fold metasysObjectReference into equals/hashCode, and since the
    // reference is never persisted it can never round-trip, so reloaded keys could never match the
    // live subscription keys. See issue #535.
    Map<String, Instant> lastUpdated;
    Map<String, Instant> lastFailed;
    // sensorId -> metasysObjectId, purely for the output column.
    private final Map<String, String> metasysObjectIdById = new HashMap<>();

    // CSV file headers
    // lastUpdatedFile: sensorId, metasysObjectId, lastUpdatedAt
    // lastFailedFile: sensorId, metasysObjectId, lastFailedAt
    private File lastUpdatedFile;
    private File lastFailedFile;

    public CsvTrendsLastUpdatedService() {
        lastUpdated = new HashMap<>();
        lastFailed = new HashMap<>();
    }

    public CsvTrendsLastUpdatedService(String lastUpdatedDirectory, String lastUpdatedFile, String lastFailedFile) {
        this();
        Path directoryPath = Paths.get(lastUpdatedDirectory);

        if (!Files.exists(directoryPath)) {
            try {
                Files.createDirectories(directoryPath);
                log.info("Created directory for trend tracking: {}", directoryPath.toAbsolutePath());
            } catch (Exception e) {
                throw new IllegalArgumentException(
                        "Directory does not exist and could not be created: " + directoryPath.toAbsolutePath() +
                        ". Please create the directory manually before restarting.", e);
            }
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

            if (sensorId != null && lastUpdatedAtStr != null) {
                Instant lastUpdatedAt = Instant.parse(lastUpdatedAtStr);
                lastUpdated.put(sensorId, lastUpdatedAt);
                rememberMetasysObjectId(sensorId, metasysObjectId);
            }
        }
        log.info("Read {} last updated records from {}", lastUpdated.size(), lastUpdatedFile.getAbsolutePath());
        collection = CsvReader.parse(lastFailedFile.toString());
        log.debug("LastFailed ColumnNames: {}", collection.getColumnNames());
        for(Map<String, String> record : collection.getRecords()) {
            String sensorId = record.get("sensorId");
            String metasysObjectId = record.get("metasysObjectId");
            String lastFailedAtStr = record.get("lastFailedAt");

            if (sensorId != null && lastFailedAtStr != null) {
                Instant lastFailedAt = Instant.parse(lastFailedAtStr);
                lastFailed.put(sensorId, lastFailedAt);
                rememberMetasysObjectId(sensorId, metasysObjectId);
            }
        }
    }

    @Override
    public Instant getLastUpdatedAt(SensorId sensorId) {
        if (sensorId == null || sensorId.getId() == null) {
            return null;
        }
        return lastUpdated.get(sensorId.getId());
    }


    @Override
    public <T extends SensorId>  void setLastUpdatedAt(SensorId sensorId, Instant lastUpdatedAt) {
        String sensorUpdateTracker = "TrackSensor-";
        if (log.isTraceEnabled() && sensorId != null) {
            sensorUpdateTracker += sensorId.getId();
        }
        if (sensorId != null && sensorId.getId() != null && lastUpdatedAt != null) {
            log.trace("{}-Setting last updated at for sensorId: {}, lastUpdatedAt: {}", sensorUpdateTracker,sensorId, lastUpdatedAt);
            String id = sensorId.getId();
            Instant currentLastUpdatedAt = lastUpdated.get(id);
            if (currentLastUpdatedAt == null || currentLastUpdatedAt.isBefore(lastUpdatedAt)) {
                log.trace("{}-Updating last updated at for sensorId: {} from current value {} to new value {}",
                        sensorUpdateTracker, sensorId, currentLastUpdatedAt, lastUpdatedAt);
                lastUpdated.put(id, lastUpdatedAt);
            } else {
                log.trace("{}-Not updating last updated at for sensorId: {} because current value {} is equal to, or newer than new value {}",
                       sensorUpdateTracker, sensorId, currentLastUpdatedAt, lastUpdatedAt);
            }
            rememberMetasysObjectId(sensorId);
        } else {
            log.trace("{}-Attempted to set last updated at for null or invalid sensorId: {}, lastUpdatedAt: {}", sensorUpdateTracker, sensorId, lastUpdatedAt);
        }
    }

    @Override
    public <T extends SensorId> void setLastFailedAt(SensorId sensorId, Instant lastFailedAt) {
        if (sensorId != null && sensorId.getId() != null && lastFailedAt != null) {
            lastFailed.put(sensorId.getId(), lastFailedAt);
            rememberMetasysObjectId(sensorId);
        }
    }

    @Override
    public <T extends SensorId> void persistLastFailed(List<T> sensorIds) {
        // Merge: persist everything we know, not just the sensors handled this cycle, so quiet
        // sensors are not dropped from the file on rewrite. See issue #535.
        rememberMetasysObjectIds(sensorIds);
        log.info("Persisting last failed for {} sensors", lastFailed.size());
        try (BufferedWriter writer = Files.newBufferedWriter(lastFailedFile.toPath())) {
            writer.write("sensorId,metasysObjectId,lastFailedAt\n");
            for (Map.Entry<String, Instant> entry : lastFailed.entrySet()) {
                writer.write(String.format("%s,%s,%s\n", entry.getKey(),
                        metasysObjectIdById.getOrDefault(entry.getKey(), ""), entry.getValue()));
            }
        } catch (Exception e) {
            log.error("Failed to persist last failed data to file: {}", lastFailedFile.getAbsolutePath(), e);
        }
    }

    @Override
    public <T extends SensorId> void persistLastUpdated(List<T> sensorIds) {
        // Merge: persist everything we know, not just the sensors handled this cycle, so quiet
        // sensors are not dropped from the file on rewrite. See issue #535.
        rememberMetasysObjectIds(sensorIds);
        log.info("Persisting last updated for {} sensors", lastUpdated.size());
        try (BufferedWriter writer = Files.newBufferedWriter(lastUpdatedFile.toPath())) {
            writer.write("sensorId,metasysObjectId,lastUpdatedAt\n");
            for (Map.Entry<String, Instant> entry : lastUpdated.entrySet()) {
                writer.write(String.format("%s,%s,%s\n", entry.getKey(),
                        metasysObjectIdById.getOrDefault(entry.getKey(), ""), entry.getValue()));
            }
        } catch (Exception e) {
            log.error("Failed to persist last updated data to file: {}", lastUpdatedFile.getAbsolutePath(), e);
        }
    }

    private <T extends SensorId> void rememberMetasysObjectIds(List<T> sensorIds) {
        if (sensorIds == null) {
            return;
        }
        for (T sensorId : sensorIds) {
            rememberMetasysObjectId(sensorId);
        }
    }

    private void rememberMetasysObjectId(SensorId sensorId) {
        if (sensorId instanceof MetasysSensorId && sensorId.getId() != null) {
            rememberMetasysObjectId(sensorId.getId(), ((MetasysSensorId) sensorId).getMetasysObjectId());
        }
    }

    private void rememberMetasysObjectId(String sensorId, String metasysObjectId) {
        if (sensorId != null && metasysObjectId != null && !metasysObjectId.isEmpty()) {
            metasysObjectIdById.put(sensorId, metasysObjectId);
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
