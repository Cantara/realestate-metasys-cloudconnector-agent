package no.cantara.realestate.metasys.cloudconnector.observations;

import no.cantara.config.ApplicationProperties;
import no.cantara.realestate.automationserver.BasClient;
import no.cantara.realestate.cloudconnector.RealestateCloudconnectorException;
import no.cantara.realestate.cloudconnector.audit.AuditTrail;
import no.cantara.realestate.cloudconnector.audit.InMemoryAuditTrail;
import no.cantara.realestate.distribution.ObservationDistributionClient;
import no.cantara.realestate.mappingtable.MappedSensorId;
import no.cantara.realestate.mappingtable.metasys.MetasysSensorId;
import no.cantara.realestate.mappingtable.repository.MappedIdQuery;
import no.cantara.realestate.mappingtable.repository.MappedIdRepository;
import no.cantara.realestate.mappingtable.repository.MappedIdRepositoryImpl;
import no.cantara.realestate.metasys.cloudconnector.MetasysCloudConnectorException;
import no.cantara.realestate.metasys.cloudconnector.MetasysCloudconnectorApplicationFactory;
import no.cantara.realestate.metasys.cloudconnector.StatusType;
import no.cantara.realestate.metasys.cloudconnector.automationserver.MetasysClient;
import no.cantara.realestate.metasys.cloudconnector.automationserver.MetasysTrendSample;
import no.cantara.realestate.metasys.cloudconnector.distribution.ObservationDistributionServiceStub;
import no.cantara.realestate.metasys.cloudconnector.metrics.MetasysLogonFailed;
import no.cantara.realestate.metasys.cloudconnector.metrics.MetasysMetricsDistributionClient;
import no.cantara.realestate.metasys.cloudconnector.metrics.MetasysTrendsFetchedOk;
import no.cantara.realestate.metasys.cloudconnector.metrics.MetricsDistributionServiceStub;
import no.cantara.realestate.cloudconnector.notifications.NotificationService;
import no.cantara.realestate.cloudconnector.notifications.SlackNotificationService;
import no.cantara.realestate.metasys.cloudconnector.sensors.MetasysConfigImporter;
import no.cantara.realestate.metasys.cloudconnector.sensors.SensorType;
import no.cantara.realestate.metasys.cloudconnector.status.TemporaryHealthResource;
import no.cantara.realestate.observations.ObservationMessage;
import no.cantara.realestate.observations.TrendSample;
import no.cantara.realestate.security.InvalidTokenException;
import no.cantara.realestate.security.LogonFailedException;
import org.slf4j.Logger;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.*;

import static no.cantara.realestate.metasys.cloudconnector.status.TemporaryHealthResource.lastImportedObservationTypes;
import static no.cantara.realestate.utils.StringUtils.hasValue;
import static org.slf4j.LoggerFactory.getLogger;

@Deprecated
public class MappedIdBasedImporter implements TrendLogsImporter {
    private static final Logger log = getLogger(MappedIdBasedImporter.class);
    public static final int FIRST_IMPORT_LATEST_SECONDS = 60 * 60 * 24;

    private final MappedIdQuery mappedIdQuery;
    private final BasClient basClient;
    private final ObservationDistributionClient distributionClient;
    private final MetasysMetricsDistributionClient metricsClient;
    private final MappedIdRepository mappedIdRepository;
    private final AuditTrail auditTrail;
    private List<MappedSensorId> importableTrendIds = new ArrayList<>();
    private final Map<String, Instant> lastSuccessfulImportAt;
    private Timer metricsDistributor;
    private int numberOfSuccessfulImports = 0;
    private int numberOfFailedImports = 0;


    public MappedIdBasedImporter(MappedIdQuery mappedIdQuery, BasClient basClient, ObservationDistributionClient distributionClient,
                                    MetasysMetricsDistributionClient metricsClient, MappedIdRepository mappedIdRepository, AuditTrail auditTrail) {
        this.mappedIdQuery = mappedIdQuery;
        this.basClient = basClient;
        this.distributionClient = distributionClient;
        this.metricsClient = metricsClient;
        this.mappedIdRepository = mappedIdRepository;
        this.auditTrail = auditTrail;
        lastSuccessfulImportAt = new HashMap<>();
    }

    @Override
    public void startup() {
        try {
            importableTrendIds = mappedIdRepository.find(mappedIdQuery);
            log.debug("Found {} trendIds to import. Query: {}", importableTrendIds.size(), mappedIdQuery);
            for (MappedSensorId importableTrendId : importableTrendIds) {
                String sensorId = importableTrendId.getSensorId().getId();
                if (sensorId == null && importableTrendId.getRec() != null) {
                    sensorId = importableTrendId.getRec().getRecId();
                }
                if (hasValue(sensorId)) {
                    auditTrail.logSubscribed(sensorId, "Subsribe to trendId");
                } else {
                    log.warn("MappedSensorId has no sensorId. Skipping import of trendId: {}", importableTrendId);
                }
            }
            metricsClient.openDb();
            lastImportedObservationTypes.loadLastUpdatedStatus();
            TemporaryHealthResource.lastImportedObservationTypes = lastImportedObservationTypes;
//            metricsClient.sendMetrics(new MetasysLogonOk());
            metricsDistributor = new Timer();
            metricsDistributor.schedule(new TimerTask() {
                @Override
                public void run() {
                    int countOfImportsLastSecond = getNumberOfSuccessfulImports();
                    if (countOfImportsLastSecond > 0) {
                        setNumberOfSuccessfulImports(0);
                        metricsClient.sendMetrics(new MetasysTrendsFetchedOk(countOfImportsLastSecond));
                    }
                }
            }, 2000, 1000);
        } catch (LogonFailedException e) {
            //FIXME hvorfor får jeg logon failed her?
            //FIXME add alerting and health
            TemporaryHealthResource.addRegisteredError("Logon to Metasys Failed");
            TemporaryHealthResource.setUnhealthy();
            metricsClient.sendMetrics(new MetasysLogonFailed());
            String message = "Failed to logon to ApiClient. Reason: " + e.getMessage();
            if (e.getCause() != null) {
                message += ". Cause is: " + e.getCause().getMessage();
            }
            log.warn(message);
            throw new RuntimeException("Failed to logon to ApiClient.", e);
        }
    }

    @Override
    public void flush() {
        if (metricsClient != null) {
            metricsClient.flush();
        }
    }

    public void close() {
        metricsClient.closeDb();
        if (lastImportedObservationTypes != null) {
            lastImportedObservationTypes.persistLastUpdatedStatus();
        }
    }

    @Override
    public void importAll() {
        Map<String, Instant> lastImportedAtList = lastImportedObservationTypes.getLastImportedObservationTypes();
        String sensorType = mappedIdQuery.getSensorType();
        Instant lastImportedAt = lastImportedAtList.get(sensorType);
        if (lastImportedAt == null) {
            lastImportedAt = Instant.now();
            lastImportedObservationTypes.updateLastImported(sensorType, lastImportedAt);
        }
        Instant importFromDateTime = getImportFromDateTime();
        importAfterDateTime(sensorType, importFromDateTime);
    }

    protected Instant getImportFromDateTime() {
        Instant importFrom = null;
        int importBeforeInSec = ApplicationProperties.getInstance().asInt("import.start.before.sec", FIRST_IMPORT_LATEST_SECONDS);
        importFrom = Instant. now().minusSeconds(importBeforeInSec);
        log.info("Start import from: {}", importFrom);
        return importFrom;
    }

    @Override
    public void importAllAfterDateTime(Instant fromDateTime) {
        int successfulImport = 0;
        int failedImport = 0;
        for (MappedSensorId mappedSensorId : importableTrendIds) {
            //TODO take and skip need logic
            int take = 200;
            int skip = 0;
            if (mappedSensorId.getSensorId() != null && mappedSensorId.getSensorId() instanceof MetasysSensorId) {

                MetasysSensorId metasysSensorId = (MetasysSensorId) mappedSensorId.getSensorId();
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

    @Override
    public void importFromDay0(String observationType) {

    }

    @Override
    public void importAfterDateTime(String observationType, Instant fromDateTime) {
        log.info("Import all after {}", fromDateTime);
        importAllAfterDateTime(fromDateTime);
        lastImportedObservationTypes.updateLastImported(observationType, Instant.now());
    }

    /*
    Helper to ensure Thread safety.
     */
    void reportSuccessfulImport(String trendId) {

        int successful = getNumberOfSuccessfulImports();
        setNumberOfSuccessfulImports(successful + 1);
    }

    void reportFailedImport(String trendId) {
        int failed = getNumberOfFailedImports();
        setNumberOfFailedImports(failed + 1);
    }

    public synchronized int getNumberOfSuccessfulImports() {
        return numberOfSuccessfulImports;
    }

    public synchronized void setNumberOfSuccessfulImports(int numberOfSuccessfulImports) {
        this.numberOfSuccessfulImports = numberOfSuccessfulImports;
    }

    public synchronized int getNumberOfFailedImports() {
        return numberOfFailedImports;
    }

    public synchronized void setNumberOfFailedImports(int numberOfFailedImports) {
        this.numberOfFailedImports = numberOfFailedImports;
    }


    public static void main(String[] args) throws URISyntaxException, InterruptedException {
        ApplicationProperties config = new MetasysCloudconnectorApplicationFactory()
                .conventions(ApplicationProperties.builder())
                .build();
        String apiUrl = config.get("sd_api_url");
        String username = config.get("sd.api.username");
        String password = config.get("sd.api.password");
        URI apiUri = new URI(apiUrl);
        NotificationService notificationService = new SlackNotificationService();
        BasClient sdClient = MetasysClient.getInstance(username, password, apiUri, notificationService);

        String measurementName = config.get("MEASUREMENT_NAME");
        ObservationDistributionClient observationClient = new ObservationDistributionServiceStub();//Simulator
        MetasysMetricsDistributionClient metricsClient = new MetricsDistributionServiceStub(measurementName);//Simulator

        MappedIdQuery tfm2RecQuery = new MetasysMappedIdQueryBuilder().realEstate("RE1")
                .sensorType(SensorType.co2.name())
                .build();
        String configDirectory = config.get("importdata.directory");
        AuditTrail auditTrail = new InMemoryAuditTrail();
        MappedIdRepository mappedIdRepository = createMappedIdRepository(true, configDirectory, auditTrail);
        MappedIdBasedImporter importer = new MappedIdBasedImporter(tfm2RecQuery, sdClient, observationClient, metricsClient, mappedIdRepository, auditTrail);
        importer.startup();
        log.info("Startup finished.");
        importer.importAllAfterDateTime(Instant.now().minusSeconds(60 * 15));
        metricsClient.flush();
        log.info("Sleeping for 10 sec");
        Thread.sleep(10000);
        log.info("Closing connections");
        importer.close();
        System.exit(0);
    }


    //FIXME duplicate code with MappedIdRepositoryImpl
    private static MappedIdRepository createMappedIdRepository(boolean doImportData, String configDirectory, AuditTrail auditTrail) {
        MappedIdRepository mappedIdRepository = new MappedIdRepositoryImpl();
        if (doImportData) {
            if (!Paths.get(configDirectory).toFile().exists()) {
                throw new MetasysCloudConnectorException("Import of data from " + configDirectory + " failed. Directory does not exist.");
            }
            new MetasysConfigImporter().importMetasysConfig(configDirectory, mappedIdRepository, auditTrail);
        }
        return mappedIdRepository;
    }
    /*
   Primarily used for testing
    */
    protected void addImportableTrendId(MappedSensorId mappedSensorId) {
        importableTrendIds.add(mappedSensorId);
    }

    @Override
    public String toString() {
        return "MappedIdBasedImporter{" +
                "numberOfSuccessfulImports=" + numberOfSuccessfulImports +
                ", numberOfFailedImports=" + numberOfFailedImports +
                ", mappedIdQuery=" + mappedIdQuery +
                '}';
    }
}
