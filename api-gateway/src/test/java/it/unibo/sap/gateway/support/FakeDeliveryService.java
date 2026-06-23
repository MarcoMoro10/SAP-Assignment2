package it.unibo.sap.gateway.support;

import io.vertx.core.json.JsonObject;
import it.unibo.sap.gateway.application.DeliveryService;

import java.util.Optional;

/**
 * Hand-written mock of the gateway's {@link DeliveryService} outbound port (the DeliveryServiceProxy)
 * for solitary unit tests. Records the last request/arguments and returns canned responses.
 */
public final class FakeDeliveryService implements DeliveryService {

    public JsonObject lastCreateRequest;
    public String lastCancelDeliveryId;
    public String lastSenderId;
    public boolean viewFleetCalled;
    public String lastSchedulingDroneFilter;

    @Override
    public JsonObject createDelivery(final JsonObject request) {
        this.lastCreateRequest = request;
        this.lastSenderId = request.getString("senderId");
        return new JsonObject().put("deliveryId", "DLV-1").put("status", "IN_PROGRESS");
    }

    @Override
    public JsonObject cancelDelivery(final String deliveryId, final String senderId) {
        this.lastCancelDeliveryId = deliveryId;
        this.lastSenderId = senderId;
        return new JsonObject().put("deliveryId", deliveryId).put("status", "CANCELLED");
    }

    @Override
    public Optional<JsonObject> getDelivery(final String deliveryId, final String senderId) {
        this.lastSenderId = senderId;
        return Optional.of(new JsonObject().put("deliveryId", deliveryId).put("status", "IN_PROGRESS"));
    }

    @Override
    public JsonObject trackDelivery(final String deliveryId, final String senderId) {
        this.lastSenderId = senderId;
        return new JsonObject().put("deliveryId", deliveryId).put("trackingSessionId", "TRK-1");
    }

    @Override
    public JsonObject viewFleet() {
        this.viewFleetCalled = true;
        return new JsonObject().put("fleet", new io.vertx.core.json.JsonArray());
    }

    @Override
    public JsonObject viewScheduling(final String droneId) {
        this.lastSchedulingDroneFilter = droneId;
        return new JsonObject().put("scheduling", new io.vertx.core.json.JsonArray());
    }
}
