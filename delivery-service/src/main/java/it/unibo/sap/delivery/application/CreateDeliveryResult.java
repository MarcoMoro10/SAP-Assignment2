package it.unibo.sap.delivery.application;

public record CreateDeliveryResult(String deliveryId,
                                   String status,
                                   String assignedDroneId) {
}
