package it.unibo.sap.delivery.application;

import it.unibo.sap.delivery.application.fleet.FleetPort;
import it.unibo.sap.delivery.domain.deliveries.Coordinates;
import it.unibo.sap.delivery.domain.deliveries.Delivery;
import it.unibo.sap.delivery.domain.deliveries.DeliveryId;
import it.unibo.sap.delivery.domain.deliveries.DeliveryStatus;
import it.unibo.sap.delivery.domain.deliveries.EstimatedTimeRemaining;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

public class DroneEventHandler {

    private final DeliveryRepository deliveryRepository;
    private final TrackingSessionRegistry trackingSessions;
    private final TrackingSessionEventObserver trackingObserver;
    private final FleetPort fleetPort;
    private final double droneSpeedUnitsPerSecond;

    public DroneEventHandler(final DeliveryRepository deliveryRepository,
                             final TrackingSessionRegistry trackingSessions,
                             final TrackingSessionEventObserver trackingObserver,
                             final FleetPort fleetPort,
                             final double droneSpeedUnitsPerSecond) {
        this.deliveryRepository = deliveryRepository;
        this.trackingSessions = trackingSessions;
        this.trackingObserver = trackingObserver;
        this.fleetPort = fleetPort;
        this.droneSpeedUnitsPerSecond = droneSpeedUnitsPerSecond;
    }

    public void onDronePositionUpdated(final String deliveryId, final double latitude, final double longitude) {
        final Optional<Delivery> found = deliveryRepository.findById(DeliveryId.of(deliveryId));
        if (found.isEmpty()) {
            return;
        }
        final Delivery delivery = found.get();
        if (delivery.getStatus() != DeliveryStatus.IN_PROGRESS) {
            return;
        }
        final Coordinates current = new Coordinates(latitude, longitude);
        final Coordinates destination = delivery.getRequest().destination().coordinates();
        final EstimatedTimeRemaining etr = computeEtr(current, destination);

        delivery.updateEstimatedTime(etr);
        deliveryRepository.save(delivery);
        delivery.clearDomainEvents();

        pushToTrackers(delivery, latitude, longitude, etr.toSeconds());
    }

    public void onDroneArrived(final String deliveryId, final double latitude, final double longitude) {
        final Optional<Delivery> found = deliveryRepository.findById(DeliveryId.of(deliveryId));
        if (found.isEmpty()) {
            return;
        }
        final Delivery delivery = found.get();
        if (delivery.getStatus() != DeliveryStatus.IN_PROGRESS) {
            return;
        }
        delivery.complete();
        deliveryRepository.save(delivery);
        delivery.clearDomainEvents();

        fleetPort.completeDelivery(deliveryId);

        pushToTrackers(delivery, latitude, longitude, 0L);
    }

    public void onDroneOutOfService(final String deliveryId, final String droneId) {
        if (deliveryId == null) {
            return;
        }
        final Optional<Delivery> found = deliveryRepository.findById(DeliveryId.of(deliveryId));
        if (found.isEmpty()) {
            return;
        }
        final Delivery delivery = found.get();
        if (delivery.getStatus() != DeliveryStatus.IN_PROGRESS) {
            return;
        }
        final LocalDateTime slot = delivery.getRequest().requestedDateTime().scheduledAt();
        delivery.abolish();
        deliveryRepository.save(delivery);
        delivery.clearDomainEvents();
        fleetPort.releaseReservation(droneId, deliveryId, slot);
    }

    private EstimatedTimeRemaining computeEtr(final Coordinates current, final Coordinates destination) {
        final double distance = current.euclideanDistanceTo(destination);
        final long seconds = Math.round(distance / droneSpeedUnitsPerSecond);
        return EstimatedTimeRemaining.of(Duration.ofSeconds(Math.max(seconds, 0)));
    }

    private void pushToTrackers(final Delivery delivery, final double lat, final double lon, final long etrSeconds) {
        final var sessions = trackingSessions.findByDelivery(delivery.getId());
        if (sessions.isEmpty()) {
            return;
        }
        trackingObserver.pushTrackingUpdate(
                delivery.getId().value(),
                delivery.getStatus().name(),
                lat, lon, etrSeconds);
    }
}