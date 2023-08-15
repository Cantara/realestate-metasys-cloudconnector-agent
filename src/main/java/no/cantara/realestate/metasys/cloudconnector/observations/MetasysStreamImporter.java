package no.cantara.realestate.metasys.cloudconnector.observations;

import no.cantara.config.ApplicationProperties;
import no.cantara.realestate.distribution.ObservationDistributionClient;
import no.cantara.realestate.mappingtable.repository.MappedIdRepository;
import no.cantara.realestate.metasys.cloudconnector.automationserver.SdClient;
import no.cantara.realestate.metasys.cloudconnector.automationserver.stream.MetasysStreamClient;
import no.cantara.realestate.metasys.cloudconnector.automationserver.stream.StreamEvent;
import no.cantara.realestate.metasys.cloudconnector.automationserver.stream.StreamListener;
import no.cantara.realestate.metasys.cloudconnector.distribution.MetricsDistributionClient;
import org.slf4j.Logger;

import static org.slf4j.LoggerFactory.getLogger;

public class MetasysStreamImporter implements StreamListener {
    private static final Logger log = getLogger(MetasysStreamImporter.class);
    private final MetasysStreamClient streamClient;
    private final SdClient sdClient;
    private final MappedIdRepository idRepository;
    private final ObservationDistributionClient distributionClient;
    private final MetricsDistributionClient metricsDistributionClient;

    public MetasysStreamImporter(MetasysStreamClient streamClient, SdClient sdClient, MappedIdRepository idRepository, ObservationDistributionClient distributionClient, MetricsDistributionClient metricsDistributionClient) {
        this.streamClient = streamClient;
        this.sdClient = sdClient;
        this.idRepository = idRepository;
        this.distributionClient = distributionClient;
        this.metricsDistributionClient = metricsDistributionClient;
    }

    @Override
    public void onEvent(StreamEvent event) {
        log.trace("MetasysStreamImporter received:\n {}", event);

    }

    protected void startSubscribing() {
                /*
          private ScheduledImportManager wireScheduledImportManager(SdClient sdClient, ObservationDistributionClient distributionClient, MetricsDistributionClient metricsClient, MappedIdRepository mappedIdRepository) {

        ScheduledImportManager scheduledImportManager = null;

        List<String> importAllFromRealestates = findListOfRealestatesToImportFrom();
        log.info("Importallres: {}", importAllFromRealestates);
        if (importAllFromRealestates != null && importAllFromRealestates.size() > 0) {
            for (String realestate : importAllFromRealestates) {
                MappedIdQuery mappedIdQuery = new MetasysMappedIdQueryBuilder().realEstate(realestate).build();
         */
    }

    public void openStream() {
        String streamUrl = ApplicationProperties.getInstance().get("sd.api.url") + "/stream";
        if (streamClient != null && !streamClient.isStreamOpen()) {
            streamClient.openStream(streamUrl, sdClient.getUserToken().getAccessToken(), this);
        }

    }
}
