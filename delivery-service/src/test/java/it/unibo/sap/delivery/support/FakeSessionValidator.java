package it.unibo.sap.delivery.support;

import it.unibo.sap.delivery.application.SessionValidatorPort;

import java.util.Optional;

public class FakeSessionValidator implements SessionValidatorPort {

    public static final String INVALID_TOKEN = "invalid-session";

    @Override
    public Optional<ValidatedCaller> validate(final String sessionId) {
        if (sessionId == null || sessionId.isBlank() || INVALID_TOKEN.equals(sessionId)) {
            return Optional.empty();
        }
        final String role = sessionId.startsWith("admin") ? "ADMIN" : "SENDER";
        return Optional.of(new ValidatedCaller(sessionId, role));
    }
}
