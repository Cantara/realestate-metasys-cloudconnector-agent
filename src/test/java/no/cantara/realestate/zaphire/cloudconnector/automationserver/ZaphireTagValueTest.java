package no.cantara.realestate.zaphire.cloudconnector.automationserver;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ZaphireTagValueTest {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void deserializeTagValueWithNumericValue() throws Exception {
        String json = "{\n" +
                "  \"Name\": \"example/tag1\",\n" +
                "  \"Units\": \"DegreesCelsius\",\n" +
                "  \"UnitsDisplay\": \"°C\",\n" +
                "  \"Value\": 23.412\n" +
                "}";

        ZaphireTagValue tagValue = objectMapper.readValue(json, ZaphireTagValue.class);

        assertEquals("example/tag1", tagValue.getName());
        assertEquals("DegreesCelsius", tagValue.getUnits());
        assertEquals("°C", tagValue.getUnitsDisplay());
        assertEquals(23.412, tagValue.getNumericValue().doubleValue(), 0.001);
        assertNull(tagValue.getErrorMessage());
        assertFalse(tagValue.hasError());
    }

    @Test
    void deserializeTagValueWithStringValue() throws Exception {
        String json = "{\n" +
                "  \"Name\": \"example/tag2\",\n" +
                "  \"Value\": \"some-string\"\n" +
                "}";

        ZaphireTagValue tagValue = objectMapper.readValue(json, ZaphireTagValue.class);

        assertEquals("example/tag2", tagValue.getName());
        assertEquals("some-string", tagValue.getValue());
        assertNull(tagValue.getNumericValue());
    }

    @Test
    void deserializeTagValueWithNullValue() throws Exception {
        String json = "{\n" +
                "  \"Name\": \"example/tag3\",\n" +
                "  \"Value\": null\n" +
                "}";

        ZaphireTagValue tagValue = objectMapper.readValue(json, ZaphireTagValue.class);

        assertEquals("example/tag3", tagValue.getName());
        assertNull(tagValue.getValue());
        assertNull(tagValue.getNumericValue());
    }

    @Test
    void deserializeTagValueWithError() throws Exception {
        String json = "{\n" +
                "  \"Name\": \"example/tag4\",\n" +
                "  \"ErrorMessage\": \"Not found.\",\n" +
                "  \"Value\": null\n" +
                "}";

        ZaphireTagValue tagValue = objectMapper.readValue(json, ZaphireTagValue.class);

        assertEquals("example/tag4", tagValue.getName());
        assertTrue(tagValue.hasError());
        assertEquals("Not found.", tagValue.getErrorMessage());
    }

    @Test
    void deserializeArray() throws Exception {
        String json = "[\n" +
                "  {\"Name\": \"example/tag1\", \"Units\": \"DegreesCelsius\", \"UnitsDisplay\": \"°C\", \"Value\": 23.412},\n" +
                "  {\"Name\": \"example/tag2\", \"Value\": \"some-string\"},\n" +
                "  {\"Name\": \"example/tag3\", \"Value\": null},\n" +
                "  {\"Name\": \"example/tag4\", \"ErrorMessage\": \"Not found.\", \"Value\": null}\n" +
                "]";

        List<ZaphireTagValue> tagValues = objectMapper.readValue(json, new TypeReference<List<ZaphireTagValue>>() {});

        assertEquals(4, tagValues.size());
        assertEquals("example/tag1", tagValues.get(0).getName());
        assertFalse(tagValues.get(0).hasError());
        assertTrue(tagValues.get(3).hasError());
    }

    @Test
    void toStringContainsFields() throws Exception {
        String json = "{\"Name\": \"sensor/temp\", \"Units\": \"DegreesCelsius\", \"Value\": 21.5}";

        ZaphireTagValue tagValue = objectMapper.readValue(json, ZaphireTagValue.class);

        String str = tagValue.toString();
        assertTrue(str.contains("sensor/temp"));
        assertTrue(str.contains("21.5"));
        assertTrue(str.contains("DegreesCelsius"));
    }
}
