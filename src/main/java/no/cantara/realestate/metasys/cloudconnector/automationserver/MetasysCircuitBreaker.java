package no.cantara.realestate.metasys.cloudconnector.automationserver;

import org.slf4j.Logger;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.slf4j.LoggerFactory.getLogger;

/**
 * Records whether Metasys is currently usable, why not, and since when.
 * <p>
 * OPEN means no further calls are sent until the owner closes the breaker again. The breaker does
 * not decide when to open or close; {@link MetasysCommandExecutor} opens it on authentication
 * failures and {@link MetasysClientCB} closes it after a successful login. Keeping the decisions
 * outside makes the breaker a plain, thread-safe state holder that is trivial to test.
 * <p>
 * Listeners are notified exactly once per real transition. Opening an open breaker or closing a
 * closed one is a no-op, so a burst of failing calls produces one log line and one alarm, not one
 * per sensor. Listeners run on the thread that caused the transition, after the state has changed.
 * <p>Example:
 * <pre>{@code
 * MetasysCircuitBreaker breaker = new MetasysCircuitBreaker(Clock.systemUTC());
 * breaker.addListener(t -> log.info("Metasys circuit {} reason={}", t.state(), t.reason()));
 * breaker.open("HTTP 403 on GET objects/abc/trendedAttributes/presentValue/samples");
 * }</pre>
 */
public class MetasysCircuitBreaker {
    private static final Logger log = getLogger(MetasysCircuitBreaker.class);

    public enum State { CLOSED, OPEN }

    /**
     * A change of state.
     *
     * @param state                 the new state
     * @param reason                why the breaker opened; {@code null} when it closed
     * @param at                    when the transition happened, from the breaker's clock
     * @param previousStateDuration how long the breaker was in the state it just left
     */
    public record Transition(State state, String reason, Instant at, Duration previousStateDuration) {
    }

    @FunctionalInterface
    public interface Listener {
        void onTransition(Transition transition);
    }

    private final Clock clock;
    private final List<Listener> listeners = new CopyOnWriteArrayList<>();
    private State state = State.CLOSED;
    private String reason;
    private Instant lastTransitionAt;

    /**
     * Create a closed breaker.
     *
     * @param clock source of transition timestamps
     */
    public MetasysCircuitBreaker(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.lastTransitionAt = clock.instant();
    }

    public void addListener(Listener listener) {
        listeners.add(Objects.requireNonNull(listener, "listener"));
    }

    public synchronized boolean isOpen() {
        return state == State.OPEN;
    }

    public synchronized State state() {
        return state;
    }

    /**
     * @return why the breaker is open, or {@code null} while it is closed
     */
    public synchronized String reason() {
        return reason;
    }

    public synchronized Instant lastTransitionAt() {
        return lastTransitionAt;
    }

    /**
     * Open the breaker. No-op if it is already open; the original reason and time are kept.
     *
     * @param reason why Metasys is considered unusable, for logs and alarms
     */
    public void open(String reason) {
        notifyListeners(transitionTo(State.OPEN, reason));
    }

    /**
     * Close the breaker. No-op if it is already closed.
     */
    public void close() {
        notifyListeners(transitionTo(State.CLOSED, null));
    }

    private synchronized Transition transitionTo(State newState, String newReason) {
        if (state == newState) {
            return null;
        }
        Instant now = clock.instant();
        Duration previousStateDuration = Duration.between(lastTransitionAt, now);
        state = newState;
        reason = newReason;
        lastTransitionAt = now;
        return new Transition(newState, newReason, now, previousStateDuration);
    }

    // Outside the lock, so a slow listener (e.g. a notification over HTTP) does not block isOpen().
    private void notifyListeners(Transition transition) {
        if (transition == null) {
            return;
        }
        for (Listener listener : listeners) {
            try {
                listener.onTransition(transition);
            } catch (RuntimeException e) {
                log.warn("Circuit breaker listener failed on transition to {}", transition.state(), e);
            }
        }
    }
}
