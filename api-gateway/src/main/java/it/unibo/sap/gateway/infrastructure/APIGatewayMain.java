package it.unibo.sap.gateway.infrastructure;

import io.vertx.core.Vertx;
import io.vertx.ext.web.client.WebClient;
import it.unibo.sap.gateway.application.AccountService;
import it.unibo.sap.gateway.application.DeliveryService;
import it.unibo.sap.gateway.application.SessionRepository;
import it.unibo.sap.gateway.application.SessionService;
import it.unibo.sap.gateway.application.SessionServiceImpl;

/**
 * Entrypoint of the api-gateway (formerly SessionServiceMain). Wires the downstream proxies and the
 * in-memory session repository behind the {@link SessionService}, then deploys the
 * {@link APIGatewayController} as the single client-facing entrypoint.
 *
 * <p>Hosts/ports are still hard-coded here (localhost). STEP 8 will switch them to the Docker DNS
 * service names read from environment variables.
 */
public class APIGatewayMain {

    static final int GATEWAY_PORT = 8080;

    static final String ACCOUNT_HOST = "localhost";
    static final int ACCOUNT_PORT = 9000;
    static final String DELIVERY_HOST = "localhost";
    static final int DELIVERY_PORT = 9002;
    static final int FLEET_PORT = 9003;

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

        final var controller = new APIGatewayController(service, GATEWAY_PORT);
        vertx.deployVerticle(controller);
    }
}
