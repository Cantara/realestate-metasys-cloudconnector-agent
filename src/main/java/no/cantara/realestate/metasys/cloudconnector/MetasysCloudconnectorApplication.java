package no.cantara.realestate.metasys.cloudconnector;

import no.cantara.config.ApplicationProperties;
import no.cantara.stingray.application.AbstractStingrayApplication;
import no.cantara.stingray.application.health.StingrayHealthService;
import no.cantara.stingray.security.StingraySecurity;
import org.slf4j.Logger;

import java.util.Random;

import static org.slf4j.LoggerFactory.getLogger;

public class MetasysCloudconnectorApplication extends AbstractStingrayApplication<MetasysCloudconnectorApplication> {
    private static final Logger log = getLogger(MetasysCloudconnectorApplication.class);
    public static void main(String[] args) {
        ApplicationProperties config = new MetasysCloudconnectorApplicationFactory()
                .conventions(ApplicationProperties.builder())
                .build();
        new MetasysCloudconnectorApplication(config).init().start();
        log.info("Server started. See status on {}:{}{}/health", "http://localhost",config.get("server.port"),config.get("server.context-path"));
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
        init(Random.class, this::createRandom);
        RandomizerResource randomizerResource = initAndRegisterJaxRsWsComponent(RandomizerResource.class, this::createRandomizerResource);
        get(StingrayHealthService.class).registerHealthProbe("randomizer.request.count", randomizerResource::getRequestCount);
    }

    private Random createRandom() {
        return new Random(System.currentTimeMillis());
    }

    private RandomizerResource createRandomizerResource() {
        Random random = get(Random.class);
        return new RandomizerResource(random);
    }
}
