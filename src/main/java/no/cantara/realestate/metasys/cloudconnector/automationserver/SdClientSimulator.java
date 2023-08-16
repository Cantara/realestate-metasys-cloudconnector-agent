package no.cantara.realestate.metasys.cloudconnector.automationserver;

import org.slf4j.Logger;

import java.net.URISyntaxException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;

import static no.cantara.realestate.metasys.cloudconnector.utils.UrlEncoder.urlEncode;
import static org.slf4j.LoggerFactory.getLogger;

public class SdClientSimulator implements SdClient {

    private static final Logger log = getLogger(SdClientSimulator.class);
    private Map<String, Map<Instant, MetasysTrendSample>> simulatedSDApiData = new ConcurrentHashMap();
    boolean scheduled_simulator_started = true;
    private final int SECONDS_BETWEEN_SCHEDULED_IMPORT_RUNS = 3;

    private Set<String> simulatedTrendIds = new HashSet<>();
    private long numberOfTrendSamplesReceived = 0;


    public SdClientSimulator() {
        log.info("SD Rest API Simulator started");
        scheduled_simulator_started = false;
        initializeMapAndStartSimulation();
    }

    @Override
    public Set<MetasysTrendSample> findTrendSamples(String bearerToken, String trendId) throws URISyntaxException {
        String prefixedUrlEncodedTrendId = encodeAndPrefix(trendId);
        Instant i = Instant.now().minus(1, ChronoUnit.DAYS);
        Set<MetasysTrendSample> trendSamples = new HashSet<>();
        Map<Instant, MetasysTrendSample> trendTimeSamples = simulatedSDApiData.get(prefixedUrlEncodedTrendId);
        for (Instant t : trendTimeSamples.keySet()) {
            if (t.isAfter(i)) {
                trendSamples.add(trendTimeSamples.get(t));
                addNumberOfTrendSamplesReceived();
            }
        }
        log.info("findTrendSamples returned:{} trendSamples", trendSamples.size());
        return trendSamples;
    }

    synchronized void addNumberOfTrendSamplesReceived() {
        if (numberOfTrendSamplesReceived < Long.MAX_VALUE) {
            numberOfTrendSamplesReceived ++;
        } else {
            numberOfTrendSamplesReceived = 1;
        }
    }

    @Override
    public Set<MetasysTrendSample> findTrendSamples(String trendId, int take, int skip) throws URISyntaxException {
        String prefixedUrlEncodedTrendId = encodeAndPrefix(trendId);
        Instant i = Instant.now().minus(1, ChronoUnit.DAYS);
        Set<MetasysTrendSample> trendSamples = new HashSet<>();
        Map<Instant, MetasysTrendSample> trendTimeSamples = simulatedSDApiData.get(prefixedUrlEncodedTrendId);
        int count = 0;
        for (Instant t : trendTimeSamples.keySet()) {
            if (t.isAfter(i)) {
                trendSamples.add(trendTimeSamples.get(t));
                count++;
                if (count > take) {
                    break;
                }
            }
        }
        log.info("findTrendSamples returned:{} trendSamples", trendSamples.size());

        return trendSamples;
    }

    @Override
    public Set<MetasysTrendSample> findTrendSamplesByDate(String trendId, int take, int skip, Instant onAndAfterDateTime) throws URISyntaxException {
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
    public void logon() throws SdLogonFailedException {
        return;
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
    public Integer subscribePresentValueChange(String subscriptionId, String objectId) throws URISyntaxException, SdLogonFailedException {
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
        ts.setValueDeep(randomValue);
        Map<Instant, MetasysTrendSample> tsMap = simulatedSDApiData.get(trendId);
        if (tsMap == null) {
            tsMap = new ConcurrentHashMap<>();
        }
        simulatedSDApiData.put(trendId.toString(), tsMap);
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
    public UserToken getUserToken() {
        return new UserToken();
    }
}
