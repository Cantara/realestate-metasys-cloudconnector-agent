package no.cantara.realestate.metasys.cloudconnector.automationserver;

import java.net.URI;
import java.net.http.HttpClient;
import java.util.function.Supplier;

public class MetasysCommandExecutor {

    public MetasysCommandExecutor(URI apiUri, HttpClient httpClient, Supplier<String> accessTokenSource,
                                  MetasysCircuitBreaker breaker) {
    }

    public <T> T execute(MetasysCommand<T> command) {
        throw new UnsupportedOperationException("Not implemented yet");
    }
}
