package system;

import io.vertx.core.Vertx;
import io.vertx.core.http.WebSocket;
import io.vertx.core.http.WebSocketClient;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;
import system.steps.Setup;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Shared state and gateway driver for the end-to-end Sender journeys. A single instance (per JVM) holds
 * the Vert.x web/WebSocket clients and the scenario context (account, session, delivery, tracking) so
 * the journey step classes can hand state to each other. Every call goes through the api-gateway on
 * {@link Setup#GATEWAY_PORT}.
 */
public final class World {

    private static World instance;

    private final Vertx vertx;
    private final WebClient webClient;
    private final WebSocketClient wsClient;

    private String username;
    private String password;
    private String sessionId;
    private String deliveryId;
    private String trackingSessionId;

    private double createdWeight;
    private String createdPickup;
    private String createdDestination;

    private int lastStatus;
    private JsonObject lastBody = new JsonObject();

    private WebSocket trackingSocket;
    private final BlockingQueue<String> frames = new LinkedBlockingQueue<>();
    private volatile boolean trackingClosed;

    private World() {
        this.vertx = Vertx.vertx();
        this.webClient = WebClient.create(vertx);
        this.wsClient = vertx.createWebSocketClient();
    }

    public static synchronized World get() {
        if (instance == null) {
            instance = new World();
        }
        return instance;
    }

    public int register(final String base, final String pwd) {
        this.username = base + "-" + System.nanoTime();
        this.password = pwd;
        post("/api/v1/accounts", new JsonObject().put("username", username).put("password", password));
        return lastStatus;
    }

    public int login() {
        post("/api/v1/login", new JsonObject().put("username", username).put("password", password));
        if (lastStatus == 200) {
            this.sessionId = lastBody.getString("sessionId");
        }
        return lastStatus;
    }

    public int createImmediateDelivery(final double weight, final String pickup, final String destination) {
        this.createdWeight = weight;
        this.createdPickup = pickup;
        this.createdDestination = destination;
        final JsonObject body = new JsonObject()
                .put("weight", weight)
                .put("startingPlace", parsePlace(pickup))
                .put("destinationPlace", parsePlace(destination))
                .put("immediate", true)
                .put("deadlineMinutes", 60);
        post("/api/v1/user-sessions/" + sessionId + "/create-delivery", body);
        if (lastStatus == 201) {
            this.deliveryId = lastBody.getString("deliveryId");
        }
        return lastStatus;
    }

    public int createScheduledDelivery(final double weight, final String pickup,
                                       final String destination, final String scheduledAtIso) {
        this.createdWeight = weight;
        this.createdPickup = pickup;
        this.createdDestination = destination;
        final JsonObject body = new JsonObject()
                .put("weight", weight)
                .put("startingPlace", parsePlace(pickup))
                .put("destinationPlace", parsePlace(destination))
                .put("immediate", false)
                .put("scheduledAt", scheduledAtIso)
                .put("deadlineMinutes", 60);
        post("/api/v1/user-sessions/" + sessionId + "/create-delivery", body);
        if (lastStatus == 201) {
            this.deliveryId = lastBody.getString("deliveryId");
        }
        return lastStatus;
    }

    public int cancelDelivery() {
        post("/api/v1/user-sessions/" + sessionId + "/cancel-delivery",
                new JsonObject().put("deliveryId", deliveryId));
        return lastStatus;
    }

    public int getDeliveryDetail() {
        get("/api/v1/user-sessions/" + sessionId + "/deliveries/" + deliveryId);
        return lastStatus;
    }

    public int startTracking() {
        post("/api/v1/user-sessions/" + sessionId + "/track-delivery",
                new JsonObject().put("deliveryId", deliveryId));
        if (lastStatus == 200) {
            this.trackingSessionId = lastBody.getString("trackingSessionId");
            openTrackingSocket();
        }
        return lastStatus;
    }

    private void openTrackingSocket() {
        final CompletableFuture<WebSocket> connected = new CompletableFuture<>();
        wsClient.connect(Setup.GATEWAY_PORT, Setup.HOST, "/api/v1/track/" + trackingSessionId)
                .onSuccess(socket -> {
                    socket.textMessageHandler(frames::add);
                    socket.closeHandler(v -> trackingClosed = true);
                    socket.writeTextMessage(new JsonObject().put("deliveryId", deliveryId).encode());
                    connected.complete(socket);
                })
                .onFailure(connected::completeExceptionally);
        try {
            this.trackingSocket = connected.get(15, TimeUnit.SECONDS);
        } catch (final Exception e) {
            throw new IllegalStateException("Could not open the tracking WebSocket via the gateway", e);
        }
    }

    public String awaitFrame(final long timeoutSeconds) {
        try {
            return frames.poll(timeoutSeconds, TimeUnit.SECONDS);
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for a tracking frame", e);
        }
    }

    public void stopTracking() {
        if (trackingSocket == null) {
            return;
        }
        final CompletableFuture<Void> closed = new CompletableFuture<>();
        trackingSocket.close().onComplete(ar -> closed.complete(null));
        try {
            closed.get(10, TimeUnit.SECONDS);
            trackingClosed = true;
        } catch (final Exception e) {
            throw new IllegalStateException("Could not stop tracking", e);
        }
    }

    public boolean isTrackingClosed() {
        return trackingClosed;
    }

    public int lastStatus() {
        return lastStatus;
    }

    public JsonObject lastBody() {
        return lastBody;
    }

    public String deliveryId() {
        return deliveryId;
    }

    public String trackingSessionId() {
        return trackingSessionId;
    }

    public double createdWeight() {
        return createdWeight;
    }

    public String createdPickup() {
        return createdPickup;
    }

    public String createdDestination() {
        return createdDestination;
    }

    private void post(final String path, final JsonObject payload) {
        exchange(path, done -> webClient.post(Setup.GATEWAY_PORT, Setup.HOST, path)
                .sendJsonObject(payload, ar -> complete(done, ar, path)));
    }

    private void get(final String path) {
        exchange(path, done -> webClient.get(Setup.GATEWAY_PORT, Setup.HOST, path)
                .send(ar -> complete(done, ar, path)));
    }

    private void exchange(final String path, final java.util.function.Consumer<CompletableFuture<JsonObject>> call) {
        final CompletableFuture<JsonObject> done = new CompletableFuture<>();
        call.accept(done);
        try {
            final JsonObject res = done.get(20, TimeUnit.SECONDS);
            this.lastStatus = res.getInteger("_statusCode", 0);
            this.lastBody = res;
        } catch (final Exception e) {
            throw new IllegalStateException("Gateway call to " + path + " failed", e);
        }
    }

    private static void complete(final CompletableFuture<JsonObject> done,
                                 final io.vertx.core.AsyncResult<io.vertx.ext.web.client.HttpResponse<io.vertx.core.buffer.Buffer>> ar,
                                 final String path) {
        if (ar.succeeded()) {
            done.complete(safeJson(ar.result().bodyAsString()).put("_statusCode", ar.result().statusCode()));
        } else {
            done.completeExceptionally(ar.cause());
        }
    }

    private static JsonObject safeJson(final String body) {
        try {
            return body == null || body.isBlank() ? new JsonObject() : new JsonObject(body);
        } catch (final RuntimeException e) {
            return new JsonObject();
        }
    }

    public static JsonObject parsePlace(final String address) {
        final int comma = address.lastIndexOf(',');
        final String street = comma >= 0 ? address.substring(0, comma).strip() : address.strip();
        final int number = comma >= 0 ? Integer.parseInt(address.substring(comma + 1).strip()) : 0;
        return new JsonObject().put("street", street).put("number", number);
    }
}
