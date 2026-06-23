package it.unibo.sap.acceptance.support;

import io.vertx.core.json.JsonObject;
import it.unibo.sap.session.domain.SessionId;

/**
 * Per-scenario shared state. Cucumber re-creates step classes for every scenario, but
 * several step classes participate in the same scenario (e.g. the login Background lives
 * in one class while the action lives in another). Rather than rely on a DI container,
 * the steps share this small singleton and the {@code @Before} hook resets it.
 */
public final class World {

    private static final World INSTANCE = new World();

    private SessionId sessionId;
    private String role;
    private JsonObject lastResponse;
    private String lastError = "";

    private World() {
    }

    public static World get() {
        return INSTANCE;
    }

    public void reset() {
        this.sessionId = null;
        this.role = null;
        this.lastResponse = null;
        this.lastError = "";
    }

    public SessionId sessionId() {
        return sessionId;
    }

    public void setSessionId(final SessionId sessionId) {
        this.sessionId = sessionId;
    }

    public String role() {
        return role;
    }

    public void setRole(final String role) {
        this.role = role;
    }

    public JsonObject lastResponse() {
        return lastResponse;
    }

    public void setLastResponse(final JsonObject lastResponse) {
        this.lastResponse = lastResponse;
    }

    public String lastError() {
        return lastError;
    }

    public void setLastError(final String lastError) {
        this.lastError = lastError == null ? "" : lastError;
    }

    public void clearOutcome() {
        this.lastResponse = null;
        this.lastError = "";
    }
}
