package it.unibo.sap.delivery.domain.deliveries;

public record DeliveryTrackingView(String deliveryId,
                                   DeliveryStatus status,
                                   long estimatedTimeRemainingSeconds) {
}
