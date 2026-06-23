package it.unibo.sap.delivery.domain.deliveries.events;

import it.unibo.sap.common.ddd.DomainEvent;
import it.unibo.sap.delivery.domain.deliveries.DeliveryId;

import java.time.Instant;
import java.time.LocalDateTime;

public record DeliveryScheduled(DeliveryId deliveryId, LocalDateTime slot, Instant occurredOn) implements DomainEvent {
}
