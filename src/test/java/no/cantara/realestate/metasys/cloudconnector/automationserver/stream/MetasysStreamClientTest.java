package no.cantara.realestate.metasys.cloudconnector.automationserver.stream;

import no.cantara.realestate.automationserver.PresentValueNotFoundException;
import no.cantara.realestate.cloudconnector.RealestateCloudconnectorException;
import no.cantara.realestate.cloudconnector.notifications.NotificationService;
import no.cantara.realestate.metasys.cloudconnector.MetasysCloudConnectorException;
import no.cantara.realestate.metasys.cloudconnector.automationserver.MetasysApiException;
import org.apache.commons.lang.NotImplementedException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockserver.integration.ClientAndServer;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MetasysStreamClientTest {

    private MetasysStreamClient metasysStreamClient;
    private static ClientAndServer mockServer;

    @Mock
    private HttpClient httpClientMock;

    @Mock
    private NotificationService notificationServiceMock;

    @Mock
    private HttpResponse<String> httpResponseMock;

    private final String USERNAME = "testuser";
    private final String PASSWORD = "testpassword";
    private final URI API_URI = URI.create("https://metasys-test-api.com/api/");

    private final String VALID_ACCESS_TOKEN = "valid_access_token";
    private final String NEW_ACCESS_TOKEN = "new_access_token";
    private final Instant TOKEN_EXPIRY = Instant.now().plusSeconds(3600);
    private String subscriptionId;

    @BeforeEach
    void setUp() throws Exception {
        // Nullstiller singleton-instansen
        Field instanceField = MetasysStreamClient.class.getDeclaredField("instance");
        instanceField.setAccessible(true);
        instanceField.set(null, null);

        // Lager en direkte instans av MetasysClient ved å bruke konstruktøren via reflection
        // istedenfor å kalle getInstance() som vil gjøre et faktisk HTTP-kall
        Constructor<MetasysStreamClient> constructor = MetasysStreamClient.class.getDeclaredConstructor(
                String.class, String.class, URI.class, NotificationService.class);
        constructor.setAccessible(true);
        metasysStreamClient = constructor.newInstance(USERNAME, PASSWORD, API_URI, notificationServiceMock);

        // Setter opp mocked HttpClient
        Field httpClientField = MetasysStreamClient.class.getDeclaredField("httpClient");
        httpClientField.setAccessible(true);
        httpClientField.set(metasysStreamClient, httpClientMock);

        // Setter feltverdier direkte for å simulere at vi allerede er logget inn
        Field accessTokenField = MetasysStreamClient.class.getDeclaredField("accessToken");
        accessTokenField.setAccessible(true);
        accessTokenField.set(metasysStreamClient, VALID_ACCESS_TOKEN);

        Field tokenExpiryTimeField = MetasysStreamClient.class.getDeclaredField("tokenExpiryTime");
        tokenExpiryTimeField.setAccessible(true);
        tokenExpiryTimeField.set(metasysStreamClient, TOKEN_EXPIRY);

        // Setter instansen i singleton-feltet
        instanceField.set(null, metasysStreamClient);

        subscriptionId = "subscription123";
    }

    @AfterEach
    void tearDown() throws Exception {
        // Rydder opp singleton-instansen etter test
        Field instanceField = MetasysStreamClient.class.getDeclaredField("instance");
        instanceField.setAccessible(true);
        instanceField.set(null, null);
    }


    /*
     * Validate expected behaviour
     */
    @Test
    void refreshTokenSilentlyGet() throws Exception {
        // Arrange
        when(httpClientMock.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponseMock);
        when(httpResponseMock.statusCode()).thenReturn(200);
        when(httpResponseMock.body()).thenReturn("{\"accessToken\":\"new_token\",\"expires\":\"2023-12-31T23:59:59Z\"}");

        ArgumentCaptor<HttpRequest> requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);

        // Act
        metasysStreamClient.refreshTokenSilently();

        // Assert
        verify(httpClientMock).send(requestCaptor.capture(), any(HttpResponse.BodyHandler.class));
        HttpRequest capturedRequest = requestCaptor.getValue();
        assertEquals("GET", capturedRequest.method(), "Expected HTTP method to be GET");
    }

    @Test
    void subscribePresentValueChangeSubscriptionNotFound() throws Exception {
        // Arrange
        String subscriptionId = "subscription123";
        String objectId = "object456";

        when(httpClientMock.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponseMock);
        when(httpResponseMock.statusCode()).thenReturn(404);
        when(httpResponseMock.body()).thenReturn("Not Found");

        // Act
        PresentValueNotFoundException exception = assertThrows(PresentValueNotFoundException.class, () -> {
            metasysStreamClient.subscribePresentValueChange(subscriptionId, objectId);
        });

        // Assert
        assertEquals(objectId, exception.getSensorId());
        verify(httpClientMock).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }

    @Test
    void subscribePresentValueChange401() throws Exception {
        // Arrange
        String objectId = "test-object-123";
        Instant onAndAfterDateTime = Instant.now().minus(1, java.time.temporal.ChronoUnit.HOURS);

        // Mock first call returns 401, second call (after token refresh) returns 200
        when(httpClientMock.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponseMock)
                .thenReturn(httpResponseMock); // For token refresh
        when(httpResponseMock.statusCode())
                .thenReturn(401); // First call fails with 401
//                .thenReturn(200); // Token refresh succeeds
        when(httpResponseMock.body())
                .thenReturn(""); // Empty body for 401 response
//                .thenReturn("{\"accessToken\":\"new_token\",\"expires\":\"2023-12-31T23:59:59Z\"}"); // Token refresh response

        // Act & Assert
        // Should trigger token refresh and retry, but we're not mocking the retry call properly
        // so it will likely still fail - this tests the 401 handling mechanism
        assertThrows(MetasysApiException.class, () -> {
            metasysStreamClient.subscribePresentValueChange(subscriptionId, objectId);
        });

//        assertFalse(metasysStreamClient.isHealthy());
//        assertFalse(metasysStreamClient.isApiAvailable());

        // Verify that HTTP request was made
        verify(httpClientMock, atLeastOnce()).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }

    @Test
    void subscribePresentValueChangeReceive403() throws Exception {
        // Arrange
        String objectId = "test-object-123";
        Instant onAndAfterDateTime = Instant.now().minus(1, java.time.temporal.ChronoUnit.HOURS);

        when(httpClientMock.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponseMock);
        when(httpResponseMock.statusCode()).thenReturn(403);
        when(httpResponseMock.body()).thenReturn("Forbidden");

        // Act & Assert
        MetasysApiException exception = assertThrows(MetasysApiException.class, () -> {
            metasysStreamClient.subscribePresentValueChange(subscriptionId, objectId);
        });

        // Verify exception details
        assertTrue(exception.getMessage().contains("Unauthorized access"));
//        assertFalse(metasysStreamClient.isHealthy());
        //assertFalse(metasysStreamClient.isApiAvailable());

        // Verify that HTTP request was made
        verify(httpClientMock, atLeastOnce()).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }

    @Test
    void subscribePresentValueChangeReceive404() throws Exception {
        // Arrange
        String objectId = "test-object-123";

        when(httpClientMock.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponseMock);
        when(httpResponseMock.statusCode()).thenReturn(404);
        when(httpResponseMock.body()).thenReturn("Not Found");

        // Act & Assert
        PresentValueNotFoundException exception = assertThrows(PresentValueNotFoundException.class, () -> {
            metasysStreamClient.subscribePresentValueChange(subscriptionId, objectId);
        });

        // Verify exception details
        assertTrue(exception.getMessage().contains("PresentValueSubscription is not available"));
        assertEquals(objectId, exception.getSensorId());

        assertTrue(metasysStreamClient.isHealthy());
        assertTrue(metasysStreamClient.isApiAvailable());

        // Verify that HTTP request was made
        verify(httpClientMock, atLeastOnce()).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }


    @Test
    void subscribePresentValueChangeReceive500() throws Exception {
        String subscriptionId = "subscription123";
        // Arrange
        String objectId = "test-object-123";

        when(httpClientMock.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponseMock);
        when(httpResponseMock.statusCode()).thenReturn(500);
        when(httpResponseMock.body()).thenReturn("Internal Server Error");

        // Act & Assert
        MetasysCloudConnectorException exception = assertThrows(MetasysCloudConnectorException.class, () -> {
            metasysStreamClient.subscribePresentValueChange(subscriptionId, objectId);
        });

        // Verify exception details
        assertTrue(exception.getMessage().contains("MetasysAPI Server error"));

//        assertFalse(metasysStreamClient.isHealthy());
//        assertFalse(metasysStreamClient.isApiAvailable());

        // Verify that HTTP request was made
        verify(httpClientMock, atLeastOnce()).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }

    /*
     * Test error and corner cases
     */
    @Test
    void testNotImplementedMethods() throws URISyntaxException {
        MetasysStreamClient client = MetasysStreamClient.getInstance();

        assertThrows(RealestateCloudconnectorException.class, client::refreshToken);
        assertThrows(RealestateCloudconnectorException.class, client::logon);
        assertThrows(NotImplementedException.class, () -> {
            client.findTrendSamplesByDate(null, 0, 0, Instant.now());
        });
        assertThrows(NotImplementedException.class, () -> {
            client.findPresentValue(null);
        });
    }

    @Test
    void subscribePresentValueChange_withValidObjectId_returnsStatusCode200() throws Exception {
        // Arrange
        String subscriptionId = "subscription123";
        String objectId = "object456";

        when(httpClientMock.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponseMock);
        when(httpResponseMock.statusCode()).thenReturn(200);
        when(httpResponseMock.body()).thenReturn("OK");

        // Act
        Integer result = metasysStreamClient.subscribePresentValueChange(subscriptionId, objectId);

        // Assert
        assertEquals(200, result);

        ArgumentCaptor<HttpRequest> requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClientMock).send(requestCaptor.capture(), any(HttpResponse.BodyHandler.class));

        HttpRequest capturedRequest = requestCaptor.getValue();
        assertEquals("GET", capturedRequest.method());
        assertTrue(capturedRequest.uri().toString().contains("objects/" + objectId + "/attributes/presentValue"));
        assertTrue(capturedRequest.headers().firstValue("Authorization").isPresent());
        assertTrue(capturedRequest.headers().firstValue("METASYS-SUBSCRIBE").isPresent());
        assertEquals(subscriptionId, capturedRequest.headers().firstValue("METASYS-SUBSCRIBE").get());
    }

    @Test
    void subscribePresentValueChange_withStatusCode202_returnsStatusCode202() throws Exception {
        // Arrange
        String subscriptionId = "subscription123";
        String objectId = "object456";

        when(httpClientMock.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponseMock);
        when(httpResponseMock.statusCode()).thenReturn(202);
        when(httpResponseMock.body()).thenReturn("Accepted");

        // Act
        Integer result = metasysStreamClient.subscribePresentValueChange(subscriptionId, objectId);

        // Assert
        assertEquals(202, result);
        verify(httpClientMock).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }

    @Test
    void subscribePresentValueChange_withNullObjectId_throwsException() throws IOException, InterruptedException {
        // Arrange
        String subscriptionId = "subscription123";
        String objectId = null;

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            metasysStreamClient.subscribePresentValueChange(subscriptionId, objectId);
        });

        assertEquals("ObjectId must be provided", exception.getMessage());
        verify(httpClientMock, never()).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }

    @Test
    void subscribePresentValueChange_withEmptyObjectId_throwsException() throws IOException, InterruptedException {
        // Arrange
        String subscriptionId = "subscription123";
        String objectId = "";

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            metasysStreamClient.subscribePresentValueChange(subscriptionId, objectId);
        });

        assertEquals("ObjectId must be provided", exception.getMessage());
        verify(httpClientMock, never()).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }

    @Test
    void subscribePresentValueChange_withNullSubscriptionId_usesDefaultValue() throws Exception {
        // Arrange
        String subscriptionId = null;
        String objectId = "object456";

        when(httpClientMock.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponseMock);
        when(httpResponseMock.statusCode()).thenReturn(200);

        // Act
        Integer result = metasysStreamClient.subscribePresentValueChange(subscriptionId, objectId);

        // Assert
        assertEquals(200, result);

        ArgumentCaptor<HttpRequest> requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClientMock).send(requestCaptor.capture(), any(HttpResponse.BodyHandler.class));

        HttpRequest capturedRequest = requestCaptor.getValue();
        assertEquals("not-set", capturedRequest.headers().firstValue("METASYS-SUBSCRIBE").get());
    }

    @Test
    void subscribePresentValueChange_withEmptySubscriptionId_usesDefaultValue() throws Exception {
        // Arrange
        String subscriptionId = "";
        String objectId = "object456";

        when(httpClientMock.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponseMock);
        when(httpResponseMock.statusCode()).thenReturn(200);

        // Act
        Integer result = metasysStreamClient.subscribePresentValueChange(subscriptionId, objectId);

        // Assert
        assertEquals(200, result);

        ArgumentCaptor<HttpRequest> requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClientMock).send(requestCaptor.capture(), any(HttpResponse.BodyHandler.class));

        HttpRequest capturedRequest = requestCaptor.getValue();
        assertEquals("not-set", capturedRequest.headers().firstValue("METASYS-SUBSCRIBE").get());
    }


    @Test
    void subscribePresentValueChangeIOException() throws Exception {
        // Arrange
        String subscriptionId = "subscription123";
        String objectId = "object456";

        when(httpClientMock.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenThrow(new IOException("Network error"));

        // Act & Assert
        MetasysCloudConnectorException exception = assertThrows(MetasysCloudConnectorException.class, () -> {
            metasysStreamClient.subscribePresentValueChange(subscriptionId, objectId);
        });

        assertTrue(exception.getMessage().contains("Failed to subscribe to present value change"));
        assertTrue(exception.getMessage().contains("object456"));
        verify(httpClientMock).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }

    @Test
    void subscribePresentValueChangeVerifiesCorrectHeaders() throws Exception {
        // Arrange
        String subscriptionId = "subscription123";
        String objectId = "object456";

        when(httpClientMock.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponseMock);
        when(httpResponseMock.statusCode()).thenReturn(200);

        // Act
        metasysStreamClient.subscribePresentValueChange(subscriptionId, objectId);

        // Assert
        ArgumentCaptor<HttpRequest> requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClientMock).send(requestCaptor.capture(), any(HttpResponse.BodyHandler.class));

        HttpRequest capturedRequest = requestCaptor.getValue();

        // Verify all required headers
        assertTrue(capturedRequest.headers().firstValue("Authorization").isPresent());
        assertTrue(capturedRequest.headers().firstValue("Authorization").get().startsWith("Bearer "));
        assertEquals("application/json", capturedRequest.headers().firstValue("Content-Type").get());
        assertEquals("METASYS-SUBSCRIBE", capturedRequest.headers().map().keySet().stream()
                .filter(key -> key.equals("METASYS-SUBSCRIBE")).findFirst().orElse(""));
    }

    @Test
    void subscribePresentValueChangeVerifiesCorrectUrl() throws Exception {
        // Arrange
        String subscriptionId = "subscription123";
        String objectId = "object456";

        when(httpClientMock.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponseMock);
        when(httpResponseMock.statusCode()).thenReturn(200);

        // Act
        metasysStreamClient.subscribePresentValueChange(subscriptionId, objectId);

        // Assert
        ArgumentCaptor<HttpRequest> requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClientMock).send(requestCaptor.capture(), any(HttpResponse.BodyHandler.class));

        HttpRequest capturedRequest = requestCaptor.getValue();
        String expectedUrl = API_URI + "objects/" + objectId + "/attributes/presentValue?includeSchema=false";
        assertEquals(expectedUrl, capturedRequest.uri().toString());
    }

    /*
    @BeforeEach
    void setUp() throws Exception{
        ApplicationPropertiesTestHelper.enableMutableSingleton();
        ApplicationProperties.builder().buildAndSetStaticSingleton();
        MetasysStreamClientMock = mock(MetasysStreamClient.class);
        MetasysStreamClient = new MetasysStreamClient(MetasysStreamClientMock);
    }


    @Test
    void hasReceivedMessagesLatelyCheck() {
        long now = System.currentTimeMillis();
        MetasysStreamClient.setLastEventReceievedAt(Instant.ofEpochMilli(now));
        assertTrue(MetasysStreamClient.hasReceivedMessagesRecently());
    }

    @Test
    void serverReply204whenOpenStream() {
        String accessToken = "dummyToken";
        when(MetasysStreamClientMock.getUserToken())
                .thenReturn(new UserToken(accessToken,Instant.now().plusSeconds(60),"refreshingtoken"));
        mockServer
                .when(
                        request()
                                .withMethod("GET")
                                .withHeader("Authorization", "Bearer " + accessToken)
                                .withPath("/api/v4/stream")
                )
                .respond(
                        response()
                                .withStatusCode(204)
                );


        try {
            StreamListener streamListenerMock = mock(StreamListener.class);
            MetasysStreamClient.openStream("http://localhost:" + HTTP_PORT + "/api/v4/stream", "dummyToken", null, streamListenerMock);
            MetasysStreamClient.close();
        } catch (Exception e) {
            System.out.println(e);
        }
        mockServer.verify(
                request()
                        .withMethod("GET")
                        .withPath("/api/v4/stream")
                        .withHeader("Authorization", "Bearer dummyToken"),
                VerificationTimes.atLeast(1));
    }

    @AfterAll
    static void afterAll() {
//        mockServer.retrieveRecordedRequestsAndResponses(request().withPath("/api/v4/stream"));
        mockServer.stop();
    }

 */
}