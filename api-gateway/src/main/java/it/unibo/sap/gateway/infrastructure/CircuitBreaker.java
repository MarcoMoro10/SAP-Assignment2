package it.unibo.sap.gateway.infrastructure;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

public final class CircuitBreaker {

    public enum State { CLOSED, OPEN, HALF_OPEN }

    private static final int DEFAULT_WINDOW_SIZE = 20;
    private static final int DEFAULT_MINIMUM_CALLS = 5;
    private static final double DEFAULT_FAILURE_THRESHOLD = 0.5;
    private static final long DEFAULT_OPEN_TIMEOUT_MS = 10_000;

    private final int windowSize;
    private final int minimumCalls;
    private final double failureThreshold;
    private final long openTimeoutMs;
    private final LongSupplier clock;

    private final Deque<Boolean> window = new ArrayDeque<>();
    private int failuresInWindow;
    private State state = State.CLOSED;
    private long openedAt;
    private boolean probing;
    private Consumer<Boolean> onStateChange = open -> { };

    public CircuitBreaker() {
        this(DEFAULT_WINDOW_SIZE, DEFAULT_MINIMUM_CALLS, DEFAULT_FAILURE_THRESHOLD,
                DEFAULT_OPEN_TIMEOUT_MS, System::currentTimeMillis);
    }

    public CircuitBreaker(final int windowSize, final int minimumCalls, final double failureThreshold,
                          final long openTimeoutMs, final LongSupplier clock) {
        this.windowSize = windowSize;
        this.minimumCalls = minimumCalls;
        this.failureThreshold = failureThreshold;
        this.openTimeoutMs = openTimeoutMs;
        this.clock = clock;
    }

    public synchronized void setOnStateChange(final Consumer<Boolean> callback) {
        this.onStateChange = callback != null ? callback : open -> { };
    }

    public synchronized State state() {
        return state;
    }

    public synchronized boolean isOpen() {
        return state == State.OPEN;
    }

    public synchronized void recordSuccess() {
        record(true);
    }

    public synchronized void recordFailure() {
        record(false);
    }

    private void record(final boolean success) {
        switch (state) {
            case HALF_OPEN -> transitionTo(success ? State.CLOSED : State.OPEN);
            case OPEN -> { /* ignore: while open no real call should reach the breaker */ }
            case CLOSED -> {
                window.addLast(success);
                if (!success) {
                    failuresInWindow++;
                }
                while (window.size() > windowSize) {
                    if (Boolean.FALSE.equals(window.removeFirst())) {
                        failuresInWindow--;
                    }
                }
                if (window.size() >= minimumCalls
                        && (double) failuresInWindow / window.size() >= failureThreshold) {
                    transitionTo(State.OPEN);
                }
            }
            default -> { }
        }
    }

    public synchronized boolean tryStartProbe() {
        if (state == State.OPEN && !probing && clock.getAsLong() - openedAt >= openTimeoutMs) {
            probing = true;
            return true;
        }
        return false;
    }

    public synchronized void probeSucceeded() {
        probing = false;
        if (state == State.OPEN) {
            transitionTo(State.HALF_OPEN);
        }
    }

    public synchronized void probeFailed() {
        probing = false;
        openedAt = clock.getAsLong();
    }

    private void transitionTo(final State newState) {
        final boolean wasOpen = state == State.OPEN;
        state = newState;
        window.clear();
        failuresInWindow = 0;
        if (newState == State.OPEN) {
            openedAt = clock.getAsLong();
            probing = false;
        }
        final boolean nowOpen = newState == State.OPEN;
        if (nowOpen != wasOpen) {
            onStateChange.accept(nowOpen);
        }
    }
}