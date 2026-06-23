package it.unibo.sap.delivery.domain.fleet.events;

import it.unibo.sap.common.ddd.DomainEvent;
import it.unibo.sap.delivery.domain.fleet.DroneId;

import java.time.Instant;
import it.unibo.sap.delivery.domain.fleet.Position;

public record PositionUpdated(DroneId droneId, Position position, Instant occurredOn) implements DomainEvent {
}
