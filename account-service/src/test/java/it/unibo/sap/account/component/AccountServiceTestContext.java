package it.unibo.sap.account.component;

import io.vertx.core.Vertx;
import io.vertx.ext.web.client.WebClient;
import it.unibo.sap.account.application.AccountService;
import it.unibo.sap.account.application.AccountServiceImpl;
import it.unibo.sap.account.infrastructure.AccountServiceController;
import it.unibo.sap.account.infrastructure.AdminSeeder;
import it.unibo.sap.account.support.InMemoryAccountRepository;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Boots the real {@link AccountServiceController} once on a dedicated test port and exposes a
 * {@link WebClient} so the component tests can drive the account-service as a black box over HTTP.
 * No session-service is involved: registration and login both hit the account REST API directly.
 * State lives in an in-memory repository that {@link #reset()} clears (and re-seeds the admin)
 * between scenarios.
 */
public final class AccountServiceTestContext {

    private static final int TEST_PORT = 9099;
    private static final String HOST = "localhost";

    private static AccountServiceTestContext instance;

    private final Vertx vertx;
    private final WebClient webClient;
    private final InMemoryAccountRepository repository;

    private AccountServiceTestContext() {
        this.vertx = Vertx.vertx();
        this.repository = new InMemoryAccountRepository();
        AdminSeeder.seed(repository);
        final AccountService service = new AccountServiceImpl(repository);
        final AccountServiceController controller = new AccountServiceController(service, TEST_PORT);

        final CountDownLatch latch = new CountDownLatch(1);
        final boolean[] deployed = {false};
        vertx.deployVerticle(controller).onComplete(ar -> {
            deployed[0] = ar.succeeded();
            latch.countDown();
        });
        try {
            if (!latch.await(15, TimeUnit.SECONDS) || !deployed[0]) {
                throw new IllegalStateException("account-service did not start on test port " + TEST_PORT);
            }
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while starting account-service", e);
        }
        this.webClient = WebClient.create(vertx);
    }

    public static synchronized AccountServiceTestContext get() {
        if (instance == null) {
            instance = new AccountServiceTestContext();
        }
        return instance;
    }

    public void reset() {
        repository.clear();
        AdminSeeder.seed(repository);
    }

    public WebClient webClient() {
        return webClient;
    }

    public int port() {
        return TEST_PORT;
    }

    public String host() {
        return HOST;
    }
}
