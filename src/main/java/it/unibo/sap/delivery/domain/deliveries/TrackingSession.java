package it.unibo.sap.delivery.domain.deliveries;

import it.unibo.sap.common.ddd.Entity;

import java.time.Instant;
import java.util.Objects;

public class TrackingSession implements Entity<TrackingSessionId> {

    private final TrackingSessionId id;
    private final DeliveryId deliveryId;
    private final SenderId senderId;
    private final Instant openedAt;

    private TrackingSession(final TrackingSessionId id, final DeliveryId deliveryId,
                            final SenderId senderId, final Instant openedAt) {
        this.id = Objects.requireNonNull(id);
        this.deliveryId = Objects.requireNonNull(deliveryId);
        this.senderId = Objects.requireNonNull(senderId);
        this.openedAt = Objects.requireNonNull(openedAt);
    }

    public static TrackingSession open(final DeliveryId deliveryId, final SenderId senderId) {
        return new TrackingSession(TrackingSessionId.generate(), deliveryId, senderId, Instant.now());
    }

    public DeliveryId getDeliveryId() {
        return deliveryId;
    }

    public SenderId getSenderId() {
        return senderId;
    }

    @Override
    public TrackingSessionId getId() {
        return id;
    }
}
