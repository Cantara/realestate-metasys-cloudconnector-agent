package no.cantara.realestate.metasys.cloudconnector.observations;

import java.time.Instant;

public interface TrendLogsImporter {

    //Lifecycle events
    void startup();
    void flush();
    void close();

    //Functionality
    void importAll();
    void importAllAfterDateTime(Instant fromDateTime);
    void importFromDay0(String observationType);
    void importAfterDateTime(String observationType, Instant fromDateTime);


}
