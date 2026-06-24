# Progetto: SAP Assignment 02 — Shipping on the Air with Patterns

La **fonte di verità** completa è in `doc/ASSIGNMENT-02-SOURCE-OF-TRUTH.md`.
LEGGILA prima di agire su qualsiasi step.

## Vincoli fissi (NON modificare senza chiedere)
- **Framework:** Vert.x **4.5.13**
- **Java:** **25** ovunque, anche nelle base image Docker (`eclipse-temurin-25`)
- **Build:** Maven multi-modulo **opzione B** = ogni servizio è un progetto Maven AUTONOMO,
  con `common` (ddd + hexagonal) DUPLICATO al suo interno. **NIENTE parent POM. NIENTE modulo `common` condiviso a livello root.**
- **Architettura:** esagonale + DDD. Il dominio non dipende da application/infrastructure (verificato da ArchUnit).
- **Fleet** resta un package INTERNO al `delivery-service` (`it.unibo.sap.delivery.domain.fleet` / `infrastructure.fleet`). NON estrarlo come servizio.
- **session-service ELIMINATO:** la sua logica di facciata/orchestrazione confluisce nell'`api-gateway`.

## Architettura target (3 microservizi deployabili + 1 modulo test)
- `account-service/`  — Users Context. REST porta **9000**.
- `delivery-service/` — Deliveries + Fleet interno. REST **9002** (delivery) + **9003** (admin/fleet). Metrics **9400**.
- `api-gateway/`      — unico entrypoint client. REST **8080**. Metrics **9401**. Assorbe il session-service.
- `system-end-to-end/` — SOLO test e2e (no main, no Dockerfile).
- Prometheus **9090**.

## Pattern da implementare
API Gateway · Health Check API (tutti i servizi, `/api/v1/health`) · Application Metrics (Prometheus, NO Grafana) ·
Event Sourcing (delivery-service, event store su file) · Circuit Breaker (proxy del gateway, soglia 50%, timeout 10s) ·
Service Discovery (Docker DNS, i proxy usano i nomi logici dei servizi).

## Regola di lavoro
- Lavora **UNO STEP ALLA VOLTA** seguendo la §7 della fonte di verità.
- Dopo ogni step **FERMATI** e indica i comandi per verificare il Definition of Done (DoD).
- **NON** avviare lo step successivo senza conferma esplicita.
- A fine step proponi un commit git con messaggio chiaro (es. "STEP 2: extract autonomous account-service").
- Su step delicati (WebSocket relay, Event Sourcing) mostra prima un piano/diff, poi applica dopo l'ok.

## Note tecniche da ricordare (insidie del codice A1)
- Il `delivery-service` deploya TRE Verticle: `DeliveryServiceController`, `FleetMonitoringController` (porta separata), `VertxSchedulerVerticle`.
- `GeocodingService` è un fake deterministico (hash SHA-256), nessuna rete esterna: ok in Docker e nei test.
- Event Sourcing: persistere gli eventi di `Delivery` SENZA rompere la catena telemetria in-process
  (`FleetModule` → `DroneEventHandlerSink` → `DroneEventHandler`). Persistenza-a-eventi e notifica-a-eventi sono piani separati.
- Esistono DUE classi `Coordinates` distinte (deliveries e fleet): vanno tenute separate, è coerenza DDD.
- WebSocket: il relay client↔gateway↔delivery usa `vertx.createWebSocketClient()` (esiste in Vert.x 4.5.13).
