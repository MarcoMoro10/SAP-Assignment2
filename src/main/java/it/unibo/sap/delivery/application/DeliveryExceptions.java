package it.unibo.sap.delivery.application;

public final class DeliveryExceptions {

    private DeliveryExceptions() {
    }

    public static class ValidationRejectedException extends RuntimeException {
        public ValidationRejectedException(final String reason) {
            super(reason);
        }
    }

    public static class BadRequestException extends RuntimeException {
        public BadRequestException(final String message) {
            super(message);
        }
    }

    public static class DeliveryNotFoundException extends RuntimeException {
        public DeliveryNotFoundException() {
            super("Delivery not found");
        }
    }

    public static class CannotCancelInFlightException extends RuntimeException {
        public CannotCancelInFlightException() {
            super("Delivery cannot be cancelled once in flight");
        }
    }
}
