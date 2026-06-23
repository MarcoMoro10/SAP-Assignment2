package it.unibo.sap.common.ddd;

import java.time.Instant;

public interface DomainEvent {

    Instant occurredOn();
}
