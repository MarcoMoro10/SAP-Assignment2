package it.unibo.sap.gateway.infrastructure;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;
import it.unibo.sap.gateway.application.SessionService;
import it.unibo.sap.gateway.application.SessionServiceImpl;
import it.unibo.sap.gateway.support.FakeAccountService;
import it.unibo.sap.gateway.support.FakeDeliveryService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Integration test of the gateway role enforcement over HTTP: an ADMIN session is only entitled to the
 * read-only admin views, never to operational delivery commands. Logging in as ADMIN and then calling
 * create-delivery through the real {@link APIGatewayController} must be rejected with 403 (the
 * {@code SecurityException} raised by {@code requireRole("SENDER")} mapped to Forbidden) and must not
 * reach the downstream delivery service. Symmetric to how the admin views require {@code requireRole("ADMIN")}.
 */
class AdminRoleAuthorizationIntegrationTest {

    private static final String HOST = "localhost";
    private static final int UNUSED_DOWNSTREAM_PORT = 9428;
    private static final int GATEWAY_PORT = 9429;

    private static Vertx vertx;
    private static WebClient webClient;
    private static FakeDeliveryService delivery;

    @BeforeAll
    static void startSystem() {
        vertx = Vertx.vertx();
        final WebClient gatewayClient = WebClient.create(vertx);
        final AccountServiceProxy accountProxy =
                new AccountServiceProxy(gatewayClient, HOST, UNUSED_DOWNSTREAM_PORT);
        final DeliveryServiceProxy deliveryProxy =
                new DeliveryServiceProxy(gatewayClient, HOST, UNUSED_DOWNSTREAM_PORT, UNUSED_DOWNSTREAM_PORT);

        delivery = new FakeDeliveryService();
        final FakeAccountService account = new FakeAccountService().withSuccessfulLogin("acc-admin", "ADMIN");
        final SessionService service = new SessionServiceImpl(account, delivery, new InMemorySessionRepository());
        final var controller = new APIGatewayController(service, accountProxy, deliveryProxy, HOST, GATEWAY_PORT);

        final CountDownLatch latch = new CountDownLatch(1);
        vertx.deployVerticle(controller).onComplete(ar -> latch.countDown());
        await(latch);
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

    @Test
    void adminSessionCannotCreateADeliveryAndIsRejectedWith403() throws Exception {
        final JsonObject loginResp = post("/api/v1/login",
                new JsonObject().put("username", "admin").put("password", "Secret#123"));
        assertEquals(200, loginResp.getInteger("_statusCode"));
        assertEquals("ADMIN", loginResp.getString("role"));
        final String sessionId = loginResp.getString("sessionId");

        final JsonObject createResp = post("/api/v1/user-sessions/" + sessionId + "/create-delivery",
                new JsonObject()
                        .put("weight", 2.0)
                        .put("startingPlace", new JsonObject().put("street", "via Emilia").put("number", 9))
                        .put("destinationPlace", new JsonObject().put("street", "via Veneto").put("number", 5))
                        .put("immediate", true)
                        .put("deadlineMinutes", 60));

        assertEquals(403, createResp.getInteger("_statusCode"), "an ADMIN must not be allowed to create a delivery");
        assertNull(delivery.lastCreateRequest, "the gateway must not forward the create to the delivery service");
    }

    private JsonObject post(final String path, final JsonObject body) throws Exception {
        final CompletableFuture<JsonObject> done = new CompletableFuture<>();
        webClient.post(GATEWAY_PORT, HOST, path).sendJsonObject(body, ar -> {
            if (ar.succeeded()) {
                JsonObject parsed;
                try {
                    parsed = ar.result().bodyAsJsonObject();
                } catch (final RuntimeException notJson) {
                    parsed = new JsonObject();
                }
                done.complete(parsed.put("_statusCode", ar.result().statusCode()));
            } else {
                done.completeExceptionally(ar.cause());
            }
        });
        return done.get(15, TimeUnit.SECONDS);
    }

    private static void await(final CountDownLatch latch) {
        try {
            if (!latch.await(15, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out setting up the admin authorization test system");
            }
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while setting up the test system", e);
        }
    }
}
