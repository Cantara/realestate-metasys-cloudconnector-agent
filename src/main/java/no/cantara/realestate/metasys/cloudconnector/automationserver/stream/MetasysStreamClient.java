package no.cantara.realestate.metasys.cloudconnector.automationserver.stream;

import jakarta.ws.rs.client.*;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.sse.SseEventSource;
import no.cantara.realestate.metasys.cloudconnector.automationserver.MetasysClient;
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
        UNKNOWN
    }

    public MetasysStreamClient() {
        client = init();
    }
    // For testing
    protected MetasysStreamClient(Client client) {
        this.client = client;
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
        // Create a custom filter for adding the bearer token
        DynamicAuthorizationFilter authFilter = new DynamicAuthorizationFilter();
        // Create a response monitor filter to track response status codes
        ResponseMonitorFilter responseMonitorFilter = new ResponseMonitorFilter();

        // Configure target with bearer token filter
        target = client.target(sseUrl).register(authFilter).register(responseMonitorFilter);
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
                        StreamEvent streamEvent = EventInputMapper.toStreamEvent(inboundEvent);
                        streamListener.onEvent(streamEvent);
                        lastEventReceievedAt = Instant.ofEpochMilli(System.currentTimeMillis());
                    } catch (Exception e) {
                        log.info("Error processing SSE event", e);
                        //FIXME improve error handling of stream event
                    }
                },
                // Handle errors
                throwable -> {
                    log.warn("SSE connection error {}", throwable);
                    log.warn("SSE connection failure" , throwable.getMessage());
//                    System.out.println("Error processing SSE event: " + throwable.printStackTrace(););
//                    throw new RealEstateStreamException("Failed to open stream on URL: " + sseUrl +
//                            ", lastKnownEventId: " + lastKnownEventId + ", reason: " + throwable.getMessage(),
//                            RealEstateStreamException.Action.RECREATE_SUBSCRIPTION_NEEDED);
                },
                // Handle connection close
                () -> {
                    log.info("SSE connection closed.");
                    log.info("SSE connection closed. EventSource {}", eventSource);
                    if (eventSource != null) {
                        log.info("SSE connection closed. EventSource isOpen {}", eventSource.isOpen());
                    }
                    log.info("SSE connection closed. Client {}", client);
                    if (client != null) {
                        log.info("SSE connection closed. Client {}", client.getConfiguration());
                    }
                    log.info("SSE connection closed. target {}", target);
                    ConnectionCloseReason reason = determineCloseReason();
                    ConnectionCloseInfo closeInfo = new ConnectionCloseInfo(
                            reason,
                            lastResponseStatus.get(),
                            lastEventTime.get()
                    );

                    log.info("SSE connection closed. closeInfo {}", closeInfo);
                }
        );

        // Start listening for events
        eventSource.open();
        isLoggedIn = true;

        log.info("SSE client connected to {}", sseUrl);
        //Check that the server are able to establish or re-establish the subscription
        /*
        Response response = null;
        if (hasValue(lastKnownEventId)) {
            response = client.target(sseUrl).request().header("Authorization", "Bearer " + bearerToken).header("Last-Event-Id", lastKnownEventId).get();
            if (response == null) {
                throw new RealEstateStreamException("Failed to open stream on URL: " + sseUrl + ", lastKnownEventId: " + lastKnownEventId + ", response is null: " + response);
            } else if (response.getStatus() == 204) {
                throw new RealEstateStreamException("Failed to open stream on URL: " + sseUrl + ", lastKnownEventId: " + lastKnownEventId + ", response: " + response, RealEstateStreamException.Action.RECREATE_SUBSCRIPTION_NEEDED);
            }
        } else {
            response = client.target(sseUrl).request().header("Authorization", "Bearer " + bearerToken).get();
            if (response == null) {
                throw new RealEstateStreamException("Failed to open stream on URL: " + sseUrl + ", lastKnownEventId: " + lastKnownEventId + ", response is null: " + response);
            }
        }
        streamThread = new Thread(() -> {

            EventInput eventInput = null;
            try {
                if (hasValue(lastKnownEventId)) {
                    try {
                        eventInput = client.target(sseUrl)
                                .request()
                                .header("Authorization", "Bearer " + bearerToken)
                                .header("Last-Event-Id", lastKnownEventId)
                                .get(EventInput.class);
                    } catch (Exception e) {
                        e.printStackTrace();
                        eventInput = null;
                        throw new RealEstateException("Failed to open stream on URL: " + sseUrl + ", lastKnownEventId: " + lastKnownEventId, e);
                    }
                } else {
                    try {
                        eventInput = client.target(sseUrl)
                                .request()
                                .header("Authorization", "Bearer " + bearerToken)
                                .get(EventInput.class);
                    } catch (Exception e) {
                        e.printStackTrace();
                        eventInput = null;
                        throw new RealEstateException("Failed to open stream on URL: " + sseUrl, e);
                    }
                }
                isLoggedIn = true;
                isStreamOpen = true;
//            try {
                while (eventInput != null && !eventInput.isClosed()) {

                    InboundEvent inboundEvent = eventInput.read();
                    if (inboundEvent == null) {
                        // Reconnect logic (you can add a delay here before reconnecting)
                        isLoggedIn = false;
                        eventInput.close();
                        isStreamOpen = false;
                        Thread.sleep(100);

                        eventInput = client.target(sseUrl)
                                .request()
                                .header("Authorization", "Bearer " + bearerToken)
                                .get(EventInput.class);
                        isLoggedIn = true;
                        isStreamOpen = true;
                    } else {
                        try {
                            String data = inboundEvent.readData(String.class);
                            System.out.println("Received Event: " + data);
                            log.trace("Received Event: id: {}, name: {}, comment: {}, \ndata: {}", inboundEvent.getId(), inboundEvent.getName(), inboundEvent.getComment(), data);
                            StreamEvent streamEvent = EventInputMapper.toStreamEvent(inboundEvent);
                            streamListener.onEvent(streamEvent);
                            lastEventReceievedAt = Instant.ofEpochMilli(System.currentTimeMillis());
                        } catch (Exception e) {
                            //FIXME improve error handling
                            log.error("Failed to read data from inboundEvent: {}", inboundEvent, e);
                        }
                    }
                }
            } catch (InterruptedException e) {
                log.info("StreamListener thread interrupted", e);
                if (eventInput != null) {
                    eventInput.close();
                }
                log.info("StreamListener thread closed");
            }
        });
        streamThread.setName("StreamListener");
        streamThread.start();


         */


    }


    public void reconnectStream(String sseUrl, String bearerToken, String lastKnownEventId, StreamListener streamListener) {
        log.info("Requesting reconnect for stream at url {} with lastKnownEventId {}", sseUrl, lastKnownEventId);
        log.warn("Not doing nothing about reconnect stream for now");//reconnectRequested = true; // Signal to the thread to reconnect
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

        public DynamicAuthorizationFilter() {
        }

        @Override
        public void filter(ClientRequestContext requestContext) throws IOException {
            // Get the current token from the supplier for each request
            String currentToken = MetasysClient.getInstance().getUserToken().getAccessToken();
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
            if (status == Response.Status.UNAUTHORIZED.getStatusCode()) {
                closeReason.set(ConnectionCloseReason.AUTHENTICATION_ERROR);
                log.warn("Authentication failed with status 401. Check bearer token validity.");
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

    public static class ConnectionCloseInfo {
        private final ConnectionCloseReason reason;
        private final Integer lastStatusCode;
        private final Instant lastEventTime;

        public ConnectionCloseInfo(ConnectionCloseReason reason, Integer lastStatusCode, Instant lastEventTime) {
            this.reason = reason;
            this.lastStatusCode = lastStatusCode;
            this.lastEventTime = lastEventTime;
        }

        public ConnectionCloseReason getReason() {
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
        sseClient.openStream(sseUrl, bearerToken,lastKnownEventId, new StreamListener() {
            @Override
            public void onEvent(StreamEvent streamEvent) {
                log.info("Received event: {}", streamEvent);
            }
        });
    }
}




