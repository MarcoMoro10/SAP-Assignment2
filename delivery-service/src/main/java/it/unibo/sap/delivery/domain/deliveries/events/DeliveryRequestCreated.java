package it.unibo.sap.delivery.domain.deliveries.events;

import it.unibo.sap.common.ddd.DomainEvent;
import it.unibo.sap.delivery.domain.deliveries.DeliveryId;
import it.unibo.sap.delivery.domain.deliveries.DeliveryRequest;
import it.unibo.sap.delivery.domain.deliveries.SenderId;

import java.time.Instant;

public record DeliveryRequestCreated(DeliveryId deliveryId,
                                     SenderId senderId,
                                     DeliveryRequest request,
                                     Instant occurredOn) implements DomainEvent {
}
