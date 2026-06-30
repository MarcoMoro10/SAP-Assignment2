package system.steps;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import system.World;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Journey 2 — (register + login + create as preconditions) -> start tracking over the gateway WebSocket
 * relay -> read status -> stop tracking. Verifies the client <-> gateway <-> delivery WebSocket relay
 * and the tracking read-model.
 */
public class UserJourneyTrackingDeliverySteps {

    private final World world = World.get();

    @Given("I have registered as {string} with password {string}")
    public void iHaveRegisteredAs(final String username, final String password) {
        assertThat(world.register(username, password)).as("registration status").isEqualTo(201);
    }

    @Given("I have logged in as {string}")
    public void iHaveLoggedInAs(final String username) {
        assertThat(world.login()).as("login status").isEqualTo(200);
    }

    @Given("I have created a delivery of weight {string} kg from {string} to {string} to ship immediately")
    public void iHaveCreatedADelivery(final String weight, final String pickup, final String destination) {
        assertThat(world.createImmediateDelivery(Double.parseDouble(weight), pickup, destination))
                .as("create-delivery status").isEqualTo(201);
    }

    @Given("I have created a delivery of weight {string} kg from {string} to {string} scheduled in {string} minutes")
    public void iHaveCreatedAScheduledDelivery(final String weight, final String pickup,
                                               final String destination, final String minutes) {
        final String scheduledAt = LocalDateTime.now().plusMinutes(Long.parseLong(minutes)).toString();
        assertThat(world.createScheduledDelivery(Double.parseDouble(weight), pickup, destination, scheduledAt))
                .as("create scheduled delivery status").isEqualTo(201);
    }

    @When("I start tracking that delivery")
    public void iStartTrackingThatDelivery() {
        assertThat(world.startTracking()).as("track-delivery status").isEqualTo(200);
    }

    @Then("I should receive a confirmation that tracking has started for the scheduled delivery")
    public void iShouldReceiveTrackingStartedForScheduledDelivery() {
        assertThat(world.trackingSessionId()).as("trackingSessionId").isNotBlank();
        final String webSocketUrl = world.lastBody().getString("webSocketUrl");
        assertThat(webSocketUrl).as("webSocketUrl relayed by the gateway").isNotBlank();
        assertThat(webSocketUrl).as("the gateway must never emit a null tracking URL").doesNotEndWith("/null");
    }

    @Then("I should receive a confirmation that tracking has started")
    public void iShouldReceiveTrackingStarted() {
        assertThat(world.trackingSessionId()).as("trackingSessionId").isNotBlank();
        final String frame = world.awaitFrame(30);
        assertThat(frame).as("a tracking frame relayed through the gateway").isNotBlank();
    }

    @When("I request the current status of that delivery")
    public void iRequestTheCurrentStatus() {
        assertThat(world.getDeliveryDetail()).as("get-delivery status").isEqualTo(200);
    }

    @Then("I should get its current status and estimated time remaining")
    public void iShouldGetStatusAndEtr() {
        assertThat(world.lastBody().getString("status")).as("status").isNotBlank();
        assertThat(world.lastBody().containsKey("estimatedTimeRemainingSeconds"))
                .as("estimatedTimeRemainingSeconds present").isTrue();
    }

    @When("I stop tracking that delivery")
    public void iStopTrackingThatDelivery() {
        world.stopTracking();
    }

    @Then("I should receive a confirmation that tracking has stopped")
    public void iShouldReceiveTrackingStopped() {
        assertThat(world.isTrackingClosed()).as("the tracking WebSocket is closed").isTrue();
    }
}
