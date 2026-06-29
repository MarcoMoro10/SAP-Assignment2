package it.unibo.sap.delivery.infrastructure;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.WorkerExecutor;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import it.unibo.sap.common.hexagonal.InputAdapter;
import it.unibo.sap.delivery.application.CreateDeliveryCommand;
import it.unibo.sap.delivery.application.CreateDeliveryResult;
import it.unibo.sap.delivery.application.DeliveryExceptions.BadRequestException;
import it.unibo.sap.delivery.application.DeliveryExceptions.CannotCancelInFlightException;
import it.unibo.sap.delivery.application.DeliveryExceptions.DeliveryNotFoundException;
import it.unibo.sap.delivery.application.DeliveryExceptions.ValidationRejectedException;
import it.unibo.sap.delivery.application.DeliveryService;
import it.unibo.sap.delivery.application.TrackingHandle;
import it.unibo.sap.delivery.domain.deliveries.DeliveryStatus;
import it.unibo.sap.delivery.domain.deliveries.DeliveryTrackingView;
import it.unibo.sap.delivery.domain.deliveries.EstimatedTimeRemaining;

import java.time.LocalDateTime;

public class DeliveryServiceController extends AbstractVerticle implements InputAdapter {

    public static final String DOMAIN_COMMAND_EXECUTOR = "delivery-domain-commands";

    private final DeliveryService deliveryService;
    private final int port;
    private WorkerExecutor commandExecutor;

    public DeliveryServiceController(final DeliveryService deliveryService, final int port) {
        this.deliveryService = deliveryService;
        this.port = port;
    }

    @Override
    public void start(final Promise<Void> startPromise) {
        commandExecutor = vertx.createSharedWorkerExecutor(DOMAIN_COMMAND_EXECUTOR, 1);
        final Router router = Router.router(vertx);
        router.route("/api/v1/*").handler(BodyHandler.create());
        router.get("/api/v1/health").handler(this::handleHealth);
        router.post("/api/v1/deliveries").handler(this::handleCreate);
        router.get("/api/v1/deliveries/:deliveryId").handler(this::handleGet);
        router.post("/api/v1/deliveries/:deliveryId/cancel").handler(this::handleCancel);
        router.post("/api/v1/deliveries/:deliveryId/track").handler(this::handleTrack);

        final var server = vertx.createHttpServer();
        server.webSocketHandler(this::handleTrackingSocket);

        server.requestHandler(router)
                .listen(port, http -> {
                    if (http.succeeded()) {
                        System.out.println("delivery-service ready - port: " + port);
                        startPromise.complete();
                    } else {
                        startPromise.fail(http.cause());
                    }
                });
    }

    @Override
    public void stop() {
        if (commandExecutor != null) {
            commandExecutor.close();
        }
    }

    private void handleHealth(final RoutingContext ctx) {
        ctx.response().setStatusCode(200)
                .putHeader("Content-Type", "application/json")
                .end(new JsonObject().put("status", "UP").encode());
    }

    private void handleCreate(final RoutingContext ctx) {
        final JsonObject body = ctx.body().asJsonObject();
        final CreateDeliveryCommand cmd;
        try {
            cmd = toCommand(body);
        } catch (final BadRequestException | IllegalArgumentException e) {
            error(ctx, 400, e.getMessage());
            return;
        }
        commandExecutor.<CreateDeliveryResult>executeBlocking(
                        () -> deliveryService.createDelivery(cmd), true)
                .onSuccess(result -> {
                    final JsonObject reply = new JsonObject()
                            .put("deliveryId", result.deliveryId())
                            .put("status", result.status());
                    if (result.assignedDroneId() != null) {
                        reply.put("assignedDroneId", result.assignedDroneId());
                    }
                    ctx.response().setStatusCode(201)
                            .putHeader("Content-Type", "application/json")
                            .end(reply.encode());
                })
                .onFailure(e -> {
                    if (e instanceof ValidationRejectedException) {
                        error(ctx, 422, e.getMessage());
                    } else if (e instanceof BadRequestException || e instanceof IllegalArgumentException) {
                        error(ctx, 400, e.getMessage());
                    } else {
                        error(ctx, 500, e.getMessage());
                    }
                });
    }

    private void handleGet(final RoutingContext ctx) {
        final String deliveryId = ctx.pathParam("deliveryId");
        final String senderId = ctx.queryParams().get("senderId");
        deliveryService.getDelivery(deliveryId, senderId).ifPresentOrElse(
                view -> ctx.response().setStatusCode(200)
                        .putHeader("Content-Type", "application/json")
                        .end(toJson(view).encode()),
                () -> error(ctx, 404, "Delivery not found"));
    }

    private void handleCancel(final RoutingContext ctx) {
        final String deliveryId = ctx.pathParam("deliveryId");
        final JsonObject body = ctx.body().asJsonObject();
        final String senderId = body == null ? null : body.getString("senderId");
        commandExecutor.<Void>executeBlocking(() -> {
                    deliveryService.cancelDelivery(deliveryId, senderId);
                    return null;
                }, true)
                .onSuccess(v -> ctx.response().setStatusCode(200)
                        .putHeader("Content-Type", "application/json")
                        .end(new JsonObject()
                                .put("deliveryId", deliveryId)
                                .put("status", "CANCELLED").encode()))
                .onFailure(e -> {
                    if (e instanceof CannotCancelInFlightException) {
                        error(ctx, 409, e.getMessage());
                    } else if (e instanceof DeliveryNotFoundException) {
                        error(ctx, 404, e.getMessage());
                    } else if (e instanceof IllegalStateException) {
                        error(ctx, 409, e.getMessage());
                    } else {
                        error(ctx, 500, e.getMessage());
                    }
                });
    }

    private void handleTrack(final RoutingContext ctx) {
        final String deliveryId = ctx.pathParam("deliveryId");
        final JsonObject body = ctx.body().asJsonObject();
        final String senderId = body == null ? null : body.getString("senderId");
        try {
            final TrackingHandle handle = deliveryService.startTracking(deliveryId, senderId);
            final String wsUrl = "ws://localhost:" + port + "/api/v1/track/" + handle.trackingSessionId();
            ctx.response().setStatusCode(201)
                    .putHeader("Content-Type", "application/json")
                    .end(new JsonObject()
                            .put("trackingSessionId", handle.trackingSessionId())
                            .put("deliveryId", handle.deliveryId())
                            .put("webSocketUrl", wsUrl).encode());
        } catch (final DeliveryNotFoundException e) {
            error(ctx, 404, e.getMessage());
        }
    }

    private void handleTrackingSocket(final io.vertx.core.http.ServerWebSocket webSocket) {
        if (!webSocket.path().startsWith("/api/v1/track/")) {
            webSocket.reject();
            return;
        }
        webSocket.textMessageHandler(openMsg -> {
            if (openMsg == null || openMsg.isBlank()) {
                return;
            }
            final JsonObject obj;
            try {
                obj = new JsonObject(openMsg);
            } catch (final RuntimeException e) {
                webSocket.writeTextMessage(new JsonObject()
                        .put("error", "Expected JSON {\"deliveryId\":\"...\"}").encode());
                return;
            }
            final String deliveryId = obj.getString("deliveryId");
            if (deliveryId == null || deliveryId.isBlank()) {
                webSocket.writeTextMessage(new JsonObject()
                        .put("error", "Missing deliveryId").encode());
                return;
            }
            final String address = VertxTrackingSessionEventObserver.TRACKING_ADDRESS_PREFIX + deliveryId;
            vertx.eventBus().consumer(address, msg -> {
                final JsonObject update = (JsonObject) msg.body();
                final String frame = update.encode();
                if (isTerminalStatus(update.getString("status"))) {
                    webSocket.writeTextMessage(frame, ar -> webSocket.close());
                } else {
                    webSocket.writeTextMessage(frame);
                }
            });
        });
    }

    private static boolean isTerminalStatus(final String status) {
        return DeliveryStatus.DELIVERED.name().equals(status);
    }

    private CreateDeliveryCommand toCommand(final JsonObject body) {
        if (body == null) {
            throw new BadRequestException("Missing request body");
        }
        final String senderId = body.getString("senderId");
        final double weight = body.getDouble("weight", 0.0);
        final JsonObject start = body.getJsonObject("startingPlace");
        final JsonObject dest = body.getJsonObject("destinationPlace");
        if (start == null || dest == null) {
            throw new BadRequestException("Invalid address");
        }
        final boolean immediate = body.getBoolean("immediate", true);
        final String scheduledAtRaw = body.getString("scheduledAt");
        final LocalDateTime scheduledAt = scheduledAtRaw == null ? null : parseDateTime(scheduledAtRaw);
        final long deadlineMinutes = body.getLong("deadlineMinutes", 0L);
        if (deadlineMinutes <= 0) {
            throw new BadRequestException("deadlineMinutes is required and must be greater than 0");
        }
        return new CreateDeliveryCommand(
                senderId, weight,
                start.getString("street"), start.getInteger("number", 0),
                dest.getString("street"), dest.getInteger("number", 0),
                immediate, scheduledAt, deadlineMinutes);
    }

    private LocalDateTime parseDateTime(final String raw) {
        try {
            return java.time.OffsetDateTime.parse(raw).toLocalDateTime();
        } catch (final Exception ignored) {
            try {
                return LocalDateTime.parse(raw);
            } catch (final Exception e) {
                throw new BadRequestException("Invalid shipping time");
            }
        }
    }

    private JsonObject toJson(final DeliveryTrackingView v) {
        return new JsonObject()
                .put("deliveryId", v.deliveryId())
                .put("status", v.status().name())
                .put("estimatedTimeRemainingSeconds", v.estimatedTimeRemainingSeconds())
                .put("estimatedTimeRemainingFormatted",
                        EstimatedTimeRemaining.formatSeconds(v.estimatedTimeRemainingSeconds()));
    }

    private void error(final RoutingContext ctx, final int status, final String message) {
        ctx.response().setStatusCode(status)
                .putHeader("Content-Type", "application/json")
                .end(new JsonObject().put("error", message).encode());
    }
}