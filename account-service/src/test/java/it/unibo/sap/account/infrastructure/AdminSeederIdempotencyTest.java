package it.unibo.sap.account.infrastructure;

import it.unibo.sap.account.domain.Account;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * With the accounts file now persisted on a named volume, {@link AdminSeeder#seed} runs on every
 * boot against a store that already contains the admin. It must be idempotent: two consecutive
 * seeds over the same persisted file must not duplicate nor overwrite the admin account.
 */
class AdminSeederIdempotencyTest {

    @TempDir
    Path tempDir;

    @Test
    void seedingTwiceOverPersistedFileDoesNotDuplicateAdmin() {
        final Path file = tempDir.resolve("accounts.json");

        // First boot: empty file, admin gets seeded and persisted.
        final FileBasedAccountRepository firstBoot = new FileBasedAccountRepository(file.toString());
        AdminSeeder.seed(firstBoot);

        // Second boot: admin is reloaded from file, seeding again must be a no-op.
        final FileBasedAccountRepository secondBoot = new FileBasedAccountRepository(file.toString());
        final String adminIdBefore = secondBoot.findByUsername(AdminSeeder.DEFAULT_ADMIN_USERNAME)
                .orElseThrow().getId().value();
        AdminSeeder.seed(secondBoot);

        final List<Account> all = secondBoot.findAll();
        assertEquals(1, all.size(), "admin must not be duplicated across boots");
        assertEquals(AdminSeeder.DEFAULT_ADMIN_USERNAME, all.get(0).getUsername());
        assertEquals(adminIdBefore, all.get(0).getId().value(), "admin id must stay stable, not be overwritten");
    }
}
