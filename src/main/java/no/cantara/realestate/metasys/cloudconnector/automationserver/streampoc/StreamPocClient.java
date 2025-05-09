package no.cantara.realestate.metasys.cloudconnector.automationserver.streampoc;

import no.cantara.config.ApplicationProperties;
import no.cantara.realestate.automationserver.BasClient;
import no.cantara.realestate.metasys.cloudconnector.MetasysCloudConnectorException;
import no.cantara.realestate.metasys.cloudconnector.MetasysCloudconnectorApplicationFactory;
import no.cantara.realestate.metasys.cloudconnector.automationserver.MetasysClient;
import no.cantara.realestate.metasys.cloudconnector.notifications.NotificationService;
import no.cantara.realestate.security.LogonFailedException;
import no.cantara.realestate.security.UserToken;
import org.slf4j.Logger;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import static org.slf4j.LoggerFactory.getLogger;

public class StreamPocClient {
    private static final Logger log = getLogger(StreamPocClient.class);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);
    private final MetasysClient metasysClient;
    private final ScheduledExecutorService scheduler;
    private final URI sdUri;
    private UserToken userToken;
    private String subscriptionId = null;
    private HttpClient httpClient;
    private Thread streamListenerThread;
    private final BlockingQueue<ServerSentEvent> eventQueue = new LinkedBlockingQueue<>();


    public StreamPocClient() {
        this(MetasysClient.getInstance());
    }

    protected StreamPocClient(MetasysClient metasysClient) {
        this.metasysClient = metasysClient;
        scheduler = Executors.newScheduledThreadPool(1);
        findLatestUserToken();
        scheduleTokenRefresh();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(REQUEST_TIMEOUT)
                .build();
        sdUri = metasysClient.getApiUri();
    }

    protected void findLatestUserToken() {
        userToken = metasysClient.getUserToken();
        String accessToken = userToken.getAccessToken();
        String shortAccessToken = shortenedAccessToken(accessToken);
        log.debug("Latest user token: {}. Expires: {}", shortAccessToken, userToken.getExpires());
    }

    protected void scheduleTokenRefresh() {
        // 2 min before user token expires, call findLatestUserToken
        // create new shcedule to call findLatestUserToken
        Runnable reminderTask = () -> {
            this.findLatestUserToken();
            this.scheduleTokenRefresh();
        };
        scheduler.schedule(reminderTask, 30, TimeUnit.SECONDS);
    }

    public UserToken getUserToken() {
        return userToken;
    }

    public String getSubscriptionId() {
        return subscriptionId;
    }

    private static MetasysClient initializeMetasysClient(ApplicationProperties config) {
        MetasysClient basClient = null;
        String apiUrl = config.get("sd.api.url");
        String username = config.get("sd.api.username");
        String password = config.get("sd.api.password");
        try {
            URI apiUri = new URI(apiUrl);
            log.info("Connect to Metasys API: {} with username: {}", apiUri, username);
            NotificationService notificationService = new NotificationService() {
                @Override
                public boolean sendWarning(String service, String warningMessage) {
                    log.info("Sending warning message: {}", warningMessage);
                    return true;
                }

                @Override
                public boolean sendAlarm(String service, String alarmMessage) {
                    log.info("Sending alarm message: {}", alarmMessage);
                    return true;
                }

                @Override
                public boolean clearService(String service) {
                    log.info("Clearing service: {}", service);
                    return true;
                }
            };
            basClient = MetasysClient.getInstance(username, password, apiUri, notificationService); //new MetasysApiClientRest(apiUri, notificationService);
            log.info("Running with a live REST SD.");
        } catch (URISyntaxException e) {
            throw new MetasysCloudConnectorException("Failed to connect SD Client to URL" + apiUrl, e);
        } catch (LogonFailedException e) {
            throw new MetasysCloudConnectorException("Failed to logon SD Client. URL used" + apiUrl, e);
        }
        return basClient;
    }

    public static String shortenedAccessToken(String accessToken) {
        return accessToken.length() > 200 ? accessToken.substring(0, 50) + "..." + accessToken.substring(accessToken.length() - 50) : accessToken;
    }

    public static void main(String[] args) {
        ApplicationProperties config = new MetasysCloudconnectorApplicationFactory()
                .conventions(ApplicationProperties.builder())
                .buildAndSetStaticSingleton();
        BasClient basClient = initializeMetasysClient(config);
        StreamPocClient streamPocClient = new StreamPocClient();

        //Verify that token refresh is working
        String accessToken = streamPocClient.getUserToken().getAccessToken();
        String shortAccessToken = shortenedAccessToken(accessToken);
        log.info("AT: {}", shortAccessToken);
        try {
            streamPocClient.createStream();
            ServerSentEvent event = streamPocClient.eventQueue.poll(10, TimeUnit.SECONDS);
            if (event == null) {
                throw new MetasysCloudConnectorException("StreamPocClient returned null events. Closing stream.");
            }
            String subscriptionId = streamPocClient.getSubscriptionId();
            log.info("Stream created. SubscriptionId: {}", subscriptionId);
            List<String> metasysObjectIds = List.of("408eb7e4-f63b-5db0-b665-999bfa6ad588");
            streamPocClient.subscribeToStream(subscriptionId, metasysObjectIds);

            do {
                String newAccessToken = streamPocClient.getUserToken().getAccessToken();
                String newShortAccessToken = shortenedAccessToken(newAccessToken);
                if (!newShortAccessToken.equals(shortAccessToken)) {
                    log.info("AT: {} -> {}, expires: {}", shortAccessToken, newShortAccessToken, streamPocClient.getUserToken().getExpires());
                    accessToken = newShortAccessToken;
                    shortAccessToken = newShortAccessToken;
                } else {
                    log.trace("Access token not changed. Expires: {}", streamPocClient.getUserToken().getExpires());
                }
                log.info("Waiting for events...");
                while (!streamPocClient.eventQueue.isEmpty()) {
                    log.trace("Event: {}", streamPocClient.eventQueue.poll());
                }
                Thread.sleep(10000);

            } while (true);
        } catch (InterruptedException e) {
            log.error("Error in main thread", e);
        } finally {
            log.info("Closing StreamPocClient");
            streamPocClient.close();
//            basClient.close();
        }
    }

    void close() {
        if (streamListenerThread != null) {
            streamListenerThread.interrupt();
            try {
                streamListenerThread.join();
            } catch (InterruptedException e) {
                log.error("Error while closing stream listener thread", e);
            }
        }
    }

    protected void subscribeToStream(String subscriptionId, List<String> metasysObjectIds) {
        for (String metasysObjectId : metasysObjectIds) {
            log.trace("Subscribe to metasysObjectId: {} subscriptionId: {}", metasysObjectId, subscriptionId);
            try {
                Integer httpStatus = metasysClient.subscribePresentValueChange(getSubscriptionId(), metasysObjectId);
                log.debug("Subscription to metasysObjectId: {} subscriptionId: {}, returned httpStatus: {}", metasysObjectId, subscriptionId, httpStatus);
            } catch (LogonFailedException e) {
                log.warn("Failed to logon to SD system. Could not subscribe to metasysObjectId: {} subscriptionId: {}", metasysObjectId, subscriptionId, e);
                throw e;
            }
        }
    }

    protected void createStream() {
        Runnable streamTask = () -> {
            String streamUrl = sdUri + "stream";
            log.info("Connecting to SSE stream at: {}", streamUrl);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(streamUrl))
                    .header("Authorization", "Bearer " + userToken.getAccessToken())
                    .GET()
                    .build();

            log.debug("Outgoing request: {}", request);

            try {
                // Use a blocking approach for simplicity
                HttpResponse<InputStream> response = httpClient.send(
                        request,
                        HttpResponse.BodyHandlers.ofInputStream()
                );

                int statusCode = response.statusCode();
                log.debug("Response status: {}", statusCode);

                if (statusCode == 200) {
                    try (InputStreamReader reader = new InputStreamReader(response.body(), StandardCharsets.UTF_8);
                         BufferedReader bufferedReader = new BufferedReader(reader)) {

                        processEventStream(bufferedReader);
                    }
                } else if (statusCode == 204) {
                    log.warn("Received 204 response");
                    throw new MetasysCloudConnectorException("Reconnect to stream with LastKnownEventId is " +
                            "not possible. Please Reconnect, and resubscribe to the stream.");
                } else {
                    log.error("Unexpected response status: {}", statusCode);
                    throw new MetasysCloudConnectorException("Unexpected response status: " + statusCode);
                }
            } catch (IOException | InterruptedException e) {
                log.error("Failed to connect to SSE stream", e);
                throw new MetasysCloudConnectorException("Failed to connect to SSE stream", e);
            }
        };

        streamListenerThread = new Thread(streamTask, "StreamListenerPoc");
        streamListenerThread.start();
    }
    /**
     * Process the incoming SSE event stream according to the specification.
     * Events are separated by blank lines and consist of fields starting with "id:", "event:", "data:", or "retry:".
     *
     * @param reader The buffered reader for the event stream
     * @throws IOException If an I/O error occurs
     */
    private void processEventStream(BufferedReader reader) throws IOException {
        String line;
        ServerSentEvent currentEvent = new ServerSentEvent();
        List<String> dataLines = new ArrayList<>();
        boolean hasData = false;

        while ((line = reader.readLine()) != null) {
            log.debug("Incoming SSE line: {}", line);

            // Empty line indicates the end of an event
            if (line.isEmpty()) {
                if (hasData) {
                    // Complete the current event
                    if (!dataLines.isEmpty()) {
                        currentEvent.setData(String.join("\n", dataLines));
                    }

                    log.info("Mapped SSE event: {}", currentEvent);
                    try {
                        eventQueue.put(currentEvent);

                        // Set the subscriptionId from the first open event
                        if (currentEvent.getEvent() != null &&
                                currentEvent.getEvent().equals("open") &&
                                subscriptionId == null) {
                            subscriptionId = currentEvent.getData();
                            log.info("Stream opened with subscriptionId: {}", subscriptionId);
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        log.error("Failed to add event to queue", e);
                        break;
                    }
                }

                // Reset for the next event
                currentEvent = new ServerSentEvent();
                dataLines.clear();
                hasData = false;
                continue;
            }

            // Process the field
            if (line.startsWith("id:")) {
                currentEvent.setId(line.substring(3).trim());
                hasData = true;
            } else if (line.startsWith("event:")) {
                currentEvent.setEvent(line.substring(6).trim());
                hasData = true;
            } else if (line.startsWith("data:")) {
                dataLines.add(line.substring(5).trim());
                hasData = true;
            } else if (line.startsWith("retry:")) {
                try {
                    currentEvent.setRetry(Integer.parseInt(line.substring(6).trim()));
                    hasData = true;
                } catch (NumberFormatException e) {
                    log.warn("Invalid retry value in SSE: {}", line);
                }
            } else {
                log.debug("Ignoring unknown SSE line: {}", line);
            }
        }

        // Handle any final event (in case the stream ends without an empty line)
        if (hasData) {
            if (!dataLines.isEmpty()) {
                currentEvent.setData(String.join("\n", dataLines));
            }

            log.info("Mapped final SSE event: {}", currentEvent);
            try {
                eventQueue.put(currentEvent);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("Failed to add final event to queue", e);
            }
        }
    }
}
