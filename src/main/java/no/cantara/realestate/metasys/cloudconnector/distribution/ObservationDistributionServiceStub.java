package no.cantara.realestate.metasys.cloudconnector.distribution;

import no.cantara.realestate.mappingtable.MappedSensorId;
import no.cantara.realestate.metasys.cloudconnector.automationserver.MetasysTrendSample;

import java.util.Set;

public class ObservationDistributionServiceStub implements ObservationDistributionClient {
    @Override
    public void publish(ObservationMessage message) {

    }

    @Override
    public void publishAll(Set<MetasysTrendSample> trendSamples, MappedSensorId mappedSensorId) {

    }
}
