package no.cantara.realestate.metasys.cloudconnector.automationserver;

import java.util.List;

public class MetasysTrendSampleResult {
    private Long total;
    private String next;
    private String previous;
    private List<MetasysTrendSample> items;
    private String attributeUrl;
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

    public String getAttributeUrl() {
        return attributeUrl;
    }

    public void setAttributeUrl(String attributeUrl) {
        this.attributeUrl = attributeUrl;
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
