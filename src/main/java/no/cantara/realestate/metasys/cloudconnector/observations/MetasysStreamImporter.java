package no.cantara.realestate.metasys.cloudconnector.observations;

import no.cantara.config.ApplicationProperties;
import no.cantara.realestate.distribution.ObservationDistributionClient;
import no.cantara.realestate.mappingtable.MappedSensorId;
import no.cantara.realestate.mappingtable.UniqueKey;
import no.cantara.realestate.mappingtable.metasys.MetasysSensorId;
import no.cantara.realestate.mappingtable.metasys.MetasysUniqueKey;
import no.cantara.realestate.mappingtable.repository.MappedIdQuery;
import no.cantara.realestate.mappingtable.repository.MappedIdRepository;
import no.cantara.realestate.metasys.cloudconnector.automationserver.SdClient;
import no.cantara.realestate.metasys.cloudconnector.automationserver.SdLogonFailedException;
import no.cantara.realestate.metasys.cloudconnector.automationserver.UserToken;
import no.cantara.realestate.metasys.cloudconnector.automationserver.stream.*;
import no.cantara.realestate.metasys.cloudconnector.distribution.MetricsDistributionClient;
import no.cantara.realestate.observations.ObservationMessage;
import org.slf4j.Logger;

import java.net.URISyntaxException;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;

import static org.slf4j.LoggerFactory.getLogger;

public class MetasysStreamImporter implements StreamListener {
    private static final Logger log = getLogger(MetasysStreamImporter.class);
    private final MetasysStreamClient streamClient;
    private final SdClient sdClient;
    private final MappedIdRepository idRepository;
    private final ObservationDistributionClient distributionClient;
    private final MetricsDistributionClient metricsDistributionClient;
    private String subscriptionId = null;

    private boolean isHealthy = false;
    private List<String> unhealthyMessages = new ArrayList<>();
    private Instant expires;

    public MetasysStreamImporter(MetasysStreamClient streamClient, SdClient sdClient, MappedIdRepository idRepository, ObservationDistributionClient distributionClient, MetricsDistributionClient metricsDistributionClient) {
        this.streamClient = streamClient;
        this.sdClient = sdClient;
        this.idRepository = idRepository;
        this.distributionClient = distributionClient;
        this.metricsDistributionClient = metricsDistributionClient;
    }

    //FIXME
    @Override
    public void onEvent(StreamEvent event) {
        if (event != null && event.getId() != null) {
            log.trace("StreamEvent received:\n {}. Class: {}", event, event.getClass());
        }
        if (event instanceof MetasysObservedValueEvent) {
            log.debug("MetasysStreamImporter received:\n {}", event);
            MetasysObservedValueEvent observedValueEvent = (MetasysObservedValueEvent) event;
            //FIXME introcuce test for this scenario
            String metasysObjectId = observedValueEvent.getObservedValue().getId();
            UniqueKey key = new MetasysUniqueKey(metasysObjectId);
            List<MappedSensorId> mappedIds = idRepository.find(key);
            if (mappedIds != null && mappedIds.size() > 0) {
                log.trace("MappedId found for metasysObjectId: {} mappedIds: {}", metasysObjectId, mappedIds.toString());
                for (MappedSensorId mappedId : mappedIds) {
                    ObservedValue observedValue = observedValueEvent.getObservedValue();
                    ObservationMessage observationMessage = new MetasysObservationMessage(observedValue, mappedId);
                    distributionClient.publish(observationMessage);
                    //TODO publish metrics metricsDistributionClient.publish(observationMessage);
                }
            } else {
                    log.trace("MappedId not found for metasysObjectId: {}", metasysObjectId);
            }
        } else if (event instanceof MetasysOpenStreamEvent) {
            this.subscriptionId = ((MetasysOpenStreamEvent) event).getSubscriptionId();
            log.info("Start subscribing to stream with subscriptionId: {}", subscriptionId);
        }
    }

    public void startSubscribing(List<MappedIdQuery> idQueries) throws SdLogonFailedException {
        if (idQueries != null && idQueries.size() > 0) {
            MappedIdQuery idQuery = idQueries.get(0);
            List<MappedSensorId> mappedSensorIds = idRepository.find(idQuery);
            for (MappedSensorId mappedSensorId : mappedSensorIds) {
                MetasysSensorId sensorId = (MetasysSensorId) mappedSensorId.getSensorId();
                String metasysObjectId = sensorId.getMetasysObjectId();
                String subscriptionId = getSubscriptionId();
                log.trace("Subscribe to metasysObjectId: {} subscriptionId: {}", metasysObjectId, subscriptionId);
                try {
                    Integer httpStatus = sdClient.subscribePresentValueChange(getSubscriptionId(), metasysObjectId);
                    log.info("Subscription returned httpStatus: {}", httpStatus);
                } catch (URISyntaxException e) {
                    log.warn("SD URL is missconfigured. Failed to subscribe to metasysObjectId: {} subscriptionId: {}", metasysObjectId, subscriptionId, e);
                } catch (SdLogonFailedException e) {
                    log.warn("Failed to logon to SD system. Could not subscribe to metasysObjectId: {} subscriptionId: {}", metasysObjectId, subscriptionId, e);
                    throw e;
                }
            }
        } else {
            log.warn("No idQueries found. Skipping subscription.");
        }
    }

    public void openStream() {
        String streamUrl = ApplicationProperties.getInstance().get("sd.api.url") + "/stream";
        if (streamClient != null && !streamClient.isStreamOpen()) {
            UserToken userToken = sdClient.getUserToken();
            if (userToken != null) {
                Instant userTokenExpires = userToken.getExpires();
                scheduleResubscribeWithin(userTokenExpires);
                String accessToken = userToken.getAccessToken();
                streamClient.openStream(streamUrl, accessToken, this);
                isHealthy = true;
            } else {
                isHealthy = false;
            }
        } else {
            log.debug("Stream already open. Skipping openStream");
        }
    }

    protected void scheduleResubscribeWithin(Instant userTokenExpires) {
        log.trace("UserToken expires at: {}", userTokenExpires);
        Duration dur = Duration.between(Instant.now(), userTokenExpires);
        long durMillis = dur.get(ChronoUnit.MILLIS);
        Timer timer = new Timer();
        timer.schedule(new java.util.TimerTask() {
            @Override
            public void run() {
                log.warn("Stream Subscription will soon expire. Need to re-subscribe with userToken {}", userTokenExpires);
                try {
                    sdClient.logon();
                    UserToken reauthentiacateduserToken = sdClient.getUserToken();
                    log.warn("Stream Subscription will soon expire. Need to re-subscribe with userToken {}", reauthentiacateduserToken);
                } catch (Exception e) {
                    log.warn("Exception trying to run re-subscribe with userToken {}", userTokenExpires, e);
                }
            }
        }, durMillis);
        /*
        ScheduledExecutorService executorService = Executors.newScheduledThreadPool(1);
        Runnable task1 = () -> {
            try {
                sdClient.logon();
                UserToken reauthentiacateduserToken = sdClient.getUserToken();
                log.warn("Stream Subscription will soon expire. Need to re-subscribe with userToken {}", reauthentiacateduserToken);
            } catch (Exception e) {
                log.info("Exception trying to run simulated generation of trendvalues");
            }
        };

        executorService.schedule(task1, dursec - 30, TimeUnit.SECONDS);

         */
    }

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
}
