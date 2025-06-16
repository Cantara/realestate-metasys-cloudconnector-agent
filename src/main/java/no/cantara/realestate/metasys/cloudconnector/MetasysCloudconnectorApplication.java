package no.cantara.realestate.metasys.cloudconnector;

import no.cantara.config.ApplicationProperties;
import no.cantara.realestate.automationserver.BasClient;
import no.cantara.realestate.cloudconnector.RealestateCloudconnectorApplication;
import no.cantara.realestate.cloudconnector.audit.AuditTrail;
import no.cantara.realestate.cloudconnector.notifications.NotificationService;
import no.cantara.realestate.cloudconnector.routing.ObservationsRepository;
import no.cantara.realestate.cloudconnector.sensorid.SensorIdRepository;
import no.cantara.realestate.distribution.ObservationDistributionClient;
import no.cantara.realestate.mappingtable.repository.MappedIdRepository;
import no.cantara.realestate.metasys.cloudconnector.automationserver.MetasysClient;
import no.cantara.realestate.metasys.cloudconnector.automationserver.SdClientSimulator;
import no.cantara.realestate.metasys.cloudconnector.automationserver.stream.MetasysStreamClient;
import no.cantara.realestate.metasys.cloudconnector.ingestion.MetasysTrendsIngestionService;
import no.cantara.realestate.metasys.cloudconnector.metrics.MetasysMetricsDistributionClient;
import no.cantara.realestate.metasys.cloudconnector.observations.MetasysStreamImporter;
import no.cantara.realestate.metasys.cloudconnector.sensors.MetasysCsvSensorImporter;
import no.cantara.realestate.metasys.cloudconnector.trends.InMemoryTrendsLastUpdatedService;
import no.cantara.realestate.metasys.cloudconnector.trends.TrendsLastUpdatedService;
import no.cantara.realestate.metasys.cloudconnector.utils.LogbackConfigLoader;
import no.cantara.realestate.observations.ObservationListener;
import no.cantara.realestate.plugins.ingestion.TrendsIngestionService;
import no.cantara.realestate.plugins.notifications.NotificationListener;
import no.cantara.realestate.rec.RecRepository;
import no.cantara.realestate.rec.RecTags;
import no.cantara.realestate.security.LogonFailedException;
import no.cantara.realestate.sensors.SensorId;
import no.cantara.realestate.sensors.metasys.MetasysSensorId;
import no.cantara.stingray.application.health.StingrayHealthService;
import org.slf4j.Logger;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;

import static org.slf4j.LoggerFactory.getLogger;

public class MetasysCloudconnectorApplication extends RealestateCloudconnectorApplication {
    private static Logger log = getLogger(MetasysCloudconnectorApplication.class);
    private boolean enableStream;
    private boolean enableScheduledImport;
    private NotificationService notificationService;

    public static final String INSTRUMENTATION_SCOPE_NAME_KEY = "opentelemetry.instrumentationScopeName";
    public static final String INSTRUMENTATION_SCOPE_NAME_VALUE = "no.cantara.realestate";


    public MetasysCloudconnectorApplication(ApplicationProperties config) {
        super(config, "no.cantara.realestate", "metasys-cloudconnector-agent");
    }


    /*
    Initialization of the application below.
     */


    @Override
    protected void doInit() {
        final MappedIdRepository mappedIdRepository = null;
        final ObservationDistributionClient finalObservationDistributionClient = null;
        final MetasysMetricsDistributionClient metricsDistributionClient = null;

        enableStream = config.asBoolean("sd.stream.enabled");
        enableScheduledImport = config.asBoolean("sd.scheduledImport.enabled");

        super.doInit();

        ObservationListener observationListener = get(ObservationsRepository.class);
        NotificationListener notificationListener = get(NotificationListener.class);
        notificationService = get(no.cantara.realestate.cloudconnector.notifications.NotificationService.class);
        //MetasysClient
        BasClient sdClient = createSdClient(config);
        if (sdClient instanceof MetasysClient) {
            get(StingrayHealthService.class).registerHealthProbe(sdClient.getName() + "-whenLastObservationImported", ((MetasysClient) sdClient)::getWhenLastTrendSampleReceived);
        }

        //SensorIdRepository
        TrendsLastUpdatedService trendsLastUpdatedService = init(TrendsLastUpdatedService.class, () -> new InMemoryTrendsLastUpdatedService());
        TrendsIngestionService trendsIngestionService = new MetasysTrendsIngestionService(config, observationListener, notificationListener, sdClient, trendsLastUpdatedService, auditTrail);
        SensorIdRepository sensorIdRepository = get(SensorIdRepository.class);
        String importDirectory = config.get("importdata.directory");
        List<MetasysSensorId> metasysSensorIds = MetasysCsvSensorImporter.importSensorIdsFromDirectory(importDirectory, "Metasys");
        for (MetasysSensorId metasysSensorId : metasysSensorIds) {
            auditTrail.logCreated(metasysSensorId.getId(), "Added to SensorIdRepository");
            sensorIdRepository.add(metasysSensorId);
        }
//        sensorIdRepository.addAll(metasysSensorIds);
        List<SensorId> sensorIds = sensorIdRepository.all();
        for (SensorId sensorId : sensorIds) {
            auditTrail.logSubscribed(sensorId.getId(), "Subscribed to TrendsIngestionService");
            trendsIngestionService.addSubscription(sensorId);
        }
//        trendsIngestionService.addSubscriptions(sensorIds);
        log.info("Starting MetasysTrendsIngestionService with {} sensorIds", trendsIngestionService.getSubscriptionsCount());

        //RecRepository
        RecRepository recRepository = get(RecRepository.class);
        List<RecTags> recTagsList = MetasysCsvSensorImporter.importRecTagsFromDirectory(importDirectory, "Metasys");
        for (RecTags recTags : recTagsList) {
            String twinId = recTags.getTwinId();
            SensorId sensorId = sensorIds.stream().filter(sensorId1 -> sensorId1.getId().equals(twinId)).findFirst().orElse(null);
            if (sensorId != null) {
                recRepository.addRecTags(sensorId, recTags);
                log.info("Added RecTags: {}", recTags);
            }
        }

        //Start ingestion and routing
        super.initIngestionService(trendsIngestionService);
        super.initRouter();


        //Wire up the stream importer
        if (enableStream) {
            MetasysStreamClient streamClient = new MetasysStreamClient();
            MetasysStreamImporter streamImporter = init(MetasysStreamImporter.class, () -> wireMetasysStreamImporter(streamClient, sdClient, sensorIdRepository, observationListener, metricsDistributionClient));
            get(StingrayHealthService.class).registerHealthProbe(streamClient.getName() + "-isHealthy", streamClient::isHealthy);
            get(StingrayHealthService.class).registerHealthProbe(streamClient.getName() + "-isLoggedIn", streamClient::isLoggedIn);
            get(StingrayHealthService.class).registerHealthProbe(streamClient.getName() + "-isStreamOpen", streamClient::isStreamOpen);
            get(StingrayHealthService.class).registerHealthProbe(streamClient.getName() + "-whenLastObservationReceived", streamClient::getWhenLastMessageImported);
            get(StingrayHealthService.class).registerHealthProbe(streamImporter.getName() + "-isHealthy", streamImporter::isHealthy);
            get(StingrayHealthService.class).registerHealthProbe(streamImporter.getName() + "-subscriptionId", streamImporter::getSubscriptionId);

            try {
                streamImporter.openStream();
            } catch (Exception e) {
                String cause = e.getMessage();
                streamImporter.setUnhealthy(cause);
                log.warn("Failed to open stream. Reason: {}", e.getMessage());
            }
            //Register health checks
            /*
            #363 FIXME disable health checks for Stream until stable testing is in place.
            get(StingrayHealthService.class).registerHealthCheck(streamClient.getName() + ".isLoggedIn", new HealthCheck() {
                @Override
                protected Result check() throws Exception {
                    if (streamClient.isHealthy() && streamClient.isLoggedIn()) {
                        return Result.healthy();
                    } else {
                        return Result.unhealthy(streamClient.getName() + " is not logged in. ");
                    }
                }
            });
            get(StingrayHealthService.class).registerHealthCheck(streamImporter.getName() + ".isHealthy", new HealthCheck() {
                @Override
                protected Result check() throws Exception {
                    if (streamImporter.isHealthy()) {
                        return Result.healthy();
                    } else {
                        return Result.unhealthy(streamImporter.getName() +" is unhealthy. ");
                    }
                }
            });
            */

        }
    }

    protected MetasysStreamImporter wireMetasysStreamImporter(MetasysStreamClient streamClient, BasClient sdClient,
                                                              SensorId sensorIdRepository,
                                                              ObservationListener observationListener,
                                                              MetasysMetricsDistributionClient metricsClient,
                                                              AuditTrail auditTrail) {
        MetasysStreamImporter streamImporter = new MetasysStreamImporter(streamClient, sdClient, sensorIdRepository,
                observationListener, metricsClient, auditTrail);

        return streamImporter;
    }

    private BasClient createSdClient(ApplicationProperties config) {
        BasClient sdClient;
        String useSDProdValue = config.get("sd.api.prod");

        if (Boolean.valueOf(useSDProdValue)) {
            String apiUrl = config.get("sd.api.url");
            String username = config.get("sd.api.username");
            String password = config.get("sd.api.password");
            try {
                URI apiUri = new URI(apiUrl);
                log.info("Connect to Metasys API: {} with username: {}", apiUri, username);
                sdClient = MetasysClient.getInstance(username, password, apiUri, notificationService); //new MetasysApiClientRest(apiUri, notificationService);
                log.info("Running with a live REST SD.");
            } catch (URISyntaxException e) {
                throw new MetasysCloudConnectorException("Failed to connect SD Client to URL" + apiUrl, e);
            } catch (LogonFailedException e) {
                throw new MetasysCloudConnectorException("Failed to logon SD Client. URL used" + apiUrl, e);
            }
        } else {
            URI simulatorUri = URI.create("https://simulator.totto.org:8080/SD");
            sdClient = new SdClientSimulator();
            log.info("Running with a simulator of SD.");
        }
        return sdClient;
    }


    public static void main(String[] args) {
        String externalConfigPath = "./logback_override.xml";
        LogbackConfigLoader.loadExternalConfig(externalConfigPath);
        log = getLogger(MetasysCloudconnectorApplication.class);

        ApplicationProperties config = new MetasysCloudconnectorApplicationFactory()
                .conventions(ApplicationProperties.builder())
                .buildAndSetStaticSingleton();

        MetasysCloudconnectorApplication application = null;
        try {
            application = new MetasysCloudconnectorApplication(config);
            application.init().start();
            String baseUrl = "http://localhost:" + config.get("server.port") + config.get("server.context-path");
            log.info("Server started. See status on {}/health", baseUrl);
            log.info("   SensorIds: {}/sensorids/status", baseUrl);
            log.info("   Recs: {}/rec/status", baseUrl);
            log.info("   Audit: {}/audit", baseUrl);
            log.info("   Distribution: {}/distribution", baseUrl);
        } catch (Exception e) {
            log.error("Failed to start MetasysCloudconnectorApplication", e);
        }

    }

}
