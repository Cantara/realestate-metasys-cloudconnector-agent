package no.cantara.realestate.metasys.cloudconnector.automationserver.stream;

import jakarta.ws.rs.client.*;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.sse.SseEventSource;
import no.cantara.realestate.metasys.cloudconnector.automationserver.MetasysClient;
import no.cantara.realestate.security.UserToken;
import org.slf4j.Logger;

import java.io.IOException;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.slf4j.LoggerFactory.getLogger;

/**
 * Stream client for Metasys ServerSentEvents (SSE) streams.
 */
public class MetasysStreamClient {
    private static final Logger log = getLogger(MetasysStreamClient.class);
    private final Client client;
    private final MetasysClient metasysClient;

    private boolean isLoggedIn = false;
    private boolean isStreamOpen = false;
    private Instant lastEventReceievedAt = null;
    private Thread streamThread = null;
    private WebTarget target;
    private final int reconnectDelaySeconds = 5;
    private SseEventSource eventSource;
    // State tracking
    private final AtomicBoolean isManualClose = new AtomicBoolean(false);
    private final AtomicReference<Instant> lastEventTime = new AtomicReference<>(Instant.now());
    private final AtomicReference<ConnectionCloseReason> closeReason = new AtomicReference<>(ConnectionCloseReason.NONE);
    private final AtomicReference<Integer> lastResponseStatus = new AtomicReference<>();

    public enum ConnectionCloseReason {
        NONE,
        MANUAL_CLOSE,
        AUTHENTICATION_ERROR,
        SERVER_ERROR,
        NETWORK_ERROR,
        TIMEOUT,
        SERVER_CLOSED,
        STREAM_NOT_RESUMABLE, AUTHORIZATION_ERROR, UNKNOWN
    }

    public MetasysStreamClient() {
        metasysClient = MetasysClient.getInstance();
        client = init();
    }

    // For testing
    protected MetasysStreamClient(MetasysClient metasysClient) {
        this.client = init();
        this.metasysClient = metasysClient;
    }
    protected MetasysStreamClient(Client client, MetasysClient metasysClient) {
        this.client = client;
        this.metasysClient = metasysClient;
    }

    private Client init() {
        // Create Jersey client
        Client client = ClientBuilder.newBuilder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .build();
        return client;
    }


    public void openStream(String sseUrl, String bearerToken, String lastKnownEventId, StreamListener streamListener) {
        log.info("Opening stream for url {}, lastKnownEventId: {}, StreamListener class: {}, objectId: ", sseUrl, lastKnownEventId, streamListener.getClass().getName(), System.identityHashCode(streamListener));
        // Create a custom filter for adding the bearer token
        UserToken userToken = metasysClient.getUserToken();
        String accessToken = userToken.getAccessToken();
        DynamicAuthorizationFilter authFilter = new DynamicAuthorizationFilter(metasysClient); //AuthorizationFilter(accessToken);
        // Create a response monitor filter to track response status codes
        ResponseMonitorFilter responseMonitorFilter = new ResponseMonitorFilter();

        // Configure target with bearer token filter
        target = client.target(sseUrl).register(authFilter).register(responseMonitorFilter);
        // Create SSE event source with auto-reconnect. This creates a new Thread named jersey-client-async-executor
        if (eventSource != null && eventSource.isOpen()) {
            log.debug("Reusing existing eventSource");
//            eventSource.close();
        } else {
            log.debug("Creating new eventSource");
            eventSource = SseEventSource.target(target)
                    .reconnectingEvery(reconnectDelaySeconds, TimeUnit.SECONDS)
                    .build();
        }

        // Register event handlers
        eventSource.register(
                // Process each event
                inboundEvent -> {
                    String name = null;
                    String data = null;
                    StreamEvent streamEvent = null;
                    try {
                        data = inboundEvent.readData();
                        name = inboundEvent.getName();

                        if (name == null || name.isEmpty()) {
                            log.trace("Received SSE event: {}, {}", name, data);
                        } else {
                            log.debug("Received SSE event: name: {}, data: {}", name, data);

                            streamEvent = EventInputMapper.toStreamEvent(inboundEvent);
                            streamListener.onEvent(streamEvent);
                            lastEventReceievedAt = Instant.ofEpochMilli(System.currentTimeMillis());
                        }
                    } catch (Exception e) {
                        log.info("Error processing SSE inboundEvent. Name: {}. Data: {}. StreamEvent: {} ", name, data, streamEvent, e);
                    }
                },
                // Handle errors
                throwable -> {
//                    log.warn("SSE connection error {}", throwable);
                    log.warn("SSE connection error. Message: {}", throwable.getMessage());
                    if (userToken != null) {
                        log.debug("SSE connection error. UserToken expires {}", userToken.getExpires());
                        if (userToken.getExpires().isBefore(Instant.now())) {
                            String subtractedAccessToken = accessToken.substring(0, 100) + "..." + accessToken.substring(accessToken.length() - 100);
                            log.info("SSE connection error. accessToken {}, expired. {}", subtractedAccessToken, userToken.getExpires());
                        }
                    } else {
                        log.debug("SSE connection error. UserToken is null");
                    }
                    isStreamOpen = false;
                    isLoggedIn = false;
//                    System.out.println("Error processing SSE event: " + throwable.printStackTrace(););
//                    throw new RealEstateStreamException("Failed to open stream on URL: " + sseUrl +
//                            ", lastKnownEventId: " + lastKnownEventId + ", reason: " + throwable.getMessage(),
//                            RealEstateStreamException.Action.RECREATE_SUBSCRIPTION_NEEDED);
                },
                // Handle connection close
                () -> {
                    log.warn("SSE connection closed. EventSource {}", eventSource);
                    if (eventSource != null) {
                        log.debug("SSE connection closed. EventSource isOpen {}", eventSource.isOpen());
                    }
                    log.debug("SSE connection closed. Client {}", client);
                    if (client != null) {
                        log.info("SSE connection closed. Client {}", client.getConfiguration());
                    }
                    log.debug("SSE connection closed. target {}", target);
                    if (userToken != null) {
                        log.debug("SSE connection closed. UserToken expires {}", userToken.getExpires());
                        if (userToken.getExpires().isBefore(Instant.now())) {
                            String subtractedAccessToken = accessToken.substring(0, 100) + "..." + accessToken.substring(accessToken.length() - 100);
                            log.info("SSE connection closed. accessToken {}, expired. {}", subtractedAccessToken, userToken.getExpires());
                        }
                    } else {
                        log.debug("SSE connection closed. UserToken is null");
                    }
                    ConnectionCloseReason reason = determineCloseReason();
                    ConnectionCloseInfo closeInfo = new ConnectionCloseInfo(
                            reason,
                            lastResponseStatus.get(),
                            lastEventTime.get()
                    );

                    log.info("SSE connection closed. closeInfo {}", closeInfo);
                    isStreamOpen = false;
                    isLoggedIn = false;
                    streamListener.onClose(closeInfo);
//                    throw new RealEstateStreamException("Stream was closed: " + sseUrl +
//                            ", closeInfo: " + closeInfo, RealEstateStreamException.Action.RECONNECT_NEEDED);
                }
        );

        // Start listening for events
        eventSource.open();
        isLoggedIn = true;

        log.info("SSE client connected to {}", sseUrl);
    }


    public void reconnectStream(String sseUrl, String bearerToken, String lastKnownEventId, StreamListener streamListener) {
        log.info("Requesting reconnect for stream at url {} with lastKnownEventId {}. ", sseUrl, lastKnownEventId);
        if (isStreamOpen()) {
            eventSource.close();
            try {
                log.trace("Waiting for stream to close before reconnecting");
                Thread.sleep(200);
                if (target != null) {
                    log.trace("Setting target to null");
                    target = null;
                }
            } catch (InterruptedException e) {
                log.trace("Interrupted while waiting for stream to close");
                throw new RuntimeException(e);
            }
        }
        log.trace("Stream closed, proceeding to open new stream");
        openStream(sseUrl, bearerToken, lastKnownEventId, streamListener);
    }


    public void close() {
        if (eventSource != null) {
            eventSource.close();
        }
        if (client != null) {
            client.close();
        }
    }

    public String getName() {
        return "MetasysStreamClient";
    }

    public boolean isLoggedIn() {
        return isLoggedIn;
    }

    protected void setLastEventReceievedAt(Instant lastEventReceievedAt) {
        this.lastEventReceievedAt = lastEventReceievedAt;
    }

    protected boolean hasReceivedMessagesRecently() {
        return lastEventReceievedAt.isAfter(Instant.now().minusSeconds(30));
    }

    public Instant getWhenLastMessageImported() {
        return lastEventReceievedAt;
    }

    public boolean isHealthy() {
        return isStreamOpen() && isLoggedIn() && hasReceivedMessagesRecently();
    }

    public boolean isStreamOpen() {
        return eventSource != null && eventSource.isOpen();
//        return isStreamOpen;
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

    // Dynamic authorization filter that fetches the token for each request
    private static class DynamicAuthorizationFilter implements ClientRequestFilter {

        private final MetasysClient authClient;

        public DynamicAuthorizationFilter(MetasysClient metasysClient) {
            authClient = metasysClient;
        }

        @Override
        public void filter(ClientRequestContext requestContext) throws IOException {
            // Get the current token from the supplier for each request
            String currentToken = authClient.getUserToken().getAccessToken();
            requestContext.getHeaders().add(
                    HttpHeaders.AUTHORIZATION,
                    "Bearer " + currentToken);
        }
    }

    // Filter to monitor response status codes
    private class ResponseMonitorFilter implements ClientResponseFilter {
        @Override
        public void filter(ClientRequestContext requestContext, ClientResponseContext responseContext) throws IOException {
            int status = responseContext.getStatus();
            lastResponseStatus.set(status);

            // Detect specific status codes that may lead to connection close
            if (status == 204) {
                // From Metasys documentation:
                // If Metasys has cleaned up your buffer or cannot find that id in the recently sent buffer,
                // you will get a 204 response per the SSE specification. Because your existing stream is not resumable,
                // your Last-Event-Id is no longer valid.
                // The recovery path is to start anew by calling get a stream without the Last-Event-Id and subscribe again to data of interest.
                closeReason.set(ConnectionCloseReason.STREAM_NOT_RESUMABLE);
                log.warn("Stream not resumable, reconnect is needed. Received 204 response. Last-Event-Id is no longer valid.");
            } else if (status == Response.Status.UNAUTHORIZED.getStatusCode()) {
                closeReason.set(ConnectionCloseReason.AUTHENTICATION_ERROR);
                log.warn("Authentication failed with status 401. Check bearer token validity.");
            } else if (status == Response.Status.FORBIDDEN.getStatusCode()) {
                closeReason.set(ConnectionCloseReason.AUTHORIZATION_ERROR);
                log.warn("Forbidden access with status 403. You might need to restart stream.");
            } else if (status >= 500) {
                closeReason.set(ConnectionCloseReason.SERVER_ERROR);
                log.warn("Remote server error response: {}", status);
            } else if (status != Response.Status.OK.getStatusCode() &&
                    status != Response.Status.ACCEPTED.getStatusCode()) {
                closeReason.set(ConnectionCloseReason.UNKNOWN);
                log.warn("Unexpected response status: {}", status);
            }
        }
    }

    private ConnectionCloseReason determineCloseReason() {
        if (isManualClose.get()) {
            return ConnectionCloseReason.MANUAL_CLOSE;
        }

        ConnectionCloseReason currentReason = closeReason.get();
        if (currentReason != ConnectionCloseReason.NONE) {
            return currentReason;
        }

        // If we have no specific reason yet, consider it server-initiated
        return ConnectionCloseReason.SERVER_CLOSED;
    }


    public static void main(String[] args) {
        if (args.length < 2) {
            System.out.println("Usage: java -cp \"target/metasys-cloudconnector-app-<version>.jar\" " +
                    "no.cantara.realestate.metasys.cloudconnector.automationserver.stream.MetasysStreamClient <metasysStreamUrl> <bearerToken> <lastKnownEventId>");
            System.exit(1);
        }
        String sseUrl = args[0];
        String bearerToken = args[1];
        String lastKnownEventId = null;
        if (args.length > 2) {
            lastKnownEventId = args[2];
        }

        MetasysStreamClient sseClient = new MetasysStreamClient();
        //Open stream first time
        //Reconnect stream from lastKnownEventId, aka a previous subscription
//        lastKnownEventId = "123";
        sseClient.openStream(sseUrl, bearerToken, lastKnownEventId, new StreamListener() {
            @Override
            public void onEvent(StreamEvent streamEvent) {
                log.info("Received event: {}", streamEvent);
            }

            @Override
            public void onClose(ConnectionCloseInfo closeInfo) {
                log.info("Connection closed: {}", closeInfo);
            }
        });
    }
}




