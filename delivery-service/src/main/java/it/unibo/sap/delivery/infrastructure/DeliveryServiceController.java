package it.unibo.sap.delivery.infrastructure;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
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
import it.unibo.sap.delivery.domain.deliveries.DeliveryTrackingView;

import java.time.LocalDateTime;

public class DeliveryServiceController extends AbstractVerticle implements InputAdapter {

    private final DeliveryService deliveryService;
    private final int port;

    public DeliveryServiceController(final DeliveryService deliveryService, final int port) {
        this.deliveryService = deliveryService;
        this.port = port;
    }

    @Override
    public void start(final Promise<Void> startPromise) {
        final Router router = Router.router(vertx);
        router.route("/api/v1/*").handler(BodyHandler.create());
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

    private void handleCreate(final RoutingContext ctx) {
        final JsonObject body = ctx.body().asJsonObject();
        try {
            final CreateDeliveryCommand cmd = toCommand(body);
            final CreateDeliveryResult result = deliveryService.createDelivery(cmd);
            final JsonObject reply = new JsonObject()
                    .put("deliveryId", result.deliveryId())
                    .put("status", result.status());
            if (result.assignedDroneId() != null) {
                reply.put("assignedDroneId", result.assignedDroneId());
            }
            ctx.response().setStatusCode(201)
                    .putHeader("Content-Type", "application/json")
                    .end(reply.encode());
        } catch (final BadRequestException e) {
            error(ctx, 400, e.getMessage());
        } catch (final ValidationRejectedException e) {
            error(ctx, 422, e.getMessage());
        } catch (final IllegalArgumentException e) {
            error(ctx, 400, e.getMessage());
        }
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
        try {
            deliveryService.cancelDelivery(deliveryId, senderId);
            ctx.response().setStatusCode(200)
                    .putHeader("Content-Type", "application/json")
                    .end(new JsonObject()
                            .put("deliveryId", deliveryId)
                            .put("status", "CANCELLED").encode());
        } catch (final CannotCancelInFlightException e) {
            error(ctx, 409, e.getMessage());
        } catch (final DeliveryNotFoundException e) {
            error(ctx, 404, e.getMessage());
        } catch (final IllegalStateException e) {
            error(ctx, 409, e.getMessage());
        }
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
            vertx.eventBus().consumer(address, msg ->
                    webSocket.writeTextMessage(((JsonObject) msg.body()).encode()));
        });
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
        if (body.containsKey("deadlineMinutes") && deadlineMinutes <= 0) {
            throw new BadRequestException("deadlineMinutes must be greater than 0");
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
                .put("estimatedTimeRemainingFormatted", formatEtr(v.estimatedTimeRemainingSeconds()));
    }

    private static String formatEtr(final long totalSeconds) {
        final long s = Math.max(0, totalSeconds);
        return String.format("%02d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60);
    }

    private void error(final RoutingContext ctx, final int status, final String message) {
        ctx.response().setStatusCode(status)
                .putHeader("Content-Type", "application/json")
                .end(new JsonObject().put("error", message).encode());
    }
}