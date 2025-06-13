package no.cantara.realestate.metasys.cloudconnector.automationserver.streampoc;

import no.cantara.config.ApplicationProperties;
import no.cantara.realestate.automationserver.BasClient;
import no.cantara.realestate.cloudconnector.notifications.NotificationService;
import no.cantara.realestate.distribution.ObservationDistributionClient;
import no.cantara.realestate.mappingtable.MappedSensorId;
import no.cantara.realestate.mappingtable.rec.SensorRecObject;
import no.cantara.realestate.metasys.cloudconnector.MetasysCloudConnectorException;
import no.cantara.realestate.metasys.cloudconnector.MetasysCloudconnectorApplicationFactory;
import no.cantara.realestate.metasys.cloudconnector.automationserver.MetasysClient;
import no.cantara.realestate.metasys.cloudconnector.automationserver.stream.*;
import no.cantara.realestate.metasys.cloudconnector.observations.MetasysObservationMessage;
import no.cantara.realestate.observations.ObservationMessage;
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
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.slf4j.LoggerFactory.getLogger;

/**
 * Connect to Stream, and keep the observations flowing. With Refreshing the token.
 */
public class StreamPocClient implements StreamListener {
    private static final Logger log = getLogger(StreamPocClient.class);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);
    public static final String LOGON_FAILED = "Logon Failed";
    public static final String RECONNECT_WITH_LAST_KNOWN_EVENT_ID_FAILED = "Reconnect with LastKnownEventId failed";
    public static final String UNKNOWN_STATUS_CODE = "Unknown status code";
    public static final String METASYS_SERVER_CLOSED_STREAM = "Metasys server closed stream";
    public static final String NETWORK_INTERRUPTED = "Network connection to Metays server interrupted";
    public static final String PROCESSING_STREAM_ERROR = "Connecting to, or processing stream failed";
    public static final String UNEXPECTED_ERROR = "Unexpected error";
    public static final String STREAM_CLOSED_UNEXPECTEDLY = "Stream closed unexpectedly";
    public static final String STREAM_ENDED_WITHOUT_EMPTY_LINE = "Stream ended without empty line.";
    public static final String STREAM_ENDED_WITH_NULL = "Stream ended because readLine returned null.";
    private final MetasysClient metasysClient;
    private final ScheduledExecutorService scheduler;
    private final URI sdUri;
    private UserToken userToken;
    private String subscriptionId = null;
    private HttpClient httpClient;
    private Thread streamListenerThread;
    private final BlockingQueue<ServerSentEvent> eventQueue = new LinkedBlockingQueue<>();
    private volatile String lastKnownEventId = null;
    private AtomicReference<String> closingStreamReason = new AtomicReference<>(null);
    private boolean reconnectOnError = true;
    private StreamListener streamListener = null;
    private final ObservationDistributionClient distributionClient;


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
        distributionClient = initializeStubDistributionClient();
    }

    ObservationDistributionClient initializeStubDistributionClient() {
        return new ObservationDistributionClient() {
            int messageCount = 0;
            List<ObservationMessage> observedMessages = new ArrayList<>();
            @Override
            public String getName() {
                return "StubDistributionClient";
            }

            @Override
            public void openConnection() {
            log.debug("Opening connection to StubDistributionClient");
            }

            @Override
            public void closeConnection() {
                log.debug("Closing connection to StubDistributionClient");
            }

            @Override
            public boolean isConnectionEstablished() {
                return true;
            }

            @Override
            public void publish(ObservationMessage observationMessage) {
                log.info("Publishing ObservationMessage: {}", observationMessage);
                messageCount++;
                observedMessages.add(observationMessage);
            }

            @Override
            public long getNumberOfMessagesObserved() {
                return messageCount;
            }

            @Override
            public List<ObservationMessage> getObservedMessages() {
                return observedMessages;
            }
        };
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
            log.debug("Run scheduled token refresh. Scheduler has {} tasks", ((ScheduledThreadPoolExecutor) scheduler).getQueue().size());
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

    void close() {
        log.info("Closing Metasys stream client");
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
                Integer httpStatus = metasysClient.subscribePresentValueChange(subscriptionId, metasysObjectId);
                log.debug("Subscription to metasysObjectId: {} subscriptionId: {}, returned httpStatus: {}", metasysObjectId, subscriptionId, httpStatus);
            } catch (LogonFailedException e) {
                log.warn("Failed to logon to SD system. Could not subscribe to metasysObjectId: {} subscriptionId: {}", metasysObjectId, subscriptionId, e);
                closingStreamReason = new AtomicReference(LOGON_FAILED);
                throw e;
            }
        }
    }

    /**
     * Creates and starts a new stream connection.
     */
    protected void createStream() {
        createStream(null);
    }

    /**
     * Creates and starts a new stream connection with a StreamListener.
     *
     * @param listener the StreamListener to handle incoming events
     */
    protected void createStream(StreamListener listener) {
        this.streamListener = listener;

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
                    log.info("Received 204 response");
                    throw new MetasysCloudConnectorException(RECONNECT_WITH_LAST_KNOWN_EVENT_ID_FAILED + " Please Reconnect, and resubscribe to the stream.");
                } else {
                    log.warn("Unexpected response status: {}", statusCode);
                    throw new MetasysCloudConnectorException(UNKNOWN_STATUS_CODE + ": " + statusCode);
                }
            } catch (MetasysCloudConnectorException e) {
                log.warn("Failure while processing, or connecting to stream: {}", e.getMessage(), e);
                if (closingStreamReason.get() == null) {
                    closingStreamReason.set(PROCESSING_STREAM_ERROR + ": " + e.getMessage());
                }
                throw e;
            } catch (IOException e) {
                //InterruptedException when Metasys Server calls .close()
                //IOException when there is a network error
                String message = "SSE stream experienced network hickup. Need to reconnect with LastKnownEventId: " + lastKnownEventId;
                MetasysCloudConnectorException exception = new MetasysCloudConnectorException(message, e);
                log.debug(message, exception);
                if (closingStreamReason.get() == null) {
                    closingStreamReason.set(NETWORK_INTERRUPTED + ": " + e.getMessage());
                }
                throw exception;
            } catch (InterruptedException e) {
                String message = "SSE stream was closed from Metasys server. Need to reconnect with LastKnownEventId: " + lastKnownEventId;
                MetasysCloudConnectorException exception = new MetasysCloudConnectorException(message, e);
                log.debug(message, exception);
                closingStreamReason.set(METASYS_SERVER_CLOSED_STREAM + ": " + e.getMessage());
                throw exception;
            } catch (Exception e) {
                // Catch any other unexpected exceptions
                String message = "Unexpected error in stream processing: " + e.getMessage();
                log.error(message, e);
                closingStreamReason.set(UNEXPECTED_ERROR + ": " + e.getMessage());
                throw new MetasysCloudConnectorException(message, e);
            } finally {
                // Ensure we always set a reason if none is set already
                if (closingStreamReason.get() == null) {
                    closingStreamReason.set(STREAM_CLOSED_UNEXPECTEDLY);
                    log.warn("Stream closed without a specific reason being set");
                }

                // Notify the StreamListener that the connection is closed
                if (streamListener != null) {
                    ConnectionCloseInfo closeInfo = new ConnectionCloseInfo(
                            mapToConnectionCloseReason(closingStreamReason.get()),
                            null,  // No status code available in this implementation
                            Instant.now()
                    );
                    try {
                        streamListener.onClose(closeInfo);
                    } catch (Exception e) {
                        log.error("Error while notifying StreamListener of connection close", e);
                    }
                }
            }
        };

        closingStreamReason.set(null);
        streamListenerThread = new Thread(streamTask, "StreamListenerPoc");
        // Add a thread shutdown hook to catch any uncaught exceptions
        streamListenerThread.setUncaughtExceptionHandler((t, e) -> {
            log.error("Uncaught exception in stream thread: {}", e.getMessage(), e);
            if (closingStreamReason.get() == null) {
                closingStreamReason.set("UNCAUGHT_EXCEPTION: " + e.getMessage());
            }
        });
        streamListenerThread.start();
    }

    /**
     * Maps the string reason to a ConnectionCloseReason enum value
     */
    private no.cantara.realestate.metasys.cloudconnector.automationserver.stream.MetasysStreamClient.ConnectionCloseReason mapToConnectionCloseReason(String reason) {
        if (reason == null) {
            return no.cantara.realestate.metasys.cloudconnector.automationserver.stream.MetasysStreamClient.ConnectionCloseReason.UNKNOWN;
        }

        if (reason.contains(LOGON_FAILED)) {
            return no.cantara.realestate.metasys.cloudconnector.automationserver.stream.MetasysStreamClient.ConnectionCloseReason.AUTHENTICATION_ERROR;
        } else if (reason.contains(RECONNECT_WITH_LAST_KNOWN_EVENT_ID_FAILED)) {
            return no.cantara.realestate.metasys.cloudconnector.automationserver.stream.MetasysStreamClient.ConnectionCloseReason.STREAM_NOT_RESUMABLE;
        } else if (reason.contains(NETWORK_INTERRUPTED)) {
            return no.cantara.realestate.metasys.cloudconnector.automationserver.stream.MetasysStreamClient.ConnectionCloseReason.NETWORK_ERROR;
        } else if (reason.contains(METASYS_SERVER_CLOSED_STREAM)) {
            return no.cantara.realestate.metasys.cloudconnector.automationserver.stream.MetasysStreamClient.ConnectionCloseReason.SERVER_CLOSED;
        } else {
            return no.cantara.realestate.metasys.cloudconnector.automationserver.stream.MetasysStreamClient.ConnectionCloseReason.UNKNOWN;
        }
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
            log.trace("Received SSE line: {}", line);

            // Empty line indicates the end of an event
            if (line.isEmpty()) {
                if (hasData) {
                    // Complete the current event
                    if (!dataLines.isEmpty()) {
                        currentEvent.setData(String.join("\n", dataLines));
                    }

                    log.debug("Mapped to SSE event: {}", currentEvent);

                    // If we have a StreamListener, call onEvent
                    if (streamListener != null) {
                        try {
                            StreamEvent streamEvent = EventInputMapper.toStreamEvent(currentEvent);
                            if (streamEvent != null && streamEvent instanceof MetasysObservedValueEvent) {
                                streamListener.onEvent(streamEvent);
                            }
                        } catch (Exception e) {
                            log.error("Error in StreamListener.onEvent", e);
                        }
                    }

                    // For backward compatibility, also add to queue if needed
                    try {
                        eventQueue.put(currentEvent);

                        // Set the subscriptionId from the first open event
                        if (currentEvent.getEvent() != null &&
                                currentEvent.getEvent().equals("hello") &&
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
                lastKnownEventId = currentEvent.getId();
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

            // If we have a StreamListener, call onEvent
            if (streamListener != null) {
                try {
                    StreamEvent streamEvent = EventInputMapper.toStreamEvent(currentEvent);
                    if (streamEvent != null && streamEvent instanceof MetasysObservedValueEvent) {
                        streamListener.onEvent(streamEvent);
                    }
                } catch (Exception e) {
                    log.error("Error in StreamListener.onEvent for final event", e);
                }
            }

            // For backward compatibility, also add to queue if needed
            try {
                eventQueue.put(currentEvent);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("Failed to add final event to queue", e);
            }
            log.warn("Stream ended without empty line. Last event: {}", currentEvent);
            if (closingStreamReason.get() == null) {
                closingStreamReason.set(STREAM_ENDED_WITHOUT_EMPTY_LINE + " This could be network-error, or server closing the connection. Please reconnect with LastKnownEventId.");
            }
        } else {
            log.warn("Stream ended because readLine returned null");
            if (closingStreamReason.get() == null) {
                closingStreamReason.set(STREAM_ENDED_WITH_NULL + " This could be network-error, or server closing the connection. Please reconnect with LastKnownEventId.");
            }
        }
    }

    @Override
    public void onEvent(StreamEvent event) {
        log.info("StreamListener received event: {}", event);
        if (event instanceof MetasysObservedValueEvent) {
            MetasysObservedValueEvent observedValueEvent = (MetasysObservedValueEvent) event;
            log.info("StreamListener received observed value event: {}", observedValueEvent);
            ObservedValue observedValue = observedValueEvent.getObservedValue();
            final String metricKey = "metasys_stream_observation_received";
            String metasysObjectId = observedValueEvent.getObservedValue().getId();
            no.cantara.realestate.mappingtable.SensorId sensorId = new no.cantara.realestate.mappingtable.metasys.MetasysSensorId(metasysObjectId, null);
            String adtSensorId = "stubSensorId"; // TODO: Replace with actual sensor ID
            SensorRecObject rec = new SensorRecObject(adtSensorId);
            MappedSensorId mappedId = new no.cantara.realestate.mappingtable.MappedSensorId(sensorId, rec);
            if (observedValue instanceof ObservedValueNumber) {
                ObservationMessage observationMessage = new MetasysObservationMessage((ObservedValueNumber) observedValue, mappedId);
                distributionClient.publish(observationMessage);
            } else if (observedValue instanceof ObservedValueBoolean) {
                ObservedValueNumber observedValueNumber = new ObservedValueNumber(observedValue.getId(), ((ObservedValueBoolean) observedValue).getValue() ? 1 : 0, observedValue.getItemReference());
                ObservationMessage observationMessage = new MetasysObservationMessage(observedValueNumber, mappedId);
                distributionClient.publish(observationMessage);
            } else {
                log.trace("ObservedValue is not a number. Not publishing to distributionClient. ObservedValue: {}", observedValue);
            }
        } else {
            log.warn("StreamListener received unknown event type: {}", event.getClass().getName());
        }
    }

    @Override
    public void onClose(ConnectionCloseInfo closeInfo) {
        log.info("StreamListener connection closed: {}", closeInfo);
    }

    public boolean isStreamOpen() {
        boolean isOpen = streamListenerThread != null && streamListenerThread.isAlive();
        log.debug("Stream is open: {}", isOpen);
        return isOpen;
    }

    public boolean isStreamThreadInterrupted() {
        if (streamListenerThread != null) {
            return streamListenerThread.isInterrupted();
        } else {
            return false;
        }
    }

    public AtomicReference<String> getClosingStreamReason() {
        return closingStreamReason;
    }

    public boolean isReconnectOnError() {
        return reconnectOnError;
    }

    public void setReconnectOnError(boolean reconnectOnError) {
        this.reconnectOnError = reconnectOnError;
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
        log.info("AccessToken: {}, expires at: {}", shortAccessToken, streamPocClient.getUserToken().getExpires());
        try {
            // Use the StreamListener based approach
            streamPocClient.createStream(streamPocClient);
            log.debug("Waiting for events... IsStreamOpen? {}", streamPocClient.isStreamOpen());

            // For backward compatibility demonstration, also check the queue
            ServerSentEvent event = streamPocClient.eventQueue.poll(10, TimeUnit.SECONDS);
            if (event == null) {
                throw new MetasysCloudConnectorException("StreamPocClient returned null events. Closing stream.");
            }
            log.info("First event from queue: {}", event);
            String subscriptionId = streamPocClient.getSubscriptionId();
            log.info("Stream created. SubscriptionId: {}", subscriptionId);
            List<String> metasysObjectIds = List.of("408eb7e4-f63b-5db0-b665-999bfa6ad588");
            if (subscriptionId == null && event.getEvent().equals("hello")) {
                subscriptionId = event.getData();

                log.info("Stream opened. Received subscriptionId: {}", subscriptionId);
            }
            if (subscriptionId != null) {
                subscriptionId = subscriptionId.replace("\"", "");
            }
            streamPocClient.subscribeToStream(subscriptionId, metasysObjectIds);

            do {
                //Check if the stream is still alive
                boolean isAlive = streamPocClient.streamListenerThread.isAlive();
                if (!isAlive) {
                    log.info("Stream is not alive. Closing Stream Reason: " + streamPocClient.closingStreamReason.get());
                    break;
                }
                //Check if access token is still valid
                String newAccessToken = streamPocClient.getUserToken().getAccessToken();
                String newShortAccessToken = shortenedAccessToken(newAccessToken);
                if (!newShortAccessToken.equals(shortAccessToken)) {
                    log.info("AT: {} -> {}, expires: {}", shortAccessToken, newShortAccessToken, streamPocClient.getUserToken().getExpires());
                    accessToken = newShortAccessToken;
                    shortAccessToken = newShortAccessToken;
                } else {
                    log.trace("Access token not changed. Expires: {}", streamPocClient.getUserToken().getExpires());
                }

                // For backward compatibility, also check the queue
                while (!streamPocClient.eventQueue.isEmpty()) {
                    log.trace("Event from queue: {}", streamPocClient.eventQueue.poll());
                }
                Thread.sleep(10000);

            } while (true);
            log.info("Stream closed. StreamPocClient will be closed.");
        } catch (InterruptedException e) {
            log.error("Error in main thread", e);
        } finally {
            log.info("Closing StreamPocClient");
            streamPocClient.close();
        }
    }
}