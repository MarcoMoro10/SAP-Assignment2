package it.unibo.sap.common.ddd;

import java.util.List;

public interface AggregateRoot<ID extends Identifier<?>> extends Entity<ID> {

    List<DomainEvent> getDomainEvents();

    void clearDomainEvents();
}
