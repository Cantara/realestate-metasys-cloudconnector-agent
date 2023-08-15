package no.cantara.realestate.metasys.cloudconnector.automationserver.stream;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;

import java.util.Map;

import static org.slf4j.LoggerFactory.getLogger;

public class ParsedObservedValue  {
    private static final Logger log = getLogger(ParsedObservedValue.class);


    String id;
    Number value;
    String itemReference;


    public ParsedObservedValue() {
    }

    @JsonProperty("item")
    private void unpackNameFromNestedObject(Map<String, Object> item) {
        this.id = (String) item.get("id");
        this.itemReference = (String) item.get("itemReference");
        this.value = (Number) item.get("presentValue");
        log.trace("ParsedObservedValue: id={}, itemReference={}, value={}", id, itemReference, value);
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Number getValue() {
        return value;
    }

    public void setValue(Number value) {
        this.value = value;
    }

    public String getItemReference() {
        return itemReference;
    }

    public void setItemReference(String itemReference) {
        this.itemReference = itemReference;
    }

    public ObservedValue toObservedValue() {
        ObservedValue observedValue = new ObservedValue(getId(), getValue(),getItemReference());
        return observedValue;
    }
}
