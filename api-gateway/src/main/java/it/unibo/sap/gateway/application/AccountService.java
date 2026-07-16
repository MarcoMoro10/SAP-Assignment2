package it.unibo.sap.gateway.application;

import io.vertx.core.json.JsonObject;
import it.unibo.sap.common.hexagonal.OutputPort;

public interface AccountService extends OutputPort {

    JsonObject register(String username, String password);
}
