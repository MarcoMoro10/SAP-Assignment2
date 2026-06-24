package it.unibo.sap.gateway.infrastructure;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.http.ServerWebSocket;
import io.vertx.core.http.WebSocketClient;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.HttpRequest;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import it.unibo.sap.common.hexagonal.OutputAdapter;
import it.unibo.sap.gateway.application.DeliveryService;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

public class DeliveryServiceProxy implements DeliveryService, OutputAdapter {

    private final WebClient webClient;
    private final String host;
    private final int port;
    private final int fleetPort;

    private static final long HEALTH_TIMEOUT_MS = 2000;

    public DeliveryServiceProxy(final WebClient webClient, final String host,
                                final int port, final int fleetPort) {
        this.webClient = webClient;
        this.host = host;
        this.port = port;
        this.fleetPort = fleetPort;
    }

    public Future<Boolean> pingHealth() {
        return webClient.get(port, host, "/api/v1/health")
                .timeout(HEALTH_TIMEOUT_MS)
                .send()
                .map(resp -> resp.statusCode() == 200)
                .otherwise(false);
    }

    @Override
    public JsonObject createDelivery(final JsonObject request) {
        return blocking(
                webClient.post(port, host, "/api/v1/deliveries"),
                request,
                resp -> resp.bodyAsJsonObject().put("_statusCode", resp.statusCode()));
    }

    @Override
    public JsonObject cancelDelivery(final String deliveryId, final String senderId) {
        return blocking(
                webClient.post(port, host, "/api/v1/deliveries/" + deliveryId + "/cancel"),
                new JsonObject().put("senderId", senderId),
                resp -> resp.bodyAsJsonObject().put("_statusCode", resp.statusCode()));
    }

    @Override
    public Optional<JsonObject> getDelivery(final String deliveryId, final String senderId) {
        final CompletableFuture<Optional<JsonObject>> future = new CompletableFuture<>();
        webClient.get(port, host, "/api/v1/deliveries/" + deliveryId)
                .addQueryParam("senderId", senderId)
                .send(ar -> {
                    if (ar.succeeded() && ar.result().statusCode() == 200) {
                        future.complete(Optional.of(ar.result().bodyAsJsonObject()));
                    } else {
                        future.complete(Optional.empty());
                    }
                });
        try {
            return future.get();
        } catch (final Exception e) {
            return Optional.empty();
        }
    }

    @Override
    public JsonObject trackDelivery(final String deliveryId, final String senderId) {
        return blocking(
                webClient.post(port, host, "/api/v1/deliveries/" + deliveryId + "/track"),
                new JsonObject().put("senderId", senderId),
                HttpResponse::bodyAsJsonObject);
    }

    @Override
    public JsonObject viewFleet() {
        return blocking(
                webClient.get(fleetPort, host, "/api/v1/admin/fleet"),
                resp -> new JsonObject().put("fleet", resp.bodyAsJsonArray()));
    }

    @Override
    public JsonObject viewScheduling(final String droneId) {
        String path = "/api/v1/admin/scheduling";
        if (droneId != null && !droneId.isBlank()) {
            path += "?droneId=" + droneId;
        }
        return blocking(
                webClient.get(fleetPort, host, path),
                resp -> new JsonObject().put("scheduling", resp.bodyAsJsonArray()));
    }

    private static final long CLIENT_CLOSE_GRACE_MS = 200;

    public void openTrackingRelay(final Vertx vertx,
                                  final WebSocketClient wsClient,
                                  final ServerWebSocket clientSocket,
                                  final String trackingSessionId,
                                  final String firstFrame) {
        wsClient.connect(port, host, "/api/v1/track/" + trackingSessionId)
                .onSuccess(deliverySocket -> {
                    deliverySocket.writeTextMessage(firstFrame);
                    deliverySocket.textMessageHandler(clientSocket::writeTextMessage);
                    clientSocket.textMessageHandler(deliverySocket::writeTextMessage);
                    clientSocket.closeHandler(v -> deliverySocket.close());
                    deliverySocket.closeHandler(v -> vertx.setTimer(CLIENT_CLOSE_GRACE_MS, id -> clientSocket.close()));
                })
                .onFailure(err -> clientSocket.close());
    }

    private JsonObject blocking(final HttpRequest<io.vertx.core.buffer.Buffer> request,
                                final Function<HttpResponse<io.vertx.core.buffer.Buffer>, JsonObject> onSuccess) {
        final CompletableFuture<JsonObject> future = new CompletableFuture<>();
        request.send(ar -> {
            if (ar.succeeded()) {
                future.complete(onSuccess.apply(ar.result()));
            } else {
                future.completeExceptionally(ar.cause());
            }
        });
        return await(future);
    }

    private JsonObject blocking(final HttpRequest<io.vertx.core.buffer.Buffer> request,
                                final JsonObject body,
                                final Function<HttpResponse<io.vertx.core.buffer.Buffer>, JsonObject> onSuccess) {
        final CompletableFuture<JsonObject> future = new CompletableFuture<>();
        request.sendJsonObject(body, ar -> {
            if (ar.succeeded()) {
                future.complete(onSuccess.apply(ar.result()));
            } else {
                future.completeExceptionally(ar.cause());
            }
        });
        return await(future);
    }

    private JsonObject await(final CompletableFuture<JsonObject> future) {
        try {
            return future.get();
        } catch (final Exception e) {
            throw new RuntimeException("Failed to contact delivery-service", e);
        }
    }
}