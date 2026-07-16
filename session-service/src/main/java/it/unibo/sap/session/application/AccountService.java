package it.unibo.sap.session.application;

import io.vertx.core.json.JsonObject;
import it.unibo.sap.common.hexagonal.OutputPort;

import java.util.Optional;

public interface AccountService extends OutputPort {

    Optional<JsonObject> login(String username, String password);
}
