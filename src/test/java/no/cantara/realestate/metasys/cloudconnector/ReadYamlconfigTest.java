package no.cantara.realestate.metasys.cloudconnector;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import no.cantara.realestate.mappingtable.repository.MappedIdQuery;
import no.cantara.realestate.mappingtable.repository.MappedIdQueryBuilder;
import no.cantara.realestate.metasys.cloudconnector.sensors.SensorType;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;

import static junit.framework.Assert.assertEquals;

public class ReadYamlconfigTest {

    @Test
    @Disabled
    void readYaml4Query() {
        MappedIdQuery energyOnlyQuery = new MappedIdQueryBuilder().realEstate("RealEst2")
                .sensorType(SensorType.energy.name())
                .build();
        ObjectMapper mapper;
        try {
            mapper = new ObjectMapper(new YAMLFactory().disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER));
            mapper.enable(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT);
            YamlMappedIdQuery yamlQuery = mapper.readValue(new File("mappedIdQuery.yaml"), YamlMappedIdQuery.class);
            assertEquals(energyOnlyQuery, yamlQuery);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
