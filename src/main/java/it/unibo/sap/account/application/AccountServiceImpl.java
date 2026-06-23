package it.unibo.sap.account.application;

import it.unibo.sap.account.domain.Account;
import it.unibo.sap.account.domain.AccountId;

import java.util.Optional;

public class AccountServiceImpl implements AccountService {

    private final AccountRepository repository;

    public AccountServiceImpl(final AccountRepository repository) {
        this.repository = repository;
    }

    @Override
    public Account register(final String username, final String password) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("Username must not be empty");
        }
        if (password == null || password.isBlank()) {
            throw new IllegalArgumentException("Password must not be empty");
        }
        if (repository.findByUsername(username).isPresent()) {
            throw new IllegalStateException("Username already taken");
        }
        final Account account = Account.register(username, password);
        repository.save(account);
        account.clearDomainEvents();
        return account;
    }

    @Override
    public Account login(final String username, final String password) {
        final Account account = repository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("Account not found"));
        if (!account.checkPassword(password)) {
            throw new IllegalArgumentException("Invalid credentials");
        }
        return account;
    }

    @Override
    public Optional<Account> getAccount(final AccountId accountId) {
        return repository.findById(accountId);
    }
}
