package no.cantara.realestate.metasys.cloudconnector.distribution;


import no.cantara.realestate.mappingtable.MappedSensorId;
import no.cantara.realestate.metasys.cloudconnector.automationserver.MetasysTrendSample;

import java.util.Set;

public interface ObservationDistributionClient {

    void publish(ObservationMessage message);

    void publishAll(Set<MetasysTrendSample> trendSamples, MappedSensorId mappedSensorId);
}
