package no.cantara.realestate.metasys.cloudconnector.automationserver.stream;

import no.cantara.realestate.cloudconnector.notifications.NotificationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockserver.integration.ClientAndServer;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
    }

    @AfterEach
    void tearDown() throws Exception {
        // Rydder opp singleton-instansen etter test
        Field instanceField = MetasysStreamClient.class.getDeclaredField("instance");
        instanceField.setAccessible(true);
        instanceField.set(null, null);
    }

//    @BeforeAll
//    static void beforeAll() {
//        mockServer = startClientAndServer(HTTP_PORT);
//    }


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
    void tempTest() {
        assertTrue(true);
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