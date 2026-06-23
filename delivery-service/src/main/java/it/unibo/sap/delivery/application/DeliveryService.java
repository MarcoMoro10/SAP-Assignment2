package it.unibo.sap.delivery.application;

import it.unibo.sap.delivery.application.fleet.FleetViews;
import it.unibo.sap.delivery.domain.deliveries.DeliverySchedulingView;
import it.unibo.sap.delivery.domain.deliveries.DeliveryTrackingView;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface DeliveryService extends it.unibo.sap.common.hexagonal.InputPort {


    CreateDeliveryResult createDelivery(CreateDeliveryCommand command);

    void cancelDelivery(String deliveryId, String senderId);

    Optional<DeliveryTrackingView> getDelivery(String deliveryId, String senderId);

    TrackingHandle startTracking(String deliveryId, String senderId);

    void assignDueScheduledDeliveries(LocalDateTime now);

    List<FleetViews.FleetDroneView> viewFleet();

    List<DeliverySchedulingView> viewScheduling(String droneIdFilter);
}