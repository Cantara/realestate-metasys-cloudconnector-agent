package no.cantara.realestate.metasys.cloudconnector;

import no.cantara.config.ApplicationProperties;
import no.cantara.realestate.automationserver.BasClient;
import no.cantara.realestate.cloudconnector.RealestateCloudconnectorApplication;
import no.cantara.realestate.cloudconnector.audit.AuditTrail;
import no.cantara.realestate.cloudconnector.notifications.NotificationService;
import no.cantara.realestate.cloudconnector.routing.ObservationsRepository;
import no.cantara.realestate.cloudconnector.sensorid.SensorIdRepository;
import no.cantara.realestate.distribution.ObservationDistributionClient;
import no.cantara.realestate.metasys.cloudconnector.automationserver.MetasysClient;
import no.cantara.realestate.metasys.cloudconnector.automationserver.SdClientSimulator;
import no.cantara.realestate.metasys.cloudconnector.automationserver.stream.MetasysStreamClient;
import no.cantara.realestate.metasys.cloudconnector.automationserver.stream.StreamListener;
import no.cantara.realestate.metasys.cloudconnector.automationserver.streampoc.ServerSentEvent;
import no.cantara.realestate.metasys.cloudconnector.ingestion.MetasysTrendsIngestionService;
import no.cantara.realestate.metasys.cloudconnector.ingestion.StreamPocClient;
import no.cantara.realestate.metasys.cloudconnector.metrics.MetasysMetricsDistributionClient;
import no.cantara.realestate.metasys.cloudconnector.metrics.MetricsDistributionServiceStub;
import no.cantara.realestate.metasys.cloudconnector.sensors.MetasysCsvSensorImporter;
import no.cantara.realestate.metasys.cloudconnector.sensors.SensorFileWatcher;
import no.cantara.realestate.metasys.cloudconnector.status.TemporaryHealthResource;
import no.cantara.realestate.metasys.cloudconnector.trends.CsvTrendsLastUpdatedService;
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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static no.cantara.realestate.metasys.cloudconnector.ingestion.StreamPocClient.shortenedAccessToken;
import static org.slf4j.LoggerFactory.getLogger;

public class MetasysCloudconnectorApplication extends RealestateCloudconnectorApplication {
    private static Logger log = getLogger(MetasysCloudconnectorApplication.class);
    private boolean enableStream;
    private boolean enableScheduledImport;
    private NotificationService notificationService;

    private SensorIdRepository sensorIdRepository;
    private RecRepository recRepository;
    private TrendsIngestionService trendsIngestionService;
    private MetasysMetricsDistributionClient metricsDistributionClient;

    public static final String INSTRUMENTATION_SCOPE_NAME_KEY = "opentelemetry.instrumentationScopeName";
    public static final String INSTRUMENTATION_SCOPE_NAME_VALUE = "no.cantara.realestate";
    private StreamPocClient streamPocClient;
    private SensorFileWatcher sensorFileWatcher;
    private final Object sensorSubscriptionLock = new Object();
    private String importDirectory;
    private String subscriptionId;



    public MetasysCloudconnectorApplication(ApplicationProperties config) {
        super(config, "no.cantara.realestate", "metasys-cloudconnector-agent");
    }


    /*
    Initialization of the application below.
     */


    @Override
    protected void doInit() {
        final ObservationDistributionClient finalObservationDistributionClient = null;
        String measurementsName = config.get("measurements.name", "metasys_cloudconnector_cantara");
        final MetasysMetricsDistributionClient metricsDistributionClient = new MetricsDistributionServiceStub(measurementsName);
        put(MetasysMetricsDistributionClient.class, metricsDistributionClient);

        enableStream = config.asBoolean("sd.stream.enabled");
        enableScheduledImport = config.asBoolean("sd.scheduledImport.enabled");

        super.doInit();

        ObservationListener observationListener = get(ObservationsRepository.class);
        NotificationListener notificationListener = get(NotificationListener.class);
        notificationService = get(no.cantara.realestate.cloudconnector.notifications.NotificationService.class);

        sensorIdRepository = get(SensorIdRepository.class);
        recRepository = get(RecRepository.class);

        //MetasysClient
        BasClient sdClient = createSdClient(config);
        if (sdClient instanceof MetasysClient) {
            MetasysClient metasysClient = (MetasysClient) sdClient;
            String sdClientName = sdClient.getName();
            get(StingrayHealthService.class).registerHealthProbe(sdClientName + "-whenLastObservationImported", metasysClient::getWhenLastTrendSampleReceived);
            get(StingrayHealthService.class).registerHealthProbe(sdClientName + "-isHealthy", sdClient::isHealthy);
            get(StingrayHealthService.class).registerHealthProbe(sdClientName + "-isLoggedIn", sdClient::isLoggedIn);
            get(StingrayHealthService.class).registerHealthProbe(sdClientName + "-apiAvailable", metasysClient::isApiAvailable);
            get(StingrayHealthService.class).registerHealthProbe(sdClientName + "-consecutiveFailures", metasysClient::getConsecutiveFailures);
            get(StingrayHealthService.class).registerHealthProbe(sdClientName + "-lastSuccessfulApiCall", metasysClient::getLastSuccessfulApiCall);
            get(StingrayHealthService.class).registerHealthProbe(sdClientName + "-lastFailedApiCall", metasysClient::getLastFailedApiCall);
        }

        MetasysStreamClient streamClient = null;
        //MetasysStreamClient
        if (enableStream) {
            streamClient = createStreamClient(config);
            get(StingrayHealthService.class).registerHealthProbe(streamClient.getName() + "-isHealthy", streamClient::isHealthy);
            get(StingrayHealthService.class).registerHealthProbe(streamClient.getName() + "-isLoggedIn", streamClient::isLoggedIn);
        }

        //SensorIdRepository
        boolean readLastUpdated = config.asBoolean("ingestion.trendsLastUpdated.enabled", false);
        TrendsLastUpdatedService trendsLastUpdatedService = null;
        if (readLastUpdated) {
            log.info("Reading last updated trends from CSV file");
            String lastUpdatedDirectory = config.get("ingestion.trendsLastUpdated.directory","status");
            String lastUpdatedFile = config.get("ingestion.trendsLastUpdated.csvFile", "trends_last_updated.csv");
            String lastFailedFile = config.get("ingestion.trendsLastFailed.csvFile", "trends_last_failed.csv");
            trendsLastUpdatedService = init(TrendsLastUpdatedService.class, () -> new CsvTrendsLastUpdatedService(lastUpdatedDirectory, lastUpdatedFile, lastFailedFile));
            trendsLastUpdatedService.readLastUpdated();
            log.info("Read last updated trends: {}", trendsLastUpdatedService.isHealthy());
        } else {
            log.info("Using in-memory TrendsLastUpdatedService");
            trendsLastUpdatedService = init(TrendsLastUpdatedService.class, () -> new InMemoryTrendsLastUpdatedService());
        }

        trendsIngestionService = new MetasysTrendsIngestionService(config, observationListener, notificationListener, sdClient, trendsLastUpdatedService, auditTrail, metricsDistributionClient);

        // Initial import of sensors and RecTags
        importDirectory = config.get("importdata.directory");
        performInitialSensorImport(trendsIngestionService, metricsDistributionClient);

        //Start ingestion and routing
        super.initIngestionService(trendsIngestionService);
        super.initRouter();

        //Open Stream, start subscribing to events
        if (enableStream && streamClient != null) {
            streamPocClient = new StreamPocClient(streamClient, get(SensorIdRepository.class), get(RecRepository.class), observationListener, metricsDistributionClient, auditTrail);

            //Verify that token refresh is working
            String accessToken = streamPocClient.getUserToken().getAccessToken();
            String shortAccessToken = shortenedAccessToken(accessToken);
            log.info("AccessToken: {}, expires at: {}", shortAccessToken, streamPocClient.getUserToken().getExpires());
            try {
                // Use the StreamListener based approach
                streamPocClient.createStream(streamPocClient);
                log.debug("Waiting for events... IsStreamOpen? {}", streamPocClient.isStreamOpen());

                // For backward compatibility demonstration, also check the queue
                log.info("Waiting for subscriptionId. This may take 10 seconds...");
                ServerSentEvent event = streamPocClient.eventQueue.poll(10, TimeUnit.SECONDS);
                if (event == null) {
                    throw new MetasysCloudConnectorException("StreamPocClient returned null events. Closing stream.");
                }
                subscriptionId = streamPocClient.getSubscriptionId();
                log.info("Stream created. SubscriptionId: {}", subscriptionId);
                if (subscriptionId == null && event.getEvent().equals("hello")) {
                    subscriptionId = event.getData();
                    log.info("Stream opened. Received subscriptionId: {}", subscriptionId);
                }
                if (subscriptionId != null) {
                    subscriptionId = subscriptionId.replace("\"", "");
                }

                List<MetasysSensorId> repositorySensorIds = get(SensorIdRepository.class).all().stream()
                        .filter(sensorId -> sensorId instanceof MetasysSensorId)
                        .map(sensorId -> (MetasysSensorId) sensorId)
                        .toList();

                streamPocClient.subscribeToStream(subscriptionId, repositorySensorIds);

            } catch (InterruptedException e) {
                log.error("Error in main thread", e);
            } catch (MetasysCloudConnectorException e) {
                log.error("Failed to open stream. Reason: {}", e.getMessage());
                TemporaryHealthResource.addRegisteredError("Failed to open stream. Reason: " + e.getMessage());
            } catch (LogonFailedException e) {
                log.error("Failed to logon Stream Client. Reason: {}", e.getMessage());
                TemporaryHealthResource.addRegisteredError("Failed to logon to stream. Reason: " + e.getMessage());
            }
        }

        // Start file watcher for dynamic updates
        startSensorFileWatcher(trendsIngestionService, metricsDistributionClient);
    }

    /**
     * Performs initial import of sensors and RecTags from CSV files
     */
    private void performInitialSensorImport(TrendsIngestionService trendsIngestionService,
                                            MetasysMetricsDistributionClient metricsDistributionClient) {
        log.info("Performing initial sensor import from directory: {}", importDirectory);

        SensorIdRepository sensorIdRepository = get(SensorIdRepository.class);
        RecRepository recRepository = get(RecRepository.class);

        // Import sensor IDs
        List<MetasysSensorId> metasysSensorIds = MetasysCsvSensorImporter.importSensorIdsFromDirectory(importDirectory, "Metasys");
        log.info("Imported {} sensor IDs from CSV files", metasysSensorIds.size());

        for (MetasysSensorId metasysSensorId : metasysSensorIds) {
            auditTrail.logCreated(metasysSensorId.getId(), "Added to SensorIdRepository");
            sensorIdRepository.add(metasysSensorId);
        }

        // Subscribe to trends
        List<SensorId> sensorIds = sensorIdRepository.all();
        for (SensorId sensorId : sensorIds) {
            auditTrail.logSubscribed(sensorId.getId(), "Subscribed to TrendsIngestionService");
            trendsIngestionService.addSubscription(sensorId);
        }
        log.info("Subscribed to trends for {} sensors", trendsIngestionService.getSubscriptionsCount());

        // Import RecTags
        List<RecTags> recTagsList = MetasysCsvSensorImporter.importRecTagsFromDirectory(importDirectory, "Metasys");
        log.info("Imported {} RecTags from CSV files", recTagsList.size());

        for (RecTags recTags : recTagsList) {
            String twinId = recTags.getTwinId();
            SensorId sensorId = sensorIds.stream()
                    .filter(sensorId1 -> sensorId1.getId().equals(twinId))
                    .findFirst()
                    .orElse(null);
            if (sensorId != null) {
                recRepository.addRecTags(sensorId, recTags);
                log.debug("Added RecTags for sensor: {}", twinId);
            }
        }
    }

    /**
     * Starts the file watcher to monitor changes in sensor CSV files
     */
    private void startSensorFileWatcher(TrendsIngestionService trendsIngestionService,
                                        MetasysMetricsDistributionClient metricsDistributionClient) {
        try {
            long debounceSeconds = config.asLong("sensorFileWatcher.debounceSeconds", 30L);

            sensorFileWatcher = new SensorFileWatcher(
                    importDirectory,
                    debounceSeconds,
                    () -> onSensorFilesChanged(trendsIngestionService, metricsDistributionClient),
                    notificationService
            );

            sensorFileWatcher.start();

            // Register health checks
            get(StingrayHealthService.class).registerHealthProbe("sensorFileWatcher-isHealthy", sensorFileWatcher::isHealthy);
            get(StingrayHealthService.class).registerHealthProbe("sensorFileWatcher-isRunning", sensorFileWatcher::isRunning);
            get(StingrayHealthService.class).registerHealthProbe("sensorFileWatcher-lastCheckTime", sensorFileWatcher::getLastCheckTime);
            get(StingrayHealthService.class).registerHealthProbe("sensorFileWatcher-lastUpdateTime", sensorFileWatcher::getLastUpdateTime);

            log.info("SensorFileWatcher started successfully");
        } catch (Exception e) {
            log.error("Failed to start SensorFileWatcher", e);
            notificationService.sendAlarm(null, "Failed to start SensorFileWatcher: " + e.getMessage());
        }
    }

    /**
     * Called when sensor files are changed. Re-imports and subscribes to new sensors.
     */
    private void onSensorFilesChanged(TrendsIngestionService trendsIngestionService,
                                      MetasysMetricsDistributionClient metricsDistributionClient) {
        log.info("Processing sensor file changes...");

        synchronized (sensorSubscriptionLock) {
            List<MetasysSensorId> newSensors = new ArrayList<>();
            // Get current sensor IDs for comparison
            List<String> existingSensorIds = new ArrayList<>();
            for (Object obj : sensorIdRepository.all()) {
                if (obj instanceof SensorId sensorId) {
                    existingSensorIds.add(sensorId.getId());
                }
            }
            log.debug("Current sensor count: {}", existingSensorIds.size());

            try {
                // Re-import sensor IDs from CSV files
                List<MetasysSensorId> importedSensorIds = MetasysCsvSensorImporter.importSensorIdsFromDirectory(importDirectory, "Metasys");
                log.info("Re-imported {} sensor IDs from CSV files", importedSensorIds.size());

                // Find new sensors
                newSensors = importedSensorIds.stream()
                        .filter(sensorId -> !existingSensorIds.contains(sensorId.getId()))
                        .toList();

                if (newSensors.isEmpty()) {
                    log.info("No new sensors found");
                } else {
                    log.info("Found {} new sensors", newSensors.size());


                    for (MetasysSensorId newSensor : newSensors) {
                        try {
                            auditTrail.logCreated(newSensor.getId(), "Added to SensorIdRepository (file watcher)");
                            // Add new sensors to SensorIdRepository
                            sensorIdRepository.add(newSensor);
                            log.info("Added new sensor: {}", newSensor.getId());
                            // Subscribe to trends for new sensor
                            trendsIngestionService.addSubscription(newSensor);
                            // Subscribe to stream for new sensors (if stream is enabled)
                            if (enableStream && streamPocClient != null && subscriptionId != null) {
                                subscribeToStream(newSensors);
                            }

                        } catch (Exception e) {
                            log.warn("Failed to add sensor to sensorIdRepository, or add trend or subscribe to stream: {}", newSensor.getId(), e);
                            notificationService.sendAlarm(null, "Failed to add subscriptions for new sensor " + newSensor.getId() + ": " + e.getMessage());
                        }
                    }

                    // Send metrics
                    metricsDistributionClient.sendValue("sensors.added", newSensors.size());
                }

            } catch (Exception e) {
                log.error("Error importing sensor IDs during file change processing", e);
                notificationService.sendAlarm(null, "Error importing sensor IDs: " + e.getMessage());
                // Don't throw - we want to continue with RecTags
            }

            try {
                // Update the RecTags for every new sensor
                List<RecTags> recTagsList = MetasysCsvSensorImporter.importRecTagsFromDirectory(importDirectory, "Metasys");
                log.info("Re-imported {} RecTags from CSV files", recTagsList.size());

                for (MetasysSensorId newSensor : newSensors) {
                    RecTags sensorRecTag = recTagsList.stream()
                            .filter(recTags -> recTags.getTwinId().equals(newSensor.getId()))
                            .findFirst()
                            .orElse(null);
                    if (sensorRecTag != null) {
                        recRepository.addRecTags(newSensor, sensorRecTag);
                        log.trace("Added new RecTags for sensor: {}", newSensor.getId());
                    }
                }

            } catch (Exception e) {
                log.error("Error importing RecTags during file change processing", e);
                notificationService.sendAlarm(null, "Error importing RecTags: " + e.getMessage());
            }
        }

        log.info("Sensor file change processing completed");
    }

//    /**
//     * Subscribes to trends for new sensors
//     */
//    private void subscribeToTrends(List<MetasysSensorId> newSensors, TrendsIngestionService trendsIngestionService) {
//        for (MetasysSensorId sensorId : newSensors) {
//            try {
//                auditTrail.logSubscribed(sensorId.getId(), "Subscribed to TrendsIngestionService (file watcher)");
//                trendsIngestionService.addSubscription(sensorId);
//                log.info("Subscribed to trends for sensor: {}", sensorId.getId());
//            } catch (Exception e) {
//                log.error("Failed to subscribe to trends for sensor: {}", sensorId.getId(), e);
//                notificationService.sendAlarm(null, "Failed to subscribe to trends for sensor " + sensorId.getId() + ": " + e.getMessage());
//                // Continue with next sensor
//            }
//        }
//    }

    /**
     * Subscribes to stream for new sensors
     */
    private void subscribeToStream(List<MetasysSensorId> newSensors) {
        for (MetasysSensorId sensorId : newSensors) {
            try {
                String metasysObjectId = sensorId.getMetasysObjectId();
                if (metasysObjectId != null && !metasysObjectId.isEmpty()) {
                    streamPocClient.subscribeToStreamForMetasysObjectId(subscriptionId, metasysObjectId);
                    auditTrail.logSubscribed(sensorId.getId(), "Subscribed to Stream (file watcher)");
                    log.info("Subscribed to stream for sensor: {} (objectId: {})", sensorId.getId(), metasysObjectId);
                } else {
                    log.warn("Sensor {} has no metasysObjectId, skipping stream subscription", sensorId.getId());
                }
            } catch (Exception e) {
                log.error("Failed to subscribe to stream for sensor: {}", sensorId.getId(), e);
                notificationService.sendAlarm(null, "Failed to subscribe to stream for sensor " + sensorId.getId() + ": " + e.getMessage());
                // Continue with next sensor
            }
        }
    }

    /**
     * Shuts down the application gracefully
     */
    public void shutdown() {
        log.info("Shutting down MetasysCloudconnectorApplication...");

        if (sensorFileWatcher != null) {
            sensorFileWatcher.stop();
        }

        if (streamPocClient != null) {
            streamPocClient.close();
        }

        // Call parent shutdown if it exists
        // super.shutdown();

        log.info("MetasysCloudconnectorApplication shutdown complete");
    }


    protected String getFaviconPath() {
        return "/static/metasys/favicon.ico";
    }

    protected StreamPocClient wireMetasysStreamImporter(MetasysStreamClient streamClient, SensorIdRepository sensorIdRepository, RecRepository recRepository, ObservationListener observationListener, MetasysMetricsDistributionClient metricsClient, AuditTrail auditTrail) {
        StreamPocClient streamPocClient = new StreamPocClient(streamClient, sensorIdRepository, recRepository, observationListener, metricsClient, auditTrail);
        try {
            StreamListener streamListener = streamPocClient;
            streamPocClient.createStream(streamListener);
            log.debug("Waiting for events... IsStreamOpen? {}", streamPocClient.isStreamOpen());

            // For backward compatibility demonstration, also check the queue
            log.info("Waiting for subscriptionId. This may take 10 seconds...");
            ServerSentEvent event = streamPocClient.eventQueue.poll(10, TimeUnit.SECONDS);
            if (event == null) {
                throw new MetasysCloudConnectorException("StreamPocClient returned null events. Closing stream.");
            }
            log.info("First event from queue: {}", event);
            String subscriptionId = streamPocClient.getSubscriptionId();
            if (subscriptionId == null || subscriptionId.isEmpty()) {
                throw new MetasysCloudConnectorException("StreamPocClient did not receive a valid subscriptionId. Closing stream.");
            }

        } catch (InterruptedException e) {
            log.error("Error in main thread", e);
        }
//        finally {
//            log.info("Closing StreamPocClient");
//            streamPocClient.close();
//        }
        return streamPocClient;
    }

    /*
    protected MetasysStreamImporter wireMetasysStreamImporter(MetasysStreamClient streamClient, SensorIdRepository sensorIdRepository, ObservationDistributionClient distributionClient, MetasysMetricsDistributionClient metricsClient, AuditTrail auditTrail) {
        MetasysStreamImporter streamImporter = new MetasysStreamImporter(streamClient, sensorIdRepository, distributionClient, metricsClient, auditTrail);

        return streamImporter;
    }

     */

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
                sdClient = MetasysClient.getInstance(username, password, apiUri, notificationService);
                log.info("Running with a live REST SD.");
            } catch (URISyntaxException e) {
                throw new MetasysCloudConnectorException("Failed to connect SD Client to URL" + apiUrl, e);
            } catch (LogonFailedException e) {
                throw new MetasysCloudConnectorException("Failed to logon SD Client. URL used" + apiUrl, e);
            }
        } else {
            sdClient = new SdClientSimulator();
            log.info("Running with a simulator of SD.");
        }
        return sdClient;
    }

    private MetasysStreamClient createStreamClient(ApplicationProperties config) {
        MetasysStreamClient streamClient;
        String useSDProdValue = config.get("sd.api.prod");

        if (Boolean.valueOf(useSDProdValue)) {
            String apiUrl = config.get("sd.api.url");
            String username = config.get("sd.stream.username");
            String password = config.get("sd.stream.password");
            try {
                URI apiUri = new URI(apiUrl);
                log.info("Connect to Metasys Stream: {} with username: {}", apiUri, username);
                streamClient = MetasysStreamClient.getInstance(username, password, apiUri, notificationService);
                log.info("Running with a live Stream.");
            } catch (URISyntaxException e) {
                throw new MetasysCloudConnectorException("Failed to connect Stream Client to URL" + apiUrl, e);
            } catch (LogonFailedException e) {
                throw new MetasysCloudConnectorException("Failed to logon Stream Client. URL used" + apiUrl, e);
            }
        } else {
            streamClient = null;
            log.warn("Stream is not enabled. The MetasysStreamClient simulator is not developed yet.");
        }
        return streamClient;
    }

    protected void checkIfStreamIsAlive() {
        try {
            String accessToken = streamPocClient.getUserToken().getAccessToken();
            String shortAccessToken = shortenedAccessToken(accessToken);
            do {
                //Check if the stream is still alive
                boolean isAlive = streamPocClient.streamListenerThread.isAlive();
                if (!isAlive) {
                    log.info("Stream is not alive. Closing Stream Reason: " + streamPocClient.closingStreamReason.get());
                    break;
                }
                //Check if access token is still valid
                String newAccessToken = streamPocClient.getUserToken().getAccessToken();
                String newShortAccessToken = shortenedAccessToken(newAccessToken);
                if (!newShortAccessToken.equals(shortAccessToken)) {
                    log.info("AT: {} -> {}, expires: {}", shortAccessToken, newShortAccessToken, streamPocClient.getUserToken().getExpires());
                    accessToken = newShortAccessToken;
                    shortAccessToken = newShortAccessToken;
                } else {
                    log.trace("Access token not changed. Expires: {}", streamPocClient.getUserToken().getExpires());
                }

                // For backward compatibility, also check the queue
                while (!streamPocClient.eventQueue.isEmpty()) {
                    log.trace("Event from queue: {}", streamPocClient.eventQueue.poll());
                }
                Thread.sleep(10000);

            } while (true);
            log.info("Stream closed. StreamPocClient will be closed.");
        } catch (InterruptedException e) {
            log.error("Error in main thread", e);
        } finally {
            log.info("Closing StreamPocClient");
            streamPocClient.close();
        }

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
            log.info("   SensorFileWatcher: Active with {} seconds debounce",
                    config.asLong("sensorFileWatcher.debounceSeconds", 30L));

            // Add shutdown hook for graceful shutdown
            final MetasysCloudconnectorApplication finalApplication = application;
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                log.info("Shutdown hook triggered");
                if (finalApplication != null) {
                    finalApplication.shutdown();
                }
            }, "Shutdown-Hook"));

            application.checkIfStreamIsAlive();
        } catch (Exception e) {
            log.error("Failed to start MetasysCloudconnectorApplication", e);
            if (application != null) {
                application.shutdown();
            }
            System.exit(1);
        }
    }

}
