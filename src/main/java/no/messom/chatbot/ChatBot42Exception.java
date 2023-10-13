package no.messom.chatbot;

import org.slf4j.helpers.MessageFormatter;

import java.util.UUID;

public class ChatBot42Exception extends RuntimeException {
    private final UUID uuid;
    private Enum<StatusType> statusType = null;

    public ChatBot42Exception(String message) {
        super(message);
        uuid = UUID.randomUUID();
    }

    public ChatBot42Exception(String message, Throwable throwable) {
        super(message, throwable);
        this.uuid = UUID.randomUUID();
    }

    public ChatBot42Exception(String message, Throwable throwable, Object... parameters) {
        this(MessageFormatter.format(message, parameters).getMessage(),throwable);

    }

    public ChatBot42Exception(String msg, StatusType statusType) {
        this(msg);
        this.statusType = statusType;
    }
    public ChatBot42Exception(String msg, Throwable t, StatusType statusType) {
        this(msg,t);
        this.statusType = statusType;
    }

    public ChatBot42Exception(String msg, Exception e, StatusType statusType) {
        this(msg, e);
        this.statusType = statusType;
    }


    @Override
    public String getMessage() {

        String message = super.getMessage() +" MessageId: " + uuid.toString();
        if (getCause() != null) {
            message = message + "\n\tCause: " + getCause().getMessage();
        }
        return message;
    }

    public String getMessageId() {
        return uuid.toString();
    }


}

