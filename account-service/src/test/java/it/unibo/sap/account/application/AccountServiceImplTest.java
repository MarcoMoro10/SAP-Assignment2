package it.unibo.sap.account.application;

import it.unibo.sap.account.domain.Account;
import it.unibo.sap.account.domain.AccountId;
import it.unibo.sap.account.domain.Role;
import it.unibo.sap.account.support.InMemoryAccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit (solitary) tests of {@link AccountServiceImpl} against an in-memory fake repository.
 * Exercises registration, login and lookup without any HTTP/file infrastructure.
 */
class AccountServiceImplTest {

    private InMemoryAccountRepository repository;
    private AccountService service;

    @BeforeEach
    void setUp() {
        repository = new InMemoryAccountRepository();
        service = new AccountServiceImpl(repository);
    }

    @Test
    void registerPersistsTheAccountAsSender() {
        final Account account = service.register("marco", "Secret#123");

        assertNotNull(account.getId());
        assertEquals("marco", account.getUsername());
        assertEquals(Role.SENDER, account.getRole());
        assertTrue(repository.findByUsername("marco").isPresent());
        assertTrue(account.getDomainEvents().isEmpty());
    }

    @Test
    void registerRejectsDuplicateUsername() {
        service.register("marco", "Secret#123");
        final IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> service.register("marco", "Another#1"));
        assertEquals("Username already taken", ex.getMessage());
    }

    @Test
    void registerRejectsBlankCredentials() {
        assertThrows(IllegalArgumentException.class, () -> service.register("  ", "Secret#123"));
        assertThrows(IllegalArgumentException.class, () -> service.register("marco", "  "));
    }

    @Test
    void loginSucceedsWithRightCredentials() {
        final Account created = service.register("marco", "Secret#123");
        final Account loggedIn = service.login("marco", "Secret#123");
        assertEquals(created.getId(), loggedIn.getId());
    }

    @Test
    void loginFailsForUnknownAccount() {
        final IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.login("ghost", "whatever"));
        assertEquals("Account not found", ex.getMessage());
    }

    @Test
    void loginFailsWithWrongPassword() {
        service.register("marco", "Secret#123");
        final IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.login("marco", "WrongPass#1"));
        assertEquals("Invalid credentials", ex.getMessage());
    }

    @Test
    void getAccountReturnsStoredAccountAndEmptyForUnknown() {
        final Account created = service.register("marco", "Secret#123");
        final Optional<Account> found = service.getAccount(created.getId());
        assertTrue(found.isPresent());
        assertEquals(created.getId(), found.get().getId());
        assertFalse(service.getAccount(AccountId.of("does-not-exist")).isPresent());
    }
}
