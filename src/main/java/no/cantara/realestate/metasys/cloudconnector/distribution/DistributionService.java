package no.cantara.realestate.metasys.cloudconnector.distribution;

import no.cantara.realestate.mappingtable.MappedSensorId;
import no.cantara.realestate.metasys.cloudconnector.automationserver.MetasysTrendSample;
import no.cantara.realestate.observations.ObservationMessage;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.slf4j.LoggerFactory.getLogger;

public class DistributionService implements ObservationDistributionClient {
    private static final Logger log = getLogger(DistributionService.class);


    @Override
    public void publish(ObservationMessage message) {

    }

    @Override
    public void publishAll(Set<MetasysTrendSample> trendSamples, MappedSensorId mappedSensorId) {

    }

    @Override
    public List<ObservationMessage> getObservedMessages() {
        return new ArrayList<>();
    }
}
