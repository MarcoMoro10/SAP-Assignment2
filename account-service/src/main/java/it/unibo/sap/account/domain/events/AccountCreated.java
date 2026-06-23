package it.unibo.sap.account.domain.events;

import it.unibo.sap.account.domain.AccountId;
import it.unibo.sap.account.domain.Role;
import it.unibo.sap.common.ddd.DomainEvent;

import java.time.Instant;

public record AccountCreated(
        AccountId accountId,
        String username,
        Role role,
        Instant occurredOn
) implements DomainEvent {
}
