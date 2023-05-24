package no.cantara.realestate.metasys.cloudconnector;

import no.cantara.config.ApplicationProperties;
import no.cantara.realestate.mappingtable.repository.MappedIdQuery;
import no.cantara.realestate.mappingtable.repository.MappedIdQueryBuilder;
import no.cantara.realestate.mappingtable.repository.MappedIdRepository;
import no.cantara.realestate.mappingtable.repository.MappedIdRepositoryImpl;
import no.cantara.realestate.metasys.cloudconnector.automationserver.MetasysApiClientRest;
import no.cantara.realestate.metasys.cloudconnector.automationserver.SdClient;
import no.cantara.realestate.metasys.cloudconnector.automationserver.SdClientSimulator;
import no.cantara.realestate.metasys.cloudconnector.distribution.*;
import no.cantara.realestate.metasys.cloudconnector.observations.MappedIdBasedImporter;
import no.cantara.realestate.metasys.cloudconnector.observations.MetasysMappedIdQueryBuilder;
import no.cantara.realestate.metasys.cloudconnector.observations.ScheduledImportManager;
import no.cantara.realestate.metasys.cloudconnector.observations.TrendLogsImporter;
import no.cantara.realestate.metasys.cloudconnector.sensors.MetasysConfigImporter;
import no.cantara.realestate.metasys.cloudconnector.sensors.SensorType;
import no.cantara.stingray.application.AbstractStingrayApplication;
import no.cantara.stingray.application.health.StingrayHealthService;
import no.cantara.stingray.security.StingraySecurity;
import org.slf4j.Logger;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Paths;
import java.util.Random;

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
        ObservationDistributionClient observationDistributionClient = new ObservationDistributionServiceStub();
        String mesurementsName = config.get("measurements.name");
        MetricsDistributionClient metricsDistributionClient = new MetricsDistributionServiceStub(mesurementsName);
        MappedIdRepository mappedIdRepository = init(MappedIdRepository.class, () -> createMappedIdRepository(doImportData));
        ScheduledImportManager scheduledImportManager = init(ScheduledImportManager.class, () -> wireScheduledImportManager(sdClient, observationDistributionClient, metricsDistributionClient, mappedIdRepository));
        ObservationDistributionResource observationDistributionResource = initAndRegisterJaxRsWsComponent(ObservationDistributionResource.class, () -> createObservationDistributionResource(observationDistributionClient));
        get(StingrayHealthService.class).registerHealthProbe("observationDistribution.message.count", observationDistributionResource::getDistributedCount);
        init(Random.class, this::createRandom);
        RandomizerResource randomizerResource = initAndRegisterJaxRsWsComponent(RandomizerResource.class, this::createRandomizerResource);
        get(StingrayHealthService.class).registerHealthProbe("randomizer.request.count", randomizerResource::getRequestCount);
        get(StingrayHealthService.class).registerHealthProbe("mappedIdRepository.size", mappedIdRepository::size);
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

        MappedIdQuery mappedIdQuery = new MetasysMappedIdQueryBuilder().realEstate("REstate1").build();

        TrendLogsImporter trendLogsImporter = new MappedIdBasedImporter(mappedIdQuery, sdClient, distributionClient, metricsClient, mappedIdRepository);
        scheduledImportManager = new ScheduledImportManager(trendLogsImporter, config);

        MappedIdQuery energyOnlyQuery = new MappedIdQueryBuilder().realEstate("RealEst2")
                .sensorType(SensorType.energy.name())
                .build();

        TrendLogsImporter mappedIdBasedImporter = new MappedIdBasedImporter(energyOnlyQuery, sdClient, distributionClient, metricsClient, mappedIdRepository);
        scheduledImportManager.addTrendLogsImporter(mappedIdBasedImporter);

        /*#15 TODO create a single file for reading import config from file

        List<MappedIdBasedImporter> fileReadImports = MultipleREsConfig.buildConfig(sdClient, distributionClient, metricsClient, mappedIdRepository);
        for (MappedIdBasedImporter fileReadImport : fileReadImports) {
            scheduledImportManager.addTrendLogsImporter(fileReadImport);
        }
         */


        return scheduledImportManager;
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
