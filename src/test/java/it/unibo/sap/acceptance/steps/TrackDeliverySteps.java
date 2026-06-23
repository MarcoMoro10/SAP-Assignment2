package it.unibo.sap.acceptance.steps;

import io.cucumber.java.en.And;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.vertx.core.json.JsonObject;
import it.unibo.sap.acceptance.support.FleetTestFixture;
import it.unibo.sap.acceptance.support.TestServices;
import it.unibo.sap.acceptance.support.World;
import it.unibo.sap.delivery.application.DroneEventHandler;
import it.unibo.sap.delivery.domain.deliveries.DeliveryId;
import it.unibo.sap.session.application.SessionService;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Track a delivery. Opening the tracking page goes through the session-service to the
 * delivery REST API; the tracking read model (status, ETR) is observed via the delivery
 * GET endpoint, while the drone position is observed in-process through the Fleet fixture
 * (the only place the drone's live position is exposed). The "I have a delivery ... in
 * status ..." and "a delivery ... belongs to another user" Givens are shared with the
 * cancel feature and defined once in {@code CancelDeliverySteps}.
 */
public class TrackDeliverySteps {

    private static final String SLOT_DRONE = "DRN-1";

    private final TestServices services = TestServices.get();
    private final SessionService session = services.sessionService();
    private final World world = World.get();
    private final FleetTestFixture fleet = new FleetTestFixture(services.droneRepository());
    private final DroneEventHandler droneEvents = services.droneEventHandler();

    private String trackedDeliveryId;
    private JsonObject trackingView;
    private long firstEtrSeconds = -1;

    @And("I am on the tracking page for delivery {string}")
    public void onTrackingPage(final String deliveryId) {
        openTracking(deliveryId);
    }

    @When("I open the tracking page for delivery {string}")
    public void openTracking(final String deliveryId) {
        this.trackedDeliveryId = deliveryId;
        world.clearOutcome();
        try {
            session.trackDelivery(world.sessionId(), deliveryId);
            final Optional<JsonObject> view = session.getDelivery(world.sessionId(), deliveryId);
            if (view.isEmpty()) {
                world.setLastError("Delivery not found");
            } else {
                this.trackingView = view.get();
                world.setLastResponse(this.trackingView);
                if (firstEtrSeconds < 0) {
                    firstEtrSeconds = this.trackingView.getLong("estimatedTimeRemainingSeconds", -1L);
                }
            }
        } catch (final RuntimeException e) {
            world.setLastError(e.getMessage() == null ? e.toString() : e.getMessage());
        }
    }

    @When("the drone moves to a new position closer to the destination")
    public void droneMovesCloser() {
        droneEvents.onDronePositionUpdated(trackedDeliveryId, 44.50, 11.35);
        final Optional<JsonObject> view = session.getDelivery(world.sessionId(), trackedDeliveryId);
        view.ifPresent(v -> this.trackingView = v);
    }

    @Then("I should see the current status of the delivery")
    public void seeCurrentStatus() {
        assertNotNull(trackingView);
        assertNotNull(trackingView.getString("status"));
    }

    @And("I should see the current position of the drone")
    public void seeCurrentPosition() {
        // The drone's live position is observed in-process from the Fleet context.
        assertNotNull(fleet.positionOf(SLOT_DRONE));
    }

    @And("I should see the estimated time remaining")
    public void seeEtr() {
        assertNotNull(trackingView);
        assertTrue(trackingView.containsKey("estimatedTimeRemainingSeconds"));
    }

    @Then("I should see the status {string}")
    public void seeStatus(final String expected) {
        assertNotNull(trackingView);
        assertEquals(expected, trackingView.getString("status"));
    }

    @And("the estimated time remaining should be {string}")
    public void etrShouldBe(final String expected) {
        assertNotNull(trackingView);
        assertEquals(Long.parseLong(expected), trackingView.getLong("estimatedTimeRemainingSeconds"));
    }

    @Then("the tracking view of delivery {string} should eventually show a decreased estimated time remaining")
    public void etrEventuallyDecreased(final String deliveryId) {
        final long before = firstEtrSeconds;
        final long after = pollEtr(deliveryId);
        assertTrue(after < before,
                "expected ETR to decrease from " + before + " but it was " + after);
    }

    private long pollEtr(final String deliveryId) {
        long etr = currentEtr(deliveryId);
        for (int i = 0; i < 20 && etr >= firstEtrSeconds; i++) {
            sleep(50);
            etr = currentEtr(deliveryId);
        }
        return etr;
    }

    private long currentEtr(final String deliveryId) {
        return services.deliveryRepository().findById(DeliveryId.of(deliveryId))
                .map(d -> d.getEstimatedTimeRemaining().toSeconds())
                .orElse(-1L);
    }

    private static void sleep(final long millis) {
        try {
            Thread.sleep(millis);
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
