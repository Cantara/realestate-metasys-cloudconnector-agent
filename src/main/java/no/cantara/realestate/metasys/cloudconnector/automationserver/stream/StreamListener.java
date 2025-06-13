package no.cantara.realestate.metasys.cloudconnector.automationserver.stream;

public interface StreamListener {

    void onEvent(StreamEvent event);
    void onClose(ConnectionCloseInfo closeInfo);
}
