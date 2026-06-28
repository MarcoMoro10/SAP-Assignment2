package it.unibo.sap.delivery.infrastructure;

import it.unibo.sap.common.hexagonal.OutputAdapter;
import it.unibo.sap.delivery.application.EstimatedTimeView;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryEstimatedTimeView implements EstimatedTimeView, OutputAdapter {

    private final Map<String, Long> secondsByDelivery = new ConcurrentHashMap<>();

    @Override
    public void update(final String deliveryId, final long etrSeconds) {
        secondsByDelivery.put(deliveryId, etrSeconds);
    }

    @Override
    public long secondsFor(final String deliveryId) {
        return secondsByDelivery.getOrDefault(deliveryId, 0L);
    }

    @Override
    public void clear(final String deliveryId) {
        secondsByDelivery.remove(deliveryId);
    }
}
