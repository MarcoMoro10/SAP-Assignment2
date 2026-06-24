package it.unibo.sap.delivery.infrastructure;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;
import it.unibo.sap.delivery.application.DeliveryService;
import it.unibo.sap.delivery.application.DeliveryServiceImpl;
import it.unibo.sap.delivery.application.TrackingSessionRegistry;
import it.unibo.sap.delivery.infrastructure.fleet.FleetModule;
import it.unibo.sap.delivery.infrastructure.fleet.InMemoryDroneRepository;
import it.unibo.sap.delivery.support.FakeGeocodingPort;
import it.unibo.sap.delivery.support.InMemoryDeliveryRepository;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Integration test of the delivery-service health endpoint: {@code GET /api/v1/health} answers 200
 * with {@code {"status":"UP"}}.
 */
class DeliveryServiceHealthTest {

    private static final String HOST = "localhost";
    private static final int PORT = 9097;

    private static Vertx vertx;
    private static WebClient webClient;

    @BeforeAll
    static void startService() throws Exception {
        vertx = Vertx.vertx();
        final TrackingSessionRegistry trackingRegistry = new InMemoryTrackingSessionRegistry();
        final FleetModule fleetModule = new FleetModule(new InMemoryDroneRepository(), 0.01);
        final DeliveryService deliveryService = new DeliveryServiceImpl(
                new InMemoryDeliveryRepository(), fleetModule, new FakeGeocodingPort(), trackingRegistry);

        final CompletableFuture<Void> deployed = new CompletableFuture<>();
        vertx.deployVerticle(new DeliveryServiceController(deliveryService, PORT))
                .onComplete(ar -> deployed.complete(null));
        deployed.get(15, TimeUnit.SECONDS);
        webClient = WebClient.create(vertx);
    }

    @AfterAll
    static void stopService() throws Exception {
        if (vertx != null) {
            final CompletableFuture<Void> closed = new CompletableFuture<>();
            vertx.close().onComplete(ar -> closed.complete(null));
            closed.get(15, TimeUnit.SECONDS);
        }
    }

    @Test
    void healthEndpointReportsUp() throws Exception {
        final CompletableFuture<JsonObject> response = new CompletableFuture<>();
        webClient.get(PORT, HOST, "/api/v1/health").send(ar -> {
            if (ar.succeeded()) {
                response.complete(new JsonObject()
                        .put("statusCode", ar.result().statusCode())
                        .put("body", ar.result().bodyAsJsonObject()));
            } else {
                response.completeExceptionally(ar.cause());
            }
        });
        final JsonObject result = response.get(15, TimeUnit.SECONDS);

        assertEquals(200, result.getInteger("statusCode"));
        assertEquals("UP", result.getJsonObject("body").getString("status"));
    }
}
