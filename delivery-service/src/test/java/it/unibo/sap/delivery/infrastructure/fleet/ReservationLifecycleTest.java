package it.unibo.sap.delivery.infrastructure.fleet;

import it.unibo.sap.delivery.application.fleet.FleetAssignmentResult;
import it.unibo.sap.delivery.application.fleet.FleetFeasibilityRequest;
import it.unibo.sap.delivery.application.fleet.FleetReservationResult;
import it.unibo.sap.delivery.domain.fleet.Drone;
import it.unibo.sap.delivery.domain.fleet.DroneId;
import it.unibo.sap.delivery.domain.fleet.DroneStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression tests for the reservation lifecycle of the Fleet adapter.
 *
 * <p>They reproduce the real user flow that left a drone stuck in RESERVED: a scheduled delivery
 * completes but its reservation lingers as a phantom, so cancelling a later reservation on the same
 * drone cannot bring it back to AVAILABLE. The domain invariant under test is: the drone status must
 * always reflect the content of its reservations — AVAILABLE when none remain, RESERVED otherwise.
 */
class ReservationLifecycleTest {

    private static final double SPEED_UNITS_PER_SECOND = 0.01;
    private static final double WEIGHT_ONLY_BIG_DRONE = 7.0;
    private static final double WEIGHT_ANY_DRONE = 2.0;

    private InMemoryDroneRepository drones;
    private FleetModule fleet;

    @BeforeEach
    void setUp() {
        drones = new InMemoryDroneRepository();
        FleetSeeder.seed(drones);
        fleet = new FleetModule(drones, SPEED_UNITS_PER_SECOND);
    }

    private static FleetFeasibilityRequest request(final String deliveryId, final double weightKg) {
        return new FleetFeasibilityRequest(deliveryId, weightKg, 44.49, 11.34, 44.50, 11.35, 60);
    }

    private Drone drone(final String droneId) {
        return drones.findById(DroneId.of(droneId)).orElseThrow();
    }

    @Test
    void noGhostReservationAfterCompletion() {
        final LocalDateTime slot = LocalDateTime.now().plusDays(1);
        final FleetReservationResult reserved =
                fleet.reserveDroneForSlot(request("A", WEIGHT_ONLY_BIG_DRONE), slot);
        assertTrue(reserved.reserved());
        final String droneId = reserved.droneId();
        assertEquals(DroneStatus.RESERVED, drone(droneId).getStatus());

        fleet.completeDelivery("A");

        assertEquals(0, drone(droneId).reservationCount(),
                "the completed delivery must not leave a phantom reservation");
        assertEquals(DroneStatus.AVAILABLE, drone(droneId).getStatus());
    }

    @Test
    void cancelAfterAPreviousDeliveryCompletedFreesTheDrone() {
        final LocalDateTime slotA = LocalDateTime.now().plusDays(1);
        final FleetReservationResult first =
                fleet.reserveDroneForSlot(request("A", WEIGHT_ONLY_BIG_DRONE), slotA);
        final String droneId = first.droneId();
        fleet.completeDelivery("A");

        final LocalDateTime slotB = LocalDateTime.now().plusDays(2);
        final FleetReservationResult second =
                fleet.reserveDroneForSlot(request("B", WEIGHT_ONLY_BIG_DRONE), slotB);
        assertEquals(droneId, second.droneId(), "the 7 kg package must reuse the single big drone");
        assertEquals(DroneStatus.RESERVED, drone(droneId).getStatus());

        fleet.releaseReservation(droneId, "B");

        assertEquals(0, drone(droneId).reservationCount());
        assertEquals(DroneStatus.AVAILABLE, drone(droneId).getStatus(),
                "after cancelling the only pending reservation the drone must be AVAILABLE again");
    }

    @Test
    void scheduledReservationsAreSpreadAcrossDrones() {
        final FleetReservationResult first =
                fleet.reserveDroneForSlot(request("A", WEIGHT_ANY_DRONE), LocalDateTime.now().plusDays(1));
        final FleetReservationResult second =
                fleet.reserveDroneForSlot(request("B", WEIGHT_ANY_DRONE), LocalDateTime.now().plusDays(2));

        assertTrue(first.reserved());
        assertTrue(second.reserved());
        assertNotEquals(first.droneId(), second.droneId(),
                "two reservations with several eligible drones must be balanced across distinct drones");
    }

    @Test
    void outOfServiceDroneIsNotSelectedForReservation() {
        final Drone broken = drone("DRN-1");
        broken.goOutOfService();
        drones.save(broken);

        final FleetReservationResult reserved =
                fleet.reserveDroneForSlot(request("A", WEIGHT_ANY_DRONE), LocalDateTime.now().plusDays(1));

        assertTrue(reserved.reserved());
        assertNotEquals("DRN-1", reserved.droneId(),
                "an out-of-service drone must never receive a scheduled reservation");
    }

    @Test
    void immediateAssignmentIsUnchangedAndCreatesNoReservation() {
        final FleetAssignmentResult assigned = fleet.assignNearestDrone(request("IMM", WEIGHT_ANY_DRONE));

        assertTrue(assigned.assigned());
        final String droneId = assigned.droneId();
        assertEquals(DroneStatus.ASSIGNED, drone(droneId).getStatus());
        assertEquals(0, drone(droneId).reservationCount(),
                "an immediate assignment must not register a slot reservation");
    }
}
