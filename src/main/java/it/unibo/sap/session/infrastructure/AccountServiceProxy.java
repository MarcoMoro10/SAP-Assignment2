package it.unibo.sap.session.infrastructure;

import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;
import it.unibo.sap.common.hexagonal.OutputAdapter;
import it.unibo.sap.session.application.AccountService;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public class AccountServiceProxy implements AccountService, OutputAdapter {

    private final WebClient webClient;
    private final String host;
    private final int port;

    public AccountServiceProxy(final WebClient webClient, final String host, final int port) {
        this.webClient = webClient;
        this.host = host;
        this.port = port;
    }

    @Override
    public Optional<JsonObject> login(final String username, final String password) {
        final CompletableFuture<Optional<JsonObject>> future = new CompletableFuture<>();
        final JsonObject body = new JsonObject()
                .put("username", username)
                .put("password", password);
        webClient.post(port, host, "/api/v1/accounts/login")
                .sendJsonObject(body, ar -> {
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
}
