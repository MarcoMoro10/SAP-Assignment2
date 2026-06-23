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
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public class Drone implements AggregateRoot<DroneId> {

    private final DroneId id;
    private DroneStatus status;
    private Position position;
    private final PayloadCapacity payloadCapacity;
    private String assignedDeliveryId;
    private final Set<LocalDateTime> reservedSlots = new HashSet<>();
    private final List<DomainEvent> domainEvents = new ArrayList<>();

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

    public boolean isAvailable() {
        return status == DroneStatus.AVAILABLE;
    }

    public boolean canCarry(final double weightKg) {
        return payloadCapacity.canCarry(weightKg);
    }

    public boolean isSlotFree(final LocalDateTime slot) {
        return !reservedSlots.contains(slot);
    }


    public void reserveSlot(final String deliveryId, final LocalDateTime slot) {
        if (!isSlotFree(slot)) {
            throw new IllegalStateException("No drone available for the requested time");
        }
        reservedSlots.add(slot);
        this.status = DroneStatus.RESERVED;
        registerEvent(new DroneReserved(id, deliveryId, slot, Instant.now()));
    }

    public void releaseReservation(final String deliveryId, final LocalDateTime slot) {
        reservedSlots.remove(slot);
        if (reservedSlots.isEmpty()) {
            this.status = DroneStatus.AVAILABLE;
        }
        this.assignedDeliveryId = null;
        registerEvent(new ReservationReleased(id, deliveryId, Instant.now()));
    }

    public void assign(final String deliveryId) {
        this.assignedDeliveryId = deliveryId;
        this.status = DroneStatus.ASSIGNED;
        registerEvent(new DroneAssigned(id, deliveryId, Instant.now()));
    }

    public void startDelivery() {
        this.status = DroneStatus.IN_DELIVERY;
    }

    public void updatePosition(final Position newPosition) {
        this.position = Objects.requireNonNull(newPosition);
        registerEvent(new PositionUpdated(id, newPosition, Instant.now()));
    }

    public void arrived() {
        this.status = DroneStatus.ARRIVED;
        registerEvent(new DroneArrived(id, assignedDeliveryId, Instant.now()));
    }

    public void becomeAvailable() {
        this.status = DroneStatus.AVAILABLE;
        this.assignedDeliveryId = null;
    }

    public void goOutOfService() {
        this.status = DroneStatus.OUT_OF_SERVICE;
        registerEvent(new DroneOutOfService(id, Instant.now()));
    }

    public DroneStatus getStatus() {
        return status;
    }

    public Position getPosition() {
        return position;
    }

    public PayloadCapacity getPayloadCapacity() {
        return payloadCapacity;
    }

    public String getAssignedDeliveryId() {
        return assignedDeliveryId;
    }

    public boolean isCarryingPackage() {
        return status == DroneStatus.IN_DELIVERY;
    }

    @Override
    public DroneId getId() {
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
}
