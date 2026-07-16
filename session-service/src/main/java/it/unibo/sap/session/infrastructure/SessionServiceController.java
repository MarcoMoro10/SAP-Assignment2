package it.unibo.sap.session.infrastructure;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import it.unibo.sap.common.hexagonal.InputAdapter;
import it.unibo.sap.session.application.SessionService;
import it.unibo.sap.session.domain.Session;
import it.unibo.sap.session.domain.SessionId;

public class SessionServiceController extends AbstractVerticle implements InputAdapter {

    private final SessionService sessionService;
    private final int port;

    public SessionServiceController(final SessionService sessionService, final int port) {
        this.sessionService = sessionService;
        this.port = port;
    }

    @Override
    public void start(final Promise<Void> startPromise) {
        final Router router = Router.router(vertx);
        router.route("/api/v1/*").handler(BodyHandler.create());
        router.get("/api/v1/health").handler(this::handleHealth);
        router.post("/api/v1/login").handler(this::handleLogin);
        router.get("/api/v1/user-sessions/:sessionId").handler(this::handleIntrospect);

        vertx.createHttpServer()
                .requestHandler(router)
                .listen(port, http -> {
                    if (http.succeeded()) {
                        System.out.println("session-service ready - port: " + port);
                        startPromise.complete();
                    } else {
                        startPromise.fail(http.cause());
                    }
                });
    }

    private void handleHealth(final RoutingContext ctx) {
        ctx.response().setStatusCode(200)
                .putHeader("Content-Type", "application/json")
                .end(new JsonObject().put("status", "UP").encode());
    }

    private void handleLogin(final RoutingContext ctx) {
        final JsonObject body = ctx.body().asJsonObject();
        final String username = body == null ? null : body.getString("username");
        final String password = body == null ? null : body.getString("password");
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            ctx.response().setStatusCode(400)
                    .putHeader("Content-Type", "application/json")
                    .end(new JsonObject().put("error", "Missing username or password").encode());
            return;
        }
        vertx.executeBlocking(() -> {
            final Session session = sessionService.login(username, password);
            final JsonObject links = new JsonObject();
            final String base = "/api/v1/user-sessions/" + session.getId().value();
            if ("SENDER".equals(session.getRole())) {
                links.put("createDeliveryLink", base + "/create-delivery");
                links.put("trackDeliveryLink", base + "/track-delivery");
            } else if ("ADMIN".equals(session.getRole())) {
                links.put("fleetLink", base + "/admin/fleet");
                links.put("schedulingLink", base + "/admin/scheduling");
            }
            return new JsonObject()
                    .put("sessionId", session.getId().value())
                    .put("accountId", session.getAccountId())
                    .put("role", session.getRole())
                    .put("links", links);
        }, false).onComplete(ar -> {
            if (ar.succeeded()) {
                ctx.response().setStatusCode(200)
                        .putHeader("Content-Type", "application/json")
                        .end(ar.result().encode());
            } else {
                ctx.response().setStatusCode(401)
                        .putHeader("Content-Type", "application/json")
                        .end(new JsonObject().put("error", causeMessage(ar.cause())).encode());
            }
        });
    }

    private void handleIntrospect(final RoutingContext ctx) {
        final SessionId sessionId = SessionId.of(ctx.pathParam("sessionId"));
        sessionService.getSession(sessionId).ifPresentOrElse(
                session -> writeJson(ctx, 200, new JsonObject()
                        .put("accountId", session.getAccountId())
                        .put("role", session.getRole())),
                () -> writeJson(ctx, 401, new JsonObject().put("error", "Invalid or expired session")));
    }

    private void writeJson(final RoutingContext ctx, final int statusCode, final JsonObject body) {
        ctx.response().setStatusCode(statusCode)
                .putHeader("Content-Type", "application/json")
                .end(body.encode());
    }

    private static String causeMessage(final Throwable t) {
        return t == null || t.getMessage() == null ? "Error" : t.getMessage();
    }
}
