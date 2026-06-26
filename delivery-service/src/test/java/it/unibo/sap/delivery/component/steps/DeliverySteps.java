package it.unibo.sap.delivery.component.steps;

import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import it.unibo.sap.delivery.component.DeliveryServiceTestContext;

import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
    private JsonArray lastFleet = new JsonArray();
    private JsonArray lastScheduling = new JsonArray();
    private String createdDeliveryId;

    @When("I create an immediate delivery of weight {string} kg from {string} to {string} as {string}")
    public void createImmediate(final String weight, final String from, final String to, final String sender) {
        create(weight, from, to, sender, true, null);
    }

    @When("I create an immediate delivery of weight {string} kg from {string} to {string} as {string} without a deadline")
    public void createImmediateWithoutDeadline(final String weight, final String from, final String to,
                                               final String sender) {
        final JsonObject body = new JsonObject()
                .put("senderId", sender)
                .put("weight", Double.parseDouble(weight))
                .put("startingPlace", address(from))
                .put("destinationPlace", address(to))
                .put("immediate", true);
        post("/api/v1/deliveries", body);
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

    @When("I cancel that delivery as {string}")
    public void cancelThatDelivery(final String sender) {
        cancel(createdDeliveryId, sender);
    }

    @Then("the cancellation succeeds")
    public void cancellationSucceeds() {
        assertEquals(200, lastStatus);
        assertEquals("CANCELLED", lastBody.getString("status"));
    }

    @Then("the cancellation is rejected because the delivery is in flight")
    public void cancellationRejectedInFlight() {
        assertEquals(409, lastStatus);
        assertEquals("Delivery cannot be cancelled once in flight", lastBody.getString("error"));
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

    @When("the admin requests the fleet view")
    public void adminRequestsFleetView() {
        final CompletableFuture<JsonArray> done = new CompletableFuture<>();
        ctx.webClient()
                .get(ctx.adminPort(), ctx.host(), "/api/v1/admin/fleet")
                .send(ar -> {
                    if (ar.succeeded()) {
                        lastStatus = ar.result().statusCode();
                        done.complete(safeJsonArray(ar.result().bodyAsString()));
                    } else {
                        done.completeExceptionally(ar.cause());
                    }
                });
        try {
            lastFleet = done.get(10, TimeUnit.SECONDS);
        } catch (final Exception e) {
            throw new IllegalStateException("HTTP call to admin fleet view failed", e);
        }
    }

    @Then("the fleet view lists {int} drones")
    public void fleetViewListsDrones(final int count) {
        assertEquals(200, lastStatus);
        assertEquals(count, lastFleet.size());
    }

    @Then("every drone in the fleet view reports a position and a status")
    public void everyDroneReportsPositionAndStatus() {
        for (int i = 0; i < lastFleet.size(); i++) {
            final JsonObject drone = lastFleet.getJsonObject(i);
            assertNotNull(drone.getString("droneId"), "drone must have an id");
            assertNotNull(drone.getString("status"), "drone must have a status");
            assertNotNull(drone.getJsonObject("position"), "drone must report a position");
        }
    }

    @Then("all drones in the fleet view are {string}")
    public void allDronesAre(final String status) {
        for (int i = 0; i < lastFleet.size(); i++) {
            assertEquals(status, lastFleet.getJsonObject(i).getString("status"));
        }
    }

    @When("the admin requests the scheduling view")
    public void adminRequestsSchedulingView() {
        final CompletableFuture<JsonArray> done = new CompletableFuture<>();
        ctx.webClient()
                .get(ctx.adminPort(), ctx.host(), "/api/v1/admin/scheduling")
                .send(ar -> {
                    if (ar.succeeded()) {
                        lastStatus = ar.result().statusCode();
                        done.complete(safeJsonArray(ar.result().bodyAsString()));
                    } else {
                        done.completeExceptionally(ar.cause());
                    }
                });
        try {
            lastScheduling = done.get(10, TimeUnit.SECONDS);
        } catch (final Exception e) {
            throw new IllegalStateException("HTTP call to admin scheduling view failed", e);
        }
    }

    @Then("the scheduling view lists that delivery with a scheduled slot")
    public void schedulingViewListsThatDelivery() {
        assertEquals(200, lastStatus);
        JsonObject entry = null;
        for (int i = 0; i < lastScheduling.size(); i++) {
            final JsonObject s = lastScheduling.getJsonObject(i);
            if (createdDeliveryId.equals(s.getString("deliveryId"))) {
                entry = s;
                break;
            }
        }
        assertNotNull(entry, "expected the scheduled delivery to appear in the scheduling view");
        assertNotNull(entry.getString("scheduledAt"), "expected a scheduled slot for the delivery");
        assertEquals("SCHEDULED", entry.getString("status"));
    }

    @Then("at least one drone is {string} and carrying a package")
    public void atLeastOneDroneIsCarrying(final String status) {
        boolean found = false;
        for (int i = 0; i < lastFleet.size(); i++) {
            final JsonObject drone = lastFleet.getJsonObject(i);
            if (status.equals(drone.getString("status")) && Boolean.TRUE.equals(drone.getBoolean("carryingPackage"))) {
                found = true;
                break;
            }
        }
        assertTrue(found, "expected at least one drone in status " + status + " carrying a package");
    }

    private void create(final String weight, final String from, final String to,
                        final String sender, final boolean immediate, final String scheduledAt) {
        final JsonObject body = new JsonObject()
                .put("senderId", sender)
                .put("weight", Double.parseDouble(weight))
                .put("startingPlace", address(from))
                .put("destinationPlace", address(to))
                .put("immediate", immediate)
                .put("deadlineMinutes", 60);
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

    private void cancel(final String deliveryId, final String sender) {
        post("/api/v1/deliveries/" + deliveryId + "/cancel", new JsonObject().put("senderId", sender));
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

    private static JsonArray safeJsonArray(final String body) {
        try {
            return body == null || body.isBlank() ? new JsonArray() : new JsonArray(body);
        } catch (final RuntimeException e) {
            return new JsonArray();
        }
    }
}
