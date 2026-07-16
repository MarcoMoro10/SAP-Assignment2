package it.unibo.sap.session.application;

import io.vertx.core.json.JsonObject;
import it.unibo.sap.session.domain.Session;
import it.unibo.sap.session.domain.SessionId;

import java.util.Optional;

public class SessionServiceImpl implements SessionService {

    private final AccountService accountService;
    private final SessionRepository sessionRepository;

    public SessionServiceImpl(final AccountService accountService,
                              final SessionRepository sessionRepository) {
        this.accountService = accountService;
        this.sessionRepository = sessionRepository;
    }

    @Override
    public Session login(final String username, final String password) {
        final JsonObject accountInfo = accountService.login(username, password)
                .orElseThrow(() -> new IllegalArgumentException("Invalid credentials"));
        final String accountId = accountInfo.getString("accountId");
        final String role = accountInfo.getString("role");
        final Session session = Session.create(accountId, role);
        sessionRepository.save(session);
        session.clearDomainEvents();
        return session;
    }

    @Override
    public Optional<Session> getSession(final SessionId sessionId) {
        return sessionRepository.findById(sessionId)
                .filter(Session::isActive);
    }
}
