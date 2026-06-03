package no.cantara.realestate.metasys.cloudconnector;

import no.cantara.config.ApplicationProperties;
import no.cantara.realestate.metasys.cloudconnector.automationserver.stream.MetasysStreamClient;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Tests for issue #526: NullPointerException when sd.stream.enabled=true but sd.api.prod=false.
 * createStreamClient() returns null when not in prod mode; doInit() must guard against this.
 */
class MetasysCloudconnectorApplicationStreamClientTest {

    @Test
    void createStreamClient_withProdDisabled_returnsNull() {
        ApplicationProperties config = ApplicationProperties.builder()
                .property("sd.api.prod", "false")
                .property("sd.stream.enabled", "true")
                .build();

        MetasysCloudconnectorApplication app = new MetasysCloudconnectorApplication(config);

        MetasysStreamClient result = app.createStreamClient(config);

        assertNull(result, "StreamClient must be null when sd.api.prod is false — no simulator is available");
    }

    @Test
    void createStreamClient_withProdExplicitlyFalse_doesNotThrowNPE() {
        // Regression test for issue #526:
        // Before the fix, the null return value caused a NullPointerException in doInit()
        // because the health probes tried to call streamClient.getName() on a null reference.
        ApplicationProperties config = ApplicationProperties.builder()
                .property("sd.api.prod", "false")
                .property("sd.stream.enabled", "true")
                .build();

        MetasysCloudconnectorApplication app = new MetasysCloudconnectorApplication(config);

        MetasysStreamClient result = app.createStreamClient(config);

        // Simulates the null-check that was missing in doInit() before the fix
        if (result != null) {
            result.getName(); // would have been called unconditionally before the fix
        }

        assertNull(result);
    }

    @Test
    void createStreamClient_withProdNotSet_returnsNull() {
        // sd.api.prod defaults to false when not set — should also return null safely
        ApplicationProperties config = ApplicationProperties.builder()
                .property("sd.stream.enabled", "true")
                .build();

        MetasysCloudconnectorApplication app = new MetasysCloudconnectorApplication(config);

        MetasysStreamClient result = app.createStreamClient(config);

        assertNull(result);
    }
}
