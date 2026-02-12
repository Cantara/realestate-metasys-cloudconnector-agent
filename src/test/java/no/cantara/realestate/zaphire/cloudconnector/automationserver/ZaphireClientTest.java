package no.cantara.realestate.zaphire.cloudconnector.automationserver;

import no.cantara.realestate.cloudconnector.notifications.NotificationService;
import no.cantara.realestate.observations.PresentValue;
import no.cantara.realestate.sensors.SensorId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ZaphireClientTest {

    private ZaphireClient zaphireClient;

    @Mock
    private HttpClient httpClientMock;

    @Mock
    private NotificationService notificationServiceMock;

    @Mock
    private HttpResponse<String> httpResponseMock;

    private final URI API_URI = URI.create("http://localhost:1081/");
    private final String DEFAULT_SITE = "test-site";
    private final String VALID_ACCESS_TOKEN = "valid_access_token";
    private final Instant TOKEN_EXPIRY = Instant.now().plusSeconds(3600);

    @BeforeEach
    void setUp() throws Exception {
        // Nullstill singleton
        Field instanceField = ZaphireClient.class.getDeclaredField("instance");
        instanceField.setAccessible(true);
        instanceField.set(null, null);

        // Lag instans via reflection
        Constructor<ZaphireClient> constructor = ZaphireClient.class.getDeclaredConstructor(
                URI.class, String.class, NotificationService.class);
        constructor.setAccessible(true);
        zaphireClient = constructor.newInstance(API_URI, DEFAULT_SITE, notificationServiceMock);

        // Sett mocked HttpClient
        Field httpClientField = ZaphireClient.class.getDeclaredField("httpClient");
        httpClientField.setAccessible(true);
        httpClientField.set(zaphireClient, httpClientMock);

        // Simuler at vi er logget inn
        Field accessTokenField = ZaphireClient.class.getDeclaredField("accessToken");
        accessTokenField.setAccessible(true);
        accessTokenField.set(zaphireClient, VALID_ACCESS_TOKEN);

        Field tokenExpiryTimeField = ZaphireClient.class.getDeclaredField("tokenExpiryTime");
        tokenExpiryTimeField.setAccessible(true);
        tokenExpiryTimeField.set(zaphireClient, TOKEN_EXPIRY);

        // Sett singleton
        instanceField.set(null, zaphireClient);
    }

    @AfterEach
    void tearDown() throws Exception {
        Field instanceField = ZaphireClient.class.getDeclaredField("instance");
        instanceField.setAccessible(true);
        instanceField.set(null, null);
    }

    // --- Basic state tests ---

    @Test
    void getName() {
        assertEquals("ZaphireApiClient", zaphireClient.getName());
    }

    @Test
    void isLoggedIn() {
        assertTrue(zaphireClient.isLoggedIn());
    }

    @Test
    void isLoggedInWhenTokenExpired() throws Exception {
        Field tokenExpiryTimeField = ZaphireClient.class.getDeclaredField("tokenExpiryTime");
        tokenExpiryTimeField.setAccessible(true);
        tokenExpiryTimeField.set(zaphireClient, Instant.now().minusSeconds(60));

        assertFalse(zaphireClient.isLoggedIn());
    }

    @Test
    void isLoggedInWhenNoToken() throws Exception {
        Field accessTokenField = ZaphireClient.class.getDeclaredField("accessToken");
        accessTokenField.setAccessible(true);
        accessTokenField.set(zaphireClient, null);

        assertFalse(zaphireClient.isLoggedIn());
    }

    @Test
    void isHealthyInitially() {
        assertTrue(zaphireClient.isHealthy());
    }

    @Test
    void subscribePresentValueChangeNotSupported() {
        Integer result = zaphireClient.subscribePresentValueChange("sub-1", "obj-1");
        assertEquals(-1, result);
    }

    @Test
    void setAccessToken() {
        Instant expires = Instant.now().plusSeconds(7200);
        zaphireClient.setAccessToken("new-token", expires);

        assertTrue(zaphireClient.isLoggedIn());
        assertNotNull(zaphireClient.getUserToken());
        assertEquals("new-token", zaphireClient.getUserToken().getAccessToken());
    }

    // --- findPresentValue tests ---

    @Test
    void findPresentValueSuccess() throws Exception {
        String tagValueJson = "[\n" +
                "  {\n" +
                "    \"Name\": \"building1/floor2/room201/temperature\",\n" +
                "    \"Units\": \"DegreesCelsius\",\n" +
                "    \"UnitsDisplay\": \"°C\",\n" +
                "    \"Value\": 21.5\n" +
                "  }\n" +
                "]";

        when(httpClientMock.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponseMock);
        when(httpResponseMock.statusCode()).thenReturn(200);
        when(httpResponseMock.body()).thenReturn(tagValueJson);

        SensorId sensorId = new SensorId("building1/floor2/room201/temperature");
        PresentValue result = zaphireClient.findPresentValue(sensorId);

        assertNotNull(result);
        assertEquals("building1/floor2/room201/temperature", result.getSensorId());
        assertEquals(21.5, result.getValue().doubleValue(), 0.01);
        assertTrue(result.getReliable());

        verify(httpClientMock).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }

    @Test
    void findPresentValueWithError() throws Exception {
        String tagValueJson = "[\n" +
                "  {\n" +
                "    \"Name\": \"nonexistent/tag\",\n" +
                "    \"ErrorMessage\": \"Not found.\",\n" +
                "    \"Value\": null\n" +
                "  }\n" +
                "]";

        when(httpClientMock.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponseMock);
        when(httpResponseMock.statusCode()).thenReturn(200);
        when(httpResponseMock.body()).thenReturn(tagValueJson);

        SensorId sensorId = new SensorId("nonexistent/tag");
        PresentValue result = zaphireClient.findPresentValue(sensorId);

        assertNull(result);
    }

    @Test
    void findPresentValueReceive401() throws Exception {
        when(httpClientMock.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponseMock);
        when(httpResponseMock.statusCode()).thenReturn(401);

        SensorId sensorId = new SensorId("building1/floor2/room201/temperature");

        assertThrows(ZaphireApiException.class, () -> {
            zaphireClient.findPresentValue(sensorId);
        });
    }

    // --- findTrendSamplesByDate tests ---

    @Test
    void findTrendSamplesByDateSuccess() throws Exception {
        String historyJson = "[\n" +
                "  {\"Name\": \"building1/floor2/room201/temperature\", \"Timestamp\": \"2024-01-15T08:00:00.0000000+00:00\", \"Value\": 20.1},\n" +
                "  {\"Name\": \"building1/floor2/room201/temperature\", \"Timestamp\": \"2024-01-15T09:00:00.0000000+00:00\", \"Value\": 21.3},\n" +
                "  {\"Name\": \"building1/floor2/room201/temperature\", \"Timestamp\": \"2024-01-15T10:00:00.0000000+00:00\", \"Value\": 22.0}\n" +
                "]";

        when(httpClientMock.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponseMock);
        when(httpResponseMock.statusCode()).thenReturn(200);
        when(httpResponseMock.body()).thenReturn(historyJson);

        Instant from = Instant.now().minus(24, ChronoUnit.HOURS);
        Set<? extends no.cantara.realestate.observations.TrendSample> results =
                zaphireClient.findTrendSamplesByDate("building1/floor2/room201/temperature", -1, -1, from);

        assertNotNull(results);
        assertEquals(3, results.size());
        assertEquals(3, zaphireClient.getNumberOfTrendSamplesReceived());

        for (no.cantara.realestate.observations.TrendSample sample : results) {
            assertNotNull(sample.getTrendId());
            assertNotNull(sample.getValue());
            assertNotNull(sample.getObservedAt());
        }
    }

    @Test
    void findTrendSamplesByDateNullDateTime() {
        // IllegalArgumentException gets wrapped by executeWithTokenHandling
        ZaphireApiException exception = assertThrows(ZaphireApiException.class, () -> {
            zaphireClient.findTrendSamplesByDate("some/tag", -1, -1, null);
        });
        assertTrue(exception.getCause() instanceof IllegalArgumentException);
    }

    @Test
    void findTrendSamplesByDateReceive404() throws Exception {
        when(httpClientMock.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponseMock);
        when(httpResponseMock.statusCode()).thenReturn(404);
        when(httpResponseMock.body()).thenReturn("{\"ErrorMessage\": \"No tag found.\"}");

        Instant from = Instant.now().minus(24, ChronoUnit.HOURS);

        assertThrows(ZaphireApiException.class, () -> {
            zaphireClient.findTrendSamplesByDate("nonexistent/tag", -1, -1, from);
        });
    }

    @Test
    void findTrendSamplesByDateReceive422() throws Exception {
        when(httpClientMock.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponseMock);
        when(httpResponseMock.statusCode()).thenReturn(422);
        when(httpResponseMock.body()).thenReturn("{\"ErrorMessage\": \"Tag doesn't have logging enabled.\"}");

        Instant from = Instant.now().minus(24, ChronoUnit.HOURS);

        ZaphireApiException exception = assertThrows(ZaphireApiException.class, () -> {
            zaphireClient.findTrendSamplesByDate("building1/floor2/room201/setpoint", -1, -1, from);
        });
        assertEquals(422, exception.getStatusCode());
    }

    @Test
    void findTrendSamplesByDateReceive500() throws Exception {
        when(httpClientMock.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponseMock);
        when(httpResponseMock.statusCode()).thenReturn(500);
        when(httpResponseMock.body()).thenReturn("Internal Server Error");

        Instant from = Instant.now().minus(24, ChronoUnit.HOURS);

        assertThrows(ZaphireApiException.class, () -> {
            zaphireClient.findTrendSamplesByDate("building1/floor2/room201/temperature", -1, -1, from);
        });

        assertFalse(zaphireClient.isHealthy());
        assertFalse(zaphireClient.isApiAvailable());
    }

    // --- findTagValues (batch POST) tests ---

    @Test
    void findTagValuesBatchSuccess() throws Exception {
        String batchJson = "[\n" +
                "  {\"Name\": \"building1/floor2/room201/temperature\", \"Units\": \"DegreesCelsius\", \"UnitsDisplay\": \"°C\", \"Value\": 21.5},\n" +
                "  {\"Name\": \"building1/floor2/room201/humidity\", \"Units\": \"PercentRelativeHumidity\", \"UnitsDisplay\": \"%RH\", \"Value\": 45.3}\n" +
                "]";

        when(httpClientMock.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponseMock);
        when(httpResponseMock.statusCode()).thenReturn(200);
        when(httpResponseMock.body()).thenReturn(batchJson);

        ArgumentCaptor<HttpRequest> requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);

        List<ZaphireTagValue> results = zaphireClient.findTagValues(DEFAULT_SITE,
                List.of("building1/floor2/room201/temperature", "building1/floor2/room201/humidity"));

        assertNotNull(results);
        assertEquals(2, results.size());
        assertEquals("building1/floor2/room201/temperature", results.get(0).getName());
        assertEquals(21.5, ((Number) results.get(0).getValue()).doubleValue(), 0.01);
        assertEquals("building1/floor2/room201/humidity", results.get(1).getName());

        verify(httpClientMock).send(requestCaptor.capture(), any(HttpResponse.BodyHandler.class));
        HttpRequest capturedRequest = requestCaptor.getValue();
        assertEquals("POST", capturedRequest.method());
    }

    // --- findHistoryRecords (explicit site) tests ---

    @Test
    void findHistoryRecordsSuccess() throws Exception {
        String historyJson = "[\n" +
                "  {\"Name\": \"building1/energy/meter1\", \"Timestamp\": \"2024-01-15T00:00:00.0000000+00:00\", \"Value\": 12400.0},\n" +
                "  {\"Name\": \"building1/energy/meter1\", \"Timestamp\": \"2024-01-15T06:00:00.0000000+00:00\", \"Value\": 12425.5},\n" +
                "  {\"Name\": \"building1/energy/meter1\", \"Timestamp\": \"2024-01-15T12:00:00.0000000+00:00\", \"Value\": 12487.6}\n" +
                "]";

        when(httpClientMock.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponseMock);
        when(httpResponseMock.statusCode()).thenReturn(200);
        when(httpResponseMock.body()).thenReturn(historyJson);

        ArgumentCaptor<HttpRequest> requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);

        Instant from = Instant.parse("2024-01-15T00:00:00Z");
        Instant to = Instant.parse("2024-01-15T23:59:59Z");
        List<ZaphireTrendSample> results = zaphireClient.findHistoryRecords("other-site", "building1/energy/meter1", from, to);

        assertNotNull(results);
        assertEquals(3, results.size());
        assertEquals(12400.0, results.get(0).getValue().doubleValue(), 0.01);
        assertEquals(12487.6, results.get(2).getValue().doubleValue(), 0.01);

        verify(httpClientMock).send(requestCaptor.capture(), any(HttpResponse.BodyHandler.class));
        HttpRequest capturedRequest = requestCaptor.getValue();
        assertTrue(capturedRequest.uri().toString().contains("other-site"), "Should use explicit site parameter");
    }

    // --- Singleton tests ---

    @Test
    void singletonPattern() {
        ZaphireClient instance1 = ZaphireClient.getInstance();
        ZaphireClient instance2 = ZaphireClient.getInstance();
        assertSame(instance1, instance2);
    }

    @Test
    void singletonNotInitializedThrows() throws Exception {
        // Nullstill singleton
        Field instanceField = ZaphireClient.class.getDeclaredField("instance");
        instanceField.setAccessible(true);
        instanceField.set(null, null);

        assertThrows(IllegalStateException.class, ZaphireClient::getInstance);
    }
}
