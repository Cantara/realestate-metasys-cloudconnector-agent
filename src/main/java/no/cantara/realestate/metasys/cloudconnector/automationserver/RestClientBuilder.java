package no.cantara.realestate.metasys.cloudconnector.automationserver;

import java.net.URI;

public class RestClientBuilder {
    public static RestClientBuilder newBuilder() {
        return new RestClientBuilder();
    }

    public RestClientBuilder baseUri(URI apiUri) {
        return this;
    }

    public <T> T build(Class<T> clazzName) {
        return null;
    }
}
