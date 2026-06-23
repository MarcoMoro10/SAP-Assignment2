package it.unibo.sap.delivery.domain.fleet;

import it.unibo.sap.common.ddd.ValueObject;

public record Coordinates(double latitude, double longitude) implements ValueObject {

    public Coordinates {
        if (latitude < -90.0 || latitude > 90.0) {
            throw new IllegalArgumentException("Invalid position");
        }
        if (longitude < -180.0 || longitude > 180.0) {
            throw new IllegalArgumentException("Invalid position");
        }
    }

    public double euclideanDistanceTo(final Coordinates other) {
        final double dLat = this.latitude - other.latitude;
        final double dLon = this.longitude - other.longitude;
        return Math.sqrt(dLat * dLat + dLon * dLon);
    }
}
