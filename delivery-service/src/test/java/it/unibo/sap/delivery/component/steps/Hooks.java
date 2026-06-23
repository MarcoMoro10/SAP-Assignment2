package it.unibo.sap.delivery.component.steps;

import io.cucumber.java.Before;
import it.unibo.sap.delivery.component.DeliveryServiceTestContext;

/**
 * Rebuilds the delivery-service wiring (fresh deliveries + freshly seeded fleet) before each
 * scenario so the black-box component scenarios stay independent.
 */
public class Hooks {

    @Before
    public void resetState() {
        DeliveryServiceTestContext.get().reset();
    }
}
