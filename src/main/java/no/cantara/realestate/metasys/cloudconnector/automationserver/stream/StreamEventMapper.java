package no.cantara.realestate.metasys.cloudconnector.automationserver.stream;

import no.cantara.realestate.json.RealEstateObjectMapper;
import org.slf4j.Logger;

import static org.slf4j.LoggerFactory.getLogger;

public class StreamEventMapper {
    private static final Logger log = getLogger(StreamEventMapper.class);

    public static ObservedValue mapFromJson(String streamEventJson) {
        ParsedObservedValue result = null;
        try {
            result = RealEstateObjectMapper.getInstance().getObjectMapper().readValue(streamEventJson, ParsedObservedValue.class);
        } catch (Exception var2) {
            log.error("Unable to unmarshal SensorEvent data", var2);
        }
        if (result != null) {
            return result.toObservedValue();
        } else {
            return null;
        }
    }
}
