package it.unibo.sap.gateway.infrastructure;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.handler.BodyHandler;
import it.unibo.sap.gateway.application.DeliveryService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration test of {@link DeliveryServiceProxy} against a stub HTTP server that mimics the
 * delivery-service. Verifies that the proxy translates calls into the right HTTP requests and maps
 * the responses back. The same stub port serves both the delivery and the admin/fleet endpoints.
 */
class DeliveryServiceProxyIntegrationTest {

    private static final int STUB_PORT = 9402;
    private static final String HOST = "localhost";

    private static Vertx vertx;
    private static WebClient webClient;
    private static DeliveryService proxy;

    @BeforeAll
    static void startStub() {
        vertx = Vertx.vertx();
        final Router router = Router.router(vertx);
        router.route("/api/v1/*").handler(BodyHandler.create());

        router.post("/api/v1/deliveries").handler(ctx -> {
            final JsonObject body = ctx.body().asJsonObject();
            ctx.response().setStatusCode(201).putHeader("Content-Type", "application/json")
                    .end(new JsonObject()
                            .put("deliveryId", "DLV-1")
                            .put("status", "IN_PROGRESS")
                            .put("assignedDroneId", "DRN-1")
                            .put("echoWeight", body.getValue("weight"))
                            .encode());
        });

        router.get("/api/v1/deliveries/:deliveryId").handler(ctx -> {
            final String senderId = ctx.queryParams().get("senderId");
            if ("user-1".equals(senderId)) {
                ctx.response().setStatusCode(200).putHeader("Content-Type", "application/json")
                        .end(new JsonObject()
                                .put("deliveryId", ctx.pathParam("deliveryId"))
                                .put("status", "IN_PROGRESS").encode());
            } else {
                ctx.response().setStatusCode(404)
                        .end(new JsonObject().put("error", "Delivery not found").encode());
            }
        });

        router.post("/api/v1/deliveries/:deliveryId/cancel").handler(ctx ->
                ctx.response().setStatusCode(200).putHeader("Content-Type", "application/json")
                        .end(new JsonObject()
                                .put("deliveryId", ctx.pathParam("deliveryId"))
                                .put("status", "CANCELLED").encode()));

        router.get("/api/v1/admin/fleet").handler(ctx ->
                ctx.response().setStatusCode(200).putHeader("Content-Type", "application/json")
                        .end(new JsonArray().add(new JsonObject()
                                .put("droneId", "DRN-1").put("status", "AVAILABLE")).encode()));

        final CountDownLatch latch = new CountDownLatch(1);
        vertx.createHttpServer().requestHandler(router).listen(STUB_PORT).onComplete(ar -> latch.countDown());
        await(latch);

        webClient = WebClient.create(vertx);
        proxy = new DeliveryServiceProxy(webClient, HOST, STUB_PORT, STUB_PORT);
    }

    @AfterAll
    static void stopStub() {
        if (vertx != null) {
            final CountDownLatch latch = new CountDownLatch(1);
            vertx.close().onComplete(ar -> latch.countDown());
            await(latch);
        }
    }

    @Test
    void createDeliveryForwardsBodyAndReturnsStatusAndPayload() {
        final JsonObject result = proxy.createDelivery(new JsonObject().put("weight", 2.0).put("senderId", "user-1"));

        assertEquals(201, result.getInteger("_statusCode"));
        assertEquals("DLV-1", result.getString("deliveryId"));
        assertEquals("DRN-1", result.getString("assignedDroneId"));
        assertEquals(2.0, result.getDouble("echoWeight"), 1e-9);
    }

    @Test
    void getDeliveryReturnsThePayloadForTheOwner() {
        final Optional<JsonObject> result = proxy.getDelivery("DLV-1", "user-1");
        assertTrue(result.isPresent());
        assertEquals("IN_PROGRESS", result.get().getString("status"));
    }

    @Test
    void getDeliveryReturnsEmptyWhenDownstreamReplies404() {
        final Optional<JsonObject> result = proxy.getDelivery("DLV-1", "intruder");
        assertFalse(result.isPresent());
    }

    @Test
    void cancelDeliveryReturnsCancelledStatus() {
        final JsonObject result = proxy.cancelDelivery("DLV-1", "user-1");
        assertEquals("CANCELLED", result.getString("status"));
    }

    @Test
    void viewFleetWrapsTheDownstreamArray() {
        final JsonObject result = proxy.viewFleet();
        final JsonArray fleet = result.getJsonArray("fleet");
        assertEquals(1, fleet.size());
        assertEquals("DRN-1", fleet.getJsonObject(0).getString("droneId"));
    }

    private static void await(final CountDownLatch latch) {
        try {
            if (!latch.await(15, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting for the delivery-service stub");
            }
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while setting up the stub", e);
        }
    }
}
