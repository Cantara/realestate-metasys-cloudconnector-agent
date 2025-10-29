package no.cantara.realestate.metasys.cloudconnector.sensors;

import no.cantara.realestate.cloudconnector.notifications.NotificationService;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.*;
import java.time.Instant;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.slf4j.LoggerFactory.getLogger;

/**
 * Watches a directory for file changes and triggers a callback after a debounce period.
 * Uses Java NIO WatchService for efficient file system monitoring.
 */
public class SensorFileWatcher implements Runnable {
    private static final Logger log = getLogger(SensorFileWatcher.class);

    private final Path directoryPath;
    private final long debounceSeconds;
    private final SensorFileChangeCallback callback;
    private final NotificationService notificationService;

    private WatchService watchService;
    private Thread watcherThread;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean healthy = new AtomicBoolean(true);
    private final AtomicReference<Instant> lastCheckTime = new AtomicReference<>(Instant.now());
    private final AtomicReference<Instant> lastUpdateTime = new AtomicReference<>();
    private final AtomicReference<String> lastError = new AtomicReference<>();

    private final ScheduledExecutorService debounceExecutor;
    private volatile boolean pendingUpdate = false;
    private final Object debounceLock = new Object();

    public SensorFileWatcher(String directoryPath, long debounceSeconds,
                             SensorFileChangeCallback callback,
                             NotificationService notificationService) {
        this.directoryPath = Paths.get(directoryPath);
        this.debounceSeconds = debounceSeconds;
        this.callback = callback;
        this.notificationService = notificationService;
        this.debounceExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "SensorFileWatcher-Debounce");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * Starts the file watcher in a daemon thread
     */
    public void start() throws IOException {
        if (running.get()) {
            log.warn("SensorFileWatcher is already running");
            return;
        }

        if (!Files.exists(directoryPath)) {
            throw new IOException("Directory does not exist: " + directoryPath);
        }

        if (!Files.isDirectory(directoryPath)) {
            throw new IOException("Path is not a directory: " + directoryPath);
        }

        watchService = FileSystems.getDefault().newWatchService();
        directoryPath.register(watchService,
                StandardWatchEventKinds.ENTRY_CREATE,
                StandardWatchEventKinds.ENTRY_MODIFY);

        running.set(true);
        watcherThread = new Thread(this, "SensorFileWatcher-" + directoryPath.getFileName());
        watcherThread.setDaemon(true);
        watcherThread.start();

        log.info("SensorFileWatcher started for directory: {} with debounce: {} seconds",
                directoryPath, debounceSeconds);
    }

    /**
     * Stops the file watcher gracefully
     */
    public void stop() {
        if (!running.get()) {
            return;
        }

        log.info("Stopping SensorFileWatcher...");
        running.set(false);

        try {
            if (watchService != null) {
                watchService.close();
            }
        } catch (IOException e) {
            log.warn("Error closing WatchService", e);
        }

        debounceExecutor.shutdown();
        try {
            if (!debounceExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                debounceExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            debounceExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }

        if (watcherThread != null) {
            watcherThread.interrupt();
        }

        log.info("SensorFileWatcher stopped");
    }

    @Override
    public void run() {
        log.info("SensorFileWatcher thread started");

        while (running.get()) {
            try {
                WatchKey key = watchService.poll(1, TimeUnit.SECONDS);
                lastCheckTime.set(Instant.now());

                if (key == null) {
                    continue;
                }

                boolean hasRelevantChanges = false;

                for (WatchEvent<?> event : key.pollEvents()) {
                    WatchEvent.Kind<?> kind = event.kind();

                    if (kind == StandardWatchEventKinds.OVERFLOW) {
                        log.warn("WatchService overflow - some events may have been lost");
                        continue;
                    }

                    @SuppressWarnings("unchecked")
                    WatchEvent<Path> pathEvent = (WatchEvent<Path>) event;
                    Path filename = pathEvent.context();

                    // Only process CSV files
                    if (filename.toString().toLowerCase().endsWith(".csv")) {
                        log.debug("Detected change in file: {} ({})", filename, kind);
                        hasRelevantChanges = true;
                    }
                }

                boolean valid = key.reset();
                if (!valid) {
                    log.error("WatchKey no longer valid - directory may have been deleted");
                    healthy.set(false);
                    break;
                }

                if (hasRelevantChanges) {
                    scheduleDebounced();
                }

            } catch (InterruptedException e) {
                log.info("SensorFileWatcher interrupted");
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.error("Error in SensorFileWatcher", e);
                lastError.set(e.getMessage());
                healthy.set(false);
                notifyError("Error in SensorFileWatcher: " + e.getMessage(), e);
                // Continue running despite error
                try {
                    Thread.sleep(5000); // Back off on error
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        log.info("SensorFileWatcher thread stopped");
    }

    /**
     * Schedules a debounced callback execution. If already scheduled, resets the timer.
     */
    private void scheduleDebounced() {
        synchronized (debounceLock) {
            if (pendingUpdate) {
                log.debug("Debounce timer reset - waiting {} seconds", debounceSeconds);
                return; // Already scheduled, the timer will be naturally extended
            }

            pendingUpdate = true;
            log.info("File changes detected - scheduling callback in {} seconds", debounceSeconds);

            debounceExecutor.schedule(() -> {
                synchronized (debounceLock) {
                    pendingUpdate = false;
                }
                executeCallback();
            }, debounceSeconds, TimeUnit.SECONDS);
        }
    }

    /**
     * Executes the callback and handles errors
     */
    private void executeCallback() {
        log.info("Debounce period elapsed - processing file changes");
        try {
            callback.onSensorFilesChanged();
            lastUpdateTime.set(Instant.now());
            healthy.set(true);
            lastError.set(null);
            log.info("Successfully processed sensor file changes");
        } catch (Exception e) {
            log.error("Error processing sensor file changes", e);
            lastError.set(e.getMessage());
            notifyError("Error processing sensor file changes: " + e.getMessage(), e);
            // Don't set unhealthy here - the watcher itself is still working
        }
    }

    /**
     * Sends error notification via Slack
     */
    private void notifyError(String message, Exception e) {
        if (notificationService != null) {
            try {
                notificationService.sendAlarm(null, message);
            } catch (Exception notificationError) {
                log.error("Failed to send notification", notificationError);
            }
        }
    }

    // Health and status methods

    public boolean isHealthy() {
        return healthy.get() && running.get() && watcherThread != null && watcherThread.isAlive();
    }

    public boolean isRunning() {
        return running.get();
    }

    public Instant getLastCheckTime() {
        return lastCheckTime.get();
    }

    public Instant getLastUpdateTime() {
        return lastUpdateTime.get();
    }

    public String getLastError() {
        return lastError.get();
    }

    public String getDirectoryPath() {
        return directoryPath.toString();
    }

    /**
     * Callback interface for sensor file changes
     */
    public interface SensorFileChangeCallback {
        void onSensorFilesChanged() throws Exception;
    }
}