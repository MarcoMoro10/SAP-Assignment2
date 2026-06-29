package it.unibo.sap.delivery.infrastructure;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.WorkerExecutor;
import it.unibo.sap.common.hexagonal.InputAdapter;
import it.unibo.sap.delivery.application.DeliveryService;

import java.time.LocalDateTime;

public class VertxSchedulerVerticle extends AbstractVerticle implements InputAdapter {

    private static final long DEFAULT_TICK_MILLIS = 1000;

    private final DeliveryService deliveryService;
    private final long tickMillis;
    private long timerId;
    private WorkerExecutor commandExecutor;

    public VertxSchedulerVerticle(final DeliveryService deliveryService) {
        this(deliveryService, DEFAULT_TICK_MILLIS);
    }

    public VertxSchedulerVerticle(final DeliveryService deliveryService, final long tickMillis) {
        this.deliveryService = deliveryService;
        this.tickMillis = tickMillis;
    }

    @Override
    public void start(final Promise<Void> startPromise) {
        commandExecutor = vertx.createSharedWorkerExecutor(
                DeliveryServiceController.DOMAIN_COMMAND_EXECUTOR, 1);
        timerId = vertx.setPeriodic(tickMillis, id ->
                commandExecutor.executeBlocking(() -> {
                    deliveryService.assignDueScheduledDeliveries(LocalDateTime.now());
                    return null;
                }, true));
        System.out.println("scheduler verticle started (tick " + tickMillis + "ms)");
        startPromise.complete();
    }

    @Override
    public void stop() {
        vertx.cancelTimer(timerId);
        if (commandExecutor != null) {
            commandExecutor.close();
        }
    }
}
