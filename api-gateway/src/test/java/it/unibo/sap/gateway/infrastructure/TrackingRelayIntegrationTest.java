package it.unibo.sap.gateway.infrastructure;

import io.vertx.core.Vertx;
import io.vertx.core.http.WebSocketClient;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.handler.BodyHandler;
import it.unibo.sap.gateway.application.SessionRepository;
import it.unibo.sap.gateway.application.SessionService;
import it.unibo.sap.gateway.application.SessionServiceImpl;
import it.unibo.sap.gateway.support.FakeAccountService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration test of the gateway WebSocket tracking relay (client &lt;-&gt; gateway &lt;-&gt; delivery).
 *
 * <p>A fake in-process delivery-service exposes both the REST {@code track} endpoint and a WS server
 * that, once it receives the opening {@code {"deliveryId":"..."}} frame, pushes a couple of update
 * frames. The test then verifies, through the real {@link APIGatewayController}, that:
 */
class TrackingRelayIntegrationTest {

    private static final String HOST = "localhost";
    private static final int DELIVERY_PORT = 9404;
    private static final int GATEWAY_PORT = 9405;
    private static final String TRACKING_SESSION_ID = "TRK-1";
    private static final String DELIVERY_ID = "DLV-9";
    private static final String FINAL_FRAME_DELIVERY_ID = "DLV-FINAL";

    private static Vertx vertx;
    private static WebClient webClient;

    @BeforeAll
    static void startSystem() {
        vertx = Vertx.vertx();
        startFakeDeliveryService();
        startGateway();
        webClient = WebClient.create(vertx);
    }

    @AfterAll
    static void stopSystem() {
        if (vertx != null) {
            final CountDownLatch latch = new CountDownLatch(1);
            vertx.close().onComplete(ar -> latch.countDown());
            await(latch);
        }
    }

    private static void startFakeDeliveryService() {
        final Router router = Router.router(vertx);
        router.route("/api/v1/*").handler(BodyHandler.create());
        router.post("/api/v1/deliveries/:deliveryId/track").handler(ctx ->
                ctx.response().setStatusCode(201).putHeader("Content-Type", "application/json")
                        .end(new JsonObject()
                                .put("trackingSessionId", TRACKING_SESSION_ID)
                                .put("deliveryId", ctx.pathParam("deliveryId"))
                                .put("webSocketUrl",
                                        "ws://" + HOST + ":" + DELIVERY_PORT + "/api/v1/track/" + TRACKING_SESSION_ID)
                                .encode()));

        final var server = vertx.createHttpServer();
        server.webSocketHandler(ws -> {
            if (!ws.path().startsWith("/api/v1/track/")) {
                ws.reject();
                return;
            }
            ws.textMessageHandler(openFrame -> {
                final String forwardedDeliveryId = new JsonObject(openFrame).getString("deliveryId");
                if (FINAL_FRAME_DELIVERY_ID.equals(forwardedDeliveryId)) {
                    ws.writeTextMessage(new JsonObject()
                            .put("deliveryId", forwardedDeliveryId)
                            .put("status", "DELIVERED")
                            .put("estimatedTimeRemainingSeconds", 0).encode(), ar -> ws.close());
                    return;
                }
                ws.writeTextMessage(new JsonObject()
                        .put("deliveryId", forwardedDeliveryId).put("etrSeconds", 120).encode());
                ws.writeTextMessage(new JsonObject()
                        .put("deliveryId", forwardedDeliveryId).put("etrSeconds", 90).encode());
            });
        });

        final CountDownLatch latch = new CountDownLatch(1);
        server.requestHandler(router).listen(DELIVERY_PORT).onComplete(ar -> latch.countDown());
        await(latch);
    }

    private static void startGateway() {
        final WebClient gatewayClient = WebClient.create(vertx);
        final AccountServiceProxy accountProxy =
                new AccountServiceProxy(gatewayClient, HOST, DELIVERY_PORT);
        final DeliveryServiceProxy deliveryProxy =
                new DeliveryServiceProxy(gatewayClient, HOST, DELIVERY_PORT, DELIVERY_PORT);
        final FakeAccountService account = new FakeAccountService().withSuccessfulLogin("acc-1", "SENDER");
        final SessionRepository sessions = new InMemorySessionRepository();
        final SessionService service = new SessionServiceImpl(account, deliveryProxy, sessions);
        final var controller = new APIGatewayController(service, accountProxy, deliveryProxy, HOST, GATEWAY_PORT);

        final CountDownLatch latch = new CountDownLatch(1);
        vertx.deployVerticle(controller).onComplete(ar -> latch.countDown());
        await(latch);
    }

    @Test
    void clientConnectedToTheGatewayReceivesUpdatesRelayedFromTheDelivery() throws Exception {
        final WebSocketClient client = vertx.createWebSocketClient();
        final List<String> received = new CopyOnWriteArrayList<>();
        final CountDownLatch twoUpdates = new CountDownLatch(2);

        client.connect(GATEWAY_PORT, HOST, "/api/v1/track/" + TRACKING_SESSION_ID)
                .onSuccess(ws -> {
                    ws.textMessageHandler(msg -> {
                        received.add(msg);
                        twoUpdates.countDown();
                    });
                    ws.writeTextMessage(new JsonObject().put("deliveryId", DELIVERY_ID).encode());
                });

        assertTrue(twoUpdates.await(15, TimeUnit.SECONDS), "expected two relayed updates from the delivery");
        assertEquals(2, received.size());
        assertTrue(received.get(0).contains("etrSeconds"), "the relayed frame should carry the delivery update");
        assertTrue(received.get(0).contains(DELIVERY_ID), "the open frame must have been forwarded downstream");
    }

    @Test
    void finalDeliveredFrameIsRelayedToTheClientEvenWhenTheDeliveryLegClosesRightAfter() throws Exception {
        final WebSocketClient client = vertx.createWebSocketClient();
        final AtomicReference<String> deliveredFrame = new AtomicReference<>();
        final CountDownLatch gotDelivered = new CountDownLatch(1);

        client.connect(GATEWAY_PORT, HOST, "/api/v1/track/" + TRACKING_SESSION_ID)
                .onSuccess(ws -> {
                    ws.textMessageHandler(msg -> {
                        if (msg.contains("DELIVERED")) {
                            deliveredFrame.set(msg);
                            gotDelivered.countDown();
                        }
                    });
                    ws.writeTextMessage(new JsonObject().put("deliveryId", FINAL_FRAME_DELIVERY_ID).encode());
                });

        assertTrue(gotDelivered.await(15, TimeUnit.SECONDS),
                "the final DELIVERED frame must reach the client before the gateway closes the client leg");
        assertTrue(deliveredFrame.get().contains("\"estimatedTimeRemainingSeconds\":0"),
                "the final frame must carry ETR 0");
    }

    @Test
    void trackDeliveryReturnsAWebSocketUrlPointingAtTheGatewayNotTheDelivery() throws Exception {
        final String sessionId = login();

        final CompletableFuture<JsonObject> track = new CompletableFuture<>();
        webClient.post(GATEWAY_PORT, HOST, "/api/v1/user-sessions/" + sessionId + "/track-delivery")
                .sendJsonObject(new JsonObject().put("deliveryId", DELIVERY_ID),
                        ar -> track.complete(ar.result().bodyAsJsonObject()));
        final JsonObject body = track.get(15, TimeUnit.SECONDS);

        final String wsUrl = body.getString("webSocketUrl");
        assertTrue(wsUrl.startsWith("ws://" + HOST + ":" + GATEWAY_PORT + "/api/v1/track/"),
                "the client must be pointed back at the gateway, got: " + wsUrl);
        assertTrue(wsUrl.contains(TRACKING_SESSION_ID), "the tracking session id must be preserved");
        assertFalse(wsUrl.contains(String.valueOf(DELIVERY_PORT)),
                "the client must never see the delivery-service port, got: " + wsUrl);
        assertEquals(DELIVERY_ID, body.getString("deliveryId"));
        assertEquals(TRACKING_SESSION_ID, body.getString("trackingSessionId"));
    }

    private String login() throws Exception {
        final CompletableFuture<JsonObject> login = new CompletableFuture<>();
        webClient.post(GATEWAY_PORT, HOST, "/api/v1/login")
                .sendJsonObject(new JsonObject().put("username", "marco").put("password", "Secret#123"),
                        ar -> login.complete(ar.result().bodyAsJsonObject()));
        return login.get(15, TimeUnit.SECONDS).getString("sessionId");
    }

    private static void await(final CountDownLatch latch) {
        try {
            if (!latch.await(15, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out setting up the tracking-relay test system");
            }
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while setting up the test system", e);
        }
    }
}
