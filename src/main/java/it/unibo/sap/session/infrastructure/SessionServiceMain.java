package it.unibo.sap.session.infrastructure;

import io.vertx.core.Vertx;
import io.vertx.ext.web.client.WebClient;
import it.unibo.sap.session.application.AccountService;
import it.unibo.sap.session.application.DeliveryService;
import it.unibo.sap.session.application.SessionRepository;
import it.unibo.sap.session.application.SessionService;
import it.unibo.sap.session.application.SessionServiceImpl;

public class SessionServiceMain {

    static final int SESSION_SERVICE_PORT = 8081;

    static final String ACCOUNT_HOST = "localhost";
    static final int ACCOUNT_PORT = 8080;
    static final String DELIVERY_HOST = "localhost";
    static final int DELIVERY_PORT = 8082;
    static final int FLEET_PORT = 8083;

    public static void main(final String[] args) {
        final Vertx vertx = Vertx.vertx();
        final WebClient webClient = WebClient.create(vertx);

        final AccountService accountServiceProxy =
                new AccountServiceProxy(webClient, ACCOUNT_HOST, ACCOUNT_PORT);
        final DeliveryService deliveryServiceProxy =
                new DeliveryServiceProxy(webClient, DELIVERY_HOST, DELIVERY_PORT, FLEET_PORT);
        final SessionRepository sessionRepository = new InMemorySessionRepository();

        final SessionService service = new SessionServiceImpl(
                accountServiceProxy, deliveryServiceProxy, sessionRepository);

        final var controller = new SessionServiceController(service, SESSION_SERVICE_PORT);
        vertx.deployVerticle(controller);
    }
}