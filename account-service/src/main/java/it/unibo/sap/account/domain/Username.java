package it.unibo.sap.account.domain;

import it.unibo.sap.common.ddd.ValueObject;

public final class Username implements ValueObject {

    private final String value;

    private Username(final String value) {
        this.value = value;
    }

    public static Username of(final String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Username must not be empty");
        }
        return new Username(value.trim());
    }

    public String value() {
        return value;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (!(o instanceof Username that)) return false;
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