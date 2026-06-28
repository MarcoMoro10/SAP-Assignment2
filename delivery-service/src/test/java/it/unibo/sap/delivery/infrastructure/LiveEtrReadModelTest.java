package it.unibo.sap.delivery.infrastructure;

import it.unibo.sap.delivery.application.CreateDeliveryCommand;
import it.unibo.sap.delivery.application.CreateDeliveryResult;
import it.unibo.sap.delivery.application.DeliveryService;
import it.unibo.sap.delivery.application.DeliveryServiceEventObserver;
import it.unibo.sap.delivery.application.DeliveryServiceImpl;
import it.unibo.sap.delivery.application.DroneEventHandler;
import it.unibo.sap.delivery.application.EventStore;
import it.unibo.sap.delivery.application.TrackingSessionRegistry;
import it.unibo.sap.delivery.domain.deliveries.DeliveryStatus;
import it.unibo.sap.delivery.domain.deliveries.DeliveryTrackingView;
import it.unibo.sap.delivery.support.FakeFleetPort;
import it.unibo.sap.delivery.support.FakeGeocodingPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LiveEtrReadModelTest {

    @TempDir
    Path tempDir;

    @Test
    void getDeliveryReturnsLiveEtrFromTheReadModel() {
        final EventStore eventStore = new FileBasedEventStore(tempDir.resolve("events.json").toString());
        final EventSourcedDeliveryRepository repository = new EventSourcedDeliveryRepository(eventStore);
        final TrackingSessionRegistry tracking = new InMemoryTrackingSessionRegistry();
        final FakeFleetPort fleet = new FakeFleetPort();
        final InMemoryEstimatedTimeView estimatedTimeView = new InMemoryEstimatedTimeView();

        final DeliveryService service = new DeliveryServiceImpl(
                repository, fleet, new FakeGeocodingPort(), tracking,
                DeliveryServiceEventObserver.NO_OP, estimatedTimeView);
        final DroneEventHandler handler = new DroneEventHandler(
                repository, tracking, (d, s, la, lo, e) -> { }, fleet, 0.01,
                DeliveryServiceEventObserver.NO_OP, estimatedTimeView);

        final CreateDeliveryResult created = service.createDelivery(new CreateDeliveryCommand(
                "user-1", 2.0, "via Emilia", 9, "via Veneto", 5, true, null, 60));
        final String deliveryId = created.deliveryId();
        assertEquals(DeliveryStatus.IN_PROGRESS.name(), created.status());

        assertEquals(0L, service.getDelivery(deliveryId, "user-1").orElseThrow().estimatedTimeRemainingSeconds());

        handler.onDronePositionUpdated(deliveryId, 44.40, 11.20);
        handler.onDronePositionUpdated(deliveryId, 44.42, 11.24);

        final DeliveryTrackingView tracked = service.getDelivery(deliveryId, "user-1").orElseThrow();
        assertEquals(DeliveryStatus.IN_PROGRESS, tracked.status());
        assertTrue(tracked.estimatedTimeRemainingSeconds() > 0,
                "getDelivery must expose the live ETR from the read-model, not the replayed zero");

        handler.onDroneArrived(deliveryId, 44.50, 11.34);

        final Optional<DeliveryTrackingView> afterArrival = service.getDelivery(deliveryId, "user-1");
        assertTrue(afterArrival.isPresent());
        assertEquals(DeliveryStatus.DELIVERED, afterArrival.get().status());
        assertEquals(0L, afterArrival.get().estimatedTimeRemainingSeconds());
    }
}
