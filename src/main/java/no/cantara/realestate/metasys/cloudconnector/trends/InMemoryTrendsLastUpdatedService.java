package no.cantara.realestate.metasys.cloudconnector.trends;

import no.cantara.realestate.sensors.SensorId;
import no.cantara.realestate.sensors.metasys.MetasysSensorId;
import org.slf4j.Logger;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.slf4j.LoggerFactory.getLogger;

public class InMemoryTrendsLastUpdatedService implements TrendsLastUpdatedService {
    private static final Logger log = getLogger(InMemoryTrendsLastUpdatedService.class);
    Map<MetasysSensorId, Instant> lastUpdated;
    Map<MetasysSensorId, Instant> lastFailed;

    public InMemoryTrendsLastUpdatedService() {
        lastUpdated = new HashMap<>();
        lastFailed = new HashMap<>();
    }

    public InMemoryTrendsLastUpdatedService(Map<MetasysSensorId, Instant> lastUpdated) {
        this.lastUpdated = lastUpdated;
    }

    @Override
    public void readLastUpdated() {
    }

    @Override
    public Instant getLastUpdatedAt(SensorId sensorId) {
        return lastUpdated.get(sensorId);
    }


    @Override
    public <T extends SensorId>  void setLastUpdatedAt(SensorId sensorId, Instant lastUpdatedAt) {
        if ( sensorId instanceof  MetasysSensorId && sensorId != null && lastUpdatedAt != null) {
            lastUpdated.put((MetasysSensorId) sensorId, lastUpdatedAt);
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
        log.info("Simulating persisting last updated for {} sensors", sensorIds.size());
    }

    @Override
    public <T extends SensorId> void persistLastUpdated(List<T> sensorIds) {
        log.info("Simulating persisting last updated for {} sensors", sensorIds.size());
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
