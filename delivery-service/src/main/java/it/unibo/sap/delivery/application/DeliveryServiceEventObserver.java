package it.unibo.sap.delivery.application;

import it.unibo.sap.common.hexagonal.OutputPort;
public interface DeliveryServiceEventObserver extends OutputPort {

    void onDeliveryCreated();

    void onDeliveryInProgress();

    void onDeliveryCompleted();

    DeliveryServiceEventObserver NO_OP = new DeliveryServiceEventObserver() {
        @Override public void onDeliveryCreated() { }
        @Override public void onDeliveryInProgress() { }
        @Override public void onDeliveryCompleted() { }
    };
}
