package it.unibo.sap.delivery.domain.deliveries;

import it.unibo.sap.common.ddd.ValueObject;

import java.time.Duration;
import java.util.Objects;
public record Deadline(Duration maxDuration) implements ValueObject {

    public Deadline {
        Objects.requireNonNull(maxDuration, "Deadline duration must not be null");
        if (maxDuration.isNegative() || maxDuration.isZero()) {
            throw new IllegalArgumentException("Deadline must be positive");
        }
    }

    public static Deadline ofMinutes(final long minutes) {
        return new Deadline(Duration.ofMinutes(minutes));
    }

}
