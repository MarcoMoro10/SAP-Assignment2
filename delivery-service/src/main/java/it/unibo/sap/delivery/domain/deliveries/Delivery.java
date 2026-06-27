package it.unibo.sap.delivery.domain.deliveries;

import it.unibo.sap.common.ddd.AggregateRoot;
import it.unibo.sap.common.ddd.DomainEvent;
import it.unibo.sap.delivery.domain.deliveries.events.DeliveryBegun;
import it.unibo.sap.delivery.domain.deliveries.events.DeliveryCancelled;
import it.unibo.sap.delivery.domain.deliveries.events.DeliveryCompleted;
import it.unibo.sap.delivery.domain.deliveries.events.DeliveryRequestCreated;
import it.unibo.sap.delivery.domain.deliveries.events.DeliveryScheduled;
import it.unibo.sap.delivery.domain.deliveries.events.DroneAssigned;
import it.unibo.sap.delivery.domain.deliveries.events.DroneReserved;
import it.unibo.sap.delivery.domain.deliveries.events.EstimatedTimeUpdated;
import it.unibo.sap.delivery.domain.deliveries.events.ValidationDeliveryPassed;
import it.unibo.sap.delivery.domain.deliveries.events.ValidationDeliveryRejected;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class Delivery implements AggregateRoot<DeliveryId> {

    private DeliveryId id;
    private SenderId senderId;
    private DeliveryRequest request;
    private DeliveryStatus status;
    private String assignedDroneId;
    private EstimatedTimeRemaining estimatedTimeRemaining;
    private final List<DomainEvent> domainEvents = new ArrayList<>();

    public Delivery() {
    }

    private Delivery(final DeliveryId id, final SenderId senderId, final DeliveryRequest request,
                     final DeliveryStatus status) {
        this.id = Objects.requireNonNull(id);
        this.senderId = Objects.requireNonNull(senderId);
        this.request = Objects.requireNonNull(request);
        this.status = Objects.requireNonNull(status);
    }

    public static Delivery createRequest(final SenderId senderId, final DeliveryRequest request) {
        final var delivery = new Delivery();
        delivery.recordAndApply(new DeliveryRequestCreated(
                DeliveryId.generate(), senderId, request, Instant.now()));
        return delivery;
    }

    public static Delivery reconstitute(final DeliveryId id, final SenderId senderId,
                                        final DeliveryRequest request, final DeliveryStatus status,
                                        final String assignedDroneId,
                                        final EstimatedTimeRemaining estimatedTimeRemaining) {
        final var delivery = new Delivery(id, senderId, request, status);
        delivery.assignedDroneId = assignedDroneId;
        delivery.estimatedTimeRemaining = estimatedTimeRemaining;
        return delivery;
    }


    public void validationPassed() {
        requireStatus(DeliveryStatus.REQUESTED, "validate");
        recordAndApply(new ValidationDeliveryPassed(id, Instant.now()));
    }

    public void reject(final String reason) {
        recordAndApply(new ValidationDeliveryRejected(id, reason, Instant.now()));
    }

    public void schedule() {
        requireStatus(DeliveryStatus.VALIDATED, "schedule");
        recordAndApply(new DeliveryScheduled(id, request.requestedDateTime().scheduledAt(), Instant.now()));
    }

    public void reserveDrone(final String droneId) {
        if (status != DeliveryStatus.SCHEDULED) {
            throw new IllegalStateException("Can only reserve a drone for a scheduled delivery, not in " + status);
        }
        recordAndApply(new DroneReserved(id, Objects.requireNonNull(droneId), Instant.now()));
    }

    public void assignDrone(final String droneId) {
        if (status != DeliveryStatus.VALIDATED && status != DeliveryStatus.SCHEDULED) {
            throw new IllegalStateException("Cannot assign a drone in status " + status);
        }
        recordAndApply(new DroneAssigned(id, Objects.requireNonNull(droneId), Instant.now()));
    }

    public void begin() {
        requireStatus(DeliveryStatus.ASSIGNED, "begin");
        recordAndApply(new DeliveryBegun(id, Instant.now()));
    }

    public void updateEstimatedTime(final EstimatedTimeRemaining etr) {
        requireStatus(DeliveryStatus.IN_PROGRESS, "updateEstimatedTime");
        recordAndApply(new EstimatedTimeUpdated(id, Objects.requireNonNull(etr).value(), Instant.now()));
    }

    public void complete() {
        requireStatus(DeliveryStatus.IN_PROGRESS, "complete");
        recordAndApply(new DeliveryCompleted(id, Instant.now()));
    }

    public void cancel() {
        if (status == DeliveryStatus.IN_PROGRESS) {
            throw new IllegalStateException("Delivery cannot be cancelled once in flight");
        }
        if (status != DeliveryStatus.SCHEDULED && status != DeliveryStatus.ASSIGNED) {
            throw new IllegalStateException("Cannot cancel a delivery in status " + status);
        }
        recordAndApply(new DeliveryCancelled(id, Instant.now()));
    }

    public void apply(final DomainEvent event) {
        switch (event) {
            case DeliveryRequestCreated e -> {
                this.id = e.deliveryId();
                this.senderId = e.senderId();
                this.request = e.request();
                this.status = DeliveryStatus.REQUESTED;
            }
            case ValidationDeliveryPassed _ -> this.status = DeliveryStatus.VALIDATED;
            case ValidationDeliveryRejected _ -> this.status = DeliveryStatus.REJECTED;
            case DeliveryScheduled _ -> this.status = DeliveryStatus.SCHEDULED;
            case DroneReserved e -> this.assignedDroneId = e.droneId();
            case DroneAssigned e -> {
                this.assignedDroneId = e.droneId();
                this.status = DeliveryStatus.ASSIGNED;
            }
            case DeliveryBegun _ -> this.status = DeliveryStatus.IN_PROGRESS;
            case EstimatedTimeUpdated e ->
                    this.estimatedTimeRemaining = EstimatedTimeRemaining.of(e.estimatedTimeRemaining());
            case DeliveryCompleted _ -> {
                this.status = DeliveryStatus.DELIVERED;
                this.estimatedTimeRemaining = EstimatedTimeRemaining.zero();
            }
            case DeliveryCancelled _ -> this.status = DeliveryStatus.CANCELLED;
            default -> throw new IllegalArgumentException(
                    "Unknown delivery event: " + event.getClass().getName());
        }
    }

    public SenderId getSenderId() {
        return senderId;
    }

    public DeliveryRequest getRequest() {
        return request;
    }

    public DeliveryStatus getStatus() {
        return status;
    }

    public String getAssignedDroneId() {
        return assignedDroneId;
    }

    public EstimatedTimeRemaining getEstimatedTimeRemaining() {
        return estimatedTimeRemaining == null ? EstimatedTimeRemaining.zero() : estimatedTimeRemaining;
    }

    public boolean isOwnedBy(final SenderId candidate) {
        return this.senderId.equals(candidate);
    }

    @Override
    public DeliveryId getId() {
        return id;
    }

    @Override
    public List<DomainEvent> getDomainEvents() {
        return Collections.unmodifiableList(domainEvents);
    }

    @Override
    public void clearDomainEvents() {
        domainEvents.clear();
    }

    protected void registerEvent(final DomainEvent event) {
        domainEvents.add(event);
    }

    private void recordAndApply(final DomainEvent event) {
        registerEvent(event);
        apply(event);
    }

    private void requireStatus(final DeliveryStatus expected, final String action) {
        if (this.status != expected) {
            throw new IllegalStateException("Cannot " + action + " a delivery in status " + status
                    + " (expected " + expected + ")");
        }
    }
}