package no.cantara.realestate.metasys.cloudconnector.automationserver.stream;


public class MetasysOpenStreamEvent extends StreamEvent {
    public static final String name = "hello";
    private final String subscriptionId;

    public MetasysOpenStreamEvent(String id, String data) {
        super(id, name, null, data);
        this.subscriptionId = data;
    }

    public String getSubscriptionId() {
        return subscriptionId;
    }

    @Override
    public String toString() {
        return "MetasysOpenStreamEvent{" +
                "subscriptionId='" + subscriptionId + '\'' +
                "} " + super.toString();
    }
}
