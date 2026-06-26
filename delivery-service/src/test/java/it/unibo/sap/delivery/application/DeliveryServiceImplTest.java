package it.unibo.sap.delivery.application;

import it.unibo.sap.delivery.application.DeliveryExceptions.BadRequestException;
import it.unibo.sap.delivery.application.DeliveryExceptions.CannotCancelInFlightException;
import it.unibo.sap.delivery.application.DeliveryExceptions.DeliveryNotFoundException;
import it.unibo.sap.delivery.application.DeliveryExceptions.ValidationRejectedException;
import it.unibo.sap.delivery.domain.deliveries.DeliveryId;
import it.unibo.sap.delivery.domain.deliveries.DeliveryStatus;
import it.unibo.sap.delivery.domain.deliveries.DeliveryTrackingView;
import it.unibo.sap.delivery.support.FakeFleetPort;
import it.unibo.sap.delivery.support.FakeGeocodingPort;
import it.unibo.sap.delivery.support.InMemoryDeliveryRepository;
import it.unibo.sap.delivery.support.RecordingTrackingSessionRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit (solitary) tests of {@link DeliveryServiceImpl} wired to fakes for the repository, fleet,
 * geocoding and tracking ports. No HTTP, no real fleet, no file I/O.
 */
class DeliveryServiceImplTest {

    private InMemoryDeliveryRepository repository;
    private FakeFleetPort fleet;
    private RecordingTrackingSessionRegistry tracking;
    private DeliveryService service;

    @BeforeEach
    void setUp() {
        repository = new InMemoryDeliveryRepository();
        fleet = new FakeFleetPort();
        tracking = new RecordingTrackingSessionRegistry();
        service = new DeliveryServiceImpl(repository, fleet, new FakeGeocodingPort(), tracking);
    }

    private static CreateDeliveryCommand immediate(final String sender, final double weight) {
        return new CreateDeliveryCommand(sender, weight,
                "via Emilia", 9, "via Veneto", 5, true, null, 60);
    }

    @Test
    void createImmediateAssignsADroneAndGoesInProgress() {
        final CreateDeliveryResult result = service.createDelivery(immediate("user-1", 2.0));

        assertEquals(DeliveryStatus.IN_PROGRESS.name(), result.status());
        assertEquals(FakeFleetPort.DEFAULT_DRONE, result.assignedDroneId());
        assertTrue(fleet.startedDrones.contains(FakeFleetPort.DEFAULT_DRONE));
        final Optional<DeliveryTrackingView> stored = service.getDelivery(result.deliveryId(), "user-1");
        assertTrue(stored.isPresent());
        assertEquals(DeliveryStatus.IN_PROGRESS, stored.get().status());
    }

    @Test
    void createImmediateRejectedWhenNoDroneAvailable() {
        fleet.rejectingWith("No drone available");
        final ValidationRejectedException ex = assertThrows(ValidationRejectedException.class,
                () -> service.createDelivery(immediate("user-1", 2.0)));
        assertEquals("No drone available", ex.getMessage());
    }

    @Test
    void createWithInvalidAddressIsBadRequest() {
        final CreateDeliveryCommand cmd = new CreateDeliveryCommand("user-1", 2.0,
                "via Emilia", 0, "via Veneto", 5, true, null, 60);
        final BadRequestException ex = assertThrows(BadRequestException.class,
                () -> service.createDelivery(cmd));
        assertEquals("Invalid address", ex.getMessage());
    }

    @Test
    void createScheduledReservesADroneAndGoesScheduled() {
        final CreateDeliveryCommand cmd = new CreateDeliveryCommand("user-1", 2.0,
                "via Emilia", 9, "via Veneto", 5, false, LocalDateTime.now().plusDays(2), 60);
        final CreateDeliveryResult result = service.createDelivery(cmd);

        assertEquals(DeliveryStatus.SCHEDULED.name(), result.status());
        assertEquals(FakeFleetPort.DEFAULT_DRONE, result.assignedDroneId());
    }

    @Test
    void getDeliveryHidesDeliveriesOwnedByOthers() {
        final CreateDeliveryResult result = service.createDelivery(immediate("user-1", 2.0));
        assertTrue(service.getDelivery(result.deliveryId(), "user-1").isPresent());
        assertFalse(service.getDelivery(result.deliveryId(), "intruder").isPresent());
    }

    @Test
    void cancelScheduledDeliveryReleasesReservation() {
        final CreateDeliveryCommand cmd = new CreateDeliveryCommand("user-1", 2.0,
                "via Emilia", 9, "via Veneto", 5, false, LocalDateTime.now().plusDays(2), 60);
        final CreateDeliveryResult result = service.createDelivery(cmd);

        service.cancelDelivery(result.deliveryId(), "user-1");

        assertEquals(DeliveryStatus.CANCELLED,
                repository.findById(DeliveryId.of(result.deliveryId())).orElseThrow().getStatus());
        assertTrue(fleet.releasedReservations.contains(FakeFleetPort.DEFAULT_DRONE));
    }

    @Test
    void cancelInFlightDeliveryIsRejected() {
        final CreateDeliveryResult result = service.createDelivery(immediate("user-1", 2.0));
        assertThrows(CannotCancelInFlightException.class,
                () -> service.cancelDelivery(result.deliveryId(), "user-1"));
    }

    @Test
    void startTrackingOpensASessionForTheOwner() {
        final CreateDeliveryResult result = service.createDelivery(immediate("user-1", 2.0));

        final TrackingHandle handle = service.startTracking(result.deliveryId(), "user-1");

        assertEquals(result.deliveryId(), handle.deliveryId());
        assertEquals(1, tracking.registered.size());
    }

    @Test
    void startTrackingUnknownDeliveryFails() {
        assertThrows(DeliveryNotFoundException.class,
                () -> service.startTracking("does-not-exist", "user-1"));
    }

    @Test
    void assignDueScheduledDeliveriesStartsThemWhenSlotIsReached() {
        final CreateDeliveryCommand cmd = new CreateDeliveryCommand("user-1", 2.0,
                "via Emilia", 9, "via Veneto", 5, false, LocalDateTime.now().plusMinutes(1), 60);
        final CreateDeliveryResult result = service.createDelivery(cmd);
        assertEquals(DeliveryStatus.SCHEDULED.name(), result.status());

        service.assignDueScheduledDeliveries(LocalDateTime.now().plusMinutes(2));

        assertEquals(DeliveryStatus.IN_PROGRESS,
                repository.findById(DeliveryId.of(result.deliveryId())).orElseThrow().getStatus());
    }
}
