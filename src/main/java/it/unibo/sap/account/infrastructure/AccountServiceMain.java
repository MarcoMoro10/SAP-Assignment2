package it.unibo.sap.account.infrastructure;

import io.vertx.core.Vertx;
import it.unibo.sap.account.application.AccountRepository;
import it.unibo.sap.account.application.AccountService;
import it.unibo.sap.account.application.AccountServiceImpl;

public class AccountServiceMain {

    static final int ACCOUNT_SERVICE_PORT = 8080;

    public static void main(final String[] args) {
        final AccountRepository repository = new FileBasedAccountRepository();
        AdminSeeder.seed(repository);

        final AccountService service = new AccountServiceImpl(repository);

        final Vertx vertx = Vertx.vertx();
        final var controller = new AccountServiceController(service, ACCOUNT_SERVICE_PORT);
        vertx.deployVerticle(controller);
    }
}
