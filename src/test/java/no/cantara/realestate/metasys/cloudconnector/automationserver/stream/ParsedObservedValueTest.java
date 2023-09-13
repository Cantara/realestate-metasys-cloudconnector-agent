package no.cantara.realestate.metasys.cloudconnector.automationserver.stream;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ParsedObservedValueTest {

    @Test
    void unpackNameFromNestedObjectNumber() {
        Map<String, Object> item = Map.of("id", "id", "itemReference", "itemReference", "presentValue", 1);
        ParsedObservedValue parsedObservedValue = new ParsedObservedValue();
        parsedObservedValue.unpackNameFromNestedObject(item);
        ObservedValue observedValue = parsedObservedValue.toObservedValue();
        assertEquals("id", observedValue.getId());
        assertEquals("itemReference", observedValue.getItemReference());
        assertEquals(1, observedValue.getValue());

    }

    @Test
    void unpackNameFromNestedObjectString() {
        Map<String, Object> item = Map.of("id", "id", "itemReference", "itemReference", "presentValue", "bacnetChanged");
        ParsedObservedValue parsedObservedValue = new ParsedObservedValue();
        parsedObservedValue.unpackNameFromNestedObject(item);
        ObservedValue observedValue = parsedObservedValue.toObservedValue();
        assertEquals("id", observedValue.getId());
        assertEquals("itemReference", observedValue.getItemReference());
        assertEquals("bacnetChanged", observedValue.getValue());

    }
}