package it.unibo.sap.delivery.domain.fleet.events;

import it.unibo.sap.common.ddd.DomainEvent;
import it.unibo.sap.delivery.domain.fleet.DroneId;

import java.time.Instant;

public record DroneOutOfService(DroneId droneId, Instant occurredOn) implements DomainEvent {
}
