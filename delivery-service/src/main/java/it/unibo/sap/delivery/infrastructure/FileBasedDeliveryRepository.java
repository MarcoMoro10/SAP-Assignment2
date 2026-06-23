package it.unibo.sap.delivery.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.unibo.sap.common.hexagonal.OutputAdapter;
import it.unibo.sap.delivery.application.DeliveryRepository;
import it.unibo.sap.delivery.domain.deliveries.Coordinates;
import it.unibo.sap.delivery.domain.deliveries.Deadline;
import it.unibo.sap.delivery.domain.deliveries.Delivery;
import it.unibo.sap.delivery.domain.deliveries.DeliveryId;
import it.unibo.sap.delivery.domain.deliveries.DeliveryRequest;
import it.unibo.sap.delivery.domain.deliveries.DeliveryStatus;
import it.unibo.sap.delivery.domain.deliveries.EstimatedTimeRemaining;
import it.unibo.sap.delivery.domain.deliveries.Location;
import it.unibo.sap.delivery.domain.deliveries.Package;
import it.unibo.sap.delivery.domain.deliveries.RequestedDateTime;
import it.unibo.sap.delivery.domain.deliveries.SenderId;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class FileBasedDeliveryRepository implements DeliveryRepository, OutputAdapter {

    private static final String DEFAULT_FILE = "data/deliveries.json";

    private final Path file;
    private final ObjectMapper mapper = new ObjectMapper();
    private final Map<String, Delivery> store = new ConcurrentHashMap<>();

    public FileBasedDeliveryRepository() {
        this(DEFAULT_FILE);
    }

    public FileBasedDeliveryRepository(final String filePath) {
        this.file = Path.of(filePath);
        load();
    }

    @Override
    public void save(final Delivery delivery) {
        store.put(delivery.getId().value(), delivery);
        flush();
    }

    @Override
    public Optional<Delivery> findById(final DeliveryId id) {
        return Optional.ofNullable(store.get(id.value()));
    }

    @Override
    public List<Delivery> findAll() {
        return new ArrayList<>(store.values());
    }

    @Override
    public void deleteById(final DeliveryId id) {
        store.remove(id.value());
        flush();
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
            final DeliveryRecord[] records = mapper.readValue(bytes, DeliveryRecord[].class);
            for (final DeliveryRecord r : records) {
                store.put(r.deliveryId, toDomain(r));
            }
        } catch (final IOException e) {
            throw new UncheckedIOException("Failed to load deliveries from " + file, e);
        }
    }

    private void flush() {
        try {
            if (file.getParent() != null) {
                Files.createDirectories(file.getParent());
            }
            final List<DeliveryRecord> records = store.values().stream()
                    .map(FileBasedDeliveryRepository::toRecord)
                    .collect(Collectors.toList());
            mapper.writerWithDefaultPrettyPrinter().writeValue(file.toFile(), records);
        } catch (final IOException e) {
            throw new UncheckedIOException("Failed to persist deliveries to " + file, e);
        }
    }

    private static DeliveryRecord toRecord(final Delivery d) {
        final DeliveryRecord r = new DeliveryRecord();
        final DeliveryRequest req = d.getRequest();
        r.deliveryId = d.getId().value();
        r.senderId = d.getSenderId().value();
        r.weightKg = req.parcel().weightKg();
        r.startAddress = req.pickupLocation().address();
        r.startLat = req.pickupLocation().coordinates().latitude();
        r.startLon = req.pickupLocation().coordinates().longitude();
        r.destinationAddress = req.destination().address();
        r.destinationLat = req.destination().coordinates().latitude();
        r.destinationLon = req.destination().coordinates().longitude();
        r.immediate = req.requestedDateTime().isImmediate();
        r.scheduledAt = req.requestedDateTime().scheduledAt() == null
                ? null : req.requestedDateTime().scheduledAt().toString();
        r.deadlineMinutes = req.deadline() == null ? 0 : req.deadline().maxDuration().toMinutes();
        r.status = d.getStatus().name();
        r.assignedDroneId = d.getAssignedDroneId();
        r.etrSeconds = d.getEstimatedTimeRemaining().toSeconds();
        return r;
    }

    private static Delivery toDomain(final DeliveryRecord r) {
        final Package parcel = new Package(r.weightKg);
        final Location pickup = Location.of(new Coordinates(r.startLat, r.startLon), r.startAddress);
        final Location destination = Location.of(new Coordinates(r.destinationLat, r.destinationLon), r.destinationAddress);
        final RequestedDateTime when = r.immediate
                ? RequestedDateTime.immediateRequest()
                : RequestedDateTime.scheduledAt(LocalDateTime.parse(r.scheduledAt));
        final Deadline deadline = r.deadlineMinutes > 0 ? Deadline.ofMinutes(r.deadlineMinutes) : null;
        final DeliveryRequest request = new DeliveryRequest(parcel, pickup, destination, when, deadline);

        return Delivery.reconstitute(
                DeliveryId.of(r.deliveryId),
                SenderId.of(r.senderId),
                request,
                DeliveryStatus.valueOf(r.status),
                r.assignedDroneId,
                EstimatedTimeRemaining.ofSeconds(r.etrSeconds));
    }
}