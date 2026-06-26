package it.unibo.sap.gateway.support;

import io.vertx.core.json.JsonObject;
import it.unibo.sap.gateway.application.AccountService;

import java.util.Optional;

/**
 * Hand-written mock of the gateway's {@link AccountService} outbound port (the AccountServiceProxy)
 * for solitary unit tests of the session/orchestration logic.
 */
public final class FakeAccountService implements AccountService {

    private JsonObject loginResult;
    private JsonObject registerResult =
            new JsonObject().put("_statusCode", 201).put("accountId", "acc-1").put("role", "SENDER");

    public FakeAccountService withSuccessfulLogin(final String accountId, final String role) {
        this.loginResult = new JsonObject().put("accountId", accountId).put("role", role);
        return this;
    }

    public FakeAccountService withFailedLogin() {
        this.loginResult = null;
        return this;
    }

    @Override
    public Optional<JsonObject> login(final String username, final String password) {
        return Optional.ofNullable(loginResult);
    }

    @Override
    public JsonObject register(final String username, final String password) {
        return registerResult;
    }
}
