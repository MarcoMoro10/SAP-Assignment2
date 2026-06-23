package it.unibo.sap.acceptance.support;

import it.unibo.sap.delivery.application.DeliveryRepository;
import it.unibo.sap.delivery.domain.deliveries.Coordinates;
import it.unibo.sap.delivery.domain.deliveries.Deadline;
import it.unibo.sap.delivery.domain.deliveries.Delivery;
import it.unibo.sap.delivery.domain.deliveries.DeliveryId;
import it.unibo.sap.delivery.domain.deliveries.DeliveryRequest;
import it.unibo.sap.delivery.domain.deliveries.DeliveryStatus;
import it.unibo.sap.delivery.domain.deliveries.EstimatedTimeRemaining;
import it.unibo.sap.delivery.domain.deliveries.Location;
import it.unibo.sap.delivery.domain.deliveries.Package;
import it.unibo.sap.delivery.domain.deliveries.RequestedDateTime;
import it.unibo.sap.delivery.domain.deliveries.SenderId;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Seeds {@link Delivery} aggregates directly into the delivery repository in a chosen
 * status, owned by a chosen sender. Used by cancel/track scenarios whose {@code Given}
 * presupposes an existing delivery in a particular state. Aggregates are reconstituted via
 * the domain's {@code reconstitute} factory, so the repository holds genuine domain objects.
 */
public final class DeliveryFixtures {

    private final DeliveryRepository repository;

    public DeliveryFixtures(final DeliveryRepository repository) {
        this.repository = repository;
    }

    /** Seeds a delivery with the given id, status and owner, with a fixed Bologna route. */
    public Delivery seed(final String deliveryId, final DeliveryStatus status, final String senderAccountId,
                         final String assignedDroneId) {
        return seedWithDestination(deliveryId, status, senderAccountId, assignedDroneId,
                new Coordinates(44.50, 11.35));
    }

    /** Like {@link #seed} but lets the caller pin the destination coordinates (used by arrival tests). */
    public Delivery seedWithDestination(final String deliveryId, final DeliveryStatus status,
                                        final String senderAccountId, final String assignedDroneId,
                                        final Coordinates destination) {
        final DeliveryRequest request = sampleRequest(status, destination);
        final EstimatedTimeRemaining etr = status == DeliveryStatus.DELIVERED
                ? EstimatedTimeRemaining.zero()
                : EstimatedTimeRemaining.ofSeconds(120);
        final Delivery delivery = Delivery.reconstitute(
                DeliveryId.of(deliveryId),
                SenderId.of(senderAccountId),
                request,
                status,
                assignedDroneId,
                etr);
        repository.save(delivery);
        delivery.clearDomainEvents();
        return delivery;
    }

    private DeliveryRequest sampleRequest(final DeliveryStatus status, final Coordinates destination) {
        final Package parcel = new Package(2.0);
        final Location pickup = Location.of(new Coordinates(44.49, 11.34), "via Emilia, 9");
        final Location dest = Location.of(destination, "via Veneto, 5");
        final RequestedDateTime when = status == DeliveryStatus.SCHEDULED
                ? RequestedDateTime.scheduledAt(LocalDateTime.now().plusDays(1))
                : RequestedDateTime.immediateRequest();
        final Deadline deadline = new Deadline(Duration.ofMinutes(60));
        return new DeliveryRequest(parcel, pickup, dest, when, deadline);
    }
}
