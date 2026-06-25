package it.unibo.sap.gateway.infrastructure;

import io.vertx.core.Vertx;
import io.vertx.ext.web.client.WebClient;
import it.unibo.sap.gateway.application.ControllerObserver;
import it.unibo.sap.gateway.application.SessionRepository;
import it.unibo.sap.gateway.application.SessionService;
import it.unibo.sap.gateway.application.SessionServiceImpl;

public class APIGatewayMain {

    static final int DEFAULT_GATEWAY_PORT = 8080;
    static final String DEFAULT_ACCOUNT_HOST = "localhost";
    static final int DEFAULT_ACCOUNT_PORT = 9000;
    static final String DEFAULT_DELIVERY_HOST = "localhost";
    static final int DEFAULT_DELIVERY_PORT = 9002;
    static final int DEFAULT_FLEET_PORT = 9003;
    static final String DEFAULT_GATEWAY_PUBLIC_HOST = "localhost";
    static final int DEFAULT_GATEWAY_METRICS_PORT = 9401;

    public static void main(final String[] args) {
        final String accountHost = Env.get("ACCOUNT_HOST", DEFAULT_ACCOUNT_HOST);
        final int accountPort = Env.getInt("ACCOUNT_PORT", DEFAULT_ACCOUNT_PORT);
        final String deliveryHost = Env.get("DELIVERY_HOST", DEFAULT_DELIVERY_HOST);
        final int deliveryPort = Env.getInt("DELIVERY_PORT", DEFAULT_DELIVERY_PORT);
        final int fleetPort = Env.getInt("FLEET_PORT", DEFAULT_FLEET_PORT);
        final int gatewayPort = Env.getInt("GATEWAY_PORT", DEFAULT_GATEWAY_PORT);
        final String gatewayPublicHost = Env.get("GATEWAY_PUBLIC_HOST", DEFAULT_GATEWAY_PUBLIC_HOST);
        final int metricsPort = Env.getInt("GATEWAY_METRICS_PORT", DEFAULT_GATEWAY_METRICS_PORT);

        final Vertx vertx = Vertx.vertx();
        final WebClient webClient = WebClient.create(vertx);

        final ControllerObserver controllerObserver = new PrometheusControllerObserver(metricsPort);

        final CircuitBreaker accountCircuitBreaker = new CircuitBreaker();
        accountCircuitBreaker.setOnStateChange(controllerObserver::setAccountCircuitOpen);

        final AccountServiceProxy accountServiceProxy =
                new AccountServiceProxy(webClient, accountHost, accountPort, accountCircuitBreaker);
        final DeliveryServiceProxy deliveryServiceProxy =
                new DeliveryServiceProxy(webClient, deliveryHost, deliveryPort, fleetPort);
        final SessionRepository sessionRepository = new InMemorySessionRepository();

        final SessionService service = new SessionServiceImpl(
                accountServiceProxy, deliveryServiceProxy, sessionRepository);

        final var controller = new APIGatewayController(
                service, accountServiceProxy, deliveryServiceProxy, gatewayPublicHost, gatewayPort,
                controllerObserver);
        vertx.deployVerticle(controller);
    }
}
