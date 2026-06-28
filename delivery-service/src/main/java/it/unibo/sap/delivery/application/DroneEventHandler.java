package it.unibo.sap.delivery.application;

import it.unibo.sap.delivery.application.fleet.FleetPort;
import it.unibo.sap.delivery.domain.deliveries.Coordinates;
import it.unibo.sap.delivery.domain.deliveries.Delivery;
import it.unibo.sap.delivery.domain.deliveries.DeliveryId;
import it.unibo.sap.delivery.domain.deliveries.DeliveryStatus;
import it.unibo.sap.delivery.domain.deliveries.EstimatedTimeRemaining;

import java.time.Duration;
import java.util.Optional;

public class DroneEventHandler {

    private final DeliveryRepository deliveryRepository;
    private final TrackingSessionRegistry trackingSessions;
    private final TrackingSessionEventObserver trackingObserver;
    private final FleetPort fleetPort;
    private final double droneSpeedUnitsPerSecond;
    private final DeliveryServiceEventObserver metricsObserver;
    private final EstimatedTimeView estimatedTimeView;

    public DroneEventHandler(final DeliveryRepository deliveryRepository,
                             final TrackingSessionRegistry trackingSessions,
                             final TrackingSessionEventObserver trackingObserver,
                             final FleetPort fleetPort,
                             final double droneSpeedUnitsPerSecond) {
        this(deliveryRepository, trackingSessions, trackingObserver, fleetPort, droneSpeedUnitsPerSecond,
                DeliveryServiceEventObserver.NO_OP);
    }

    public DroneEventHandler(final DeliveryRepository deliveryRepository,
                             final TrackingSessionRegistry trackingSessions,
                             final TrackingSessionEventObserver trackingObserver,
                             final FleetPort fleetPort,
                             final double droneSpeedUnitsPerSecond,
                             final DeliveryServiceEventObserver metricsObserver) {
        this(deliveryRepository, trackingSessions, trackingObserver, fleetPort, droneSpeedUnitsPerSecond,
                metricsObserver, EstimatedTimeView.NO_OP);
    }

    public DroneEventHandler(final DeliveryRepository deliveryRepository,
                             final TrackingSessionRegistry trackingSessions,
                             final TrackingSessionEventObserver trackingObserver,
                             final FleetPort fleetPort,
                             final double droneSpeedUnitsPerSecond,
                             final DeliveryServiceEventObserver metricsObserver,
                             final EstimatedTimeView estimatedTimeView) {
        this.deliveryRepository = deliveryRepository;
        this.trackingSessions = trackingSessions;
        this.trackingObserver = trackingObserver;
        this.fleetPort = fleetPort;
        this.droneSpeedUnitsPerSecond = droneSpeedUnitsPerSecond;
        this.metricsObserver = metricsObserver;
        this.estimatedTimeView = estimatedTimeView;
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

        estimatedTimeView.update(deliveryId, etr.toSeconds());

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
        metricsObserver.onDeliveryCompleted();

        estimatedTimeView.clear(deliveryId);

        fleetPort.completeDelivery(deliveryId);

        pushToTrackers(delivery, latitude, longitude, 0L);
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