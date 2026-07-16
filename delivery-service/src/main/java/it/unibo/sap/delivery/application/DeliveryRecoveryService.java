package it.unibo.sap.delivery.application;

import it.unibo.sap.delivery.application.fleet.FleetAssignmentResult;
import it.unibo.sap.delivery.application.fleet.FleetFeasibilityRequest;
import it.unibo.sap.delivery.application.fleet.FleetPort;
import it.unibo.sap.delivery.domain.deliveries.Delivery;
import it.unibo.sap.delivery.domain.deliveries.DeliveryRequest;

public class DeliveryRecoveryService {

    private final DeliveryRepository deliveryRepository;
    private final FleetPort fleetPort;
    private final DeliveryServiceEventObserver metricsObserver;

    public DeliveryRecoveryService(final DeliveryRepository deliveryRepository,
                                   final FleetPort fleetPort) {
        this(deliveryRepository, fleetPort, DeliveryServiceEventObserver.NO_OP);
    }

    public DeliveryRecoveryService(final DeliveryRepository deliveryRepository,
                                   final FleetPort fleetPort,
                                   final DeliveryServiceEventObserver metricsObserver) {
        this.deliveryRepository = deliveryRepository;
        this.fleetPort = fleetPort;
        this.metricsObserver = metricsObserver;
    }

    public void recover() {
        int restarted = 0;
        int cancelled = 0;
        int skipped = 0;
        for (final Delivery delivery : deliveryRepository.findAll()) {
            switch (delivery.getStatus()) {
                case IN_PROGRESS -> {
                    if (restartInFlight(delivery)) {
                        restarted++;
                    }
                }
                case SCHEDULED -> {
                    cancelScheduled(delivery);
                    cancelled++;
                }
                default -> skipped++;
            }
        }
        System.out.println("delivery recovery complete: restarted=" + restarted
                + ", cancelled=" + cancelled + ", skipped=" + skipped);
    }

    private boolean restartInFlight(final Delivery delivery) {
        final FleetAssignmentResult outcome = fleetPort.assignNearestDrone(feasibilityOf(delivery));
        if (!outcome.assigned()) {
            System.err.println("WARN recovery: in-flight delivery " + delivery.getId().value()
                    + " could not be restarted (" + outcome.rejectionReason() + "); more active "
                    + "deliveries than seeded drones - left IN_PROGRESS without a drone");
            return false;
        }
        final String droneId = outcome.droneIdOpt().orElseThrow();
        fleetPort.startDelivery(droneId);
        metricsObserver.onDeliveryInProgress();
        return true;
    }

    private void cancelScheduled(final Delivery delivery) {
        final String droneId = delivery.getAssignedDroneId();
        delivery.cancel();
        deliveryRepository.save(delivery);
        delivery.clearDomainEvents();
        if (droneId != null) {
            fleetPort.releaseReservation(droneId, delivery.getId().value());
        }
    }

    private FleetFeasibilityRequest feasibilityOf(final Delivery delivery) {
        final DeliveryRequest request = delivery.getRequest();
        final long deadlineMinutes = request.deadline() == null
                ? 0 : request.deadline().maxDuration().toMinutes();
        return new FleetFeasibilityRequest(
                delivery.getId().value(),
                request.parcel().weightKg(),
                request.pickupLocation().coordinates().latitude(),
                request.pickupLocation().coordinates().longitude(),
                request.destination().coordinates().latitude(),
                request.destination().coordinates().longitude(),
                deadlineMinutes);
    }
}
