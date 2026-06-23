package it.unibo.sap.delivery.infrastructure;

import io.vertx.core.Vertx;
import it.unibo.sap.delivery.application.DeliveryRepository;
import it.unibo.sap.delivery.application.DeliveryService;
import it.unibo.sap.delivery.application.DeliveryServiceImpl;
import it.unibo.sap.delivery.application.DroneEventHandler;
import it.unibo.sap.delivery.application.GeocodingPort;
import it.unibo.sap.delivery.application.TrackingSessionEventObserver;
import it.unibo.sap.delivery.application.TrackingSessionRegistry;
import it.unibo.sap.delivery.infrastructure.fleet.DroneEventHandlerSink;
import it.unibo.sap.delivery.infrastructure.fleet.FleetModule;
import it.unibo.sap.delivery.infrastructure.fleet.FleetSeeder;
import it.unibo.sap.delivery.infrastructure.fleet.InMemoryDroneRepository;

public class DeliveryServiceMain {

    static final int DELIVERY_SERVICE_PORT = 8082;
    static final int ADMIN_PORT = 8083;

    static final double DRONE_SPEED_UNITS_PER_SECOND = 0.01;

    public static void main(final String[] args) {
        final Vertx vertx = Vertx.vertx();

        final DeliveryRepository deliveryRepository = new FileBasedDeliveryRepository();
        final TrackingSessionRegistry trackingRegistry = new InMemoryTrackingSessionRegistry();
        final GeocodingPort geocoding = new GeocodingService();
        final TrackingSessionEventObserver trackingObserver =
                new VertxTrackingSessionEventObserver(vertx.eventBus());

        final InMemoryDroneRepository droneRepository = new InMemoryDroneRepository();
        final FleetModule fleetModule = new FleetModule(droneRepository, DRONE_SPEED_UNITS_PER_SECOND);

        final DeliveryService deliveryService = new DeliveryServiceImpl(
                deliveryRepository, fleetModule, geocoding, trackingRegistry);

        final DroneEventHandler droneEventHandler = new DroneEventHandler(
                deliveryRepository, trackingRegistry, trackingObserver,
                fleetModule, DRONE_SPEED_UNITS_PER_SECOND);
        fleetModule.setTelemetrySink(new DroneEventHandlerSink(droneEventHandler));

        FleetSeeder.seed(droneRepository);

        vertx.deployVerticle(new DeliveryServiceController(deliveryService, DELIVERY_SERVICE_PORT));
        vertx.deployVerticle(new FleetMonitoringController(deliveryService, ADMIN_PORT));
        vertx.deployVerticle(new VertxSchedulerVerticle(deliveryService));
    }
}
