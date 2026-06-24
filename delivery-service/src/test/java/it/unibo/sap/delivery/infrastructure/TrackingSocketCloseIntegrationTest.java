package it.unibo.sap.delivery.infrastructure;

import io.vertx.core.Vertx;
import io.vertx.core.http.WebSocketClient;
import io.vertx.core.json.JsonObject;
import it.unibo.sap.delivery.application.DeliveryService;
import it.unibo.sap.delivery.application.DeliveryServiceImpl;
import it.unibo.sap.delivery.application.GeocodingPort;
import it.unibo.sap.delivery.application.TrackingSessionRegistry;
import it.unibo.sap.delivery.infrastructure.fleet.FleetModule;
import it.unibo.sap.delivery.infrastructure.fleet.InMemoryDroneRepository;
import it.unibo.sap.delivery.support.InMemoryDeliveryRepository;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * <p>Simulates the arrival push (the terminal {@code DELIVERED}/ETR-0 update the {@code DroneEventHandler}
 * publishes on {@code onDroneArrived}) and verifies that a connected WebSocket client receives that final
 * frame, and only afterwards sees the socket closed — i.e. the close never overtakes the final update.
 */
class TrackingSocketCloseIntegrationTest {

    private static final String HOST = "localhost";
    private static final int PORT = 9094;
    private static final String DELIVERY_ID = "DLV-1";
    private static final double DRONE_SPEED_UNITS_PER_SECOND = 0.01;

    private static Vertx vertx;

    @BeforeAll
    static void startService() {
        vertx = Vertx.vertx();
        final InMemoryDeliveryRepository repository = new InMemoryDeliveryRepository();
        final TrackingSessionRegistry trackingRegistry = new InMemoryTrackingSessionRegistry();
        final GeocodingPort geocoding = new GeocodingService();
        final FleetModule fleetModule = new FleetModule(new InMemoryDroneRepository(), DRONE_SPEED_UNITS_PER_SECOND);
        final DeliveryService deliveryService =
                new DeliveryServiceImpl(repository, fleetModule, geocoding, trackingRegistry);

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
    void finalDeliveredFrameReachesTheClientBeforeTheSocketCloses() {
        final WebSocketClient client = vertx.createWebSocketClient();
        final AtomicReference<JsonObject> finalFrame = new AtomicReference<>();
        final AtomicReference<Long> publisherTimer = new AtomicReference<>();
        final CountDownLatch gotFinal = new CountDownLatch(1);
        final CountDownLatch socketClosed = new CountDownLatch(1);
        final VertxTrackingSessionEventObserver arrivalPush =
                new VertxTrackingSessionEventObserver(vertx.eventBus());

        client.connect(PORT, HOST, "/api/v1/track/TRK-1").onSuccess(ws -> {
            ws.textMessageHandler(msg -> {
                final Long timer = publisherTimer.get();
                if (timer != null) {
                    vertx.cancelTimer(timer);
                }
                finalFrame.set(new JsonObject(msg));
                gotFinal.countDown();
            });
            ws.closeHandler(v -> socketClosed.countDown());
            ws.writeTextMessage(new JsonObject().put("deliveryId", DELIVERY_ID).encode());
            publisherTimer.set(vertx.setPeriodic(50, id ->
                    arrivalPush.pushTrackingUpdate(DELIVERY_ID, "DELIVERED", 44.50, 11.35, 0L)));
        });

        assertTrue(awaitLatch(gotFinal), "the client must receive the final DELIVERED frame");
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
            throw new IllegalStateException("Timed out setting up the tracking-socket test");
        }
    }
}
