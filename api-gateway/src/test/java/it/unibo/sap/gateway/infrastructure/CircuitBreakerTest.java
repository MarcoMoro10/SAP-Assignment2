package it.unibo.sap.gateway.infrastructure;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit test of the {@link CircuitBreaker} state machine with an injected clock: failures beyond the
 * threshold trip it open, while open it fails fast, and after the timeout + a healthy probe it
 * re-closes. The {@code onStateChange} callback must mirror the open/closed boolean.
 */
class CircuitBreakerTest {

    private static final int WINDOW = 10;
    private static final int MIN_CALLS = 4;
    private static final double THRESHOLD = 0.5;
    private static final long TIMEOUT_MS = 10_000;

    private final AtomicLong now = new AtomicLong(0);

    private CircuitBreaker newBreaker() {
        return new CircuitBreaker(WINDOW, MIN_CALLS, THRESHOLD, TIMEOUT_MS, now::get);
    }

    @Test
    void opensAfterFailuresBeyondThreshold() {
        final CircuitBreaker cb = newBreaker();

        cb.recordSuccess();
        cb.recordFailure();
        cb.recordFailure();
        assertFalse(cb.isOpen(), "should still be closed before reaching the minimum volume");
        cb.recordFailure();

        assertTrue(cb.isOpen());
        assertEquals(CircuitBreaker.State.OPEN, cb.state());
    }

    @Test
    void belowThresholdStaysClosed() {
        final CircuitBreaker cb = newBreaker();
        cb.recordSuccess();
        cb.recordSuccess();
        cb.recordSuccess();
        cb.recordFailure();
        assertFalse(cb.isOpen(), "25% failures must not trip the breaker");
    }

    @Test
    void whileOpenNoProbeBeforeTimeout() {
        final CircuitBreaker cb = trip(newBreaker());
        assertFalse(cb.tryStartProbe(), "no probe is allowed before the open timeout elapses");
        now.addAndGet(TIMEOUT_MS - 1);
        assertFalse(cb.tryStartProbe());
    }

    @Test
    void afterTimeoutHealthyProbeReClosesOnNextSuccess() {
        final CircuitBreaker cb = trip(newBreaker());

        now.addAndGet(TIMEOUT_MS);
        assertTrue(cb.tryStartProbe(), "after the timeout a single probe is allowed");
        assertFalse(cb.tryStartProbe(), "a second concurrent probe must be refused");

        cb.probeSucceeded();
        assertEquals(CircuitBreaker.State.HALF_OPEN, cb.state());
        assertFalse(cb.isOpen(), "HALF_OPEN lets the trial call through");

        cb.recordSuccess();
        assertEquals(CircuitBreaker.State.CLOSED, cb.state());
        assertFalse(cb.isOpen());
    }

    @Test
    void failedTrialReopensTheCircuit() {
        final CircuitBreaker cb = trip(newBreaker());
        now.addAndGet(TIMEOUT_MS);
        assertTrue(cb.tryStartProbe());
        cb.probeSucceeded();

        cb.recordFailure();
        assertTrue(cb.isOpen(), "a failed trial call sends the breaker back to OPEN");
    }

    @Test
    void onStateChangeMirrorsTheOpenBoolean() {
        final CircuitBreaker cb = newBreaker();
        final AtomicInteger lastOpen = new AtomicInteger(-1);
        cb.setOnStateChange(open -> lastOpen.set(open ? 1 : 0));

        trip(cb);
        assertEquals(1, lastOpen.get(), "tripping fires onStateChange(true)");

        now.addAndGet(TIMEOUT_MS);
        cb.tryStartProbe();
        cb.probeSucceeded();
        cb.recordSuccess();
        assertEquals(0, lastOpen.get(), "re-closing fires onStateChange(false)");
    }

    private CircuitBreaker trip(final CircuitBreaker cb) {
        cb.recordFailure();
        cb.recordFailure();
        cb.recordFailure();
        cb.recordFailure();
        assertTrue(cb.isOpen());
        return cb;
    }
}
