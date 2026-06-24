package it.unibo.sap.gateway.infrastructure;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit test of the {@link Env} helper: with an UNSET variable it falls back to the default, so the
 * gateway keeps its current localhost hosts/ports when no env is provided (STEP 8 — service discovery
 * preparation). Uses a variable name that is guaranteed not to exist in the environment.
 */
class EnvTest {

    private static final String UNSET = "SAP_A2_DEFINITELY_UNSET_VAR_8080";

    @Test
    void getFallsBackToDefaultWhenVariableIsUnset() {
        assertEquals("localhost", Env.get(UNSET, "localhost"));
    }

    @Test
    void getIntFallsBackToDefaultWhenVariableIsUnset() {
        assertEquals(9000, Env.getInt(UNSET, 9000));
        assertEquals(8080, Env.getInt(UNSET, 8080));
    }

    @Test
    void gatewayMainDefaultsMatchTheCurrentLocalValues() {
        assertEquals("localhost", APIGatewayMain.DEFAULT_ACCOUNT_HOST);
        assertEquals(9000, APIGatewayMain.DEFAULT_ACCOUNT_PORT);
        assertEquals("localhost", APIGatewayMain.DEFAULT_DELIVERY_HOST);
        assertEquals(9002, APIGatewayMain.DEFAULT_DELIVERY_PORT);
        assertEquals(9003, APIGatewayMain.DEFAULT_FLEET_PORT);
        assertEquals(8080, APIGatewayMain.DEFAULT_GATEWAY_PORT);
        assertEquals("localhost", APIGatewayMain.DEFAULT_GATEWAY_PUBLIC_HOST);
    }
}
