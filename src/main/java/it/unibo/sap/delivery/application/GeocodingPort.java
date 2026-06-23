package it.unibo.sap.delivery.application;

import it.unibo.sap.common.hexagonal.OutputPort;
import it.unibo.sap.delivery.domain.deliveries.Coordinates;

public interface GeocodingPort extends OutputPort {

    Coordinates geocode(String street, int number);

    class InvalidAddressException extends RuntimeException {
        public InvalidAddressException(final String message) {
            super(message);
        }
    }
}
