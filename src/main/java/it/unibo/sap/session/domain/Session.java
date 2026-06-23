package it.unibo.sap.session.domain;

import it.unibo.sap.common.ddd.AggregateRoot;
import it.unibo.sap.common.ddd.DomainEvent;
import it.unibo.sap.session.domain.events.SessionCreated;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class Session implements AggregateRoot<SessionId> {

    private final SessionId id;
    private final String accountId;
    private final String role;
    private final Instant createdAt;
    private boolean active;
    private final List<DomainEvent> domainEvents = new ArrayList<>();

    private Session(final SessionId id, final String accountId, final String role,
                    final Instant createdAt, final boolean active) {
        this.id = Objects.requireNonNull(id);
        this.accountId = Objects.requireNonNull(accountId);
        this.role = Objects.requireNonNull(role);
        this.createdAt = Objects.requireNonNull(createdAt);
        this.active = active;
    }

    public static Session create(final String accountId, final String role) {
        final var session = new Session(SessionId.generate(), accountId, role, Instant.now(), true);
        session.registerEvent(new SessionCreated(session.id, accountId, role, session.createdAt));
        return session;
    }

    public boolean isActive() {
        return active;
    }

    public String getAccountId() {
        return accountId;
    }

    public String getRole() {
        return role;
    }

    @Override
    public SessionId getId() {
        return id;
    }

    @Override
    public List<DomainEvent> getDomainEvents() {
        return Collections.unmodifiableList(domainEvents);
    }

    @Override
    public void clearDomainEvents() {
        domainEvents.clear();
    }

    protected void registerEvent(final DomainEvent event) {
        domainEvents.add(event);
    }
}
