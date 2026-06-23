package it.unibo.sap.acceptance.steps;

import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import it.unibo.sap.acceptance.support.FleetTestFixture;
import it.unibo.sap.acceptance.support.TestServices;
import it.unibo.sap.acceptance.support.World;
import it.unibo.sap.delivery.domain.fleet.Coordinates;
import it.unibo.sap.delivery.domain.fleet.DroneStatus;
import it.unibo.sap.session.application.SessionService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Fleet monitoring (Admin). The read view is fetched through the session-service admin
 * endpoint (viewFleet). Drone state is arranged and the live position is observed
 * in-process through the Fleet fixture, since the fleet has no network ingestion surface.
 */
public class FleetMonitoringSteps {

    private final TestServices services = TestServices.get();
    private final SessionService session = services.sessionService();
    private final World world = World.get();
    private final FleetTestFixture fleet = new FleetTestFixture(services.droneRepository());

    private JsonArray fleetView;

    @Given("the fleet has {string} drones")
    public void theFleetHasDrones(final String count) {
        // The seeded fleet already has exactly 3 active drones (DRN-1/2/3).
        assertEquals(Integer.parseInt(count), (int) fleet.activeCount());
    }

    @Given("a drone {string} is in status {string} and is carrying a package")
    public void droneInStatusCarrying(final String droneId, final String status) {
        fleet.knownDrone(droneId, new Coordinates(44.49, 11.34));
        fleet.inDelivery(droneId, "DLV-MON");
        assertEquals(DroneStatus.valueOf("IN_DELIVERY"), fleet.statusOf(droneId));
    }

    @Given("I am on the fleet monitoring page")
    public void onFleetMonitoringPage() {
        openMonitoring();
    }

    @And("a drone {string} is at position {string}")
    public void droneAtPosition(final String droneId, final String coords) {
        fleet.knownDrone(droneId, parse(coords));
        fleet.updatePosition(droneId, parse(coords));
    }

    @When("I open the fleet monitoring page")
    public void openMonitoring() {
        final JsonObject result = session.viewFleet(world.sessionId());
        this.fleetView = result.getJsonArray("fleet");
        world.setLastResponse(result);
    }

    @When("drone {string} updates its position to {string}")
    public void droneUpdatesPosition(final String droneId, final String coords) {
        fleet.updatePosition(droneId, parse(coords));
    }

    @Then("I should see {string} drones on the map")
    public void seeDronesOnMap(final String count) {
        assertNotNull(fleetView);
        assertEquals(Integer.parseInt(count), fleetView.size());
    }

    @And("each drone should show its current position")
    public void eachDroneShowsPosition() {
        for (int i = 0; i < fleetView.size(); i++) {
            final JsonObject d = fleetView.getJsonObject(i);
            assertNotNull(d.getJsonObject("position"), "drone " + d.getString("droneId") + " has no position");
        }
    }

    @Then("I should see drone {string} with status {string}")
    public void seeDroneWithStatus(final String droneId, final String status) {
        final JsonObject d = findDrone(droneId);
        assertNotNull(d, "drone " + droneId + " not in the fleet view");
        assertEquals(status, d.getString("status"));
    }

    @And("drone {string} should be shown as carrying a package")
    public void shownAsCarrying(final String droneId) {
        final JsonObject d = findDrone(droneId);
        assertNotNull(d);
        assertTrue(d.getBoolean("carryingPackage"), "drone " + droneId + " is not carrying a package");
    }

    @Then("the map should eventually show drone {string} at position {string}")
    public void mapEventuallyShowsPosition(final String droneId, final String coords) {
        final Coordinates expected = parse(coords);
        Coordinates actual = fleet.positionOf(droneId);
        for (int i = 0; i < 20 && !close(actual, expected); i++) {
            sleep(50);
            actual = fleet.positionOf(droneId);
        }
        assertTrue(close(actual, expected),
                "expected drone " + droneId + " near " + coords + " but was " + actual.latitude() + ", " + actual.longitude());
    }

    private JsonObject findDrone(final String droneId) {
        if (fleetView == null) {
            openMonitoring();
        }
        for (int i = 0; i < fleetView.size(); i++) {
            final JsonObject d = fleetView.getJsonObject(i);
            if (droneId.equals(d.getString("droneId"))) {
                return d;
            }
        }
        return null;
    }

    private static Coordinates parse(final String coords) {
        final String[] parts = coords.split(",\\s*");
        return new Coordinates(Double.parseDouble(parts[0].trim()), Double.parseDouble(parts[1].trim()));
    }

    private static boolean close(final Coordinates a, final Coordinates b) {
        return Math.abs(a.latitude() - b.latitude()) < 1e-6
                && Math.abs(a.longitude() - b.longitude()) < 1e-6;
    }

    private static void sleep(final long millis) {
        try {
            Thread.sleep(millis);
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
