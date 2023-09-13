package no.cantara.realestate.metasys.cloudconnector.automationserver.stream;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;

import java.util.Map;

import static org.slf4j.LoggerFactory.getLogger;

public class ParsedObservedValue  {
    private static final Logger log = getLogger(ParsedObservedValue.class);


    String id;
    Object value;
    String itemReference;


    public ParsedObservedValue() {
    }

    @JsonProperty("item")
    protected void unpackNameFromNestedObject(Map<String, Object> item) {
        this.id = (String) item.get("id");
        this.itemReference = (String) item.get("itemReference");
        this.value = item.get("presentValue");
        log.trace("ParsedObservedValue: id={}, itemReference={}, value={}", id, itemReference, value);
    }



    public ObservedValue toObservedValue() {
        ObservedValue observedValue = null;
        if (value != null && value instanceof Number) {
            observedValue = new ObservedValueNumber(id,(Number) value,itemReference);
        } else {
            observedValue = new ObservedValueString(id,(String) value,itemReference);
        }
        return observedValue;
    }
}
