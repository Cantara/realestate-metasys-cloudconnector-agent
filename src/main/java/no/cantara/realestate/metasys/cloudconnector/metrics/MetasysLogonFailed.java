package no.cantara.realestate.metasys.cloudconnector.metrics;

public class MetasysLogonFailed extends Metric {

    public MetasysLogonFailed() {
        super("metrics-metasys-cloudconnector");
        addTag("metasys-api-logon", "failed");
    }
}
