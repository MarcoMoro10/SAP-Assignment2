package system.steps;

import io.cucumber.java.Before;
import io.cucumber.java.en.Given;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Wires the dockerized system into the Cucumber lifecycle. A {@code @Before} hook ensures the system is
 * up (idempotent, once per JVM — see {@link Setup}); the "system is running" Given asserts it.
 */
public class SetupSteps extends Setup {

    @Before
    public void ensureUp() {
        ensureSystemUp();
    }

    @Given("the system is running")
    public void theSystemIsRunning() {
        assertThat(isGatewayHealthy()).as("the gateway must answer health on :%d", GATEWAY_PORT).isTrue();
        assertThat(awaitAccountBreakerClosed())
                .as("circuit breaker still open after CircuitBreakerTest: system not ready for journeys")
                .isTrue();
    }
}
