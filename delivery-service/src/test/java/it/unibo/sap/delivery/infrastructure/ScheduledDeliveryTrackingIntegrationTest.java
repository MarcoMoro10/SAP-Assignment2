package it.unibo.sap.delivery.infrastructure;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.Checkpoint;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import it.unibo.sap.delivery.application.CreateDeliveryCommand;
import it.unibo.sap.delivery.application.CreateDeliveryResult;
import it.unibo.sap.delivery.application.DeliveryService;
import it.unibo.sap.delivery.application.DeliveryServiceImpl;
import it.unibo.sap.delivery.application.DroneEventHandler;
import it.unibo.sap.delivery.application.GeocodingPort;
import it.unibo.sap.delivery.application.TrackingSessionEventObserver;
import it.unibo.sap.delivery.application.TrackingSessionRegistry;
import it.unibo.sap.delivery.domain.deliveries.DeliveryStatus;
import it.unibo.sap.delivery.infrastructure.fleet.DroneEventHandlerSink;
import it.unibo.sap.delivery.infrastructure.fleet.FleetModule;
import it.unibo.sap.delivery.infrastructure.fleet.FleetSeeder;
import it.unibo.sap.delivery.infrastructure.fleet.InMemoryDroneRepository;
import it.unibo.sap.delivery.support.InMemoryDeliveryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Integration test of the scheduled-delivery + tracking pipeline driven by the real timer. A delivery is
 * scheduled a couple of seconds in the future and a tracking session is opened on it; the
 * {@link VertxSchedulerVerticle} timer (short tick, NO manual trigger) flips it to IN_PROGRESS, the drone
 * simulator starts moving and the in-process telemetry chain
 * ({@code FleetModule} → {@code DroneEventHandlerSink} → {@code DroneEventHandler}) publishes tracking
 * frames on the event bus at {@code TRACKING_ADDRESS_PREFIX + deliveryId}. The test asserts that the
 * constant-speed movement emits MORE than one IN_PROGRESS frame (each carrying a position) before the
 * delivery reaches DELIVERED — i.e. the drone cannot teleport to destination. Uses
 * {@link VertxTestContext} — no fixed sleeps.
 */
@ExtendWith(VertxExtension.class)
class ScheduledDeliveryTrackingIntegrationTest {

    private static final double DRONE_SPEED_UNITS_PER_SECOND = 0.01;
    private static final long SHORT_TICK_MILLIS = 150;

    @Test
    @Timeout(value = 20, timeUnit = TimeUnit.SECONDS)
    void scheduledDeliveryStartedByTheTimerPushesMultipleInProgressFramesBeforeDelivered(final Vertx vertx,
                                                                                         final VertxTestContext testContext) {
        final InMemoryDeliveryRepository deliveryRepository = new InMemoryDeliveryRepository();
        final TrackingSessionRegistry trackingRegistry = new InMemoryTrackingSessionRegistry();
        final GeocodingPort geocoding = new GeocodingService();
        final TrackingSessionEventObserver trackingObserver =
                new VertxTrackingSessionEventObserver(vertx.eventBus());

        final InMemoryDroneRepository droneRepository = new InMemoryDroneRepository();
        final FleetModule fleetModule = new FleetModule(droneRepository, DRONE_SPEED_UNITS_PER_SECOND);
        final DeliveryService deliveryService = new DeliveryServiceImpl(
                deliveryRepository, fleetModule, geocoding, trackingRegistry);
        final DroneEventHandler droneEventHandler = new DroneEventHandler(
                deliveryRepository, trackingRegistry, trackingObserver,
                fleetModule, DRONE_SPEED_UNITS_PER_SECOND);
        fleetModule.setTelemetrySink(new DroneEventHandlerSink(droneEventHandler));
        FleetSeeder.seed(droneRepository);

        final LocalDateTime slot = LocalDateTime.now().plusSeconds(2);
        final CreateDeliveryResult created = deliveryService.createDelivery(new CreateDeliveryCommand(
                "user-1", 2.0, "via Emilia", 9, "via Veneto", 5, false, slot, 60));
        assertEquals(DeliveryStatus.SCHEDULED.name(), created.status());
        final String deliveryId = created.deliveryId();

        deliveryService.startTracking(deliveryId, "user-1");

        final AtomicInteger inProgressFrames = new AtomicInteger(0);
        final Checkpoint multipleFrames = testContext.laxCheckpoint();
        vertx.eventBus().consumer(
                VertxTrackingSessionEventObserver.TRACKING_ADDRESS_PREFIX + deliveryId, msg -> {
                    final JsonObject frame = (JsonObject) msg.body();
                    final String status = frame.getString("status");
                    if (DeliveryStatus.DELIVERED.name().equals(status)) {
                        if (inProgressFrames.get() <= 1) {
                            testContext.failNow("delivery reached DELIVERED after only "
                                    + inProgressFrames.get() + " IN_PROGRESS frame(s): the drone teleported");
                        }
                        return;
                    }
                    if (DeliveryStatus.IN_PROGRESS.name().equals(status)) {
                        testContext.verify(() -> {
                            final JsonObject position = frame.getJsonObject("position");
                            assertNotNull(position, "an IN_PROGRESS frame must carry a position");
                            assertNotNull(position.getDouble("latitude"), "position must have a latitude");
                            assertNotNull(position.getDouble("longitude"), "position must have a longitude");
                        });
                        if (inProgressFrames.incrementAndGet() >= 2) {
                            multipleFrames.flag();
                        }
                    }
                });

        vertx.deployVerticle(new VertxSchedulerVerticle(deliveryService, SHORT_TICK_MILLIS))
                .onFailure(testContext::failNow);
    }
}
