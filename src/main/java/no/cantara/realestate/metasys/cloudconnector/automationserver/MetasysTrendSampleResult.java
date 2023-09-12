package no.cantara.realestate.metasys.cloudconnector.automationserver;

import java.util.List;

public class MetasysTrendSampleResult {
    private Long total;
    private String next;
    private String previous;
    private List<MetasysTrendSample> items;
    private String attribute;
    private String self;
    private String objectUrl;

    public MetasysTrendSampleResult() {
    }

    public Long getTotal() {
        return total;
    }

    public void setTotal(Long total) {
        this.total = total;
    }

    public String getNext() {
        return next;
    }

    public void setNext(String next) {
        this.next = next;
    }

    public String getPrevious() {
        return previous;
    }

    public void setPrevious(String previous) {
        this.previous = previous;
    }

    public List<MetasysTrendSample> getItems() {
        return items;
    }

    public void setItems(List<MetasysTrendSample> items) {
        this.items = items;
    }

    public String getAttribute() {
        return attribute;
    }

    public void setAttribute(String attribute) {
        this.attribute = attribute;
    }

    public String getSelf() {
        return self;
    }

    public void setSelf(String self) {
        this.self = self;
    }

    public String getObjectUrl() {
        return objectUrl;
    }

    public void setObjectUrl(String objectUrl) {
        this.objectUrl = objectUrl;
    }
}
