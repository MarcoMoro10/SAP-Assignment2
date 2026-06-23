package it.unibo.sap.delivery.domain.deliveries;

import it.unibo.sap.common.ddd.Identifier;

import java.util.Objects;
import java.util.UUID;

public final class TrackingSessionId implements Identifier<String> {

    private final String value;

    private TrackingSessionId(final String value) {
        Objects.requireNonNull(value, "TrackingSessionId value must not be null");
        this.value = value;
    }

    public static TrackingSessionId of(final String value) {
        return new TrackingSessionId(value);
    }

    public static TrackingSessionId generate() {
        return new TrackingSessionId(UUID.randomUUID().toString());
    }

    @Override
    public String value() {
        return value;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (!(o instanceof TrackingSessionId that)) return false;
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
