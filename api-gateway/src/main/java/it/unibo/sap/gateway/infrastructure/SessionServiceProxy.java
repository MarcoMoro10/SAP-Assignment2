package it.unibo.sap.gateway.infrastructure;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import it.unibo.sap.common.hexagonal.OutputAdapter;

import java.util.concurrent.CompletableFuture;

public class SessionServiceProxy implements OutputAdapter {

    private static final long HEALTH_TIMEOUT_MS = 2000;
    private static final long REQUEST_TIMEOUT_MS = 10_000;

    private final WebClient webClient;
    private final String host;
    private final int port;

    public SessionServiceProxy(final WebClient webClient, final String host, final int port) {
        this.webClient = webClient;
        this.host = host;
        this.port = port;
    }

    public Future<Boolean> pingHealth() {
        return webClient.get(port, host, "/api/v1/health")
                .timeout(HEALTH_TIMEOUT_MS)
                .send()
                .map(resp -> resp.statusCode() == 200)
                .otherwise(false);
    }

    public JsonObject login(final String username, final String password) {
        final CompletableFuture<JsonObject> future = new CompletableFuture<>();
        final JsonObject body = new JsonObject()
                .put("username", username)
                .put("password", password);
        webClient.post(port, host, "/api/v1/login")
                .timeout(REQUEST_TIMEOUT_MS)
                .sendJsonObject(body, ar -> {
                    if (ar.succeeded()) {
                        future.complete(bodyWithStatus(ar.result()));
                    } else {
                        future.complete(new JsonObject()
                                .put("_statusCode", 502)
                                .put("error", "session-service unreachable"));
                    }
                });
        try {
            return future.get();
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            return new JsonObject().put("_statusCode", 502).put("error", "session-service unreachable");
        } catch (final Exception e) {
            return new JsonObject().put("_statusCode", 502).put("error", "session-service unreachable");
        }
    }

    private static JsonObject bodyWithStatus(final HttpResponse<io.vertx.core.buffer.Buffer> resp) {
        JsonObject body;
        try {
            body = resp.bodyAsJsonObject();
        } catch (final RuntimeException notJson) {
            body = null;
        }
        if (body == null) {
            body = new JsonObject();
        }
        return body.put("_statusCode", resp.statusCode());
    }
}
