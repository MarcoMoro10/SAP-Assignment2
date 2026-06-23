package it.unibo.sap.delivery.domain.deliveries;

import it.unibo.sap.common.ddd.ValueObject;

public record Package(double weightKg) implements ValueObject {

    public Package {
        if (weightKg <= 0) {
            throw new IllegalArgumentException("Package weight must be positive");
        }
    }
}
