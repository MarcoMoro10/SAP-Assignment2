package it.unibo.sap.delivery.domain.deliveries;

import java.time.LocalDateTime;

public record DeliverySchedulingView(String droneId,
                                     String deliveryId,
                                     LocalDateTime scheduledAt,
                                     DeliveryStatus status) {
}
