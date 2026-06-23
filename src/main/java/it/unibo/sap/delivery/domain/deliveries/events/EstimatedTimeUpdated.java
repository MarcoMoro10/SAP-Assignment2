package it.unibo.sap.delivery.domain.deliveries.events;

import it.unibo.sap.common.ddd.DomainEvent;
import it.unibo.sap.delivery.domain.deliveries.DeliveryId;

import java.time.Instant;
import java.time.Duration;

public record EstimatedTimeUpdated(DeliveryId deliveryId, Duration estimatedTimeRemaining, Instant occurredOn) implements DomainEvent {
}
