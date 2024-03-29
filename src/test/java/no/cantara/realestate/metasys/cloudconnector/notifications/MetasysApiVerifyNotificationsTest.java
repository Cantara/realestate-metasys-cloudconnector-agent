package no.cantara.realestate.metasys.cloudconnector.notifications;

import no.cantara.config.ApplicationProperties;
import no.cantara.config.testsupport.ApplicationPropertiesTestHelper;
import no.cantara.realestate.metasys.cloudconnector.MetasysCloudconnectorApplicationFactory;
import no.cantara.realestate.metasys.cloudconnector.automationserver.MetasysApiClientRest;
import org.junit.jupiter.api.*;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.model.MediaType;

import java.net.URI;
import java.time.Instant;

import static no.cantara.realestate.mappingtable.Main.getConfigValue;
import static no.cantara.realestate.metasys.cloudconnector.automationserver.MetasysApiClientRest.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.JsonBody.json;

public class MetasysApiVerifyNotificationsTest {

    private static String apiUrl;
    private NotificationService notificationService;
    private static ClientAndServer mockServer;

    private static int HTTP_PORT = 1084;

    @BeforeAll
    static void beforeAll() {
        ApplicationPropertiesTestHelper.enableMutableSingleton();
        ApplicationProperties config = new MetasysCloudconnectorApplicationFactory()
                .conventions(ApplicationProperties.builder())
                .buildAndSetStaticSingleton();
        mockServer = ClientAndServer.startClientAndServer(HTTP_PORT);
    }

    @AfterAll
    static void afterAll() {
        mockServer.stop();
    }

    @BeforeEach
    void setUp() {
        notificationService = mock(NotificationService.class);
    }

    @AfterEach
    void tearDown() {
        mockServer.reset();
    }

    @Test
    void verifyMetasysHostUnreachable() {
        URI apiUri = URI.create("http://localhost:8080");
        MetasysApiClientRest metasysApiClient = new MetasysApiClientRest(apiUri, notificationService);
        try {
            metasysApiClient.logon();
            fail("Expected exception");
        } catch (Exception e) {
            verify(notificationService).sendAlarm(METASYS_API,HOST_UNREACHABLE);
        }
        assertFalse(metasysApiClient.isHealthy());
    }

    @Test
    void verifyMetasysLoginOk() {
        String userName = getConfigValue("sd.api.username");
        String password = getConfigValue("sd.api.password");
        mockServer
                .when(
                        request()
                                .withMethod("POST")
                                .withPath("/api/v4/login")
                                .withContentType(MediaType.APPLICATION_JSON)
                                .withBody(json("{\"username\": \"" + userName + "\", \"password\": \"" + password + "\"}"))
                )
                .respond(
                        response()
                                .withStatusCode(200)
                                .withBody("{\n" +
                                        "  \"accessToken\": \"eyJ0eXAiOiJKV1QiLCJhbGciOiJSUzI1...\",\n" +
                                        "  \"expires\": \"" + Instant.now().plusSeconds(60 * 60).toString() + "\"\n" +
                                        "}\n")
                                .withHeader(
                                        "Content-Type", "application/vnd.metasysapi.v4+json"
                                )
                );
        String apiUrl = "http://localhost:" + mockServer.getPort() + "/api/v4/";
        URI apiUri = URI.create(apiUrl);
        MetasysApiClientRest metasysApiClient = new MetasysApiClientRest(apiUri, notificationService);
        try {
            metasysApiClient.logon();
            verify(notificationService).clearService(METASYS_API);
        } catch (Exception e) {
            fail("No exception expected");
        }
        assertTrue(metasysApiClient.isHealthy());
    }
    @Test
    void verifyMetasysLoginFailed() {
        mockServer
                .when(
                        request()
                                .withMethod("POST")
                                .withPath("/api/v4/login")
                                .withContentType(MediaType.APPLICATION_JSON)
                                .withBody(json("{\"username\": \"jane-doe\", \"password\": \"strongPassword\"}"))
                )
                .respond(
                        response()
                                .withStatusCode(401)
                );
        String apiUrl = "http://localhost:" + mockServer.getPort() + "/api/v4/";
        URI apiUri = URI.create(apiUrl);
        MetasysApiClientRest metasysApiClient = new MetasysApiClientRest(apiUri, notificationService);
        try {
            metasysApiClient.logon();
            fail("Expected exception");
        } catch (Exception e) {
            verify(notificationService).sendWarning(METASYS_API, LOGON_FAILED);
        }
        assertFalse(metasysApiClient.isHealthy());
    }
}
