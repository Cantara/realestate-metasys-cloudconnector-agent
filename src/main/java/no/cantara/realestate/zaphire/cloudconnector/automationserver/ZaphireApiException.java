package no.cantara.realestate.zaphire.cloudconnector.automationserver;

import no.cantara.realestate.zaphire.cloudconnector.ZaphireCloudConnectorException;

public class ZaphireApiException extends ZaphireCloudConnectorException {
    private final int statusCode;

    public ZaphireApiException(String message, int statusCode) {
        super(message);
        this.statusCode = statusCode;
    }

    public ZaphireApiException(String message, Throwable cause) {
        super(message, cause);
        this.statusCode = 0;
    }

    public int getStatusCode() {
        return statusCode;
    }
}
