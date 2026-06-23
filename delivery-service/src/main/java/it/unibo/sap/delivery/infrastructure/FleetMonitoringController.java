package it.unibo.sap.delivery.infrastructure;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import it.unibo.sap.common.hexagonal.InputAdapter;
import it.unibo.sap.delivery.application.DeliveryService;
import it.unibo.sap.delivery.application.fleet.FleetViews;
import it.unibo.sap.delivery.domain.deliveries.DeliverySchedulingView;

import java.util.List;

public class FleetMonitoringController extends AbstractVerticle implements InputAdapter {

    public static final int DEFAULT_PORT = 8083;

    private final DeliveryService deliveryService;
    private final int port;

    public FleetMonitoringController(final DeliveryService deliveryService, final int port) {
        this.deliveryService = deliveryService;
        this.port = port;
    }

    @Override
    public void start(final Promise<Void> startPromise) {
        final Router router = Router.router(vertx);
        router.get("/api/v1/admin/fleet").handler(this::handleViewFleet);
        router.get("/api/v1/admin/scheduling").handler(this::handleViewScheduling);

        vertx.createHttpServer()
                .requestHandler(router)
                .listen(port, http -> {
                    if (http.succeeded()) {
                        System.out.println("fleet-monitoring (admin) ready - port: " + port);
                        startPromise.complete();
                    } else {
                        startPromise.fail(http.cause());
                    }
                });
    }

    private void handleViewFleet(final RoutingContext ctx) {
        final List<FleetViews.FleetDroneView> view = deliveryService.viewFleet();
        final JsonArray array = new JsonArray();
        for (final FleetViews.FleetDroneView d : view) {
            array.add(new JsonObject()
                    .put("droneId", d.droneId())
                    .put("status", d.status())
                    .put("position", new JsonObject()
                            .put("latitude", d.latitude())
                            .put("longitude", d.longitude()))
                    .put("carryingPackage", d.carryingPackage()));
        }
        ctx.response().setStatusCode(200)
                .putHeader("Content-Type", "application/json")
                .end(array.encode());
    }

    private void handleViewScheduling(final RoutingContext ctx) {
        final String droneId = ctx.queryParams().get("droneId");
        final List<DeliverySchedulingView> view = deliveryService.viewScheduling(droneId);
        final JsonArray array = new JsonArray();
        for (final DeliverySchedulingView s : view) {
            array.add(new JsonObject()
                    .put("droneId", s.droneId())
                    .put("deliveryId", s.deliveryId())
                    .put("scheduledAt", s.scheduledAt() == null ? null : s.scheduledAt().toString())
                    .put("status", s.status() == null ? null : s.status().name()));
        }
        ctx.response().setStatusCode(200)
                .putHeader("Content-Type", "application/json")
                .end(array.encode());
    }
}