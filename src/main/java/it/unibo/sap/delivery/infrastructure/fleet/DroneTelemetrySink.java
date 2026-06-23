package it.unibo.sap.delivery.infrastructure.fleet;

public interface DroneTelemetrySink {

    void onPositionUpdated(String deliveryId, double latitude, double longitude);

    void onArrived(String deliveryId, double latitude, double longitude);

}
