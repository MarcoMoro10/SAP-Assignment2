package it.unibo.sap.session.application;

import io.vertx.core.json.JsonObject;
import it.unibo.sap.session.domain.Session;
import it.unibo.sap.session.domain.SessionId;

import java.util.Optional;

public class SessionServiceImpl implements SessionService {

    private final AccountService accountService;
    private final DeliveryService deliveryService;
    private final SessionRepository sessionRepository;

    public SessionServiceImpl(final AccountService accountService,
                              final DeliveryService deliveryService,
                              final SessionRepository sessionRepository) {
        this.accountService = accountService;
        this.deliveryService = deliveryService;
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

    @Override
    public JsonObject createDelivery(final SessionId sessionId, final JsonObject request) {
        final Session session = getActiveSession(sessionId);
        requireRole(session, "SENDER");
        if (request == null) {
            throw new IllegalArgumentException("Missing request body");
        }
        request.put("senderId", session.getAccountId());
        return deliveryService.createDelivery(request);
    }

    @Override
    public JsonObject cancelDelivery(final SessionId sessionId, final String deliveryId) {
        final Session session = getActiveSession(sessionId);
        requireRole(session, "SENDER");
        return deliveryService.cancelDelivery(deliveryId, session.getAccountId());
    }

    @Override
    public Optional<JsonObject> getDelivery(final SessionId sessionId, final String deliveryId) {
        final Session session = getActiveSession(sessionId);
        requireRole(session, "SENDER");
        return deliveryService.getDelivery(deliveryId, session.getAccountId());
    }

    @Override
    public JsonObject trackDelivery(final SessionId sessionId, final String deliveryId) {
        final Session session = getActiveSession(sessionId);
        requireRole(session, "SENDER");
        return deliveryService.trackDelivery(deliveryId, session.getAccountId());
    }

    @Override
    public JsonObject viewFleet(final SessionId sessionId) {
        final Session session = getActiveSession(sessionId);
        requireRole(session, "ADMIN");
        return deliveryService.viewFleet();
    }

    @Override
    public JsonObject viewScheduling(final SessionId sessionId, final String droneId) {
        final Session session = getActiveSession(sessionId);
        requireRole(session, "ADMIN");
        return deliveryService.viewScheduling(droneId);
    }

    private Session getActiveSession(final SessionId sessionId) {
        return sessionRepository.findById(sessionId)
                .filter(Session::isActive)
                .orElseThrow(() -> new IllegalArgumentException("Session not found or expired"));
    }

    private void requireRole(final Session session, final String requiredRole) {
        if (!session.getRole().equals(requiredRole)) {
            throw new SecurityException("Forbidden: requires " + requiredRole + " role");
        }
    }
}