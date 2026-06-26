package it.unibo.sap.delivery.infrastructure.fleet;

import it.unibo.sap.delivery.domain.fleet.Coordinates;
import it.unibo.sap.delivery.domain.fleet.Drone;
import it.unibo.sap.delivery.domain.fleet.Position;

public class DroneSimulator {

    private static final long TICK_MILLIS = 1000;

    private final Drone drone;
    private final String deliveryId;
    private final Coordinates destination;
    private final DroneTelemetrySink sink;
    private final double speedUnitsPerTick;
    private volatile boolean stopped = false;

    public DroneSimulator(final Drone drone, final String deliveryId,
                          final Coordinates destination, final DroneTelemetrySink sink,
                          final double speedUnitsPerTick) {
        this.drone = drone;
        this.deliveryId = deliveryId;
        this.destination = destination;
        this.sink = sink;
        this.speedUnitsPerTick = speedUnitsPerTick;
    }

    public void start() {
        Thread.ofVirtual().name("drone-sim-" + drone.getId().value()).start(this::run);
    }

    public void stop() {
        this.stopped = true;
    }

    private void run() {
        try {
            while (!stopped) {
                Thread.sleep(TICK_MILLIS);
                if (stopped) {
                    return;
                }
                final Coordinates current = drone.getPosition().coordinates();
                final double remaining = current.euclideanDistanceTo(destination);
                if (remaining <= speedUnitsPerTick) {
                    drone.updatePosition(Position.at(destination.latitude(), destination.longitude()));
                    drone.arrived();
                    sink.onArrived(deliveryId, destination.latitude(), destination.longitude());
                    return;
                }
                final Coordinates next = step(current, destination, remaining);
                drone.updatePosition(Position.at(next.latitude(), next.longitude()));
                sink.onPositionUpdated(deliveryId, next.latitude(), next.longitude());
            }
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }


    private Coordinates step(final Coordinates from, final Coordinates to, final double remaining) {
        final double fraction = speedUnitsPerTick / remaining;
        final double lat = from.latitude() + (to.latitude() - from.latitude()) * fraction;
        final double lon = from.longitude() + (to.longitude() - from.longitude()) * fraction;
        return new Coordinates(lat, lon);
    }
}