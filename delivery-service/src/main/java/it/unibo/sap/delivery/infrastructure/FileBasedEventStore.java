package it.unibo.sap.delivery.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.unibo.sap.common.ddd.DomainEvent;
import it.unibo.sap.common.hexagonal.OutputAdapter;
import it.unibo.sap.delivery.application.EventStore;
import it.unibo.sap.delivery.domain.deliveries.Coordinates;
import it.unibo.sap.delivery.domain.deliveries.Deadline;
import it.unibo.sap.delivery.domain.deliveries.DeliveryId;
import it.unibo.sap.delivery.domain.deliveries.DeliveryRequest;
import it.unibo.sap.delivery.domain.deliveries.Location;
import it.unibo.sap.delivery.domain.deliveries.Package;
import it.unibo.sap.delivery.domain.deliveries.RequestedDateTime;
import it.unibo.sap.delivery.domain.deliveries.SenderId;
import it.unibo.sap.delivery.domain.deliveries.events.DeliveryBegun;
import it.unibo.sap.delivery.domain.deliveries.events.DeliveryCancelled;
import it.unibo.sap.delivery.domain.deliveries.events.DeliveryCompleted;
import it.unibo.sap.delivery.domain.deliveries.events.DeliveryRequestCreated;
import it.unibo.sap.delivery.domain.deliveries.events.DeliveryScheduled;
import it.unibo.sap.delivery.domain.deliveries.events.DroneAssigned;
import it.unibo.sap.delivery.domain.deliveries.events.DroneReserved;
import it.unibo.sap.delivery.domain.deliveries.events.ValidationDeliveryPassed;
import it.unibo.sap.delivery.domain.deliveries.events.ValidationDeliveryRejected;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class FileBasedEventStore implements EventStore, OutputAdapter {

    public static final String DEFAULT_FILE = "data/delivery-events.json";

    private final Path file;
    private final ObjectMapper mapper = new ObjectMapper();
    private final List<StoredDeliveryEvent> log = new ArrayList<>();

    public FileBasedEventStore() {
        this(DEFAULT_FILE, true);
    }

    public FileBasedEventStore(final String filePath) {
        this(filePath, true);
    }

    public static FileBasedEventStore resetting() {
        return resetting(DEFAULT_FILE);
    }

    public static FileBasedEventStore resetting(final String filePath) {
        return new FileBasedEventStore(filePath, false);
    }

    private FileBasedEventStore(final String filePath, final boolean loadExisting) {
        this.file = Path.of(filePath);
        if (loadExisting) {
            load();
        } else {
            reset();
        }
    }

    private void reset() {
        try {
            Files.deleteIfExists(file);
        } catch (final IOException e) {
            throw new UncheckedIOException("Failed to reset delivery events at " + file, e);
        }
    }

    @Override
    public synchronized void append(final String aggregateId, final List<DomainEvent> events) {
        for (final DomainEvent event : events) {
            log.add(toStored(aggregateId, event));
        }
        flush();
    }

    @Override
    public synchronized List<DomainEvent> load(final String aggregateId) {
        return log.stream()
                .filter(s -> aggregateId.equals(s.aggregateId))
                .map(FileBasedEventStore::toDomain)
                .toList();
    }

    @Override
    public synchronized List<String> aggregateIds() {
        return log.stream().map(s -> s.aggregateId).distinct().toList();
    }

    private void load() {
        if (!Files.exists(file)) {
            return;
        }
        try {
            final byte[] bytes = Files.readAllBytes(file);
            if (bytes.length == 0) {
                return;
            }
            final StoredDeliveryEvent[] stored = mapper.readValue(bytes, StoredDeliveryEvent[].class);
            log.addAll(List.of(stored));
        } catch (final IOException e) {
            throw new UncheckedIOException("Failed to load delivery events from " + file, e);
        }
    }

    private void flush() {
        try {
            if (file.getParent() != null) {
                Files.createDirectories(file.getParent());
            }
            mapper.writerWithDefaultPrettyPrinter().writeValue(file.toFile(), log);
        } catch (final IOException e) {
            throw new UncheckedIOException("Failed to persist delivery events to " + file, e);
        }
    }

    private static StoredDeliveryEvent toStored(final String aggregateId, final DomainEvent event) {
        final StoredDeliveryEvent s = new StoredDeliveryEvent();
        s.aggregateId = aggregateId;
        s.occurredOn = event.occurredOn().toString();
        switch (event) {
            case DeliveryRequestCreated e -> {
                s.eventType = "DeliveryRequestCreated";
                final DeliveryRequest req = e.request();
                s.senderId = e.senderId().value();
                s.weightKg = req.parcel().weightKg();
                s.startAddress = req.pickupLocation().address();
                s.startLat = req.pickupLocation().coordinates().latitude();
                s.startLon = req.pickupLocation().coordinates().longitude();
                s.destinationAddress = req.destination().address();
                s.destinationLat = req.destination().coordinates().latitude();
                s.destinationLon = req.destination().coordinates().longitude();
                s.immediate = req.requestedDateTime().isImmediate();
                s.scheduledAt = req.requestedDateTime().scheduledAt() == null
                        ? null : req.requestedDateTime().scheduledAt().toString();
                s.deadlineMinutes = req.deadline() == null ? 0 : req.deadline().maxDuration().toMinutes();
            }
            case ValidationDeliveryPassed _ -> s.eventType = "ValidationDeliveryPassed";
            case ValidationDeliveryRejected e -> {
                s.eventType = "ValidationDeliveryRejected";
                s.reason = e.reason();
            }
            case DeliveryScheduled e -> {
                s.eventType = "DeliveryScheduled";
                s.slot = e.slot() == null ? null : e.slot().toString();
            }
            case DroneReserved e -> {
                s.eventType = "DroneReserved";
                s.droneId = e.droneId();
            }
            case DroneAssigned e -> {
                s.eventType = "DroneAssigned";
                s.droneId = e.droneId();
            }
            case DeliveryBegun _ -> s.eventType = "DeliveryBegun";
            case DeliveryCompleted _ -> s.eventType = "DeliveryCompleted";
            case DeliveryCancelled _ -> s.eventType = "DeliveryCancelled";
            default -> throw new IllegalArgumentException(
                    "Cannot persist unsupported delivery event: " + event.getClass().getName());
        }
        return s;
    }

    private static DomainEvent toDomain(final StoredDeliveryEvent s) {
        final DeliveryId id = DeliveryId.of(s.aggregateId);
        final Instant occurredOn = Instant.parse(s.occurredOn);
        return switch (s.eventType) {
            case "DeliveryRequestCreated" -> new DeliveryRequestCreated(
                    id, SenderId.of(s.senderId), toRequest(s), occurredOn);
            case "ValidationDeliveryPassed" -> new ValidationDeliveryPassed(id, occurredOn);
            case "ValidationDeliveryRejected" -> new ValidationDeliveryRejected(id, s.reason, occurredOn);
            case "DeliveryScheduled" -> new DeliveryScheduled(
                    id, s.slot == null ? null : LocalDateTime.parse(s.slot), occurredOn);
            case "DroneReserved" -> new DroneReserved(id, s.droneId, occurredOn);
            case "DroneAssigned" -> new DroneAssigned(id, s.droneId, occurredOn);
            case "DeliveryBegun" -> new DeliveryBegun(id, occurredOn);
            case "DeliveryCompleted" -> new DeliveryCompleted(id, occurredOn);
            case "DeliveryCancelled" -> new DeliveryCancelled(id, occurredOn);
            default -> throw new IllegalStateException("Unknown stored event type: " + s.eventType);
        };
    }

    private static DeliveryRequest toRequest(final StoredDeliveryEvent s) {
        final Package parcel = new Package(s.weightKg);
        final Location pickup = Location.of(new Coordinates(s.startLat, s.startLon), s.startAddress);
        final Location destination = Location.of(
                new Coordinates(s.destinationLat, s.destinationLon), s.destinationAddress);
        final RequestedDateTime when = s.immediate
                ? RequestedDateTime.immediateRequest()
                : RequestedDateTime.scheduledAt(LocalDateTime.parse(s.scheduledAt));
        final Deadline deadline = s.deadlineMinutes > 0 ? Deadline.ofMinutes(s.deadlineMinutes) : null;
        return new DeliveryRequest(parcel, pickup, destination, when, deadline);
    }
}
