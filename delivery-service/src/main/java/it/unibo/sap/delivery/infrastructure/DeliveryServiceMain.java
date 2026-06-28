package it.unibo.sap.delivery.infrastructure;

import io.vertx.core.Vertx;
import it.unibo.sap.delivery.application.DeliveryRepository;
import it.unibo.sap.delivery.application.DeliveryService;
import it.unibo.sap.delivery.application.DeliveryServiceEventObserver;
import it.unibo.sap.delivery.application.DeliveryServiceImpl;
import it.unibo.sap.delivery.application.DroneEventHandler;
import it.unibo.sap.delivery.application.EventStore;
import it.unibo.sap.delivery.application.GeocodingPort;
import it.unibo.sap.delivery.application.TrackingSessionEventObserver;
import it.unibo.sap.delivery.application.TrackingSessionRegistry;
import it.unibo.sap.delivery.infrastructure.fleet.DroneEventHandlerSink;
import it.unibo.sap.delivery.infrastructure.fleet.FleetModule;
import it.unibo.sap.delivery.infrastructure.fleet.FleetSeeder;
import it.unibo.sap.delivery.infrastructure.fleet.InMemoryDroneRepository;

public class DeliveryServiceMain {

    static final int DEFAULT_DELIVERY_SERVICE_PORT = 9002;
    static final int DEFAULT_ADMIN_PORT = 9003;
    static final int DEFAULT_METRICS_PORT = 9400;

    static final double DRONE_SPEED_UNITS_PER_SECOND = 0.01;

    public static void main(final String[] args) {
        final int deliveryPort = Env.getInt("DELIVERY_PORT", DEFAULT_DELIVERY_SERVICE_PORT);
        final int adminPort = Env.getInt("FLEET_PORT", DEFAULT_ADMIN_PORT);
        final int metricsPort = Env.getInt("DELIVERY_METRICS_PORT", DEFAULT_METRICS_PORT);

        final Vertx vertx = Vertx.vertx();

        final EventStore eventStore = new FileBasedEventStore();
        final DeliveryRepository deliveryRepository = new EventSourcedDeliveryRepository(eventStore);
        final TrackingSessionRegistry trackingRegistry = new InMemoryTrackingSessionRegistry();
        final GeocodingPort geocoding = new GeocodingService();
        final TrackingSessionEventObserver trackingObserver =
                new VertxTrackingSessionEventObserver(vertx.eventBus());
        final DeliveryServiceEventObserver metricsObserver =
                new PrometheusDeliveryServiceObserver(metricsPort);

        final InMemoryDroneRepository droneRepository = new InMemoryDroneRepository();
        final FleetModule fleetModule = new FleetModule(droneRepository, DRONE_SPEED_UNITS_PER_SECOND);
        final InMemoryEstimatedTimeView estimatedTimeView = new InMemoryEstimatedTimeView();

        final DeliveryService deliveryService = new DeliveryServiceImpl(
                deliveryRepository, fleetModule, geocoding, trackingRegistry, metricsObserver, estimatedTimeView);

        final DroneEventHandler droneEventHandler = new DroneEventHandler(
                deliveryRepository, trackingRegistry, trackingObserver,
                fleetModule, DRONE_SPEED_UNITS_PER_SECOND, metricsObserver, estimatedTimeView);
        fleetModule.setTelemetrySink(new DroneEventHandlerSink(droneEventHandler));

        FleetSeeder.seed(droneRepository);

        vertx.deployVerticle(new DeliveryServiceController(deliveryService, deliveryPort));
        vertx.deployVerticle(new FleetMonitoringController(deliveryService, adminPort));
        vertx.deployVerticle(new VertxSchedulerVerticle(deliveryService));
    }
}
