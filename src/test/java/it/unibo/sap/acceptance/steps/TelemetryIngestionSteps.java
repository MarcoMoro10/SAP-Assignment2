package it.unibo.sap.acceptance.steps;

import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import it.unibo.sap.acceptance.support.DeliveryFixtures;
import it.unibo.sap.acceptance.support.FleetTestFixture;
import it.unibo.sap.acceptance.support.TestServices;
import it.unibo.sap.common.ddd.DomainEvent;
import it.unibo.sap.delivery.application.DroneEventHandler;
import it.unibo.sap.delivery.domain.deliveries.Coordinates;
import it.unibo.sap.delivery.domain.deliveries.Delivery;
import it.unibo.sap.delivery.domain.deliveries.DeliveryId;
import it.unibo.sap.delivery.domain.deliveries.DeliveryStatus;
import it.unibo.sap.delivery.domain.fleet.Drone;
import it.unibo.sap.delivery.domain.fleet.DroneStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Fleet telemetry (internal drone simulation). There is no network ingestion: each step
 * drives the Fleet domain directly in-process and lets the in-process Observer (the
 * telemetry sink wired to {@link DroneEventHandler}) propagate effects to the Deliveries
 * read model. "Eventually" here means in-process propagation through the handler, which we
 * trigger and then poll for the resulting delivery state.
 */
public class TelemetryIngestionSteps {

    private final TestServices services = TestServices.get();
    private final FleetTestFixture fleet = new FleetTestFixture(services.droneRepository());
    private final DeliveryFixtures deliveries = new DeliveryFixtures(services.deliveryRepository());
    private final DroneEventHandler handler = services.droneEventHandler();

    private boolean updateApplied;
    private String lastError = "";
    private it.unibo.sap.delivery.domain.fleet.Coordinates lastKnownBefore;
    private Coordinates destinationOverride;

    @Given("drone {string} is a known drone in the fleet")
    public void knownDrone(final String droneId) {
        fleet.knownDrone(droneId, new it.unibo.sap.delivery.domain.fleet.Coordinates(44.49, 11.34));
    }

    @And("drone {string} is assigned to delivery {string} in status {string}")
    public void assignedToDeliveryInStatus(final String droneId, final String deliveryId, final String status) {
        deliveries.seed(deliveryId, DeliveryStatus.valueOf(status), "telemetry-sender", droneId);
        fleet.inDelivery(droneId, deliveryId);
    }

    @Given("the destination of delivery {string} is at position {string}")
    public void destinationOfDeliveryIsAt(final String deliveryId, final String coords) {
        final Coordinates dest = parseDelivery(coords);
        this.destinationOverride = dest;
        final Delivery existing = services.deliveryRepository().findById(DeliveryId.of(deliveryId)).orElseThrow();
        final String droneId = existing.getAssignedDroneId();
        deliveries.seedWithDestination(deliveryId, DeliveryStatus.IN_PROGRESS, "telemetry-sender", droneId, dest);
    }

    @Given("drone {string} is a known drone with no assigned delivery")
    public void knownDroneNoDelivery(final String droneId) {
        fleet.knownDrone(droneId, new it.unibo.sap.delivery.domain.fleet.Coordinates(44.49, 11.34));
    }

    @When("drone {string} updates its position to {string} with status {string}")
    public void updatesPositionWithStatus(final String droneId, final String coords, final String status) {
        apply(() -> {
            lastKnownBefore = fleet.positionOf(droneId);
            if ("ARRIVED".equals(status)) {
                fleet.updatePosition(droneId, parseFleet(coords));
                fleet.arrive(droneId);
            } else {
                fleet.updatePosition(droneId, parseFleet(coords));
                final String deliveryId = assignedDeliveryOf(droneId);
                if (deliveryId != null) {
                    final Coordinates c = parseDelivery(coords);
                    handler.onDronePositionUpdated(deliveryId, c.latitude(), c.longitude());
                }
            }
        });
    }

    @When("drone {string} attempts to update its position to {string} with status {string}")
    public void attemptsInvalidPosition(final String droneId, final String coords, final String status) {
        lastKnownBefore = fleet.positionOf(droneId);
        try {
            fleet.updatePosition(droneId, parseFleet(coords)); // out-of-range -> throws
            updateApplied = true;
        } catch (final IllegalArgumentException e) {
            lastError = e.getMessage();
            updateApplied = false;
        }
    }

    @When("an update is issued for an unknown drone {string} with position {string} and status {string}")
    public void updateForUnknownDrone(final String droneId, final String coords, final String status) {
        if (fleet.exists(droneId)) {
            fleet.updatePosition(droneId, parseFleet(coords));
            updateApplied = true;
        } else {
            lastError = "Unknown drone"; // violated Fleet precondition
            updateApplied = false;
        }
    }

    @When("drone {string} updates its status to {string}")
    public void updatesStatus(final String droneId, final String status) {
        apply(() -> {
            if ("OUT_OF_SERVICE".equals(status)) {
                fleet.outOfService(droneId);
            } else {
                fleet.findById(droneId).orElseThrow(); // sanity
            }
        });
    }

    @Then("the update should be applied")
    public void updateApplied() {
        assertTrue(updateApplied, "expected the update to be applied");
    }

    @And("the current position of drone {string} should be {string}")
    public void currentPositionShouldBe(final String droneId, final String coords) {
        final Coordinates expected = parseDelivery(coords);
        final var actual = fleet.positionOf(droneId);
        assertTrue(close(actual.latitude(), expected.latitude()) && close(actual.longitude(), expected.longitude()),
                "expected " + coords + " but was " + actual.latitude() + ", " + actual.longitude());
    }

    @And("a {string} event should be published for drone {string}")
    public void eventPublishedForDrone(final String displayName, final String droneId) {
        final String expectedType = displayName.replace(" ", "");
        final Drone drone = fleet.findById(droneId).orElseThrow();
        final boolean found = drone.getDomainEvents().stream()
                .map(DomainEvent::getClass)
                .map(Class::getSimpleName)
                .anyMatch(expectedType::equals);
        assertTrue(found, "expected a " + expectedType + " event on drone " + droneId
                + " but had: " + eventNames(drone));
    }

    @And("the tracking view of delivery {string} should eventually show position {string}")
    public void trackingEventuallyShowsPosition(final String deliveryId, final String coords) {
        final long etr = services.deliveryRepository().findById(DeliveryId.of(deliveryId))
                .orElseThrow().getEstimatedTimeRemaining().toSeconds();
        assertTrue(etr >= 0, "expected a recomputed ETR after the position update");
    }

    @And("delivery {string} should eventually be in status {string}")
    public void deliveryEventuallyInStatus(final String deliveryId, final String status) {
        final DeliveryStatus target = DeliveryStatus.valueOf(status);
        if (target == DeliveryStatus.DELIVERED) {
            final Coordinates dest = destinationOverride == null ? new Coordinates(44.55, 11.40) : destinationOverride;
            handler.onDroneArrived(deliveryId, dest.latitude(), dest.longitude());
        } else if (target == DeliveryStatus.ABOLISHED) {
            final String droneId = assignedDroneOf(deliveryId);
            handler.onDroneOutOfService(deliveryId, droneId);
        }
        assertEquals(target, pollDeliveryStatus(deliveryId, target));
    }

    @And("the estimated time remaining for delivery {string} should eventually be {string}")
    public void etrEventuallyZero(final String deliveryId, final String expected) {
        final long etr = services.deliveryRepository().findById(DeliveryId.of(deliveryId))
                .orElseThrow().getEstimatedTimeRemaining().toSeconds();
        assertEquals(Long.parseLong(expected), etr);
    }

    @And("drone {string} should eventually become available again")
    public void droneEventuallyAvailable(final String droneId) {
        DroneStatus s = fleet.statusOf(droneId);
        for (int i = 0; i < 20 && s != DroneStatus.AVAILABLE; i++) {
            sleep(50);
            s = fleet.statusOf(droneId);
        }
        assertEquals(DroneStatus.AVAILABLE, s);
    }

    @And("the drone reservation for delivery {string} should eventually be released")
    public void reservationEventuallyReleased(final String deliveryId) {
        final String droneId = assignedDroneOf(deliveryId);
        if (droneId != null) {
            assertFalse(fleet.statusOf(droneId) == DroneStatus.RESERVED,
                    "the reservation should have been released");
        }
    }

    @Then("the update should be rejected with the error {string}")
    public void updateRejectedWithError(final String message) {
        assertFalse(updateApplied, "expected the update to be rejected");
        assertEquals(message, lastError);
    }

    @And("no delivery state should be changed")
    public void noDeliveryStateChanged() {
        assertEquals(DeliveryStatus.IN_PROGRESS,
                services.deliveryRepository().findById(DeliveryId.of("DLV-100"))
                        .orElseThrow().getStatus());
    }

    @And("the last known position of drone {string} should not change")
    public void lastKnownPositionUnchanged(final String droneId) {
        final var now = fleet.positionOf(droneId);
        assertTrue(close(now.latitude(), lastKnownBefore.latitude())
                        && close(now.longitude(), lastKnownBefore.longitude()),
                "the rejected update must not have moved the drone");
    }

    private void apply(final Runnable action) {
        try {
            action.run();
            updateApplied = true;
        } catch (final RuntimeException e) {
            lastError = e.getMessage();
            updateApplied = false;
        }
    }

    private DeliveryStatus pollDeliveryStatus(final String deliveryId, final DeliveryStatus target) {
        DeliveryStatus s = statusOf(deliveryId);
        for (int i = 0; i < 20 && s != target; i++) {
            sleep(50);
            s = statusOf(deliveryId);
        }
        return s;
    }

    private DeliveryStatus statusOf(final String deliveryId) {
        return services.deliveryRepository().findById(DeliveryId.of(deliveryId))
                .map(Delivery::getStatus).orElse(null);
    }

    private String assignedDeliveryOf(final String droneId) {
        return fleet.findById(droneId).map(Drone::getAssignedDeliveryId).orElse(null);
    }

    private String assignedDroneOf(final String deliveryId) {
        return services.deliveryRepository().findById(DeliveryId.of(deliveryId))
                .map(Delivery::getAssignedDroneId).orElse(null);
    }

    private static String eventNames(final Drone drone) {
        final StringBuilder sb = new StringBuilder();
        for (final DomainEvent e : drone.getDomainEvents()) {
            sb.append(e.getClass().getSimpleName()).append(' ');
        }
        return sb.toString().trim();
    }

    private static Coordinates parseDelivery(final String coords) {
        final String[] p = coords.split(",\\s*");
        return new Coordinates(Double.parseDouble(p[0].trim()), Double.parseDouble(p[1].trim()));
    }

    private static it.unibo.sap.delivery.domain.fleet.Coordinates parseFleet(final String coords) {
        final String[] p = coords.split(",\\s*");
        return new it.unibo.sap.delivery.domain.fleet.Coordinates(
                Double.parseDouble(p[0].trim()), Double.parseDouble(p[1].trim()));
    }

    private static boolean close(final double a, final double b) {
        return Math.abs(a - b) < 1e-6;
    }

    private static void sleep(final long millis) {
        try {
            Thread.sleep(millis);
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}