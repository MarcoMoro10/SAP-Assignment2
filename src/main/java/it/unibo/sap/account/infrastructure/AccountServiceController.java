package it.unibo.sap.account.infrastructure;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import it.unibo.sap.account.application.AccountService;
import it.unibo.sap.account.domain.Account;
import it.unibo.sap.account.domain.AccountId;
import it.unibo.sap.common.hexagonal.InputAdapter;

public class AccountServiceController extends AbstractVerticle implements InputAdapter {

    private final AccountService accountService;
    private final int port;

    public AccountServiceController(final AccountService accountService, final int port) {
        this.accountService = accountService;
        this.port = port;
    }

    @Override
    public void start(final Promise<Void> startPromise) {
        final Router router = Router.router(vertx);
        router.route("/api/v1/accounts*").handler(BodyHandler.create());
        router.post("/api/v1/accounts").handler(this::handleRegister);
        router.post("/api/v1/accounts/login").handler(this::handleLogin);
        router.get("/api/v1/accounts/:accountId").handler(this::handleGetAccount);

        vertx.createHttpServer()
                .requestHandler(router)
                .listen(port, http -> {
                    if (http.succeeded()) {
                        System.out.println("account-service ready - port: " + port);
                        startPromise.complete();
                    } else {
                        startPromise.fail(http.cause());
                    }
                });
    }

    private void handleRegister(final RoutingContext ctx) {
        final JsonObject body = ctx.body().asJsonObject();
        final String username = body == null ? null : body.getString("username");
        final String password = body == null ? null : body.getString("password");
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            ctx.response().setStatusCode(400)
                    .end(new JsonObject().put("error", "Missing or malformed username/password").encode());
            return;
        }
        try {
            final Account account = accountService.register(username, password);
            ctx.response().setStatusCode(201)
                    .putHeader("Content-Type", "application/json")
                    .end(new JsonObject()
                            .put("accountId", account.getId().value())
                            .put("username", account.getUsername())
                            .put("role", account.getRole().name())
                            .put("loginLink", "/api/v1/accounts/login")
                            .encode());
        } catch (final IllegalStateException e) {
            ctx.response().setStatusCode(409)
                    .end(new JsonObject().put("error", e.getMessage()).encode());
        } catch (final IllegalArgumentException e) {
            ctx.response().setStatusCode(400)
                    .end(new JsonObject().put("error", e.getMessage()).encode());
        }
    }

    private void handleLogin(final RoutingContext ctx) {
        final JsonObject body = ctx.body().asJsonObject();
        final String username = body == null ? null : body.getString("username");
        final String password = body == null ? null : body.getString("password");
        try {
            final Account account = accountService.login(username, password);
            ctx.response().setStatusCode(200)
                    .putHeader("Content-Type", "application/json")
                    .end(new JsonObject()
                            .put("accountId", account.getId().value())
                            .put("username", account.getUsername())
                            .put("role", account.getRole().name())
                            .encode());
        } catch (final IllegalArgumentException e) {
            final String msg = e.getMessage();
            final int status = "Account not found".equals(msg) ? 404 : 401;
            ctx.response().setStatusCode(status)
                    .end(new JsonObject().put("error", msg).encode());
        }
    }

    private void handleGetAccount(final RoutingContext ctx) {
        final String id = ctx.pathParam("accountId");
        accountService.getAccount(AccountId.of(id)).ifPresentOrElse(
                account -> ctx.response().setStatusCode(200)
                        .putHeader("Content-Type", "application/json")
                        .end(new JsonObject()
                                .put("accountId", account.getId().value())
                                .put("username", account.getUsername())
                                .put("role", account.getRole().name())
                                .put("whenCreated", account.getWhenCreated().toEpochMilli())
                                .encode()),
                () -> ctx.response().setStatusCode(404)
                        .end(new JsonObject().put("error", "Account not found").encode())
        );
    }
}
