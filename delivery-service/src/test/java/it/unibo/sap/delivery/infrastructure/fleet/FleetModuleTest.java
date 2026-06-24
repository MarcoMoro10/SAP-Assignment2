package it.unibo.sap.delivery.infrastructure.fleet;

import it.unibo.sap.delivery.application.fleet.FleetAssignmentResult;
import it.unibo.sap.delivery.application.fleet.FleetFeasibilityRequest;
import it.unibo.sap.delivery.domain.fleet.Coordinates;
import it.unibo.sap.delivery.domain.fleet.Drone;
import it.unibo.sap.delivery.domain.fleet.DroneId;
import it.unibo.sap.delivery.domain.fleet.DroneStatus;
import it.unibo.sap.delivery.domain.fleet.PayloadCapacity;
import it.unibo.sap.delivery.domain.fleet.Position;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration-ish unit test of {@link FleetModule} (the Fleet adapter) against a real
 * {@link InMemoryDroneRepository}: it must pick the nearest eligible (available, can-carry) drone
 * and reject when no drone can carry the package.
 */
class FleetModuleTest {

    private InMemoryDroneRepository drones;
    private FleetModule fleet;

    @BeforeEach
    void setUp() {
        drones = new InMemoryDroneRepository();
        drones.save(Drone.create(DroneId.of("DRN-NEAR"),
                Position.at(new Coordinates(44.49, 11.34)), new PayloadCapacity(5.0)));
        drones.save(Drone.create(DroneId.of("DRN-FAR"),
                Position.at(new Coordinates(44.55, 11.40)), new PayloadCapacity(5.0)));
        fleet = new FleetModule(drones, 0.01);
    }

    private static FleetFeasibilityRequest pickupNear(final double weightKg) {
        return new FleetFeasibilityRequest("DLV-1", weightKg, 44.49, 11.34, 44.50, 11.35, 0);
    }

    @Test
    void assignsTheNearestEligibleDrone() {
        final FleetAssignmentResult result = fleet.assignNearestDrone(pickupNear(2.0));

        assertTrue(result.assigned());
        assertEquals("DRN-NEAR", result.droneId());
        assertEquals(DroneStatus.ASSIGNED,
                drones.findById(DroneId.of("DRN-NEAR")).orElseThrow().getStatus());
    }

    @Test
    void skipsBusyDronesAndPicksTheNextNearestAvailable() {
        final Drone near = drones.findById(DroneId.of("DRN-NEAR")).orElseThrow();
        near.goOutOfService();
        drones.save(near);

        final FleetAssignmentResult result = fleet.assignNearestDrone(pickupNear(2.0));

        assertTrue(result.assigned());
        assertEquals("DRN-FAR", result.droneId());
    }

    @Test
    void rejectsWhenNoDroneCanCarryThePackage() {
        final FleetAssignmentResult result = fleet.assignNearestDrone(pickupNear(99.0));

        assertFalse(result.assigned());
        assertEquals("No drone can carry this package", result.rejectionReason());
    }
}
