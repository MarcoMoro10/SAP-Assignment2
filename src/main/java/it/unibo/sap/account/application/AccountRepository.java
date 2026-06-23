package it.unibo.sap.account.application;

import it.unibo.sap.account.domain.Account;
import it.unibo.sap.account.domain.AccountId;
import it.unibo.sap.common.ddd.Repository;
import it.unibo.sap.common.hexagonal.OutputPort;

import java.util.Optional;

public interface AccountRepository extends Repository<AccountId, Account>, OutputPort {

    Optional<Account> findByUsername(String username);
}
