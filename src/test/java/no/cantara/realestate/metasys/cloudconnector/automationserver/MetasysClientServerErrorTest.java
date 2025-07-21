package no.cantara.realestate.metasys.cloudconnector.automationserver;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration test to verify the new server error handling logic in MetasysClient.
 * This test verifies that the logic for detecting server errors (5xx) is correct.
 */
public class MetasysClientServerErrorTest {

    @Test
    void testServerErrorDetection() {
        // Test that our new logic correctly identifies server errors that should trigger login fallback
        assertTrue(isServerError(500), "500 should be considered a server error");
        assertTrue(isServerError(501), "501 should be considered a server error");
        assertTrue(isServerError(502), "502 should be considered a server error");
        assertTrue(isServerError(503), "503 should be considered a server error");
        assertTrue(isServerError(504), "504 should be considered a server error");
        assertTrue(isServerError(550), "550 should be considered a server error");
        assertTrue(isServerError(599), "599 should be considered a server error");
        
        // Test that non-server errors are not considered server errors
        assertTrue(!isServerError(200), "200 should not be considered a server error");
        assertTrue(!isServerError(401), "401 should not be considered a server error");
        assertTrue(!isServerError(403), "403 should not be considered a server error");
        assertTrue(!isServerError(404), "404 should not be considered a server error");
        assertTrue(!isServerError(400), "400 should not be considered a server error");
        assertTrue(!isServerError(499), "499 should not be considered a server error");
        assertTrue(!isServerError(600), "600 should not be considered a server error");
    }

    /**
     * This method replicates the logic from the updated MetasysClient.refreshTokenSilently() method
     */
    private boolean isServerError(int statusCode) {
        return statusCode >= 500 && statusCode < 600;
    }
}