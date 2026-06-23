package it.unibo.sap.delivery.application;

import it.unibo.sap.delivery.application.DeliveryExceptions.BadRequestException;
import it.unibo.sap.delivery.application.DeliveryExceptions.CannotCancelInFlightException;
import it.unibo.sap.delivery.application.DeliveryExceptions.DeliveryNotFoundException;
import it.unibo.sap.delivery.application.DeliveryExceptions.ValidationRejectedException;
import it.unibo.sap.delivery.application.fleet.FleetAssignmentResult;
import it.unibo.sap.delivery.application.fleet.FleetFeasibilityRequest;
import it.unibo.sap.delivery.application.fleet.FleetPort;
import it.unibo.sap.delivery.application.fleet.FleetReservationResult;
import it.unibo.sap.delivery.application.fleet.FleetViews;
import it.unibo.sap.delivery.domain.deliveries.Coordinates;
import it.unibo.sap.delivery.domain.deliveries.Deadline;
import it.unibo.sap.delivery.domain.deliveries.Delivery;
import it.unibo.sap.delivery.domain.deliveries.DeliveryId;
import it.unibo.sap.delivery.domain.deliveries.DeliveryRequest;
import it.unibo.sap.delivery.domain.deliveries.DeliverySchedulingView;
import it.unibo.sap.delivery.domain.deliveries.DeliveryStatus;
import it.unibo.sap.delivery.domain.deliveries.DeliveryTrackingView;
import it.unibo.sap.delivery.domain.deliveries.Location;
import it.unibo.sap.delivery.domain.deliveries.Package;
import it.unibo.sap.delivery.domain.deliveries.RequestedDateTime;
import it.unibo.sap.delivery.domain.deliveries.SenderId;
import it.unibo.sap.delivery.domain.deliveries.TrackingSession;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public class DeliveryServiceImpl implements DeliveryService {

    private static final int MAX_SCHEDULING_HORIZON_DAYS = 7;

    private final DeliveryRepository deliveryRepository;
    private final FleetPort fleetPort;
    private final GeocodingPort geocodingPort;
    private final TrackingSessionRegistry trackingSessions;

    public DeliveryServiceImpl(final DeliveryRepository deliveryRepository,
                               final FleetPort fleetPort,
                               final GeocodingPort geocodingPort,
                               final TrackingSessionRegistry trackingSessions) {
        this.deliveryRepository = deliveryRepository;
        this.fleetPort = fleetPort;
        this.geocodingPort = geocodingPort;
        this.trackingSessions = trackingSessions;
    }

    @Override
    public CreateDeliveryResult createDelivery(final CreateDeliveryCommand cmd) {
        final Coordinates pickupCoord = geocode(cmd.startStreet(), cmd.startNumber());
        final Coordinates destCoord = geocode(cmd.destinationStreet(), cmd.destinationNumber());

        final Package parcel = buildParcel(cmd.weightKg());
        final Location pickup = Location.of(pickupCoord, cmd.startStreet() + ", " + cmd.startNumber());
        final Location destination = Location.of(destCoord, cmd.destinationStreet() + ", " + cmd.destinationNumber());
        final RequestedDateTime when = buildRequestedDateTime(cmd);
        final Deadline deadline = cmd.deadlineMinutes() > 0 ? Deadline.ofMinutes(cmd.deadlineMinutes()) : null;

        final DeliveryRequest request = new DeliveryRequest(parcel, pickup, destination, when, deadline);
        final Delivery delivery = Delivery.createRequest(SenderId.of(cmd.senderId()), request);

        validateShippingTime(when);
        delivery.validationPassed();

        final FleetFeasibilityRequest feas = new FleetFeasibilityRequest(
                delivery.getId().value(),
                cmd.weightKg(),
                pickupCoord.latitude(),
                pickupCoord.longitude(),
                destCoord.latitude(),
                destCoord.longitude(),
                cmd.deadlineMinutes());

        final CreateDeliveryResult result;
        if (when.isImmediate()) {
            result = handleImmediate(delivery, feas);
        } else {
            result = handleScheduled(delivery, feas, when.scheduledAt());
        }

        deliveryRepository.save(delivery);
        delivery.clearDomainEvents();
        return result;
    }

    private CreateDeliveryResult handleImmediate(final Delivery delivery, final FleetFeasibilityRequest feas) {
        final FleetAssignmentResult outcome = fleetPort.assignNearestDrone(feas);
        if (!outcome.assigned()) {
            final String reason = outcome.rejectionReasonOpt().orElse("No drone available");
            delivery.reject(reason);
            throw new ValidationRejectedException(reason);
        }
        final String droneId = outcome.droneIdOpt().orElseThrow();
        delivery.assignDrone(droneId);
        delivery.begin();
        fleetPort.startDelivery(droneId);
        return new CreateDeliveryResult(delivery.getId().value(), delivery.getStatus().name(), droneId);
    }

    private CreateDeliveryResult handleScheduled(final Delivery delivery, final FleetFeasibilityRequest feas,
                                                 final LocalDateTime slot) {
        final FleetReservationResult outcome = fleetPort.reserveDroneForSlot(feas, slot);
        if (!outcome.reserved()) {
            final String reason = outcome.rejectionReasonOpt().orElse("No drone available for the requested time");
            delivery.reject(reason);
            throw new ValidationRejectedException(reason);
        }
        delivery.schedule();
        final String droneId = outcome.droneIdOpt().orElse(null);
        if (droneId != null) {
            delivery.reserveDrone(droneId);
        }
        return new CreateDeliveryResult(delivery.getId().value(), delivery.getStatus().name(), droneId);
    }

    @Override
    public void cancelDelivery(final String deliveryId, final String senderId) {
        final Delivery delivery = loadOwned(deliveryId, senderId);
        if (delivery.getStatus() == DeliveryStatus.IN_PROGRESS) {
            throw new CannotCancelInFlightException();
        }
        final String droneId = delivery.getAssignedDroneId();
        final LocalDateTime slot = delivery.getRequest().requestedDateTime().scheduledAt();
        delivery.cancel();
        if (droneId != null) {
            fleetPort.releaseReservation(droneId, deliveryId, slot);
        }
        deliveryRepository.save(delivery);
        delivery.clearDomainEvents();
    }

    @Override
    public Optional<DeliveryTrackingView> getDelivery(final String deliveryId, final String senderId) {
        final Optional<Delivery> found = deliveryRepository.findById(DeliveryId.of(deliveryId));
        if (found.isEmpty() || !found.get().isOwnedBy(SenderId.of(senderId))) {
            return Optional.empty();
        }
        final Delivery d = found.get();
        final Coordinates c = d.getRequest().destination().coordinates();
        return Optional.of(new DeliveryTrackingView(
                d.getId().value(),
                d.getStatus(),
                c.latitude(),
                c.longitude(),
                d.getEstimatedTimeRemaining().toSeconds()));
    }

    @Override
    public TrackingHandle startTracking(final String deliveryId, final String senderId) {
        final Delivery delivery = loadOwned(deliveryId, senderId);
        final TrackingSession session = TrackingSession.open(delivery.getId(), SenderId.of(senderId));
        trackingSessions.register(session);
        return new TrackingHandle(session.getId().value(), delivery.getId().value());
    }

    @Override
    public void assignDueScheduledDeliveries(final LocalDateTime now) {
        final List<Delivery> scheduled = deliveryRepository.findAll().stream()
                .filter(d -> d.getStatus() == DeliveryStatus.SCHEDULED)
                .filter(d -> {
                    final LocalDateTime slot = d.getRequest().requestedDateTime().scheduledAt();
                    return slot != null && !slot.isAfter(now);
                })
                .toList();
        for (final Delivery d : scheduled) {
            final LocalDateTime slot = d.getRequest().requestedDateTime().scheduledAt();
            final FleetAssignmentResult outcome = fleetPort.assignReservedDrone(d.getId().value(), slot);
            if (outcome.assigned()) {
                final String droneId = outcome.droneIdOpt().orElseThrow();
                d.assignDrone(droneId);
                d.begin();
                fleetPort.startDelivery(droneId);
                deliveryRepository.save(d);
                d.clearDomainEvents();
            }
        }
    }

    @Override
    public List<FleetViews.FleetDroneView> viewFleet() {
        return fleetPort.fleetMonitoringView();
    }

    @Override
    public List<DeliverySchedulingView> viewScheduling(final String droneIdFilter) {
        return deliveryRepository.findAll().stream()
                .filter(d -> d.getStatus() == DeliveryStatus.SCHEDULED)
                .filter(d -> droneIdFilter == null || droneIdFilter.isBlank()
                        || droneIdFilter.equals(d.getAssignedDroneId()))
                .map(d -> new DeliverySchedulingView(
                        d.getAssignedDroneId(),
                        d.getId().value(),
                        d.getRequest().requestedDateTime().scheduledAt(),
                        d.getStatus()))
                .collect(java.util.stream.Collectors.toList());
    }

    private Delivery loadOwned(final String deliveryId, final String senderId) {
        final Delivery delivery = deliveryRepository.findById(DeliveryId.of(deliveryId))
                .orElseThrow(DeliveryNotFoundException::new);
        if (!delivery.isOwnedBy(SenderId.of(senderId))) {
            throw new DeliveryNotFoundException();
        }
        return delivery;
    }

    private Coordinates geocode(final String street, final int number) {
        try {
            return geocodingPort.geocode(street, number);
        } catch (final GeocodingPort.InvalidAddressException e) {
            throw new BadRequestException("Invalid address");
        }
    }

    private Package buildParcel(final double weightKg) {
        try {
            return new Package(weightKg);
        } catch (final IllegalArgumentException e) {
            throw new BadRequestException("Invalid package weight");
        }
    }

    private RequestedDateTime buildRequestedDateTime(final CreateDeliveryCommand cmd) {
        if (cmd.immediate()) {
            return RequestedDateTime.immediateRequest();
        }
        if (cmd.scheduledAt() == null) {
            throw new BadRequestException("Invalid shipping time");
        }
        return RequestedDateTime.scheduledAt(cmd.scheduledAt());
    }

    private void validateShippingTime(final RequestedDateTime when) {
        if (when.isImmediate()) {
            return;
        }
        final LocalDateTime slot = when.scheduledAt();
        final LocalDateTime now = LocalDateTime.now();
        if (slot.isBefore(now)) {
            throw new BadRequestException("Invalid shipping time");
        }
        if (slot.isAfter(now.plusDays(MAX_SCHEDULING_HORIZON_DAYS))) {
            throw new ValidationRejectedException("Shipping time exceeds the maximum scheduling horizon");
        }
    }
}