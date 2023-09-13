package no.cantara.realestate.metasys.cloudconnector.automationserver.stream;

public class ObservedValueNumber extends ObservedValue<Number>{
    public ObservedValueNumber(String id, Number value, String itemReference) {
        super(id, value, itemReference);
    }

    @Override
    public Number getValue() {
        return super.value;
    }
}
