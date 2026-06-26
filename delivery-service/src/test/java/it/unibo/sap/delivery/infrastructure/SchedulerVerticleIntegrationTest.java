package it.unibo.sap.delivery.infrastructure;

import io.vertx.core.Vertx;
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
import it.unibo.sap.delivery.domain.deliveries.Delivery;
import it.unibo.sap.delivery.domain.deliveries.DeliveryId;
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

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Integration test of the {@link VertxSchedulerVerticle} timer. Deploys the verticle in-process with a
 * short tick, creates a scheduled delivery whose slot is a couple of seconds in the future, and verifies
 * that the periodic timer (NOT a manual call to {@code assignDueScheduledDeliveries}) flips the delivery
 * to IN_PROGRESS once the slot falls due. Uses {@link VertxTestContext} — no fixed sleeps.
 */
@ExtendWith(VertxExtension.class)
class SchedulerVerticleIntegrationTest {

    private static final double DRONE_SPEED_UNITS_PER_SECOND = 0.01;
    private static final long SHORT_TICK_MILLIS = 150;

    @Test
    @Timeout(value = 20, timeUnit = TimeUnit.SECONDS)
    void scheduledDeliveryIsAssignedByTheTimerWithoutManualTrigger(final Vertx vertx,
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

        vertx.deployVerticle(new VertxSchedulerVerticle(deliveryService, SHORT_TICK_MILLIS))
                .onFailure(testContext::failNow)
                .onSuccess(deploymentId -> vertx.setPeriodic(50, pollId -> {
                    final DeliveryStatus status = deliveryRepository.findById(DeliveryId.of(deliveryId))
                            .map(Delivery::getStatus)
                            .orElse(null);
                    if (status == DeliveryStatus.IN_PROGRESS) {
                        vertx.cancelTimer(pollId);
                        testContext.completeNow();
                    }
                }));
    }
}
