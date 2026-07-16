package it.unibo.sap.session.domain.events;

import it.unibo.sap.common.ddd.DomainEvent;
import it.unibo.sap.session.domain.SessionId;

import java.time.Instant;

public record SessionCreated(
        SessionId sessionId,
        String accountId,
        String role,
        Instant occurredOn
) implements DomainEvent {
}
