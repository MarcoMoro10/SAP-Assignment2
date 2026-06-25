package it.unibo.sap.gateway.infrastructure;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.handler.BodyHandler;
import it.unibo.sap.gateway.application.SessionService;
import it.unibo.sap.gateway.application.SessionServiceImpl;
import it.unibo.sap.gateway.support.FakeAccountService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Integration test of the gateway routing client registration (POST /api/v1/accounts) to a stub
 * account-service: a new username returns 201, an existing one 409 (status and body propagated), and
 * a malformed payload is rejected at the gateway with 400 without reaching the downstream service.
 * Registration must not open a session, so the stub session repository stays empty.
 */
class GatewayRegistrationIntegrationTest {

    private static final String HOST = "localhost";
    private static final int ACCOUNT_STUB_PORT = 9418;
    private static final int GATEWAY_PORT = 9419;

    private static Vertx vertx;
    private static WebClient webClient;
    private static final AtomicInteger downstreamHits = new AtomicInteger(0);

    @BeforeAll
    static void startSystem() {
        vertx = Vertx.vertx();
        startAccountStub();
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

    private static void startAccountStub() {
        final Router router = Router.router(vertx);
        router.route("/api/v1/*").handler(BodyHandler.create());
        router.post("/api/v1/accounts").handler(ctx -> {
            downstreamHits.incrementAndGet();
            final JsonObject body = ctx.body().asJsonObject();
            final String username = body.getString("username");
            if ("existing".equals(username)) {
                ctx.response().setStatusCode(409).putHeader("Content-Type", "application/json")
                        .end(new JsonObject().put("error", "Username already taken").encode());
            } else {
                ctx.response().setStatusCode(201).putHeader("Content-Type", "application/json")
                        .end(new JsonObject()
                                .put("accountId", "acc-" + username)
                                .put("username", username)
                                .put("role", "SENDER")
                                .put("loginLink", "/api/v1/accounts/acc-" + username + "/login")
                                .encode());
            }
        });
        final CountDownLatch latch = new CountDownLatch(1);
        vertx.createHttpServer().requestHandler(router).listen(ACCOUNT_STUB_PORT).onComplete(ar -> latch.countDown());
        await(latch);
    }

    private static void startGateway() {
        final WebClient gatewayClient = WebClient.create(vertx);
        final AccountServiceProxy accountProxy =
                new AccountServiceProxy(gatewayClient, HOST, ACCOUNT_STUB_PORT);
        final DeliveryServiceProxy deliveryProxy =
                new DeliveryServiceProxy(gatewayClient, HOST, ACCOUNT_STUB_PORT, ACCOUNT_STUB_PORT);
        final SessionService service = new SessionServiceImpl(
                new FakeAccountService(), deliveryProxy, new InMemorySessionRepository());
        final var controller = new APIGatewayController(service, accountProxy, deliveryProxy, HOST, GATEWAY_PORT);

        final CountDownLatch latch = new CountDownLatch(1);
        vertx.deployVerticle(controller).onComplete(ar -> latch.countDown());
        await(latch);
    }

    @Test
    void registerNewUserReturns201WithPayload() throws Exception {
        final JsonObject resp = register(new JsonObject().put("username", "marco").put("password", "Secret#123"));

        assertEquals(201, resp.getInteger("statusCode"));
        assertEquals("acc-marco", resp.getString("accountId"));
        assertEquals("SENDER", resp.getString("role"));
    }

    @Test
    void registerExistingUserPropagates409() throws Exception {
        final JsonObject resp = register(new JsonObject().put("username", "existing").put("password", "Secret#123"));

        assertEquals(409, resp.getInteger("statusCode"));
        assertEquals("Username already taken", resp.getString("error"));
    }

    @Test
    void malformedPayloadIsRejectedAtGatewayWithoutHittingDownstream() throws Exception {
        final int before = downstreamHits.get();
        final JsonObject resp = register(new JsonObject().put("username", "noPassword"));

        assertEquals(400, resp.getInteger("statusCode"));
        assertEquals(before, downstreamHits.get(), "the gateway must not forward a malformed registration");
    }

    private JsonObject register(final JsonObject body) throws Exception {
        final CompletableFuture<JsonObject> response = new CompletableFuture<>();
        webClient.post(GATEWAY_PORT, HOST, "/api/v1/accounts").sendJsonObject(body, ar -> {
            if (ar.succeeded()) {
                JsonObject parsed;
                try {
                    parsed = ar.result().bodyAsJsonObject();
                } catch (final RuntimeException notJson) {
                    parsed = new JsonObject();
                }
                response.complete(parsed.put("statusCode", ar.result().statusCode()));
            } else {
                response.completeExceptionally(ar.cause());
            }
        });
        return response.get(15, TimeUnit.SECONDS);
    }

    private static void await(final CountDownLatch latch) {
        try {
            if (!latch.await(15, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out setting up the gateway registration test system");
            }
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while setting up the test system", e);
        }
    }
}
