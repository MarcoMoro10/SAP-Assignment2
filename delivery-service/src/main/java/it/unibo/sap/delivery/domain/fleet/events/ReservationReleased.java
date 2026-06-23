package it.unibo.sap.delivery.domain.fleet.events;

import it.unibo.sap.common.ddd.DomainEvent;
import it.unibo.sap.delivery.domain.fleet.DroneId;

import java.time.Instant;

public record ReservationReleased(DroneId droneId, String deliveryId, Instant occurredOn) implements DomainEvent {
}
