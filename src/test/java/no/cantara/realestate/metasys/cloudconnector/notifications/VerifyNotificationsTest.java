package no.cantara.realestate.metasys.cloudconnector.notifications;

import no.cantara.config.ApplicationProperties;
import no.cantara.realestate.metasys.cloudconnector.MetasysCloudconnectorApplicationFactory;
import no.cantara.realestate.metasys.cloudconnector.automationserver.MetasysApiClientRest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;

import static no.cantara.realestate.metasys.cloudconnector.automationserver.MetasysApiClientRest.HOST_UNREACHABLE;
import static no.cantara.realestate.metasys.cloudconnector.automationserver.MetasysApiClientRest.METASYS_API;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class VerifyNotificationsTest {

    private NotificationService notificationService;

    @BeforeAll
    static void beforeAll() {
        ApplicationProperties config = new MetasysCloudconnectorApplicationFactory()
                .conventions(ApplicationProperties.builder())
                .buildAndSetStaticSingleton();
    }

    @BeforeEach
    void setUp() {
        notificationService = mock(NotificationService.class);
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
}
