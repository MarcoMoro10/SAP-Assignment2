package it.unibo.sap.acceptance.steps;

import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.vertx.core.json.JsonObject;
import it.unibo.sap.acceptance.support.DeliveryFixtures;
import it.unibo.sap.acceptance.support.FleetTestFixture;
import it.unibo.sap.acceptance.support.TestServices;
import it.unibo.sap.acceptance.support.World;
import it.unibo.sap.delivery.domain.deliveries.DeliveryId;
import it.unibo.sap.delivery.domain.deliveries.DeliveryStatus;
import it.unibo.sap.delivery.domain.fleet.DroneStatus;
import it.unibo.sap.session.application.SessionService;
import it.unibo.sap.session.domain.Session;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Cancel a delivery request. The cancel action goes through the session-service to the
 * delivery REST API; preconditions (an existing delivery in a given status, a reserved
 * slot) are arranged in-process via the delivery repository and the Fleet fixture.
 */
public class CancelDeliverySteps {

    private static final String SLOT_DRONE = "DRN-1";

    private final TestServices services = TestServices.get();
    private final SessionService session = services.sessionService();
    private final World world = World.get();
    private final DeliveryFixtures deliveries = new DeliveryFixtures(services.deliveryRepository());
    private final FleetTestFixture fleet = new FleetTestFixture(services.droneRepository());

    @Given("I have a delivery {string} in status {string}")
    public void iHaveADeliveryInStatus(final String deliveryId, final String status) {
        final DeliveryStatus target = DeliveryStatus.valueOf(status);
        final String droneId = (target == DeliveryStatus.ASSIGNED
                || target == DeliveryStatus.SCHEDULED
                || target == DeliveryStatus.IN_PROGRESS) ? SLOT_DRONE : null;
        deliveries.seed(deliveryId, target, currentAccountId(), droneId);
        arrangeFleetFor(target, deliveryId);
    }

    @Given("a delivery {string} belongs to another user")
    public void aDeliveryBelongsToAnotherUser(final String deliveryId) {
        deliveries.seed(deliveryId, DeliveryStatus.ASSIGNED, "another-user-account", SLOT_DRONE);
    }

    @When("I cancel the delivery {string}")
    public void iCancelTheDelivery(final String deliveryId) {
        world.clearOutcome();
        try {
            final JsonObject result = session.cancelDelivery(world.sessionId(), deliveryId);
            if (result != null && result.getInteger("_statusCode", 200) >= 400) {
                world.setLastError(result.getString("error", "cancel failed"));
            } else {
                world.setLastResponse(result);
            }
        } catch (final RuntimeException e) {
            world.setLastError(e.getMessage() == null ? e.toString() : e.getMessage());
        }
    }

    @Then("I should see a confirmation that the delivery has been cancelled")
    public void confirmationCancelled() {
        assertTrue(world.lastError().isEmpty(), "expected success but got error: " + world.lastError());
        assertEquals("CANCELLED", world.lastResponse().getString("status"));
    }

    @And("the delivery {string} should be in status {string}")
    public void theDeliveryShouldBeInStatus(final String deliveryId, final String status) {
        final DeliveryStatus actual = services.deliveryRepository()
                .findById(DeliveryId.of(deliveryId)).orElseThrow().getStatus();
        assertEquals(DeliveryStatus.valueOf(status), actual);
    }

    @And("the delivery {string} should remain in status {string}")
    public void theDeliveryShouldRemainInStatus(final String deliveryId, final String status) {
        theDeliveryShouldBeInStatus(deliveryId, status);
    }

    @And("the assigned drone should become available again")
    public void assignedDroneAvailableAgain() {
        assertEquals(DroneStatus.AVAILABLE, fleet.statusOf(SLOT_DRONE));
    }

    @And("the reserved drone slot should be released")
    public void reservedSlotReleased() {
        assertNotEquals(DroneStatus.RESERVED, fleet.statusOf(SLOT_DRONE),
                "the slot reservation should have been released");
    }

    private void arrangeFleetFor(final DeliveryStatus target, final String deliveryId) {
        switch (target) {
            case ASSIGNED, IN_PROGRESS -> {
                fleet.knownDrone(SLOT_DRONE, new it.unibo.sap.delivery.domain.fleet.Coordinates(44.49, 11.34));
                fleet.inDelivery(SLOT_DRONE, deliveryId); // occupied; release will free it
            }
            case SCHEDULED -> {
                fleet.knownDrone(SLOT_DRONE, new it.unibo.sap.delivery.domain.fleet.Coordinates(44.49, 11.34));
                final LocalDateTime actualSlot = services.deliveryRepository()
                        .findById(DeliveryId.of(deliveryId)).orElseThrow()
                        .getRequest().requestedDateTime().scheduledAt();
                fleet.reserveSlot(SLOT_DRONE, deliveryId, actualSlot);
            }
            default -> {
                // no fleet arrangement needed
            }
        }
    }

    private String currentAccountId() {
        final Session s = session.getSession(world.sessionId())
                .orElseThrow(() -> new IllegalStateException("No active session for the current scenario"));
        return s.getAccountId();
    }
}
