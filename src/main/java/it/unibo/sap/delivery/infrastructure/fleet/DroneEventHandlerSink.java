package it.unibo.sap.delivery.infrastructure.fleet;

import it.unibo.sap.delivery.application.DroneEventHandler;

public class DroneEventHandlerSink implements DroneTelemetrySink {

    private final DroneEventHandler handler;

    public DroneEventHandlerSink(final DroneEventHandler handler) {
        this.handler = handler;
    }

    @Override
    public void onPositionUpdated(final String deliveryId, final double latitude, final double longitude) {
        handler.onDronePositionUpdated(deliveryId, latitude, longitude);
    }

    @Override
    public void onArrived(final String deliveryId, final double latitude, final double longitude) {
        handler.onDroneArrived(deliveryId, latitude, longitude);
    }

}
