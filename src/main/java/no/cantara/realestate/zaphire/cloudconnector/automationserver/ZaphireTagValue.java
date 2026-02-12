package no.cantara.realestate.zaphire.cloudconnector.automationserver;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * POJO for Zaphire Tag/Values API response.
 * Maps from JSON: {"Name": "...", "Value": 23.4, "Units": "DegreesCelsius", "UnitsDisplay": "°C", "ErrorMessage": "..."}
 */
public class ZaphireTagValue {

    @JsonProperty("Name")
    private String name;

    @JsonProperty("Value")
    private Object value;

    @JsonProperty("Units")
    private String units;

    @JsonProperty("UnitsDisplay")
    private String unitsDisplay;

    @JsonProperty("ErrorMessage")
    private String errorMessage;

    public ZaphireTagValue() {
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Object getValue() {
        return value;
    }

    public void setValue(Object value) {
        this.value = value;
    }

    public String getUnits() {
        return units;
    }

    public void setUnits(String units) {
        this.units = units;
    }

    public String getUnitsDisplay() {
        return unitsDisplay;
    }

    public void setUnitsDisplay(String unitsDisplay) {
        this.unitsDisplay = unitsDisplay;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public boolean hasError() {
        return errorMessage != null && !errorMessage.isEmpty();
    }

    public Number getNumericValue() {
        if (value instanceof Number) {
            return (Number) value;
        }
        return null;
    }

    @Override
    public String toString() {
        return "ZaphireTagValue{" +
                "name='" + name + '\'' +
                ", value=" + value +
                ", units='" + units + '\'' +
                ", unitsDisplay='" + unitsDisplay + '\'' +
                ", errorMessage='" + errorMessage + '\'' +
                '}';
    }
}
