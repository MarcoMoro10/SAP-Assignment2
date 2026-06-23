package it.unibo.sap.delivery.domain.deliveries;

import it.unibo.sap.common.ddd.ValueObject;

import java.util.Objects;

public record Location(Coordinates coordinates, String address) implements ValueObject {

    public Location {
        Objects.requireNonNull(coordinates, "Location coordinates must not be null");
        Objects.requireNonNull(address, "Location address must not be null");
        if (address.isBlank()) {
            throw new IllegalArgumentException("Location address must not be empty");
        }
    }

    public static Location of(final Coordinates coordinates, final String address) {
        return new Location(coordinates, address);
    }
}
