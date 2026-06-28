package it.unibo.sap.delivery.application;

import it.unibo.sap.common.hexagonal.OutputPort;

public interface EstimatedTimeView extends OutputPort {

    void update(String deliveryId, long etrSeconds);

    long secondsFor(String deliveryId);

    void clear(String deliveryId);

    EstimatedTimeView NO_OP = new EstimatedTimeView() {
        @Override public void update(final String deliveryId, final long etrSeconds) { }
        @Override public long secondsFor(final String deliveryId) { return 0; }
        @Override public void clear(final String deliveryId) { }
    };
}
