package it.unibo.sap.acceptance.support;

import it.unibo.sap.delivery.domain.fleet.Coordinates;
import it.unibo.sap.delivery.domain.fleet.Drone;
import it.unibo.sap.delivery.domain.fleet.DroneId;
import it.unibo.sap.delivery.domain.fleet.DroneStatus;
import it.unibo.sap.delivery.domain.fleet.PayloadCapacity;
import it.unibo.sap.delivery.domain.fleet.Position;
import it.unibo.sap.delivery.infrastructure.fleet.InMemoryDroneRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * In-process test fixture for the Fleet bounded context.
 *
 * <p>The Fleet has no network surface (see {@code telemetry_ingestion.feature}); its
 * legitimate test access is through the domain itself. This fixture creates and arranges
 * drones via their real factory and public mutators, and reads their state back. It is the
 * "Option A / FleetTestFixture" seam frozen in the architecture: {@code Given} steps shape
 * Fleet state in-process, while {@code When/Then} of HTTP-facing scenarios still go through
 * the public REST API.
 *
 * <p>The in-memory drone repository has no delete operation (production never needs one),
 * so "removing" a drone is emulated by overwriting it (same id) with a fresh drone, or by
 * parking it OUT_OF_SERVICE so it is neither AVAILABLE nor a viable carrier candidate.
 */
public final class FleetTestFixture {

    /** Seeded drone ids, used when an explicit configuration must shadow the whole fleet. */
    private static final List<String> SEEDED_IDS = List.of("DRN-1", "DRN-2", "DRN-3");

    private final InMemoryDroneRepository drones;

    public FleetTestFixture(final InMemoryDroneRepository drones) {
        this.drones = drones;
    }

    /** Adds (or overwrites) an AVAILABLE drone at a position with a capacity. */
    public Drone addAvailable(final String droneId, final Coordinates at, final double capacityKg) {
        final Drone drone = Drone.create(DroneId.of(droneId), Position.at(at), new PayloadCapacity(capacityKg));
        drones.save(drone);
        return drone;
    }

    /**
     * Replaces the effective fleet with exactly the drones the caller will add next:
     * every seeded drone NOT in {@code keepIds} is parked OUT_OF_SERVICE, so it can be
     * neither assigned nor counted as a carrier. Call before adding the precise set.
     */
    public void keepOnly(final List<String> keepIds) {
        for (final Drone d : drones.findAll()) {
            if (!keepIds.contains(d.getId().value())) {
                park(d);
            }
        }
    }

    /** Parks every drone OUT_OF_SERVICE: the fleet has no assignable drone. */
    public void emptyFleet() {
        for (final Drone d : drones.findAll()) {
            park(d);
        }
    }

    /**
     * Overwrites every seeded drone so the heaviest capacity in the fleet is {@code maxKg}.
     * Used by the "package too heavy for any drone" scenario, where a package above this
     * value must be rejected by the domain with "No drone can carry this package".
     */
    public void capacityCeiling(final double maxKg) {
        for (final String id : SEEDED_IDS) {
            final Coordinates at = findById(id)
                    .map(d -> d.getPosition().coordinates())
                    .orElse(new Coordinates(44.49, 11.34));
            addAvailable(id, at, maxKg);
        }
    }

    /** Ensures a known AVAILABLE drone (default 10 kg) exists at the given position. */
    public Drone knownDrone(final String droneId, final Coordinates at) {
        return findById(droneId).orElseGet(() -> addAvailable(droneId, at, 10.0));
    }

    /** Marks a drone as currently flying a delivery (IN_DELIVERY), carrying a package. */
    public Drone inDelivery(final String droneId, final String deliveryId) {
        final Drone drone = findOrFail(droneId);
        drone.assign(deliveryId);
        drone.startDelivery();
        drones.save(drone);
        return drone;
    }

    /** Reserves a slot for a drone (RESERVED). */
    public Drone reserveSlot(final String droneId, final String deliveryId, final LocalDateTime slot) {
        final Drone drone = findOrFail(droneId);
        drone.reserveSlot(deliveryId, slot);
        drones.save(drone);
        return drone;
    }

    public void updatePosition(final String droneId, final Coordinates to) {
        final Drone drone = findOrFail(droneId);
        drone.updatePosition(Position.at(to));
        drones.save(drone);
    }

    public void arrive(final String droneId) {
        final Drone drone = findOrFail(droneId);
        drone.arrived();
        drones.save(drone);
    }

    public void outOfService(final String droneId) {
        final Drone drone = findOrFail(droneId);
        drone.goOutOfService();
        drones.save(drone);
    }

    public Optional<Drone> findById(final String droneId) {
        return drones.findById(DroneId.of(droneId));
    }

    public boolean exists(final String droneId) {
        return drones.contains(DroneId.of(droneId));
    }

    public DroneStatus statusOf(final String droneId) {
        return findOrFail(droneId).getStatus();
    }

    public Coordinates positionOf(final String droneId) {
        return findOrFail(droneId).getPosition().coordinates();
    }

    /** Count of drones that are not parked OUT_OF_SERVICE (the "active" fleet). */
    public long activeCount() {
        return drones.findAll().stream()
                .filter(d -> d.getStatus() != DroneStatus.OUT_OF_SERVICE)
                .count();
    }

    public List<Drone> all() {
        return drones.findAll();
    }

    private void park(final Drone d) {
        final Drone disabled = Drone.create(d.getId(), d.getPosition(), d.getPayloadCapacity());
        disabled.goOutOfService();
        drones.save(disabled);
    }

    private Drone findOrFail(final String droneId) {
        return findById(droneId)
                .orElseThrow(() -> new IllegalStateException("Unknown drone in fixture: " + droneId));
    }
}
