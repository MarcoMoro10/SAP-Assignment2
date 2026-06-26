package it.unibo.sap.delivery.domain.fleet;

import it.unibo.sap.common.ddd.Identifier;

import java.util.Objects;

public final class DroneId implements Identifier<String> {

    private final String value;

    private DroneId(final String value) {
        Objects.requireNonNull(value, "DroneId value must not be null");
        this.value = value;
    }

    public static DroneId of(final String value) {
        return new DroneId(value);
    }

    @Override
    public String value() {
        return value;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (!(o instanceof DroneId that)) return false;
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
