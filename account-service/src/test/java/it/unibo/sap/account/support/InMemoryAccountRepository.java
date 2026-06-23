package it.unibo.sap.account.support;

import it.unibo.sap.account.application.AccountRepository;
import it.unibo.sap.account.domain.Account;
import it.unibo.sap.account.domain.AccountId;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory fake of {@link AccountRepository} for unit and component tests.
 * Keeps no file state, so it is fast and isolated; {@link #clear()} lets the
 * component tests reset the store between scenarios.
 */
public final class InMemoryAccountRepository implements AccountRepository {

    private final Map<String, Account> store = new ConcurrentHashMap<>();

    @Override
    public void save(final Account account) {
        store.put(account.getId().value(), account);
    }

    @Override
    public Optional<Account> findById(final AccountId id) {
        return Optional.ofNullable(store.get(id.value()));
    }

    @Override
    public List<Account> findAll() {
        return new ArrayList<>(store.values());
    }

    @Override
    public void deleteById(final AccountId id) {
        store.remove(id.value());
    }

    @Override
    public Optional<Account> findByUsername(final String username) {
        return store.values().stream()
                .filter(a -> a.getUsername().equals(username))
                .findFirst();
    }

    public void clear() {
        store.clear();
    }
}
