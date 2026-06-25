package it.unibo.sap.delivery.domain;

import it.unibo.sap.delivery.domain.deliveries.Coordinates;
import it.unibo.sap.delivery.domain.deliveries.Deadline;
import it.unibo.sap.delivery.domain.deliveries.EstimatedTimeRemaining;
import it.unibo.sap.delivery.domain.deliveries.Package;
import it.unibo.sap.delivery.domain.deliveries.SenderId;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests of delivery value objects. Note: this exercises the deliveries-context
 * {@link Coordinates}, which is intentionally distinct from the fleet-context Coordinates.
 */
class DeliveryValueObjectsTest {

    @Test
    void packageRejectsNonPositiveWeight() {
        assertThrows(IllegalArgumentException.class, () -> new Package(0));
        assertThrows(IllegalArgumentException.class, () -> new Package(-1));
        assertEquals(2.0, new Package(2.0).weightKg());
    }

    @Test
    void coordinatesRejectOutOfRangeValues() {
        assertThrows(IllegalArgumentException.class, () -> new Coordinates(91, 0));
        assertThrows(IllegalArgumentException.class, () -> new Coordinates(0, 181));
    }

    @Test
    void coordinatesComputeEuclideanDistance() {
        final Coordinates a = new Coordinates(0, 0);
        final Coordinates b = new Coordinates(3, 4);
        assertEquals(5.0, a.euclideanDistanceTo(b), 1e-9);
    }

    @Test
    void senderIdRejectsBlank() {
        assertThrows(IllegalArgumentException.class, () -> SenderId.of(" "));
        assertEquals(SenderId.of("user-1"), SenderId.of("user-1"));
    }

    @Test
    void deadlineRejectsNonPositiveDuration() {
        assertThrows(IllegalArgumentException.class, () -> Deadline.ofMinutes(0));
        assertThrows(IllegalArgumentException.class, () -> Deadline.ofMinutes(-5));
        assertThrows(IllegalArgumentException.class, () -> new Deadline(Duration.ZERO));
        assertEquals(Duration.ofMinutes(30), Deadline.ofMinutes(30).maxDuration());
    }

    @Test
    void estimatedTimeRemainingRejectsNegativeAndExposesSeconds() {
        assertThrows(IllegalArgumentException.class, () -> EstimatedTimeRemaining.of(Duration.ofSeconds(-1)));
        assertEquals(90, EstimatedTimeRemaining.ofSeconds(90).toSeconds());
        assertTrue(EstimatedTimeRemaining.zero().toSeconds() == 0);
    }
}
