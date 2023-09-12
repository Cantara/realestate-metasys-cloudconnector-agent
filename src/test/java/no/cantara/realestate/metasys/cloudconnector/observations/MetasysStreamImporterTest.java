package no.cantara.realestate.metasys.cloudconnector.observations;

import no.cantara.config.ApplicationProperties;
import no.cantara.config.testsupport.ApplicationPropertiesTestHelper;
import no.cantara.realestate.distribution.ObservationDistributionClient;
import no.cantara.realestate.mappingtable.repository.MappedIdRepository;
import no.cantara.realestate.metasys.cloudconnector.automationserver.SdClient;
import no.cantara.realestate.metasys.cloudconnector.automationserver.SdLogonFailedException;
import no.cantara.realestate.metasys.cloudconnector.automationserver.UserToken;
import no.cantara.realestate.metasys.cloudconnector.automationserver.stream.MetasysOpenStreamEvent;
import no.cantara.realestate.metasys.cloudconnector.automationserver.stream.MetasysStreamClient;
import no.cantara.realestate.metasys.cloudconnector.distribution.MetricsDistributionClient;
import org.junit.jupiter.api.BeforeAll;
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

    @BeforeAll
    static void beforeAll() {
        ApplicationPropertiesTestHelper.enableMutableSingleton();
        ApplicationProperties.builder().buildAndSetStaticSingleton();
    }

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
    void scheduleRefreshAccessToken() throws SdLogonFailedException, InterruptedException {
        UserToken stubUserToken = new UserToken();
        stubUserToken.setExpires(Instant.now().plusSeconds(90));
        when(sdClient.getUserToken()).thenReturn(stubUserToken);
        metasysStreamImporter.scheduleRefreshToken(1L);
        Thread.sleep(1100);
        //Will run twice. First time immediately due to interval is less than 30 seconds
        verify(sdClient, times(2)).refreshToken();
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
        assertEquals(0, executorService.getActiveCount());
        verify(sdClient, times(0)).refreshToken();
    }

    @Test
    void attemptRescheduleTwice() throws SdLogonFailedException {
        String id = "1";
        String data = "subscriptionId-12345";
        MetasysOpenStreamEvent stubEvent = new MetasysOpenStreamEvent(id, data);
        ScheduledThreadPoolExecutor executorService = (ScheduledThreadPoolExecutor) metasysStreamImporter.getScheduledExecutorService();
        UserToken stubUserToken = new UserToken();
        stubUserToken.setExpires(Instant.now().plusSeconds(180));
        when(sdClient.getUserToken()).thenReturn(stubUserToken);
        //Prepareation - first reschedule
        assertEquals(0, executorService.getQueue().size());
        metasysStreamImporter.openStream();
        assertEquals(1, executorService.getQueue().size());
        //Attempt to refresh again
        UserToken newestUserToken = new UserToken();
        newestUserToken.setExpires(Instant.now().plusSeconds(3600));
        when(sdClient.getUserToken()).thenReturn(newestUserToken);
        id = "2";
        metasysStreamImporter.scheduleRefreshToken(1L);
        assertEquals(1, executorService.getQueue().size());
        verify(sdClient, times(1)).getUserToken();
    }

    @Test
    void openStream() {
        UserToken stubUserToken = new UserToken();
        stubUserToken.setAccessToken("dummyToken");
        stubUserToken.setExpires(Instant.now().plusSeconds(90));
        when(sdClient.getUserToken()).thenReturn(stubUserToken);
        metasysStreamImporter.openStream();
        ScheduledThreadPoolExecutor executorService = (ScheduledThreadPoolExecutor) metasysStreamImporter.getScheduledExecutorService();

        verify(metasysStreamClient, times(1)).openStream(anyString(), anyString(), isNull(), any(MetasysStreamImporter.class));
        //Verify that the refreshTokenTask is scheduled
        assertEquals(1, executorService.getQueue().size());

    }

    @Test
    void onEventPresentValueIsNumber() {

    }
}