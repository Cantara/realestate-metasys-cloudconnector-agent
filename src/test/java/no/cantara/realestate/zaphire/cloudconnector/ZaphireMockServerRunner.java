package no.cantara.realestate.zaphire.cloudconnector;

import org.mockserver.integration.ClientAndServer;
import org.slf4j.Logger;

import static org.slf4j.LoggerFactory.getLogger;

/**
 * Standalone runner for Zaphire mock server.
 * Starter en MockServer på port 1081 med alle Zaphire API-endepunkter konfigurert.
 * <p>
 * Bruk denne til manuell testing mot ZaphireClient:
 * <pre>
 *   URI apiUri = URI.create("http://localhost:1081/");
 *   ZaphireClient client = ZaphireClient.getInstance(apiUri, "test-site", notificationService);
 *   client.setAccessToken("test-token", Instant.now().plusSeconds(3600));
 * </pre>
 */
public class ZaphireMockServerRunner {
    private static final Logger log = getLogger(ZaphireMockServerRunner.class);
    private static ClientAndServer mockServer;

    public static void main(String[] args) {
        int port = 1081;
        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                log.warn("Invalid port argument '{}', using default port 1081", args[0]);
            }
        }

        mockServer = ClientAndServer.startClientAndServer(port);

        ZaphireMockServerSetup.setupAll(port);

        log.info("Zaphire MockServer is running on port {} with mock responses configured.", port);
        System.out.println("Zaphire MockServer is running on port " + port + " with mock responses configured.");
        System.out.println();
        System.out.println("Available endpoints:");
        System.out.println("  GET  /site/test-site/Tag/Values?name=building1/floor2/room201/temperature");
        System.out.println("  GET  /site/test-site/Tag/Values?name=building1/floor2/room201/humidity");
        System.out.println("  GET  /site/test-site/Tag/Values?name=building1/energy/meter1");
        System.out.println("  GET  /site/test-site/Tag/Values?name=nonexistent/tag  (returns error)");
        System.out.println("  POST /site/test-site/Tag/Values  (batch, body: [\"tag1\",\"tag2\"])");
        System.out.println("  GET  /site/test-site/Tags/History/Records?name={tag}&from={from}&to={to}");
        System.out.println();
        System.out.println("Press Ctrl+C to stop.");
    }
}
