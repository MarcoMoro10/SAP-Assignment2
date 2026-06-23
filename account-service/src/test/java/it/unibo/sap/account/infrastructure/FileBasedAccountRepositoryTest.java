package it.unibo.sap.account.infrastructure;

import it.unibo.sap.account.domain.Account;
import it.unibo.sap.account.domain.Role;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Persistence round-trip for {@link FileBasedAccountRepository}: an account written by one
 * instance must be readable by a second instance pointed at the same file. Guards against the
 * Jackson deserialization regression (AccountRecord must have a no-arg constructor).
 */
class FileBasedAccountRepositoryTest {

    @TempDir
    Path tempDir;

    @Test
    void accountSurvivesSaveAndReloadFromFile() {
        final Path file = tempDir.resolve("accounts.json");

        final FileBasedAccountRepository writer = new FileBasedAccountRepository(file.toString());
        final Account account = Account.register("marco", "Secret#123");
        writer.save(account);

        final FileBasedAccountRepository reader = new FileBasedAccountRepository(file.toString());
        final Optional<Account> reloaded = reader.findByUsername("marco");

        assertTrue(reloaded.isPresent(), "account should be reloaded from file after restart");
        assertEquals(account.getId(), reloaded.get().getId());
        assertEquals("marco", reloaded.get().getUsername());
        assertEquals(Role.SENDER, reloaded.get().getRole());
        assertTrue(reloaded.get().checkPassword("Secret#123"), "password hash must round-trip");
    }
}
