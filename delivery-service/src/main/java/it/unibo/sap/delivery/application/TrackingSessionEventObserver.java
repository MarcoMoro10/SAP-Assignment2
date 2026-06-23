package it.unibo.sap.delivery.application;

import it.unibo.sap.common.hexagonal.OutputPort;

public interface TrackingSessionEventObserver extends OutputPort {

    void pushTrackingUpdate(String deliveryId,
                            String status,
                            double latitude,
                            double longitude,
                            long estimatedTimeRemainingSeconds);
}
