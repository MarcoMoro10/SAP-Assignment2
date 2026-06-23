package it.unibo.sap.account.component.steps;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.vertx.core.json.JsonObject;
import it.unibo.sap.account.component.AccountServiceTestContext;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Black-box steps that drive the account-service over HTTP. Registration hits
 * {@code POST /api/v1/accounts} and login hits {@code POST /api/v1/accounts/login};
 * the last HTTP status and body are kept for the assertion steps.
 */
public class AccountSteps {

    private final AccountServiceTestContext ctx = AccountServiceTestContext.get();

    private int lastStatus;
    private JsonObject lastBody = new JsonObject();

    @Given("an account already exists with username {string}")
    public void anAccountAlreadyExists(final String username) {
        register(username, "Secret#123");
        assertEquals(201, lastStatus, "precondition: account should have been created");
    }

    @When("I register with username {string} and password {string}")
    public void register(final String username, final String password) {
        post("/api/v1/accounts", new JsonObject().put("username", username).put("password", password));
    }

    @When("I log in with username {string} and password {string}")
    public void login(final String username, final String password) {
        post("/api/v1/accounts/login", new JsonObject().put("username", username).put("password", password));
    }

    @Then("the account is created with role {string}")
    public void theAccountIsCreatedWithRole(final String role) {
        assertEquals(201, lastStatus);
        assertNotNull(lastBody.getString("accountId"), "expected an account id in the response");
        assertEquals(role, lastBody.getString("role"));
    }

    @Then("I am authenticated with role {string}")
    public void iAmAuthenticatedWithRole(final String role) {
        assertEquals(200, lastStatus);
        assertNotNull(lastBody.getString("accountId"), "expected an account id in the response");
        assertEquals(role, lastBody.getString("role"));
    }

    @Then("the response status is {int} with error {string}")
    public void theResponseStatusIsWithError(final int status, final String error) {
        assertEquals(status, lastStatus);
        assertEquals(error, lastBody.getString("error"));
    }

    private void post(final String path, final JsonObject payload) {
        final CompletableFuture<JsonObject> done = new CompletableFuture<>();
        ctx.webClient()
                .post(ctx.port(), ctx.host(), path)
                .sendJsonObject(payload, ar -> {
                    if (ar.succeeded()) {
                        final JsonObject body = safeJson(ar.result().bodyAsString());
                        done.complete(body.put("_statusCode", ar.result().statusCode()));
                    } else {
                        done.completeExceptionally(ar.cause());
                    }
                });
        try {
            final JsonObject res = done.get(10, TimeUnit.SECONDS);
            lastStatus = res.getInteger("_statusCode", 0);
            lastBody = res;
        } catch (final Exception e) {
            throw new IllegalStateException("HTTP call to " + path + " failed", e);
        }
    }

    private static JsonObject safeJson(final String body) {
        try {
            return body == null || body.isBlank() ? new JsonObject() : new JsonObject(body);
        } catch (final RuntimeException e) {
            return new JsonObject();
        }
    }
}
