package no.cantara.realestate.metasys.cloudconnector.status;

import no.cantara.realestate.metasys.cloudconnector.observations.InMemoryLastImportedObservationTypes;
import no.cantara.realestate.metasys.cloudconnector.observations.LastImportedObservationTypes;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

public class TemporaryHealthResource {
    private static Set<String> trendIds = new HashSet<>();
    public static boolean isSDSimulatorRunning = false;
    public static boolean isInfluxDbSimulatorRunning = false;
    public static LastImportedObservationTypes lastImportedObservationTypes = new InMemoryLastImportedObservationTypes();

    public static String last_import_start = "";
    public static String duratipn = "";
    public static String count = "";
    public static String current_import_start = "";

    protected static List<String> latestErrors = new LinkedList<>();
    private static boolean status;

    public static void setHealthy() {
        TemporaryHealthResource.status = true;
    }

    public static void setUnhealthy() {
        TemporaryHealthResource.status = false;
    }

    public static void addRegisteredError(String errorString) {
        if (latestErrors.size() > 8) {
            latestErrors = new LinkedList<>();
        }
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy hh:mm:ss");
        latestErrors.add(LocalDateTime.now().format(formatter) + " - " + errorString);
    }
}
