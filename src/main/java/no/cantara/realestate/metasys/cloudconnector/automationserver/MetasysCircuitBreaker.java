package no.cantara.realestate.metasys.cloudconnector.automationserver;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

public class MetasysCircuitBreaker {

    public enum State { CLOSED, OPEN }

    public record Transition(State state, String reason, Instant at, Duration previousStateDuration) {
    }

    @FunctionalInterface
    public interface Listener {
        void onTransition(Transition transition);
    }

    public MetasysCircuitBreaker(Clock clock) {
    }

    public void addListener(Listener listener) {
    }

    public boolean isOpen() {
        return false;
    }

    public State state() {
        return State.CLOSED;
    }

    public String reason() {
        return null;
    }

    public Instant lastTransitionAt() {
        return null;
    }

    public void open(String reason) {
    }

    public void close() {
    }
}
