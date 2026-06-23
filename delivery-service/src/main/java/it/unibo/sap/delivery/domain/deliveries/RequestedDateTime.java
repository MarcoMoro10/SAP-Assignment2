package it.unibo.sap.delivery.domain.deliveries;

import it.unibo.sap.common.ddd.ValueObject;

import java.time.LocalDateTime;
import java.util.Objects;

public record RequestedDateTime(boolean immediate, LocalDateTime scheduledAt) implements ValueObject {

    public RequestedDateTime {
        if (!immediate) {
            Objects.requireNonNull(scheduledAt, "A scheduled request must carry a scheduledAt instant");
        }
    }

    public static RequestedDateTime immediateRequest() {
        return new RequestedDateTime(true, null);
    }

    public static RequestedDateTime scheduledAt(final LocalDateTime when) {
        return new RequestedDateTime(false, when);
    }

    public boolean isImmediate() {
        return immediate;
    }

}