package no.cantara.realestate.metasys.cloudconnector.automationserver;

import javax.json.bind.annotation.JsonbProperty;

public class Value {
    @JsonbProperty("value.value")
    private Number value;
    @JsonbProperty("value.units")
    private String units;

    public Value() {

    }

    public Number getValue() {
        return value;
    }

    public void setValue(Number value) {
        this.value = value;
    }


    public String getUnits() {
        return units;
    }

    public void setUnits(String units) {
        this.units = units;
    }

    @Override
    public String toString() {
        return "Value{" +
                "value=" + value +
                ", units='" + units + '\'' +
                '}';
    }
}
