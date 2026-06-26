package it.unibo.sap.delivery.infrastructure;

import io.vertx.core.Vertx;
import io.vertx.core.http.WebSocketClient;
import io.vertx.core.json.JsonObject;
import it.unibo.sap.delivery.application.CreateDeliveryCommand;
import it.unibo.sap.delivery.application.CreateDeliveryResult;
import it.unibo.sap.delivery.application.DeliveryService;
import it.unibo.sap.delivery.application.DeliveryServiceImpl;
import it.unibo.sap.delivery.application.DroneEventHandler;
import it.unibo.sap.delivery.application.TrackingHandle;
import it.unibo.sap.delivery.application.TrackingSessionRegistry;
import it.unibo.sap.delivery.support.FakeFleetPort;
import it.unibo.sap.delivery.support.FakeGeocodingPort;
import it.unibo.sap.delivery.support.InMemoryDeliveryRepository;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end (in-process) regression test for the arrival of a delivery: drives the REAL
 * {@link DroneEventHandler#onDroneArrived} (not a hand-published event) through the deployed
 * {@link DeliveryServiceController} WebSocket, and verifies a connected client receives the final
 * {@code DELIVERED}/ETR-0 frame and then the socket closes — guarding against the final frame being
 * lost on drone arrival. A {@link FakeFleetPort} is used so no background drone simulator runs.
 */
class ArrivalTerminalFrameIntegrationTest {

    private static final String HOST = "localhost";
    private static final int PORT = 9095;
    private static final String SENDER_ID = "user-1";
    private static final double DRONE_SPEED_UNITS_PER_SECOND = 0.01;

    private static Vertx vertx;
    private static DeliveryService deliveryService;
    private static DroneEventHandler droneEventHandler;

    @BeforeAll
    static void startService() {
        vertx = Vertx.vertx();
        final InMemoryDeliveryRepository repository = new InMemoryDeliveryRepository();
        final TrackingSessionRegistry trackingRegistry = new InMemoryTrackingSessionRegistry();
        final FakeGeocodingPort geocoding = new FakeGeocodingPort();
        final FakeFleetPort fleet = new FakeFleetPort();
        final VertxTrackingSessionEventObserver observer =
                new VertxTrackingSessionEventObserver(vertx.eventBus());

        deliveryService = new DeliveryServiceImpl(repository, fleet, geocoding, trackingRegistry);
        droneEventHandler = new DroneEventHandler(
                repository, trackingRegistry, observer, fleet, DRONE_SPEED_UNITS_PER_SECOND);

        final CountDownLatch deployed = new CountDownLatch(1);
        vertx.deployVerticle(new DeliveryServiceController(deliveryService, PORT))
                .onComplete(ar -> deployed.countDown());
        await(deployed);
    }

    @AfterAll
    static void stopService() {
        if (vertx != null) {
            final CountDownLatch latch = new CountDownLatch(1);
            vertx.close().onComplete(ar -> latch.countDown());
            await(latch);
        }
    }

    @Test
    void onDroneArrivedPushesTheFinalDeliveredFrameToTheTrackingClientBeforeClose() {
        final CreateDeliveryResult created = deliveryService.createDelivery(new CreateDeliveryCommand(
                SENDER_ID, 2.0, "via Emilia", 9, "via Veneto", 5, true, null, 60));
        final String deliveryId = created.deliveryId();
        final TrackingHandle handle = deliveryService.startTracking(deliveryId, SENDER_ID);

        final WebSocketClient client = vertx.createWebSocketClient();
        final AtomicReference<JsonObject> finalFrame = new AtomicReference<>();
        final AtomicBoolean arrivalTriggered = new AtomicBoolean(false);
        final AtomicLong warmupTimer = new AtomicLong(-1);
        final CountDownLatch gotFinal = new CountDownLatch(1);
        final CountDownLatch socketClosed = new CountDownLatch(1);

        client.connect(PORT, HOST, "/api/v1/track/" + handle.trackingSessionId()).onSuccess(ws -> {
            ws.textMessageHandler(msg -> {
                final JsonObject frame = new JsonObject(msg);
                if ("DELIVERED".equals(frame.getString("status"))) {
                    finalFrame.set(frame);
                    gotFinal.countDown();
                } else if (arrivalTriggered.compareAndSet(false, true)) {
                    vertx.cancelTimer(warmupTimer.get());
                    droneEventHandler.onDroneArrived(deliveryId, 44.50, 11.34);
                }
            });
            ws.closeHandler(v -> socketClosed.countDown());
            ws.writeTextMessage(new JsonObject().put("deliveryId", deliveryId).encode());
            warmupTimer.set(vertx.setPeriodic(50, id ->
                    droneEventHandler.onDronePositionUpdated(deliveryId, 44.49, 11.34)));
        });

        assertTrue(awaitLatch(gotFinal), "the client must receive the final DELIVERED frame on arrival");
        assertNotNull(finalFrame.get());
        assertEquals("DELIVERED", finalFrame.get().getString("status"));
        assertEquals(0L, finalFrame.get().getLong("estimatedTimeRemainingSeconds"));
        assertTrue(awaitLatch(socketClosed), "the socket must close after the final frame was delivered");
    }

    private static boolean awaitLatch(final CountDownLatch latch) {
        try {
            return latch.await(15, TimeUnit.SECONDS);
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private static void await(final CountDownLatch latch) {
        if (!awaitLatch(latch)) {
            throw new IllegalStateException("Timed out setting up the arrival test");
        }
    }
}
