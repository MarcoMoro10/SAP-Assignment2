package it.unibo.sap.session.support;

import io.vertx.core.json.JsonObject;
import it.unibo.sap.session.application.AccountService;

import java.util.Optional;

/**
 * In-memory fake of the account-service login port, so the session-service can be tested in
 * isolation (no HTTP to a real account-service). Configure the account that a given
 * username/password resolves to; any other credential is rejected (empty).
 */
public class FakeAccountService implements AccountService {

    private String username;
    private String password;
    private String accountId;
    private String role;

    public FakeAccountService withSuccessfulLogin(final String username, final String password,
                                                  final String accountId, final String role) {
        this.username = username;
        this.password = password;
        this.accountId = accountId;
        this.role = role;
        return this;
    }

    @Override
    public Optional<JsonObject> login(final String username, final String password) {
        if (accountId != null && this.username.equals(username) && this.password.equals(password)) {
            return Optional.of(new JsonObject().put("accountId", accountId).put("role", role));
        }
        return Optional.empty();
    }
}
