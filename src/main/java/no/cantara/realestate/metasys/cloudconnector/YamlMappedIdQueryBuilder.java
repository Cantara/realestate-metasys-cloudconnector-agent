package no.cantara.realestate.metasys.cloudconnector;

import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import no.cantara.realestate.mappingtable.repository.MappedIdQueryBuilder;

@JsonPOJOBuilder(withPrefix = "with")
public class YamlMappedIdQueryBuilder extends MappedIdQueryBuilder {

    public MappedIdQueryBuilder setRealEstate(String value) {
        return super.realEstate(value);
    }
}
