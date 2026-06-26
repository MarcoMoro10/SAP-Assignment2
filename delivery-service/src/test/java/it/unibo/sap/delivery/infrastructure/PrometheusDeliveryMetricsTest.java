package it.unibo.sap.delivery.infrastructure;

import io.prometheus.metrics.model.registry.PrometheusRegistry;
import it.unibo.sap.delivery.application.CreateDeliveryCommand;
import it.unibo.sap.delivery.application.CreateDeliveryResult;
import it.unibo.sap.delivery.application.DeliveryService;
import it.unibo.sap.delivery.application.DeliveryServiceImpl;
import it.unibo.sap.delivery.application.DroneEventHandler;
import it.unibo.sap.delivery.application.TrackingSessionRegistry;
import it.unibo.sap.delivery.support.FakeFleetPort;
import it.unibo.sap.delivery.support.FakeGeocodingPort;
import it.unibo.sap.delivery.support.InMemoryDeliveryRepository;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Verifies the application-level delivery metrics: after creating (immediate → IN_PROGRESS) and then
 * completing a delivery, the Prometheus counters/gauge exposed on {@code /metrics} reflect the expected
 * values. Drives the real service + drone handler wired to a {@link PrometheusDeliveryServiceObserver}
 * with its own registry (test isolation) and a {@link FakeFleetPort} (no background simulator).
 */
class PrometheusDeliveryMetricsTest {

    private static final int METRICS_PORT = 9412;

    private static PrometheusDeliveryServiceObserver observer;
    private static DeliveryService service;
    private static DroneEventHandler handler;

    @BeforeAll
    static void setUp() {
        observer = new PrometheusDeliveryServiceObserver(new PrometheusRegistry(), METRICS_PORT);
        final InMemoryDeliveryRepository repository = new InMemoryDeliveryRepository();
        final TrackingSessionRegistry tracking = new InMemoryTrackingSessionRegistry();
        final FakeFleetPort fleet = new FakeFleetPort();
        service = new DeliveryServiceImpl(repository, fleet, new FakeGeocodingPort(), tracking, observer);
        handler = new DroneEventHandler(repository, tracking, (d, s, la, lo, e) -> { }, fleet, 0.01, observer);
    }

    @AfterAll
    static void tearDown() {
        if (observer != null) {
            observer.stop();
        }
    }

    @Test
    void countersAndGaugeReflectTheDeliveryLifecycle() throws Exception {
        final CreateDeliveryResult created = service.createDelivery(new CreateDeliveryCommand(
                "user-1", 2.0, "via Emilia", 9, "via Veneto", 5, true, null, 60));

        assertEquals(1.0, metric("created_deliveries"));
        assertEquals(1.0, metric("deliveries_in_progress"));
        assertEquals(0.0, metric("deliveries_delivered"));

        handler.onDroneArrived(created.deliveryId(), 44.50, 11.34);

        assertEquals(0.0, metric("deliveries_in_progress"));
        assertEquals(1.0, metric("deliveries_delivered"));
        assertEquals(1.0, metric("created_deliveries"));
    }

    private static double metric(final String name) throws Exception {
        final HttpResponse<String> resp = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + METRICS_PORT + "/metrics")).build(),
                HttpResponse.BodyHandlers.ofString());
        final Matcher m = Pattern.compile(
                        "(?m)^" + Pattern.quote(name) + "(?:_total)?(?:\\{[^}]*\\})?\\s+([-0-9.eE+]+)$")
                .matcher(resp.body());
        if (!m.find()) {
            throw new AssertionError("metric not found: " + name + "\n" + resp.body());
        }
        return Double.parseDouble(m.group(1));
    }
}
