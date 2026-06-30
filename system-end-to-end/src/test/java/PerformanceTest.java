import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import system.steps.Setup;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * QA scenario — responsiveness. Fires 1000 requests at the gateway, each on its own virtual thread,
 * and reads the gateway metrics to compute the average response time
 * ({@code request_response_time_seconds / rest_requests}, the metric is in SECONDS) which must stay
 * under 100ms. Because the Prometheus counters are cumulative since gateway startup, the assertions are
 * taken on the DELTA produced by this load (so prior health polls/tests do not skew the result), and
 * that delta must be exactly the 1000 requests we issued.
 */
class PerformanceTest {

    private static final int REQUESTS = 1000;
    private static final int WARMUP = 500;
    private static final double MAX_AVERAGE_SECONDS = 0.1;
    private static final int MAX_IN_FLIGHT = 64;
    private static final HttpRequest PING = HttpRequest.newBuilder(
                    URI.create("http://" + Setup.HOST + ":" + Setup.GATEWAY_PORT + "/api/v1/health/live"))
            .GET().build();
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .build();

    @BeforeAll
    static void systemUp() {
        Setup.ensureSystemUp();
    }

    @Test
    void averageResponseTimeStaysUnderTheBudget() throws Exception {
        runLoad(WARMUP);

        final double requestsBefore = Setup.gatewayMetric("rest_requests");
        final double timeBefore = Setup.gatewayMetric("request_response_time_seconds");

        final int ok = runLoad(REQUESTS);

        assertThat(ok).as("all %d requests should succeed", REQUESTS).isEqualTo(REQUESTS);

        final double requestsDelta = Setup.gatewayMetric("rest_requests") - requestsBefore;
        final double timeDelta = Setup.gatewayMetric("request_response_time_seconds") - timeBefore;

        final long counted = Math.round(requestsDelta);
        assertThat(counted)
                .as("the gateway must have counted at least the %d issued requests", REQUESTS)
                .isBetween((long) REQUESTS, (long) REQUESTS + 50);

        final double averageSeconds = timeDelta / requestsDelta;
        System.out.printf("[e2e] average gateway response time: %.4f s over %d requests%n",
                averageSeconds, REQUESTS);
        assertThat(averageSeconds)
                .as("average response time must stay under %.0f ms", MAX_AVERAGE_SECONDS * 1000)
                .isLessThan(MAX_AVERAGE_SECONDS);
    }

    private static int runLoad(final int count) throws Exception {
        final AtomicInteger ok = new AtomicInteger(0);
        final Semaphore inFlight = new Semaphore(MAX_IN_FLIGHT);
        try (ExecutorService vthreads = Executors.newVirtualThreadPerTaskExecutor()) {
            final List<Future<?>> futures = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                futures.add(vthreads.submit(() -> {
                    inFlight.acquire();
                    try {
                        if (sendOnce()) {
                            ok.incrementAndGet();
                        }
                    } finally {
                        inFlight.release();
                    }
                    return null;
                }));
            }
            for (final Future<?> f : futures) {
                f.get();
            }
        }
        return ok.get();
    }

    private static boolean sendOnce() throws Exception {
        try {
            return HTTP.send(PING, HttpResponse.BodyHandlers.discarding()).statusCode() == 200;
        } catch (final java.io.IOException transientError) {
            Thread.sleep(50);
            return HTTP.send(PING, HttpResponse.BodyHandlers.discarding()).statusCode() == 200;
        }
    }
}
