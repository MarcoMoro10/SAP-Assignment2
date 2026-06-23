package it.unibo.sap.account.component.steps;

import io.cucumber.java.Before;
import it.unibo.sap.account.component.AccountServiceTestContext;

/**
 * Resets the in-memory account store (and re-seeds the admin) before each scenario so the
 * black-box component scenarios stay independent of one another.
 */
public class Hooks {

    @Before
    public void resetState() {
        AccountServiceTestContext.get().reset();
    }
}
