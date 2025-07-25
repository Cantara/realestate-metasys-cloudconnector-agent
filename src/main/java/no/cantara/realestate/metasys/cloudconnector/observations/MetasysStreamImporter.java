package no.cantara.realestate.metasys.cloudconnector.observations;

import no.cantara.config.ApplicationProperties;
import no.cantara.realestate.automationserver.BasClient;
import no.cantara.realestate.cloudconnector.audit.AuditTrail;
import no.cantara.realestate.distribution.ObservationDistributionClient;
import no.cantara.realestate.mappingtable.MappedSensorId;
import no.cantara.realestate.mappingtable.UniqueKey;
import no.cantara.realestate.mappingtable.metasys.MetasysSensorId;
import no.cantara.realestate.mappingtable.metasys.MetasysUniqueKey;
import no.cantara.realestate.mappingtable.repository.MappedIdQuery;
import no.cantara.realestate.mappingtable.repository.MappedIdRepository;
import no.cantara.realestate.metasys.cloudconnector.automationserver.SdLogonFailedException;
import no.cantara.realestate.metasys.cloudconnector.automationserver.stream.*;
import no.cantara.realestate.metasys.cloudconnector.metrics.MetasysMetricsDistributionClient;
import no.cantara.realestate.metasys.cloudconnector.metrics.Metric;
import no.cantara.realestate.observations.ObservationMessage;
import no.cantara.realestate.security.LogonFailedException;
import no.cantara.realestate.security.UserToken;
import org.apache.commons.lang.NotImplementedException;
import org.slf4j.Logger;

import java.net.URISyntaxException;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static no.cantara.realestate.metasys.cloudconnector.automationserver.MetasysClient.truncateAccessToken;
import static no.cantara.realestate.utils.StringUtils.hasValue;
import static org.slf4j.LoggerFactory.getLogger;

public class MetasysStreamImporter implements StreamListener {
    private static final Logger log = getLogger(MetasysStreamImporter.class);
    private static final long REAUTHENTICATE_WITHIN_MILLIS = 30000;
    private final MetasysStreamClient streamClient;
    private final BasClient sdClient;
    private final MappedIdRepository idRepository;
    private final ObservationDistributionClient distributionClient;
    private final MetasysMetricsDistributionClient metricsDistributionClient;
    private final AuditTrail auditTrail;
    private String subscriptionId = null;

    private boolean isHealthy = false;
    private List<String> unhealthyMessages = new ArrayList<>();
    private Instant expires;

    //Scheduler
    private ScheduledExecutorService scheduler;
    private ScheduledFuture<?> scheduledFuture;
    private boolean isSchedulerActive = false;

    //    private ScheduledThreadPoolExecutor scheduledExecutorService;
    private String streamUrl;
    private String lastKnownEventId;
    //    private boolean reAuthorizationIsScheduled;
    private List<MappedIdQuery> idQueries;



    public MetasysStreamImporter(MetasysStreamClient streamClient, BasClient sdClient, MappedIdRepository idRepository, ObservationDistributionClient distributionClient, MetasysMetricsDistributionClient metricsDistributionClient, AuditTrail auditTrail) {
        this.streamClient = streamClient;
        this.sdClient = sdClient;
        this.idRepository = idRepository;
        this.distributionClient = distributionClient;
        this.metricsDistributionClient = metricsDistributionClient;
        this.auditTrail = auditTrail;
//        scheduledExecutorService = new ScheduledThreadPoolExecutor(1);
//        scheduledExecutorService.setRemoveOnCancelPolicy(true);
    }
    /*


    @Override
    public void onEvent(StreamEvent event) {
        if (event != null && event.getId() != null) {
            log.trace("StreamEvent received: {}. Class: {}", event, event.getClass());
        }
        if (event instanceof MetasysObservedValueEvent) {
            log.debug("MetasysStreamImporter received event: {}", event);
            MetasysObservedValueEvent observedValueEvent = (MetasysObservedValueEvent) event;
            setLastKnownEventId(observedValueEvent.getId());
            String metasysObjectId = observedValueEvent.getObservedValue().getId();
            UniqueKey key = new MetasysUniqueKey(metasysObjectId);
            List<MappedSensorId> mappedIds = idRepository.find(key);
            if (mappedIds != null && mappedIds.size() > 0) {
                log.trace("MappedId found for metasysObjectId: {} mappedIds: {}", metasysObjectId, mappedIds.toString());
                for (MappedSensorId mappedId : mappedIds) {
                    ObservedValue observedValue = observedValueEvent.getObservedValue();

                    final String metricKey = "metasys_stream_observation_received";
                    Metric observedMetric = new Metric(metricKey, 1);
                    if (observedValue instanceof ObservedValueNumber) {
                        ObservationMessage observationMessage = new MetasysObservationMessage((ObservedValueNumber) observedValue, mappedId);
                        distributionClient.publish(observationMessage);
                        metricsDistributionClient.sendMetrics(observedMetric);
                        String sensorTwinId = observationMessage.getSensorId();
                        if (sensorTwinId != null) {
                            auditTrail.logObservedStream(sensorTwinId, "Stream observation received for MetasysObjectId: " + metasysObjectId + " with value: " + observedValue.getValue());
                        }
                    } else if (observedValue instanceof ObservedValueBoolean) {
                        ObservedValueNumber observedValueNumber = new ObservedValueNumber(observedValue.getId(), ((ObservedValueBoolean) observedValue).getValue() ? 1 : 0, observedValue.getItemReference());
                        ObservationMessage observationMessage = new MetasysObservationMessage(observedValueNumber, mappedId);
                        distributionClient.publish(observationMessage);
                        metricsDistributionClient.sendMetrics(observedMetric);
                        String sensorTwinId = observationMessage.getSensorId();
                        if (sensorTwinId != null) {
                            auditTrail.logObservedStream(sensorTwinId, "Stream observation received for MetasysObjectId: " + metasysObjectId + " with value: " + observedValue.getValue());
                        }
                        } else {
                        log.trace("ObservedValue is not a number. Not publishing to distributionClient. ObservedValue: {}", observedValue);
                    }
                    //TODO publish metrics metricsDistributionClient.publish(observationMessage);
                }
            } else {
                log.trace("MappedId not found for metasysObjectId: {}", metasysObjectId);
            }
        } else if (event instanceof MetasysOpenStreamEvent) {
            this.subscriptionId = ((MetasysOpenStreamEvent) event).getSubscriptionId();
            log.trace("Current subscriptionId: {}", subscriptionId);
            if (!isSchedulerRunning()) {
                log.info("Start subscribing to stream with subscriptionId: {}", subscriptionId);
                log.debug("Schedule resubscribe.");
                UserToken userToken = sdClient.getUserToken();
                expires = userToken.getExpires();
                Long reSubscribeIntervalInSeconds = Duration.between(Instant.now(), expires).get(ChronoUnit.SECONDS);
                Long testTime = 600L;
                log.warn("Schedule resubscribe should be within: {}. Will test with only 10 minute delay. Resubscribe within: {} seconds", expires, testTime);
                reSubscribeIntervalInSeconds = testTime;
                //Resubscribe is now handled by the onClose method
                // scheduleResubscribe(reSubscribeIntervalInSeconds);
            }
        }
    }

    @Override
    public void onClose(ConnectionCloseInfo closeInfo) {
        //FIXME onClose
        log.warn("Stream closed. CloseInfo: {}", closeInfo);
        if (closeInfo.getReason() == MetasysStreamClient.ConnectionCloseReason.AUTHORIZATION_ERROR) {
            log.info("MetasysStreamClient no longer authorized. Will try to reconnect.");
//            streamClient.reconnectStream(streamUrl, null, null, this);
            resubscribe();
        } else if (closeInfo.getReason() == MetasysStreamClient.ConnectionCloseReason.STREAM_NOT_RESUMABLE) {
            log.info("MetasysStreamClient stream not resumable. Will try to reconnect.");
//            streamClient.reconnectStream(streamUrl, null, null, this);
            resubscribe();
        } else if (closeInfo.getLastStatusCode() == 200) {
            log.warn("Not sure why stream closed. Will try to reconnect.");
//            streamClient.reconnectStream(streamUrl, null, null, this);
            resubscribe();
        } else {
            log.warn("Stream closed. And will not be reconnected. CloseInfo: {}", closeInfo);
        }

    }

    public void resubscribe() {
        if (idQueries != null) {
            log.debug("Resubscribing. {} idQueries: {}", idQueries.size());

            for (MappedIdQuery idQuery : idQueries) {
                List<MappedSensorId> mappedSensorIds = idRepository.find(idQuery);
                log.trace("Subscribing to {} mappedSensorIds for idQuery: {}", mappedSensorIds.size(), idQuery);
                for (MappedSensorId mappedSensorId : mappedSensorIds) {
                    MetasysSensorId sensorId = (MetasysSensorId) mappedSensorId.getSensorId();
                    String metasysObjectId = sensorId.getMetasysObjectId();
                    String subscriptionId = getSubscriptionId();
                    log.trace("Subscribe to metasysObjectId: {} subscriptionId: {}", metasysObjectId, subscriptionId);
                    try {
                        Integer httpStatus = sdClient.subscribePresentValueChange(getSubscriptionId(), metasysObjectId);
                        log.debug("Subscription to metasysObjectId: {} subscriptionId: {}, returned httpStatus: {}", metasysObjectId, subscriptionId, httpStatus);
                    } catch (URISyntaxException e) {
                        log.warn("SD URL is misconfigured. Failed to subscribe to metasysObjectId: {} subscriptionId: {}", metasysObjectId, subscriptionId, e);
                    } catch (LogonFailedException e) {
                        log.warn("Failed to logon to SD system. Could not subscribe to metasysObjectId: {} subscriptionId: {}", metasysObjectId, subscriptionId, e);
                        throw e;
                    }
                }
            }
        } else {
            log.debug("Resubscribing. No idQueries found.");
        }
    }

    public void startSubscribing(List<MappedIdQuery> idQueries) throws SdLogonFailedException {
        log.trace("Start subscribing to MetasysStream");
        this.idQueries = idQueries;
        if (idQueries == null || idQueries.size() == 0) {
            log.warn("No idQueries found for Stream import.");
            return;
        }
        for (MappedIdQuery idQuery : idQueries) {
            List<MappedSensorId> mappedSensorIds = idRepository.find(idQuery);
            log.trace("Subscribing to {} mappedSensorIds for idQuery: {}", mappedSensorIds.size(), idQuery);
            for (MappedSensorId mappedSensorId : mappedSensorIds) {
                MetasysSensorId metasysSensorId = (MetasysSensorId) mappedSensorId.getSensorId();
                String metasysObjectId = metasysSensorId.getMetasysObjectId();
                String subscriptionId = getSubscriptionId();
                log.trace("Subscribe to metasysObjectId: {} subscriptionId: {}", metasysObjectId, subscriptionId);
                try {
                    Integer httpStatus = sdClient.subscribePresentValueChange(getSubscriptionId(), metasysObjectId);
                    log.debug("Subscription to metasysObjectId: {} subscriptionId: {}, returned httpStatus: {}", metasysObjectId, subscriptionId, httpStatus);
                    String sensorId = mappedSensorId.getSensorId().getId();
                    if (sensorId == null && mappedSensorId.getRec() != null) {
                        sensorId = mappedSensorId.getRec().getRecId();
                    }
                    if (hasValue(sensorId)) {
                        auditTrail.logSubscribed(sensorId, "Subscribe to Stream for MetasysObjectId: " + metasysObjectId);
                    } else {
                        log.warn("MappedSensorId has no sensorId. Skipping stream import for MetasysObjectId: {}", metasysObjectId);
                    }
                } catch (URISyntaxException e) {
                    auditTrail.logFailed(metasysSensorId.getId(), "Failed to subscribe to MetasysObjectId: " + metasysObjectId);
                    log.warn("SD URL is misconfigured. Failed to subscribe to metasysObjectId: {} subscriptionId: {}", metasysObjectId, subscriptionId, e);
                } catch (LogonFailedException e) {
                    log.warn("Failed to logon to SD system. Could not subscribe to metasysObjectId: {} subscriptionId: {}", metasysObjectId, subscriptionId, e);
                    throw e;
                }
            }
        }
    }

    public void openStream() {
        log.trace("Open stream to Metasys");
        streamUrl = ApplicationProperties.getInstance().get("sd.api.url") + "stream";
//        if (streamClient != null && !streamClient.isStreamOpen()) {
            UserToken userToken = sdClient.getUserToken();
            if (userToken != null) {
                String accessToken = userToken.getAccessToken();
                try {
//                    streamClient.openStream(streamUrl, accessToken, null, this);
                    log.trace("Metasys stream opened.");
                    isHealthy = true;
                    expires = userToken.getExpires();
                    Long refreshTokenIntervalInSeconds = Duration.between(Instant.now(), expires).get(ChronoUnit.SECONDS);
                    Long testTime = 600L;
                    refreshTokenIntervalInSeconds = testTime;
                    log.warn("Schedule resubscribe should be within: {}. Will test with only 10 minute delay. Resubscribe within: {} seconds", expires, testTime);
                    //TODO need different approach scheduleRefreshToken(refreshTokenIntervalInSeconds);
                } catch (RealEstateStreamException e) {
                    log.warn("Failed to open stream. Will try to reauthorize subscription", e);
                    isHealthy = false;
                }
            } else {
                log.warn("MetasysUserToken is null. Cannot openStream");
                isHealthy = false;
            }
        } else {
            log.debug("Stream already open. Skipping openStream");
        }
    }

    void scheduleResubscribe(Long reSubscribeIntervalInSeconds) {
        log.info("Scheduling resubscribe to stream every {} seconds", reSubscribeIntervalInSeconds);

        scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduledFuture = scheduler.scheduleAtFixedRate(() -> {
            try {
                reauthorizeSubscription();
            } catch (Exception e) {
                log.warn("Error during reauthorization to stream: {}", e.getMessage(), e);
            }
        }, 0, reSubscribeIntervalInSeconds, TimeUnit.SECONDS);
        isSchedulerActive = true;
    }

    boolean isSchedulerRunning() {
        return isSchedulerActive && scheduledFuture != null && !scheduledFuture.isCancelled();
    }

    public void reauthorizeSubscription() {
        log.info("ReAuthorize on thread {}", Thread.currentThread().getName());
        streamUrl = ApplicationProperties.getInstance().get("sd.api.url") + "stream";
        if (streamClient != null) {
            UserToken userToken = sdClient.getUserToken();
            if (userToken != null) {
                String accessToken = userToken.getAccessToken();
                try {
                    String shortenedAccessToken = truncateAccessToken(accessToken);
                    log.trace("Reconnect to Stream with lastKnownEventId: {}, accessToken: {}", getLastKnownEventId(), shortenedAccessToken);
                    streamClient.reconnectStream(streamUrl, accessToken, getLastKnownEventId(), this);
                    isHealthy = true;
                } catch (RealEstateStreamException e) {
                    isHealthy = false;
                    if (e.getAction() != null && e.getAction() == RealEstateStreamException.Action.RECREATE_SUBSCRIPTION_NEEDED) {
                        log.info("Failed to reconnect stream. Will try to open a new subscription", e);
                        streamClient.openStream(streamUrl, null, null, this);
                        try {
                            startSubscribing(idQueries);
                        } catch (SdLogonFailedException ex) {
                            log.warn("Failed to logon to Metasys when trying to reauthorizeSubscription", ex);
                            isHealthy = false;
                        }
                    }
                }
            } else {
                log.warn("MetasysUserToken is null. Cannot reauthorizeSubscription");
                isHealthy = false;
            }
        } else {
            log.debug("Stream already open. Skipping openStream");
        }
    }

     */

    /*
    protected void scheduleRefreshToken(long reSubscribeIntervalInSeconds) {
        log.trace("Schedule refresh every {} seconds", reSubscribeIntervalInSeconds);

        if (!reAuthorizationIsScheduled || scheduledExecutorService.getQueue().size() < 1) {

            Long initialDelay = reSubscribeIntervalInSeconds - 30;
            if (initialDelay < 0) {
                initialDelay = 0L;
            }
            log.info("Refresh first time around {}. Then every {} seconds. ", Instant.now().plusSeconds(initialDelay), reSubscribeIntervalInSeconds);
            Runnable refreshTokenTask = () -> {
                try {
                    log.warn("Stream Subscription will soon expire. Need to refresh accessToken ");
                    UserToken userToken = sdClient.getUserToken();
                    if (userToken == null ||
                            userToken.getExpires() == null ||
                            userToken.getExpires().isBefore(Instant.now().plusSeconds(reSubscribeIntervalInSeconds + 30))) {
                        log.trace("Need to reauthenticate. Current UserToken: {}", userToken);
                        UserToken reAuthentiacateduserToken = sdClient.refreshToken();
                        if (reAuthentiacateduserToken != null && reAuthentiacateduserToken.getExpires() != null) {
                            log.trace("Refresh of accessToken successful. New userToken expires at: {}", reAuthentiacateduserToken.getExpires());
                        }
                    } else {
                         log.trace("No need to reauthenticate. UserToken expires at: {}", userToken.getExpires());
                    }
                } catch (Exception e) {
                    log.warn("Exception trying to refresh MetasysUserToken <hidden>", e);
                }
            };
            scheduledExecutorService.scheduleAtFixedRate(refreshTokenTask, initialDelay, reSubscribeIntervalInSeconds, TimeUnit.SECONDS);
            reAuthorizationIsScheduled = true;
        } else {
            log.trace("Resubscribe already scheduled. Skipping scheduleResubscribeWithin");
        }
    }

     */
/*
FIXME reschedule subscription when user token has expired.
    protected void scheduleResubscribe(long reSubscribeIntervalInSeconds) {
        log.trace("Schedule resubscribe every {} seconds", reSubscribeIntervalInSeconds);

        if (!reAuthorizationIsScheduled || scheduledExecutorService.getQueue().size() < 1) {

            Long initialDelay = reSubscribeIntervalInSeconds - 30;
            if (initialDelay < 0) {
                initialDelay = 0L;
            }
            log.info("Resubscribe first time around {}. Then every {} seconds. ", Instant.now().plusSeconds(initialDelay), reSubscribeIntervalInSeconds);
            Runnable task1 = () -> {
                try {
                    log.warn("Stream Subscription will soon expire. Need to re-subscribe ");
                    UserToken userToken = sdClient.getUserToken();
                    if (userToken != null && userToken.getExpires() != null) {
                        userToken.getExpires().isAfter(Instant.now().plusSeconds(reSubscribeIntervalInSeconds + 30));
                        log.trace("No need to reauthenticate. UserToken expires at: {}", userToken.getExpires());
                    } else {
                        log.trace("Need to reauthenticate. Current UserToken: {}", userToken);
                        sdClient.logon();
                    }
                    UserToken reAuthentiacateduserToken = sdClient.getUserToken();
                    if (reAuthentiacateduserToken != null && reAuthentiacateduserToken.getExpires() != null) {
                        log.info("Resubscribe successful. New userToken expires at: {}", reAuthentiacateduserToken.getExpires());
                    }
                    reauthorizeSubscription();
                } catch (Exception e) {
                    log.warn("Exception trying to reauthenticate with MetasysUserToken <hidden>", e);
                }
            };
            scheduledExecutorService.scheduleAtFixedRate(task1, initialDelay, reSubscribeIntervalInSeconds, TimeUnit.SECONDS);
            reAuthorizationIsScheduled = true;
        } else {
            log.trace("Resubscribe already scheduled. Skipping scheduleResubscribeWithin");
        }
    }

 */

    public String getSubscriptionId() {
        return subscriptionId;
    }

    public String getName() {
        return "MetasysStreamImporter";
    }

    public boolean isHealthy() {
        return isHealthy;
    }

    public void setUnhealthy(String cause) {
        this.isHealthy = false;
        this.unhealthyMessages.add(cause);
    }

    public List<String> getUnhealthyMessages() {
        return unhealthyMessages;
    }

    public Instant getExpires() {
        return expires;
    }

    /*
    protected ScheduledExecutorService getScheduledExecutorService() {
        return scheduledExecutorService;
    }
    */

    public String getStreamUrl() {
        return streamUrl;
    }

    public synchronized String getLastKnownEventId() {
        return lastKnownEventId;
    }

    public synchronized void setLastKnownEventId(String lastKnownEventId) {
        this.lastKnownEventId = lastKnownEventId;
    }

    @Override
    public void onEvent(StreamEvent event) {
        throw new NotImplementedException();
    }

    @Override
    public void onClose(ConnectionCloseInfo closeInfo) {
        throw new NotImplementedException();
    }
}
