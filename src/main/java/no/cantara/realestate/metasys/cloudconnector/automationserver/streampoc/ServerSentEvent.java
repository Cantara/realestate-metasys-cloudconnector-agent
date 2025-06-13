package no.cantara.realestate.metasys.cloudconnector.automationserver.streampoc;

public class ServerSentEvent {
    private String id;
    private String event;
    private String data;
    private Integer retry;

    // Getters and setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getEvent() { return event; }
    public void setEvent(String event) { this.event = event; }
    public String getData() { return data; }
    public void setData(String data) { this.data = data; }
    public Integer getRetry() { return retry; }
    public void setRetry(Integer retry) { this.retry = retry; }

    @Override
    public String toString() {
        return "ServerSentEvent{" +
                "data='" + data + '\'' +
                ", id='" + id + '\'' +
                ", event='" + event + '\'' +
                ", retry=" + retry +
                '}';
    }
}