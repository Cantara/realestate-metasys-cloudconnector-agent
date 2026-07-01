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
        MockServerSetup.clearAndSetSensorMockData("7c0b9098-e858-5d1a-abdc-2597583a2934");
        MockServerSetup.clearAndSetSensorMockData("Sensor-640f4b48-4f87-4337-93e4-messom");
        MockServerSetup.mockPresentValueSubscription();
        MockServerSetup.streamMock();

        log.info("MockServer is running on port 1080 with mock responses configured.");
        System.out.println("MockServer is running on port 1080 with mock responses configured.");
    }
}
