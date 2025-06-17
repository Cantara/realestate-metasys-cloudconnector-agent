package no.cantara.realestate.metasys.cloudconnector.observations;

import no.cantara.config.ApplicationProperties;
import no.cantara.config.testsupport.ApplicationPropertiesTestHelper;
import no.cantara.realestate.automationserver.BasClient;
import no.cantara.realestate.cloudconnector.audit.AuditTrail;
import no.cantara.realestate.distribution.ObservationDistributionClient;
import no.cantara.realestate.metasys.cloudconnector.automationserver.MetasysTokenManager;
import no.cantara.realestate.metasys.cloudconnector.automationserver.MetasysUserToken;
import no.cantara.realestate.metasys.cloudconnector.automationserver.stream.*;
import no.cantara.realestate.metasys.cloudconnector.metrics.MetasysMetricsDistributionClient;
import no.cantara.realestate.observations.ObservationMessage;
import no.cantara.realestate.rec.RecRepository;
import no.cantara.realestate.rec.RecTags;
import no.cantara.realestate.rec.SensorRecObject;
import no.cantara.realestate.sensors.metasys.MetasysSensorSystemId;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.Mockito.*;

@Disabled
class MetasysStreamImporterTest {

    private MetasysStreamImporter metasysStreamImporter;
    private MetasysStreamClient metasysStreamClient;
    private BasClient sdClient;
    private RecRepository recRepository;
    private ObservationDistributionClient distributionClient;
    private MetasysMetricsDistributionClient metricsDistributionClient;
    private MetasysTokenManager metasysTokenManager;
    private AuditTrail auditTrail;

    @BeforeAll
    static void beforeAll() {
        ApplicationPropertiesTestHelper.enableMutableSingleton();
        ApplicationProperties.builder().buildAndSetStaticSingleton();
    }

    @BeforeEach
    void setUp() {
        metasysStreamClient = mock(MetasysStreamClient.class);
        sdClient = mock(BasClient.class);
        recRepository = mock(RecRepository.class);
        distributionClient = mock(ObservationDistributionClient.class);
        metricsDistributionClient = mock(MetasysMetricsDistributionClient.class);
        metasysTokenManager = mock(MetasysTokenManager.class);
        auditTrail = mock(AuditTrail.class);
        MetasysUserToken metasysUserToken = new MetasysUserToken();
        metasysUserToken.setExpires(Instant.now().plusSeconds(60));
        when(metasysTokenManager.getCurrentToken()).thenReturn(metasysUserToken);

//        metasysStreamImporter = new MetasysStreamImporter(metasysStreamClient, sdClient, recRepository, distributionClient, metricsDistributionClient,auditTrail);
    }

    /*
    @Test
    void scheduleRefreshAccessToken() throws SdLogonFailedException, InterruptedException {
        MetasysUserToken stubUserToken = new MetasysUserToken();
        stubUserToken.setExpires(Instant.now().plusSeconds(20));
        when(sdClient.getUserToken()).thenReturn(stubUserToken);
        metasysStreamImporter.scheduleRefreshToken(1L);
        Thread.sleep(1100);
        //Will run twice. First time immediately due to interval is less than 30 seconds
        verify(sdClient, times(2)).refreshToken();
    }
    */

    /*
    @Test
    void verifyRescheduleIsEnabled() throws SdLogonFailedException {
        String id = "1";
        String data = "subscriptionId-12345";
        MetasysOpenStreamEvent stubEvent = new MetasysOpenStreamEvent(id, data);
        ScheduledThreadPoolExecutor executorService = (ScheduledThreadPoolExecutor) metasysStreamImporter.getScheduledExecutorService();
        MetasysUserToken stubUserToken = new MetasysUserToken();
        stubUserToken.setExpires(Instant.now().plusSeconds(20));
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
    */

    /*
    @Test
    void attemptRescheduleTwice() throws SdLogonFailedException {
        String id = "1";
        String data = "subscriptionId-12345";
        MetasysOpenStreamEvent stubEvent = new MetasysOpenStreamEvent(id, data);
        ScheduledThreadPoolExecutor executorService = (ScheduledThreadPoolExecutor) metasysStreamImporter.getScheduledExecutorService();
        MetasysUserToken stubUserToken = new MetasysUserToken();
        stubUserToken.setExpires(Instant.now().plusSeconds(180));
        when(sdClient.getUserToken()).thenReturn(stubUserToken);
        //Prepareation - first reschedule
        assertEquals(0, executorService.getQueue().size());
        metasysStreamImporter.openStream();
        assertEquals(1, executorService.getQueue().size());
        //Attempt to refresh again
        MetasysUserToken newestUserToken = new MetasysUserToken();
        newestUserToken.setExpires(Instant.now().plusSeconds(3600));
        when(sdClient.getUserToken()).thenReturn(newestUserToken);
        id = "2";
        metasysStreamImporter.scheduleRefreshToken(1L);
        assertEquals(1, executorService.getQueue().size());
        verify(sdClient, times(1)).getUserToken();
    }
    */

    @Test
    void openStream() {
        MetasysUserToken stubUserToken = new MetasysUserToken();
        stubUserToken.setAccessToken("dummyToken");
        stubUserToken.setExpires(Instant.now().plusSeconds(90));
        when(sdClient.getUserToken()).thenReturn(stubUserToken);
//        metasysStreamImporter.openStream();

//        verify(metasysStreamClient, times(1)).openStream(anyString(), anyString(), isNull(), any(MetasysStreamImporter.class));

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
        RecTags recTags = new RecTags("Sensor-Twin-Id");
        MetasysSensorSystemId sensorSystemId = new MetasysSensorSystemId(metasysObjectId);

        when(recRepository.findBySensorSystemId(eq(sensorSystemId))).thenReturn(List.of(recTags));
        StreamEvent streamEvent = new MetasysObservedValueEvent("1", "object.values.update", "comment", observedValueNumber);
        //Test onEventMethod
        metasysStreamImporter.onEvent(streamEvent);
        MetasysObservationMessage observationMessage = new MetasysObservationMessage(observedValueNumber, recTags);
        verify(distributionClient, times(1)).publish(eq(observationMessage));
    }

    @Test
    void onEventPresentValueSensorIsMissingFromIdRepository() {
        /*
        String metasysObjectId = "61abb522-7173-57f6-9dc2-11e89d51ctbd";
        ObservedValueNumber observedValueNumber = new ObservedValueNumber(metasysObjectId, 408, "tbdw-adx-01:building001-434402-OS01/BACnet IP.E433_101-OU001.R1027.-RY601");
        when(idRepository.find(any(UniqueKey.class))).thenReturn(new ArrayList<>());
        StreamEvent streamEvent = new MetasysObservedValueEvent("1", "object.values.update", "comment", observedValueNumber);
        //Test onEventMethod
        metasysStreamImporter.onEvent(streamEvent);
        verify(distributionClient, times(0)).publish(any(ObservationMessage.class));

         */
    }

    @Test
    void onEventPresentValueIsString() {
        /*
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

         */
    }

    @Test
    void onEventPresentValueIsBoolean() {
        /*
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

         */
    }

    @Test
    void onEventOpenStreamEvent() {
        Instant expires = Instant.parse("2023-09-12T13:39:46Z");
        MetasysUserToken userToken = mock(MetasysUserToken.class);
        when(userToken.getExpires()).thenReturn(expires);
        when(sdClient.getUserToken()).thenReturn(userToken);
//        when(sdClient.getUserToken()).thenReturn(userToken);
        assertNotEquals(expires, metasysStreamImporter.getExpires());
        String openStreamData = "";
        StreamEvent openStreamEvent = new MetasysOpenStreamEvent("1223567", openStreamData);
        metasysStreamImporter.onEvent(openStreamEvent);
        //Verify that MetasysUserToken is refreshed
        assertEquals(expires, metasysStreamImporter.getExpires());
    }
}