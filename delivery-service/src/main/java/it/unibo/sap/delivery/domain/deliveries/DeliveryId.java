package it.unibo.sap.delivery.domain.deliveries;

import it.unibo.sap.common.ddd.Identifier;

import java.util.Objects;
import java.util.UUID;

public final class DeliveryId implements Identifier<String> {

    private final String value;

    private DeliveryId(final String value) {
        Objects.requireNonNull(value, "DeliveryId value must not be null");
        this.value = value;
    }

    public static DeliveryId of(final String value) {
        return new DeliveryId(value);
    }

    public static DeliveryId generate() {
        return new DeliveryId(UUID.randomUUID().toString());
    }

    @Override
    public String value() {
        return value;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (!(o instanceof DeliveryId that)) return false;
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
