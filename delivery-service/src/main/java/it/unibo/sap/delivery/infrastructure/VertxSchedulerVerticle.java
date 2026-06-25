package it.unibo.sap.delivery.infrastructure;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import it.unibo.sap.common.hexagonal.InputAdapter;
import it.unibo.sap.delivery.application.DeliveryService;

import java.time.LocalDateTime;

public class VertxSchedulerVerticle extends AbstractVerticle implements InputAdapter {

    private static final long DEFAULT_TICK_MILLIS = 1000;

    private final DeliveryService deliveryService;
    private final long tickMillis;
    private long timerId;

    public VertxSchedulerVerticle(final DeliveryService deliveryService) {
        this(deliveryService, DEFAULT_TICK_MILLIS);
    }

    public VertxSchedulerVerticle(final DeliveryService deliveryService, final long tickMillis) {
        this.deliveryService = deliveryService;
        this.tickMillis = tickMillis;
    }

    @Override
    public void start(final Promise<Void> startPromise) {
        timerId = vertx.setPeriodic(tickMillis, id ->
                vertx.executeBlocking(() -> {
                    deliveryService.assignDueScheduledDeliveries(LocalDateTime.now());
                    return null;
                }, false));
        System.out.println("scheduler verticle started (tick " + tickMillis + "ms)");
        startPromise.complete();
    }

    @Override
    public void stop() {
        vertx.cancelTimer(timerId);
    }
}
