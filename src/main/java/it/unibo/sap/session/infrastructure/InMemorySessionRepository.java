package it.unibo.sap.session.infrastructure;

import it.unibo.sap.common.hexagonal.OutputAdapter;
import it.unibo.sap.session.application.SessionRepository;
import it.unibo.sap.session.domain.Session;
import it.unibo.sap.session.domain.SessionId;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class InMemorySessionRepository implements SessionRepository, OutputAdapter {

    private final Map<String, Session> store = new ConcurrentHashMap<>();

    @Override
    public void save(final Session session) {
        store.put(session.getId().value(), session);
    }

    @Override
    public Optional<Session> findById(final SessionId id) {
        return Optional.ofNullable(store.get(id.value()));
    }

    @Override
    public List<Session> findAll() {
        return new ArrayList<>(store.values());
    }

    @Override
    public void deleteById(final SessionId id) {
        store.remove(id.value());
    }
}
