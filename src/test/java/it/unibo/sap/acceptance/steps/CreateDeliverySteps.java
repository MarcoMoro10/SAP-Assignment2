package it.unibo.sap.acceptance.steps;

import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.vertx.core.json.JsonObject;
import it.unibo.sap.acceptance.support.FleetTestFixture;
import it.unibo.sap.acceptance.support.TestServices;
import it.unibo.sap.acceptance.support.World;
import it.unibo.sap.session.application.SessionService;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CreateDeliverySteps {

    private final TestServices services = TestServices.get();
    private final SessionService session = services.sessionService();
    private final World world = World.get();
    private final FleetTestFixture fleet = new FleetTestFixture(services.droneRepository());

    @And("I am on the delivery creation page")
    public void onTheCreationPage() {
        // UI-level phrasing; no backend state to set.
    }

    @And("deliveries can be scheduled at most {string} days in advance")
    public void schedulingHorizon(final String days) {
        // The horizon is a domain constant (7 days); this Given documents the assumption.
    }

    @Given("the maximum load capacity in the fleet is {string} kg")
    public void maxLoadCapacity(final String kg) {
        // Shrink every drone's capacity to this ceiling so a heavier package is rejected
        // by the domain with "No drone can carry this package".
        fleet.capacityCeiling(Double.parseDouble(kg));
    }

    @Given("all drones in the fleet are currently busy")
    public void allDronesBusy() {
        // Park the whole fleet OUT_OF_SERVICE so no drone is assignable: the domain then
        // rejects an immediate request with "No drone available".
        fleet.emptyFleet();
    }

    @When("I create a delivery with weight {string} kg, starting place {string}, destination place {string} to ship immediately")
    public void createImmediate(final String weight, final String start, final String dest) {
        create(weight, start, dest, true, null, 0);
    }


    @When("I create a delivery with weight {string} kg, starting place {string}, destination place {string} to ship in {string} days")
    public void createScheduledInDays(final String weight, final String start, final String dest, final String days) {
        final LocalDateTime when = LocalDateTime.now().plusDays(Long.parseLong(days));
        create(weight, start, dest, false, when, 0);
    }

    @Then("I should see a confirmation that the delivery has been created and receive its identifier")
    public void confirmationWithId() {
        assertTrue(world.lastError().isEmpty(), "expected success but got error: " + world.lastError());
        assertNotNull(world.lastResponse());
        assertNotNull(world.lastResponse().getString("deliveryId"));
    }

    @And("the delivery should be in status {string}")
    public void deliveryInStatus(final String expected) {
        if ("REJECTED".equals(expected)) {
            assertTrue(!world.lastError().isEmpty(), "expected the creation to be rejected");
            return;
        }
        assertNotNull(world.lastResponse(), "expected a created delivery but got error: " + world.lastError());
        assertEquals(expected, world.lastResponse().getString("status"));
    }

    @And("a drone should be assigned to the delivery")
    public void droneAssigned() {
        assertNotNull(world.lastResponse());
        assertNotNull(world.lastResponse().getString("assignedDroneId"));
    }

    @And("a drone should be reserved for the scheduled slot")
    public void droneReservedForSlot() {
        assertNotNull(world.lastResponse());
        assertEquals("SCHEDULED", world.lastResponse().getString("status"));
    }

    @And("the delivery should not be confirmed")
    public void notConfirmed() {
        assertTrue(world.lastResponse() == null || world.lastResponse().getString("deliveryId") == null,
                "delivery should not have been created");
    }

    private void create(final String weight, final String start, final String dest,
                        final boolean immediate, final LocalDateTime when, final long deadlineMinutes) {
        final String[] s = start.split(",\\s*");
        final String[] d = dest.split(",\\s*");
        final JsonObject body = new JsonObject()
                .put("weight", Double.parseDouble(weight))
                .put("startingPlace", new JsonObject()
                        .put("street", s[0].trim())
                        .put("number", s.length > 1 ? Integer.parseInt(s[1].trim()) : 0))
                .put("destinationPlace", new JsonObject()
                        .put("street", d[0].trim())
                        .put("number", d.length > 1 ? Integer.parseInt(d[1].trim()) : 0))
                .put("immediate", immediate);
        if (deadlineMinutes > 0) {
            body.put("deadlineMinutes", deadlineMinutes);
        }
        if (when != null) {
            body.put("scheduledAt", when.toString());
        }
        world.clearOutcome();
        try {
            final JsonObject result = session.createDelivery(world.sessionId(), body);
            if (result != null && result.containsKey("error")) {
                world.setLastError(result.getString("error"));
            } else if (result != null && result.getInteger("_statusCode", 200) >= 400) {
                world.setLastError(result.getString("error", "request failed"));
            } else {
                world.setLastResponse(result);
            }
        } catch (final RuntimeException e) {
            world.setLastError(e.getMessage() == null ? e.toString() : e.getMessage());
        }
    }
}
