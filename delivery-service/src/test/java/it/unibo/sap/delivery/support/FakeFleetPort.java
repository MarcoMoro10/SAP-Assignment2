package it.unibo.sap.delivery.support;

import it.unibo.sap.delivery.application.fleet.FleetAssignmentResult;
import it.unibo.sap.delivery.application.fleet.FleetFeasibilityRequest;
import it.unibo.sap.delivery.application.fleet.FleetPort;
import it.unibo.sap.delivery.application.fleet.FleetReservationResult;
import it.unibo.sap.delivery.application.fleet.FleetViews;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Configurable fake of {@link FleetPort} for solitary unit tests of the delivery service.
 * By default it assigns/reserves the drone {@link #DEFAULT_DRONE}; set a rejection reason to
 * simulate "no drone" outcomes. Side-effecting calls are recorded for assertions.
 */
public final class FakeFleetPort implements FleetPort {

    public static final String DEFAULT_DRONE = "DRN-TEST";

    private String rejectionReason;
    private String droneId = DEFAULT_DRONE;

    public final List<String> startedDrones = new ArrayList<>();
    public final List<String> releasedReservations = new ArrayList<>();

    public FakeFleetPort rejectingWith(final String reason) {
        this.rejectionReason = reason;
        return this;
    }

    public FakeFleetPort assigningDrone(final String id) {
        this.droneId = id;
        return this;
    }

    @Override
    public FleetAssignmentResult assignNearestDrone(final FleetFeasibilityRequest request) {
        return rejectionReason != null
                ? FleetAssignmentResult.rejected(rejectionReason)
                : FleetAssignmentResult.assigned(droneId);
    }

    @Override
    public FleetReservationResult reserveDroneForSlot(final FleetFeasibilityRequest request, final LocalDateTime slot) {
        return rejectionReason != null
                ? FleetReservationResult.rejected(rejectionReason)
                : FleetReservationResult.reserved(droneId);
    }

    @Override
    public FleetAssignmentResult assignReservedDrone(final String deliveryId) {
        return rejectionReason != null
                ? FleetAssignmentResult.rejected(rejectionReason)
                : FleetAssignmentResult.assigned(droneId);
    }

    @Override
    public void releaseReservation(final String droneId, final String deliveryId) {
        releasedReservations.add(droneId);
    }

    @Override
    public void startDelivery(final String droneId) {
        startedDrones.add(droneId);
    }

    @Override
    public void completeDelivery(final String deliveryId) {
        // no-op for tests
    }

    @Override
    public List<FleetViews.FleetDroneView> fleetMonitoringView() {
        return List.of();
    }
}
