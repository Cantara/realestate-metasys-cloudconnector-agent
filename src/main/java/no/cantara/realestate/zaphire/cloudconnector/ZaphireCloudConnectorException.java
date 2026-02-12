package no.cantara.realestate.zaphire.cloudconnector;

import no.cantara.realestate.metasys.cloudconnector.StatusType;
import org.slf4j.helpers.MessageFormatter;

import java.util.UUID;

public class ZaphireCloudConnectorException extends RuntimeException {
    private final UUID uuid;
    private Enum<StatusType> statusType = null;

    public ZaphireCloudConnectorException(String message) {
        super(message);
        uuid = UUID.randomUUID();
    }

    public ZaphireCloudConnectorException(String message, Throwable throwable) {
        super(message, throwable);
        this.uuid = UUID.randomUUID();
    }

    public ZaphireCloudConnectorException(String message, Throwable throwable, Object... parameters) {
        this(MessageFormatter.format(message, parameters).getMessage(), throwable);
    }

    public ZaphireCloudConnectorException(String msg, StatusType statusType) {
        this(msg);
        this.statusType = statusType;
    }

    public ZaphireCloudConnectorException(String msg, Throwable t, StatusType statusType) {
        this(msg, t);
        this.statusType = statusType;
    }

    public ZaphireCloudConnectorException(String msg, Exception e, StatusType statusType) {
        this(msg, e);
        this.statusType = statusType;
    }

    @Override
    public String getMessage() {
        String message = super.getMessage() + " MessageId: " + uuid.toString();
        if (getCause() != null) {
            message = message + "\n\tCause: " + getCause().getMessage();
        }
        return message;
    }

    public String getMessageId() {
        return uuid.toString();
    }
}
