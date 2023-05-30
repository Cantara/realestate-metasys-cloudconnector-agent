package no.cantara.realestate.metasys.cloudconnector;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import no.cantara.realestate.mappingtable.repository.MappedIdQueryBuilder;

@JsonDeserialize(builder = MappedIdQueryBuilder.class)
public static class YamlMappedIdQueryBuilder  {


}
