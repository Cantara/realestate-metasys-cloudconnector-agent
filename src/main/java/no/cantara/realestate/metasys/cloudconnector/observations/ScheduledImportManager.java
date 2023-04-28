package no.cantara.realestate.metasys.cloudconnector.observations;

import no.cantara.config.ApplicationProperties;
import org.slf4j.Logger;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static org.slf4j.LoggerFactory.getLogger;

public class ScheduledImportManager {
    private static final Logger log = getLogger(ScheduledImportManager.class);
    private final int DEFAULT_SECONDS_BETWEEN_SCHEDULED_IMPORT_RUNS = 60*10;
    public static final String IMPORT_SCHEDULE_MINUTES_KEY = "import_schedule_minutes";
    private static boolean scheduled_import_started = false;
    private static boolean scheduled_import_running = false;
    private final int SECONDS_BETWEEN_SCHEDULED_IMPORT_RUNS;

    private final List<TrendLogsImporter> trendLogsImporters;

    public ScheduledImportManager(TrendLogsImporter trendLogsImporter, ApplicationProperties config) {
        trendLogsImporters = new ArrayList<>();
        trendLogsImporters.add(trendLogsImporter);
        Integer scheduleMinutes = findScheduledMinutes(config);
        if (scheduleMinutes != null) {
            SECONDS_BETWEEN_SCHEDULED_IMPORT_RUNS = scheduleMinutes * 60;
        } else {
            SECONDS_BETWEEN_SCHEDULED_IMPORT_RUNS = DEFAULT_SECONDS_BETWEEN_SCHEDULED_IMPORT_RUNS;
        }
    }

    public void addTrendLogsImporter(TrendLogsImporter trendLogsImporter) {
        trendLogsImporters.add(trendLogsImporter);
    }

    private Integer findScheduledMinutes(ApplicationProperties config) {
        Integer scheduleMinutes = null;
        String scheduleMinutesValue = config.get(IMPORT_SCHEDULE_MINUTES_KEY);
        if (scheduleMinutesValue != null) {
            try {
                scheduleMinutes = Integer.valueOf(scheduleMinutesValue);
            } catch (NumberFormatException nfe) {
                log.debug("Failed to create scheduledMinutes from [{}]", scheduleMinutesValue);
            }
        }
        return scheduleMinutes;
    }

    public void startScheduledImportOfTrendIds() {
        if (!scheduled_import_started) {
            log.info("Starting ScheduledImportManager");

            scheduled_import_started = true;
            ScheduledExecutorService ses = Executors.newScheduledThreadPool(1);

            Runnable task1 = () -> {
                if (scheduled_import_running == false) {
                    log.info("Running an new import round.");
                    for (TrendLogsImporter trendLogsImporter : trendLogsImporters) {
                        try {
                            scheduled_import_running = true;
                            trendLogsImporter.startup();
                            log.info("Running...importTrends for {} ", trendLogsImporter);
                            trendLogsImporter.importAll();
                            log.info("Imported all TrendIds for {}. Flushing", trendLogsImporter);
                            trendLogsImporter.close();
                            log.info("Flushed. Looking for next importer to run.");
                            scheduled_import_running = false;
                        } catch (Exception e) {

                            log.info("Exception trying to run scheduled imports of trendIds for {}. Reason: {}", trendLogsImporter, e.getMessage());
                            scheduled_import_running = false;
                        }
                    }
                    log.info("Now waiting {} seconds for next scheduled run at: {}", SECONDS_BETWEEN_SCHEDULED_IMPORT_RUNS, Instant.now().plusSeconds(SECONDS_BETWEEN_SCHEDULED_IMPORT_RUNS));
                } else {
                    log.info("Last round of imports has not finished yet. ");
                }
            };

            // init Delay = 5, repeat the task every 60 second
            ScheduledFuture<?> scheduledFuture = ses.scheduleAtFixedRate(task1, 5, SECONDS_BETWEEN_SCHEDULED_IMPORT_RUNS, TimeUnit.SECONDS);
        } else {
            log.info("ScheduledImportManager is is already started");
        }
    }
}
