package it.unibo.sap.account.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.unibo.sap.account.application.AccountRepository;
import it.unibo.sap.account.domain.Account;
import it.unibo.sap.account.domain.AccountId;
import it.unibo.sap.account.domain.Role;
import it.unibo.sap.common.hexagonal.OutputAdapter;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class FileBasedAccountRepository implements AccountRepository, OutputAdapter {

    private static final String DEFAULT_FILE = "data/accounts.json";

    private final Path file;
    private final ObjectMapper mapper = new ObjectMapper();
    private final Map<String, Account> store = new ConcurrentHashMap<>();

    public FileBasedAccountRepository() {
        this(DEFAULT_FILE);
    }

    public FileBasedAccountRepository(final String filePath) {
        this.file = Path.of(filePath);
        load();
    }

    @Override
    public void save(final Account account) {
        store.put(account.getId().value(), account);
        flush();
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
        flush();
    }

    @Override
    public Optional<Account> findByUsername(final String username) {
        return store.values().stream()
                .filter(a -> a.getUsername().equals(username))
                .findFirst();
    }

    private void load() {
        if (!Files.exists(file)) {
            return;
        }
        try {
            final byte[] bytes = Files.readAllBytes(file);
            if (bytes.length == 0) {
                return;
            }
            final AccountRecord[] records = mapper.readValue(bytes, AccountRecord[].class);
            for (final AccountRecord r : records) {
                final Account account = Account.reconstitute(
                        AccountId.of(r.accountId),
                        r.username,
                        r.passwordHash,
                        Role.valueOf(r.role),
                        Instant.ofEpochMilli(r.whenCreated));
                store.put(account.getId().value(), account);
            }
        } catch (final IOException e) {
            throw new UncheckedIOException("Failed to load accounts from " + file, e);
        }
    }

    private void flush() {
        try {
            if (file.getParent() != null) {
                Files.createDirectories(file.getParent());
            }
            final List<AccountRecord> records = new ArrayList<>();
            for (final Account a : store.values()) {
                records.add(new AccountRecord(
                        a.getId().value(),
                        a.getUsername(),
                        a.getPasswordHash(),
                        a.getRole().name(),
                        a.getWhenCreated().toEpochMilli()));
            }
            mapper.writerWithDefaultPrettyPrinter().writeValue(file.toFile(), records);
        } catch (final IOException e) {
            throw new UncheckedIOException("Failed to persist accounts to " + file, e);
        }
    }
}