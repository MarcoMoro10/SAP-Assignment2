package it.unibo.sap.delivery.support;

import it.unibo.sap.delivery.application.GeocodingPort;
import it.unibo.sap.delivery.domain.deliveries.Coordinates;

/**
 * Deterministic fake of {@link GeocodingPort} for unit tests: returns fixed coordinates inside the
 * Bologna bounding box, and rejects a blank street or a non-positive number (as the real geocoder does).
 */
public final class FakeGeocodingPort implements GeocodingPort {

    @Override
    public Coordinates geocode(final String street, final int number) {
        if (street == null || street.isBlank() || number <= 0) {
            throw new InvalidAddressException("Invalid address");
        }
        return new Coordinates(44.50, 11.34);
    }
}
