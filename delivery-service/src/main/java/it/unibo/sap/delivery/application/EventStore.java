package it.unibo.sap.delivery.application;

import it.unibo.sap.common.ddd.DomainEvent;
import it.unibo.sap.common.hexagonal.OutputPort;

import java.util.List;

public interface EventStore extends OutputPort {

    void append(String aggregateId, List<DomainEvent> events);

    List<DomainEvent> load(String aggregateId);

    List<String> aggregateIds();
}
