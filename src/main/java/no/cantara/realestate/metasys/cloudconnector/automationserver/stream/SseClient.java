package no.cantara.realestate.metasys.cloudconnector.automationserver.stream;

import jakarta.ws.rs.client.*;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.sse.SseEventSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class SseClient implements AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(SseClient.class);

    private final String serverUrl;
    private final String bearerToken;
    private final int reconnectDelaySeconds;
    private SseEventSource eventSource;
    private final Client client;
    private final WebTarget target;

    public SseClient(String serverUrl, String bearerToken, int reconnectDelaySeconds) {
        this.serverUrl = serverUrl;
        this.bearerToken = bearerToken;
        this.reconnectDelaySeconds = reconnectDelaySeconds;

        // Create Jersey client
        client = ClientBuilder.newBuilder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build();

        // Create a custom filter for adding the bearer token
        AuthorizationFilter authFilter = new AuthorizationFilter(bearerToken);

        // Configure target with bearer token filter
        target = client.target(serverUrl).register(authFilter);
    }

    // Custom authorization filter class
    private static class AuthorizationFilter implements ClientRequestFilter {
        private final String bearerToken;

        public AuthorizationFilter(String bearerToken) {
            this.bearerToken = bearerToken;
        }

        @Override
        public void filter(ClientRequestContext requestContext) throws IOException {
            requestContext.getHeaders().add(
                    HttpHeaders.AUTHORIZATION,
                    "Bearer " + bearerToken);
        }
    }

    public void connect(Consumer<String> onEvent, Consumer<Throwable> onError) {
        // Create SSE event source with auto-reconnect
        eventSource = SseEventSource.target(target)
                .reconnectingEvery(reconnectDelaySeconds, TimeUnit.SECONDS)
                .build();

        // Register event handlers
        eventSource.register(
                // Process each event
                inboundEvent -> {
                    try {
                        String data = inboundEvent.readData();
                        log.debug("Received SSE event: {}", data);
                        onEvent.accept(data);
                    } catch (Exception e) {
                        log.error("Error processing SSE event", e);
                        onError.accept(e);
                    }
                },
                // Handle errors
                throwable -> {
                    log.error("SSE connection error", throwable);
                    onError.accept(throwable);
                },
                // Handle connection close
                () -> log.info("SSE connection closed")
        );

        // Start listening for events
        eventSource.open();
        log.info("SSE client connected to {}", serverUrl);
    }

    public boolean isConnected() {
        return eventSource != null && eventSource.isOpen();
    }

    @Override
    public void close() {
        if (eventSource != null) {
            eventSource.close();
        }
        client.close();
        log.info("SSE client closed");
    }

    // Generic method to connect with typed events
    public <T> void connectWithType(Class<T> eventType, Consumer<T> onEvent, Consumer<Throwable> onError) {
        eventSource = SseEventSource.target(target)
                .reconnectingEvery(reconnectDelaySeconds, TimeUnit.SECONDS)
                .build();

        eventSource.register(
                inboundEvent -> {
                    try {
                        T data = inboundEvent.readData(eventType);
                        log.debug("Received typed SSE event: {}", data);
                        onEvent.accept(data);
                    } catch (Exception e) {
                        log.error("Error processing typed SSE event", e);
                        onError.accept(e);
                    }
                },
                throwable -> {
                    log.error("SSE connection error", throwable);
                    onError.accept(throwable);
                },
                () -> log.info("SSE connection closed")
        );

        eventSource.open();
        log.info("Typed SSE client connected to {}", serverUrl);
    }
}
