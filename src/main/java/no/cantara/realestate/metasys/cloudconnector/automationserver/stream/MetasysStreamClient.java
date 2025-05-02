package no.cantara.realestate.metasys.cloudconnector.automationserver.stream;

import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.core.Response;
import no.cantara.realestate.RealEstateException;
import org.glassfish.jersey.media.sse.EventInput;
import org.glassfish.jersey.media.sse.InboundEvent;
import org.glassfish.jersey.media.sse.SseFeature;
import org.slf4j.Logger;

import java.time.Instant;

import static no.cantara.realestate.metasys.cloudconnector.utils.StringUtils.hasValue;
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

    public MetasysStreamClient() {
        client = init();
    }
    // For testing
    protected MetasysStreamClient(Client client) {
        this.client = client;
    }

    private static Client init() {
        Client client = ClientBuilder.newBuilder()
                .register(SseFeature.class)
                .build();
        return client;
    }

    public void openStream(String sseUrl, String bearerToken, String lastKnownEventId, StreamListener streamListener) {
        //Check that the server are able to establish or re-establish the subscription
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
                log.info("StreamListener thread interrupted");
                if (eventInput != null) {
                    eventInput.close();
                }
                log.info("StreamListener thread closed");
            }
        });
        streamThread.setName("StreamListener");
        streamThread.start();

    }
    public void reconnectStream(String sseUrl, String bearerToken, String lastKnownEventId, StreamListener streamListener) throws RealEstateStreamException {
        log.info("Reconnect stream at url {} with lastKnownEventId {}", sseUrl, lastKnownEventId);
        if (streamThread != null && streamThread.isAlive()) {
            streamThread.interrupt();
            try {
                streamThread.join(200);
            } catch (InterruptedException e) {
                log.warn("Interrupted while waiting for stream thread to join");
            }
        }
        try {
            openStream(sseUrl, bearerToken, lastKnownEventId, streamListener);
        } catch (RealEstateStreamException re) {
            isStreamOpen = false;
            isLoggedIn = false;
            if (re.getAction() != null && re.getAction() == RealEstateStreamException.Action.RECREATE_SUBSCRIPTION_NEEDED) {
                log.warn("Recreate subscription needed for URL: {}, lastKnownEventId: {}", sseUrl, lastKnownEventId);

            } else {
                log.warn("Failed to open stream on URL: {}, lastKnownEventId: {}", sseUrl, lastKnownEventId, re);
            }
            throw re;
        }
    }


    public void close() {
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
        return isStreamOpen && isLoggedIn && hasReceivedMessagesRecently();
    }

    public boolean isStreamOpen() {
        return isStreamOpen;
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




