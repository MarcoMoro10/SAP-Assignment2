package it.unibo.sap.acceptance.steps;

import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.vertx.core.json.JsonObject;
import it.unibo.sap.acceptance.support.TestServices;
import it.unibo.sap.acceptance.support.World;
import it.unibo.sap.session.application.SessionService;
import it.unibo.sap.session.domain.Session;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Registration & access. Registration goes through the account-service REST API; login
 * goes through the session-service (the orchestrator that authenticates against accounts
 * and opens a session). Errors are captured in {@link World} for the shared error step.
 */
public class RegistrationSteps {

    private final TestServices services = TestServices.get();
    private final SessionService session = services.sessionService();
    private final World world = World.get();

    private String lastRegisteredUser;
    private int lastRegisterStatus;
    private boolean authenticated;

    @Given("I am on the registration page")
    public void onRegistrationPage() {
        // UI phrasing only.
    }

    @Given("I am on the login page")
    public void onLoginPage() {
        // UI phrasing only.
    }

    @Given("a user already exists with username {string}")
    public void userAlreadyExists(final String username) {
        register(username, "Secret#123"); // pre-create so the next register collides
    }

    @Given("I am a registered user {string} with password {string}")
    public void registeredUser(final String username, final String password) {
        register(username, password);
    }

    @Given("I am a registered admin {string} with password {string}")
    public void registeredAdmin(final String username, final String password) {
        // The admin (admin-1 / Admin#123) is seeded by TestServices; nothing to create.
    }

    @When("I register with username {string} and password {string}")
    public void register(final String username, final String password) {
        final CompletableFuture<JsonObject> done = new CompletableFuture<>();
        services.webClient()
                .post(services.accountPort(), services.host(), "/api/v1/accounts")
                .sendJsonObject(new JsonObject().put("username", username).put("password", password),
                        ar -> {
                            if (ar.succeeded()) {
                                final JsonObject body = safeJson(ar.result().bodyAsString());
                                done.complete(body.put("_statusCode", ar.result().statusCode()));
                            } else {
                                done.completeExceptionally(ar.cause());
                            }
                        });
        try {
            final JsonObject res = done.get(10, TimeUnit.SECONDS);
            lastRegisterStatus = res.getInteger("_statusCode", 0);
            world.clearOutcome();
            if (lastRegisterStatus == 201) {
                lastRegisteredUser = username;
                world.setLastResponse(res);
            } else {
                world.setLastError(res.getString("error", "registration failed"));
            }
        } catch (final Exception e) {
            throw new IllegalStateException("Registration call failed", e);
        }
    }

    @When("I log in with username {string} and password {string}")
    public void login(final String username, final String password) {
        world.clearOutcome();
        authenticated = false;
        try {
            final Session s = session.login(username, password);
            world.setSessionId(s.getId());
            world.setRole(s.getRole());
            authenticated = true;
        } catch (final RuntimeException e) {
            world.setLastError(e.getMessage());
        }
    }

    @Then("I should see a confirmation that my account has been created")
    public void accountCreatedConfirmation() {
        assertEquals(201, lastRegisterStatus);
        assertNotNull(world.lastResponse());
        assertNotNull(world.lastResponse().getString("accountId"));
    }

    @And("I should be able to log in")
    public void shouldBeAbleToLogIn() {
        final Session s = session.login(lastRegisteredUser, "Secret#123");
        assertNotNull(s.getId());
    }

    @And("the account should not be created")
    public void accountShouldNotBeCreated() {
        assertEquals(409, lastRegisterStatus, "duplicate registration should be rejected");
    }

    @Then("I should be authenticated")
    public void authenticated() {
        assertTrue(authenticated, "expected the login to succeed");
        assertNotNull(world.sessionId());
    }

    @And("I should be redirected to my home page")
    public void redirectedToHome() {
        assertEquals("SENDER", world.role());
    }

    @And("I should not be authenticated")
    public void notAuthenticated() {
        assertFalse(authenticated, "expected the login to fail");
        assertNull(world.sessionId());
    }

    @Then("I should be authenticated as an admin")
    public void authenticatedAsAdmin() {
        assertTrue(authenticated, "expected the admin login to succeed");
        assertEquals("ADMIN", world.role());
    }

    @And("I should be redirected to the fleet monitoring home page")
    public void redirectedToFleetMonitoring() {
        assertEquals("ADMIN", world.role());
    }

    private static JsonObject safeJson(final String body) {
        try {
            return body == null || body.isBlank() ? new JsonObject() : new JsonObject(body);
        } catch (final RuntimeException e) {
            return new JsonObject();
        }
    }
}
