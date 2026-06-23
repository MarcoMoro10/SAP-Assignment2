package it.unibo.sap.delivery.infrastructure.fleet;

import it.unibo.sap.common.hexagonal.OutputAdapter;
import it.unibo.sap.delivery.application.fleet.FleetAssignmentResult;
import it.unibo.sap.delivery.application.fleet.FleetFeasibilityRequest;
import it.unibo.sap.delivery.application.fleet.FleetPort;
import it.unibo.sap.delivery.application.fleet.FleetReservationResult;
import it.unibo.sap.delivery.application.fleet.FleetViews;
import it.unibo.sap.delivery.domain.fleet.Coordinates;
import it.unibo.sap.delivery.domain.fleet.Drone;
import it.unibo.sap.delivery.domain.fleet.DroneId;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class FleetModule implements FleetPort, OutputAdapter {

    private final InMemoryDroneRepository drones;
    private DroneTelemetrySink telemetrySink;
    private final double droneSpeedUnitsPerSecond;

    private final Map<String, DroneSimulator> simulators = new ConcurrentHashMap<>();
    private final Map<String, String> deliveryToDrone = new ConcurrentHashMap<>();
    private final Map<String, Coordinates> deliveryDestination = new ConcurrentHashMap<>();

    public FleetModule(final InMemoryDroneRepository drones,
                       final double droneSpeedUnitsPerSecond) {
        this.drones = drones;
        this.droneSpeedUnitsPerSecond = droneSpeedUnitsPerSecond;
    }

    public void setTelemetrySink(final DroneTelemetrySink telemetrySink) {
        this.telemetrySink = telemetrySink;
    }

    @Override
    public FleetAssignmentResult assignNearestDrone(final FleetFeasibilityRequest req) {
        final Coordinates pickup = new Coordinates(req.pickupLatitude(), req.pickupLongitude());
        final Coordinates destination = new Coordinates(req.destinationLatitude(), req.destinationLongitude());

        final boolean anyCanCarry = drones.findAll().stream()
                .anyMatch(d -> d.canCarry(req.weightKg()));
        if (!anyCanCarry) {
            return FleetAssignmentResult.rejected("No drone can carry this package");
        }

        if (req.deadlineMinutes() > 0) {
            final double distance = pickup.euclideanDistanceTo(destination);
            final long estimatedSeconds = Math.round(distance / droneSpeedUnitsPerSecond);
            if (estimatedSeconds > req.deadlineMinutes() * 60L) {
                return FleetAssignmentResult.rejected("Delivery cannot be completed within the requested time");
            }
        }

        final Optional<Drone> chosen = drones.findAll().stream()
                .filter(Drone::isAvailable)
                .filter(d -> d.canCarry(req.weightKg()))
                .min(Comparator.comparingDouble(d -> d.getPosition().distanceTo(pickup)));

        if (chosen.isEmpty()) {
            return FleetAssignmentResult.rejected("No drone available");
        }

        final Drone drone = chosen.get();
        drone.assign(req.deliveryId());
        drones.save(drone);
        deliveryToDrone.put(req.deliveryId(), drone.getId().value());
        deliveryDestination.put(req.deliveryId(), destination);
        return FleetAssignmentResult.assigned(drone.getId().value());
    }

    @Override
    public FleetReservationResult reserveDroneForSlot(final FleetFeasibilityRequest req, final LocalDateTime slot) {
        final Optional<Drone> chosen = drones.findAll().stream()
                .filter(d -> d.canCarry(req.weightKg()))
                .filter(d -> d.isSlotFree(slot))
                .findFirst();

        if (chosen.isEmpty()) {
            return FleetReservationResult.rejected("No drone available for the requested time");
        }
        final Drone drone = chosen.get();
        drone.reserveSlot(req.deliveryId(), slot);
        drones.save(drone);
        deliveryToDrone.put(req.deliveryId(), drone.getId().value());
        deliveryDestination.put(req.deliveryId(),
                new Coordinates(req.destinationLatitude(), req.destinationLongitude()));
        return FleetReservationResult.reserved(drone.getId().value());
    }

    @Override
    public FleetAssignmentResult assignReservedDrone(final String deliveryId, final LocalDateTime slot) {
        final String droneId = deliveryToDrone.get(deliveryId);
        if (droneId == null) {
            return FleetAssignmentResult.rejected("No drone available");
        }
        final Optional<Drone> found = drones.findById(DroneId.of(droneId));
        if (found.isEmpty()) {
            return FleetAssignmentResult.rejected("No drone available");
        }
        final Drone drone = found.get();
        drone.assign(deliveryId);
        drones.save(drone);
        return FleetAssignmentResult.assigned(droneId);
    }

    @Override
    public void releaseReservation(final String droneId, final String deliveryId, final LocalDateTime slot) {
        if (droneId == null) {
            return;
        }
        drones.findById(DroneId.of(droneId)).ifPresent(drone -> {
            drone.releaseReservation(deliveryId, slot);
            drones.save(drone);
        });
        final DroneSimulator sim = simulators.remove(deliveryId);
        if (sim != null) {
            sim.stop();
        }
        deliveryToDrone.remove(deliveryId);
        deliveryDestination.remove(deliveryId);
    }

    @Override
    public void startDelivery(final String droneId) {
        if (droneId == null) {
            return;
        }
        drones.findById(DroneId.of(droneId)).ifPresent(drone -> {
            drone.startDelivery();
            drones.save(drone);
            final String deliveryId = drone.getAssignedDeliveryId();
            final Coordinates destination = deliveryDestination.get(deliveryId);
            if (deliveryId != null && destination != null) {
                final DroneSimulator sim = new DroneSimulator(drone, deliveryId, destination,
                        telemetrySink, droneSpeedUnitsPerSecond * 0.5);
                simulators.put(deliveryId, sim);
                sim.start();
            }
        });
    }

    @Override
    public void completeDelivery(final String deliveryId) {
        if (deliveryId == null) {
            return;
        }
        final String droneId = deliveryToDrone.get(deliveryId);
        if (droneId != null) {
            drones.findById(DroneId.of(droneId)).ifPresent(drone -> {
                drone.becomeAvailable();
                drones.save(drone);
            });
        }
        simulators.remove(deliveryId);
        deliveryToDrone.remove(deliveryId);
        deliveryDestination.remove(deliveryId);
    }

    @Override
    public List<FleetViews.FleetDroneView> fleetMonitoringView() {
        return drones.findAll().stream()
                .map(d -> new FleetViews.FleetDroneView(
                        d.getId().value(),
                        d.getStatus().name(),
                        d.getPosition().coordinates().latitude(),
                        d.getPosition().coordinates().longitude(),
                        d.isCarryingPackage()))
                .collect(Collectors.toList());
    }
}