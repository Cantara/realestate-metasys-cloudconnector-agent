package no.cantara.realestate.metasys.cloudconnector;

import com.codahale.metrics.health.HealthCheck;
import no.cantara.config.ApplicationProperties;
import no.cantara.realestate.automationserver.BasClient;
import no.cantara.realestate.azure.AzureObservationDistributionClient;
import no.cantara.realestate.distribution.ObservationDistributionClient;
import no.cantara.realestate.mappingtable.repository.MappedIdQuery;
import no.cantara.realestate.mappingtable.repository.MappedIdQueryBuilder;
import no.cantara.realestate.mappingtable.repository.MappedIdRepository;
import no.cantara.realestate.mappingtable.repository.MappedIdRepositoryImpl;
import no.cantara.realestate.metasys.cloudconnector.automationserver.MetasysClient;
import no.cantara.realestate.metasys.cloudconnector.automationserver.SdClientSimulator;
import no.cantara.realestate.metasys.cloudconnector.automationserver.SdLogonFailedException;
import no.cantara.realestate.metasys.cloudconnector.automationserver.stream.MetasysStreamClient;
import no.cantara.realestate.metasys.cloudconnector.distribution.ObservationDistributionResource;
import no.cantara.realestate.metasys.cloudconnector.distribution.ObservationDistributionServiceStub;
import no.cantara.realestate.metasys.cloudconnector.metrics.MetasysMetricsDistributionClient;
import no.cantara.realestate.metasys.cloudconnector.metrics.MetricsDistributionServiceStub;
import no.cantara.realestate.metasys.cloudconnector.notifications.NotificationService;
import no.cantara.realestate.metasys.cloudconnector.notifications.SlackNotificationService;
import no.cantara.realestate.metasys.cloudconnector.observations.*;
import no.cantara.realestate.metasys.cloudconnector.sensors.MetasysConfigImporter;
import no.cantara.realestate.metasys.cloudconnector.sensors.SensorType;
import no.cantara.realestate.metasys.cloudconnector.utils.LogbackConfigLoader;
import no.cantara.realestate.observations.ObservationMessage;
import no.cantara.realestate.security.LogonFailedException;
import no.cantara.stingray.application.AbstractStingrayApplication;
import no.cantara.stingray.application.health.StingrayHealthService;
import no.cantara.stingray.security.StingraySecurity;
import org.slf4j.Logger;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Paths;
import java.util.*;

import static no.cantara.realestate.metasys.cloudconnector.ObservationMesstageStubs.buildStubObservation;
import static no.cantara.realestate.metasys.cloudconnector.status.TemporaryHealthResource.setUnhealthy;
import static org.slf4j.LoggerFactory.getLogger;

public class MetasysCloudconnectorApplication extends AbstractStingrayApplication<MetasysCloudconnectorApplication> {
    private static Logger log = getLogger(MetasysCloudconnectorApplication.class);
    private boolean enableStream;
    private boolean enableScheduledImport;
    private NotificationService notificationService;

    public static final String INSTRUMENTATION_SCOPE_NAME_KEY = "opentelemetry.instrumentationScopeName";
    public static final String INSTRUMENTATION_SCOPE_NAME_VALUE = "no.cantara.realestate";


    public MetasysCloudconnectorApplication(ApplicationProperties config) {
        super("MetasysCloudconnector",
                readMetaInfMavenPomVersion("no.cantara.realestate", "metasys-cloudconnector-app"),
                config
        );
    }


    public static void main(String[] args) {
        String externalConfigPath = "./logback_override.xml";
        LogbackConfigLoader.loadExternalConfig(externalConfigPath);
        log = getLogger(MetasysCloudconnectorApplication.class);

        ApplicationProperties config = new MetasysCloudconnectorApplicationFactory()
                .conventions(ApplicationProperties.builder())
                .buildAndSetStaticSingleton();

        try {
            MetasysCloudconnectorApplication application = new MetasysCloudconnectorApplication(config).init().start();
            log.info("Server started. See status on {}:{}{}/health", "http://localhost", config.get("server.port"), config.get("server.context-path"));
            application.startImportingObservations();
        } catch (Exception e) {
            log.error("Failed to start MetasysCloudconnectorApplication", e);
        }

    }

    protected void startImportingObservations() {
        // Start import scheduler and stream
        if (enableStream) {
            log.info("Stream import is enabled.");
            List<String> importAllFromRealestates = findListOfRealestatesToImportFrom();
            log.info("Stream import all from these RealEstates: {}", importAllFromRealestates);
            List<MappedIdQuery> idQueries = new ArrayList<>();
            if (importAllFromRealestates != null && importAllFromRealestates.size() > 0) {
                for (String realestate : importAllFromRealestates) {
                    MappedIdQuery mappedIdQuery = new MetasysMappedIdQueryBuilder().realEstate(realestate).build();
                    idQueries.add(mappedIdQuery);
                }
            }

            try {
                log.info("Stream import with these queries: {}", idQueries);
                get(MetasysStreamImporter.class).startSubscribing(idQueries);
            } catch (SdLogonFailedException e) {
                setUnhealthy();
                log.warn("Failed to start subscribing to stream. Reason: {}", e.getMessage());
            }
        } else {
            log.info("Stream import is disabled.");
        }
        if (enableScheduledImport) {
            get(ScheduledImportManager.class).startScheduledImportOfTrendIds();
        } else {
            log.info("Scheduled import is disabled.");
        }
    }



    @Override
    protected void doInit() {
        initBuiltinDefaults();
        StingraySecurity.initSecurity(this);

        initNotificationServices();
        boolean doImportData = config.asBoolean("import.data");
        enableStream = config.asBoolean("sd.stream.enabled");
        enableScheduledImport = config.asBoolean("sd.scheduledImport.enabled");
        BasClient sdClient = createSdClient(config);

        ServiceLoader<ObservationDistributionClient> observationDistributionClients = ServiceLoader.load(ObservationDistributionClient.class);
        ObservationDistributionClient observationDistributionClient = null;
        for (ObservationDistributionClient distributionClient : observationDistributionClients) {
            if (distributionClient != null && distributionClient instanceof AzureObservationDistributionClient) {
                log.info("Found implementation of ObservationDistributionClient on classpath: {}", distributionClient.toString());
                observationDistributionClient = distributionClient;
            }
            get(StingrayHealthService.class).registerHealthProbe(observationDistributionClient.getName() +"-isConnected", observationDistributionClient::isConnectionEstablished);
            get(StingrayHealthService.class).registerHealthProbe(observationDistributionClient.getName() +"-numberofMessagesObserved", observationDistributionClient::getNumberOfMessagesObserved);
        }
        if (observationDistributionClient == null) {
            log.warn("No implementation of ObservationDistributionClient was found on classpath. Creating a ObservationDistributionServiceStub explicitly.");
            observationDistributionClient = new ObservationDistributionServiceStub();
            get(StingrayHealthService.class).registerHealthProbe(observationDistributionClient.getName() +"-isConnected", observationDistributionClient::isConnectionEstablished);
            get(StingrayHealthService.class).registerHealthProbe(observationDistributionClient.getName() +"-numberofMessagesDistributed", observationDistributionClient::getNumberOfMessagesObserved);
        }
        observationDistributionClient.openConnection();
        log.info("Establishing and verifying connection to Azure.");
        if (observationDistributionClient.isConnectionEstablished()) {
            ObservationMessage stubMessage = buildStubObservation();
            observationDistributionClient.publish(stubMessage);
        }
        String measurementsName = config.get("measurements.name");
        MetasysMetricsDistributionClient metricsDistributionClient = new MetricsDistributionServiceStub(measurementsName);
        MappedIdRepository mappedIdRepository = init(MappedIdRepository.class, () -> createMappedIdRepository(doImportData));
        ObservationDistributionClient finalObservationDistributionClient = observationDistributionClient;
        ScheduledImportManager scheduledImportManager = init(ScheduledImportManager.class, () -> wireScheduledImportManager(sdClient, finalObservationDistributionClient, metricsDistributionClient, mappedIdRepository));
        ObservationDistributionResource observationDistributionResource = initAndRegisterJaxRsWsComponent(ObservationDistributionResource.class, () -> createObservationDistributionResource(finalObservationDistributionClient));

        get(StingrayHealthService.class).registerHealthProbe("mappedIdRepository.size", mappedIdRepository::size);
        get(StingrayHealthService.class).registerHealthProbe(sdClient.getName() + "-isHealthy", sdClient::isHealthy);
        get(StingrayHealthService.class).registerHealthProbe(sdClient.getName() + "-isLogedIn", sdClient::isLoggedIn);
        get(StingrayHealthService.class).registerHealthProbe(sdClient.getName() + "-numberofTrendsSamples", sdClient::getNumberOfTrendSamplesReceived);
        get(StingrayHealthService.class).registerHealthProbe("observationDistribution.message.count", observationDistributionResource::getDistributedCount);
        //Random Example
        init(Random.class, this::createRandom);
        RandomizerResource randomizerResource = initAndRegisterJaxRsWsComponent(RandomizerResource.class, this::createRandomizerResource);

        //Wire up the stream importer
        if (enableStream) {
            MetasysStreamClient streamClient =  new MetasysStreamClient();
            MetasysStreamImporter streamImporter = init(MetasysStreamImporter.class, () -> wireMetasysStreamImporter(streamClient, sdClient, mappedIdRepository, finalObservationDistributionClient, metricsDistributionClient));
            get(StingrayHealthService.class).registerHealthProbe(streamClient.getName() + "-isHealthy", streamClient::isHealthy);
            get(StingrayHealthService.class).registerHealthProbe(streamClient.getName() + "-isLoggedIn", streamClient::isLoggedIn);
            get(StingrayHealthService.class).registerHealthProbe(streamClient.getName() + "-isStreamOpen", streamClient::isStreamOpen);
            get(StingrayHealthService.class).registerHealthProbe(sdClient.getName() + "-whenLastObservationImported", streamClient::getWhenLastMessageImported);
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
        }
    }

    private void initNotificationServices() {
        ServiceLoader<NotificationService> notificationServices = ServiceLoader.load(NotificationService.class);
        if (notificationServices != null && notificationServices.iterator().hasNext()) {
            notificationService = notificationServices.findFirst().orElse(null);
            log.trace("Alerts and Warnings will be sent with NotificationService: {}", notificationService);
        } else {
            log.warn("ServiceLoader could not find any implementation of NotificationService. Using SlackNotificationService.");
            notificationService = new SlackNotificationService();
        }
    }


    protected MetasysStreamImporter wireMetasysStreamImporter(MetasysStreamClient streamClient, BasClient sdClient, MappedIdRepository mappedIdRepository, ObservationDistributionClient distributionClient, MetasysMetricsDistributionClient metricsClient) {
        MetasysStreamImporter streamImporter = new MetasysStreamImporter(streamClient, sdClient, mappedIdRepository, distributionClient, metricsClient);

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

    protected MappedIdRepository createMappedIdRepository(boolean doImportData) {
        MappedIdRepository mappedIdRepository = new MappedIdRepositoryImpl();
        if (doImportData) {
            String configDirectory = config.get("importdata.directory");
            if (!Paths.get(configDirectory).toFile().exists()) {
                throw new MetasysCloudConnectorException("Import of data from " + configDirectory + " failed. Directory does not exist.");
            }
            new MetasysConfigImporter().importMetasysConfig(configDirectory, mappedIdRepository);
        }
        return mappedIdRepository;
    }

    private ScheduledImportManager wireScheduledImportManager(BasClient sdClient, ObservationDistributionClient distributionClient, MetasysMetricsDistributionClient metricsClient, MappedIdRepository mappedIdRepository) {

        ScheduledImportManager scheduledImportManager = null;

        List<String> importAllFromRealestates = findListOfRealestatesToImportFrom();
        log.info("Importallres: {}", importAllFromRealestates);
        if (importAllFromRealestates != null && importAllFromRealestates.size() > 0) {
            for (String realestate : importAllFromRealestates) {
                MappedIdQuery mappedIdQuery = new MetasysMappedIdQueryBuilder().realEstate(realestate).build();
                TrendLogsImporter trendLogsImporter = new MappedIdBasedImporter(mappedIdQuery, sdClient, distributionClient, metricsClient, mappedIdRepository);
                if (scheduledImportManager == null) {
                    scheduledImportManager = new ScheduledImportManager(trendLogsImporter, config);
                } else {
                    scheduledImportManager.addTrendLogsImporter(trendLogsImporter);
                }
            }
        } else {
            log.warn("Using Template import config for RealEstates: REstate1 and RealEst2");
            MappedIdQuery mappedIdQuery = new MetasysMappedIdQueryBuilder().realEstate("REstate1").build();
            TrendLogsImporter trendLogsImporter = new MappedIdBasedImporter(mappedIdQuery, sdClient, distributionClient, metricsClient, mappedIdRepository);
            scheduledImportManager = new ScheduledImportManager(trendLogsImporter, config);

            MappedIdQuery energyOnlyQuery = new MappedIdQueryBuilder().realEstate("RealEst2")
                    .sensorType(SensorType.energy.name())
                    .build();

            TrendLogsImporter mappedIdBasedImporter = new MappedIdBasedImporter(energyOnlyQuery, sdClient, distributionClient, metricsClient, mappedIdRepository);
            scheduledImportManager.addTrendLogsImporter(mappedIdBasedImporter);

            MappedIdQuery mysteryHouseQuery = new MappedIdQueryBuilder().realEstate("511")
                    .build();
            TrendLogsImporter mysteryImporter = new MappedIdBasedImporter(mysteryHouseQuery, sdClient, distributionClient, metricsClient, mappedIdRepository);
            scheduledImportManager.addTrendLogsImporter(mysteryImporter);
        }

        return scheduledImportManager;
    }

    private List<String> findListOfRealestatesToImportFrom() {
        List<String> realEstates = null;
        try {
            String reCsvSplitted = config.get("importsensorsQuery.realestates");
            if (reCsvSplitted != null) {
                realEstates = Arrays.asList(reCsvSplitted.split(","));
            }
        } catch (Exception e) {
            log.warn("Failed to read list of RealEstates used for import.", e);
        }
        return realEstates;
    }

    private ObservationDistributionResource createObservationDistributionResource(ObservationDistributionClient observationDistributionClient) {
        return new ObservationDistributionResource(observationDistributionClient);
    }

    private Random createRandom() {
        return new Random(System.currentTimeMillis());
    }

    private RandomizerResource createRandomizerResource() {
        Random random = get(Random.class);
        return new RandomizerResource(random);
    }

}
