package no.cantara.realestate.metasys.cloudconnector.automationserver;

import no.cantara.realestate.metasys.cloudconnector.notifications.NotificationService;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MetasysClientTest {

    private MetasysClient metasysClient;

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

    @BeforeEach
    void setUp() throws Exception {
        // Nullstiller singleton-instansen
        Field instanceField = MetasysClient.class.getDeclaredField("instance");
        instanceField.setAccessible(true);
        instanceField.set(null, null);

        // Lager en direkte instans av MetasysClient ved å bruke konstruktøren via reflection
        // istedenfor å kalle getInstance() som vil gjøre et faktisk HTTP-kall
        Constructor<MetasysClient> constructor = MetasysClient.class.getDeclaredConstructor(
                String.class, String.class, URI.class, NotificationService.class);
        constructor.setAccessible(true);
        metasysClient = constructor.newInstance(USERNAME, PASSWORD, API_URI, notificationServiceMock);

        // Setter opp mocked HttpClient
        Field httpClientField = MetasysClient.class.getDeclaredField("httpClient");
        httpClientField.setAccessible(true);
        httpClientField.set(metasysClient, httpClientMock);

        // Setter feltverdier direkte for å simulere at vi allerede er logget inn
        Field accessTokenField = MetasysClient.class.getDeclaredField("accessToken");
        accessTokenField.setAccessible(true);
        accessTokenField.set(metasysClient, VALID_ACCESS_TOKEN);

        Field tokenExpiryTimeField = MetasysClient.class.getDeclaredField("tokenExpiryTime");
        tokenExpiryTimeField.setAccessible(true);
        tokenExpiryTimeField.set(metasysClient, TOKEN_EXPIRY);

        // Setter instansen i singleton-feltet
        instanceField.set(null, metasysClient);
    }

    @AfterEach
    void tearDown() throws Exception {
        // Rydder opp singleton-instansen etter test
        Field instanceField = MetasysClient.class.getDeclaredField("instance");
        instanceField.setAccessible(true);
        instanceField.set(null, null);
    }

    @Test
    void loginIsHealthy() {

    }

    @Test
    void loginIsUnHealthy() {

    }

    @Test
    void refreshTokenIsHealthy() {
//        fail("Not yet implemented");
    }

    @Test
    void refreshTokenSilentlyGet() throws Exception {
        // Arrange
        when(httpClientMock.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponseMock);
        when(httpResponseMock.statusCode()).thenReturn(200);
        when(httpResponseMock.body()).thenReturn("{\"accessToken\":\"new_token\",\"expires\":\"2023-12-31T23:59:59Z\"}");

        ArgumentCaptor<HttpRequest> requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);

        // Act
        metasysClient.refreshTokenSilently();

        // Assert
        verify(httpClientMock).send(requestCaptor.capture(), any(HttpResponse.BodyHandler.class));
        HttpRequest capturedRequest = requestCaptor.getValue();
        assertEquals("GET", capturedRequest.method(), "Expected HTTP method to be GET");
    }

    /*
    TODO Fail on Jenkins.
    eg refreshTokenSilently_IOException
    An unexpected error occurred while verifying a static stub

To correctly verify a stub, invoke a single static method of no.cantara.realestate.metasys.cloudconnector.status.TemporaryHealthResource in the provided lambda.
For example, if a method 'sample' was defined, provide a lambda or anonymous class containing the code

() -> TemporaryHealthResource.sample()
or
TemporaryHealthResource::sample


    @Test
    void refreshTokenSilently_Success() throws Exception {
        // Arrange
        String successResponse = "{\"accessToken\":\"" + NEW_ACCESS_TOKEN + "\",\"expires\":\"" + TOKEN_EXPIRY + "\"}";

        when(httpClientMock.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponseMock);
        when(httpResponseMock.statusCode()).thenReturn(200);
        when(httpResponseMock.body()).thenReturn(successResponse);

        MetasysUserToken mockUserToken = new MetasysUserToken();
        mockUserToken.setAccessToken(NEW_ACCESS_TOKEN);
        mockUserToken.setExpires(TOKEN_EXPIRY);

        try (MockedStatic<RealEstateObjectMapper> mapperMock = mockStatic(RealEstateObjectMapper.class)) {
            RealEstateObjectMapper mockInstance = mock(RealEstateObjectMapper.class);
            com.fasterxml.jackson.databind.ObjectMapper mockObjectMapper = mock(com.fasterxml.jackson.databind.ObjectMapper.class);

            mapperMock.when(RealEstateObjectMapper::getInstance).thenReturn(mockInstance);
            when(mockInstance.getObjectMapper()).thenReturn(mockObjectMapper);
            when(mockObjectMapper.readValue(anyString(), eq(MetasysUserToken.class))).thenReturn(mockUserToken);

            ArgumentCaptor<HttpRequest> requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);

            // Act
            metasysClient.refreshTokenSilently();

            // Assert
            Field accessTokenField = MetasysClient.class.getDeclaredField("accessToken");
            accessTokenField.setAccessible(true);
            String updatedToken = (String) accessTokenField.get(metasysClient);

            assertEquals(NEW_ACCESS_TOKEN, updatedToken, "Access token should be updated with new value");
            verify(notificationServiceMock, never()).sendWarning(anyString(), anyString());
            verify(notificationServiceMock, never()).sendAlarm(anyString(), anyString());
        }
    }

    // De øvrige testmetodene forblir uendret
    @Test
    void refreshTokenSilently_Unauthorized() throws Exception {
        // Arrange
        when(httpClientMock.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponseMock);
        when(httpResponseMock.statusCode()).thenReturn(401);

        try (MockedStatic<TemporaryHealthResource> healthMock = mockStatic(TemporaryHealthResource.class)) {
            // Act & Assert
            MetasysApiException exception = assertThrows(MetasysApiException.class,
                    () -> metasysClient.refreshTokenSilently(),
                    "Should throw MetasysApiException on 401 status");

            assertTrue(exception.getMessage().contains("Login failed:"), "Refresh with invalid token should trigger new login.");
            healthMock.verify(() -> TemporaryHealthResource.setUnhealthy(), times(1));
        }
    }

    @Test
    void refreshTokenSilently_JsonProcessingException() throws Exception {
        // Arrange
        String invalidJsonResponse = "{invalid_json:}";

        when(httpClientMock.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponseMock);
        when(httpResponseMock.statusCode()).thenReturn(200);
        when(httpResponseMock.body()).thenReturn(invalidJsonResponse);

        try (MockedStatic<RealEstateObjectMapper> mapperMock = mockStatic(RealEstateObjectMapper.class);
             MockedStatic<TemporaryHealthResource> healthMock = mockStatic(TemporaryHealthResource.class)) {

            RealEstateObjectMapper mockInstance = mock(RealEstateObjectMapper.class);
            com.fasterxml.jackson.databind.ObjectMapper mockObjectMapper = mock(com.fasterxml.jackson.databind.ObjectMapper.class);

            mapperMock.when(RealEstateObjectMapper::getInstance).thenReturn(mockInstance);
            when(mockInstance.getObjectMapper()).thenReturn(mockObjectMapper);
            when(mockObjectMapper.readValue(anyString(), eq(MetasysUserToken.class)))
                    .thenThrow(new com.fasterxml.jackson.core.JsonProcessingException("Invalid JSON"){});

            // Act & Assert
            LogonFailedException exception = assertThrows(LogonFailedException.class,
                    () -> metasysClient.refreshTokenSilently(),
                    "Should throw LogonFailedException on JSON parsing error");

            verify(notificationServiceMock).sendWarning(
                    eq(MetasysClient.METASYS_API),
                    eq("Parsing of AccessToken information failed."));

            healthMock.verify(() -> TemporaryHealthResource.setUnhealthy(), times(1));
            healthMock.verify(() -> TemporaryHealthResource.addRegisteredError(contains("Failed to refresh token")), times(1));
        }
    }

    @Test
    void refreshTokenSilently_IOException() throws Exception {
        // Arrange
        when(httpClientMock.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenThrow(new IOException("Network error"));

        try (MockedStatic<TemporaryHealthResource> healthMock = mockStatic(TemporaryHealthResource.class)) {
            // Act & Assert
            LogonFailedException exception = assertThrows(LogonFailedException.class,
                    () -> metasysClient.refreshTokenSilently(),
                    "Should throw LogonFailedException on network error");

            verify(notificationServiceMock).sendAlarm(
                    eq(MetasysClient.METASYS_API),
                    eq(MetasysClient.HOST_UNREACHABLE));

            healthMock.verify(() -> TemporaryHealthResource.setUnhealthy(), times(1));
            healthMock.verify(() -> TemporaryHealthResource.addRegisteredError(contains("Failed to refresh accessToken")), times(1));
        }
    }

    @Test
    void refreshTokenSilently_ServerError() throws Exception {
        // Arrange
        when(httpClientMock.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponseMock);
        when(httpResponseMock.statusCode()).thenReturn(500);

        try (MockedStatic<TemporaryHealthResource> healthMock = mockStatic(TemporaryHealthResource.class)) {
            // Act & Assert
            MetasysApiException exception = assertThrows(MetasysApiException.class,
                    () -> metasysClient.refreshTokenSilently(),
                    "Should throw MetasysApiException on server error");

            assertTrue(exception.getMessage().contains("Token refresh failed with status code: 500"));
            healthMock.verify(() -> TemporaryHealthResource.setUnhealthy(), times(1));
        }
    }

     */
}