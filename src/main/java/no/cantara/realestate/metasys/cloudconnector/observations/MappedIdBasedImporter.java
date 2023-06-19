package no.cantara.realestate.metasys.cloudconnector.observations;

import no.cantara.config.ApplicationProperties;
import no.cantara.realestate.distribution.ObservationDistributionClient;
import no.cantara.realestate.mappingtable.MappedSensorId;
import no.cantara.realestate.mappingtable.metasys.MetasysSensorId;
import no.cantara.realestate.mappingtable.repository.MappedIdQuery;
import no.cantara.realestate.mappingtable.repository.MappedIdRepository;
import no.cantara.realestate.mappingtable.repository.MappedIdRepositoryImpl;
import no.cantara.realestate.metasys.cloudconnector.MetasysCloudConnectorException;
import no.cantara.realestate.metasys.cloudconnector.MetasysCloudconnectorApplicationFactory;
import no.cantara.realestate.metasys.cloudconnector.StatusType;
import no.cantara.realestate.metasys.cloudconnector.automationserver.MetasysApiClientRest;
import no.cantara.realestate.metasys.cloudconnector.automationserver.MetasysTrendSample;
import no.cantara.realestate.metasys.cloudconnector.automationserver.SdClient;
import no.cantara.realestate.metasys.cloudconnector.automationserver.SdLogonFailedException;
import no.cantara.realestate.metasys.cloudconnector.distribution.*;
import no.cantara.realestate.metasys.cloudconnector.sensors.MetasysConfigImporter;
import no.cantara.realestate.metasys.cloudconnector.sensors.SensorType;
import no.cantara.realestate.metasys.cloudconnector.status.TemporaryHealthResource;
import no.cantara.realestate.observations.ObservationMessage;
import org.slf4j.Logger;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.*;

import static no.cantara.realestate.metasys.cloudconnector.status.TemporaryHealthResource.lastImportedObservationTypes;
import static org.slf4j.LoggerFactory.getLogger;

public class MappedIdBasedImporter implements TrendLogsImporter {
    private static final Logger log = getLogger(MappedIdBasedImporter.class);
    public static final int FIRST_IMPORT_LATEST_SECONDS = 60 * 60 * 24;

    private final MappedIdQuery mappedIdQuery;
    private final SdClient basClient;
    private final ObservationDistributionClient distributionClient;
    private final MetricsDistributionClient metricsClient;
    private final MappedIdRepository mappedIdRepository;
    private List<MappedSensorId> importableTrendIds = new ArrayList<>();
    private final Map<String, Instant> lastSuccessfulImportAt;
    private Timer metricsDistributor;
    private int numberOfSuccessfulImports = 0;

    public MappedIdBasedImporter(MappedIdQuery mappedIdQuery, SdClient basClient, ObservationDistributionClient distributionClient, MetricsDistributionClient metricsClient, MappedIdRepository mappedIdRepository) {
        this.mappedIdQuery = mappedIdQuery;
        this.basClient = basClient;
        this.distributionClient = distributionClient;
        this.metricsClient = metricsClient;
        this.mappedIdRepository = mappedIdRepository;
        lastSuccessfulImportAt = new HashMap<>();
    }

    @Override
    public void startup() {
        try {
            importableTrendIds = mappedIdRepository.find(mappedIdQuery);
            if (!basClient.isLoggedIn()) {
                basClient.logon();
            }
            metricsClient.openDb();
            lastImportedObservationTypes.loadLastUpdatedStatus();
            TemporaryHealthResource.lastImportedObservationTypes = lastImportedObservationTypes;
            metricsClient.sendMetrics(new MetasysLogonOk());
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
        } catch (SdLogonFailedException e) {
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

                MetasysSensorId sensorId = (MetasysSensorId) mappedSensorId.getSensorId();
                String trendId = sensorId.getMetasysObjectId();
                if (trendId == null) {
                    log.warn("TrendId is null for sensorId: {}", sensorId);
                } else {
                    log.trace("****");
                    Instant importFrom = lastSuccessfulImportAt.get(trendId);
                    if (importFrom == null) {
                        importFrom = fromDateTime;
                    }

                    log.trace("Try import of trendId: {} from: {}", trendId, importFrom);
                    try {
                        Set<MetasysTrendSample> trendSamples = basClient.findTrendSamplesByDate(trendId, take, skip, importFrom);
                        log.trace("Found {} samples for trendId: {}", trendSamples.size(), trendId);
                        if (trendSamples.size() > 0) {
                            lastSuccessfulImportAt.put(trendId, Instant.now());
                        }
                        successfulImport++;
                        for (MetasysTrendSample trendSample : trendSamples) {
                            ObservationMessage observationMessage = new MetasysObservationMessage(trendSample, mappedSensorId);
                            distributionClient.publish(observationMessage);
                        }
                        metricsClient.populate(trendSamples, mappedSensorId);
                        reportSuccessfulImport(trendId);
                    } catch (URISyntaxException e) {
                        MetasysCloudConnectorException se = new MetasysCloudConnectorException("Import of trend: {} is not possible now. Reason: {}", e, StatusType.RETRY_NOT_POSSIBLE);
                        log.warn("Import of trend: {} is not possible now. URI to SD server is misconfigured. Reason: {} ", trendId, e.getMessage());
                        throw se;
                    } catch (SdLogonFailedException e) {
                        MetasysCloudConnectorException se = new MetasysCloudConnectorException("Failed to logon to SD server.", e, StatusType.RETRY_NOT_POSSIBLE);
                        log.warn("Import of trend: {} is not possible now. Reason: {} ", trendId, e.getMessage());
                        throw se;
                    } catch (Exception e) {
                        MetasysCloudConnectorException se = new MetasysCloudConnectorException("Failed to import trendId " + trendId, e, StatusType.RETRY_MAY_FIX_ISSUE);
                        log.trace("Failed to import trendId {} for tfm2rec: {}. Reason: {}", trendId, mappedSensorId, se.getMessage());
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

    public synchronized int getNumberOfSuccessfulImports() {
        return numberOfSuccessfulImports;
    }

    public synchronized void setNumberOfSuccessfulImports(int numberOfSuccessfulImports) {
        this.numberOfSuccessfulImports = numberOfSuccessfulImports;
    }


    public static void main(String[] args) throws URISyntaxException, InterruptedException {
        ApplicationProperties config = new MetasysCloudconnectorApplicationFactory()
                .conventions(ApplicationProperties.builder())
                .build();
        String apiUrl = config.get("sd_api_url");
        URI apiUri = new URI(apiUrl);
        SdClient sdClient = new MetasysApiClientRest(apiUri);

        String measurementName = config.get("MEASUREMENT_NAME");
        ObservationDistributionClient observationClient = new ObservationDistributionServiceStub();//Simulator
        MetricsDistributionClient metricsClient = new MetricsDistributionServiceStub(measurementName);//Simulator

        MappedIdQuery tfm2RecQuery = new MetasysMappedIdQueryBuilder().realEstate("RE1")
                .sensorType(SensorType.co2.name())
                .build();
        String configDirectory = config.get("importdata.directory");
        MappedIdRepository mappedIdRepository = createMappedIdRepository(true, configDirectory);
        MappedIdBasedImporter importer = new MappedIdBasedImporter(tfm2RecQuery, sdClient, observationClient, metricsClient, mappedIdRepository);
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



    private static MappedIdRepository createMappedIdRepository(boolean doImportData, String configDirectory) {
        MappedIdRepository mappedIdRepository = new MappedIdRepositoryImpl();
        if (doImportData) {
            if (!Paths.get(configDirectory).toFile().exists()) {
                throw new MetasysCloudConnectorException("Import of data from " + configDirectory + " failed. Directory does not exist.");
            }
            new MetasysConfigImporter().importMetasysConfig(configDirectory, mappedIdRepository);
        }
        return mappedIdRepository;
    }
    /*
   Primarily used for testing
    */
    protected void addImportableTrendId(MappedSensorId mappedSensorId) {
        importableTrendIds.add(mappedSensorId);
    }
}
