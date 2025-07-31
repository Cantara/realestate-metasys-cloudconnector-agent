package no.cantara.realestate.metasys.cloudconnector.trends;

import no.cantara.realestate.sensors.SensorId;

import java.time.Instant;
import java.util.List;

@Deprecated // Use no.cantara.realestate.cloudconnector.trends.TrendsLastUpdatedService instead
public interface TrendsLastUpdatedService {
    void readLastUpdated();
    Instant getLastUpdatedAt(SensorId sensorId);
    <T extends SensorId> void setLastUpdatedAt(SensorId sensorId, Instant lastUpdatedAt);
    <T extends SensorId> void setLastFailedAt(SensorId sensorId, Instant lastFailedAt);

    <T extends SensorId> void persistLastUpdated(List<T> sensorIds);

    <T extends SensorId> void persistLastFailed(List<T> sensorIds);
    boolean isHealthy();
    List<String> getErrors();
}