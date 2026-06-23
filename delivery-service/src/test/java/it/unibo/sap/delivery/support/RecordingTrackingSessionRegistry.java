package it.unibo.sap.delivery.support;

import it.unibo.sap.delivery.application.TrackingSessionRegistry;
import it.unibo.sap.delivery.domain.deliveries.DeliveryId;
import it.unibo.sap.delivery.domain.deliveries.TrackingSession;

import java.util.ArrayList;
import java.util.List;

/**
 * Fake {@link TrackingSessionRegistry} for unit tests that simply records the registered sessions
 * so a test can assert that tracking opened a session.
 */
public final class RecordingTrackingSessionRegistry implements TrackingSessionRegistry {

    public final List<TrackingSession> registered = new ArrayList<>();

    @Override
    public void register(final TrackingSession session) {
        registered.add(session);
    }

    @Override
    public List<TrackingSession> findByDelivery(final DeliveryId deliveryId) {
        return registered.stream()
                .filter(s -> s.getDeliveryId().equals(deliveryId))
                .toList();
    }
}
