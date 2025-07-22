package no.cantara.realestate.metasys.cloudconnector.ingestion;

import no.cantara.config.ApplicationProperties;
import no.cantara.realestate.automationserver.BasClient;
import no.cantara.realestate.automationserver.TrendNotFoundException;
import no.cantara.realestate.cloudconnector.audit.AuditTrail;
import no.cantara.realestate.metasys.cloudconnector.MetasysCloudConnectorException;
import no.cantara.realestate.metasys.cloudconnector.metrics.MetasysMetricsDistributionClient;
import no.cantara.realestate.metasys.cloudconnector.trends.TrendsLastUpdatedService;
import no.cantara.realestate.observations.ObservationListener;
import no.cantara.realestate.observations.ObservedTrendedValue;
import no.cantara.realestate.observations.ObservedValue;
import no.cantara.realestate.observations.TrendSample;
import no.cantara.realestate.plugins.config.PluginConfig;
import no.cantara.realestate.plugins.ingestion.TrendsIngestionService;
import no.cantara.realestate.plugins.notifications.NotificationListener;
import no.cantara.realestate.security.LogonFailedException;
import no.cantara.realestate.sensors.SensorId;
import no.cantara.realestate.sensors.metasys.MetasysSensorId;
import org.slf4j.Logger;

import java.net.URISyntaxException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static no.cantara.realestate.metasys.cloudconnector.utils.MetasysConstants.auditLog;
import static no.cantara.realestate.utils.StringUtils.hasValue;
import static org.slf4j.LoggerFactory.getLogger;

public class MetasysTrendsIngestionService implements TrendsIngestionService {
    private static final Logger log = getLogger(MetasysTrendsIngestionService.class);

    public static final String BAS_URL_KEY = "sd.api.url";
    public final String METRIC_NAME_TRENDSAMPLES_RECEIVED;

    private ApplicationProperties config;
    private ObservationListener observationListener;
    private NotificationListener notificationListener;
    private BasClient metasysApiClient;
    private TrendsLastUpdatedService trendsLastUpdatedService;
    private final AuditTrail auditTrail;
    private final MetasysMetricsDistributionClient metricsClient;

    private ArrayList<SensorId> sensorIds;
    private long numberOfMessagesImported = 0;
    private long numberOfMessagesFailed = 0;
    private boolean isInitialized = false;
    private String apiUrl;
    private boolean isHealthy;
    private Instant lastObservationReceievedAt = null;


    /**
     * Used for testing
     *
     * @param config
     * @param observationListener
     * @param notificationListener
     * @param metasysApiClient
     */
    public MetasysTrendsIngestionService(ApplicationProperties config, ObservationListener observationListener,
                                         NotificationListener notificationListener, BasClient metasysApiClient,
                                         TrendsLastUpdatedService trendsLastUpdatedService, AuditTrail auditTrail, MetasysMetricsDistributionClient metricsClient) {
        sensorIds = new ArrayList<>();
        this.config = config;
        if (config == null || observationListener == null || notificationListener == null || metasysApiClient == null || trendsLastUpdatedService == null) {
            throw new MetasysCloudConnectorException("Failed to create MetasysTrendsIngestionService. " +
                    "One or more of the parameters are null. config has value: " + (config != null)
                    + ", observationListener: " + observationListener
                    + ", notificationListener: " + notificationListener
                    + ", metasysApiClient: " + metasysApiClient
                    + ", trendsLastUpdatedService: "
                    + trendsLastUpdatedService);
        }
        this.observationListener = observationListener;
        this.notificationListener = notificationListener;
        this.metasysApiClient = metasysApiClient;
        this.trendsLastUpdatedService = trendsLastUpdatedService;
        this.auditTrail = auditTrail;
        this.metricsClient = metricsClient;
        this.METRIC_NAME_TRENDSAMPLES_RECEIVED = config.get("metrics.name.trendsamplesReceived", "metasys_trendsamples_received");
    }

    //FIXME Sjekk mot MetasysClient, og hva som kjører denne ingestTrends periodisk i CloudConnector-Common. ScheduledObservationMessageRouter

    @Override
    public void ingestTrends() {
        //Read last updated from file or database TODO should be done automatically when starting the service.
        try {
            trendsLastUpdatedService.readLastUpdated();
        } catch (NullPointerException npe) {
            isHealthy = false;
            MetasysCloudConnectorException de = new MetasysCloudConnectorException("Failed to read last updated. TrendsLastUpdatedService is null. That service must be injected on creation of MetasysTrendsIngestionService.", npe);
            log.warn(de.getMessage());
            throw de;
        }
        log.info("Running ingestTrends for {} sensors", sensorIds.size());


        List<MetasysSensorId> updatedSensors = new ArrayList<>();
        List<MetasysSensorId> failedSensors = new ArrayList<>();


        for (SensorId sensorId : sensorIds) {
            String metasysObjectId = ((MetasysSensorId) sensorId).getMetasysObjectId();
            if (metasysObjectId != null && !metasysObjectId.isEmpty()) {
                auditLog.trace("Ingest__TrendFindSamples__{}__{}", sensorId.getClass(), sensorId.getId());
                try {
                    Instant lastObservedAt = trendsLastUpdatedService.getLastUpdatedAt((MetasysSensorId) sensorId);
                    auditLog.trace("Ingest__TrendLastUpdatedAt__{}__{}__{}", sensorId.getClass(), sensorId.getId(), lastObservedAt);
                    if (lastObservedAt == null) {
                        lastObservedAt = getDefaultLastObservedAt();
                        log.trace("Try import. Use default lastObservedAt for sensorId: {}, trendId: {}, from: {}", sensorId.getTwinId(), metasysObjectId, lastObservedAt);
                    }

                    log.trace("Try import of sensorId: {}, trendId: {} from: {}", sensorId.getTwinId(), metasysObjectId, lastObservedAt);
                    Set<? extends TrendSample> trendSamples = metasysApiClient.findTrendSamplesByDate(metasysObjectId, -1, -1, lastObservedAt);
                    isHealthy = true;
                    if (trendSamples != null && trendSamples.size() > 0) {
                        updateWhenLastObservationReceived();
                        auditTrail.logObservedTrend(sensorId.getId(), "Observed: " + trendSamples.size());
                        auditLog.trace("Ingest__TrendSamplesFound__{}__{}__{}__{}", metasysObjectId, sensorId.getClass(), sensorId.getId(), trendSamples.size());
                        long value = trendSamples.size();
                        metricsClient.sendValue(METRIC_NAME_TRENDSAMPLES_RECEIVED, value);
                    } else {
                        auditLog.trace("Ingest__TrendSamplesFound__{}__{}__{}__{}", metasysObjectId, sensorId.getClass(), sensorId.getId(), 0);
                    }
                    for (TrendSample trendValue : trendSamples) {
                        ObservedValue observedValue = new ObservedTrendedValue(sensorId, trendValue.getValue());
                        if (trendValue.getObservedAt() != null) {
                            observedValue.setObservedAt(trendValue.getObservedAt());
                        }
                        auditLog.trace("Ingest__TrendObserved__{}__{}__{}__{}__{}", metasysObjectId, observedValue.getClass(), observedValue.getSensorId().getId(), observedValue.getValue(), observedValue.getObservedAt());
                        observationListener.observedValue(observedValue);
                        addMessagesImportedCount();
                        trendsLastUpdatedService.setLastUpdatedAt(sensorId, trendValue.getObservedAt());
                    }
                    updatedSensors.add((MetasysSensorId) sensorId);
                } catch (TrendNotFoundException e) {
                    addMessagesFailedCount();
                    trendsLastUpdatedService.setLastFailedAt(sensorId, Instant.now());
                    failedSensors.add((MetasysSensorId) sensorId);
                    auditLog.trace("Ingest__TrendNotFound__{}__{}__{}__{}", metasysObjectId, sensorId.getClass(), sensorId.getId(), e.getMessage());
                    log.debug("Trend not found for TrendId {} sensorId {}.", metasysObjectId, sensorId, e);
                    auditTrail.logFailed(sensorId.getId(), "TrendNotFound");
                } catch (LogonFailedException e) {
                    addMessagesFailedCount();
                    trendsLastUpdatedService.setLastFailedAt(sensorId, Instant.now());
                    failedSensors.add((MetasysSensorId) sensorId);
                    log.error("Failed to logon to Metasys API {} using username {}", apiUrl, config.get("sd.api.username", "admin"), e);
                    throw new MetasysCloudConnectorException("Could not ingest trends for " + getName() + " Logon failed to " + apiUrl + ", using username: " + config.get("sd.api.username", "admin"), e);
                } catch (URISyntaxException e) {
                    addMessagesFailedCount();
                    trendsLastUpdatedService.setLastFailedAt((MetasysSensorId) sensorId, Instant.now());
                    failedSensors.add((MetasysSensorId) sensorId);
                    auditLog.trace("Ingest__Failed__TrendId__{}__sensorId__{}. Reason {}", metasysObjectId, sensorId, e.getMessage());
                } catch (MetasysCloudConnectorException dce) {
                    addMessagesFailedCount();
                    trendsLastUpdatedService.setLastFailedAt((MetasysSensorId) sensorId, Instant.now());
                    failedSensors.add((MetasysSensorId) sensorId);
                    log.debug("Failed to ingest trends for TrendId {} sensorId {}.", metasysObjectId, sensorId, dce);
                    auditLog.trace("Ingest__TrendImportFailed__{}__{}__{}__{}", metasysObjectId, sensorId.getId(), ((MetasysSensorId) sensorId).getMetasysObjectId(), dce.getMessage());
                } catch (Exception e) {
                    addMessagesFailedCount();
                    trendsLastUpdatedService.setLastFailedAt((MetasysSensorId) sensorId, Instant.now());
                    failedSensors.add((MetasysSensorId) sensorId);
                    log.debug("Failed to ingest trends for sensorId {}.", sensorId, e);
                }
            } else {
                auditLog.trace("Ingest__TrendIdMissing__{}__{}__{}__{}__{}", sensorId.getClass(), sensorId.getId(), ((MetasysSensorId) sensorId).getMetasysObjectId());
            }
        }


        trendsLastUpdatedService.persistLastUpdated(updatedSensors);
        trendsLastUpdatedService.persistLastFailed(failedSensors);

    }



    protected Instant getDefaultLastObservedAt() {
        return Instant.now().minus(2, ChronoUnit.HOURS);
    }

    @Override
    public String getName() {
        return "MetasysTrendsIngestionService";
    }

    @Override
    public boolean initialize(PluginConfig pluginConfig) {
        log.trace("MetasysTrendsIngestionService.initialize");
        apiUrl = pluginConfig.asString(BAS_URL_KEY, null);
        if (!hasValue(apiUrl)) {
            throw new MetasysCloudConnectorException("Failed to initialize MetasysTrendsIngestionService. Desigo." + BAS_URL_KEY + " is null or empty. Please set this property.");
        }
        boolean initializationOk = false;

        if (metasysApiClient != null && metasysApiClient.isHealthy()) {
            initializationOk = true;
        }

        return initializationOk;
    }

    @Override
    public void openConnection(ObservationListener observationListener, NotificationListener notificationListener) {
        this.observationListener = observationListener;
        this.notificationListener = notificationListener;
        if (!isInitialized) {
            throw new RuntimeException("Not initialized. Please call initialize() first.");
        }
    }

    @Override
    public void closeConnection() {
        //Do nothing for now. metasysApiClient = null;
    }

    @Override
    public void addSubscriptions(List<SensorId> list) {
        if (sensorIds == null) {
            sensorIds = new ArrayList<>();
        }
        sensorIds.addAll(list);
    }

    @Override
    public void addSubscription(SensorId sensorId) {
        if (sensorIds == null) {
            sensorIds = new ArrayList<>();
        }
        sensorIds.add(sensorId);
    }

    @Override
    public void removeSubscription(SensorId sensorId) {
        if (sensorIds != null) {
            sensorIds.remove(sensorId);
        }
    }

    @Override
    public long getSubscriptionsCount() {
        if (sensorIds != null) {
            return sensorIds.size();
        } else {
            return 0;
        }
    }

    @Override
    public boolean isInitialized() {
        return isInitialized;
    }

    @Override
    public boolean isHealthy() {
        return isHealthy;
    }

    @Override
    public long getNumberOfMessagesImported() {
        return numberOfMessagesImported;
    }

    @Override
    public long getNumberOfMessagesFailed() {
        return numberOfMessagesFailed;
    }

    synchronized void addMessagesImportedCount() {
        numberOfMessagesImported++;
    }

    synchronized void addMessagesFailedCount() {
        numberOfMessagesFailed++;
    }

    protected BasClient getMetasysApiClientRest() {
        return metasysApiClient;
    }

    protected synchronized void updateWhenLastObservationReceived() {
        lastObservationReceievedAt = Instant.ofEpochMilli(System.currentTimeMillis());
    }

    @Override
    public Instant getWhenLastMessageImported() {
        return lastObservationReceievedAt;
    }

    protected void setWhenLastObservationReceivedAt(Instant lastObservationReceievedAt) {
        this.lastObservationReceievedAt = lastObservationReceievedAt;
    }
}
