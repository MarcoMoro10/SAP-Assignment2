package it.unibo.sap.session.domain;

import it.unibo.sap.common.ddd.Identifier;

import java.util.Objects;
import java.util.UUID;

public final class SessionId implements Identifier<String> {

    private final String value;

    private SessionId(final String value) {
        Objects.requireNonNull(value, "SessionId value must not be null");
        this.value = value;
    }

    public static SessionId of(final String value) {
        return new SessionId(value);
    }

    public static SessionId generate() {
        return new SessionId(UUID.randomUUID().toString());
    }

    @Override
    public String value() {
        return value;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (!(o instanceof SessionId that)) return false;
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
