package no.cantara.realestate.metasys.cloudconnector.automationserver;

import no.cantara.realestate.metasys.cloudconnector.CloudConnectorObjectMapper;
import org.slf4j.Logger;

import static org.slf4j.LoggerFactory.getLogger;

public class TrendSamplesMapper {
    private static final Logger log = getLogger(TrendSamplesMapper.class);
    public static MetasysTrendSampleResult mapFromJson(String trendSampleJson) {
        MetasysTrendSampleResult result = null;
        try {
            result = CloudConnectorObjectMapper.getInstance().getObjectMapper().readValue(trendSampleJson, MetasysTrendSampleResult.class);
        } catch (Exception var2) {
            log.error("Unable to unmarshal SensorReading", var2);
        }
        return result;
    }
}
