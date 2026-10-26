package no.cantara.realestate.metasys.cloudconnector.automationserver;

import no.cantara.realestate.metasys.cloudconnector.automationserver.MetasysCircuitBreaker.State;
import no.cantara.realestate.metasys.cloudconnector.automationserver.MetasysCircuitBreaker.Transition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class MetasysCircuitBreakerTest {

    private static final Instant START = Instant.parse("2026-09-05T10:00:00Z");

    private MutableClock clock;
    private MetasysCircuitBreaker breaker;
    private List<Transition> transitions;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(START);
        breaker = new MetasysCircuitBreaker(clock);
        transitions = new ArrayList<>();
        breaker.addListener(transitions::add);
    }

    @Test
    void startsClosed() {
        assertFalse(breaker.isOpen());
        assertEquals(State.CLOSED, breaker.state());
        assertNull(breaker.reason());
        assertEquals(START, breaker.lastTransitionAt());
    }

    @Test
    void openRecordsReasonAndTimeFromClock() {
        clock.advance(Duration.ofMinutes(3));

        breaker.open("HTTP 403");

        assertTrue(breaker.isOpen());
        assertEquals(State.OPEN, breaker.state());
        assertEquals("HTTP 403", breaker.reason());
        assertEquals(START.plus(Duration.ofMinutes(3)), breaker.lastTransitionAt());
    }

    @Test
    void closeClearsReasonAndRecordsTime() {
        breaker.open("HTTP 403");
        clock.advance(Duration.ofMinutes(2));

        breaker.close();

        assertFalse(breaker.isOpen());
        assertNull(breaker.reason());
        assertEquals(START.plus(Duration.ofMinutes(2)), breaker.lastTransitionAt());
    }

    @Test
    void listenerIsToldAboutEachTransitionWithTimeSpentInPreviousState() {
        clock.advance(Duration.ofMinutes(10));
        breaker.open("HTTP 403");
        clock.advance(Duration.ofSeconds(75));
        breaker.close();

        assertEquals(2, transitions.size());
        assertEquals(new Transition(State.OPEN, "HTTP 403", START.plus(Duration.ofMinutes(10)), Duration.ofMinutes(10)),
                transitions.get(0));
        assertEquals(new Transition(State.CLOSED, null, START.plus(Duration.ofSeconds(675)), Duration.ofSeconds(75)),
                transitions.get(1));
    }

    @Test
    void openWhenAlreadyOpenIsNoOp() {
        breaker.open("HTTP 403");
        clock.advance(Duration.ofSeconds(30));

        breaker.open("HTTP 401");

        assertEquals(1, transitions.size());
        assertEquals("HTTP 403", breaker.reason(), "First reason is kept");
        assertEquals(START, breaker.lastTransitionAt(), "Time of the real transition is kept");
    }

    @Test
    void closeWhenAlreadyClosedIsNoOp() {
        clock.advance(Duration.ofSeconds(30));

        breaker.close();

        assertTrue(transitions.isEmpty());
        assertEquals(START, breaker.lastTransitionAt());
    }

    @Test
    void failingListenerDoesNotStopOtherListenersOrTheTransition() {
        MetasysCircuitBreaker breakerWithFailingListener = new MetasysCircuitBreaker(clock);
        List<Transition> seen = new ArrayList<>();
        breakerWithFailingListener.addListener(t -> {
            throw new IllegalStateException("listener failure");
        });
        breakerWithFailingListener.addListener(seen::add);

        breakerWithFailingListener.open("HTTP 403");

        assertTrue(breakerWithFailingListener.isOpen());
        assertEquals(1, seen.size());
    }

    @Test
    void concurrentOpenNotifiesExactlyOnce() throws InterruptedException {
        int threads = 16;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch go = new CountDownLatch(1);
        List<Transition> seen = java.util.Collections.synchronizedList(new ArrayList<>());
        MetasysCircuitBreaker shared = new MetasysCircuitBreaker(clock);
        shared.addListener(seen::add);

        for (int i = 0; i < threads; i++) {
            int n = i;
            pool.submit(() -> {
                go.await();
                shared.open("HTTP 403 from thread " + n);
                return null;
            });
        }
        go.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS));

        assertEquals(1, seen.size());
    }
}
