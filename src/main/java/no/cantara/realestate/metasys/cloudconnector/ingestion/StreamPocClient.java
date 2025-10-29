package no.cantara.realestate.metasys.cloudconnector.ingestion;

import no.cantara.config.ApplicationProperties;
import no.cantara.realestate.cloudconnector.audit.AuditTrail;
import no.cantara.realestate.cloudconnector.notifications.NotificationService;
import no.cantara.realestate.cloudconnector.sensorid.InMemorySensorIdRepository;
import no.cantara.realestate.cloudconnector.sensorid.SensorIdRepository;
import no.cantara.realestate.metasys.cloudconnector.MetasysCloudConnectorException;
import no.cantara.realestate.metasys.cloudconnector.MetasysCloudconnectorApplicationFactory;
import no.cantara.realestate.metasys.cloudconnector.automationserver.MetasysClient;
import no.cantara.realestate.metasys.cloudconnector.automationserver.stream.*;
import no.cantara.realestate.metasys.cloudconnector.automationserver.streampoc.ServerSentEvent;
import no.cantara.realestate.metasys.cloudconnector.metrics.MetasysMetricsDistributionClient;
import no.cantara.realestate.observations.ConfigMessage;
import no.cantara.realestate.observations.ConfigValue;
import no.cantara.realestate.observations.ObservationListener;
import no.cantara.realestate.rec.RecRepository;
import no.cantara.realestate.security.LogonFailedException;
import no.cantara.realestate.security.UserToken;
import no.cantara.realestate.sensors.SensorId;
import no.cantara.realestate.sensors.metasys.MetasysSensorId;
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
    private static final String METRIC_NAME_STREAMVALUE_RECEIVED = "metasys_streamvalues_received";
    private final MetasysStreamClient metasysStreamClient;
    private final ScheduledExecutorService scheduler;
    private final URI sdUri;
    private final MetasysMetricsDistributionClient metricsClient;
    private final AuditTrail auditTrail;
    private final InMemorySensorIdRepository sensorIdRepository;
    private final RecRepository recRepository;
    private UserToken userToken;
    private String subscriptionId = null;
    private HttpClient httpClient;
    public Thread streamListenerThread;
    public final BlockingQueue<ServerSentEvent> eventQueue = new LinkedBlockingQueue<>();
    private volatile String lastKnownEventId = null;
    public AtomicReference<String> closingStreamReason = new AtomicReference<>(null);
    private boolean reconnectOnError = true;
    private StreamListener streamListener = null;
    private final ObservationListener observationListener;


    public StreamPocClient() {
        this(MetasysStreamClient.getInstance());
    }

    protected StreamPocClient(MetasysStreamClient metasysStreamClient) {
        this.metasysStreamClient = metasysStreamClient;
        scheduler = Executors.newScheduledThreadPool(1);
        findLatestUserToken();
        scheduleTokenRefresh();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(REQUEST_TIMEOUT)
                .build();
        sdUri = metasysStreamClient.getApiUri();
        observationListener = initializeStubObservationListener();
        this.metricsClient = null;
        this.auditTrail = null;
        this.sensorIdRepository = null;
        this.recRepository = null;
    }

    public StreamPocClient(MetasysStreamClient streamClient, SensorIdRepository sensorIdRepository, RecRepository recRepository, ObservationListener observationListener, MetasysMetricsDistributionClient metricsClient, AuditTrail auditTrail) {
        this.metasysStreamClient = streamClient;
        this.scheduler = Executors.newScheduledThreadPool(1);
        findLatestUserToken();
        scheduleTokenRefresh();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(REQUEST_TIMEOUT)
                .build();
        this.sdUri = metasysStreamClient.getApiUri();
        this.observationListener = observationListener;
        this.metricsClient = metricsClient;
        this.auditTrail = auditTrail;
        this.sensorIdRepository = (InMemorySensorIdRepository)sensorIdRepository;
        this.recRepository = recRepository;
    }

    ObservationListener initializeStubObservationListener() {
        return new ObservationListener() {
            private Instant lastMessageObserved = null;
            @Override
            public void observedValue(no.cantara.realestate.observations.ObservedValue observedValue) {
                lastMessageObserved = Instant.now();
            }

            @Override
            public void observedConfigValue(ConfigValue configValue) {
                lastMessageObserved = Instant.now();
            }

            @Override
            public void observedConfigMessage(ConfigMessage configMessage) {
                lastMessageObserved = Instant.now();
            }

            @Override
            public Instant getWhenLastMessageObserved() {
                return lastMessageObserved;
            }

        };
    }

    protected void findLatestUserToken() {
        userToken = metasysStreamClient.getUserToken();
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
        String username = config.get("sd.stream.username");
        String password = config.get("sd.stream.password");
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
            basClient = MetasysClient.getInstance(username, password, apiUri, notificationService);
            log.info("Running with a live Stream.");
        } catch (URISyntaxException e) {
            throw new MetasysCloudConnectorException("Failed to connect Stream Client to URL" + apiUrl, e);
        } catch (LogonFailedException e) {
            throw new MetasysCloudConnectorException("Failed to logon Stream Client. URL used" + apiUrl, e);
        }
        return basClient;
    }

    public static String shortenedAccessToken(String accessToken) {
        return accessToken.length() > 200 ? accessToken.substring(0, 50) + "..." + accessToken.substring(accessToken.length() - 50) : accessToken;
    }

    public void close() {
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

    public void subscribeToStream(String subscriptionId, List<MetasysSensorId> sensorIds) {
        for (MetasysSensorId metasysSensorId : sensorIds) {
            String metasysObjectId = metasysSensorId.getMetasysObjectId();
            log.trace("Subscribe to metasysObjectId: {} subscriptionId: {}", metasysObjectId, subscriptionId);
            String sensorId = metasysSensorId.getTwinId();
            auditTrail.logSubscribed(sensorId, "Subscribe to Stream for MetasysObjectId: " + metasysObjectId);

            subscribeToStreamForMetasysObjectId(subscriptionId, metasysObjectId);
        }
    }

    public void subscribeToStreamForMetasysObjectId(String subscriptionId, String metasysObjectId) {
        try {
            Integer httpStatus = metasysStreamClient.subscribePresentValueChange(subscriptionId, metasysObjectId);
            log.debug("Subscription to metasysObjectId: {} subscriptionId: {}, returned httpStatus: {}", metasysObjectId, subscriptionId, httpStatus);
        } catch (LogonFailedException e) {
            log.warn("Failed to logon to SD system. Could not subscribe to metasysObjectId: {} subscriptionId: {}", metasysObjectId, subscriptionId, e);
            closingStreamReason = new AtomicReference(LOGON_FAILED);
            throw e;
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
    public void createStream(StreamListener listener) {
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

            log.trace("Mapped final SSE event: {}", currentEvent);

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
        log.trace("StreamListener received event: {}", event);
        if (event instanceof MetasysObservedValueEvent) {
            MetasysObservedValueEvent observedValueEvent = (MetasysObservedValueEvent) event;
            log.trace("StreamListener received observed value event: {}", observedValueEvent);
            ObservedValue metasysObservedValue = observedValueEvent.getObservedValue();
            final String metricKey = "metasys_stream_observation_received";
            String metasysObjectId = observedValueEvent.getObservedValue().getId();
            List<SensorId> sensorIds = sensorIdRepository.find(MetasysSensorId.METASYS_OBJECT_ID, metasysObjectId);
            if (sensorIds == null || sensorIds.isEmpty()) {
                log.trace("No SensorId found for MetasysObjectId: {} from stream event {}", metasysObjectId, observedValueEvent);
                return;
            }else {
                String twinId = sensorIds.get(0).getTwinId();
                auditTrail.logObservedStream(twinId, "StreamListener received event for MetasysObjectId: " + metasysObjectId);
                no.cantara.realestate.observations.ObservedValue realestateObservedValue = null;
                for (SensorId sensorId : sensorIds) {
                    if (metasysObservedValue instanceof ObservedValueNumber) {
                        realestateObservedValue = new no.cantara.realestate.observations.ObservedValue(sensorId, (Number) metasysObservedValue.getValue(), Instant.now());
                    } else if (metasysObservedValue instanceof ObservedValueBoolean) {
                        Number value = (Boolean) metasysObservedValue.getValue() ? 1 : 0;
                        realestateObservedValue = new no.cantara.realestate.observations.ObservedValue(sensorId, value, Instant.now());
                    }
                    if (realestateObservedValue != null) {
                        metricsClient.sendValue(METRIC_NAME_STREAMVALUE_RECEIVED, 1);
                        observationListener.observedValue(realestateObservedValue);
                    }
                }
            }
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
        URI apiUrl = URI.create(config.get("sd.api.url"));
        String username = config.get("sd.stream.username");
        String password = config.get("sd.stream.password");
        NotificationService notificationService = new NotificationService() {
            @Override
            public boolean sendWarning(String service, String warningMessage) {
                log.trace("Sending warning message: {}", warningMessage);
                return true;
            }

            @Override
            public boolean sendAlarm(String service, String alarmMessage) {
                log.trace("Sending alarm message: {}", alarmMessage);
                return true;
            }

            @Override
            public boolean clearService(String service) {
                log.trace("Clearing service: {}", service);
                return true;
            }
        };
        MetasysStreamClient streamClient = MetasysStreamClient.getInstance(username, password, apiUrl, notificationService);
        StreamPocClient streamPocClient = new StreamPocClient();


        //Verify that token refresh is working
        String accessToken = streamPocClient.getUserToken().getAccessToken();
        String shortAccessToken = shortenedAccessToken(accessToken);
        log.debug("AccessToken: {}, expires at: {}", shortAccessToken, streamPocClient.getUserToken().getExpires());
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
            List<MetasysSensorId> metasysObjectIds = List.of(new MetasysSensorId("Sensor-twin-poc1","408eb7e4-f63b-5db0-b665-999bfa6ad588"));
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