package it.unibo.sap.session.application;

import io.vertx.core.json.JsonObject;
import it.unibo.sap.common.hexagonal.OutputPort;

import java.util.Optional;

public interface DeliveryService extends OutputPort {

    JsonObject createDelivery(JsonObject request);

    JsonObject cancelDelivery(String deliveryId, String senderId);

    Optional<JsonObject> getDelivery(String deliveryId, String senderId);

    JsonObject trackDelivery(String deliveryId, String senderId);

    JsonObject viewFleet();

    JsonObject viewScheduling(String droneId);
}