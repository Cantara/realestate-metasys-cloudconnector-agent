package no.cantara.realestate.metasys.cloudconnector;

import no.cantara.config.ApplicationProperties;
import no.cantara.realestate.cloudconnector.RealestateCloudconnectorApplication;
import no.cantara.stingray.application.StingrayApplication;
import no.cantara.stingray.application.StingrayApplicationFactory;
import org.slf4j.Logger;

import static org.slf4j.LoggerFactory.getLogger;

public class MetasysCloudconnectorApplicationFactory implements StingrayApplicationFactory<RealestateCloudconnectorApplication> {
    private static final Logger log = getLogger(MetasysCloudconnectorApplicationFactory.class);

    @Override
    public Class<?> providerClass() {
        return MetasysCloudconnectorApplication.class;
    }

    @Override
    public String alias() {
        return "MetasysCloudconnector";
    }

    @Override
    public StingrayApplication<RealestateCloudconnectorApplication> create(ApplicationProperties applicationProperties) {
        return new MetasysCloudconnectorApplication(applicationProperties);
    }

}
