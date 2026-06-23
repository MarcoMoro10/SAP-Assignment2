package it.unibo.sap.acceptance.steps;

import io.cucumber.java.Before;
import it.unibo.sap.acceptance.support.TestServices;
import it.unibo.sap.acceptance.support.World;
/**
 * Cucumber lifecycle hooks. Runs before every scenario to guarantee determinism:
 * the seeded fleet is restored (drones do not leak status across scenarios) and the
 * shared {@link World} is cleared. This removes the order-dependence that previously
 * let "no drone available" pass by accident.
 */
public class Hooks {

    @Before
    public void resetState() {
        TestServices.get().resetFleet();
        TestServices.get().resetAccounts();
        TestServices.get().resetDeliveries();
        World.get().reset();
    }
}
