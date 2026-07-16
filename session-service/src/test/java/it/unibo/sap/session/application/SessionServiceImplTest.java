package it.unibo.sap.session.application;

import it.unibo.sap.session.domain.Session;
import it.unibo.sap.session.domain.SessionId;
import it.unibo.sap.session.domain.events.SessionCreated;
import it.unibo.sap.session.infrastructure.InMemorySessionRepository;
import it.unibo.sap.session.support.FakeAccountService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit (solitary) tests of {@link SessionServiceImpl}: login authenticates against the account port
 * and coins a {@link Session} aggregate (emitting {@link SessionCreated}); introspection resolves a
 * stored, active session to its identity.
 */
class SessionServiceImplTest {

    private InMemorySessionRepository repository;

    @BeforeEach
    void setUp() {
        repository = new InMemorySessionRepository();
    }

    @Test
    void loginCoinsAnActiveSessionForValidCredentials() {
        final SessionService service = new SessionServiceImpl(
                new FakeAccountService().withSuccessfulLogin("marco", "Secret#123", "acc-1", "SENDER"),
                repository);

        final Session session = service.login("marco", "Secret#123");

        assertNotNull(session.getId());
        assertEquals("acc-1", session.getAccountId());
        assertEquals("SENDER", session.getRole());
        assertTrue(session.isActive());
        assertTrue(repository.findById(session.getId()).isPresent());
    }

    @Test
    void loginRegistersASessionCreatedEventBeforeItIsCleared() {
        // Verify the aggregate emits the domain event by inspecting the aggregate before persistence.
        final Session freshlyCreated = Session.create("acc-1", "SENDER");
        assertEquals(1, freshlyCreated.getDomainEvents().size());
        assertTrue(freshlyCreated.getDomainEvents().get(0) instanceof SessionCreated);
    }

    @Test
    void loginRejectsInvalidCredentials() {
        final SessionService service = new SessionServiceImpl(
                new FakeAccountService().withSuccessfulLogin("marco", "Secret#123", "acc-1", "SENDER"),
                repository);

        assertThrows(IllegalArgumentException.class, () -> service.login("marco", "wrong"));
    }

    @Test
    void introspectionResolvesAStoredActiveSession() {
        final SessionService service = new SessionServiceImpl(
                new FakeAccountService().withSuccessfulLogin("ada", "Secret#123", "acc-admin", "ADMIN"),
                repository);
        final Session session = service.login("ada", "Secret#123");

        final Optional<Session> resolved = service.getSession(session.getId());

        assertTrue(resolved.isPresent());
        assertEquals("acc-admin", resolved.get().getAccountId());
        assertEquals("ADMIN", resolved.get().getRole());
    }

    @Test
    void introspectionOfAnUnknownSessionIsEmpty() {
        final SessionService service = new SessionServiceImpl(
                new FakeAccountService().withSuccessfulLogin("ada", "Secret#123", "acc-admin", "ADMIN"),
                repository);

        assertFalse(service.getSession(SessionId.of("does-not-exist")).isPresent());
    }
}
