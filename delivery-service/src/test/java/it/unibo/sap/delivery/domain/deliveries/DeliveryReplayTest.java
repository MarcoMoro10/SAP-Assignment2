package it.unibo.sap.delivery.domain.deliveries;

import it.unibo.sap.common.ddd.DomainEvent;
import it.unibo.sap.delivery.domain.deliveries.events.DeliveryBegun;
import it.unibo.sap.delivery.domain.deliveries.events.DeliveryRequestCreated;
import it.unibo.sap.delivery.domain.deliveries.events.DeliveryScheduled;
import it.unibo.sap.delivery.domain.deliveries.events.DroneAssigned;
import it.unibo.sap.delivery.domain.deliveries.events.DroneReserved;
import it.unibo.sap.delivery.domain.deliveries.events.EstimatedTimeUpdated;
import it.unibo.sap.delivery.domain.deliveries.events.ValidationDeliveryPassed;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Event-sourcing unit test of the {@link Delivery} aggregate: drives the aggregate through its state
 * machine, captures the recorded domain events, then replays them into a fresh aggregate via
 * {@link Delivery#apply} and asserts the rebuilt state is identical to the original.
 */
class DeliveryReplayTest {

    private static DeliveryRequest immediateRequest() {
        final Package parcel = new Package(2.0);
        final Location pickup = Location.of(new Coordinates(44.49, 11.34), "via Emilia, 9");
        final Location destination = Location.of(new Coordinates(44.50, 11.35), "via Veneto, 5");
        return new DeliveryRequest(parcel, pickup, destination, RequestedDateTime.immediateRequest(), null);
    }

    private static Delivery replay(final List<DomainEvent> events) {
        final Delivery rebuilt = new Delivery();
        events.forEach(rebuilt::apply);
        return rebuilt;
    }

    @Test
    void immediateFlowRecordsTheExpectedSequenceAndReplayRebuildsTheSameState() {
        final Delivery delivery = Delivery.createRequest(SenderId.of("user-1"), immediateRequest());
        delivery.validationPassed();
        delivery.assignDrone("DRN-1");
        delivery.begin();
        delivery.updateEstimatedTime(EstimatedTimeRemaining.ofSeconds(90));

        final List<DomainEvent> events = new ArrayList<>(delivery.getDomainEvents());
        final List<Class<?>> sequence = events.stream().<Class<?>>map(DomainEvent::getClass).toList();
        assertEquals(List.of(
                DeliveryRequestCreated.class,
                ValidationDeliveryPassed.class,
                DroneAssigned.class,
                DeliveryBegun.class,
                EstimatedTimeUpdated.class), sequence);

        final Delivery rebuilt = replay(events);

        assertEquals(delivery.getId(), rebuilt.getId());
        assertEquals(delivery.getSenderId(), rebuilt.getSenderId());
        assertEquals(delivery.getRequest(), rebuilt.getRequest());
        assertEquals(DeliveryStatus.IN_PROGRESS, rebuilt.getStatus());
        assertEquals("DRN-1", rebuilt.getAssignedDroneId());
        assertEquals(90, rebuilt.getEstimatedTimeRemaining().toSeconds());
    }

    @Test
    void completedFlowIsRebuiltAsDeliveredWithZeroEtr() {
        final Delivery delivery = Delivery.createRequest(SenderId.of("user-1"), immediateRequest());
        delivery.validationPassed();
        delivery.assignDrone("DRN-1");
        delivery.begin();
        delivery.updateEstimatedTime(EstimatedTimeRemaining.ofSeconds(90));
        delivery.complete();

        final Delivery rebuilt = replay(new ArrayList<>(delivery.getDomainEvents()));

        assertEquals(DeliveryStatus.DELIVERED, rebuilt.getStatus());
        assertEquals(0, rebuilt.getEstimatedTimeRemaining().toSeconds());
    }

    @Test
    void scheduledFlowWithReservationIsRebuiltFromEvents() {
        final Delivery delivery = Delivery.createRequest(SenderId.of("user-1"), immediateRequest());
        delivery.validationPassed();
        delivery.schedule();
        delivery.reserveDrone("DRN-2");

        final List<DomainEvent> events = new ArrayList<>(delivery.getDomainEvents());
        final List<Class<?>> sequence = events.stream().<Class<?>>map(DomainEvent::getClass).toList();
        assertEquals(List.of(
                DeliveryRequestCreated.class,
                ValidationDeliveryPassed.class,
                DeliveryScheduled.class,
                DroneReserved.class), sequence);

        final Delivery rebuilt = replay(events);

        assertEquals(DeliveryStatus.SCHEDULED, rebuilt.getStatus());
        assertEquals("DRN-2", rebuilt.getAssignedDroneId());
    }
}
