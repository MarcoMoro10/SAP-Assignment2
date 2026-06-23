package it.unibo.sap.delivery.application;

import java.time.LocalDateTime;

public record CreateDeliveryCommand(String senderId,
                                    double weightKg,
                                    String startStreet,
                                    int startNumber,
                                    String destinationStreet,
                                    int destinationNumber,
                                    boolean immediate,
                                    LocalDateTime scheduledAt,
                                    long deadlineMinutes) {
}
