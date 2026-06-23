package it.unibo.sap.delivery.domain.fleet.events;

import it.unibo.sap.common.ddd.DomainEvent;
import it.unibo.sap.delivery.domain.fleet.DroneId;

import java.time.Instant;
import java.time.LocalDateTime;

public record DroneReserved(DroneId droneId, String deliveryId, LocalDateTime slot, Instant occurredOn) implements DomainEvent {
}
