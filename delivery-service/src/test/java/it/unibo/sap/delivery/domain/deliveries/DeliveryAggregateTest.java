package it.unibo.sap.delivery.domain.deliveries;

import it.unibo.sap.delivery.domain.deliveries.events.DeliveryBegun;
import it.unibo.sap.delivery.domain.deliveries.events.DeliveryRequestCreated;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit (sociable) tests of the {@link Delivery} aggregate: the happy-path state machine and the
 * guards that protect illegal {@link DeliveryStatus} transitions.
 */
class DeliveryAggregateTest {

    private static DeliveryRequest immediateRequest() {
        final Package parcel = new Package(2.0);
        final Location pickup = Location.of(new Coordinates(44.49, 11.34), "via Emilia, 9");
        final Location destination = Location.of(new Coordinates(44.50, 11.35), "via Veneto, 5");
        return new DeliveryRequest(parcel, pickup, destination, RequestedDateTime.immediateRequest(), null);
    }

    private static Delivery newRequested() {
        return Delivery.createRequest(SenderId.of("user-1"), immediateRequest());
    }

    @Test
    void createRequestStartsInRequestedAndRaisesEvent() {
        final Delivery delivery = newRequested();

        assertEquals(DeliveryStatus.REQUESTED, delivery.getStatus());
        assertTrue(delivery.isOwnedBy(SenderId.of("user-1")));
        assertFalse(delivery.isOwnedBy(SenderId.of("someone-else")));
        assertEquals(1, delivery.getDomainEvents().size());
        assertTrue(delivery.getDomainEvents().get(0) instanceof DeliveryRequestCreated);
    }

    @Test
    void happyPathImmediateFlowReachesInProgress() {
        final Delivery delivery = newRequested();

        delivery.validationPassed();
        assertEquals(DeliveryStatus.VALIDATED, delivery.getStatus());

        delivery.assignDrone("DRN-1");
        assertEquals(DeliveryStatus.ASSIGNED, delivery.getStatus());
        assertEquals("DRN-1", delivery.getAssignedDroneId());

        delivery.begin();
        assertEquals(DeliveryStatus.IN_PROGRESS, delivery.getStatus());
        assertTrue(delivery.getDomainEvents().stream().anyMatch(e -> e instanceof DeliveryBegun));

        delivery.updateEstimatedTime(EstimatedTimeRemaining.ofSeconds(120));
        assertEquals(120, delivery.getEstimatedTimeRemaining().toSeconds());

        delivery.complete();
        assertEquals(DeliveryStatus.DELIVERED, delivery.getStatus());
        assertEquals(0, delivery.getEstimatedTimeRemaining().toSeconds());
    }

    @Test
    void scheduledFlowReservesAndCanBeAssigned() {
        final Delivery delivery = newRequested();
        delivery.validationPassed();
        delivery.schedule();
        assertEquals(DeliveryStatus.SCHEDULED, delivery.getStatus());

        delivery.reserveDrone("DRN-2");
        assertEquals("DRN-2", delivery.getAssignedDroneId());
        assertEquals(DeliveryStatus.SCHEDULED, delivery.getStatus());

        delivery.assignDrone("DRN-2");
        assertEquals(DeliveryStatus.ASSIGNED, delivery.getStatus());
    }

    @Test
    void cannotBeginBeforeAssignment() {
        final Delivery delivery = newRequested();
        delivery.validationPassed();
        assertThrows(IllegalStateException.class, delivery::begin);
    }

    @Test
    void cannotCancelOnceInFlight() {
        final Delivery delivery = newRequested();
        delivery.validationPassed();
        delivery.assignDrone("DRN-1");
        delivery.begin();
        assertThrows(IllegalStateException.class, delivery::cancel);
    }

    @Test
    void rejectMovesToRejected() {
        final Delivery delivery = newRequested();
        delivery.reject("No drone available");
        assertEquals(DeliveryStatus.REJECTED, delivery.getStatus());
    }

    @Test
    void cancelScheduledMovesToCancelled() {
        final Delivery delivery = newRequested();
        delivery.validationPassed();
        delivery.schedule();
        delivery.cancel();
        assertEquals(DeliveryStatus.CANCELLED, delivery.getStatus());
    }

    @Test
    void estimatedTimeRemainingDefaultsToZeroWhenNeverSet() {
        final Delivery delivery = newRequested();
        assertNull(delivery.getAssignedDroneId());
        assertEquals(0L, delivery.getEstimatedTimeRemaining().toSeconds());
    }
}
