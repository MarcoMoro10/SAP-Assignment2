package it.unibo.sap.delivery.infrastructure;

import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import it.unibo.sap.delivery.application.SessionValidatorPort;
import it.unibo.sap.delivery.application.SessionValidatorPort.ValidatedCaller;

import java.util.Optional;

public class RequestAuthorizer {

    static final String SESSION_ID_HEADER = "X-Session-Id";

    public static final String CALLER_ACCOUNT_ID = "callerAccountId";

    private final SessionValidatorPort sessionValidator;

    public RequestAuthorizer(final SessionValidatorPort sessionValidator) {
        this.sessionValidator = sessionValidator;
    }

    public Handler<RoutingContext> requireRole(final String requiredRole) {
        return ctx -> {
            final String sessionId = ctx.request().getHeader(SESSION_ID_HEADER);
            if (sessionId == null || sessionId.isBlank()) {
                reject(ctx, 401, "Missing session identity");
                return;
            }
            final Optional<ValidatedCaller> caller = sessionValidator.validate(sessionId);
            if (caller.isEmpty()) {
                reject(ctx, 401, "Invalid or expired session");
                return;
            }
            if (!requiredRole.equals(caller.get().role())) {
                reject(ctx, 403, "Forbidden: requires " + requiredRole + " role");
                return;
            }
            ctx.put(CALLER_ACCOUNT_ID, caller.get().accountId());
            ctx.next();
        };
    }

    private void reject(final RoutingContext ctx, final int status, final String message) {
        ctx.response().setStatusCode(status)
                .putHeader("Content-Type", "application/json")
                .end(new JsonObject().put("error", message).encode());
    }
}
