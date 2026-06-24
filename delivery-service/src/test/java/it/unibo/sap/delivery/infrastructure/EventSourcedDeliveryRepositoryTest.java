package it.unibo.sap.delivery.infrastructure;

import it.unibo.sap.delivery.application.EventStore;
import it.unibo.sap.delivery.domain.deliveries.Coordinates;
import it.unibo.sap.delivery.domain.deliveries.Delivery;
import it.unibo.sap.delivery.domain.deliveries.DeliveryId;
import it.unibo.sap.delivery.domain.deliveries.DeliveryRequest;
import it.unibo.sap.delivery.domain.deliveries.DeliveryStatus;
import it.unibo.sap.delivery.domain.deliveries.EstimatedTimeRemaining;
import it.unibo.sap.delivery.domain.deliveries.Location;
import it.unibo.sap.delivery.domain.deliveries.Package;
import it.unibo.sap.delivery.domain.deliveries.RequestedDateTime;
import it.unibo.sap.delivery.domain.deliveries.SenderId;
import it.unibo.sap.delivery.domain.deliveries.events.EstimatedTimeUpdated;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Persistence/integration test of {@link FileBasedEventStore} + {@link EventSourcedDeliveryRepository}:
 * appends an aggregate's events to a real file, then rebuilds it from a brand-new store/repository
 * instance reading that file, proving the state is reconstructed purely by replaying the event stream.
 */
class EventSourcedDeliveryRepositoryTest {

    @TempDir
    Path tempDir;

    private static DeliveryRequest immediateRequest() {
        final Package parcel = new Package(2.0);
        final Location pickup = Location.of(new Coordinates(44.49, 11.34), "via Emilia, 9");
        final Location destination = Location.of(new Coordinates(44.50, 11.35), "via Veneto, 5");
        return new DeliveryRequest(parcel, pickup, destination, RequestedDateTime.immediateRequest(), null);
    }

    private static Delivery inFlightDelivery() {
        final Delivery delivery = Delivery.createRequest(SenderId.of("user-1"), immediateRequest());
        delivery.validationPassed();
        delivery.assignDrone("DRN-1");
        delivery.begin();
        return delivery;
    }

    @Test
    void appendsEventsAndRebuildsTheAggregateFromANewInstance() {
        final Path file = tempDir.resolve("delivery-events.json");
        final EventStore writeStore = new FileBasedEventStore(file.toString());
        final EventSourcedDeliveryRepository writeRepo = new EventSourcedDeliveryRepository(writeStore);

        final Delivery delivery = inFlightDelivery();
        final String deliveryId = delivery.getId().value();
        writeRepo.save(delivery);

        final EventStore readStore = new FileBasedEventStore(file.toString());
        final EventSourcedDeliveryRepository readRepo = new EventSourcedDeliveryRepository(readStore);

        final Optional<Delivery> rebuilt = readRepo.findById(DeliveryId.of(deliveryId));
        assertTrue(rebuilt.isPresent());
        assertEquals(DeliveryStatus.IN_PROGRESS, rebuilt.get().getStatus());
        assertEquals("DRN-1", rebuilt.get().getAssignedDroneId());
        assertEquals(SenderId.of("user-1"), rebuilt.get().getSenderId());
        assertEquals("via Emilia, 9", rebuilt.get().getRequest().pickupLocation().address());
        assertEquals(1, readRepo.findAll().size());
    }

    @Test
    void estimatedTimeUpdatedEventsAreNotPersisted() {
        final Path file = tempDir.resolve("delivery-events.json");
        final EventStore store = new FileBasedEventStore(file.toString());
        final EventSourcedDeliveryRepository repo = new EventSourcedDeliveryRepository(store);

        final Delivery delivery = inFlightDelivery();
        final String deliveryId = delivery.getId().value();
        repo.save(delivery);

        delivery.updateEstimatedTime(EstimatedTimeRemaining.ofSeconds(90));
        repo.save(delivery);

        final EventStore reloaded = new FileBasedEventStore(file.toString());
        assertEquals(4, reloaded.load(deliveryId).size(),
                "only the 4 lifecycle events must be stored, never the ETR updates");
        assertFalse(reloaded.load(deliveryId).stream().anyMatch(e -> e instanceof EstimatedTimeUpdated));
    }
}
