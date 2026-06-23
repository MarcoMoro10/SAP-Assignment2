package it.unibo.sap.delivery.application.fleet;

import java.util.Optional;

public record FleetReservationResult(boolean reserved,
                                     String droneId,
                                     String rejectionReason) {

    public static FleetReservationResult reserved(final String droneId) {
        return new FleetReservationResult(true, droneId, null);
    }

    public static FleetReservationResult rejected(final String reason) {
        return new FleetReservationResult(false, null, reason);
    }

    public Optional<String> droneIdOpt() {
        return Optional.ofNullable(droneId);
    }

    public Optional<String> rejectionReasonOpt() {
        return Optional.ofNullable(rejectionReason);
    }
}