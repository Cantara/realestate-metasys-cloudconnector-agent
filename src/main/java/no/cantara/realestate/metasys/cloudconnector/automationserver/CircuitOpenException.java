package no.cantara.realestate.metasys.cloudconnector.automationserver;

/**
 * A call to Metasys was rejected locally because the circuit is open.
 * <p>
 * Thrown before any HTTP request is made, never as the result of one. Callers can treat it as
 * "skip this round": the client recovers on its own, and retrying immediately only adds noise.
 * The status code is 0 because no response exists.
 */
public class CircuitOpenException extends MetasysApiException {

    public CircuitOpenException(String message) {
        super(message, 0);
    }
}
