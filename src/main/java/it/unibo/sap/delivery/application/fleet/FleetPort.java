package it.unibo.sap.delivery.application.fleet;

import it.unibo.sap.common.hexagonal.OutputPort;

import java.time.LocalDateTime;
import java.util.List;

public interface FleetPort extends OutputPort {

    FleetAssignmentResult assignNearestDrone(FleetFeasibilityRequest request);

    FleetReservationResult reserveDroneForSlot(FleetFeasibilityRequest request, LocalDateTime slot);

    FleetAssignmentResult assignReservedDrone(String deliveryId, LocalDateTime slot);

    void releaseReservation(String droneId, String deliveryId, LocalDateTime slot);

    void startDelivery(String droneId);

    void completeDelivery(String deliveryId);

    List<FleetViews.FleetDroneView> fleetMonitoringView();

}