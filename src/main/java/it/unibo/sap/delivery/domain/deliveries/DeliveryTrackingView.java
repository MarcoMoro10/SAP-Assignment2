package it.unibo.sap.delivery.domain.deliveries;

public record DeliveryTrackingView(String deliveryId,
                                   DeliveryStatus status,
                                   double currentLatitude,
                                   double currentLongitude,
                                   long estimatedTimeRemainingSeconds) {
}
