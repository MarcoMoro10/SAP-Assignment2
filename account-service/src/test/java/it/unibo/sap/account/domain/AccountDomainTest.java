package it.unibo.sap.account.domain;

import it.unibo.sap.account.domain.events.AccountCreated;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit (solitary) tests of the account domain: value objects {@link Username}/{@link Password}
 * and the {@link Account} aggregate. No infrastructure involved.
 */
class AccountDomainTest {

    @Test
    void usernameRejectsNullOrBlank() {
        assertThrows(IllegalArgumentException.class, () -> Username.of(null));
        assertThrows(IllegalArgumentException.class, () -> Username.of("   "));
    }

    @Test
    void usernameTrimsAndEquals() {
        assertEquals(Username.of("marco"), Username.of("  marco  "));
    }

    @Test
    void passwordRejectsNullOrBlankRaw() {
        assertThrows(IllegalArgumentException.class, () -> Password.fromRaw(null));
        assertThrows(IllegalArgumentException.class, () -> Password.fromRaw(""));
    }

    @Test
    void passwordHashesAndMatches() {
        final Password password = Password.fromRaw("Secret#123");
        assertTrue(password.matches("Secret#123"));
        assertFalse(password.matches("WrongPass#1"));
        assertFalse(password.matches(null));
        assertFalse(password.hash().contains("Secret#123"));
    }

    @Test
    void passwordFromHashRoundTrips() {
        final String hash = Password.fromRaw("Secret#123").hash();
        final Password rebuilt = Password.fromHash(hash);
        assertTrue(rebuilt.matches("Secret#123"));
    }

    @Test
    void registerCreatesSenderAndRaisesEvent() {
        final Account account = Account.register("marco", "Secret#123");

        assertNotNull(account.getId());
        assertEquals("marco", account.getUsername());
        assertEquals(Role.SENDER, account.getRole());
        assertNotNull(account.getWhenCreated());
        assertTrue(account.checkPassword("Secret#123"));
        assertFalse(account.checkPassword("WrongPass#1"));

        assertEquals(1, account.getDomainEvents().size());
        assertTrue(account.getDomainEvents().get(0) instanceof AccountCreated);
        final AccountCreated event = (AccountCreated) account.getDomainEvents().get(0);
        assertEquals("marco", event.username());
        assertEquals(Role.SENDER, event.role());
    }

    @Test
    void createAdminHasAdminRoleAndNoEvents() {
        final Account admin = Account.createAdmin(AccountId.of("admin-1"), "admin-1", "Admin#123");
        assertEquals(Role.ADMIN, admin.getRole());
        assertTrue(admin.checkPassword("Admin#123"));
        assertTrue(admin.getDomainEvents().isEmpty());
    }

    @Test
    void clearDomainEventsEmptiesTheList() {
        final Account account = Account.register("marco", "Secret#123");
        account.clearDomainEvents();
        assertTrue(account.getDomainEvents().isEmpty());
    }
}
