package it.unibo.sap.session.application;

import it.unibo.sap.common.hexagonal.InputPort;
import it.unibo.sap.session.domain.Session;
import it.unibo.sap.session.domain.SessionId;

import java.util.Optional;

public interface SessionService extends InputPort {

    Session login(String username, String password);

    Optional<Session> getSession(SessionId sessionId);
}
