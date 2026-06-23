package it.unibo.sap.acceptance.steps;

import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import it.unibo.sap.acceptance.support.DeliveryFixtures;
import it.unibo.sap.acceptance.support.FleetTestFixture;
import it.unibo.sap.acceptance.support.TestServices;
import it.unibo.sap.acceptance.support.World;
import it.unibo.sap.delivery.application.fleet.FleetFeasibilityRequest;
import it.unibo.sap.delivery.application.fleet.FleetReservationResult;
import it.unibo.sap.delivery.domain.deliveries.DeliveryStatus;
import it.unibo.sap.delivery.domain.fleet.Coordinates;
import it.unibo.sap.delivery.infrastructure.fleet.FleetModule;
import it.unibo.sap.session.application.SessionService;

import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Automatic scheduling, observed by the Admin. The read-only scheduling view is fetched
 * through the session-service admin endpoint. The reservation mechanics are exercised
 * in-process against the real {@link FleetModule}, which is the component that reserves a
 * drone slot for a scheduled delivery.
 */
public class SchedulingManagementSteps {

    private final TestServices services = TestServices.get();
    private final SessionService session = services.sessionService();
    private final World world = World.get();
    private final FleetModule fleetModule = services.fleetModule();
    private final FleetTestFixture fleet = new FleetTestFixture(services.droneRepository());
    private final DeliveryFixtures deliveries = new DeliveryFixtures(services.deliveryRepository());

    private LocalDateTime slot;
    private JsonArray schedulingView;
    private FleetReservationResult lastReservation;

    @And("drone {string} has {string} scheduled deliveries today")
    public void droneHasScheduledDeliveries(final String droneId, final String count) {
        final int n = Integer.parseInt(count);
        for (int i = 0; i < n; i++) {
            deliveries.seed("DLV-SCHED-" + i, DeliveryStatus.SCHEDULED, "admin-observed-sender", droneId);
        }
    }

    @Given("a scheduled delivery {string} is planned for {string} today")
    public void scheduledDeliveryPlanned(final String deliveryId, final String time) {
        this.slot = LocalDateTime.of(LocalDateTime.now().toLocalDate(), LocalTime.parse(time));
    }

    @Given("all drones are already reserved for {string} today")
    public void allDronesReserved(final String time) {
        this.slot = LocalDateTime.of(LocalDateTime.now().toLocalDate(), LocalTime.parse(time));
        for (final var drone : fleet.all()) {
            fleet.reserveSlot(drone.getId().value(), "preexisting", slot);
        }
    }

    @When("I open the scheduling view for drone {string}")
    public void openSchedulingView(final String droneId) {
        final JsonObject result = session.viewScheduling(world.sessionId(), droneId);
        this.schedulingView = result.getJsonArray("scheduling");
        world.setLastResponse(result);
    }

    @When("the system schedules it onto drone {string}")
    public void systemSchedulesOnto(final String droneId) {
        fleet.knownDrone(droneId, new Coordinates(44.49, 11.34));
        for (final var drone : fleet.all()) {
            if (!drone.getId().value().equals(droneId)) {
                fleet.reserveSlot(drone.getId().value(), "preexisting", slot);
            }
        }
        this.lastReservation = fleetModule.reserveDroneForSlot(feasibility("DLV-300"), slot);
    }

    @When("a scheduled delivery is requested for that slot")
    public void scheduledDeliveryRequested() {
        this.lastReservation = fleetModule.reserveDroneForSlot(feasibility("DLV-NEW"), slot);
    }

    @Then("I should see {string} scheduled deliveries")
    public void seeScheduledDeliveries(final String count) {
        assertEquals(Integer.parseInt(count), schedulingView.size());
    }

    @Then("drone {string} should be reserved for that slot")
    public void droneReservedForSlot(final String droneId) {
        assertTrue(lastReservation.reserved(), "expected the reservation to succeed");
        assertEquals(droneId, lastReservation.droneIdOpt().orElse(null));
    }

    @And("drone {string} should not be assignable to another delivery in that slot")
    public void notAssignableInSlot(final String droneId) {
        assertFalse(fleet.findById(droneId).orElseThrow().isSlotFree(slot),
                "the slot should be taken");
    }

    @Then("the request should be rejected with the error {string}")
    public void requestRejectedWithError(final String message) {
        assertFalse(lastReservation.reserved(), "expected the reservation to be rejected");
        assertEquals(message, lastReservation.rejectionReasonOpt().orElse(""));
    }

    private FleetFeasibilityRequest feasibility(final String deliveryId) {
        return new FleetFeasibilityRequest(deliveryId, 2.0, 44.49, 11.34, 44.50, 11.35, 0);
    }
}
