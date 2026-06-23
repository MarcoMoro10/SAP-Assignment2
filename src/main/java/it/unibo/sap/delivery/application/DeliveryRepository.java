package it.unibo.sap.delivery.application;

import it.unibo.sap.common.ddd.Repository;
import it.unibo.sap.common.hexagonal.OutputPort;
import it.unibo.sap.delivery.domain.deliveries.Delivery;
import it.unibo.sap.delivery.domain.deliveries.DeliveryId;

public interface DeliveryRepository extends Repository<DeliveryId, Delivery>, OutputPort {

}
