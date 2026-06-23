package it.unibo.sap.delivery.application.fleet;

import java.util.Optional;

public record FleetAssignmentResult(boolean assigned,
                                    String droneId,
                                    String rejectionReason) {

    public static FleetAssignmentResult assigned(final String droneId) {
        return new FleetAssignmentResult(true, droneId, null);
    }

    public static FleetAssignmentResult rejected(final String reason) {
        return new FleetAssignmentResult(false, null, reason);
    }

    public Optional<String> droneIdOpt() {
        return Optional.ofNullable(droneId);
    }

    public Optional<String> rejectionReasonOpt() {
        return Optional.ofNullable(rejectionReason);
    }
}