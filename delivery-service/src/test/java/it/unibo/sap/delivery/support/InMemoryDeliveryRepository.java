package it.unibo.sap.delivery.support;

import it.unibo.sap.delivery.application.DeliveryRepository;
import it.unibo.sap.delivery.domain.deliveries.Delivery;
import it.unibo.sap.delivery.domain.deliveries.DeliveryId;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory fake of {@link DeliveryRepository} for unit and component tests: no file I/O,
 * fully isolated, and {@link #clear()} resets it between scenarios.
 */
public final class InMemoryDeliveryRepository implements DeliveryRepository {

    private final Map<String, Delivery> store = new ConcurrentHashMap<>();

    @Override
    public void save(final Delivery delivery) {
        store.put(delivery.getId().value(), delivery);
    }

    @Override
    public Optional<Delivery> findById(final DeliveryId id) {
        return Optional.ofNullable(store.get(id.value()));
    }

    @Override
    public List<Delivery> findAll() {
        return new ArrayList<>(store.values());
    }

    @Override
    public void deleteById(final DeliveryId id) {
        store.remove(id.value());
    }

    public void clear() {
        store.clear();
    }
}
