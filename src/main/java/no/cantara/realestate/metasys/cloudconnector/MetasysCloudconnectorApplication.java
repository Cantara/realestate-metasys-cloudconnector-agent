package no.cantara.realestate.metasys.cloudconnector;

import no.cantara.config.ApplicationProperties;
import no.cantara.realestate.mappingtable.repository.MappedIdQuery;
import no.cantara.realestate.mappingtable.repository.MappedIdQueryBuilder;
import no.cantara.realestate.mappingtable.repository.MappedIdRepository;
import no.cantara.realestate.mappingtable.repository.MappedIdRepositoryImpl;
import no.cantara.realestate.metasys.cloudconnector.automationserver.MetasysApiClientRest;
import no.cantara.realestate.metasys.cloudconnector.automationserver.SdClient;
import no.cantara.realestate.metasys.cloudconnector.automationserver.SdClientSimulator;
import no.cantara.realestate.metasys.cloudconnector.distribution.MetricsDistributionClient;
import no.cantara.realestate.metasys.cloudconnector.distribution.ObservationDistributionClient;
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
                .build();
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
        MappedIdRepository mappedIdRepository = init(MappedIdRepository.class, () -> createMappedIdRepository(doImportData));
        init(Random.class, this::createRandom);
        RandomizerResource randomizerResource = initAndRegisterJaxRsWsComponent(RandomizerResource.class, this::createRandomizerResource);
        get(StingrayHealthService.class).registerHealthProbe("randomizer.request.count", randomizerResource::getRequestCount);
        get(StingrayHealthService.class).registerHealthProbe("mappedIdRepository.size", mappedIdRepository::size);
    }

    protected MappedIdRepository createMappedIdRepository(boolean doImportData) {
        MappedIdRepository mappedIdRepository = new MappedIdRepositoryImpl();
        if (doImportData) {
            String configDirectory = config.get("config.directory");
            if (!Paths.get(configDirectory).toFile().exists()) {
                throw new MetasysCloudConnectorException("Import of data from " + configDirectory + " failed. Directory does not exist.");
            }
            new MetasysConfigImporter().importMetasysConfig(configDirectory, mappedIdRepository);
        }
        return mappedIdRepository;
    }

    private ScheduledImportManager wireScheduledImportManager(ObservationDistributionClient distributionClient, MetricsDistributionClient metricsClient, MappedIdRepository mappedIdRepository) throws URISyntaxException {

        String useSDProdValue = getConfigValue("influxdb.prod");

        SdClient sdClient;
        if (Boolean.valueOf(useSDProdValue)) {
            String apiUrl = getConfigValue("sd.api.url");
            URI apiUri = new URI(apiUrl);
            sdClient = new MetasysApiClientRest(apiUri);
            log.info("Running with a live REST SD.");
        } else {
            URI simulatorUri = URI.create("https://simulator.totto.org:8080/SD");
            sdClient = new SdClientSimulator();
            log.info("Running with a simulator of SD.");
        }

        /*
        String measurementName = getConfigValue("MEASUREMENT_NAME");
        InfluxClient influxClient;
        String useInfluxProdValue = getConfigValue("influxdb.prod");
        if (Boolean.valueOf(useInfluxProdValue)) {
            log.info("Using live InfluxDb.");
            influxClient = new InfluxClient(measurementName);
        } else {
            log.info("Running with a simulator of InfluxDb.");
            InfluxDbSimulator simulator = new InfluxDbSimulator();
            influxClient = new InfluxClient(measurementName, simulator);
        }

         */

        ScheduledImportManager scheduledImportManager = null;

        MappedIdQuery mappedIdQuery = new MetasysMappedIdQueryBuilder().realEstate("RE1").build();


        TrendLogsImporter kjorboEnergyImporter = new MappedIdBasedImporter(mappedIdQuery, sdClient, distributionClient, metricsClient, mappedIdRepository);
        scheduledImportManager = new ScheduledImportManager(kjorboEnergyImporter);

        MappedIdQuery energyOnlyQuery = new MappedIdQueryBuilder().realEstate("RE1")
                .sensorType(SensorType.energy.name())
                .build();

        TrendLogsImporter kjorboPowerImporter = new MappedIdBasedImporter(energyOnlyQuery, sdClient, distributionClient, metricsClient, mappedIdRepository);
        scheduledImportManager.addTrendLogsImporter(kjorboPowerImporter);

        /*#15 TODO create a single file for reading import config from file

        List<MappedIdBasedImporter> fileReadImports = MultipleREsConfig.buildConfig(sdClient, distributionClient, metricsClient, mappedIdRepository);
        for (MappedIdBasedImporter fileReadImport : fileReadImports) {
            scheduledImportManager.addTrendLogsImporter(fileReadImport);
        }
         */


        return scheduledImportManager;
    }

    private Random createRandom() {
        return new Random(System.currentTimeMillis());
    }

    private RandomizerResource createRandomizerResource() {
        Random random = get(Random.class);
        return new RandomizerResource(random);
    }

    public static String getConfigValue(String key) {
        return ApplicationProperties.getInstance().get(key);
    }
}
