package no.cantara.realestate.metasys.cloudconnector;

import org.mockserver.integration.ClientAndServer;
import org.slf4j.Logger;

import static org.slf4j.LoggerFactory.getLogger;

public class MockServerRunner {
    private static final Logger log = getLogger(MockServerRunner.class);
    private static ClientAndServer mockServer;

    public static void main(String[] args) {
        // Start MockServer
        mockServer = ClientAndServer.startClientAndServer(1080);

        // Run mock setup
        MockServerSetup.loginMock();
        MockServerSetup.refreshTokenMock();
        MockServerSetup.clearAndSetSensorMockData("01ee7349-f468-554f-8221-d65663799d24");
        MockServerSetup.mockPresentValueSubscription();

        log.info("MockServer is running on port 1080 with mock responses configured.");
        System.out.println("MockServer is running on port 1080 with mock responses configured.");
    }
}
