package it.unibo.sap.delivery.application.fleet;

public final class FleetViews {

    public record FleetDroneView(String droneId,
                                 String status,
                                 double latitude,
                                 double longitude,
                                 boolean carryingPackage) {
    }

}
