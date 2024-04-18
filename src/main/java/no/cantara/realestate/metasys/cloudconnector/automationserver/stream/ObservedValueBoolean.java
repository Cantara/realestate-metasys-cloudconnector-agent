package no.cantara.realestate.metasys.cloudconnector.automationserver.stream;

public class ObservedValueBoolean extends ObservedValue<Boolean>{
    public ObservedValueBoolean(String id, Boolean value, String itemReference) {
        super(id, value, itemReference);
    }

    @Override
    public Boolean getValue() {
        return super.value;
    }
}
