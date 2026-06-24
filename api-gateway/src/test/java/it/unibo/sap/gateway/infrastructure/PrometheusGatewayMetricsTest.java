package it.unibo.sap.gateway.infrastructure;

import io.prometheus.metrics.model.registry.PrometheusRegistry;
import io.vertx.core.Vertx;
import io.vertx.ext.web.client.WebClient;
import it.unibo.sap.gateway.application.SessionService;
import it.unibo.sap.gateway.application.SessionServiceImpl;
import it.unibo.sap.gateway.support.FakeAccountService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the gateway's infrastructure-level metrics: a REST request through the controller increments
 * {@code rest_requests} on the Prometheus {@code /metrics} endpoint. The controller is wired
 * to a {@link PrometheusControllerObserver} with its own registry (test isolation) and a dedicated
 * metrics port. The downstream proxies point at a dead port, so health resolves fast (DOWN → still 200).
 */
class PrometheusGatewayMetricsTest {

    private static final String HOST = "localhost";
    private static final int GW_PORT = 9413;
    private static final int GW_METRICS_PORT = 9414;
    private static final int DEAD_PORT = 65000;

    private static Vertx vertx;
    private static WebClient webClient;
    private static PrometheusControllerObserver observer;

    @BeforeAll
    static void setUp() {
        vertx = Vertx.vertx();
        observer = new PrometheusControllerObserver(new PrometheusRegistry(), GW_METRICS_PORT);
        final AccountServiceProxy accountProxy =
                new AccountServiceProxy(WebClient.create(vertx), HOST, DEAD_PORT);
        final DeliveryServiceProxy deliveryProxy =
                new DeliveryServiceProxy(WebClient.create(vertx), HOST, DEAD_PORT, DEAD_PORT);
        final SessionService service = new SessionServiceImpl(
                new FakeAccountService(), deliveryProxy, new InMemorySessionRepository());
        final var controller = new APIGatewayController(
                service, accountProxy, deliveryProxy, HOST, GW_PORT, observer);

        final CountDownLatch latch = new CountDownLatch(1);
        vertx.deployVerticle(controller).onComplete(ar -> latch.countDown());
        await(latch);
        webClient = WebClient.create(vertx);
    }

    @AfterAll
    static void tearDown() {
        if (observer != null) {
            observer.stop();
        }
        if (vertx != null) {
            final CountDownLatch latch = new CountDownLatch(1);
            vertx.close().onComplete(ar -> latch.countDown());
            await(latch);
        }
    }

    @Test
    void aRestRequestIncrementsTheRequestCounter() throws Exception {
        final double before = metric("rest_requests");

        final CompletableFuture<Integer> done = new CompletableFuture<>();
        webClient.get(GW_PORT, HOST, "/api/v1/health")
                .send(ar -> done.complete(ar.succeeded() ? ar.result().statusCode() : -1));
        done.get(15, TimeUnit.SECONDS);

        final double after = metric("rest_requests");
        assertTrue(after >= before + 1,
                "expected the REST request counter to increase, before=" + before + " after=" + after);
    }

    private static double metric(final String name) throws Exception {
        final HttpResponse<String> resp = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create("http://" + HOST + ":" + GW_METRICS_PORT + "/metrics")).build(),
                HttpResponse.BodyHandlers.ofString());
        final Matcher m = Pattern.compile(
                        "(?m)^" + Pattern.quote(name) + "(?:_total)?(?:\\{[^}]*\\})?\\s+([-0-9.eE+]+)$")
                .matcher(resp.body());
        if (!m.find()) {
            throw new AssertionError("metric not found: " + name + "\n" + resp.body());
        }
        return Double.parseDouble(m.group(1));
    }

    private static void await(final CountDownLatch latch) {
        try {
            if (!latch.await(15, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out setting up the gateway metrics test");
            }
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while setting up the test", e);
        }
    }
}
