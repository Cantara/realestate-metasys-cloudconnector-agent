package no.cantara.realestate.metasys.cloudconnector.ingestion;

import no.cantara.config.ApplicationProperties;
import no.cantara.realestate.automationserver.BasClient;
import no.cantara.realestate.cloudconnector.RealestateCloudconnectorException;
import no.cantara.realestate.cloudconnector.audit.AuditTrail;
import no.cantara.realestate.mappingtable.MappedSensorId;
import no.cantara.realestate.metasys.cloudconnector.MetasysCloudConnectorException;
import no.cantara.realestate.metasys.cloudconnector.StatusType;
import no.cantara.realestate.metasys.cloudconnector.automationserver.MetasysTrendSample;
import no.cantara.realestate.metasys.cloudconnector.observations.MetasysObservationMessage;
import no.cantara.realestate.metasys.cloudconnector.trends.TrendsLastUpdatedService;
import no.cantara.realestate.observations.*;
import no.cantara.realestate.plugins.config.PluginConfig;
import no.cantara.realestate.plugins.ingestion.TrendsIngestionService;
import no.cantara.realestate.plugins.notifications.NotificationListener;
import no.cantara.realestate.security.InvalidTokenException;
import no.cantara.realestate.security.LogonFailedException;
import no.cantara.realestate.sensors.SensorId;
import static no.cantara.realestate.metasys.cloudconnector.utils.MetasysConstants.auditLog;

import no.cantara.realestate.sensors.metasys.MetasysSensorId;
import org.slf4j.Logger;

import java.net.URISyntaxException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static no.cantara.realestate.utils.StringUtils.hasValue;
import static org.slf4j.LoggerFactory.getLogger;

public class MetasysTrendsIngestionService implements TrendsIngestionService {
    private static final Logger log = getLogger(MetasysTrendsIngestionService.class);

    public static final String BAS_URL_KEY = "sd.api.url";
    private final AuditTrail auditTrail;
    private ObservationListener observationListener;
    private NotificationListener notificationListener;
    private BasClient metasysApiClient;

    private TrendsLastUpdatedService trendsLastUpdatedService;
    private ApplicationProperties config;
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
    public MetasysTrendsIngestionService(ApplicationProperties config, ObservationListener observationListener, NotificationListener notificationListener, BasClient metasysApiClient, TrendsLastUpdatedService trendsLastUpdatedService, AuditTrail auditTrail) {
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
        //TODO: Remove this commented code when the MetasysApiClient is ready to use.
        /*
        for (SensorId sensorId : sensorIds) {
            if (sensorId instanceof MetasysSensorId) {
                updatedSensors.add((MetasysSensorId) sensorId);
                trendsLastUpdatedService.setLastUpdatedAt(sensorId, Instant.now());
            }
        }

         */

        for (SensorId sensorId : sensorIds) {
            String trendId = ((MetasysSensorId)sensorId).getMetasysObjectId();
            if (trendId != null && !trendId.isEmpty()) {
                auditLog.trace("Ingest__TrendFindSamples__{}__{}", sensorId.getClass(), sensorId.getId());
                try {
                    Instant lastObservedAt = trendsLastUpdatedService.getLastUpdatedAt((MetasysSensorId) sensorId);
                    auditLog.trace("Ingest__TrendLastUpdatedAt__{}__{}__{}", sensorId.getClass(), sensorId.getId(), lastObservedAt);
                    if (lastObservedAt == null) {
                        lastObservedAt = getDefaultLastObservedAt();
                    }
                    Set<? extends TrendSample> trendSamples = metasysApiClient.findTrendSamplesByDate(trendId, -1, -1, lastObservedAt.minusSeconds(600));
                    isHealthy = true;
                    if (trendSamples != null && trendSamples.size() > 0) {
                        updateWhenLastObservationReceived();
                        auditTrail.logObservedTrend(sensorId.getId(), "Observed: " + trendSamples.size());
                        auditLog.trace("Ingest__TrendSamplesFound__{}__{}__{}__{}", trendId, sensorId.getClass(), sensorId.getId(), trendSamples.size());
                    } else {
                        auditLog.trace("Ingest__TrendSamplesFound__{}__{}__{}__{}", trendId, sensorId.getClass(), sensorId.getId(), 0);
                    }
                    for (TrendSample trendValue : trendSamples) {
                        ObservedValue observedValue = new ObservedTrendedValue(sensorId, trendValue.getValue());
                        if (trendValue.getObservedAt() != null) {
                            observedValue.setObservedAt(trendValue.getObservedAt());
                        }
                        auditLog.trace("Ingest__TrendObserved__{}__{}__{}__{}__{}", trendId, observedValue.getClass(), observedValue.getSensorId().getId(), observedValue.getValue(), observedValue.getObservedAt());
                        observationListener.observedValue(observedValue);
                        addMessagesImportedCount();
                        trendsLastUpdatedService.setLastUpdatedAt((MetasysSensorId) sensorId, trendValue.getObservedAt());
                    }
                    updatedSensors.add((MetasysSensorId) sensorId);
                } catch (LogonFailedException e) {
                    addMessagesFailedCount();
                    trendsLastUpdatedService.setLastFailedAt((MetasysSensorId) sensorId, Instant.now());
                    failedSensors.add((MetasysSensorId) sensorId);
                    log.error("Failed to logon to Metasys API {} using username {}", apiUrl, config.get("sd.api.username", "admin"), e);
                    throw new MetasysCloudConnectorException("Could not ingest trends for " + getName() + " Logon failed to " + apiUrl + ", using username: " + config.get("sd.api.username", "admin"), e);
                } catch (URISyntaxException e) {
                    addMessagesFailedCount();
                    trendsLastUpdatedService.setLastFailedAt((MetasysSensorId) sensorId, Instant.now());
                    failedSensors.add((MetasysSensorId) sensorId);
                    auditLog.trace("Ingest__Failed__TrendId__{}__sensorId__{}. Reason {}", trendId, sensorId, e.getMessage());
                } catch (MetasysCloudConnectorException dce) {
                    addMessagesFailedCount();
                    trendsLastUpdatedService.setLastFailedAt((MetasysSensorId) sensorId, Instant.now());
                    failedSensors.add((MetasysSensorId) sensorId);
                    log.debug("Failed to ingest trends for TrendId {} sensorId {}.", trendId, sensorId, dce);
                    auditLog.trace("Ingest__TrendImportFailed__{}__{}__{}__{}", trendId, sensorId.getId(), ((MetasysSensorId) sensorId).getMetasysObjectId(), dce.getMessage());
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

   String tmp = """
    void ingestTrendsMapped() {
        int successfulImport = 0;
        int failedImport = 0;
        for (MappedSensorId mappedSensorId : importableTrendIds) {
            //TODO take and skip need logic
            int take = 200;
            int skip = 0;
            if (mappedSensorId.getSensorId() != null && mappedSensorId.getSensorId() instanceof no.cantara.realestate.mappingtable.metasys.MetasysSensorId) {

                no.cantara.realestate.mappingtable.metasys.MetasysSensorId metasysSensorId = (no.cantara.realestate.mappingtable.metasys.MetasysSensorId) mappedSensorId.getSensorId();
                String trendId = metasysSensorId.getMetasysObjectId();
                if (trendId == null) {
                    log.warn("TrendId is null for sensorId: {}", metasysSensorId);
                } else {
                    Instant importFrom = lastSuccessfulImportAt.get(trendId);
                    if (importFrom == null) {
                        importFrom = fromDateTime;
                    }

                    log.trace("Try import of trendId: {} from: {}", trendId, importFrom);
                    try {
                        Set<TrendSample> trendSamples;
                        try {
                            //Ensure thread safety, and re-login if needed
                            trendSamples = (Set<TrendSample>) basClient.findTrendSamplesByDate(trendId, take, skip, importFrom);
                        } catch (InvalidTokenException e) {
                            throw new RealestateCloudconnectorException("This Code should not be reached. InvalidTokenException", e);
                            /*
                            log.warn("Invalid token. Try to re-logon to Metasys.");
                            if (userToken != null) {
                                trendSamples = (Set<TrendSample>) basClient.findTrendSamplesByDate(trendId, take, skip, importFrom);
                            } else {
                                log.warn("Failed to re-logon to Metasys. No userToken.");
                                throw new MetasysCloudConnectorException("Missing userToken. Failed to re-logon to Metasys. TrendId: " + trendId, e, StatusType.RETRY_NOT_POSSIBLE);
                            }

                             */
                        }
                        if (trendSamples != null) {
                            log.trace("Found {} samples for trendId: {}", trendSamples.size(), trendId);
                            if (trendSamples.size() > 0) {
                                lastSuccessfulImportAt.put(trendId, Instant.now());
                                String sensorId = null;
                                if (mappedSensorId.getRec() != null) {
                                    sensorId = mappedSensorId.getRec().getRecId();
                                }
                                auditTrail.logObservedTrend(sensorId, "Observed: " + trendSamples.size());
                            }

                            successfulImport++;
                            for (TrendSample trendSample : trendSamples) {
                                ObservationMessage observationMessage = new MetasysObservationMessage((MetasysTrendSample) trendSample, mappedSensorId);
                                distributionClient.publish(observationMessage);
                            }
                            metricsClient.populate(trendSamples, mappedSensorId);
                            reportSuccessfulImport(trendId);
                        } else {
                            log.trace("Missing TrendSamples for trendId: {}",trendId);
                        }
                    } catch (URISyntaxException e) {
                        MetasysCloudConnectorException se = new MetasysCloudConnectorException("Import of trend: {} is not possible now. Reason: {}", e, StatusType.RETRY_NOT_POSSIBLE);
                        log.warn("Import of trend: {} is not possible now. URI to SD server is misconfigured. Reason: {} ", trendId, e.getMessage());
                        reportFailedImport(trendId);
                        throw se;
                    } catch (InvalidTokenException e) {
                        MetasysCloudConnectorException ite = new MetasysCloudConnectorException("Failed to fetch observations " +
                                "due to invalid token. Re-logon did not help.", e, StatusType.RETRY_NOT_POSSIBLE);
                        log.warn("Import of trend: {} is not possible now. Reason: {} ", trendId, e.getMessage());
                        reportFailedImport(trendId);
                        throw ite;
                    } catch (LogonFailedException e) {
                        MetasysCloudConnectorException se = new MetasysCloudConnectorException("Failed to logon to SD server.", e, StatusType.RETRY_NOT_POSSIBLE);
                        log.warn("Import of trend: {} is not possible now. Reason: {} ", trendId, e.getMessage());
                        reportFailedImport(trendId);
                        throw se;
                    } catch (MetasysCloudConnectorException e) {
                        log.trace("Failed to import trendId {} for tfm2rec: {}. Reason: {}", trendId, mappedSensorId, e.getMessage());
                        log.trace("cause:", e);
                        reportFailedImport(trendId);
                        failedImport++;
                    } catch (Exception e) {
                        MetasysCloudConnectorException se = new MetasysCloudConnectorException("Failed to import trendId " + trendId, e, StatusType.RETRY_MAY_FIX_ISSUE);
                        log.trace("Failed to import trendId {} for tfm2rec: {}. Reason: {}", trendId, mappedSensorId, se.getMessage());
                        log.trace("cause:", e);
                        reportFailedImport(trendId);
                        failedImport++;
                    }
                }
            } else {
                log.warn("SensorId is not a MetasysSensorId. Skipping import of sensorId: {}", mappedSensorId.getSensorId());
                continue;
            }
        }
        log.trace("Tried to import {}. Successful {}. Failed {}", importableTrendIds.size(), successfulImport, failedImport);
    }
    
""";

    protected Instant getDefaultLastObservedAt() {
        return Instant.now().minus(30, ChronoUnit.DAYS);
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
        /*
        if (metasysApiClient != null && !metasysApiClient.isHealthy()) {
            log.warn("MetasysApiClient is unhealthy. {}. Can not initialize MetasysTrendsIngestionService", metasysApiClient);

            if (metasysApiClient instanceof MetasysClient) {
                log.info("metasysApiClient is null or unhealty. Creating a new one. {}", metasysApiClient);
                String username = config.asString("sd.api.username", "admin");
                String password = config.asString("sd.api.password", "admin");
                try {
                    ((MetasysClient) metasysApiClient).logon(username, password, notificationListener);
                } catch (LogonFailedException e) {

                    log.error("Failed to logon to Desigo CC API {} using username {}", apiUrl, username, e);
                    throw new MetasysCloudConnectorException("Could not open connection for " + getName() + " Logon failed to " + apiUrl + ", using username: " + username, e);
                }
                initializationOk = true;

            } else if (metasysApiClient instanceof SdClientSimulator) {
                ((SdClientSimulator) metasysApiClient).logon();
                initializationOk = true;
            }
        } else if (metasysApiClient == null) {
            log.warn("metasysApiClient is null. {}", metasysApiClient);
            initializationOk = false;
        } else if (metasysApiClient.isHealthy()) {
            log.trace("metasysApiClient is healthy. {}", metasysApiClient);
            initializationOk = true;
        }
        isInitialized = initializationOk;
    */
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
