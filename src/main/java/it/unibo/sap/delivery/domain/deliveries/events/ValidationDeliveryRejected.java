package it.unibo.sap.delivery.domain.deliveries.events;

import it.unibo.sap.common.ddd.DomainEvent;
import it.unibo.sap.delivery.domain.deliveries.DeliveryId;

import java.time.Instant;

public record ValidationDeliveryRejected(DeliveryId deliveryId, String reason, Instant occurredOn) implements DomainEvent {
}
