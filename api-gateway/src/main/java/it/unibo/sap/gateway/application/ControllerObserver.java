package it.unibo.sap.gateway.application;

import it.unibo.sap.common.hexagonal.OutputPort;
public interface ControllerObserver extends OutputPort {

    void notifyNewRESTRequest();

    void recordResponseTime(double seconds);

    void setAccountCircuitOpen(boolean open);

    ControllerObserver NO_OP = new ControllerObserver() {
        @Override public void notifyNewRESTRequest() { }
        @Override public void recordResponseTime(final double seconds) { }
        @Override public void setAccountCircuitOpen(final boolean open) { }
    };
}
