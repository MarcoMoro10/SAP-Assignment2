package it.unibo.sap.delivery.domain.fleet;

import it.unibo.sap.common.ddd.ValueObject;

public record PayloadCapacity(double maxWeightKg) implements ValueObject {

    public PayloadCapacity {
        if (maxWeightKg <= 0) {
            throw new IllegalArgumentException("Payload capacity must be positive");
        }
    }

    public boolean canCarry(final double weightKg) {
        return weightKg <= maxWeightKg;
    }
}
