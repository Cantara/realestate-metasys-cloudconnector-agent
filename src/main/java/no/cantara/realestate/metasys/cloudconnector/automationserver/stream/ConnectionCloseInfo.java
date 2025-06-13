package no.cantara.realestate.metasys.cloudconnector.automationserver.stream;


import java.time.Instant;

public class ConnectionCloseInfo {
    private final MetasysStreamClient.ConnectionCloseReason reason;
    private final Integer lastStatusCode;
    private final Instant lastEventTime;

    public ConnectionCloseInfo(MetasysStreamClient.ConnectionCloseReason reason, Integer lastStatusCode, Instant lastEventTime) {
        this.reason = reason;
        this.lastStatusCode = lastStatusCode;
        this.lastEventTime = lastEventTime;
    }

    public MetasysStreamClient.ConnectionCloseReason getReason() {
        return reason;
    }

    public Integer getLastStatusCode() {
        return lastStatusCode;
    }

    public Instant getLastEventTime() {
        return lastEventTime;
    }

    @Override
    public String toString() {
        return "ConnectionCloseInfo{" +
                "reason=" + reason +
                ", lastStatusCode=" + lastStatusCode +
                ", lastEventTime=" + lastEventTime +
                '}';
    }
}
