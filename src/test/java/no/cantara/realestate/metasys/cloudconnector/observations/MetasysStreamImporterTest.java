package no.cantara.realestate.metasys.cloudconnector.observations;

import no.cantara.config.ApplicationProperties;
import no.cantara.config.testsupport.ApplicationPropertiesTestHelper;
import no.cantara.realestate.distribution.ObservationDistributionClient;
import no.cantara.realestate.mappingtable.MappedSensorId;
import no.cantara.realestate.mappingtable.SensorId;
import no.cantara.realestate.mappingtable.UniqueKey;
import no.cantara.realestate.mappingtable.metasys.MetasysSensorId;
import no.cantara.realestate.mappingtable.metasys.MetasysUniqueKey;
import no.cantara.realestate.mappingtable.rec.SensorRecObject;
import no.cantara.realestate.mappingtable.repository.MappedIdRepository;
import no.cantara.realestate.metasys.cloudconnector.automationserver.SdClient;
import no.cantara.realestate.metasys.cloudconnector.automationserver.SdLogonFailedException;
import no.cantara.realestate.metasys.cloudconnector.automationserver.UserToken;
import no.cantara.realestate.metasys.cloudconnector.automationserver.stream.*;
import no.cantara.realestate.metasys.cloudconnector.distribution.MetricsDistributionClient;
import no.cantara.realestate.observations.ObservationMessage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledThreadPoolExecutor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
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
        String expectedMessageFormat = """
                {
                  "item": {
                    "presentValue": 408,
                    "id": "61abb522-7173-57f6-9dc2-11e89d51ctbd",
                    "itemReference": "tbdw-adx-01:building001-434402-OS01/BACnet IP.E433_101-OU001.R1027.-RY601"
                  },
                  "condition": {
                    "presentValue": {
                      "reliability": "reliabilityEnumSet.reliable",
                      "priority": "writePriorityEnumSet.priorityDefault"
                    }
                  }
                }
                """;
        String metasysObjectId = "61abb522-7173-57f6-9dc2-11e89d51ctbd";
        ObservedValueNumber observedValueNumber = new ObservedValueNumber(metasysObjectId, 408, "tbdw-adx-01:building001-434402-OS01/BACnet IP.E433_101-OU001.R1027.-RY601");
        UniqueKey key = new MetasysUniqueKey(metasysObjectId);
        SensorRecObject rec = mock(SensorRecObject.class);
        SensorId sensorId = new MetasysSensorId(metasysObjectId, "metasysObjectReferene");
        MappedSensorId mappedSensorId = new MappedSensorId(sensorId, rec);
        when(idRepository.find(eq(key))).thenReturn(List.of(mappedSensorId));
        StreamEvent streamEvent = new MetasysObservedValueEvent("1", "object.values.update", "comment", observedValueNumber);
        //Test onEventMethod
        metasysStreamImporter.onEvent(streamEvent);
        MetasysObservationMessage observationMessage = new MetasysObservationMessage(observedValueNumber, mappedSensorId);
        verify(distributionClient, times(1)).publish(eq(observationMessage));
    }

    @Test
    void onEventPresentValueSensorIsMissingFromIdRepository() {
        String metasysObjectId = "61abb522-7173-57f6-9dc2-11e89d51ctbd";
        ObservedValueNumber observedValueNumber = new ObservedValueNumber(metasysObjectId, 408, "tbdw-adx-01:building001-434402-OS01/BACnet IP.E433_101-OU001.R1027.-RY601");
        when(idRepository.find(any(UniqueKey.class))).thenReturn(new ArrayList<>());
        StreamEvent streamEvent = new MetasysObservedValueEvent("1", "object.values.update", "comment", observedValueNumber);
        //Test onEventMethod
        metasysStreamImporter.onEvent(streamEvent);
        verify(distributionClient, times(0)).publish(any(ObservationMessage.class));
    }

    @Test
    void onEventPresentValueIsString() {
        String metasysObjectId = "61abb522-7173-57f6-9dc2-11e89d51ctbd";
        ObservedValueString observedValueString = new ObservedValueString(metasysObjectId, "anyString", "tbdw-adx-01:building001-434402-OS01/BACnet IP.E433_101-OU001.R1027.-RY601");
        UniqueKey key = new MetasysUniqueKey(metasysObjectId);
        SensorRecObject rec = mock(SensorRecObject.class);
        SensorId sensorId = new MetasysSensorId(metasysObjectId, "metasysObjectReferene");
        MappedSensorId mappedSensorId = new MappedSensorId(sensorId, rec);
        when(idRepository.find(eq(key))).thenReturn(List.of(mappedSensorId));
        StreamEvent streamEvent = new MetasysObservedValueEvent("1", "object.values.update", "comment", observedValueString);
        //Test onEventMethod
        metasysStreamImporter.onEvent(streamEvent);
        verify(distributionClient, times(0)).publish(any(ObservationMessage.class));
    }

    @Test
    void onEventPresentValueIsBoolean() {
        String metasysObjectId = "61abb522-7173-57f6-9dc2-11e89d51ctbd";
        ObservedValueBoolean observedValueBoolean = new ObservedValueBoolean(metasysObjectId, false, "tbdw-adx-01:building001-434402-OS01/BACnet IP.E433_101-OU001.R1027.-RY601");
        UniqueKey key = new MetasysUniqueKey(metasysObjectId);
        SensorRecObject rec = mock(SensorRecObject.class);
        SensorId sensorId = new MetasysSensorId(metasysObjectId, "metasysObjectReferene");
        MappedSensorId mappedSensorId = new MappedSensorId(sensorId, rec);
        when(idRepository.find(eq(key))).thenReturn(List.of(mappedSensorId));
        StreamEvent streamEvent = new MetasysObservedValueEvent("1", "object.values.update", "comment", observedValueBoolean);
        //Test onEventMethod
        metasysStreamImporter.onEvent(streamEvent);
        verify(distributionClient, times(1)).publish(any(ObservationMessage.class));
    }

    @Test
    void onEventOpenStreamEvent() {
        Instant expires = Instant.parse("2023-09-12T13:39:46Z");
        UserToken userToken = mock(UserToken.class);
        when(userToken.getExpires()).thenReturn(expires);
        when(sdClient.getUserToken()).thenReturn(userToken);
        assertNotEquals(expires, metasysStreamImporter.getExpires());
        String openStreamData = "";
        StreamEvent openStreamEvent = new MetasysOpenStreamEvent("1223567", openStreamData);
        metasysStreamImporter.onEvent(openStreamEvent);
        //Verify that UserToken is refreshed
        assertEquals(expires, metasysStreamImporter.getExpires());
    }
}