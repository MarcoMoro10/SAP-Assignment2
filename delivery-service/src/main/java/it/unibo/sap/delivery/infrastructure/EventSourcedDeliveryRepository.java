package it.unibo.sap.delivery.infrastructure;

import it.unibo.sap.common.ddd.DomainEvent;
import it.unibo.sap.common.hexagonal.OutputAdapter;
import it.unibo.sap.delivery.application.DeliveryRepository;
import it.unibo.sap.delivery.application.EventStore;
import it.unibo.sap.delivery.domain.deliveries.Delivery;
import it.unibo.sap.delivery.domain.deliveries.DeliveryId;
import it.unibo.sap.delivery.domain.deliveries.events.EstimatedTimeUpdated;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class EventSourcedDeliveryRepository implements DeliveryRepository, OutputAdapter {

    private final EventStore eventStore;

    public EventSourcedDeliveryRepository(final EventStore eventStore) {
        this.eventStore = eventStore;
    }

    @Override
    public void save(final Delivery delivery) {
        final List<DomainEvent> newEvents = delivery.getDomainEvents().stream()
                .filter(event -> !(event instanceof EstimatedTimeUpdated))
                .toList();
        eventStore.append(delivery.getId().value(), newEvents);
        delivery.clearDomainEvents();
    }

    @Override
    public Optional<Delivery> findById(final DeliveryId id) {
        return rebuild(id.value());
    }

    @Override
    public List<Delivery> findAll() {
        final List<Delivery> deliveries = new ArrayList<>();
        for (final String aggregateId : eventStore.aggregateIds()) {
            rebuild(aggregateId).ifPresent(deliveries::add);
        }
        return deliveries;
    }

    @Override
    public void deleteById(final DeliveryId id) {
        throw new UnsupportedOperationException(
                "Deliveries are an append-only event stream and cannot be deleted");
    }

    private Optional<Delivery> rebuild(final String aggregateId) {
        final List<DomainEvent> events = eventStore.load(aggregateId);
        if (events.isEmpty()) {
            return Optional.empty();
        }
        final Delivery delivery = new Delivery();
        for (final DomainEvent event : events) {
            delivery.apply(event);
        }
        delivery.clearDomainEvents();
        return Optional.of(delivery);
    }
}
