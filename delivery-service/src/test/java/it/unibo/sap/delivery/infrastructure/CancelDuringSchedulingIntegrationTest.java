package it.unibo.sap.delivery.infrastructure;

import io.vertx.core.Vertx;
import io.vertx.core.WorkerExecutor;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import it.unibo.sap.delivery.application.CreateDeliveryCommand;
import it.unibo.sap.delivery.application.CreateDeliveryResult;
import it.unibo.sap.delivery.application.DeliveryExceptions.CannotCancelInFlightException;
import it.unibo.sap.delivery.application.DeliveryService;
import it.unibo.sap.delivery.application.DeliveryServiceImpl;
import it.unibo.sap.delivery.application.DroneEventHandler;
import it.unibo.sap.delivery.application.GeocodingPort;
import it.unibo.sap.delivery.application.TrackingSessionEventObserver;
import it.unibo.sap.delivery.application.TrackingSessionRegistry;
import it.unibo.sap.delivery.application.fleet.FleetViews;
import it.unibo.sap.delivery.domain.deliveries.Delivery;
import it.unibo.sap.delivery.domain.deliveries.DeliveryId;
import it.unibo.sap.delivery.domain.deliveries.DeliveryStatus;
import it.unibo.sap.delivery.domain.fleet.DroneStatus;
import it.unibo.sap.delivery.infrastructure.fleet.DroneEventHandlerSink;
import it.unibo.sap.delivery.infrastructure.fleet.FleetModule;
import it.unibo.sap.delivery.infrastructure.fleet.FleetSeeder;
import it.unibo.sap.delivery.infrastructure.fleet.InMemoryDroneRepository;
import it.unibo.sap.delivery.support.InMemoryDeliveryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Regression test for the scheduler/cancel race.
 *
 * <p>Each iteration creates a SCHEDULED delivery whose slot is about to fall due, then has the sender
 * cancel it at the exact moment the periodic {@link VertxSchedulerVerticle} tick would assign the
 * reserved drone. The cancel is submitted to the SAME shared, single-threaded ordered worker pool
 * ({@link DeliveryServiceController#DOMAIN_COMMAND_EXECUTOR}) the production REST handlers use, and the
 * tick runs on the real verticle — so the test reproduces the exact production topology where the two
 * state-mutating commands collide.
 */
@ExtendWith(VertxExtension.class)
class CancelDuringSchedulingIntegrationTest {

    private static final double DRONE_SPEED_UNITS_PER_SECOND = 0.01;
    private static final long SHORT_TICK_MILLIS = 40;
    private static final long SLOT_DELAY_MILLIS = 120;
    private static final int ITERATIONS = 80;

    @Test
    @Timeout(value = 90, timeUnit = TimeUnit.SECONDS)
    void cancelRacingTheSchedulerNeverLeavesADroneStuck(final Vertx vertx,
                                                        final VertxTestContext testContext) {
        final WorkerExecutor commands =
                vertx.createSharedWorkerExecutor(DeliveryServiceController.DOMAIN_COMMAND_EXECUTOR, 1);
        runIteration(ITERATIONS, vertx, commands, testContext);
    }

    private void runIteration(final int remaining, final Vertx vertx, final WorkerExecutor commands,
                              final VertxTestContext testContext) {
        if (remaining == 0) {
            testContext.completeNow();
            return;
        }
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

        final LocalDateTime slot = LocalDateTime.now().plusNanos(SLOT_DELAY_MILLIS * 1_000_000L);
        final CreateDeliveryResult created = deliveryService.createDelivery(new CreateDeliveryCommand(
                "user-1", 2.0, "via Emilia", 9, "via Veneto", 5, false, slot, 60));
        if (!DeliveryStatus.SCHEDULED.name().equals(created.status())) {
            testContext.failNow("expected SCHEDULED but got " + created.status());
            return;
        }
        final String deliveryId = created.deliveryId();
        final String reservedDroneId = created.assignedDroneId();

        final AtomicReference<String> cancelOutcome = new AtomicReference<>();

        vertx.deployVerticle(new VertxSchedulerVerticle(deliveryService, SHORT_TICK_MILLIS))
                .onFailure(testContext::failNow)
                .onSuccess(depId -> {
                    vertx.setTimer(SLOT_DELAY_MILLIS, t -> commands.<String>executeBlocking(() -> {
                        try {
                            deliveryService.cancelDelivery(deliveryId, "user-1");
                            return "CANCELLED";
                        } catch (final CannotCancelInFlightException e) {
                            return "REJECTED";
                        }
                    }, true).onComplete(ar ->
                            cancelOutcome.set(ar.succeeded() ? ar.result() : "ERROR")));

                    // Poll until both the cancel has completed and the delivery has settled.
                    vertx.setPeriodic(20, pollId -> {
                        final String outcome = cancelOutcome.get();
                        if (outcome == null) {
                            return;
                        }
                        final DeliveryStatus status = deliveryRepository
                                .findById(DeliveryId.of(deliveryId))
                                .map(Delivery::getStatus)
                                .orElse(null);
                        if (status != DeliveryStatus.CANCELLED && status != DeliveryStatus.IN_PROGRESS) {
                            return;
                        }
                        vertx.cancelTimer(pollId);
                        try {
                            assertConsistentInvariant(outcome, status, deliveryService, reservedDroneId);
                        } catch (final AssertionError e) {
                            testContext.failNow(e);
                            return;
                        }
                        vertx.undeploy(depId).onComplete(ignored ->
                                runIteration(remaining - 1, vertx, commands, testContext));
                    });
                });
    }

    private static void assertConsistentInvariant(final String cancelOutcome,
                                                  final DeliveryStatus status,
                                                  final DeliveryService deliveryService,
                                                  final String reservedDroneId) {
        final Optional<FleetViews.FleetDroneView> drone = deliveryService.viewFleet().stream()
                .filter(d -> d.droneId().equals(reservedDroneId))
                .findFirst();
        if (drone.isEmpty()) {
            fail("Reserved drone " + reservedDroneId + " disappeared from the fleet view");
            return;
        }
        final String droneStatus = drone.get().status();
        switch (status) {
            case CANCELLED -> {
                assertEquals("CANCELLED", cancelOutcome,
                        "delivery is CANCELLED but the cancel command did not succeed");
                assertEquals(DroneStatus.AVAILABLE.name(), droneStatus,
                        "delivery is CANCELLED but the drone is stuck in " + droneStatus);
            }
            case IN_PROGRESS -> assertEquals("REJECTED", cancelOutcome,
                    "delivery is IN_PROGRESS but the cancel was not rejected with "
                            + "CannotCancelInFlightException");
            default -> fail("Unexpected settled status: " + status);
        }
    }
}
