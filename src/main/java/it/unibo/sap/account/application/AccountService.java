package it.unibo.sap.account.application;

import it.unibo.sap.account.domain.Account;
import it.unibo.sap.account.domain.AccountId;
import it.unibo.sap.common.hexagonal.InputPort;

import java.util.Optional;

public interface AccountService extends InputPort {

    Account register(String username, String password);

    Account login(String username, String password);

    Optional<Account> getAccount(AccountId accountId);
}
