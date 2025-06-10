package no.cantara.realestate.metasys.cloudconnector.observations;

import no.cantara.realestate.mappingtable.metasys.MetasysSensorId;
import no.cantara.realestate.mappingtable.repository.MappedIdQueryBuilder;

@Deprecated
public class MetasysMappedIdQueryBuilder extends MappedIdQueryBuilder {
    public MetasysMappedIdQueryBuilder() {
        super();
        sensorIdClass(MetasysSensorId.class);
    }
}
