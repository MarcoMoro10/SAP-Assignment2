package it.unibo.sap.account.domain;

import it.unibo.sap.common.ddd.Identifier;

import java.util.Objects;
import java.util.UUID;

public final class AccountId implements Identifier<String> {

    private final String value;

    private AccountId(final String value) {
        Objects.requireNonNull(value, "AccountId value must not be null");
        this.value = value;
    }

    public static AccountId of(final String value) {
        return new AccountId(value);
    }

    public static AccountId generate() {
        return new AccountId(UUID.randomUUID().toString());
    }

    @Override
    public String value() {
        return value;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (!(o instanceof AccountId that)) return false;
        return value.equals(that.value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    @Override
    public String toString() {
        return value;
    }
}
