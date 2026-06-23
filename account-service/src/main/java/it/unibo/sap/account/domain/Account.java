package it.unibo.sap.account.domain;

import it.unibo.sap.common.ddd.AggregateRoot;
import it.unibo.sap.common.ddd.DomainEvent;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class Account implements AggregateRoot<AccountId> {

    private final AccountId id;
    private final Username username;
    private final Password password;
    private final Role role;
    private final Instant whenCreated;
    private final List<DomainEvent> domainEvents = new ArrayList<>();

    private Account(final AccountId id, final Username username, final Password password,
                    final Role role, final Instant whenCreated) {
        this.id = Objects.requireNonNull(id);
        this.username = Objects.requireNonNull(username);
        this.password = Objects.requireNonNull(password);
        this.role = Objects.requireNonNull(role);
        this.whenCreated = Objects.requireNonNull(whenCreated);
    }

    public static Account register(final String username, final String password) {
        final var account = new Account(
                AccountId.generate(),
                Username.of(username),
                Password.fromRaw(password),
                Role.SENDER,
                Instant.now());
        account.registerEvent(new it.unibo.sap.account.domain.events.AccountCreated(
                account.id, account.username.value(), account.role, account.whenCreated));
        return account;
    }

    public static Account createAdmin(final AccountId id, final String username, final String password) {
        return new Account(id, Username.of(username), Password.fromRaw(password),
                Role.ADMIN, Instant.now());
    }

    public static Account reconstitute(final AccountId id, final String username, final String passwordHash,
                                       final Role role, final Instant whenCreated) {
        return new Account(id, Username.of(username), Password.fromHash(passwordHash),
                role, whenCreated);
    }

    public boolean checkPassword(final String rawPassword) {
        return this.password.matches(rawPassword);
    }

    public String getUsername() {
        return username.value();
    }

    public String getPasswordHash() {
        return password.hash();
    }

    public Role getRole() {
        return role;
    }

    public Instant getWhenCreated() {
        return whenCreated;
    }

    @Override
    public AccountId getId() {
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