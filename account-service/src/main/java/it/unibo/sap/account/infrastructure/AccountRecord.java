package it.unibo.sap.account.infrastructure;

public class AccountRecord {

    public String accountId;
    public String username;
    public String passwordHash;
    public String role;
    public long whenCreated;

    public AccountRecord() {
    }

    public AccountRecord(final String accountId, final String username, final String passwordHash,
                         final String role, final long whenCreated) {
        this.accountId = accountId;
        this.username = username;
        this.passwordHash = passwordHash;
        this.role = role;
        this.whenCreated = whenCreated;
    }
}