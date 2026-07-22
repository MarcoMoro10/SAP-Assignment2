package it.unibo.sap.gateway.infrastructure;

import io.prometheus.metrics.model.registry.PrometheusRegistry;
import io.vertx.core.Vertx;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.handler.BodyHandler;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration test of {@link AccountServiceProxy} + {@link CircuitBreaker} against a stub account
 * service whose availability is toggled at runtime. The breaker is exercised through the
 * registration call (the gateway no longer authenticates: login moved to the session-service). When
 * the stub fails (&gt;50%) the circuit opens, the proxy fails fast (no more requests reach the stub)
 * and the {@code account_circuit_open} gauge goes to 1. When the stub recovers, after the open timeout
 * the breaker probes its health and the next trial call re-closes it, dropping the gauge back to 0.
 */
class AccountServiceCircuitBreakerTest {

    private static final String HOST = "localhost";
    private static final int STUB_PORT = 9416;
    private static final int METRICS_PORT = 9417;
    private static final long OPEN_TIMEOUT_MS = 500;

    private static Vertx vertx;
    private static WebClient webClient;
    private static PrometheusControllerObserver observer;
    private static final AtomicInteger registerHits = new AtomicInteger(0);
    private static volatile boolean serviceUp = false;
    private static volatile boolean serviceRejectsRegistration = false;

    private CircuitBreaker breaker;
    private AccountServiceProxy proxy;

    @BeforeAll
    static void startStub() {
        vertx = Vertx.vertx();
        final Router router = Router.router(vertx);
        router.route("/api/v1/*").handler(BodyHandler.create());

        router.post("/api/v1/accounts").handler(ctx -> {
            registerHits.incrementAndGet();
            if (serviceRejectsRegistration) {
                ctx.response().setStatusCode(409).putHeader("Content-Type", "application/json")
                        .end("{\"error\":\"Username already taken\"}");
            } else if (serviceUp) {
                ctx.response().setStatusCode(201).putHeader("Content-Type", "application/json")
                        .end("{\"accountId\":\"acc-1\",\"role\":\"SENDER\"}");
            } else {
                ctx.response().setStatusCode(503).end();
            }
        });
        router.get("/api/v1/health").handler(ctx ->
                ctx.response().setStatusCode(serviceUp ? 200 : 503).end());

        final CountDownLatch latch = new CountDownLatch(1);
        vertx.createHttpServer().requestHandler(router).listen(STUB_PORT).onComplete(ar -> latch.countDown());
        await(latch);

        observer = new PrometheusControllerObserver(new PrometheusRegistry(), METRICS_PORT);
        webClient = WebClient.create(vertx);
    }

    @AfterAll
    static void stopStub() {
        if (observer != null) {
            observer.stop();
        }
        if (vertx != null) {
            final CountDownLatch latch = new CountDownLatch(1);
            vertx.close().onComplete(ar -> latch.countDown());
            await(latch);
        }
    }

    @BeforeEach
    void wireProxy() {
        serviceRejectsRegistration = false;
        breaker = new CircuitBreaker(20, 5, 0.5, OPEN_TIMEOUT_MS, System::currentTimeMillis);
        breaker.setOnStateChange(observer::setAccountCircuitOpen);
        proxy = new AccountServiceProxy(webClient, HOST, STUB_PORT, breaker);
    }

    @Test
    void circuitOpensOnFailuresThenRecloses() throws Exception {
        serviceUp = false;

        for (int i = 0; i < 6; i++) {
            assertEquals(503, proxy.register("user", "pwd").getInteger("_statusCode"));
        }
        assertTrue(breaker.isOpen(), "the circuit must open after >50% failures");
        assertEquals(1.0, metric("account_circuit_open"), 1e-9, "gauge must report the open circuit");

        final int hitsWhenOpened = registerHits.get();
        for (int i = 0; i < 5; i++) {
            proxy.register("user", "pwd");
        }
        assertEquals(hitsWhenOpened, registerHits.get(), "open circuit must not forward calls downstream");

        serviceUp = true;
        final boolean reclosed = pollUntil(() -> {
            proxy.register("user", "pwd");
            return breaker.state() == CircuitBreaker.State.CLOSED
                    && metric("account_circuit_open") == 0.0;
        }, 5_000);

        assertTrue(reclosed, "the circuit must re-close once the service is healthy again");
        assertEquals(CircuitBreaker.State.CLOSED, breaker.state());
        assertEquals(201, proxy.register("user", "pwd").getInteger("_statusCode"),
                "a closed circuit forwards calls again");
    }

    @Test
    void rejectedRegistrationKeepsTheCircuitClosed() throws Exception {
        serviceUp = true;
        serviceRejectsRegistration = true;

        for (int i = 0; i < 6; i++) {
            assertEquals(409, proxy.register("user", "dup").getInteger("_statusCode"),
                    "a business rejection (409) must still return to the client");
        }

        assertFalse(breaker.isOpen(),
                "a healthy service returning a business 409 must not open the circuit");
        assertEquals(CircuitBreaker.State.CLOSED, breaker.state());
        assertEquals(0.0, metric("account_circuit_open"), 1e-9, "gauge must stay closed");

        serviceRejectsRegistration = false;
        assertEquals(201, proxy.register("user", "new").getInteger("_statusCode"),
                "registration must work again: the breaker never opened");
    }

    private interface Probe {
        boolean check() throws Exception;
    }

    private static boolean pollUntil(final Probe probe, final long timeoutMs) throws Exception {
        final long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            if (probe.check()) {
                return true;
            }
            Thread.sleep(100);
        }
        return probe.check();
    }

    private static double metric(final String name) {
        try {
            final HttpResponse<String> resp = HttpClient.newHttpClient().send(
                    HttpRequest.newBuilder(URI.create("http://" + HOST + ":" + METRICS_PORT + "/metrics")).build(),
                    HttpResponse.BodyHandlers.ofString());
            final Matcher m = Pattern.compile(
                            "(?m)^" + Pattern.quote(name) + "(?:\\{[^}]*\\})?\\s+([-0-9.eE+]+)$")
                    .matcher(resp.body());
            if (!m.find()) {
                throw new AssertionError("metric not found: " + name + "\n" + resp.body());
            }
            return Double.parseDouble(m.group(1));
        } catch (final Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static void await(final CountDownLatch latch) {
        try {
            if (!latch.await(15, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out setting up the account circuit-breaker test");
            }
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while setting up the test", e);
        }
    }
}
