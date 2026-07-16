package it.unibo.sap.delivery.application;

import it.unibo.sap.common.hexagonal.OutputPort;

import java.util.Optional;

public interface SessionValidatorPort extends OutputPort {

    Optional<ValidatedCaller> validate(String sessionId);

    record ValidatedCaller(String accountId, String role) { }
}
