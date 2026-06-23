package it.unibo.sap.acceptance.support;

import io.vertx.core.Vertx;
import io.vertx.ext.web.client.WebClient;
import it.unibo.sap.account.application.AccountServiceImpl;
import it.unibo.sap.account.infrastructure.AccountServiceController;
import it.unibo.sap.account.infrastructure.AdminSeeder;
import it.unibo.sap.account.infrastructure.FileBasedAccountRepository;
import it.unibo.sap.delivery.application.DeliveryRepository;
import it.unibo.sap.delivery.application.DeliveryServiceImpl;
import it.unibo.sap.delivery.application.DroneEventHandler;
import it.unibo.sap.delivery.application.TrackingSessionRegistry;
import it.unibo.sap.delivery.infrastructure.DeliveryServiceController;
import it.unibo.sap.delivery.infrastructure.FileBasedDeliveryRepository;
import it.unibo.sap.delivery.infrastructure.FleetMonitoringController;
import it.unibo.sap.delivery.infrastructure.GeocodingService;
import it.unibo.sap.delivery.infrastructure.InMemoryTrackingSessionRegistry;
import it.unibo.sap.delivery.infrastructure.VertxSchedulerVerticle;
import it.unibo.sap.delivery.infrastructure.VertxTrackingSessionEventObserver;
import it.unibo.sap.delivery.infrastructure.fleet.DroneEventHandlerSink;
import it.unibo.sap.delivery.infrastructure.fleet.FleetModule;
import it.unibo.sap.delivery.infrastructure.fleet.FleetSeeder;
import it.unibo.sap.delivery.infrastructure.fleet.InMemoryDroneRepository;
import it.unibo.sap.session.application.SessionService;
import it.unibo.sap.session.application.SessionServiceImpl;
import it.unibo.sap.session.infrastructure.AccountServiceProxy;
import it.unibo.sap.session.infrastructure.DeliveryServiceProxy;
import it.unibo.sap.session.infrastructure.InMemorySessionRepository;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 *
 * <p>It boots the three services in-process on dedicated test ports and wires the
 * session-service with the REAL REST proxies, exactly like production. The few extra
 * getters and {@link #resetFleet()} below are TEST-ONLY seams: they expose the internal
 * delivery-side collaborators (drone repository, fleet module, delivery repository,
 * tracking registry and the in-process telemetry handler) so that acceptance steps can
 * configure and observe the Fleet bounded context directly, in-process -- which is the
 * only legitimate surface the fleet has (it exposes no network endpoint of its own).
 */
public final class TestServices {

    private static final String HOST = "localhost";
    private static final int ACCOUNT_PORT = 9080;
    private static final int DELIVERY_PORT = 9082;
    private static final int FLEET_PORT = 9083;
    private static final double DRONE_SPEED = 0.01;

    private static TestServices instance;

    private final Vertx vertx;
    private final WebClient webClient;
    private final SessionService sessionService;

    private FileBasedAccountRepository accountRepository;
    private InMemoryDroneRepository droneRepository;
    private FleetModule fleetModule;
    private DeliveryRepository deliveryRepository;
    private TrackingSessionRegistry trackingRegistry;
    private DroneEventHandler droneEventHandler;

    private TestServices() {
        this.vertx = Vertx.vertx();
        this.webClient = WebClient.create(vertx);

        startAccountService();
        startDeliveryService();

        final var accountProxy = new AccountServiceProxy(webClient, HOST, ACCOUNT_PORT);
        final var deliveryProxy = new DeliveryServiceProxy(webClient, HOST, DELIVERY_PORT, FLEET_PORT);
        this.sessionService = new SessionServiceImpl(
                accountProxy, deliveryProxy, new InMemorySessionRepository());
    }

    public static synchronized TestServices get() {
        if (instance == null) {
            instance = new TestServices();
        }
        return instance;
    }

    public SessionService sessionService() {
        return sessionService;
    }

    public WebClient webClient() {
        return webClient;
    }

    public String host() {
        return HOST;
    }

    public int accountPort() {
        return ACCOUNT_PORT;
    }

    public InMemoryDroneRepository droneRepository() {
        return droneRepository;
    }

    public FleetModule fleetModule() {
        return fleetModule;
    }

    public DeliveryRepository deliveryRepository() {
        return deliveryRepository;
    }

    public TrackingSessionRegistry trackingRegistry() {
        return trackingRegistry;
    }

    public DroneEventHandler droneEventHandler() {
        return droneEventHandler;
    }

    /**
     * Restores the seeded fleet (DRN-1, DRN-2, DRN-3, all AVAILABLE) before each scenario,
     * so drone state does not leak across scenarios. {@code FleetSeeder.seed} re-saves the
     * three drones by id, overwriting any status/assignment they accumulated.
     */
    public void resetFleet() {
        FleetSeeder.seed(droneRepository);
    }

    /** Clears all deliveries before each scenario so scheduling/tracking counts are deterministic. */
    public void resetDeliveries() {
        for (final var delivery : deliveryRepository.findAll()) {
            deliveryRepository.deleteById(delivery.getId());
        }
    }

    private void startAccountService() {
        this.accountRepository = new FileBasedAccountRepository(tempFile("accounts-test"));
        AdminSeeder.seed(accountRepository); // seed admin-1 / Admin#123 for admin scenarios
        final var service = new AccountServiceImpl(accountRepository);
        deployAndWait(new AccountServiceController(service, ACCOUNT_PORT));
    }

    /**
     * Removes every non-admin account before each scenario so registration/login scenarios
     * are order-independent (e.g. "Successful registration" must always create "user-1"
     * fresh, never colliding with a leftover from a previous scenario).
     */
    public void resetAccounts() {
        for (final var account : accountRepository.findAll()) {
            if (account.getRole() != it.unibo.sap.account.domain.Role.ADMIN) {
                accountRepository.deleteById(account.getId());
            }
        }
        AdminSeeder.seed(accountRepository);
    }

    private void startDeliveryService() {
        this.deliveryRepository = new FileBasedDeliveryRepository(tempFile("deliveries-test"));
        this.trackingRegistry = new InMemoryTrackingSessionRegistry();
        final var geocoding = new GeocodingService();
        final var trackingObserver = new VertxTrackingSessionEventObserver(vertx.eventBus());

        this.droneRepository = new InMemoryDroneRepository();
        this.fleetModule = new FleetModule(droneRepository, DRONE_SPEED);

        final var deliveryService = new DeliveryServiceImpl(
                deliveryRepository, fleetModule, geocoding, trackingRegistry);

        this.droneEventHandler = new DroneEventHandler(
                deliveryRepository, trackingRegistry, trackingObserver, fleetModule, DRONE_SPEED);
        fleetModule.setTelemetrySink(new DroneEventHandlerSink(droneEventHandler));

        FleetSeeder.seed(droneRepository);

        deployAndWait(new DeliveryServiceController(deliveryService, DELIVERY_PORT));
        deployAndWait(new FleetMonitoringController(deliveryService, FLEET_PORT));
        deployAndWait(new VertxSchedulerVerticle(deliveryService));
    }

    private void deployAndWait(final io.vertx.core.Verticle verticle) {
        final CountDownLatch latch = new CountDownLatch(1);
        vertx.deployVerticle(verticle, ar -> latch.countDown());
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Verticle deploy timed out: " + verticle);
            }
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private static String tempFile(final String prefix) {
        try {
            final Path dir = Files.createTempDirectory(prefix);
            return dir.resolve(prefix + "-" + UUID.randomUUID() + ".json").toString();
        } catch (final Exception e) {
            throw new IllegalStateException("Cannot create temp data file", e);
        }
    }
}
