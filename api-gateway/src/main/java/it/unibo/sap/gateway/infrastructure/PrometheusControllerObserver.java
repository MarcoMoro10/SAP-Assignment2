package it.unibo.sap.gateway.infrastructure;

import io.prometheus.metrics.core.metrics.Counter;
import io.prometheus.metrics.core.metrics.Gauge;
import io.prometheus.metrics.exporter.httpserver.HTTPServer;
import io.prometheus.metrics.instrumentation.jvm.JvmMetrics;
import io.prometheus.metrics.model.registry.PrometheusRegistry;
import it.unibo.sap.common.hexagonal.OutputAdapter;
import it.unibo.sap.gateway.application.ControllerObserver;

import java.io.IOException;
import java.io.UncheckedIOException;

public class PrometheusControllerObserver implements ControllerObserver, OutputAdapter {

    private final Counter nTotalNumberOfRESTRequests;
    private final Counter totalRequestResponseTime;
    private final Gauge isAccountCircuitOpen;
    private final HTTPServer server;

    public PrometheusControllerObserver(final int metricsPort) {
        this(PrometheusRegistry.defaultRegistry, metricsPort);
    }

    public PrometheusControllerObserver(final PrometheusRegistry registry, final int metricsPort) {
        JvmMetrics.builder().register(registry);
        this.nTotalNumberOfRESTRequests = Counter.builder()
                .name("rest_requests")
                .help("Total number of REST requests received by the gateway")
                .register(registry);
        this.totalRequestResponseTime = Counter.builder()
                .name("request_response_time_seconds")
                .help("Accumulated REST request/response time in seconds")
                .register(registry);
        this.isAccountCircuitOpen = Gauge.builder()
                .name("account_circuit_open")
                .help("Whether the account circuit breaker is open (1) or closed (0)")
                .register(registry);
        this.isAccountCircuitOpen.set(0);
        try {
            this.server = HTTPServer.builder().registry(registry).port(metricsPort).buildAndStart();
        } catch (final IOException e) {
            throw new UncheckedIOException("Failed to start gateway metrics server on port " + metricsPort, e);
        }
        System.out.println("api-gateway metrics ready - port: " + metricsPort);
    }

    @Override
    public void notifyNewRESTRequest() {
        nTotalNumberOfRESTRequests.inc();
    }

    @Override
    public void recordResponseTime(final double seconds) {
        totalRequestResponseTime.inc(seconds);
    }

    @Override
    public void setAccountCircuitOpen(final boolean open) {
        isAccountCircuitOpen.set(open ? 1 : 0);
    }

    public void stop() {
        if (server != null) {
            server.stop();
        }
    }
}
