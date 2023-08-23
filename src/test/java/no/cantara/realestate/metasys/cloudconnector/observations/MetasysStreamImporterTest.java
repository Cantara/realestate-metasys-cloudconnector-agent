package no.cantara.realestate.metasys.cloudconnector.observations;

import no.cantara.realestate.distribution.ObservationDistributionClient;
import no.cantara.realestate.mappingtable.repository.MappedIdRepository;
import no.cantara.realestate.metasys.cloudconnector.automationserver.SdClient;
import no.cantara.realestate.metasys.cloudconnector.automationserver.SdLogonFailedException;
import no.cantara.realestate.metasys.cloudconnector.automationserver.UserToken;
import no.cantara.realestate.metasys.cloudconnector.automationserver.stream.MetasysOpenStreamEvent;
import no.cantara.realestate.metasys.cloudconnector.automationserver.stream.MetasysStreamClient;
import no.cantara.realestate.metasys.cloudconnector.distribution.MetricsDistributionClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.concurrent.ScheduledThreadPoolExecutor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class MetasysStreamImporterTest {

    private MetasysStreamImporter metasysStreamImporter;
    private MetasysStreamClient metasysStreamClient;
    private SdClient sdClient;
    private MappedIdRepository idRepository;
    private ObservationDistributionClient distributionClient;
    private MetricsDistributionClient metricsDistributionClient;

    @BeforeEach
    void setUp() {
        metasysStreamClient = mock(MetasysStreamClient.class);
        sdClient = mock(SdClient.class);
        idRepository = mock(MappedIdRepository.class);
        distributionClient = mock(ObservationDistributionClient.class);
        metricsDistributionClient = mock(MetricsDistributionClient.class);
        metasysStreamImporter = new MetasysStreamImporter(metasysStreamClient, sdClient, idRepository, distributionClient, metricsDistributionClient);
    }

    @Test
    void scheduleResubscribeWithin() throws SdLogonFailedException, InterruptedException {
        UserToken stubUserToken = new UserToken();
        stubUserToken.setExpires(Instant.now().plusSeconds(90));
        when(sdClient.getUserToken()).thenReturn(stubUserToken);
        metasysStreamImporter.scheduleResubscribeWithin(Instant.now().plusSeconds(1));
        Thread.sleep(1100);
        verify(sdClient, times(1)).logon();
        verify(sdClient, times(1)).getUserToken();
    }

    @Test
    void verifyRescheduleIsEnabled() throws SdLogonFailedException {
        String id = "1";
        String data = "subscriptionId-12345";
        MetasysOpenStreamEvent stubEvent = new MetasysOpenStreamEvent(id, data);
        ScheduledThreadPoolExecutor executorService = (ScheduledThreadPoolExecutor) metasysStreamImporter.getScheduledExecutorService();
        UserToken stubUserToken = new UserToken();
        stubUserToken.setExpires(Instant.now().plusSeconds(90));
        when(sdClient.getUserToken()).thenReturn(stubUserToken);
        //Find list of not executed tasks
//        List<Runnable> notExecutedTasks = executorService.shutdownNow();
        assertEquals(0, executorService.getActiveCount());
        //The test
        metasysStreamImporter.onEvent(stubEvent);
        //Verify that the task is scheduled
        assertEquals(1, executorService.getActiveCount());
        verify(sdClient, times(1)).getUserToken();
    }
}