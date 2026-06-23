package it.unibo.sap.delivery.domain.fleet;

import it.unibo.sap.common.ddd.ValueObject;

import java.time.Instant;
import java.util.Objects;

public record Position(Coordinates coordinates, Instant observedAt) implements ValueObject {

    public Position {
        Objects.requireNonNull(coordinates, "Position coordinates must not be null");
        Objects.requireNonNull(observedAt, "Position observedAt must not be null");
    }

    public static Position at(final double latitude, final double longitude) {
        return new Position(new Coordinates(latitude, longitude), Instant.now());
    }

    public static Position at(final Coordinates coordinates) {
        return new Position(coordinates, Instant.now());
    }

    public double distanceTo(final Coordinates target) {
        return this.coordinates.euclideanDistanceTo(target);
    }
}
