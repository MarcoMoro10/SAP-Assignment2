package it.unibo.sap.gateway.infrastructure;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;
import it.unibo.sap.common.hexagonal.OutputAdapter;
import it.unibo.sap.gateway.application.AccountService;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public class AccountServiceProxy implements AccountService, OutputAdapter {

    private static final long HEALTH_TIMEOUT_MS = 2000;
    private static final long REQUEST_TIMEOUT_MS = 10_000;

    private final WebClient webClient;
    private final String host;
    private final int port;
    private final CircuitBreaker circuitBreaker;

    public AccountServiceProxy(final WebClient webClient, final String host, final int port) {
        this(webClient, host, port, new CircuitBreaker());
    }

    public AccountServiceProxy(final WebClient webClient, final String host, final int port,
                              final CircuitBreaker circuitBreaker) {
        this.webClient = webClient;
        this.host = host;
        this.port = port;
        this.circuitBreaker = circuitBreaker;
    }

    public Future<Boolean> pingHealth() {
        return webClient.get(port, host, "/api/v1/health")
                .timeout(HEALTH_TIMEOUT_MS)
                .send()
                .map(resp -> resp.statusCode() == 200)
                .otherwise(false);
    }

    @Override
    public Optional<JsonObject> login(final String username, final String password) {
        if (circuitBreaker.isOpen()) {
            attemptRecovery();
            return Optional.empty();
        }
        final CompletableFuture<Optional<JsonObject>> future = new CompletableFuture<>();
        final JsonObject body = new JsonObject()
                .put("username", username)
                .put("password", password);
        webClient.post(port, host, "/api/v1/accounts/login")
                .timeout(REQUEST_TIMEOUT_MS)
                .sendJsonObject(body, ar -> {
                    if (ar.failed()) {
                        circuitBreaker.recordFailure();
                        future.complete(Optional.empty());
                        return;
                    }
                    final int statusCode = ar.result().statusCode();
                    if (isDownstreamHealthy(statusCode)) {
                        circuitBreaker.recordSuccess();
                    } else {
                        circuitBreaker.recordFailure();
                    }
                    if (statusCode == 200) {
                        future.complete(Optional.of(ar.result().bodyAsJsonObject()));
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

    @Override
    public JsonObject register(final String username, final String password) {
        if (circuitBreaker.isOpen()) {
            attemptRecovery();
            return circuitOpenResponse();
        }
        final CompletableFuture<JsonObject> future = new CompletableFuture<>();
        final JsonObject body = new JsonObject()
                .put("username", username)
                .put("password", password);
        webClient.post(port, host, "/api/v1/accounts")
                .timeout(REQUEST_TIMEOUT_MS)
                .sendJsonObject(body, ar -> {
                    if (ar.failed()) {
                        circuitBreaker.recordFailure();
                        future.complete(circuitOpenResponse());
                        return;
                    }
                    final int statusCode = ar.result().statusCode();
                    if (isDownstreamHealthy(statusCode)) {
                        circuitBreaker.recordSuccess();
                    } else {
                        circuitBreaker.recordFailure();
                    }
                    future.complete(bodyWithStatus(ar.result(), statusCode));
                });
        try {
            return future.get();
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            return circuitOpenResponse();
        } catch (final Exception e) {
            return circuitOpenResponse();
        }
    }

    private static boolean isDownstreamHealthy(final int statusCode) {
        return statusCode < 500;
    }

    private static JsonObject bodyWithStatus(
            final io.vertx.ext.web.client.HttpResponse<io.vertx.core.buffer.Buffer> resp,
            final int statusCode) {
        JsonObject body;
        try {
            body = resp.bodyAsJsonObject();
        } catch (final RuntimeException notJson) {
            body = null;
        }
        if (body == null) {
            body = new JsonObject();
        }
        return body.put("_statusCode", statusCode);
    }

    private static JsonObject circuitOpenResponse() {
        return new JsonObject()
                .put("_statusCode", 503)
                .put("error", "account-service unavailable");
    }

    private void attemptRecovery() {
        if (circuitBreaker.tryStartProbe()) {
            pingHealth().onComplete(ar -> {
                if (ar.succeeded() && Boolean.TRUE.equals(ar.result())) {
                    circuitBreaker.probeSucceeded();
                } else {
                    circuitBreaker.probeFailed();
                }
            });
        }
    }
}
