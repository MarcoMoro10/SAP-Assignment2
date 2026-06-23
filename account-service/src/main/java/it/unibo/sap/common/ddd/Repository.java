package it.unibo.sap.common.ddd;

import java.util.List;
import java.util.Optional;

public interface Repository<ID extends Identifier<?>, T extends AggregateRoot<ID>> {

    void save(T aggregateRoot);

    Optional<T> findById(ID id);

    List<T> findAll();

    void deleteById(ID id);
}
