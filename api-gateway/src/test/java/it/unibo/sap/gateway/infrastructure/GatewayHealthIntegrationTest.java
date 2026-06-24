package it.unibo.sap.gateway.infrastructure;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.client.WebClient;
import it.unibo.sap.gateway.application.SessionRepository;
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
 * Integration test of the gateway's rich health endpoint: it aggregates the downstream account/delivery
 * health checks and stays {@code UP} even when a dependency is DOWN (the gateway itself works; fail-fast
 * is the Circuit Breaker's job.
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
    void healthAggregatesDownstreamChecksWhenAllUp() throws Exception {
        deliveryHealthy.set(true);
        final JsonObject body = getGatewayHealth();

        assertEquals("UP", body.getString("status"));
        final Map<String, String> checks = checksByName(body.getJsonArray("checks"));
        assertEquals("UP", checks.get("account-service"));
        assertEquals("UP", checks.get("delivery-service"));
    }

    @Test
    void gatewayStaysUpButReportsADownDependency() throws Exception {
        deliveryHealthy.set(false);
        final JsonObject body = getGatewayHealth();

        assertEquals("UP", body.getString("status"), "the gateway itself must stay UP");
        final Map<String, String> checks = checksByName(body.getJsonArray("checks"));
        assertEquals("UP", checks.get("account-service"));
        assertEquals("DOWN", checks.get("delivery-service"), "a down dependency must be reported DOWN");
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
        final JsonObject result = response.get(15, TimeUnit.SECONDS);
        assertEquals(200, result.getInteger("statusCode"));
        return result;
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
