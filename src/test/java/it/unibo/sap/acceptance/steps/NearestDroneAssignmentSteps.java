package it.unibo.sap.acceptance.steps;

import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import it.unibo.sap.acceptance.support.FleetTestFixture;
import it.unibo.sap.acceptance.support.TestServices;
import it.unibo.sap.acceptance.support.World;
import it.unibo.sap.delivery.domain.fleet.Coordinates;
import it.unibo.sap.delivery.infrastructure.GeocodingService;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Nearest-drone assignment. Drones are positioned at a chosen distance from the pickup
 * point (geocoded with the same deterministic geocoder the service uses) so that the
 * domain's nearest-eligible selection is exercised. Only DRN-1/DRN-2 are kept active;
 * the rest of the seeded fleet is parked so it cannot interfere. The actual creation
 * happens through the shared create-delivery When step.
 */
public class NearestDroneAssignmentSteps {

    private static final double DEG_PER_KM = 0.009; // ~1 km in degrees near Bologna

    private final TestServices services = TestServices.get();
    private final World world = World.get();
    private final FleetTestFixture fleet = new FleetTestFixture(services.droneRepository());
    private final GeocodingService geocoder = new GeocodingService();

    private Coordinates pickup;

    @Given("drone {string} is available {string} km from the pickup point")
    public void droneAvailableKmFromPickup(final String droneId, final String km) {
        keepOnlyTheTwo();
        fleet.addAvailable(droneId, atDistance(Double.parseDouble(km)), 5.0);
    }

    @And("both drones can carry the package")
    public void bothDronesCanCarry() {
        // Both seeded with 5 kg capacity above; the 2 kg package fits both.
    }

    @Given("drone {string} is available {string} km from the pickup point with max capacity {string} kg")
    public void droneAvailableKmWithCapacity(final String droneId, final String km, final String capacityKg) {
        keepOnlyTheTwo();
        fleet.addAvailable(droneId, atDistance(Double.parseDouble(km)), Double.parseDouble(capacityKg));
    }

    @Then("drone {string} should be assigned to the delivery")
    public void droneShouldBeAssigned(final String expectedDroneId) {
        assertNotNull(world.lastResponse(), "expected a created delivery but got: " + world.lastError());
        assertEquals(expectedDroneId, world.lastResponse().getString("assignedDroneId"));
    }

    private void keepOnlyTheTwo() {
        if (pickup == null) {
            final var dc = geocoder.geocode("via Emilia", 9);
            this.pickup = new Coordinates(dc.latitude(), dc.longitude());
            fleet.keepOnly(List.of("DRN-1", "DRN-2"));
        }
    }

    private Coordinates atDistance(final double km) {
        double lon = pickup.longitude() + km * DEG_PER_KM;
        if (lon > 11.40) {
            lon = pickup.longitude() - km * DEG_PER_KM;
        }
        return new Coordinates(pickup.latitude(), lon);
    }
}
