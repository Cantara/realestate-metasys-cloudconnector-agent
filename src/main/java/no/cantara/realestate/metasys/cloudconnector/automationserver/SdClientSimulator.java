package no.cantara.realestate.metasys.cloudconnector.automationserver;

import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import no.cantara.realestate.automationserver.BasClient;
import no.cantara.realestate.cloudconnector.RealestateCloudconnectorException;
import no.cantara.realestate.cloudconnector.StatusType;
import no.cantara.realestate.observations.PresentValue;
import no.cantara.realestate.security.LogonFailedException;
import no.cantara.realestate.security.UserToken;
import no.cantara.realestate.sensors.SensorId;
import org.slf4j.Logger;

import java.net.URISyntaxException;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.*;

import static no.cantara.realestate.metasys.cloudconnector.automationserver.MetasysTokenManager.REFRESH_TOKEN_BEFORE_EXPIRES_SECONDS;
import static no.cantara.realestate.metasys.cloudconnector.utils.UrlEncoder.urlEncode;
import static org.slf4j.LoggerFactory.getLogger;

public class SdClientSimulator implements BasClient {

    private static final Logger log = getLogger(SdClientSimulator.class);
    private static final long USER_TOKEN_TTL_SECONDS = REFRESH_TOKEN_BEFORE_EXPIRES_SECONDS + 60;
    private final RateLimiter rateLimiter;
    private Map<String, Map<Instant, MetasysTrendSample>> simulatedSDApiData = new ConcurrentHashMap();
    boolean scheduled_simulator_started = true;
    private final int SECONDS_BETWEEN_SCHEDULED_IMPORT_RUNS = 3;

    private Set<String> simulatedTrendIds = new HashSet<>();
    private long numberOfTrendSamplesReceived = 0;
    private Instant whenLastTrendSampleReceived = null;
    private UserToken userToken = null;


    public SdClientSimulator() {
        log.info("SD Rest API Simulator started");
        scheduled_simulator_started = false;
        initializeMapAndStartSimulation();
        RateLimiterConfig config = RateLimiterConfig.custom()
                .limitForPeriod(2)                          // 2 kall per periode
                .limitRefreshPeriod(Duration.ofMillis(200)) // periode på 200 ms
                .timeoutDuration(Duration.ofSeconds(30))    // vent inntil 30 sek for tillatelse
                .build();

        RateLimiterRegistry registry = RateLimiterRegistry.of(config);
        this.rateLimiter = registry.rateLimiter("myDistributedLimiter");
    }

    /*
    Used for testing
     */
    protected SdClientSimulator(RateLimiter rateLimiter) {
        this.rateLimiter = rateLimiter;
    }

    @Override
    public Set<MetasysTrendSample> findTrendSamplesByDate(String trendId, int take, int skip, Instant onAndAfterDateTime) throws URISyntaxException {
        boolean permission = rateLimiter.acquirePermission();  //getPermission(Duration.ofSeconds(10));
        if (!permission) {
            throw new RealestateCloudconnectorException("RateLimit exceeded", StatusType.RETRY_MAY_FIX_ISSUE);
        }
        String prefixedUrlEncodedTrendId = encodeAndPrefix(trendId);
        Instant i = onAndAfterDateTime;
        Set<MetasysTrendSample> trendSamples = new HashSet<>();
        Map<Instant, MetasysTrendSample> trendTimeSamples = simulatedSDApiData.get(prefixedUrlEncodedTrendId);
        int count = 0;
        if (trendTimeSamples != null) {
            for (Instant t : trendTimeSamples.keySet()) {
                if (t.isAfter(i)) {
                    trendSamples.add(trendTimeSamples.get(t));
                    count++;
                    if (count > take) {
                        break;
                    }
                }
            }
        }
        log.info("findTrendSamples returned:{} trendSamples", trendSamples.size());
        return trendSamples;
    }


    @Override
    public void logon() throws LogonFailedException {
        userToken = new MetasysUserToken();
        userToken.setAccessToken(UUID.randomUUID().toString());
        userToken.setExpires(Instant.now().plusSeconds(USER_TOKEN_TTL_SECONDS));
    }

    @Override
    public UserToken refreshToken() throws LogonFailedException {
        if (userToken == null) {
            logon();
        } else {
            userToken.setAccessToken(UUID.randomUUID().toString());
            userToken.setExpires(Instant.now().plusSeconds(USER_TOKEN_TTL_SECONDS));
        }
        return userToken;
    }

    String encodeAndPrefix(String trendId) {
        if (trendId != null) {
            return urlEncode(trendId);
        } else {
            return null;
        }
    }

    private void initializeMapAndStartSimulation() {
        simulatedTrendIds.add("208540b1-ab8a-566a-8a41-8b4cee515baf");
        simulatedTrendIds.add("2a15fea2-a196-566b-9d69-d2abcd86a1d8");
        simulatedTrendIds.add("42061ba5-0f0a-5892-aa28-d33b96e8bed5");
        simulatedTrendIds.add("8413788b-b8b5-5f23-afde-b659740c74b6");
        /*
        log.info("Initializing TrendValue dataset");
        for (int n = 0; n < 500; n++) {
            simulateSensorReadings();
        }
        */
        startScheduledSimulationOfTrendValues();
    }

    private void startScheduledSimulationOfTrendValues() {
        if (!scheduled_simulator_started) {
            scheduled_simulator_started = true;
            ScheduledExecutorService ses = Executors.newScheduledThreadPool(1);
            log.info("Initializing TrendValue simulator");

            Runnable task1 = () -> {
                try {
                    simulateSensorReadings();
                } catch (Exception e) {
                    log.info("Exception trying to run simulated generation of trendvalues");
                }
            };

            // init Delay = 5, repeat the task every 60 second
            ScheduledFuture<?> scheduledFuture = ses.scheduleAtFixedRate(task1, 5, SECONDS_BETWEEN_SCHEDULED_IMPORT_RUNS, TimeUnit.SECONDS);
        }
    }

    private void addTrendIdToSimulation(String trendId) {
        simulatedTrendIds.add(trendId);
    }

    private void simulateSensorReadings() {
//        log.info("starting SD Sensor simulator run");

        for (String trendid : simulatedTrendIds) {
            //  We generate trensValues for 20% for each run
//            log.trace("Running SD Sensor simulator for: {}", trendid);
            Integer randomValue = ThreadLocalRandom.current().nextInt(100);
            //  We generate trensValues for 20% for each run
            if (randomValue < 90) {
                addSimulatedSDTrendSample(trendid);
            }
        }
    }

    @Override
    public Integer subscribePresentValueChange(String subscriptionId, String objectId) throws LogonFailedException {

        boolean permission = rateLimiter.acquirePermission();  //getPermission(Duration.ofSeconds(10));
        if (permission) {
            return presentValueChangeStub(subscriptionId, objectId);
        } else {
            throw new RealestateCloudconnectorException("RateLimit exceeded", StatusType.RETRY_MAY_FIX_ISSUE);
        }
        /*
        Supplier<Integer> restricetedCall = RateLimiter
                .decorateSupplier(rateLimiter, () -> presentValueChangeStub(subscriptionId, objectId));
        try {
            return restricetedCall.get();
        } catch (Exception e) {
            log.error("Error in subscribePresentValueChange: {}", e.getMessage());
            return 500;
        }

         */
    }

    protected static int presentValueChangeStub(String subscriptionId, String objectId) {
        if (subscriptionId == null || objectId == null) {
            return 400;
        }

        if (subscriptionId.isEmpty() || objectId.isEmpty()) {
            return 404;
        }
        return 202;
    }

    private void addSimulatedSDTrendSample(String trendId) {
        MetasysTrendSample ts = new MetasysTrendSample();
        ts.setTrendId(trendId);
        Instant ti = Instant.now();
        ts.setTimestamp(ti.toString());
        Integer randomValue = ThreadLocalRandom.current().nextInt(50);
        ts.setValue(randomValue);
        Map<Instant, MetasysTrendSample> tsMap = simulatedSDApiData.get(trendId);
        if (tsMap == null) {
            tsMap = new ConcurrentHashMap<>();
        }
        simulatedSDApiData.put(trendId.toString(), tsMap);
        whenLastTrendSampleReceived = Instant.ofEpochMilli(System.currentTimeMillis());
        tsMap.put(ti, ts);
//        log.info("   - added trendSample for {} - new size: {}", trendId, tsMap.size());
    }

    @Override
    public boolean isLoggedIn() {
        return true;
    }

    @Override
    public String getName() {
        return "SdClientSimulator";

    }

    @Override
    public boolean isHealthy() {
        return true;
    }

    @Override
    public long getNumberOfTrendSamplesReceived() {
        return numberOfTrendSamplesReceived;
    }


    @Override
    public PresentValue findPresentValue(SensorId sensorId) throws URISyntaxException, LogonFailedException {
        return null;
    }

    @Override
    public UserToken getUserToken() {
        return userToken;
    }

}
