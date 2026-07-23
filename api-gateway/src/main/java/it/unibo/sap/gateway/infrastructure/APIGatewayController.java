package it.unibo.sap.gateway.infrastructure;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.http.ServerWebSocket;
import io.vertx.core.http.WebSocketClient;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import it.unibo.sap.common.hexagonal.InputAdapter;
import it.unibo.sap.gateway.application.AccountService;
import it.unibo.sap.gateway.application.ControllerObserver;
import it.unibo.sap.gateway.application.DeliveryService;
import it.unibo.sap.gateway.application.SessionService;

import java.util.concurrent.atomic.AtomicBoolean;

public class APIGatewayController extends AbstractVerticle implements InputAdapter {

    private static final String TRACK_PREFIX = "/api/v1/track/";

    private final AccountService accountServiceProxy;
    private final DeliveryService deliveryServiceProxy;
    private final SessionService sessionServiceProxy;
    private final String publicHost;
    private final int port;
    private final ControllerObserver observer;
    private WebSocketClient webSocketClient;

    public APIGatewayController(final AccountService accountServiceProxy,
                                final DeliveryService deliveryServiceProxy,
                                final SessionService sessionServiceProxy,
                                final String publicHost,
                                final int port) {
        this(accountServiceProxy, deliveryServiceProxy, sessionServiceProxy, publicHost, port,
                ControllerObserver.NO_OP);
    }

    public APIGatewayController(final AccountService accountServiceProxy,
                                final DeliveryService deliveryServiceProxy,
                                final SessionService sessionServiceProxy,
                                final String publicHost,
                                final int port,
                                final ControllerObserver observer) {
        this.accountServiceProxy = accountServiceProxy;
        this.deliveryServiceProxy = deliveryServiceProxy;
        this.sessionServiceProxy = sessionServiceProxy;
        this.publicHost = publicHost;
        this.port = port;
        this.observer = observer;
    }

    @Override
    public void start(final Promise<Void> startPromise) {
        final Router router = Router.router(vertx);
        router.route("/api/v1/*").handler(BodyHandler.create());
        router.route("/api/v1/*").handler(this::observeRequest);
        router.get("/api/v1/health/live").handler(this::handleLiveness);
        router.get("/api/v1/health").handler(this::handleHealth);
        router.post("/api/v1/accounts").handler(this::handleRegister);
        router.post("/api/v1/login").handler(this::handleLogin);
        router.post("/api/v1/user-sessions/:sessionId/create-delivery").handler(this::handleCreateDelivery);
        router.post("/api/v1/user-sessions/:sessionId/cancel-delivery").handler(this::handleCancelDelivery);
        router.post("/api/v1/user-sessions/:sessionId/track-delivery").handler(this::handleTrackDelivery);
        router.get("/api/v1/user-sessions/:sessionId/deliveries/:deliveryId").handler(this::handleGetDelivery);
        router.get("/api/v1/user-sessions/:sessionId/admin/fleet").handler(this::handleViewFleet);
        router.get("/api/v1/user-sessions/:sessionId/admin/scheduling").handler(this::handleViewScheduling);

        this.webSocketClient = vertx.createWebSocketClient();

        final var server = vertx.createHttpServer();
        server.webSocketHandler(this::handleTrackingRelay);

        server.requestHandler(router)
                .listen(port, http -> {
                    if (http.succeeded()) {
                        System.out.println("api-gateway ready - port: " + port);
                        startPromise.complete();
                    } else {
                        startPromise.fail(http.cause());
                    }
                });
    }

    private void observeRequest(final RoutingContext ctx) {
        observer.notifyNewRESTRequest();
        final long startNanos = System.nanoTime();
        ctx.addEndHandler(ar -> observer.recordResponseTime((System.nanoTime() - startNanos) / 1_000_000_000.0));
        ctx.next();
    }

    private void handleLiveness(final RoutingContext ctx) {
        ctx.response().setStatusCode(200)
                .putHeader("Content-Type", "application/json")
                .end(new JsonObject().put("status", "UP").encode());
    }

    private void handleHealth(final RoutingContext ctx) {
        final Future<Boolean> account = accountServiceProxy.pingHealth();
        final Future<Boolean> delivery = deliveryServiceProxy.pingHealth();
        final Future<Boolean> session = sessionServiceProxy.pingHealth();
        Future.all(account, delivery, session).onComplete(ar -> {
            final boolean ready = Boolean.TRUE.equals(account.result())
                    && Boolean.TRUE.equals(delivery.result())
                    && Boolean.TRUE.equals(session.result());
            final JsonArray checks = new JsonArray()
                    .add(check("account-service", account.result()))
                    .add(check("delivery-service", delivery.result()))
                    .add(check("session-service", session.result()));
            ctx.response().setStatusCode(ready ? 200 : 503)
                    .putHeader("Content-Type", "application/json")
                    .end(new JsonObject().put("status", ready ? "UP" : "DOWN").put("checks", checks).encode());
        });
    }

    private static JsonObject check(final String name, final Boolean up) {
        return new JsonObject().put("name", name).put("status", Boolean.TRUE.equals(up) ? "UP" : "DOWN");
    }

    private void handleTrackingRelay(final ServerWebSocket clientSocket) {
        final String path = clientSocket.path();
        if (path == null || !path.startsWith(TRACK_PREFIX)) {
            clientSocket.reject();
            return;
        }
        final String trackingSessionId = path.substring(TRACK_PREFIX.length());
        final AtomicBoolean relayOpened = new AtomicBoolean(false);
        clientSocket.textMessageHandler(firstFrame -> {
            if (relayOpened.compareAndSet(false, true)) {
                deliveryServiceProxy.openTrackingRelay(
                        vertx, webSocketClient, clientSocket, trackingSessionId, firstFrame);
            }
        });
    }

    private void handleRegister(final RoutingContext ctx) {
        final JsonObject body = ctx.body().asJsonObject();
        final String username = body == null ? null : body.getString("username");
        final String password = body == null ? null : body.getString("password");
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            ctx.response().setStatusCode(400)
                    .putHeader("Content-Type", "application/json")
                    .end(new JsonObject().put("error", "Missing username or password").encode());
            return;
        }
        vertx.executeBlocking(() -> accountServiceProxy.register(username, password), false)
                .onComplete(ar -> {
                    if (ar.succeeded()) {
                        writeWithEmbeddedStatus(ctx, ar.result(), 201);
                    } else {
                        ctx.response().setStatusCode(502)
                                .putHeader("Content-Type", "application/json")
                                .end(new JsonObject().put("error", causeMessage(ar.cause())).encode());
                    }
                });
    }

    private void handleLogin(final RoutingContext ctx) {
        final JsonObject body = ctx.body().asJsonObject();
        final String username = body == null ? null : body.getString("username");
        final String password = body == null ? null : body.getString("password");
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            ctx.response().setStatusCode(400)
                    .end(new JsonObject().put("error", "Missing username or password").encode());
            return;
        }
        vertx.executeBlocking(() -> sessionServiceProxy.login(username, password), false)
                .onComplete(ar -> {
                    if (ar.succeeded()) {
                        writeWithEmbeddedStatus(ctx, ar.result(), 200);
                    } else {
                        ctx.response().setStatusCode(502)
                                .putHeader("Content-Type", "application/json")
                                .end(new JsonObject().put("error", causeMessage(ar.cause())).encode());
                    }
                });
    }

    private void handleCreateDelivery(final RoutingContext ctx) {
        final String sessionId = ctx.pathParam("sessionId");
        final JsonObject body = ctx.body().asJsonObject();
        dispatch(ctx, () -> deliveryServiceProxy.createDelivery(body, sessionId),
                result -> writeWithEmbeddedStatus(ctx, result, 201));
    }

    private void handleCancelDelivery(final RoutingContext ctx) {
        final String sessionId = ctx.pathParam("sessionId");
        final JsonObject body = ctx.body().asJsonObject();
        final String deliveryId = body == null ? null : body.getString("deliveryId");
        dispatch(ctx, () -> deliveryServiceProxy.cancelDelivery(deliveryId, sessionId),
                result -> writeWithEmbeddedStatus(ctx, result, 200));
    }

    private void handleTrackDelivery(final RoutingContext ctx) {
        final String sessionId = ctx.pathParam("sessionId");
        final JsonObject body = ctx.body().asJsonObject();
        final String deliveryId = body == null ? null : body.getString("deliveryId");
        dispatch(ctx, () -> deliveryServiceProxy.trackDelivery(deliveryId, sessionId),
                result -> {
                    final int status = result.containsKey("_statusCode")
                            ? result.getInteger("_statusCode") : 200;
                    result.remove("_statusCode");
                    if (status >= 200 && status < 300) {
                        writeJson(ctx, 200, rewriteTrackingUrl(result));
                    } else {
                        writeJson(ctx, status, result);
                    }
                });
    }

    private JsonObject rewriteTrackingUrl(final JsonObject deliveryResponse) {
        final String trackingSessionId = deliveryResponse.getString("trackingSessionId");
        if (trackingSessionId == null || trackingSessionId.isBlank()) {
            return deliveryResponse.copy();
        }
        return deliveryResponse.copy()
                .put("webSocketUrl", "ws://" + publicHost + ":" + port + TRACK_PREFIX + trackingSessionId);
    }

    private void handleGetDelivery(final RoutingContext ctx) {
        final String sessionId = ctx.pathParam("sessionId");
        final String deliveryId = ctx.pathParam("deliveryId");
        dispatch(ctx, () -> deliveryServiceProxy.getDelivery(deliveryId, sessionId),
                opt -> opt.ifPresentOrElse(
                        delivery -> writeJson(ctx, 200, delivery),
                        () -> writeJson(ctx, 404, new JsonObject().put("error", "Delivery not found"))));
    }

    private void handleViewFleet(final RoutingContext ctx) {
        final String sessionId = ctx.pathParam("sessionId");
        dispatch(ctx, () -> deliveryServiceProxy.viewFleet(sessionId),
                result -> writeJsonArray(ctx, result, "fleet"));
    }

    private void handleViewScheduling(final RoutingContext ctx) {
        final String sessionId = ctx.pathParam("sessionId");
        final String droneId = ctx.queryParams().get("droneId");
        dispatch(ctx, () -> deliveryServiceProxy.viewScheduling(droneId, sessionId),
                result -> writeJsonArray(ctx, result, "scheduling"));
    }

    private <T> void dispatch(final RoutingContext ctx,
                              final java.util.concurrent.Callable<T> serviceCall,
                              final java.util.function.Consumer<T> onSuccess) {
        vertx.executeBlocking(serviceCall, false)
                .onComplete(ar -> {
                    if (ar.succeeded()) {
                        onSuccess.accept(ar.result());
                    } else {
                        respondError(ctx, ar.cause());
                    }
                });
    }

    private void writeJson(final RoutingContext ctx, final int statusCode, final JsonObject body) {
        ctx.response().setStatusCode(statusCode)
                .putHeader("Content-Type", "application/json")
                .end(body.encode());
    }

    private void writeWithEmbeddedStatus(final RoutingContext ctx, final JsonObject result,
                                         final int defaultStatus) {
        final int statusCode = result.containsKey("_statusCode")
                ? result.getInteger("_statusCode") : defaultStatus;
        result.remove("_statusCode");
        writeJson(ctx, statusCode, result);
    }

    private void writeJsonArray(final RoutingContext ctx, final JsonObject wrapper, final String key) {
        if (wrapper.containsKey("_statusCode")) {
            final int statusCode = wrapper.getInteger("_statusCode");
            writeJson(ctx, statusCode, new JsonObject().put("error", wrapper.getString("error", "Error")));
            return;
        }
        ctx.response().setStatusCode(200)
                .putHeader("Content-Type", "application/json")
                .end(wrapper.getJsonArray(key).encode());
    }

    private void respondError(final RoutingContext ctx, final Throwable cause) {
        if (cause instanceof SecurityException) {
            ctx.response().setStatusCode(403)
                    .end(new JsonObject().put("error", cause.getMessage()).encode());
        } else {
            ctx.response().setStatusCode(404)
                    .end(new JsonObject().put("error", causeMessage(cause)).encode());
        }
    }

    private static String causeMessage(final Throwable t) {
        return t == null || t.getMessage() == null ? "Error" : t.getMessage();
    }
}
