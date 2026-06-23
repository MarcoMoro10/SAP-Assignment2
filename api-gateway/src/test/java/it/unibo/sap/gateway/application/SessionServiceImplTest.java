package it.unibo.sap.gateway.application;

import io.vertx.core.json.JsonObject;
import it.unibo.sap.gateway.domain.Session;
import it.unibo.sap.gateway.domain.SessionId;
import it.unibo.sap.gateway.infrastructure.InMemorySessionRepository;
import it.unibo.sap.gateway.support.FakeAccountService;
import it.unibo.sap.gateway.support.FakeDeliveryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit (solitary) tests of {@link SessionServiceImpl} (the gateway orchestration logic) with the
 * downstream account/delivery ports mocked and a real in-memory session repository.
 */
class SessionServiceImplTest {

    private FakeAccountService account;
    private FakeDeliveryService delivery;
    private SessionRepository sessions;
    private SessionService service;

    @BeforeEach
    void setUp() {
        account = new FakeAccountService();
        delivery = new FakeDeliveryService();
        sessions = new InMemorySessionRepository();
        service = new SessionServiceImpl(account, delivery, sessions);
    }

    private Session loginAs(final String role) {
        account.withSuccessfulLogin("acc-1", role);
        return service.login("user-1", "Secret#123");
    }

    @Test
    void loginCreatesAndStoresASession() {
        final Session session = loginAs("SENDER");

        assertEquals("acc-1", session.getAccountId());
        assertEquals("SENDER", session.getRole());
        assertTrue(sessions.findById(session.getId()).isPresent());
        assertTrue(session.getDomainEvents().isEmpty(), "events should be cleared after persistence");
    }

    @Test
    void loginWithBadCredentialsFails() {
        account.withFailedLogin();
        final IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.login("user-1", "wrong"));
        assertEquals("Invalid credentials", ex.getMessage());
    }

    @Test
    void createDeliveryInjectsSenderIdAndDelegates() {
        final Session session = loginAs("SENDER");
        final JsonObject request = new JsonObject().put("weight", 2.0);

        final JsonObject result = service.createDelivery(session.getId(), request);

        assertEquals("acc-1", delivery.lastSenderId, "the gateway must inject the session's accountId");
        assertEquals("DLV-1", result.getString("deliveryId"));
    }

    @Test
    void createDeliveryWithUnknownSessionFails() {
        final IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.createDelivery(SessionId.of("ghost"), new JsonObject()));
        assertEquals("Session not found or expired", ex.getMessage());
    }

    @Test
    void senderCannotAccessAdminFleetView() {
        final Session session = loginAs("SENDER");
        assertThrows(SecurityException.class, () -> service.viewFleet(session.getId()));
    }

    @Test
    void adminCanAccessFleetView() {
        final Session session = loginAs("ADMIN");
        service.viewFleet(session.getId());
        assertTrue(delivery.viewFleetCalled);
    }

    @Test
    void adminCannotCreateDelivery() {
        final Session session = loginAs("ADMIN");
        assertThrows(SecurityException.class,
                () -> service.createDelivery(session.getId(), new JsonObject()));
    }

    @Test
    void getSessionReturnsActiveSession() {
        final Session session = loginAs("SENDER");
        assertTrue(service.getSession(session.getId()).isPresent());
    }
}
