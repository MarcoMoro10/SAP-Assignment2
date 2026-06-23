package it.unibo.sap.delivery.infrastructure.fleet;

import it.unibo.sap.delivery.domain.fleet.Coordinates;
import it.unibo.sap.delivery.domain.fleet.Drone;
import it.unibo.sap.delivery.domain.fleet.Position;

public class DroneSimulator {

    private static final long TICK_MILLIS = 1000;
    private static final double STEP_FRACTION = 0.05;  // fraction of remaining route per tick

    private final Drone drone;
    private final String deliveryId;
    private final Coordinates destination;
    private final DroneTelemetrySink sink;
    private final double arrivalThreshold;
    private volatile boolean stopped = false;

    public DroneSimulator(final Drone drone, final String deliveryId,
                          final Coordinates destination, final DroneTelemetrySink sink,
                          final double arrivalThreshold) {
        this.drone = drone;
        this.deliveryId = deliveryId;
        this.destination = destination;
        this.sink = sink;
        this.arrivalThreshold = arrivalThreshold;
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
                if (hasArrived(current)) {
                    drone.updatePosition(Position.at(destination.latitude(), destination.longitude()));
                    drone.arrived();
                    sink.onArrived(deliveryId, destination.latitude(), destination.longitude());
                    return;
                }
                final Coordinates next = step(current, destination);
                drone.updatePosition(Position.at(next.latitude(), next.longitude()));
                sink.onPositionUpdated(deliveryId, next.latitude(), next.longitude());
            }
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private boolean hasArrived(final Coordinates current) {
        return current.euclideanDistanceTo(destination) < arrivalThreshold;
    }

    private Coordinates step(final Coordinates from, final Coordinates to) {
        final double lat = from.latitude() + (to.latitude() - from.latitude()) * STEP_FRACTION;
        final double lon = from.longitude() + (to.longitude() - from.longitude()) * STEP_FRACTION;
        return new Coordinates(lat, lon);
    }
}