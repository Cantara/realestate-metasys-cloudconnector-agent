package no.cantara.realestate.metasys.cloudconnector.automationserver.stream;

import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import org.glassfish.jersey.media.sse.EventInput;
import org.glassfish.jersey.media.sse.InboundEvent;
import org.glassfish.jersey.media.sse.SseFeature;
import org.slf4j.Logger;

import java.time.Instant;

import static org.slf4j.LoggerFactory.getLogger;

/**
 * Stream client for Metasys ServerSentEvents (SSE) streams.
 */
public class MetasysStreamClient {
    private static final Logger log = getLogger(MetasysStreamClient.class);
    private final Client client;

    private boolean isLoggedIn = false;
    private boolean isStreamOpen = false;
    private long lastEventReceievedAt = -1;

    public MetasysStreamClient() {
        client = init();
    }
    // For testing
    protected MetasysStreamClient(Client client) {
        this.client = client;
    }

    public static void main(String[] args) {
        if (args.length != 2) {
            System.out.println("Usage: java -cp \"target/ServerSentEvents-<version>.jar\" no.cantara.sse.SseClient <sseUrl> <bearerToken>");
            System.exit(1);
        }
        String sseUrl = args[0];
        String bearerToken = args[1];

        MetasysStreamClient sseClient = new MetasysStreamClient();
        sseClient.openStream(sseUrl, bearerToken, new StreamListener() {
            @Override
            public void onEvent(StreamEvent streamEvent) {
                log.info("Received event: {}", streamEvent);
            }
        });

    }

    public void openStream(String sseUrl, String bearerToken, StreamListener streamListener) {
        Thread t = new Thread(() -> {
            EventInput eventInput = client.target(sseUrl)
                    .request()
                    .header("Authorization", "Bearer " + bearerToken)
                    .get(EventInput.class);
            isLoggedIn = true;
            isStreamOpen = true;
            try {
                while (!eventInput.isClosed()) {
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
                            lastEventReceievedAt = System.currentTimeMillis();
                        } catch (Exception e) {
                            //FIXME improve error handling
                            log.error("Failed to read data from inboundEvent: {}", inboundEvent, e);
                        }
                    }
                }
            } catch (InterruptedException e) {
                //Do nothing based on sleep interupted
            }
        });
        t.start();

    }

    private static Client init() {
        Client client = ClientBuilder.newBuilder()
                .register(SseFeature.class)
                .build();
        return client;
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

    protected void setLastEventReceievedAt(long lastEventReceievedAt) {
        this.lastEventReceievedAt = lastEventReceievedAt;
    }

    protected boolean hasReceivedMessagesRecently() {
        return Instant.ofEpochMilli(lastEventReceievedAt).isAfter(Instant.now().minusSeconds(30));
    }

    public boolean isHealthy() {
        return isStreamOpen && isLoggedIn && hasReceivedMessagesRecently();
    }

    public boolean isStreamOpen() {
        return isStreamOpen;
    }
}

