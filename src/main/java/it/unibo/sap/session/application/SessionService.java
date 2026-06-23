package it.unibo.sap.session.application;

import io.vertx.core.json.JsonObject;
import it.unibo.sap.common.hexagonal.InputPort;
import it.unibo.sap.session.domain.Session;
import it.unibo.sap.session.domain.SessionId;

import java.util.Optional;

public interface SessionService extends InputPort {

    Session login(String username, String password);

    Optional<Session> getSession(SessionId sessionId);

    JsonObject createDelivery(SessionId sessionId, JsonObject request);

    JsonObject cancelDelivery(SessionId sessionId, String deliveryId);

    Optional<JsonObject> getDelivery(SessionId sessionId, String deliveryId);

    JsonObject trackDelivery(SessionId sessionId, String deliveryId);

    JsonObject viewFleet(SessionId sessionId);

    JsonObject viewScheduling(SessionId sessionId, String droneId);
}
