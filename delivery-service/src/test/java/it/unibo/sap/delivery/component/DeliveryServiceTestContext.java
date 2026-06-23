package it.unibo.sap.delivery.component;

import io.vertx.core.Verticle;
import io.vertx.core.Vertx;
import io.vertx.ext.web.client.WebClient;
import it.unibo.sap.delivery.application.DeliveryService;
import it.unibo.sap.delivery.application.DeliveryServiceImpl;
import it.unibo.sap.delivery.application.DroneEventHandler;
import it.unibo.sap.delivery.application.GeocodingPort;
import it.unibo.sap.delivery.application.TrackingSessionEventObserver;
import it.unibo.sap.delivery.application.TrackingSessionRegistry;
import it.unibo.sap.delivery.infrastructure.DeliveryServiceController;
import it.unibo.sap.delivery.infrastructure.FleetMonitoringController;
import it.unibo.sap.delivery.infrastructure.GeocodingService;
import it.unibo.sap.delivery.infrastructure.InMemoryTrackingSessionRegistry;
import it.unibo.sap.delivery.infrastructure.VertxTrackingSessionEventObserver;
import it.unibo.sap.delivery.infrastructure.fleet.DroneEventHandlerSink;
import it.unibo.sap.delivery.infrastructure.fleet.FleetModule;
import it.unibo.sap.delivery.infrastructure.fleet.FleetSeeder;
import it.unibo.sap.delivery.infrastructure.fleet.InMemoryDroneRepository;
import it.unibo.sap.delivery.support.InMemoryDeliveryRepository;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Boots the real delivery REST + admin controllers (delivery + Fleet wired in-process, exactly like
 * {@code DeliveryServiceMain} but without the scheduler verticle and with an in-memory delivery
 * repository) on dedicated test ports, and exposes a {@link WebClient} for black-box HTTP calls.
 */
public final class DeliveryServiceTestContext {

    static final int DELIVERY_PORT = 9092;
    static final int ADMIN_PORT = 9093;
    static final String HOST = "localhost";

    private static final double DRONE_SPEED_UNITS_PER_SECOND = 0.01;

    private static DeliveryServiceTestContext instance;

    private final Vertx vertx;
    private final WebClient webClient;
    private String deliveryDeploymentId;
    private String adminDeploymentId;

    private DeliveryServiceTestContext() {
        this.vertx = Vertx.vertx();
        this.webClient = WebClient.create(vertx);
        deployFresh();
    }

    public static synchronized DeliveryServiceTestContext get() {
        if (instance == null) {
            instance = new DeliveryServiceTestContext();
        }
        return instance;
    }

    public void reset() {
        undeploy(deliveryDeploymentId);
        undeploy(adminDeploymentId);
        deployFresh();
    }

    private void deployFresh() {
        final InMemoryDeliveryRepository deliveryRepository = new InMemoryDeliveryRepository();
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

        deliveryDeploymentId = deployAwait(new DeliveryServiceController(deliveryService, DELIVERY_PORT));
        adminDeploymentId = deployAwait(new FleetMonitoringController(deliveryService, ADMIN_PORT));
    }

    private String deployAwait(final Verticle verticle) {
        final CountDownLatch latch = new CountDownLatch(1);
        final String[] id = {null};
        final Throwable[] failure = {null};
        vertx.deployVerticle(verticle).onComplete(ar -> {
            if (ar.succeeded()) {
                id[0] = ar.result();
            } else {
                failure[0] = ar.cause();
            }
            latch.countDown();
        });
        await(latch);
        if (failure[0] != null) {
            throw new IllegalStateException("Failed to deploy " + verticle.getClass().getSimpleName(), failure[0]);
        }
        return id[0];
    }

    private void undeploy(final String deploymentId) {
        if (deploymentId == null) {
            return;
        }
        final CountDownLatch latch = new CountDownLatch(1);
        vertx.undeploy(deploymentId).onComplete(ar -> latch.countDown());
        await(latch);
    }

    private static void await(final CountDownLatch latch) {
        try {
            if (!latch.await(15, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting for the delivery-service test context");
            }
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while setting up the delivery-service test context", e);
        }
    }

    public WebClient webClient() {
        return webClient;
    }

    public int deliveryPort() {
        return DELIVERY_PORT;
    }

    public int adminPort() {
        return ADMIN_PORT;
    }

    public String host() {
        return HOST;
    }
}
