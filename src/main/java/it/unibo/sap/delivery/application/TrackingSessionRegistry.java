package it.unibo.sap.delivery.application;

import it.unibo.sap.common.hexagonal.OutputPort;
import it.unibo.sap.delivery.domain.deliveries.DeliveryId;
import it.unibo.sap.delivery.domain.deliveries.TrackingSession;

import java.util.List;

public interface TrackingSessionRegistry extends OutputPort {

    void register(TrackingSession session);

    List<TrackingSession> findByDelivery(DeliveryId deliveryId);

}
