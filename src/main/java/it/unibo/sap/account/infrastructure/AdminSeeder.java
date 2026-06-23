package it.unibo.sap.account.infrastructure;

import it.unibo.sap.account.application.AccountRepository;
import it.unibo.sap.account.domain.Account;
import it.unibo.sap.account.domain.AccountId;

public final class AdminSeeder {

    public static final String DEFAULT_ADMIN_ID = "admin-1";
    public static final String DEFAULT_ADMIN_USERNAME = "admin-1";
    public static final String DEFAULT_ADMIN_PASSWORD = "Admin#123";

    private AdminSeeder() {
    }

    public static void seed(final AccountRepository repository) {
        seed(repository, DEFAULT_ADMIN_ID, DEFAULT_ADMIN_USERNAME, DEFAULT_ADMIN_PASSWORD);
    }

    public static void seed(final AccountRepository repository,
                            final String adminId,
                            final String username,
                            final String password) {
        if (repository.findByUsername(username).isPresent()) {
            return;
        }
        final Account admin = Account.createAdmin(AccountId.of(adminId), username, password);
        repository.save(admin);
    }
}
