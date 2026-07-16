package it.unibo.sap.delivery.infrastructure;

import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;
import it.unibo.sap.common.hexagonal.OutputAdapter;
import it.unibo.sap.delivery.application.SessionValidatorPort;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public class SessionValidatorProxy implements SessionValidatorPort, OutputAdapter {

    private final WebClient webClient;
    private final String sessionHost;
    private final int sessionPort;

    public SessionValidatorProxy(final WebClient webClient, final String sessionHost, final int sessionPort) {
        this.webClient = webClient;
        this.sessionHost = sessionHost;
        this.sessionPort = sessionPort;
    }

    @Override
    public Optional<ValidatedCaller> validate(final String sessionId) {
        final CompletableFuture<Optional<ValidatedCaller>> future = new CompletableFuture<>();
        webClient.get(sessionPort, sessionHost, "/api/v1/user-sessions/" + sessionId)
                .send(ar -> {
                    if (ar.succeeded() && ar.result().statusCode() == 200) {
                        final JsonObject body = ar.result().bodyAsJsonObject();
                        future.complete(Optional.of(new ValidatedCaller(
                                body.getString("accountId"), body.getString("role"))));
                    } else {
                        future.complete(Optional.empty());
                    }
                });
        try {
            return future.get();
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        } catch (final Exception e) {
            return Optional.empty();
        }
    }
}
