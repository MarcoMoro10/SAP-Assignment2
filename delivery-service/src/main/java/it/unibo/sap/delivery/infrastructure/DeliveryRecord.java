package it.unibo.sap.delivery.infrastructure;

public class DeliveryRecord {

    public String deliveryId;
    public String senderId;
    public double weightKg;

    public String startAddress;
    public double startLat;
    public double startLon;

    public String destinationAddress;
    public double destinationLat;
    public double destinationLon;

    public boolean immediate;
    public String scheduledAt;
    public long deadlineMinutes;

    public String status;
    public String assignedDroneId;
    public long etrSeconds;

    public DeliveryRecord() {
    }
}
