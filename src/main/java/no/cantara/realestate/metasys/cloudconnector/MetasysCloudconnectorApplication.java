package no.cantara.realestate.metasys.cloudconnector;

import no.cantara.config.ApplicationProperties;
import no.cantara.realestate.azure.AzureObservationDistributionClient;
import no.cantara.realestate.distribution.ObservationDistributionClient;
import no.cantara.realestate.mappingtable.repository.MappedIdQuery;
import no.cantara.realestate.mappingtable.repository.MappedIdQueryBuilder;
import no.cantara.realestate.mappingtable.repository.MappedIdRepository;
import no.cantara.realestate.mappingtable.repository.MappedIdRepositoryImpl;
import no.cantara.realestate.metasys.cloudconnector.automationserver.MetasysApiClientRest;
import no.cantara.realestate.metasys.cloudconnector.automationserver.SdClient;
import no.cantara.realestate.metasys.cloudconnector.automationserver.SdClientSimulator;
import no.cantara.realestate.metasys.cloudconnector.distribution.MetricsDistributionClient;
import no.cantara.realestate.metasys.cloudconnector.distribution.MetricsDistributionServiceStub;
import no.cantara.realestate.metasys.cloudconnector.distribution.ObservationDistributionResource;
import no.cantara.realestate.metasys.cloudconnector.distribution.ObservationDistributionServiceStub;
import no.cantara.realestate.metasys.cloudconnector.observations.MappedIdBasedImporter;
import no.cantara.realestate.metasys.cloudconnector.observations.MetasysMappedIdQueryBuilder;
import no.cantara.realestate.metasys.cloudconnector.observations.ScheduledImportManager;
import no.cantara.realestate.metasys.cloudconnector.observations.TrendLogsImporter;
import no.cantara.realestate.metasys.cloudconnector.sensors.MetasysConfigImporter;
import no.cantara.realestate.metasys.cloudconnector.sensors.SensorType;
import no.cantara.realestate.observations.ObservationMessage;
import no.cantara.stingray.application.AbstractStingrayApplication;
import no.cantara.stingray.application.health.StingrayHealthService;
import no.cantara.stingray.security.StingraySecurity;
import org.slf4j.Logger;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.ServiceLoader;

import static no.cantara.realestate.metasys.cloudconnector.ObservationMesstageStubs.buildStubObservation;
import static org.slf4j.LoggerFactory.getLogger;

public class MetasysCloudconnectorApplication extends AbstractStingrayApplication<MetasysCloudconnectorApplication> {
    private static final Logger log = getLogger(MetasysCloudconnectorApplication.class);



    public static void main(String[] args) {
        ApplicationProperties config = new MetasysCloudconnectorApplicationFactory()
                .conventions(ApplicationProperties.builder())
                .buildAndSetStaticSingleton();

        new MetasysCloudconnectorApplication(config).init().start();
        log.info("Server started. See status on {}:{}{}/health", "http://localhost", config.get("server.port"), config.get("server.context-path"));
    }

    public MetasysCloudconnectorApplication(ApplicationProperties config) {
        super("MetasysCloudconnector",
                readMetaInfMavenPomVersion("no.cantara.realestate", "metasys-cloudconnector-app"),
                config
        );
    }

    @Override
    protected void doInit() {
        initBuiltinDefaults();
        StingraySecurity.initSecurity(this);
        boolean doImportData = config.asBoolean("import.data");
        SdClient sdClient = createSdClient(config);

        ServiceLoader<ObservationDistributionClient> observationDistributionClients = ServiceLoader.load(ObservationDistributionClient.class);
        ObservationDistributionClient observationDistributionClient = null;
        for (ObservationDistributionClient distributionClient : observationDistributionClients) {
            if (distributionClient != null && distributionClient instanceof AzureObservationDistributionClient) {
                log.info("Found implementation of ObservationDistributionClient on classpath: {}", distributionClient.toString());
                observationDistributionClient = distributionClient;
            }
            get(StingrayHealthService.class).registerHealthProbe(observationDistributionClient.getName() +"-isConnected: ", observationDistributionClient::isConnectionEstablished);
            get(StingrayHealthService.class).registerHealthProbe(observationDistributionClient.getName() +"-numberofmessagesobserved: ", observationDistributionClient::getNumberOfMessagesObserved);
        }
        if (observationDistributionClient == null) {
            log.warn("No implemmentation of ObservationDistributionClient was found on classpath. Creating a ObservationDistributionServiceStub explicitly.");
            observationDistributionClient = new ObservationDistributionServiceStub();
            get(StingrayHealthService.class).registerHealthProbe(observationDistributionClient.getName() +"-isConnected: ", observationDistributionClient::isConnectionEstablished);
            get(StingrayHealthService.class).registerHealthProbe(observationDistributionClient.getName() +"-numberofmessagesobserved: ", observationDistributionClient::getNumberOfMessagesObserved);
        }
        observationDistributionClient.openConnection();
        log.info("Establishing and verifying connection to Azure.");
        if (observationDistributionClient.isConnectionEstablished()) {
            ObservationMessage stubMessage = buildStubObservation();
            observationDistributionClient.publish(stubMessage);
        }
        String mesurementsName = config.get("measurements.name");
        MetricsDistributionClient metricsDistributionClient = new MetricsDistributionServiceStub(mesurementsName);
        MappedIdRepository mappedIdRepository = init(MappedIdRepository.class, () -> createMappedIdRepository(doImportData));
        ObservationDistributionClient finalObservationDistributionClient = observationDistributionClient;
        ScheduledImportManager scheduledImportManager = init(ScheduledImportManager.class, () -> wireScheduledImportManager(sdClient, finalObservationDistributionClient, metricsDistributionClient, mappedIdRepository));
        ObservationDistributionResource observationDistributionResource = initAndRegisterJaxRsWsComponent(ObservationDistributionResource.class, () -> createObservationDistributionResource(finalObservationDistributionClient));
        get(StingrayHealthService.class).registerHealthProbe("observationDistribution.message.count", observationDistributionResource::getDistributedCount);
        init(Random.class, this::createRandom);
        RandomizerResource randomizerResource = initAndRegisterJaxRsWsComponent(RandomizerResource.class, this::createRandomizerResource);
        get(StingrayHealthService.class).registerHealthProbe("randomizer.request.count", randomizerResource::getRequestCount);
        get(StingrayHealthService.class).registerHealthProbe("mappedIdRepository.size", mappedIdRepository::size);
        get(StingrayHealthService.class).registerHealthProbe(sdClient.getName() + "-isHealthy: ", sdClient::isHealthy);
        get(StingrayHealthService.class).registerHealthProbe(sdClient.getName() + "-isLogedIn: ", sdClient::isLoggedIn);
        get(StingrayHealthService.class).registerHealthProbe(sdClient.getName() + "-numberofTrendsSamples: ", sdClient::getNumberOfTrendSamplesReceived);
        scheduledImportManager.startScheduledImportOfTrendIds();
    }

    private SdClient createSdClient(ApplicationProperties config) {
        SdClient sdClient;
        String useSDProdValue = config.get("sd.api.prod");

        if (Boolean.valueOf(useSDProdValue)) {
            String apiUrl = config.get("sd.api.url");
            try {
                URI apiUri = new URI(apiUrl);
                sdClient = new MetasysApiClientRest(apiUri);
                log.info("Running with a live REST SD.");
            } catch (URISyntaxException e) {
                throw new MetasysCloudConnectorException("Failed to connect SD Client to URL: " + apiUrl, e);
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

    private ScheduledImportManager wireScheduledImportManager(SdClient sdClient, ObservationDistributionClient distributionClient, MetricsDistributionClient metricsClient, MappedIdRepository mappedIdRepository) {

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
