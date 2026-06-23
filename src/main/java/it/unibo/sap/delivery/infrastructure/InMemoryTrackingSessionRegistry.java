package it.unibo.sap.delivery.infrastructure;

import it.unibo.sap.common.hexagonal.OutputAdapter;
import it.unibo.sap.delivery.application.TrackingSessionRegistry;
import it.unibo.sap.delivery.domain.deliveries.DeliveryId;
import it.unibo.sap.delivery.domain.deliveries.TrackingSession;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class InMemoryTrackingSessionRegistry implements TrackingSessionRegistry, OutputAdapter {

    private final Map<String, TrackingSession> store = new ConcurrentHashMap<>();

    @Override
    public void register(final TrackingSession session) {
        store.put(session.getId().value(), session);
    }

    @Override
    public List<TrackingSession> findByDelivery(final DeliveryId deliveryId) {
        return store.values().stream()
                .filter(s -> s.getDeliveryId().equals(deliveryId))
                .collect(Collectors.toList());
    }

    public Optional<TrackingSession> findById(final String trackingSessionId) {
        return Optional.ofNullable(store.get(trackingSessionId));
    }
}
