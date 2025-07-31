package no.cantara.realestate.metasys.cloudconnector.ingestion;

import no.cantara.config.ApplicationProperties;
import no.cantara.realestate.automationserver.BasClient;
import no.cantara.realestate.automationserver.TrendNotFoundException;
import no.cantara.realestate.cloudconnector.audit.AuditTrail;
import no.cantara.realestate.metasys.cloudconnector.MetasysCloudConnectorException;
import no.cantara.realestate.metasys.cloudconnector.metrics.MetasysMetricsDistributionClient;
import no.cantara.realestate.metasys.cloudconnector.trends.TrendsLastUpdatedService;
import no.cantara.realestate.observations.ObservationListener;
import no.cantara.realestate.observations.ObservedTrendedValue;
import no.cantara.realestate.observations.TrendSample;
import no.cantara.realestate.plugins.notifications.NotificationListener;
import no.cantara.realestate.security.LogonFailedException;
import no.cantara.realestate.sensors.SensorId;
import no.cantara.realestate.sensors.metasys.MetasysSensorId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.URISyntaxException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MetasysTrendsIngestionServiceTest {

    @Mock
    private ApplicationProperties config;
    @Mock
    private ObservationListener observationListener;
    @Mock
    private NotificationListener notificationListener;
    @Mock
    private BasClient metasysApiClient;
    @Mock
    private TrendsLastUpdatedService trendsLastUpdatedService;
    @Mock
    private AuditTrail auditTrail;
    @Mock
    private MetasysMetricsDistributionClient metricsClient;

    private MetasysTrendsIngestionService ingestionService;

    @BeforeEach
    void setUp() {
        // Setup default config responses
        when(config.get("metrics.name.trendsamplesReceived", "metasys_trendsamples_received"))
                .thenReturn("test_trendsamples_received");
//        when(config.get("sd.api.username", "admin")).thenReturn("testuser");

        ingestionService = new MetasysTrendsIngestionService(
                config,
                observationListener,
                notificationListener,
                metasysApiClient,
                trendsLastUpdatedService,
                auditTrail,
                metricsClient
        );
    }

    /*
    * Verify expected behavior
     */


    @Test
    void ingestTrendsTrendNotFound() throws URISyntaxException {
        // Arrange
        MetasysSensorId sensorId = createTestSensorId("sensor1", "metasysObject1234");
        ingestionService.addSubscription(sensorId);

        when(trendsLastUpdatedService.getLastUpdatedAt(sensorId))
                .thenReturn(Instant.now().minus(1, ChronoUnit.HOURS));
        when(metasysApiClient.findTrendSamplesByDate(anyString(), anyInt(), anyInt(), any(Instant.class)))
                .thenThrow(new TrendNotFoundException("Trend not found"));

        // Act
        ingestionService.ingestTrends();

        // Assert
        verify(observationListener, never()).observedValue(any());
        verify(auditTrail, never()).logObservedTrend(anyString(), anyString());
        verify(auditTrail, atLeastOnce()).logFailed(eq(sensorId.getId()), eq("TrendNotFound"));

        // Verify error handling
        verify(trendsLastUpdatedService).setLastFailedAt(eq(sensorId), any(Instant.class));

        ArgumentCaptor<List<MetasysSensorId>> failedSensorsCaptor = ArgumentCaptor.forClass(List.class);
        verify(trendsLastUpdatedService).persistLastFailed(failedSensorsCaptor.capture());
        assertTrue(failedSensorsCaptor.getValue().contains(sensorId));

        verify(trendsLastUpdatedService).persistLastUpdated(Collections.emptyList());
        ArgumentCaptor<List<MetasysSensorId>> captor = ArgumentCaptor.forClass(List.class);
        verify(trendsLastUpdatedService).persistLastFailed(captor.capture());
        assertTrue(captor.getValue().contains(sensorId));

        assertEquals(0, ingestionService.getNumberOfMessagesImported());
        assertEquals(1, ingestionService.getNumberOfMessagesFailed());
    }

    @Test
    void ingestTrendsOkSingleSensorOneTrendSample() throws URISyntaxException {
        // Arrange
        MetasysSensorId sensorId = createTestSensorId("sensor1", "metasysObject1234");
        ingestionService.addSubscription(sensorId);

        Instant lastObservedAt = Instant.now().minus(30, ChronoUnit.MINUTES);
        when(trendsLastUpdatedService.getLastUpdatedAt(sensorId)).thenReturn(lastObservedAt);

        Set<TrendSample> mockTrendSamples = createMockTrendSamples(1);
        when(metasysApiClient.findTrendSamplesByDate(eq("metasysObject1234"), eq(-1), eq(-1), eq(lastObservedAt)))
                .thenAnswer(invocation -> mockTrendSamples);

        // Act
        ingestionService.ingestTrends();

        // Assert
        verify(trendsLastUpdatedService).readLastUpdated();
        verify(metasysApiClient).findTrendSamplesByDate("metasysObject1234", -1, -1, lastObservedAt);

        // Verify observation listener called once
        ArgumentCaptor<ObservedTrendedValue> observedValueCaptor = ArgumentCaptor.forClass(ObservedTrendedValue.class);
        verify(observationListener, times(1)).observedValue(observedValueCaptor.capture());

        ObservedTrendedValue observedValue = observedValueCaptor.getValue();
        assertEquals("sensor1", observedValue.getSensorId().getId());
        assertEquals(0, observedValue.getValue());

        // Verify audit trail logging
        verify(auditTrail).logObservedTrend(eq("sensor1"), eq("Observed: 1"));

        // Verify metrics
        verify(metricsClient).sendValue("test_trendsamples_received", 1L);

        // Verify last updated timestamp is set
        verify(trendsLastUpdatedService, times(1)).setLastUpdatedAt(eq(sensorId), any(Instant.class));

        // Verify persist calls
        ArgumentCaptor<List<MetasysSensorId>> updatedSensorsCaptor = ArgumentCaptor.forClass(List.class);
        verify(trendsLastUpdatedService).persistLastUpdated(updatedSensorsCaptor.capture());
        assertEquals(1, updatedSensorsCaptor.getValue().size());
        assertTrue(updatedSensorsCaptor.getValue().contains(sensorId));

        verify(trendsLastUpdatedService).persistLastFailed(Collections.emptyList());

        // Verify service state
        assertTrue(ingestionService.isHealthy());
        assertEquals(1, ingestionService.getNumberOfMessagesImported());
        assertEquals(0, ingestionService.getNumberOfMessagesFailed());
        assertNotNull(ingestionService.getWhenLastMessageImported());
    }

    @Test
    void ingestTrendsOkTwoSensorsMultipleTrendSamples() throws URISyntaxException {
        // Arrange
        MetasysSensorId sensor1 = createTestSensorId("sensor1", "metasysObject1234");
        MetasysSensorId sensor2 = createTestSensorId("sensor2", "trend456");
        ingestionService.addSubscription(sensor1);
        ingestionService.addSubscription(sensor2);

        Instant lastObservedAt1 = Instant.now().minus(1, ChronoUnit.HOURS);
        Instant lastObservedAt2 = Instant.now().minus(45, ChronoUnit.MINUTES);
        when(trendsLastUpdatedService.getLastUpdatedAt(sensor1)).thenReturn(lastObservedAt1);
        when(trendsLastUpdatedService.getLastUpdatedAt(sensor2)).thenReturn(lastObservedAt2);

        Set<TrendSample> mockTrendSamples1 = createMockTrendSamples(3);
        Set<TrendSample> mockTrendSamples2 = createMockTrendSamples(2);

        when(metasysApiClient.findTrendSamplesByDate(eq("metasysObject1234"), eq(-1), eq(-1), eq(lastObservedAt1)))
                .thenAnswer(invocation -> mockTrendSamples1);
        when(metasysApiClient.findTrendSamplesByDate(eq("trend456"), eq(-1), eq(-1), eq(lastObservedAt2)))
                .thenAnswer(invocation -> mockTrendSamples2);

        // Act
        ingestionService.ingestTrends();

        // Assert
        verify(trendsLastUpdatedService, times(2)).getLastUpdatedAt(any(MetasysSensorId.class));
        verify(metasysApiClient).findTrendSamplesByDate("metasysObject1234", -1, -1, lastObservedAt1);
        verify(metasysApiClient).findTrendSamplesByDate("trend456", -1, -1, lastObservedAt2);

        // Verify observation listener called 5 times total (3 + 2)
        ArgumentCaptor<ObservedTrendedValue> observedValueCaptor = ArgumentCaptor.forClass(ObservedTrendedValue.class);
        verify(observationListener, times(5)).observedValue(observedValueCaptor.capture());

        List<ObservedTrendedValue> observedValues = observedValueCaptor.getAllValues();
        assertEquals(5, observedValues.size());

        // Verify audit trail logging for both sensors
        verify(auditTrail).logObservedTrend(eq("sensor1"), eq("Observed: 3"));
        verify(auditTrail).logObservedTrend(eq("sensor2"), eq("Observed: 2"));

        // Verify metrics sent twice
        verify(metricsClient).sendValue("test_trendsamples_received", 3L);
        verify(metricsClient).sendValue("test_trendsamples_received", 2L);

        // Verify last updated timestamp is set for each sample (3 + 2 = 5 times)
        verify(trendsLastUpdatedService, times(3)).setLastUpdatedAt(eq(sensor1), any(Instant.class));
        verify(trendsLastUpdatedService, times(2)).setLastUpdatedAt(eq(sensor2), any(Instant.class));

        // Verify persist calls
        ArgumentCaptor<List<MetasysSensorId>> updatedSensorsCaptor = ArgumentCaptor.forClass(List.class);
        verify(trendsLastUpdatedService).persistLastUpdated(updatedSensorsCaptor.capture());
        assertEquals(2, updatedSensorsCaptor.getValue().size());
        assertTrue(updatedSensorsCaptor.getValue().contains(sensor1));
        assertTrue(updatedSensorsCaptor.getValue().contains(sensor2));

        verify(trendsLastUpdatedService).persistLastFailed(Collections.emptyList());

        // Verify service state
        assertTrue(ingestionService.isHealthy());
        assertEquals(5, ingestionService.getNumberOfMessagesImported());
        assertEquals(0, ingestionService.getNumberOfMessagesFailed());
        assertNotNull(ingestionService.getWhenLastMessageImported());
    }

    /*
    * Test error handling and edge cases
     */
    @Test
    void constructor_withValidParams_createsService() {
        assertNotNull(ingestionService);
        assertEquals("MetasysTrendsIngestionService", ingestionService.getName());
        assertEquals(0, ingestionService.getSubscriptionsCount());
        assertFalse(ingestionService.isInitialized());
        assertEquals(0, ingestionService.getNumberOfMessagesImported());
        assertEquals(0, ingestionService.getNumberOfMessagesFailed());
    }

    @Test
    void constructorWithNullConfig() {
        MetasysCloudConnectorException exception = assertThrows(
                MetasysCloudConnectorException.class,
                () -> new MetasysTrendsIngestionService(null, observationListener, notificationListener,
                        metasysApiClient, trendsLastUpdatedService, auditTrail, metricsClient)
        );
        assertTrue(exception.getMessage().contains("One or more of the parameters are null"));
    }

    @Test
    void constructor_withNullObservationListener_throwsException() {
        MetasysCloudConnectorException exception = assertThrows(
                MetasysCloudConnectorException.class,
                () -> new MetasysTrendsIngestionService(config, null, notificationListener,
                        metasysApiClient, trendsLastUpdatedService, auditTrail, metricsClient)
        );
        assertTrue(exception.getMessage().contains("One or more of the parameters are null"));
    }


    @Test
    void ingestTrends_withNoSensors_completesSuccessfully() {
        // Act
        ingestionService.ingestTrends();

        // Assert
        verify(trendsLastUpdatedService).readLastUpdated();
        verify(trendsLastUpdatedService).persistLastUpdated(Collections.emptyList());
        verify(trendsLastUpdatedService).persistLastFailed(Collections.emptyList());
    }

    @Test
    void ingestTrends_withValidSensorAndTrendSamples_processesSuccessfully() throws URISyntaxException {
        // Arrange
        MetasysSensorId sensorId = createTestSensorId("sensor1", "metasysObject1234");
        ingestionService.addSubscription(sensorId);

        Instant lastObservedAt = Instant.now().minus(1, ChronoUnit.HOURS);
        when(trendsLastUpdatedService.getLastUpdatedAt(sensorId)).thenReturn(lastObservedAt);

        Set<TrendSample> mockTrendSamples = createMockTrendSamples(3);
        when(metasysApiClient.findTrendSamplesByDate(eq("metasysObject1234"), eq(-1), eq(-1), eq(lastObservedAt)))
                .thenAnswer(invocation ->mockTrendSamples);

        // Act
        ingestionService.ingestTrends();

        // Assert
        verify(trendsLastUpdatedService).readLastUpdated();
        verify(metasysApiClient).findTrendSamplesByDate("metasysObject1234", -1, -1, lastObservedAt);

        // Verify observation listener called for each sample
        ArgumentCaptor<ObservedTrendedValue> observedValueCaptor = ArgumentCaptor.forClass(ObservedTrendedValue.class);
        verify(observationListener, times(3)).observedValue(observedValueCaptor.capture());

        List<ObservedTrendedValue> observedValues = observedValueCaptor.getAllValues();
        assertEquals(3, observedValues.size());

        // Verify audit trail logging
        verify(auditTrail).logObservedTrend(eq("sensor1"), eq("Observed: 3"));

        // Verify metrics
        verify(metricsClient).sendValue("test_trendsamples_received", 3L);

        // Verify last updated timestamp is set for each sample
        verify(trendsLastUpdatedService, times(3)).setLastUpdatedAt(eq(sensorId), any(Instant.class));

        // Verify persist calls
        ArgumentCaptor<List<MetasysSensorId>> updatedSensorsCaptor = ArgumentCaptor.forClass(List.class);
        verify(trendsLastUpdatedService).persistLastUpdated(updatedSensorsCaptor.capture());
        assertTrue(updatedSensorsCaptor.getValue().contains(sensorId));

        verify(trendsLastUpdatedService).persistLastFailed(Collections.emptyList());

        // Verify service health and counters
        assertTrue(ingestionService.isHealthy());
        assertEquals(3, ingestionService.getNumberOfMessagesImported());
        assertEquals(0, ingestionService.getNumberOfMessagesFailed());
        assertNotNull(ingestionService.getWhenLastMessageImported());
    }

    @Test
    void ingestTrends_withNoLastObservedAt_usesDefaultTime() throws URISyntaxException {
        // Arrange
        MetasysSensorId sensorId = createTestSensorId("sensor1", "metasysObject1234");
        ingestionService.addSubscription(sensorId);

        when(trendsLastUpdatedService.getLastUpdatedAt(sensorId)).thenReturn(null);

        Set<TrendSample> mockTrendSamples = createMockTrendSamples(1);
        when(metasysApiClient.findTrendSamplesByDate(eq("metasysObject1234"), eq(-1), eq(-1), any(Instant.class)))
                .thenAnswer(invocation -> mockTrendSamples);

        // Act
        ingestionService.ingestTrends();

        // Assert
        ArgumentCaptor<Instant> timeCaptor = ArgumentCaptor.forClass(Instant.class);
        verify(metasysApiClient).findTrendSamplesByDate(eq("metasysObject1234"), eq(-1), eq(-1), timeCaptor.capture());

        // Should use default time (2 hours ago)
        Instant capturedTime = timeCaptor.getValue();
        Instant expectedTime = Instant.now().minus(2, ChronoUnit.HOURS);
        assertTrue(Math.abs(capturedTime.toEpochMilli() - expectedTime.toEpochMilli()) < 1000); // Within 1 second
    }

    @Test
    void ingestTrends_withEmptyTrendSamples_handlesGracefully() throws URISyntaxException {
        // Arrange
        MetasysSensorId sensorId = createTestSensorId("sensor1", "metasysObject1234");
        ingestionService.addSubscription(sensorId);

        when(trendsLastUpdatedService.getLastUpdatedAt(sensorId))
                .thenReturn(Instant.now().minus(1, ChronoUnit.HOURS));
        when(metasysApiClient.findTrendSamplesByDate(anyString(), anyInt(), anyInt(), any(Instant.class)))
                .thenReturn(Collections.emptySet());

        // Act
        ingestionService.ingestTrends();

        // Assert
        verify(observationListener, never()).observedValue(any());
        verify(auditTrail, never()).logObservedTrend(anyString(), anyString());
        verify(metricsClient, never()).sendValue(anyString(), anyLong());

        assertEquals(0, ingestionService.getNumberOfMessagesImported());
        assertEquals(0, ingestionService.getNumberOfMessagesFailed());
        assertTrue(ingestionService.isHealthy()); // Should still be healthy
    }


    @Test
    void ingestTrendsLogonFailed() throws URISyntaxException {
        // Arrange
        MetasysSensorId sensorId = createTestSensorId("sensor1", "metasysObject1234");
        ingestionService.addSubscription(sensorId);

        when(trendsLastUpdatedService.getLastUpdatedAt(sensorId))
                .thenReturn(Instant.now().minus(1, ChronoUnit.HOURS));
        when(metasysApiClient.findTrendSamplesByDate(anyString(), anyInt(), anyInt(), any(Instant.class)))
                .thenThrow(new LogonFailedException("Authentication failed"));

        // Act & Assert
        try {
            ingestionService.ingestTrends();
        } catch (MetasysCloudConnectorException exception) {
            assertTrue(exception.getMessage().contains("Could not ingest trends"));
            assertTrue(exception.getMessage().contains("Logon failed"));
        }

        // Verify error handling
        verify(trendsLastUpdatedService).setLastFailedAt(eq(sensorId), any(Instant.class));
        assertEquals(1, ingestionService.getNumberOfMessagesFailed());
    }

    @Test
    void ingestTrendsThrowsUriSyntaxException() throws URISyntaxException {
        // Arrange
        MetasysSensorId sensorId = createTestSensorId("sensor1", "metasysObject1234");
        ingestionService.addSubscription(sensorId);

        when(trendsLastUpdatedService.getLastUpdatedAt(sensorId))
                .thenReturn(Instant.now().minus(1, ChronoUnit.HOURS));
        when(metasysApiClient.findTrendSamplesByDate(anyString(), anyInt(), anyInt(), any(Instant.class)))
                .thenThrow(new URISyntaxException("invalid URI", "Bad URI"));

        // Act
        ingestionService.ingestTrends();

        // Assert
        verify(trendsLastUpdatedService).setLastFailedAt(eq(sensorId), any(Instant.class));

        ArgumentCaptor<List<MetasysSensorId>> failedSensorsCaptor = ArgumentCaptor.forClass(List.class);
        verify(trendsLastUpdatedService).persistLastFailed(failedSensorsCaptor.capture());
        assertTrue(failedSensorsCaptor.getValue().contains(sensorId));

        assertEquals(0, ingestionService.getNumberOfMessagesImported());
        assertEquals(1, ingestionService.getNumberOfMessagesFailed());
    }

    @Test
    void ingestTrends_withMetasysCloudConnectorException_handlesGracefully() throws URISyntaxException {
        // Arrange
        MetasysSensorId sensorId = createTestSensorId("sensor1", "metasysObject1234");
        ingestionService.addSubscription(sensorId);

        when(trendsLastUpdatedService.getLastUpdatedAt(sensorId))
                .thenReturn(Instant.now().minus(1, ChronoUnit.HOURS));
        when(metasysApiClient.findTrendSamplesByDate(anyString(), anyInt(), anyInt(), any(Instant.class)))
                .thenThrow(new MetasysCloudConnectorException("API Error"));

        // Act
        ingestionService.ingestTrends();

        // Assert
        verify(trendsLastUpdatedService).setLastFailedAt(eq(sensorId), any(Instant.class));
        assertEquals(1, ingestionService.getNumberOfMessagesFailed());
    }

    @Test
    void ingestTrends_withGenericException_handlesGracefully() throws URISyntaxException {
        // Arrange
        MetasysSensorId sensorId = createTestSensorId("sensor1", "metasysObject1234");
        ingestionService.addSubscription(sensorId);

        when(trendsLastUpdatedService.getLastUpdatedAt(sensorId))
                .thenReturn(Instant.now().minus(1, ChronoUnit.HOURS));
        when(metasysApiClient.findTrendSamplesByDate(anyString(), anyInt(), anyInt(), any(Instant.class)))
                .thenThrow(new RuntimeException("Unexpected error"));

        // Act
        ingestionService.ingestTrends();

        // Assert
        verify(trendsLastUpdatedService).setLastFailedAt(eq(sensorId), any(Instant.class));
        assertEquals(1, ingestionService.getNumberOfMessagesFailed());
    }

    @Test
    void ingestTrends_withNullMetasysObjectId_skipsProcessing() throws URISyntaxException {
        // Arrange
        MetasysSensorId sensorId = createTestSensorId("sensor1", null); // null metasys object ID
        ingestionService.addSubscription(sensorId);

        // Act
        ingestionService.ingestTrends();

        // Assert
        verify(metasysApiClient, never()).findTrendSamplesByDate(anyString(), anyInt(), anyInt(), any(Instant.class));
        verify(observationListener, never()).observedValue(any());

        assertEquals(0, ingestionService.getNumberOfMessagesImported());
        assertEquals(0, ingestionService.getNumberOfMessagesFailed());
    }

    @Test
    void ingestTrends_withEmptyMetasysObjectId_skipsProcessing() throws URISyntaxException {
        // Arrange
        MetasysSensorId sensorId = createTestSensorId("sensor1", ""); // empty metasys object ID
        ingestionService.addSubscription(sensorId);

        // Act
        ingestionService.ingestTrends();

        // Assert
        verify(metasysApiClient, never()).findTrendSamplesByDate(anyString(), anyInt(), anyInt(), any(Instant.class));
        verify(observationListener, never()).observedValue(any());

        assertEquals(0, ingestionService.getNumberOfMessagesImported());
        assertEquals(0, ingestionService.getNumberOfMessagesFailed());
    }

    @Test
    void ingestTrends_withTrendsLastUpdatedServiceThrowingNPE_throwsException() {
        // Arrange
        doThrow(new NullPointerException("Service is null"))
                .when(trendsLastUpdatedService).readLastUpdated();

        // Act & Assert
        MetasysCloudConnectorException exception = assertThrows(
                MetasysCloudConnectorException.class,
                () -> ingestionService.ingestTrends()
        );

        assertTrue(exception.getMessage().contains("Failed to read last updated"));
        assertFalse(ingestionService.isHealthy());
    }

    @Test
    void addSubscription_withValidSensor_addsSensor() {
        // Arrange
        MetasysSensorId sensorId = createTestSensorId("sensor1", "metasysObject1234");

        // Act
        ingestionService.addSubscription(sensorId);

        // Assert
        assertEquals(1, ingestionService.getSubscriptionsCount());
    }

    @Test
    void addSubscriptions_withValidSensorList_addsSensors() {
        // Arrange
        List<SensorId> sensors = Arrays.asList(
                createTestSensorId("sensor1", "metasysObject1234"),
                createTestSensorId("sensor2", "trend456")
        );

        // Act
        ingestionService.addSubscriptions(sensors);

        // Assert
        assertEquals(2, ingestionService.getSubscriptionsCount());
    }

    @Test
    void removeSubscription_withExistingSensor_removesSensor() {
        // Arrange
        MetasysSensorId sensorId = createTestSensorId("sensor1", "metasysObject1234");
        ingestionService.addSubscription(sensorId);

        // Act
        ingestionService.removeSubscription(sensorId);

        // Assert
        assertEquals(0, ingestionService.getSubscriptionsCount());
    }

    // Helper methods
    private MetasysSensorId createTestSensorId(String sensorId, String metasysObjectId) {
        MetasysSensorId mockSensorId = new MetasysSensorId(sensorId, metasysObjectId);
        return mockSensorId;
    }

    private Set<TrendSample> createMockTrendSamples(int count) {
        Set<TrendSample> samples = new LinkedHashSet<>();
        for (int i = 0; i < count; i++) {
            TrendSample sample = new TrendSample();
            sample.setValue(i);
            sample.setObservedAt(Instant.now().minus(i, ChronoUnit.MINUTES));
            samples.add(sample);
        }
        return samples;
    }
}