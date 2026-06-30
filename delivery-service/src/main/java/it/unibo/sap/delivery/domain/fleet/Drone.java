package it.unibo.sap.delivery.domain.fleet;

import it.unibo.sap.common.ddd.AggregateRoot;
import it.unibo.sap.common.ddd.DomainEvent;
import it.unibo.sap.delivery.domain.fleet.events.DroneArrived;
import it.unibo.sap.delivery.domain.fleet.events.DroneAssigned;
import it.unibo.sap.delivery.domain.fleet.events.DroneOutOfService;
import it.unibo.sap.delivery.domain.fleet.events.DroneReserved;
import it.unibo.sap.delivery.domain.fleet.events.PositionUpdated;
import it.unibo.sap.delivery.domain.fleet.events.ReservationReleased;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public class Drone implements AggregateRoot<DroneId> {

    private final DroneId id;
    private DroneStatus status;
    private Position position;
    private final PayloadCapacity payloadCapacity;
    private String assignedDeliveryId;
    private final Map<String, LocalDateTime> reservationsByDelivery = new ConcurrentHashMap<>();
    private final List<DomainEvent> domainEvents = Collections.synchronizedList(new ArrayList<>());

    private Drone(final DroneId id, final DroneStatus status, final Position position,
                  final PayloadCapacity payloadCapacity) {
        this.id = Objects.requireNonNull(id);
        this.status = Objects.requireNonNull(status);
        this.position = Objects.requireNonNull(position);
        this.payloadCapacity = Objects.requireNonNull(payloadCapacity);
    }

    public static Drone create(final DroneId id, final Position position,
                               final PayloadCapacity payloadCapacity) {
        return new Drone(id, DroneStatus.AVAILABLE, position, payloadCapacity);
    }

    public synchronized boolean isAvailable() {
        return status == DroneStatus.AVAILABLE;
    }

    public boolean canCarry(final double weightKg) {
        return payloadCapacity.canCarry(weightKg);
    }

    public synchronized boolean isSlotFree(final LocalDateTime slot) {
        return !reservationsByDelivery.containsValue(slot);
    }


    public synchronized void reserveSlot(final String deliveryId, final LocalDateTime slot) {
        if (!isSlotFree(slot)) {
            throw new IllegalStateException("No drone available for the requested time");
        }
        reservationsByDelivery.put(deliveryId, slot);
        syncStatusToReservations();
        registerEvent(new DroneReserved(id, deliveryId, slot, Instant.now()));
    }

    public synchronized void releaseReservation(final String deliveryId) {
        reservationsByDelivery.remove(deliveryId);
        this.assignedDeliveryId = null;
        syncStatusToReservations();
        registerEvent(new ReservationReleased(id, deliveryId, Instant.now()));
    }

    public synchronized void completeReservation(final String deliveryId) {
        reservationsByDelivery.remove(deliveryId);
        this.assignedDeliveryId = null;
        syncStatusToReservations();
    }

    public synchronized int reservationCount() {
        return reservationsByDelivery.size();
    }

    public synchronized void assign(final String deliveryId) {
        this.assignedDeliveryId = deliveryId;
        this.status = DroneStatus.ASSIGNED;
        registerEvent(new DroneAssigned(id, deliveryId, Instant.now()));
    }

    public synchronized void startDelivery() {
        this.status = DroneStatus.IN_DELIVERY;
    }

    public synchronized void updatePosition(final Position newPosition) {
        this.position = Objects.requireNonNull(newPosition);
        registerEvent(new PositionUpdated(id, newPosition, Instant.now()));
    }

    public synchronized void arrived() {
        this.status = DroneStatus.ARRIVED;
        registerEvent(new DroneArrived(id, assignedDeliveryId, Instant.now()));
    }

    private synchronized void syncStatusToReservations() {
        if (this.status == DroneStatus.OUT_OF_SERVICE) {
            return;
        }
        this.status = reservationsByDelivery.isEmpty()
                ? DroneStatus.AVAILABLE
                : DroneStatus.RESERVED;
    }

    public synchronized void goOutOfService() {
        this.status = DroneStatus.OUT_OF_SERVICE;
        registerEvent(new DroneOutOfService(id, Instant.now()));
    }

    public synchronized DroneStatus getStatus() {
        return status;
    }

    public synchronized Position getPosition() {
        return position;
    }

    public synchronized String getAssignedDeliveryId() {
        return assignedDeliveryId;
    }

    public synchronized boolean isCarryingPackage() {
        return status == DroneStatus.IN_DELIVERY;
    }

    @Override
    public DroneId getId() {
        return id;
    }

    @Override
    public synchronized List<DomainEvent> getDomainEvents() {
        return List.copyOf(domainEvents);
    }

    @Override
    public synchronized void clearDomainEvents() {
        domainEvents.clear();
    }

    protected synchronized void registerEvent(final DomainEvent event) {
        domainEvents.add(event);
    }
}
