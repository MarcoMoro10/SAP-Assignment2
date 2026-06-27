package it.unibo.sap.delivery.infrastructure;

import io.prometheus.metrics.core.metrics.Counter;
import io.prometheus.metrics.core.metrics.Gauge;
import io.prometheus.metrics.exporter.httpserver.HTTPServer;
import io.prometheus.metrics.instrumentation.jvm.JvmMetrics;
import io.prometheus.metrics.model.registry.PrometheusRegistry;
import it.unibo.sap.common.hexagonal.OutputAdapter;
import it.unibo.sap.delivery.application.DeliveryServiceEventObserver;

import java.io.IOException;
import java.io.UncheckedIOException;

public class PrometheusDeliveryServiceObserver implements DeliveryServiceEventObserver, OutputAdapter {

    private final Counter nTotalDeliveriesCreated;
    private final Gauge nDeliveriesOnDelivery;
    private final Counter nDeliveriesDelivered;
    private final HTTPServer server;

    public PrometheusDeliveryServiceObserver(final int metricsPort) {
        this(PrometheusRegistry.defaultRegistry, metricsPort);
    }

    public PrometheusDeliveryServiceObserver(final PrometheusRegistry registry, final int metricsPort) {
        JvmMetrics.builder().register(registry);
        this.nTotalDeliveriesCreated = Counter.builder()
                .name("created_deliveries")
                .help("Total number of deliveries created")
                .register(registry);
        this.nDeliveriesOnDelivery = Gauge.builder()
                .name("deliveries_in_progress")
                .help("Number of deliveries currently in progress")
                .register(registry);
        this.nDeliveriesDelivered = Counter.builder()
                .name("deliveries_delivered")
                .help("Total number of deliveries delivered")
                .register(registry);
        try {
            this.server = HTTPServer.builder().registry(registry).port(metricsPort).buildAndStart();
        } catch (final IOException e) {
            throw new UncheckedIOException("Failed to start delivery metrics server on port " + metricsPort, e);
        }
        System.out.println("delivery-service metrics ready - port: " + metricsPort);
    }

    @Override
    public void onDeliveryCreated() {
        nTotalDeliveriesCreated.inc();
    }

    @Override
    public void onDeliveryInProgress() {
        nDeliveriesOnDelivery.inc();
    }

    @Override
    public void onDeliveryCompleted() {
        nDeliveriesOnDelivery.dec();
        nDeliveriesDelivered.inc();
    }

    public void stop() {
        if (server != null) {
            server.stop();
        }
    }
}
