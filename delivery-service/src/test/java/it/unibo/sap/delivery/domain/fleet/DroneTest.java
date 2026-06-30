package it.unibo.sap.delivery.domain.fleet;

import it.unibo.sap.delivery.domain.fleet.events.DroneArrived;
import it.unibo.sap.delivery.domain.fleet.events.DroneOutOfService;
import it.unibo.sap.delivery.domain.fleet.events.DroneReserved;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit (sociable) tests of the {@link Drone} aggregate of the Fleet context: initial state,
 * payload check, the reservation -> assignment -> delivery -> arrival lifecycle, and the
 * slot/availability guards. Until now Fleet was only covered indirectly through FakeFleetPort.
 */
class DroneTest {

    private static Drone availableDrone() {
        return Drone.create(DroneId.of("DRN-1"),
                Position.at(new Coordinates(44.49, 11.34)), new PayloadCapacity(5.0));
    }

    @Test
    void newDroneIsAvailable() {
        final Drone drone = availableDrone();
        assertEquals(DroneStatus.AVAILABLE, drone.getStatus());
        assertTrue(drone.isAvailable());
        assertFalse(drone.isCarryingPackage());
    }

    @Test
    void canCarryRespectsPayloadCapacity() {
        final Drone drone = availableDrone();
        assertTrue(drone.canCarry(5.0), "exactly at capacity should be carriable");
        assertTrue(drone.canCarry(4.9));
        assertFalse(drone.canCarry(5.1), "above capacity should not be carriable");
    }

    @Test
    void reservationToAssignmentToDeliveryToArrivalLifecycle() {
        final Drone drone = availableDrone();
        final LocalDateTime slot = LocalDateTime.now().plusDays(1);

        drone.reserveSlot("DLV-1", slot);
        assertEquals(DroneStatus.RESERVED, drone.getStatus());
        assertTrue(drone.getDomainEvents().stream().anyMatch(e -> e instanceof DroneReserved));

        drone.assign("DLV-1");
        assertEquals(DroneStatus.ASSIGNED, drone.getStatus());
        assertEquals("DLV-1", drone.getAssignedDeliveryId());

        drone.startDelivery();
        assertEquals(DroneStatus.IN_DELIVERY, drone.getStatus());
        assertTrue(drone.isCarryingPackage());

        drone.arrived();
        assertEquals(DroneStatus.ARRIVED, drone.getStatus());
        assertTrue(drone.getDomainEvents().stream().anyMatch(e -> e instanceof DroneArrived));
    }

    @Test
    void reservingAnAlreadyReservedSlotThrows() {
        final Drone drone = availableDrone();
        final LocalDateTime slot = LocalDateTime.now().plusDays(1);
        drone.reserveSlot("DLV-1", slot);

        final IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> drone.reserveSlot("DLV-2", slot));
        assertEquals("No drone available for the requested time", ex.getMessage());
    }

    @Test
    void releaseReservationBringsDroneBackToAvailable() {
        final Drone drone = availableDrone();
        final LocalDateTime slot = LocalDateTime.now().plusDays(1);
        drone.reserveSlot("DLV-1", slot);

        drone.releaseReservation("DLV-1");

        assertEquals(DroneStatus.AVAILABLE, drone.getStatus());
        assertTrue(drone.isAvailable());
    }

    @Test
    void releaseReservationFreesDroneEvenWhenSlotPrecisionDiffers() {
        final Drone drone = availableDrone();
        final LocalDateTime highPrecisionSlot = LocalDateTime.of(2026, 1, 1, 10, 0, 0, 123_000_000);
        final LocalDateTime recreatedSlot = LocalDateTime.of(2026, 1, 1, 10, 0);
        assertFalse(highPrecisionSlot.equals(recreatedSlot),
                "the reserved slot must be irrecoverable for this test to be meaningful");

        drone.reserveSlot("DLV-1", highPrecisionSlot);
        assertEquals(DroneStatus.RESERVED, drone.getStatus());

        drone.releaseReservation("DLV-1");

        assertEquals(DroneStatus.AVAILABLE, drone.getStatus());
        assertTrue(drone.isAvailable());
    }

    @Test
    void goOutOfServiceMovesToOutOfService() {
        final Drone drone = availableDrone();
        drone.goOutOfService();
        assertEquals(DroneStatus.OUT_OF_SERVICE, drone.getStatus());
        assertTrue(drone.getDomainEvents().stream().anyMatch(e -> e instanceof DroneOutOfService));
    }

    @Test
    void goOutOfServiceKeepsStatusEvenAfterReservationRelease() {
        final Drone drone = availableDrone();
        final LocalDateTime slot = LocalDateTime.now().plusDays(1);
        drone.reserveSlot("DLV-1", slot);

        drone.goOutOfService();
        drone.releaseReservation("DLV-1");

        assertEquals(DroneStatus.OUT_OF_SERVICE, drone.getStatus(),
                "releasing a reservation must not resurrect an out-of-service drone");
    }

    @Test
    void isSlotFreeReflectsReservedSlots() {
        final Drone drone = availableDrone();
        final LocalDateTime taken = LocalDateTime.now().plusDays(1);
        final LocalDateTime free = LocalDateTime.now().plusDays(2);

        assertTrue(drone.isSlotFree(taken));
        drone.reserveSlot("DLV-1", taken);

        assertFalse(drone.isSlotFree(taken), "a reserved slot must not be free");
        assertTrue(drone.isSlotFree(free), "a different slot must still be free");
    }
}
