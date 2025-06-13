package no.cantara.realestate.metasys.cloudconnector.automationserver;

import no.cantara.realestate.metasys.cloudconnector.MetasysCloudConnectorException;

public class MetasysApiException extends MetasysCloudConnectorException {
    private final int statusCode;

    public MetasysApiException(String message, int statusCode) {
        super(message);
        this.statusCode = statusCode;
    }

    public MetasysApiException(String message, Throwable cause) {
        super(message, cause);
        this.statusCode = 0;
    }

    public int getStatusCode() {
        return statusCode;
    }
}
