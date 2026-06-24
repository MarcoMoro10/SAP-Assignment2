package it.unibo.sap.delivery.infrastructure;

public class StoredDeliveryEvent {

    public String eventType;
    public String aggregateId;
    public String occurredOn;
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
    public String droneId;
    public String reason;
    public String slot;

    public StoredDeliveryEvent() {
    }
}
