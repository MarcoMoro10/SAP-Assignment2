package it.unibo.sap.delivery.domain.deliveries;

import it.unibo.sap.common.ddd.ValueObject;

import java.time.Duration;
import java.util.Objects;

public record EstimatedTimeRemaining(Duration value) implements ValueObject {

    public EstimatedTimeRemaining {
        Objects.requireNonNull(value, "ETR value must not be null");
        if (value.isNegative()) {
            throw new IllegalArgumentException("ETR must not be negative");
        }
    }

    public static EstimatedTimeRemaining of(final Duration value) {
        return new EstimatedTimeRemaining(value);
    }

    public static EstimatedTimeRemaining ofSeconds(final long seconds) {
        return new EstimatedTimeRemaining(Duration.ofSeconds(seconds));
    }

    public static EstimatedTimeRemaining zero() {
        return new EstimatedTimeRemaining(Duration.ZERO);
    }

    public long toSeconds() {
        return value.getSeconds();
    }

}
