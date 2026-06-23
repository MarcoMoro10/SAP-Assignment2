package it.unibo.sap.delivery.domain.deliveries;

import it.unibo.sap.common.ddd.AggregateRoot;
import it.unibo.sap.common.ddd.DomainEvent;
import it.unibo.sap.delivery.domain.deliveries.events.DeliveryAbolished;
import it.unibo.sap.delivery.domain.deliveries.events.DeliveryBegun;
import it.unibo.sap.delivery.domain.deliveries.events.DeliveryCancelled;
import it.unibo.sap.delivery.domain.deliveries.events.DeliveryCompleted;
import it.unibo.sap.delivery.domain.deliveries.events.DeliveryRequestCreated;
import it.unibo.sap.delivery.domain.deliveries.events.DeliveryScheduled;
import it.unibo.sap.delivery.domain.deliveries.events.EstimatedTimeUpdated;
import it.unibo.sap.delivery.domain.deliveries.events.ValidationDeliveryPassed;
import it.unibo.sap.delivery.domain.deliveries.events.ValidationDeliveryRejected;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class Delivery implements AggregateRoot<DeliveryId> {

    private final DeliveryId id;
    private final SenderId senderId;
    private final DeliveryRequest request;
    private DeliveryStatus status;
    private String assignedDroneId;
    private EstimatedTimeRemaining estimatedTimeRemaining;
    private final List<DomainEvent> domainEvents = new ArrayList<>();

    private Delivery(final DeliveryId id, final SenderId senderId, final DeliveryRequest request,
                     final DeliveryStatus status) {
        this.id = Objects.requireNonNull(id);
        this.senderId = Objects.requireNonNull(senderId);
        this.request = Objects.requireNonNull(request);
        this.status = Objects.requireNonNull(status);
    }

    public static Delivery createRequest(final SenderId senderId, final DeliveryRequest request) {
        final var delivery = new Delivery(DeliveryId.generate(), senderId, request, DeliveryStatus.REQUESTED);
        delivery.registerEvent(new DeliveryRequestCreated(delivery.id, Instant.now()));
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
        this.status = DeliveryStatus.VALIDATED;
        registerEvent(new ValidationDeliveryPassed(id, Instant.now()));
    }

    public void reject(final String reason) {
        this.status = DeliveryStatus.REJECTED;
        registerEvent(new ValidationDeliveryRejected(id, reason, Instant.now()));
    }

    public void schedule() {
        requireStatus(DeliveryStatus.VALIDATED, "schedule");
        this.status = DeliveryStatus.SCHEDULED;
        registerEvent(new DeliveryScheduled(id, request.requestedDateTime().scheduledAt(), Instant.now()));
    }

    public void reserveDrone(final String droneId) {
        if (status != DeliveryStatus.SCHEDULED) {
            throw new IllegalStateException("Can only reserve a drone for a scheduled delivery, not in " + status);
        }
        this.assignedDroneId = Objects.requireNonNull(droneId);
    }

    public void assignDrone(final String droneId) {
        if (status != DeliveryStatus.VALIDATED && status != DeliveryStatus.SCHEDULED) {
            throw new IllegalStateException("Cannot assign a drone in status " + status);
        }
        this.assignedDroneId = Objects.requireNonNull(droneId);
        this.status = DeliveryStatus.ASSIGNED;
    }

    public void begin() {
        requireStatus(DeliveryStatus.ASSIGNED, "begin");
        this.status = DeliveryStatus.IN_PROGRESS;
        registerEvent(new DeliveryBegun(id, Instant.now()));
    }

    public void updateEstimatedTime(final EstimatedTimeRemaining etr) {
        requireStatus(DeliveryStatus.IN_PROGRESS, "updateEstimatedTime");
        this.estimatedTimeRemaining = Objects.requireNonNull(etr);
        registerEvent(new EstimatedTimeUpdated(id, etr.value(), Instant.now()));
    }

    public void complete() {
        requireStatus(DeliveryStatus.IN_PROGRESS, "complete");
        this.status = DeliveryStatus.DELIVERED;
        this.estimatedTimeRemaining = EstimatedTimeRemaining.zero();
        registerEvent(new DeliveryCompleted(id, Instant.now()));
    }

    public void cancel() {
        if (status == DeliveryStatus.IN_PROGRESS) {
            throw new IllegalStateException("Delivery cannot be cancelled once in flight");
        }
        if (status != DeliveryStatus.SCHEDULED && status != DeliveryStatus.ASSIGNED) {
            throw new IllegalStateException("Cannot cancel a delivery in status " + status);
        }
        this.status = DeliveryStatus.CANCELLED;
        registerEvent(new DeliveryCancelled(id, Instant.now()));
    }

    public void abolish() {
        requireStatus(DeliveryStatus.IN_PROGRESS, "abolish");
        this.status = DeliveryStatus.ABOLISHED;
        registerEvent(new DeliveryAbolished(id, Instant.now()));
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

    private void requireStatus(final DeliveryStatus expected, final String action) {
        if (this.status != expected) {
            throw new IllegalStateException("Cannot " + action + " a delivery in status " + status
                    + " (expected " + expected + ")");
        }
    }
}