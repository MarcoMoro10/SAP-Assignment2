package it.unibo.sap.delivery.domain.deliveries;

import it.unibo.sap.common.ddd.ValueObject;

import java.util.Objects;

public final class SenderId implements ValueObject {

    private final String value;

    private SenderId(final String value) {
        Objects.requireNonNull(value, "SenderId value must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException("SenderId must not be empty");
        }
        this.value = value;
    }

    public static SenderId of(final String value) {
        return new SenderId(value);
    }

    public String value() {
        return value;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (!(o instanceof SenderId that)) return false;
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
