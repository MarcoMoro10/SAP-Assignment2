package system.steps;

import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Base class for the end-to-end tests: brings the whole dockerized system up and exposes helpers to
 * poll health, scrape Prometheus metrics and manipulate the Docker network.
 */
public abstract class Setup {

    public static final String HOST = "localhost";
    public static final int GATEWAY_PORT = 8080;
    public static final int GATEWAY_METRICS_PORT = 9401;
    public static final int DELIVERY_METRICS_PORT = 9400;
    public static final String NETWORK_FILTER = "shipping_network";

    public static final String ADMIN_USERNAME = "admin-1";
    public static final String ADMIN_PASSWORD = "Admin#123";

    private static final String HEALTH_URL = "http://" + HOST + ":" + GATEWAY_PORT + "/api/v1/health/live";
    private static final String LOGIN_URL = "http://" + HOST + ":" + GATEWAY_PORT + "/api/v1/login";
    private static final Duration STARTUP_TIMEOUT = Duration.ofMinutes(5);
    private static final long POLL_INTERVAL_MS = 2_000;

    private static final Duration BREAKER_READY_TIMEOUT = Duration.ofSeconds(60);
    private static final long BREAKER_READY_INTERVAL_MS = 2_000;

    private static final HttpClient HTTP = HttpClient.newHttpClient();
    private static volatile boolean initialized = false;
    private static volatile boolean startedByUs = false;

    public static synchronized void ensureSystemUp() {
        if (initialized) {
            return;
        }
        if (isGatewayHealthy()) {
            startedByUs = false;
            initialized = true;
            System.out.println("[e2e] system already up — reusing it (no teardown).");
            return;
        }
        System.out.println("[e2e] system not up — running docker compose build + up --detach ...");
        runDocker(List.of("docker", "compose", "build"));
        runDocker(List.of("docker", "compose", "up", "--detach"));
        startedByUs = true;
        Runtime.getRuntime().addShutdownHook(new Thread(Setup::tearDownIfStarted));
        pollHealthUntilUp();
        initialized = true;
    }

    private static void tearDownIfStarted() {
        if (startedByUs) {
            System.out.println("[e2e] tearing down the system we started (docker compose down) ...");
            try {
                runDocker(List.of("docker", "compose", "down"));
            } catch (final RuntimeException ignored) {
                // best-effort on JVM shutdown
            }
        }
    }

    private static void pollHealthUntilUp() {
        final long deadline = System.currentTimeMillis() + STARTUP_TIMEOUT.toMillis();
        while (System.currentTimeMillis() < deadline) {
            if (isGatewayHealthy()) {
                System.out.println("[e2e] gateway is healthy.");
                return;
            }
            sleep(POLL_INTERVAL_MS);
        }
        throw new IllegalStateException("System did not become healthy within " + STARTUP_TIMEOUT);
    }

    public static boolean isGatewayHealthy() {
        try {
            final HttpResponse<String> resp = HTTP.send(
                    HttpRequest.newBuilder(URI.create(HEALTH_URL)).timeout(Duration.ofSeconds(5)).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            return resp.statusCode() == 200;
        } catch (final Exception e) {
            return false;
        }
    }

    public static boolean awaitAccountBreakerClosed() {
        final long deadline = System.currentTimeMillis() + BREAKER_READY_TIMEOUT.toMillis();
        while (System.currentTimeMillis() < deadline) {
            if (accountBreakerClosedNow()) {
                return true;
            }
            sleep(BREAKER_READY_INTERVAL_MS);
        }
        return accountBreakerClosedNow();
    }

    private static boolean accountBreakerClosedNow() {
        try {
            if (gatewayMetric("account_circuit_open") != 0.0) {
                return false;
            }
        } catch (final RuntimeException metricNotReadyYet) {
            return false;
        }
        return loginStatus(ADMIN_USERNAME, ADMIN_PASSWORD) == 200;
    }

    public static int loginStatus(final String username, final String password) {
        final String body = "{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}";
        try {
            return HTTP.send(HttpRequest.newBuilder(URI.create(LOGIN_URL))
                            .timeout(Duration.ofSeconds(10))
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(body))
                            .build(), HttpResponse.BodyHandlers.discarding()).statusCode();
        } catch (final Exception failFastOrTimeout) {
            return -1;
        }
    }

    public static double gatewayMetric(final String name) {
        return metric(GATEWAY_METRICS_PORT, name);
    }

    public static double deliveryMetric(final String name) {
        return metric(DELIVERY_METRICS_PORT, name);
    }

    private static double metric(final int port, final String name) {
        try {
            final HttpResponse<String> resp = HTTP.send(
                    HttpRequest.newBuilder(URI.create("http://" + HOST + ":" + port + "/metrics"))
                            .timeout(Duration.ofSeconds(5)).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            final Matcher m = Pattern.compile(
                            "(?m)^" + Pattern.quote(name) + "(?:_total)?(?:\\{[^}]*\\})?\\s+([-0-9.eE+]+)$")
                    .matcher(resp.body());
            if (!m.find()) {
                throw new AssertionError("metric not found on :" + port + " -> " + name + "\n" + resp.body());
            }
            return Double.parseDouble(m.group(1));
        } catch (final Exception e) {
            throw new RuntimeException("Failed to read metric " + name + " from :" + port, e);
        }
    }

    public static String dockerNetworkName() {
        final String out = runDocker(List.of(
                "docker", "network", "ls", "--filter", "name=" + NETWORK_FILTER, "--format", "{{.Name}}"));
        final String name = out.strip().lines().findFirst().orElse("").strip();
        if (name.isEmpty()) {
            throw new IllegalStateException("Could not find a docker network matching name=" + NETWORK_FILTER);
        }
        return name;
    }

    public static void networkDisconnect(final String network, final String container) {
        runDocker(List.of("docker", "network", "disconnect", network, container));
    }

    public static void networkConnect(final String network, final String container) {
        runDocker(List.of("docker", "network", "connect", network, container));
    }

    private static String runDocker(final List<String> command) {
        try {
            final Process process = new ProcessBuilder(command)
                    .directory(repoRoot())
                    .redirectErrorStream(true)
                    .start();
            final String output = new String(process.getInputStream().readAllBytes());
            final boolean finished = process.waitFor(5, java.util.concurrent.TimeUnit.MINUTES);
            if (!finished) {
                process.destroyForcibly();
                throw new IllegalStateException("Command timed out: " + command);
            }
            if (process.exitValue() != 0) {
                throw new IllegalStateException("Command failed (" + process.exitValue() + "): " + command
                        + "\n" + output);
            }
            return output;
        } catch (final java.io.IOException e) {
            throw new RuntimeException("Failed to run: " + command, e);
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while running: " + command, e);
        }
    }

    private static File repoRoot() {
        return new File(System.getProperty("user.dir")).getParentFile();
    }

    private static void sleep(final long millis) {
        try {
            Thread.sleep(millis);
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while waiting for the system", e);
        }
    }
}
