package no.cantara.realestate.metasys.cloudconnector.automationserver.streampoc;

import io.undertow.Undertow;
import io.undertow.io.Sender;
import io.undertow.util.Headers;
import org.slf4j.Logger;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static org.slf4j.LoggerFactory.getLogger;

public class MockServerSentEventsRunner {
    private static final Logger log = getLogger(MockServerSentEventsRunner.class);
    public static final String SUBSCRIPTION_ID = "subscription123345";

    private Undertow server;
    private ScheduledExecutorService executor;

    public enum Scenario {
        HELLO, HTTP_204, SERVER_CLOSE, NETWORK_HICKUP
    }

    public void start(int port, Scenario scenario) {
        executor = Executors.newScheduledThreadPool(1);
        server = Undertow.builder()
                .addHttpListener(port, "localhost")
                .setHandler(exchange -> {
                    Sender sender = null;
                    switch (scenario) {
                        case HELLO: // Event + lukke
                            exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "text/event-stream");
                            exchange.setPersistent(false);
                            sender = exchange.getResponseSender();
                            sender.send("id: eventId789\nevent: hello\ndata: "+ SUBSCRIPTION_ID +"\n\n");

                            // Start sending additional events
                            Sender finalSender = sender;
                            executor.scheduleAtFixedRate(() -> {
                                try {
                                    finalSender.send("id: %s\nevent: object.values.heartbeat\ndata: %s\n\n".formatted(UUID.randomUUID(), Instant.now().toString()));
                                } catch (Exception e) {
                                    log.error("Error sending SSE message", e);
                                }
                            }, 1, 1, TimeUnit.SECONDS);
                            break;
                        case HTTP_204:
                            log.debug("Received 204 scenario");
                            exchange.setStatusCode(204);
                            exchange.endExchange();
                            break;
                        case SERVER_CLOSE: // Event + lukke
                            exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "text/event-stream");
                            exchange.setPersistent(false);
                            sender = exchange.getResponseSender();
                            sender.send("data: close\n\n");
                            sender.close();
                            break;
                        case NETWORK_HICKUP: // Hickup
                            exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "text/event-stream");
                            exchange.getConnection().close(); // brå avslutning
                            break;
                    }
                }).build();
        server.start();
    }

    public void stop() {
        if (server != null) {
            server.stop();
        }
    }
}

