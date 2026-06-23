package it.unibo.sap.delivery.infrastructure;

import io.vertx.core.eventbus.EventBus;
import io.vertx.core.json.JsonObject;
import it.unibo.sap.common.hexagonal.OutputAdapter;
import it.unibo.sap.delivery.application.TrackingSessionEventObserver;

public class VertxTrackingSessionEventObserver implements TrackingSessionEventObserver, OutputAdapter {

    public static final String TRACKING_ADDRESS_PREFIX = "tracking.";

    private final EventBus eventBus;

    public VertxTrackingSessionEventObserver(final EventBus eventBus) {
        this.eventBus = eventBus;
    }

    @Override
    public void pushTrackingUpdate(final String deliveryId,
                                   final String status,
                                   final double latitude,
                                   final double longitude,
                                   final long estimatedTimeRemainingSeconds) {
        final JsonObject update = new JsonObject()
                .put("deliveryId", deliveryId)
                .put("status", status)
                .put("position", new JsonObject()
                        .put("latitude", latitude)
                        .put("longitude", longitude))
                .put("estimatedTimeRemainingSeconds", estimatedTimeRemainingSeconds)
                .put("estimatedTimeRemainingFormatted", formatEtr(estimatedTimeRemainingSeconds));
        eventBus.publish(TRACKING_ADDRESS_PREFIX + deliveryId, update);
    }

    private static String formatEtr(final long totalSeconds) {
        final long s = Math.max(0, totalSeconds);
        return String.format("%02d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60);
    }
}