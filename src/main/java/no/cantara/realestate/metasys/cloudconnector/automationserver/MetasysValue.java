package no.cantara.realestate.metasys.cloudconnector.automationserver;

import no.cantara.realestate.observations.Value;

import javax.json.bind.annotation.JsonbProperty;


public class MetasysValue extends Value {
    @JsonbProperty("value")
    private Number value;
    @JsonbProperty("units")
    private String units;

    public MetasysValue() {

    }

//    public Number getValue() {
//        return value;
//    }
//
//    public void setValue(Number value) {
//        this.value = value;
//    }


//    public String getUnits() {
//        return units;
//    }
//
//    public void setUnits(String units) {
//        this.units = units;
//    }




    @Override
    public String toString() {
        return "MetasysValue{" +
                "value=" + value +
                ", units='" + units + '\'' +
                '}';
    }
}
