package it.unibo.sap.delivery.infrastructure;

import it.unibo.sap.delivery.application.DeliveryRecoveryService;
import it.unibo.sap.delivery.application.EventStore;
import it.unibo.sap.delivery.application.fleet.FleetViews;
import it.unibo.sap.delivery.domain.deliveries.Coordinates;
import it.unibo.sap.delivery.domain.deliveries.Delivery;
import it.unibo.sap.delivery.domain.deliveries.DeliveryId;
import it.unibo.sap.delivery.domain.deliveries.DeliveryRequest;
import it.unibo.sap.delivery.domain.deliveries.DeliveryStatus;
import it.unibo.sap.delivery.domain.deliveries.Location;
import it.unibo.sap.delivery.domain.deliveries.Package;
import it.unibo.sap.delivery.domain.deliveries.RequestedDateTime;
import it.unibo.sap.delivery.domain.deliveries.SenderId;
import it.unibo.sap.delivery.domain.fleet.DroneStatus;
import it.unibo.sap.delivery.infrastructure.fleet.DroneTelemetrySink;
import it.unibo.sap.delivery.infrastructure.fleet.FleetModule;
import it.unibo.sap.delivery.infrastructure.fleet.FleetSeeder;
import it.unibo.sap.delivery.infrastructure.fleet.InMemoryDroneRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Component/integration test of {@link DeliveryRecoveryService}: it seeds a real file-based event
 * store, then simulates a restart (a brand-new store reading that file, a freshly re-seeded
 * {@link FleetModule}) and runs the recovery, asserting the restart policy per delivery status.
 */
class DeliveryRecoveryServiceTest {

    private static final double DRONE_SPEED_UNITS_PER_SECOND = 0.01;
    private static final SenderId SENDER = SenderId.of("user-1");
    private static final DroneTelemetrySink NO_OP_SINK = new DroneTelemetrySink() {
        @Override public void onPositionUpdated(final String deliveryId, final double lat, final double lon) { }
        @Override public void onArrived(final String deliveryId, final double lat, final double lon) { }
    };

    @TempDir
    Path tempDir;

    private static DeliveryRequest immediateRequest() {
        return new DeliveryRequest(
                new Package(2.0),
                Location.of(new Coordinates(44.49, 11.34), "via Emilia, 9"),
                Location.of(new Coordinates(44.50, 11.35), "via Veneto, 5"),
                RequestedDateTime.immediateRequest(), null);
    }

    private static DeliveryRequest scheduledRequest() {
        return new DeliveryRequest(
                new Package(2.0),
                Location.of(new Coordinates(44.49, 11.34), "via Emilia, 9"),
                Location.of(new Coordinates(44.50, 11.35), "via Veneto, 5"),
                RequestedDateTime.scheduledAt(LocalDateTime.now().plusDays(1)), null);
    }

    private static Delivery inFlight() {
        final Delivery d = Delivery.createRequest(SENDER, immediateRequest());
        d.validationPassed();
        d.assignDrone("DRN-1");
        d.begin();
        return d;
    }

    private static Delivery scheduledAndReserved() {
        final Delivery d = Delivery.createRequest(SENDER, scheduledRequest());
        d.validationPassed();
        d.schedule();
        d.reserveDrone("DRN-1");
        return d;
    }

    private static Delivery delivered() {
        final Delivery d = inFlight();
        d.complete();
        return d;
    }

    private EventStore storeFromFile() {
        return new FileBasedEventStore(tempDir.resolve("delivery-events.json").toString());
    }

    /** A restart: a fresh store reading the same file, a freshly re-seeded fleet, and the recovery. */
    private record Restart(EventSourcedDeliveryRepository repository, FleetModule fleet) {
        void recover() {
            new DeliveryRecoveryService(repository, fleet).recover();
        }
    }

    private Restart restart() {
        final InMemoryDroneRepository drones = new InMemoryDroneRepository();
        FleetSeeder.seed(drones);
        final FleetModule fleet = new FleetModule(drones, DRONE_SPEED_UNITS_PER_SECOND);
        fleet.setTelemetrySink(NO_OP_SINK);
        return new Restart(new EventSourcedDeliveryRepository(storeFromFile()), fleet);
    }

    private String persist(final Delivery delivery) {
        new EventSourcedDeliveryRepository(storeFromFile()).save(delivery);
        return delivery.getId().value();
    }

    @Test
    void inProgressDeliveryRestartsFromScratchOnAFreshDrone() {
        final String id = persist(inFlight());

        final Restart restart = restart();
        restart.recover();

        assertEquals(DeliveryStatus.IN_PROGRESS,
                restart.repository().findById(DeliveryId.of(id)).orElseThrow().getStatus(),
                "an in-flight delivery stays active after recovery");
        assertEquals(1, carryingDrones(restart.fleet()),
                "exactly one freshly seeded drone is reassigned and flying");
    }

    @Test
    void scheduledDeliveryIsCancelledAndItsReservationReleased() {
        final String id = persist(scheduledAndReserved());

        final Restart restart = restart();
        restart.recover();

        assertEquals(DeliveryStatus.CANCELLED,
                restart.repository().findById(DeliveryId.of(id)).orElseThrow().getStatus(),
                "a scheduled/reserved delivery is cancelled on recovery");
        assertTrue(allDronesAvailable(restart.fleet()),
                "the released reservation leaves the whole fleet AVAILABLE");
    }

    @Test
    void terminalDeliveriesAreLeftUntouched() {
        final String id = persist(delivered());

        final Restart restart = restart();
        restart.recover();

        assertEquals(DeliveryStatus.DELIVERED,
                restart.repository().findById(DeliveryId.of(id)).orElseThrow().getStatus(),
                "a delivered delivery is not touched");
        assertTrue(allDronesAvailable(restart.fleet()),
                "recovery does not engage any drone for terminal deliveries");
    }

    @Test
    void recoveryIsIdempotentAcrossTwoConsecutiveRestarts() {
        final String id = persist(scheduledAndReserved());

        restart().recover();
        final int eventsAfterFirst = storeFromFile().load(id).size();

        // second restart: fresh store from the same file, fresh fleet — must not cancel twice
        restart().recover();
        final int eventsAfterSecond = storeFromFile().load(id).size();

        assertEquals(eventsAfterFirst, eventsAfterSecond,
                "a second restart must not append a second DeliveryCancelled");
        assertEquals(DeliveryStatus.CANCELLED,
                new EventSourcedDeliveryRepository(storeFromFile())
                        .findById(DeliveryId.of(id)).orElseThrow().getStatus());
    }

    private static long carryingDrones(final FleetModule fleet) {
        return fleet.fleetMonitoringView().stream()
                .filter(FleetViews.FleetDroneView::carryingPackage)
                .count();
    }

    private static boolean allDronesAvailable(final FleetModule fleet) {
        final List<FleetViews.FleetDroneView> view = fleet.fleetMonitoringView();
        return view.stream().allMatch(d -> DroneStatus.AVAILABLE.name().equals(d.status()));
    }
}
