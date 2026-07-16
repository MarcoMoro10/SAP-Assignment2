package it.unibo.sap.session.infrastructure;

import io.vertx.core.Vertx;
import io.vertx.ext.web.client.WebClient;
import it.unibo.sap.session.application.AccountService;
import it.unibo.sap.session.application.SessionRepository;
import it.unibo.sap.session.application.SessionService;
import it.unibo.sap.session.application.SessionServiceImpl;

public class SessionServiceMain {

    static final int DEFAULT_SESSION_SERVICE_PORT = 9001;
    static final String DEFAULT_ACCOUNT_HOST = "localhost";
    static final int DEFAULT_ACCOUNT_PORT = 9000;

    public static void main(final String[] args) {
        final int sessionPort = Env.getInt("SESSION_PORT", DEFAULT_SESSION_SERVICE_PORT);
        final String accountHost = Env.get("ACCOUNT_HOST", DEFAULT_ACCOUNT_HOST);
        final int accountPort = Env.getInt("ACCOUNT_PORT", DEFAULT_ACCOUNT_PORT);

        final Vertx vertx = Vertx.vertx();
        final WebClient webClient = WebClient.create(vertx);

        final AccountService accountServiceProxy =
                new AccountServiceProxy(webClient, accountHost, accountPort);
        final SessionRepository sessionRepository = new InMemorySessionRepository();

        final SessionService service = new SessionServiceImpl(accountServiceProxy, sessionRepository);

        vertx.deployVerticle(new SessionServiceController(service, sessionPort));
    }
}
