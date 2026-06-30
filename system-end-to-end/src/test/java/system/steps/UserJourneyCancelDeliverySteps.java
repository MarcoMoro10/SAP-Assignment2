package system.steps;

import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import system.World;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Journey 3 — (register + login + create as preconditions, reused from {@link UserJourneyTrackingDeliverySteps})
 * -> cancel the delivery over the api-gateway. Pure black-box: the only signal under test is the HTTP
 * response observable from the outside (status code and body), never the internal delivery state.
 */
public class UserJourneyCancelDeliverySteps {

    private final World world = World.get();

    @When("I cancel that delivery")
    public void iCancelThatDelivery() {
        assertThat(world.cancelDelivery()).as("cancel-delivery status").isEqualTo(200);
    }

    @Then("I should get a confirmation that the delivery has been cancelled")
    public void iShouldGetCancellationConfirmation() {
        assertThat(world.lastStatus()).as("cancel status").isEqualTo(200);
        assertThat(world.lastBody().getString("deliveryId")).as("deliveryId").isEqualTo(world.deliveryId());
        assertThat(world.lastBody().getString("status")).as("delivery status").isEqualTo("CANCELLED");
    }
}
