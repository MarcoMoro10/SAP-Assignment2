package it.unibo.sap.delivery.component.steps;

import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.vertx.core.json.JsonObject;
import it.unibo.sap.delivery.component.DeliveryServiceTestContext;

import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Black-box steps that drive the delivery-service over HTTP: create a delivery
 * ({@code POST /api/v1/deliveries}), read its detail ({@code GET /api/v1/deliveries/:id}) and start
 * tracking ({@code POST /api/v1/deliveries/:id/track}). The last status/body and the created
 * delivery id are kept for the assertion steps.
 */
public class DeliverySteps {

    private final DeliveryServiceTestContext ctx = DeliveryServiceTestContext.get();

    private int lastStatus;
    private JsonObject lastBody = new JsonObject();
    private String createdDeliveryId;

    @When("I create an immediate delivery of weight {string} kg from {string} to {string} as {string}")
    public void createImmediate(final String weight, final String from, final String to, final String sender) {
        create(weight, from, to, sender, true, null);
    }

    @When("I create a delivery of weight {string} kg from {string} to {string} scheduled in {string} days as {string}")
    public void createScheduled(final String weight, final String from, final String to,
                                final String days, final String sender) {
        final String slot = LocalDateTime.now().plusDays(Long.parseLong(days)).toString();
        create(weight, from, to, sender, false, slot);
    }

    @Then("the delivery is created with status {string}")
    public void deliveryCreatedWithStatus(final String status) {
        assertEquals(201, lastStatus);
        createdDeliveryId = lastBody.getString("deliveryId");
        assertNotNull(createdDeliveryId, "expected a delivery id in the response");
        assertEquals(status, lastBody.getString("status"));
    }

    @Then("a drone is assigned to the delivery")
    public void aDroneIsAssigned() {
        assertNotNull(lastBody.getString("assignedDroneId"), "expected an assigned drone id");
    }

    @When("I request the detail of that delivery as {string}")
    public void requestDetailOfThatDelivery(final String sender) {
        getDetail(createdDeliveryId, sender);
    }

    @When("I request the detail of delivery {string} as {string}")
    public void requestDetailOfDelivery(final String deliveryId, final String sender) {
        getDetail(deliveryId, sender);
    }

    @Then("the delivery detail shows status {string}")
    public void deliveryDetailShowsStatus(final String status) {
        assertEquals(200, lastStatus);
        assertEquals(status, lastBody.getString("status"));
    }

    @When("I start tracking that delivery as {string}")
    public void startTrackingThatDelivery(final String sender) {
        track(createdDeliveryId, sender);
    }

    @When("I start tracking delivery {string} as {string}")
    public void startTrackingDelivery(final String deliveryId, final String sender) {
        track(deliveryId, sender);
    }

    @Then("tracking starts successfully")
    public void trackingStartsSuccessfully() {
        assertEquals(201, lastStatus);
        assertNotNull(lastBody.getString("trackingSessionId"), "expected a tracking session id");
        assertNotNull(lastBody.getString("webSocketUrl"), "expected a websocket url");
    }

    @Then("the response status is {int} with error {string}")
    public void responseStatusWithError(final int status, final String error) {
        assertEquals(status, lastStatus);
        assertEquals(error, lastBody.getString("error"));
    }

    private void create(final String weight, final String from, final String to,
                        final String sender, final boolean immediate, final String scheduledAt) {
        final JsonObject body = new JsonObject()
                .put("senderId", sender)
                .put("weight", Double.parseDouble(weight))
                .put("startingPlace", address(from))
                .put("destinationPlace", address(to))
                .put("immediate", immediate);
        if (scheduledAt != null) {
            body.put("scheduledAt", scheduledAt);
        }
        post("/api/v1/deliveries", body);
    }

    private void getDetail(final String deliveryId, final String sender) {
        final CompletableFuture<JsonObject> done = new CompletableFuture<>();
        ctx.webClient()
                .get(ctx.deliveryPort(), ctx.host(), "/api/v1/deliveries/" + deliveryId + "?senderId=" + sender)
                .send(ar -> complete(done, ar));
        capture(done, "GET detail");
    }

    private void track(final String deliveryId, final String sender) {
        post("/api/v1/deliveries/" + deliveryId + "/track", new JsonObject().put("senderId", sender));
    }

    /** Splits "via Emilia, 9" into {street:"via Emilia", number:9}; "xxxxx" -> number 0 (invalid). */
    private static JsonObject address(final String raw) {
        final int comma = raw.lastIndexOf(',');
        if (comma < 0) {
            return new JsonObject().put("street", raw.trim()).put("number", 0);
        }
        final String street = raw.substring(0, comma).trim();
        int number = 0;
        try {
            number = Integer.parseInt(raw.substring(comma + 1).trim());
        } catch (final NumberFormatException ignored) {
            number = 0;
        }
        return new JsonObject().put("street", street).put("number", number);
    }

    private void post(final String path, final JsonObject payload) {
        final CompletableFuture<JsonObject> done = new CompletableFuture<>();
        ctx.webClient()
                .post(ctx.deliveryPort(), ctx.host(), path)
                .sendJsonObject(payload, ar -> complete(done, ar));
        capture(done, "POST " + path);
    }

    private void complete(final CompletableFuture<JsonObject> done,
                          final io.vertx.core.AsyncResult<io.vertx.ext.web.client.HttpResponse<io.vertx.core.buffer.Buffer>> ar) {
        if (ar.succeeded()) {
            final JsonObject parsed = safeJson(ar.result().bodyAsString());
            done.complete(parsed.put("_statusCode", ar.result().statusCode()));
        } else {
            done.completeExceptionally(ar.cause());
        }
    }

    private void capture(final CompletableFuture<JsonObject> done, final String what) {
        try {
            final JsonObject res = done.get(10, TimeUnit.SECONDS);
            lastStatus = res.getInteger("_statusCode", 0);
            lastBody = res;
        } catch (final Exception e) {
            throw new IllegalStateException("HTTP call failed: " + what, e);
        }
    }

    private static JsonObject safeJson(final String body) {
        try {
            return body == null || body.isBlank() ? new JsonObject() : new JsonObject(body);
        } catch (final RuntimeException e) {
            return new JsonObject();
        }
    }
}
