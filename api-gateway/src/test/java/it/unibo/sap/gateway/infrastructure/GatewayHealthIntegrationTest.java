package it.unibo.sap.gateway.infrastructure;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.client.WebClient;
import it.unibo.sap.gateway.application.SessionService;
import it.unibo.sap.gateway.application.SessionServiceImpl;
import it.unibo.sap.gateway.support.FakeAccountService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Integration test of the gateway's health endpoints. {@code GET /api/v1/health} is the aggregated
 * READINESS probe: 200/UP only when both downstream account/delivery are UP, otherwise 503/DOWN (so an
 * operator can diagnose a down dependency). {@code GET /api/v1/health/live} is the LIVENESS probe used by
 * autoheal/compose: it always answers 200 without touching the downstreams, so a down dependency never
 * triggers a restart of the gateway (which is not the faulty component).
 */
class GatewayHealthIntegrationTest {

    private static final String HOST = "localhost";
    private static final int ACCOUNT_STUB_PORT = 9406;
    private static final int DELIVERY_STUB_PORT = 9407;
    private static final int GATEWAY_PORT = 9408;

    private static final AtomicBoolean deliveryHealthy = new AtomicBoolean(true);

    private static Vertx vertx;
    private static WebClient webClient;

    @BeforeAll
    static void startSystem() {
        vertx = Vertx.vertx();
        startStubHealthServer(ACCOUNT_STUB_PORT, () -> true);
        startStubHealthServer(DELIVERY_STUB_PORT, deliveryHealthy::get);
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

    private static void startStubHealthServer(final int port, final java.util.function.BooleanSupplier healthy) {
        final Router router = Router.router(vertx);
        router.get("/api/v1/health").handler(ctx -> {
            if (healthy.getAsBoolean()) {
                ctx.response().setStatusCode(200).putHeader("Content-Type", "application/json")
                        .end(new JsonObject().put("status", "UP").encode());
            } else {
                ctx.response().setStatusCode(503).end();
            }
        });
        final CountDownLatch latch = new CountDownLatch(1);
        vertx.createHttpServer().requestHandler(router).listen(port).onComplete(ar -> latch.countDown());
        await(latch);
    }

    private static void startGateway() {
        final WebClient gatewayClient = WebClient.create(vertx);
        final AccountServiceProxy accountProxy =
                new AccountServiceProxy(gatewayClient, HOST, ACCOUNT_STUB_PORT);
        final DeliveryServiceProxy deliveryProxy =
                new DeliveryServiceProxy(gatewayClient, HOST, DELIVERY_STUB_PORT, DELIVERY_STUB_PORT);
        final SessionService service = new SessionServiceImpl(
                new FakeAccountService(), deliveryProxy, new InMemorySessionRepository());
        final var controller = new APIGatewayController(service, accountProxy, deliveryProxy, HOST, GATEWAY_PORT);

        final CountDownLatch latch = new CountDownLatch(1);
        vertx.deployVerticle(controller).onComplete(ar -> latch.countDown());
        await(latch);
    }

    @Test
    void readinessIs200AndUpWhenAllDownstreamsAreUp() throws Exception {
        deliveryHealthy.set(true);
        final JsonObject body = getGatewayHealth();

        assertEquals(200, body.getInteger("statusCode"));
        assertEquals("UP", body.getString("status"));
        final Map<String, String> checks = checksByName(body.getJsonArray("checks"));
        assertEquals("UP", checks.get("account-service"));
        assertEquals("UP", checks.get("delivery-service"));
    }

    @Test
    void readinessIs503AndDownWhenADependencyIsDown() throws Exception {
        deliveryHealthy.set(false);
        final JsonObject body = getGatewayHealth();

        assertEquals(503, body.getInteger("statusCode"), "readiness must fail when a dependency is down");
        assertEquals("DOWN", body.getString("status"));
        final Map<String, String> checks = checksByName(body.getJsonArray("checks"));
        assertEquals("UP", checks.get("account-service"));
        assertEquals("DOWN", checks.get("delivery-service"), "a down dependency must be reported DOWN");
    }

    @Test
    void livenessIs200EvenWhenADependencyIsDown() throws Exception {
        deliveryHealthy.set(false);
        final CompletableFuture<JsonObject> response = new CompletableFuture<>();
        webClient.get(GATEWAY_PORT, HOST, "/api/v1/health/live").send(ar -> {
            if (ar.succeeded()) {
                response.complete(new JsonObject()
                        .put("statusCode", ar.result().statusCode())
                        .mergeIn(ar.result().bodyAsJsonObject()));
            } else {
                response.completeExceptionally(ar.cause());
            }
        });
        final JsonObject result = response.get(15, TimeUnit.SECONDS);

        assertEquals(200, result.getInteger("statusCode"), "liveness must stay 200 regardless of dependencies");
        assertEquals("UP", result.getString("status"));
    }

    private JsonObject getGatewayHealth() throws Exception {
        final CompletableFuture<JsonObject> response = new CompletableFuture<>();
        webClient.get(GATEWAY_PORT, HOST, "/api/v1/health").send(ar -> {
            if (ar.succeeded()) {
                response.complete(new JsonObject()
                        .put("statusCode", ar.result().statusCode())
                        .mergeIn(ar.result().bodyAsJsonObject()));
            } else {
                response.completeExceptionally(ar.cause());
            }
        });
        return response.get(15, TimeUnit.SECONDS);
    }

    private static Map<String, String> checksByName(final JsonArray checks) {
        final Map<String, String> byName = new HashMap<>();
        for (int i = 0; i < checks.size(); i++) {
            final JsonObject check = checks.getJsonObject(i);
            byName.put(check.getString("name"), check.getString("status"));
        }
        return byName;
    }

    private static void await(final CountDownLatch latch) {
        try {
            if (!latch.await(15, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out setting up the gateway health test system");
            }
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while setting up the test system", e);
        }
    }
}
