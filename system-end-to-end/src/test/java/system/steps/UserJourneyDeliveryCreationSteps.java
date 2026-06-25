package system.steps;

import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import system.World;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Journey 1 — registration -> login -> immediate delivery creation -> detail. Every step is a real
 * REST call to the api-gateway, exercising the full gateway -> account / delivery chain.
 */
public class UserJourneyDeliveryCreationSteps {

    private final World world = World.get();

    @When("there is no account with username {string}")
    public void thereIsNoAccountWithUsername(final String username) {
    }

    @When("I register as a sender with username {string} and password {string}")
    public void iRegisterAsASender(final String username, final String password) {
        world.register(username, password);
    }

    @Then("I should get a confirmation that my account has been created")
    public void iShouldGetAConfirmationAccountCreated() {
        assertThat(world.lastStatus()).as("registration status").isEqualTo(201);
        assertThat(world.lastBody().getString("accountId")).as("accountId").isNotBlank();
    }

    @When("I login with username {string} and password {string}")
    public void iLoginWith(final String username, final String password) {
        assertThat(world.login()).as("login status").isEqualTo(200);
    }

    @Then("I should get a session confirming I am logged in")
    public void iShouldGetASession() {
        assertThat(world.lastBody().getString("sessionId")).as("sessionId").isNotBlank();
        assertThat(world.lastBody().getString("role")).as("role").isEqualTo("SENDER");
    }

    @When("I create a delivery of weight {string} kg from {string} to {string} to ship immediately")
    public void iCreateADelivery(final String weight, final String pickup, final String destination) {
        world.createImmediateDelivery(Double.parseDouble(weight), pickup, destination);
    }

    @Then("I should get a confirmation that the delivery has been created and its delivery id")
    public void iShouldGetADeliveryConfirmation() {
        assertThat(world.lastStatus()).as("create-delivery status").isEqualTo(201);
        assertThat(world.deliveryId()).as("deliveryId").isNotBlank();
        assertThat(world.lastBody().getString("status")).as("delivery status").isNotBlank();
    }

    @When("I request the detail of that delivery")
    public void iRequestTheDetail() {
        assertThat(world.getDeliveryDetail()).as("get-delivery status").isEqualTo(200);
    }

    @Then("I should get its detail with weight {string} kg, pickup {string} and destination {string}")
    public void iShouldGetItsDetail(final String weight, final String pickup, final String destination) {
        assertThat(world.lastBody().getString("deliveryId")).as("deliveryId").isEqualTo(world.deliveryId());
        assertThat(world.lastBody().getString("status")).as("status").isNotBlank();
        assertThat(world.createdWeight()).isEqualTo(Double.parseDouble(weight));
        assertThat(world.createdPickup()).isEqualTo(pickup);
        assertThat(world.createdDestination()).isEqualTo(destination);
    }
}
