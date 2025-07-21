package no.cantara.realestate.metasys.cloudconnector.automationserver;

import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

public class MockServerExtension implements BeforeEachCallback, AfterEachCallback {
    private MetasysApiFailureSimulator simulator;

    @Override
    public void afterEach(ExtensionContext context) throws Exception {

    }

    @Override
    public void beforeEach(ExtensionContext context) throws Exception {

    }
    /*
    @Override
    public void beforeEach(ExtensionContext context) {
        simulator = new MetasysApiFailureSimulator(); // Automatisk port
        simulator.start();

        // Lagre port i test context
        context.getStore(ExtensionContext.Namespace.GLOBAL)
                .put("mockserver.port", simulator.getPort());
    }

    @Override
    public void afterEach(ExtensionContext context) {
        if (simulator != null) {
            simulator.close();
        }
    }

    public static int getPort(ExtensionContext context) {
        return context.getStore(ExtensionContext.Namespace.GLOBAL)
                .get("mockserver.port", Integer.class);
    }

     */
}