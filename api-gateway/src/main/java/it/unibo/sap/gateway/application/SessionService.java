package it.unibo.sap.gateway.application;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import it.unibo.sap.common.hexagonal.OutputPort;

public interface SessionService extends OutputPort {
    JsonObject login(String username, String password);
    Future<Boolean> pingHealth();
}
