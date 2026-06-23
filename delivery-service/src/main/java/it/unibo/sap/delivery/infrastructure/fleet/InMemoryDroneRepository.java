package it.unibo.sap.delivery.infrastructure.fleet;

import it.unibo.sap.delivery.domain.fleet.Drone;
import it.unibo.sap.delivery.domain.fleet.DroneId;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryDroneRepository {

    private final Map<String, Drone> store = new ConcurrentHashMap<>();

    public void save(final Drone drone) {
        store.put(drone.getId().value(), drone);
    }

    public Optional<Drone> findById(final DroneId id) {
        return Optional.ofNullable(store.get(id.value()));
    }

    public List<Drone> findAll() {
        return new ArrayList<>(store.values());
    }

    public boolean contains(final DroneId id) {
        return store.containsKey(id.value());
    }
}
