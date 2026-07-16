package it.unibo.sap.session.infrastructure;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;
import it.unibo.sap.session.application.SessionService;
import it.unibo.sap.session.application.SessionServiceImpl;
import it.unibo.sap.session.support.FakeAccountService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Integration test of the session-service inbound adapter over HTTP: login coins a session and the
 * introspection endpoint resolves that session id to {@code {accountId, role}}. An unknown session
 * id is rejected with 401. The account-service is faked in-process.
 */
class SessionServiceIntrospectionTest {

    private static final String HOST = "localhost";
    private static final int PORT = 9591;

    private static Vertx vertx;
    private static WebClient webClient;

    @BeforeAll
    static void startService() throws Exception {
        vertx = Vertx.vertx();
        final SessionService service = new SessionServiceImpl(
                new FakeAccountService().withSuccessfulLogin("marco", "Secret#123", "acc-1", "SENDER"),
                new InMemorySessionRepository());
        final CompletableFuture<Void> deployed = new CompletableFuture<>();
        vertx.deployVerticle(new SessionServiceController(service, PORT))
                .onComplete(ar -> deployed.complete(null));
        deployed.get(15, TimeUnit.SECONDS);
        webClient = WebClient.create(vertx);
    }

    @AfterAll
    static void stopService() throws Exception {
        if (vertx != null) {
            final CompletableFuture<Void> closed = new CompletableFuture<>();
            vertx.close().onComplete(ar -> closed.complete(null));
            closed.get(15, TimeUnit.SECONDS);
        }
    }

    @Test
    void healthEndpointReportsUp() throws Exception {
        final JsonObject resp = get("/api/v1/health");
        assertEquals(200, resp.getInteger("_statusCode"));
        assertEquals("UP", resp.getString("status"));
    }

    @Test
    void loginThenIntrospectResolvesTheIdentity() throws Exception {
        final JsonObject login = post("/api/v1/login",
                new JsonObject().put("username", "marco").put("password", "Secret#123"));
        assertEquals(200, login.getInteger("_statusCode"));
        final String sessionId = login.getString("sessionId");
        assertNotNull(sessionId);
        assertEquals("acc-1", login.getString("accountId"));
        assertEquals("SENDER", login.getString("role"));

        final JsonObject introspect = get("/api/v1/user-sessions/" + sessionId);
        assertEquals(200, introspect.getInteger("_statusCode"));
        assertEquals("acc-1", introspect.getString("accountId"));
        assertEquals("SENDER", introspect.getString("role"));
    }

    @Test
    void loginWithInvalidCredentialsIsRejected() throws Exception {
        final JsonObject login = post("/api/v1/login",
                new JsonObject().put("username", "marco").put("password", "wrong"));
        assertEquals(401, login.getInteger("_statusCode"));
    }

    @Test
    void introspectionOfAnUnknownSessionIsUnauthorized() throws Exception {
        final JsonObject introspect = get("/api/v1/user-sessions/does-not-exist");
        assertEquals(401, introspect.getInteger("_statusCode"));
    }

    private JsonObject get(final String path) throws Exception {
        final CompletableFuture<JsonObject> done = new CompletableFuture<>();
        webClient.get(PORT, HOST, path).send(ar -> complete(done, ar));
        return done.get(15, TimeUnit.SECONDS);
    }

    private JsonObject post(final String path, final JsonObject body) throws Exception {
        final CompletableFuture<JsonObject> done = new CompletableFuture<>();
        webClient.post(PORT, HOST, path).sendJsonObject(body, ar -> complete(done, ar));
        return done.get(15, TimeUnit.SECONDS);
    }

    private static void complete(final CompletableFuture<JsonObject> done,
                                 final io.vertx.core.AsyncResult<io.vertx.ext.web.client.HttpResponse<io.vertx.core.buffer.Buffer>> ar) {
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
    }
}
