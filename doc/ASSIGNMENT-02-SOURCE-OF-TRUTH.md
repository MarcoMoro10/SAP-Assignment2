# Assignment #02 — Shipping on the Air with Patterns
## Fonte di verità per sviluppo e implementazione

> **Scopo.** Riferimento unico per trasformare l'Assignment 1 nell'Assignment 2. Contiene punti della consegna, scelte architetturali **decise e validate**, tecnologie e passi concreti.
>
> Base: il tuo Assignment 1 (`MarcoMoro10/SAP-Assignment1`). Riferimenti prof: `lab-activity-08` (API Gateway + Observability + Prometheus), `lab-activity-09` (testing pyramid). Report colleghi come esempio.
>
> **STATO: tutte le decisioni architetturali sono chiuse.** Si passa all'implementazione (roadmap §7). Alcune scelte sono ora **confermate dal codice reale del lab-08** del prof (clonato e ispezionato).

---

## 0. Sintesi della consegna

Pattern **obbligatori**: (1) **API Gateway** — unico entrypoint; (2) **Observability**: **Health Check API** + **Application Metrics**; (3) **Event Sourcing** su **un** servizio; (4) **Due pattern a scelta**.

Trasversali: **Deployment** con **container** (Docker + Compose); **Testing** con **un esempio per livello** della pyramid; discutere **come l'observability implementa i QA Scenario** con **due esempi**.

Deliverable: **sorgenti + report** (descrizione architetturale dei pattern), repo GitHub organizzata.

> **Checklist:** API Gateway ✔ · Health Check ✔ · Application Metrics ✔ · Event Sourcing (1 servizio) ✔ · 2 pattern extra ✔ · container+compose ✔ · 1 test/livello (×4) ✔ · 2 QA scenario observability ✔ · report ✔.

---

## 1. Stato di partenza (Assignment 1)

Dominio **Shipping on the Air** (drone delivery), 3 bounded context: **Users**, **Deliveries**, **Fleet**.

**3 microservizi A1:**
- **account-service** — Users Context. Registrazione/login. Repository su file. Admin precaricato.
- **session-service** — orchestratore sottile + entrypoint client. Login, comandi delivery (via `DeliveryServiceProxy`), query admin. Nessuna logica di dominio. Sessioni non persistite.
- **delivery-service** — Deliveries + **Fleet come modulo interno**. Ciclo di vita delivery, Scheduler automatico (driving adapter a timer), tracking WebSocket diretto client↔delivery, droni Virtual Thread, eventi in-process via Observer.

Tech già in casa: **Vert.x 4.5.13** (core/web/web-client), **Jackson 2.18**, **JUnit 5.11**, **Cucumber 7.21**, **vertx-junit5**, **ArchUnit 1.4.1**. Hexagonal + DDD con modulo **`common`** (building block). Acceptance test Cucumber (`features/`, step, `World`, `TestServices`) + test architetturali ArchUnit.

---

## 2. DECISIONI ARCHITETTURALI VALIDATE

### 2.1 Struttura repo → MULTI-MODULO ✅

Si passa a **multi-modulo Maven**, una cartella per servizio (modello lab-08).

```
SAP-Assignment2/
├── account-service/      (pom.xml, Dockerfile, doc/, src/ … incluso it.unibo.sap.common duplicato)
├── delivery-service/     (pom.xml, Dockerfile, doc/, src/ … common duplicato)   ← Fleet interno
├── api-gateway/          (pom.xml, Dockerfile, doc/, src/ … common duplicato)   ← NUOVO, assorbe la facciata
├── system-end-to-end/    (pom.xml, src/test/ … solo test e2e, NO main, NO Dockerfile)  ← NUOVO (vedi §5)
├── doc/                  (ubiquitous_language.md, user_stories.md — doc condivisa, vedi §7quater)
├── docker-compose.yml
├── prometheus.yml
└── README.md
```
> Opzione B: nessun `common/` né `pom.xml` a livello root. Ogni servizio è autonomo e self-contained (vedi nota sotto). Il modulo `system-end-to-end/` è di soli test (§5).

> ✅ **DECISO: opzione B (modello prof + colleghi).** Niente parent POM, niente modulo `common` condiviso. Ogni servizio è un progetto Maven **autonomo** con il proprio `pom.xml` completo e **duplica** al suo interno i building block `common/ddd` e `common/hexagonal`. Confermato da DUE fonti: il lab-08 del prof e la repo dei tuoi colleghi (`FilippoPracucci/sap-assignment-02`), che hanno esattamente `account-service/`, `delivery-service/`, `api-gateway/` autonomi con `doc/` ciascuno + `system-end-to-end/` separato. Motivo: ogni Dockerfile fa build sulla *singola cartella*, quindi il servizio dev'essere self-contained.
>
> **Conseguenze pratiche:**
> - Il package `it.unibo.sap.common` (le interfacce `AggregateRoot`, `DomainEvent`, `ValueObject`, `Repository`, `InputAdapter`/`OutputAdapter`, ecc.) viene **copiato identico** dentro ogni modulo (account, delivery, gateway).
> - I building block sono interfacce stabili e piccole: la duplicazione è a basso costo e a basso rischio (non cambiano spesso).
> - Ogni `pom.xml` di modulo dichiara le *proprie* dipendenze (Vert.x, Jackson, JUnit, Cucumber, ArchUnit) — non eredita da un parent.
> - Nessuna cartella `common/` a livello root: la struttura §2.1 va letta con `common` *dentro* ogni servizio, non come modulo separato.

### 2.2 Fleet → RESTA DENTRO delivery-service ✅

**NON si stacca Fleet.** Multi-modulo = confini di *deployment*, non *logici*. Fleet resta package interno (`it.unibo.sap.delivery.domain.fleet`).

**Motivazione (per il report A2):** Deliveries e Fleet collaborano in modo **sincrono** sul cammino critico (validazione → feasibility → assegnazione drone → `begin()`); separarli introdurrebbe rete sul path e — senza broker — costringerebbe a simulare uno stream di eventi cross-servizio. In-process con confine logico netto (porte interne + Observer) = separazione DDD senza costo distribuito. I droni-Virtual-Thread e gli eventi `PositionUpdated`/`DroneArrived` restano in-process, coerente col requisito di bassa latenza del tracking.

> Risultato: **3 microservizi** (account, delivery, api-gateway) e **3 bounded context** di cui 2 co-locati. "Context ≠ servizio" è scelta consapevole — dirlo nel report.

### 2.3 API Gateway → ASSORBE la facciata del session-service ✅

Il nuovo **`api-gateway`** è l'unico entrypoint esterno e **assorbe orchestrazione/facciata** del session-service (modello lab-08 e colleghi).

- Il `session-service` **non sopravvive** come microservizio: routing/orchestrazione (login→account, comandi→delivery, query admin) confluiscono nel gateway.
- Proxy migrano nel gateway: `AccountServiceProxy`, `DeliveryServiceProxy` (+ gestione fleet/scheduling).
- `APIGatewayController` instrada via routing map + proxy. Espone la REST API client-facing + `GET /api/v1/health` + metrics.
- Gestione sessione (login→sessionId/ruolo/link) mantenuta nel gateway come edge function.

**Conferma dal codice lab-08:** il gateway del prof ha esattamente `APIGatewayController` + `AccountServiceProxy`/`LobbyServiceProxy`/`GameServiceProxy` + `HTTPSyncBaseProxy` + routing map (`router.route(METHOD, PATH).handler(...)`) + `healthCheckHandler`. È il template da seguire 1:1.

### 2.4 WebSocket → SI SPEZZA in due tratte ✅ (confermato dal codice del prof)

**Priorità: rispettare il pattern API Gateway.** Hai detto: spezzo se è la soluzione più pulita per il gateway. **Lo è**, e lo dimostra il codice del lab-08.

**Come fa il prof (template):**
- Lato client: `server.webSocketHandler(webSocket -> { ... })` nell'`APIGatewayController` accetta la WS dal client.
- Lato servizio: nel proxy (`GameServiceProxy`) un `WebSocketClient` interno (`vertx.createWebSocketClient().connect(wsPort, wsAddress, "/api/v1/events")`) apre la WS verso il servizio a valle.
- Il gateway fa da **relay**: inoltra i messaggi testuali tra le due tratte (`textMessageHandler` → `writeTextMessage`).

→ Per te: client↔gateway↔delivery, con il gateway che fa relay dello stream di tracking. Questo è il modello canonico e "smooth" del gateway, non un compromesso: il prof lo implementa così.

**Trade-off da annotare nel report:** nell'A1 lo stream andava diretto per non saturare l'orchestratore; ora il gateway tiene una connessione aperta per ogni utente che traccia. Per il prototipo è accettabile e in linea col pattern; documenta la consapevolezza (incapsulamento vs connessioni aperte).

### 2.5 Event Sourcing → delivery-service ✅
### 2.6 Pattern extra #1 → Circuit Breaker (nei proxy del gateway) ✅
### 2.7 Pattern extra #2 → Service Discovery (deployment-level via Docker) ✅
### 2.8 QA Scenario → i due esistenti (responsiveness + partial failures) ✅
### 2.9 Grafana → NO ✅ (Prometheus è sufficiente; eventuale extra solo se avanza tempo)
### 2.10 Health Check → su TUTTI i servizi ✅ (completezza/robustezza; quello del gateway è il decisivo per la valutazione)
### 2.11 Porte/naming → convenzione lab-08 ✅ (vedi tabella §4)

### 2.12 Versione Vert.x → resta **4.5.13** ✅ (DECISO)
Si mantiene **Vert.x 4.5.13** (quella dell'A1). Verificato sulla documentazione ufficiale: il `WebSocketClient` del relay del gateway (`vertx.createWebSocketClient().connect(port, host, uri, handler)`) **esiste già in 4.5.13** — il vecchio `httpClient.webSocket(...)` è solo deprecato ma il nuovo client funziona identico. Quindi si replica il pattern di relay del prof senza salire a Vert.x 5 (che porterebbe rischio di breaking change e zero punti). I colleghi usano 5.0.5, ma non è un vincolo.

### 2.13 Versione Java → **25** ovunque, anche Docker ✅ (DECISO)
Si compila e si esegue con **Java 25** (quella dell'A1). Le base image Docker usano `eclipse-temurin-25` (o l'immagine `maven` corrispondente con JDK 25), **non** la temurin-21 del lab-08. Da allineare nei Dockerfile (`maven.compiler.release` = 25 e base image coerente).

---

## 3. Pattern obbligatori — implementazione

### 3.1 API Gateway
Vedi §2.3–§2.4. Tech: Vert.x web + web-client; chiamate proxy fuori dall'event-loop; relay WebSocket. Template = `ttt-api-gateway` del lab-08. API client-facing + health + metrics, documentata in `api-gateway/doc/`.

### 3.2 Health Check API
`GET /api/v1/health` su **tutti** i servizi. Docker Compose `healthcheck` interroga l'endpoint; container unhealthy → **autoheal** (`willfarrell/autoheal:1.1.0`) via label `autoheal=true`; `restart: always` su ogni servizio.

**Template lab-08 (compose):**
```yaml
healthcheck:
  test: curl --fail http://api-gateway:8080/api/v1/health || exit 1
  interval: 1m30s
  timeout: 10s
  retries: 3
  start_period: 40s
  start_interval: 5s
labels:
  - "autoheal=true"
restart: always
```
Versione ricca: l'health verifica anche le dipendenze a valle (il gateway pinga l'health dei servizi — utile pure per la richiusura del Circuit Breaker, §3.5).

### 3.3 Application Metrics (Prometheus)
Prometheus nel compose, configurato via `prometheus.yml`. **No Grafana** (§2.9).

Metriche:
- **delivery-service** (application level): `nTotalDeliveriesCreated` (counter), `nDeliveriesOnDelivery` (gauge), `nDeliveriesDelivered` (counter).
- **api-gateway** (infrastructure level): `nTotalNumberOfRESTRequests` (counter), `totalRequestResponseTime` (→ QA responsiveness), `isAccountCircuitOpen` (→ QA partial failures).

Implementazione (adapter, tiene la metrica fuori dalla business logic — confermato dal lab-08 che ha `PrometheusControllerObserver` + `ControllerObserver`):
- delivery: `PrometheusDeliveryServiceObserver` implementa `DeliveryServiceEventObserver` → `DeliveryObserver`.
- gateway: `PrometheusControllerObserver` implementa `ControllerObserver` usato dall'`APIGatewayController`.

Tech: Prometheus Java client library.

### 3.4 Event Sourcing (delivery-service)
- `Delivery` persistito come **sequenza di eventi**, non stato.
- Aggiungi evento **`DeliveryCreated`** come primo della sequenza.
- **Event Store file-based** (`FileBasedEventStore`), coerente coi tuoi repository su file.
- Ricostruzione: nuova istanza + **replay** eventi in ordine (`apply()`).
- **Ogni** transizione emette un evento (creazione + ogni update).
- Snapshot non necessari (poche transizioni per delivery).

> Punto a favore CQRS: le tue read model (Tracking/Scheduling/Fleet Monitoring View) sono già proiezioni costruite dagli eventi — menzionalo (event sourcing → CQRS).

### 3.5 Circuit Breaker (gateway)
In **ogni proxy** del gateway: traccia successi/fallimenti; soglia fallimenti **50%** → apri il circuito; a circuito aperto rispondi subito con errore senza inoltrare; **timeout 10s** → richiesta all'**health** del servizio → se up, **richiudi**. Alimenta `isAccountCircuitOpen`.

### 3.6 Service Discovery (Docker, deployment-level)
Docker ha service registry built-in + DNS resolver interno. Ogni servizio ha un **DNS name** (nome nel compose); i proxy del gateway puntano al **nome logico**, non a IP. Serve solo: naming nel compose + DNS name nei proxy + rete condivisa. Report: 3rd-party registration + server-side discovery; pro: zero codice di discovery; contro: legato alla piattaforma (Docker).

---

## 4. Deployment (container) + porte/naming lab-08

**Docker** (un container per servizio) + **Docker Compose** (rete condivisa). Dockerfile per `account-service`, `delivery-service`, `api-gateway` (NON per `common` se opzione A; se opzione B `common` è dentro ciascuno).

**Template Dockerfile (lab-08):**
```dockerfile
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /project
COPY . /project/
RUN mvn clean package
EXPOSE <rest_port>
EXPOSE <metrics_port>
CMD ["mvn", "exec:java", "-Dexec.mainClass=<...>.Main"]
```
> Nota: tu usi Java 25 nell'A1; il prof usa temurin-21 e i colleghi compilano a 21. Allinea la base image alla tua versione Java (es. `eclipse-temurin-25`) o abbassa il target. Da decidere in fase di build.

**Due stili di compose (scegli):**
- **Lab-08:** immagini pre-buildate (`image: ttt-account-service`) — devi buildare le immagini a mano prima (`docker build`).
- **Colleghi (più comodo):** `build: context: ./account-service` — Compose builda l'immagine dal Dockerfile del servizio in automatico al `docker compose up`. **Consigliato:** un solo comando builda e avvia tutto.

Esempio stile colleghi:
```yaml
services:
  account-service:
    build:
      context: ./account-service
    image: account-service
    container_name: account-service-01
    networks: [shipping_network]
    expose: ["9000"]
    restart: always
```

**Porte (convenzione lab-08, adattata al tuo dominio):**

| Servizio | REST | Metrics | Note |
|---|---|---|---|
| account-service | 9000 | — | |
| delivery-service | 9002 (delivery) + 9003 (admin/fleet) | **9400** | DUE porte REST (vedi §7quinquies.A); espone metriche application-level |
| api-gateway | **8080** | **9401** | entrypoint client + metriche infra; healthcheck. (Risolve collisione 8080 dell'A1 — vedi §7quinquies.B) |
| prometheus | 9090 | — | scrape su :9400 e :9401 |
| autoheal | — | — | monta `/var/run/docker.sock` |

**Template `prometheus.yml` (lab-08):**
```yaml
global:
  scrape_interval: 10s
scrape_configs:
 - job_name: monitoring-delivery-service
   static_configs:
    - targets: ["delivery-service:9400"]
 - job_name: monitoring-api-gateway
   static_configs:
    - targets: ["api-gateway:9401"]
```

**Motivazione (report):** portabilità, riproducibilità, isolamento, scaling facile; Compose avvia tutto con un comando; rete condivisa abilita il service discovery DNS.

---

## 5. Testing (un esempio per livello) + folder e2e dedicata

### 5.1 Struttura: dove vivono i test (confermata da lab-09 e colleghi)
Ho ispezionato `lab-activity-09` e la repo dei colleghi (`FilippoPracucci/sap-assignment-02`). Il modello è netto e va replicato:

- **Unit, Integration, Component** → **dentro `src/test/` di ogni servizio** (account, delivery, gateway). Ogni servizio testa se stesso.
- **End-to-end** → **modulo separato a livello root**, dedicato. Prof: `ttt-game-system-end-to-end/`. Colleghi: `system-end-to-end/`. **Tu aggiungi: `system-end-to-end/`** (o `shipping-system-end-to-end/`).

```
SAP-Assignment2/
├── account-service/      └ src/test/  → unit + component (Cucumber) del servizio
├── delivery-service/     └ src/test/  → unit + integration + component + ArchitectureTests
├── api-gateway/          └ src/test/  → unit + integration (proxy)
├── system-end-to-end/    ← NUOVO MODULO solo-test (no main, no Dockerfile)
│   ├── pom.xml
│   └── src/test/
│       ├── java/system/
│       │   ├── RunCucumberTest.java
│       │   ├── steps/UserJourneyDeliveryCreationSteps.java
│       │   ├── steps/UserJourneyTrackingDeliverySteps.java
│       │   ├── steps/SetupSteps.java   (+ Setup.java)
│       │   ├── CircuitBreakerTest.java   ← QA scenario 2
│       │   └── PerformanceTest.java      ← QA scenario 1
│       └── resources/system/
│           ├── userJourneyDeliveryCreation.feature
│           └── userJourneyTrackingDelivery.feature
├── docker-compose.yml
└── prometheus.yml
```

### 5.2 Il modulo `system-end-to-end` — caratteristiche
- È un **progetto Maven di soli test**: nessun `src/main`, nessun Dockerfile, nessuna immagine. Non viene deployato.
- I test fanno **richieste esterne** verso il sistema **già up** (dopo `docker compose up`). Verificano il sistema completo come black-box dall'esterno.
- Dipendenze tipiche (dal pom dei colleghi): Cucumber, JUnit, AssertJ, Vert.x web-client (per le chiamate HTTP/WS), ArchUnit se vuoi anche check globali.
- Contiene **sia** gli user-journey Gherkin **sia** i due test QA (`PerformanceTest`, `CircuitBreakerTest`) — esattamente la collocazione scelta dai colleghi.

### 5.3 Un esempio per livello (mappa sul tuo dominio)

| Livello | Dove | Esempio sul tuo dominio | Come |
|---|---|---|---|
| **Unit** | nel servizio | Aggregate `Delivery`, entity `MutableDeliveryStatus`; `DeliveryService` con mock; controller con `DeliveryServiceMock`. | JUnit. Solitary (mock) per controller/service; sociable per aggregate/entity. |
| **Integration** | nel servizio | `DeliveryServiceProxy` (gateway) vs delivery-service, con `DeliveryServiceMock`. | JUnit + stub/mock. No app intera. |
| **Component** | nel servizio | Black-box via API: account (registrazione+login), delivery (creazione+tracciamento), successo+fallimento. | Cucumber/Gherkin (`createDelivery.feature`, `trackDelivery.feature` — stile colleghi). |
| **End-to-end** | modulo `system-end-to-end` | User journey sul sistema dockerizzato: (a) registra→login→crea; (b) traccia→status→stop. + `PerformanceTest` + `CircuitBreakerTest`. | Richieste esterne dopo `docker compose up`. |

> I tuoi `.feature` A1 (`track_delivery`, `fleet_monitoring`, `scheduling_management`, `telemetry_ingestion`) coprono già il **component** → ridistribuiscili nel `src/test` del servizio giusto. ArchUnit (`ArchitectureTests.java`) va in ogni servizio (i colleghi lo mettono come `delivery-service/src/test/java/ArchitectureTests.java`).

### 5.4 Gli user journey e2e da creare (DECISO)

Due user journey, modellati su quelli dei colleghi ma adattati al tuo dominio. Stanno in `system-end-to-end/src/test/resources/system/`. Sono test di sistema completo dopo `docker compose up`: ogni passo è una chiamata REST/WS reale verso l'**api-gateway** (l'unico entrypoint).

**User Journey 1 — Creazione delivery** (`userJourneyDeliveryCreation.feature`)
Percorso: registrazione → login → creazione delivery → recupero dettaglio. Verifica l'intera catena gateway → account → delivery.
```gherkin
Feature: User registration, login and delivery creation
  As a sender
  I want to register, login and create a delivery
  so that my package can be shipped from one place to another.

  Scenario: Successful registration, login and immediate delivery creation
    Given the system is running
    And there is no account with username "marco"
    When I register as a sender with username "marco" and password "Secret#123"
    Then I should get a confirmation that my account has been created
    When I login with username "marco" and password "Secret#123"
    Then I should get a session confirming I am logged in
    When I create a delivery of weight "2" kg from "via Emilia, 9" to "via Veneto, 5" to ship immediately
    Then I should get a confirmation that the delivery has been created and its delivery id
    When I request the detail of that delivery
    Then I should get its detail with weight "2" kg, pickup "via Emilia, 9" and destination "via Veneto, 5"
```

**User Journey 2 — Tracciamento delivery** (`userJourneyTrackingDelivery.feature`)
Percorso: (registrazione+login+creazione come precondizioni) → avvio tracking (WebSocket via gateway relay) → lettura status → stop tracking. Verifica il **relay WebSocket** client↔gateway↔delivery e i read model di tracking.
```gherkin
Feature: Delivery tracking
  As a sender
  I want to track my delivery in real time
  so that I know its current position and the estimated time remaining.

  Scenario: Successful delivery tracking and stop
    Given the system is running
    And I have registered as "marco" with password "Secret#123"
    And I have logged in as "marco"
    And I have created a delivery of weight "2" kg from "via Emilia, 9" to "via Veneto, 5" to ship immediately
    When I start tracking that delivery
    Then I should receive a confirmation that tracking has started
    When I request the current status of that delivery
    Then I should get its current status and estimated time remaining
    When I stop tracking that delivery
    Then I should receive a confirmation that tracking has stopped
```

**Perché questi due** (per il report): coprono i due verbi centrali del dominio (creare, tracciare) e insieme esercitano *tutti* i servizi e il gateway: account (registrazione/login), delivery (creazione/dettaglio/status), gateway (routing + relay WebSocket). È l'approccio "user journey" della teoria (un singolo test che ripercorre il percorso completo) invece di testare ogni funzionalità isolata.

**Nota indirizzi:** usa indirizzi che il `GeocodingService` mappa dentro il bounding box di Bologna (lat 44.45–44.55, lon 11.28–11.40). Essendo deterministico (hash SHA-256), gli stessi indirizzi danno sempre le stesse coordinate: i test sono ripetibili.

> Oltre ai due journey, nel modulo `system-end-to-end` stanno anche `PerformanceTest.java` (QA responsiveness) e `CircuitBreakerTest.java` (QA partial failures) — vedi §6.

**E l'Admin? NIENTE user journey dedicato (DECISO).** Bastano due journey sul Sender (creazione + tracciamento), come prof e colleghi. Motivo di merito, non solo di sufficienza: l'Admin è **per definizione un attore di sola osservazione** (viste read-only su flotta e scheduling, nessuna operazione che cambia stato). Uno user journey è un percorso di operazioni in sequenza — che l'Admin non ha. L'Admin è già coperto al livello **component** dai tuoi `.feature` A1 `fleet_monitoring` e `scheduling_management` (viste admin del delivery testate come black-box), che è il livello corretto per un attore read-only. *(Opzionale: se vuoi dare visibilità all'Admin nell'e2e, aggiungi al massimo uno step di verifica della vista fleet dentro il journey di tracciamento — non necessario.)*

---

## 6. Observability ↔ QA Scenarios (2 esempi)

**1 — Responsiveness (1.1):** 1000 richieste concorrenti, tempo medio < 100ms. Observability: `totalRequestResponseTime` + `nTotalNumberOfRESTRequests` sul gateway → misura + alert Prometheus. Test: `PerformanceTest` e2e.

**2 — Handling partial failures (1.2):** >50% richieste verso account falliscono → fail-fast. Observability: `isAccountCircuitOpen` espone lo stato del Circuit Breaker → Prometheus osserva/allerta. Il pattern realizza, la metrica rende verificabile. Test: `CircuitBreakerTest` e2e.

> Lega observability + circuit breaker + QA + e2e in un'unica storia: esattamente ciò che il prof vuole nel report.

---

## 7. Roadmap implementativa — step atomici (opzione B, eseguibili uno alla volta)

Ordine pensato per essere eseguito **uno step alla volta**, ciascuno verificabile prima di passare al successivo (ideale anche per Claude Code, §11). Ogni step ha un **obiettivo**, le **azioni** e un **criterio di completamento (DoD = Definition of Done)**.

### STEP 0 — Setup repo nuovo
- **Obiettivo:** repo `SAP-Assignment2` con il codice A1 come baseline, history pulita.
- **Azioni:** §7bis (clone A1 → `rm -rf .git` → init → push su repo nuovo).
- **DoD:** repo su GitHub con il codice A1 che compila ancora (`mvn -q -DskipTests package` ok), commit iniziale.

### STEP 1 — Scaffolding multi-modulo (opzione B)
- **Obiettivo:** tre cartelle-servizio autonome + cartelle di supporto, senza ancora spostare il codice.
- **Azioni:** crea `account-service/`, `delivery-service/`, `api-gateway/`, `system-end-to-end/`, `doc/` (root). Niente parent POM, niente `common/` root. Prepara un `pom.xml` scheletro in ciascun servizio (groupId `it.unibo.sap`, artifactId = nome servizio, Java release 25, dipendenze Vert.x 4.5.13 / Jackson / JUnit / Cucumber / ArchUnit copiate dal pom A1).
- **DoD:** `mvn -q package` gira in ciascuna cartella servizio (anche se vuota).

### STEP 2 — Estrai account-service (il più semplice, fa da apripista)
- **Obiettivo:** account-service autonomo e funzionante in isolamento.
- **Azioni:** sposta `it.unibo.sap.account.*` in `account-service/src/main`; **duplica** `it.unibo.sap.common.*` dentro il modulo; sposta i relativi test; aggiorna `AccountServiceMain` (porta **9000**, era 8080); aggiungi `ArchitectureTests`.
- **DoD:** `mvn package` verde nel modulo; `AccountServiceMain` parte su 9000; test unit/component del solo account passano.

### STEP 3 — Estrai delivery-service (Fleet interno)
- **Obiettivo:** delivery-service autonomo con Fleet interno, due porte REST.
- **Azioni:** sposta `it.unibo.sap.delivery.*` (incluso `domain.fleet` e `infrastructure.fleet`) in `delivery-service/src/main`; duplica `common`; sposta test (deliveries + telemetry + fleet fixtures); aggiorna `DeliveryServiceMain` (porte **9002** delivery + **9003** admin/fleet, erano 8082/8083); aggiungi `ArchitectureTests`.
- **DoD:** `mvn package` verde; `DeliveryServiceMain` avvia i tre Verticle sulle nuove porte; test di dominio/telemetria passano. *(Event Sourcing arriva dopo, qui resta il repository su file A1.)*

### STEP 4 — Crea api-gateway (assorbe il session-service)
- **Obiettivo:** unico entrypoint con routing verso account e delivery.
- **Azioni:** crea `api-gateway/src/main`; duplica `common`; migra qui la logica del session-service: `APIGatewayController` (ex `SessionServiceController`, routing map client-facing), `AccountServiceProxy` e `DeliveryServiceProxy` (ex session), gestione sessione in-memory; `APIGatewayMain` (porta **8080**); endpoint `/api/v1/health`. **Niente WebSocket ancora** (solo REST). I proxy puntano a host/porte fissi per ora (DNS arriva allo step 8).
- **DoD:** con account+delivery+gateway avviati a mano, un login e una create-delivery via gateway funzionano end-to-end (REST).

### STEP 5 — WebSocket relay nel gateway
- **Obiettivo:** tracking client↔gateway↔delivery.
- **Azioni:** nel gateway aggiungi `server.webSocketHandler(...)` lato client e un `WebSocketClient` (`vertx.createWebSocketClient().connect(9002-ws, delivery, "/...")`) lato delivery; inoltra i messaggi testuali tra le due tratte (template `GameServiceProxy` lab-08). Lato delivery, il WS server ora dialoga col gateway.
- **DoD:** avviare un tracking via gateway riceve aggiornamenti di posizione/ETR inoltrati dal delivery.

### STEP 6 — Event Sourcing nel delivery-service
- **Obiettivo:** `Delivery` persistito come eventi.
- **Azioni:** definisci `EventStore` (append/load per aggregateId) + `FileBasedEventStore`; aggiungi evento `DeliveryCreated`; `EventSourcedDeliveryRepository` che fa append degli eventi e ricostruisce via replay (`apply()`); assicura che ogni transizione emetta un evento. **Preserva** la catena telemetria in-process (§7quinquies.D) — è un piano separato.
- **DoD:** creando e poi rileggendo una delivery, lo stato è ricostruito dagli eventi; i test di dominio passano; il tracking continua a funzionare.

### STEP 7 — Health Check su tutti i servizi
- **Obiettivo:** `/api/v1/health` su account, delivery, gateway.
- **Azioni:** aggiungi l'handler health a ciascun controller (risposta healthy/unhealthy); versione ricca nel gateway che verifica i servizi a valle.
- **DoD:** `curl http://<host>:<porta>/api/v1/health` risponde su tutti e tre.

### STEP 8 — Service Discovery (DNS Docker) — preparazione
- **Obiettivo:** i proxy usano i **nomi logici** dei servizi, non IP/localhost.
- **Azioni:** sostituisci host hardcoded nei proxy del gateway con i DNS name (`account-service`, `delivery-service`) letti idealmente da variabili d'ambiente con default. (Funzionerà davvero allo step 9 dentro la rete Docker.)
- **DoD:** i proxy puntano a nomi logici; in locale puoi ancora overridare con env a `localhost`.

### STEP 9 — Dockerizzazione + Compose + rete + Service Discovery attivo
- **Obiettivo:** `docker compose up` avvia tutto il sistema.
- **Azioni:** `Dockerfile` (base image **JDK 25**) per account/delivery/gateway; `docker-compose.yml` stile `build: context: ./servizio`, rete condivisa `shipping_network`, `restart: always`, healthcheck sul gateway, servizio `autoheal`; mapping porte §4. Verifica che il DNS discovery funzioni (i proxy raggiungono i servizi per nome).
- **DoD:** `docker compose up --build` avvia i container; un user journey REST manuale via gateway funziona dentro Docker.

### STEP 10 — Application Metrics (Prometheus)
- **Obiettivo:** metriche esposte e scrapate.
- **Azioni:** Prometheus client lib nei due servizi; `PrometheusDeliveryServiceObserver` (delivery, port metriche **9400**) e `PrometheusControllerObserver` (gateway, **9401**) con le metriche di §3.3; `prometheus.yml` con scrape su `delivery-service:9400` e `api-gateway:9401`; aggiungi `prometheus` al compose (porta 9090).
- **DoD:** Prometheus su :9090 mostra le metriche; `isAccountCircuitOpen`, `totalRequestResponseTime`, i counter delivery sono visibili.

### STEP 11 — Circuit Breaker nei proxy del gateway
- **Obiettivo:** fail-fast quando un servizio a valle è giù.
- **Azioni:** in ogni proxy traccia successi/fallimenti; soglia **50%** → apri; a aperto rispondi subito con errore; **timeout 10s** → ping all'health del servizio → richiudi se up; aggiorna la metrica `isAccountCircuitOpen`.
- **DoD:** spegnendo account-service, dopo il superamento soglia il gateway risponde immediatamente; riavviandolo, dopo il timeout il circuito si richiude.

### STEP 12 — Test pyramid completa (per servizio)
- **Obiettivo:** unit + integration + component verdi in ogni servizio.
- **Azioni:** ridistribuisci/integra i test A1 nei moduli; assicura almeno un esempio per livello (vedi §5.3); ArchUnit per servizio.
- **DoD:** `mvn test` verde in ogni modulo; un esempio chiaro per ciascun livello.

### STEP 13 — Modulo system-end-to-end
- **Obiettivo:** e2e sul sistema dockerizzato.
- **Azioni:** crea `system-end-to-end/` (pom solo-test); i due user journey §5.4; `PerformanceTest` (QA responsiveness) e `CircuitBreakerTest` (QA partial failures); `Setup`/`SetupSteps` che assumono il sistema up.
- **DoD:** con `docker compose up`, i due journey e i due QA test passano.

### STEP 14 — Documentazione + diagrammi + report
- **Obiettivo:** deliverable completo.
- **Azioni:** snellisci `doc/` (§7quater: tieni Ubiquitous Language + User Stories); rifai i C&C (§7sexies); scrivi il report architetturale (pattern, deployment, testing, 2 QA scenario observability); aggiorna README con istruzioni `docker compose up` ed esecuzione test.
- **DoD:** report completo + diagrammi aggiornati + README; checklist §0 tutta spuntata.

> **Razionale dell'ordine:** prima la struttura (0-1), poi i servizi dal più semplice (2-4), poi le funzionalità di comunicazione (5-6), poi observability/robustezza (7-11) con Docker in mezzo (9) perché Service Discovery e metriche hanno senso solo nella rete container, infine i test (12-13) e la doc (14). Ogni step lascia il sistema in stato compilabile/eseguibile.

## 7bis. Repo GitHub: NUOVO repo (non fork) — come fare

Vuoi un **repo nuovo** con il progetto preso dall'A1. Per avere **history pulita** e nessun legame "Forked from", **NON usare il fork di GitHub**: il fork mantiene il collegamento all'upstream e la history dell'A1. Fai così.

### Opzione consigliata — repo nuovo, codice copiato senza history A1
```bash
# 1. Crea su GitHub un repo VUOTO chiamato SAP-Assignment2 (senza README/license)

# 2. In locale, parti da una copia pulita dell'A1 SENZA la sua history git
git clone https://github.com/MarcoMoro10/SAP-Assignment1.git SAP-Assignment2
cd SAP-Assignment2
rm -rf .git                      # stacca completamente dalla history A1

# 3. Inizializza un nuovo repo e collega il remote nuovo
git init
git add .
git commit -m "Initial import from Assignment 1 (baseline for Assignment 2)"
git branch -M main
git remote add origin https://github.com/<tuo-utente>/SAP-Assignment2.git
git push -u origin main

# 4. Da qui ristrutturi (multi-modulo, gateway, ecc.) committando l'evoluzione
```
- **Pro:** repo autonomo, history che parte dall'A2, nessun "forked from".
- In alternativa, se vuoi *conservare* la history dell'A1 nel nuovo repo, salta `rm -rf .git` e cambia solo il remote (`git remote set-url origin <nuovo>`), poi push. Ma per un assignment nuovo la history pulita è preferibile.

> Il fork classico ha senso solo se vuoi mantenere il legame con l'A1 e contribuire indietro — non è il tuo caso.

### Cosa TENERE
- Tutto il **dominio**: aggregate, entity, value object, domain event, repository, Fleet, Scheduler, Observer, TrackingSession.
- Modulo **`common`** (building block DDD/hexagonal).
- **Acceptance test Cucumber** (`features/`, step, `World`, `TestServices`) — ridistribuiti nei moduli.
- **Test architetturali ArchUnit** — replicati per modulo.
- **OpenAPI** esistenti (riallocate, §7ter).

### Cosa MODIFICARE / SPOSTARE
- single `pom.xml` → multi-modulo (opzione A o B).
- host/porte hardcoded nei proxy → DNS name (service discovery).
- WebSocket → relay in due tratte (template lab-08).
- persistenza `Delivery` → eventi (event store).
- session-service → assorbito nel gateway (non lasciare modulo orfano).

### Cosa NON tenere
- session-service come microservizio autonomo.
- main "monolitici" che lanciavano più verticle insieme.
- **Documentazione superata** (vedi §7quater).

---

## 7ter. API per microservizio (cartella `doc/` in ciascuno)

Ogni servizio espone e documenta la **propria** API in `doc/`:
- **account-service** → `account-service/doc/account-service-openapi.yaml` — `/api/v1/accounts*`.
- **delivery-service** → `delivery-service/doc/delivery-service-openapi.yaml` — `/api/v1/deliveries*`, `/api/v1/admin/*`, WebSocket tracking.
- **api-gateway** → `api-gateway/doc/gateway-openapi.yaml` — API client-facing (ex session-service: login, user-sessions, create/cancel/track, view admin) + `/api/v1/health` + metrics.

Le OpenAPI dell'A1 vanno **riallocate**: la parte client-facing → spec del gateway; la parte delivery → resta col delivery-service.

---

## 7quater. Documentazione: cosa tenere (DECISO)

Per l'A2 la documentazione di analisi si **snellisce**. **TENGO solo:**
- **Ubiquitous Language** (`doc/ubiquitous_language.md`) — resta valido e utile.
- **User Stories** (`doc/user_stories.md`) — restano valide, agganciano i test.

**NON tengo** (non rilevanti a livello di doc per l'A2):
- Domain Model (diagramma)
- Event Storming
- Use Case diagram

> Nel report A2 la parte di analisi è già consolidata nell'A1; l'A2 si concentra sui pattern. Ubiquitous Language + User Stories bastano a dare contesto di dominio e a giustificare i test. I diagrammi C&C vanno invece **rifatti** (vedi §7sexies) — sono documentazione *architetturale*, non di analisi.

---

## 7quinquies. Note tecniche del TUO sistema A1 (insidie da conoscere prima di iniziare)

Dettagli reali del tuo codice A1 che impattano l'implementazione. Letti dal sorgente, non assunti.

### A. Il delivery-service NON è un solo Verticle — ne deploya TRE
Il tuo `DeliveryServiceMain` deploya: `DeliveryServiceController` (REST delivery), `FleetMonitoringController` (REST admin fleet/scheduling, **porta separata**), `VertxSchedulerVerticle` (timer scheduling automatico). Conseguenze:
- Il delivery-service espone **DUE porte REST**: delivery (A1: 8082) e admin/fleet (A1: 8083). Vanno entrambe gestite nel `Main`, nel Dockerfile (`EXPOSE`), nel compose e nei proxy del gateway (il gateway deve sapere a quale porta/endpoint instradare le query admin fleet/scheduling).
- Lo `VertxSchedulerVerticle` (tick 1s, `assignDueScheduledDeliveries`) resta interno al delivery-service: nessun impatto di rete, ma deve continuare a girare nel container.

### B. Collisione di porte 8080 da risolvere nel rimapping
A1: account=8080, session=8081, delivery=8082, fleet-admin=8083. Lab-08: gateway=8080, account=9000. **Il gateway (8080) e l'account A1 (8080) collidono.** Nel rimapping alle porte lab-08 assegna con cura — proposta in §4: account=9000, delivery=9002 (+ admin 9003), gateway=8080, metrics 9400/9401, prometheus 9090. Aggiorna le costanti di porta in TUTTI i `Main` e proxy.

### C. GeocodingService NON fa rete (nessun problema Docker) ✅
Verificato: `GeocodingService` è un geocoder **deterministico fake** basato su hash SHA-256 (mappa `via X#numero` → coordinate in un bounding box di Bologna). Niente HTTP esterno, niente API key, niente dipendenze di rete. Funziona identico dentro un container e nei test e2e. Non serve mock né connettività verso l'esterno.

### D. Event Sourcing deve preservare il flusso telemetria in-process
La conversione del `FileBasedDeliveryRepository` a event store **non deve rompere** la catena esistente: `FleetModule` (telemetry sink) → `DroneEventHandlerSink` → `DroneEventHandler.onDronePositionUpdated/onDroneArrived` → ricalcolo ETR + update tracking. Questo flusso già emette/consuma eventi in-process via Observer. Strategia: l'event store persiste gli eventi dell'aggregate `Delivery` (lato write/persistenza); il flusso telemetria Observer resta com'è (è propagazione in-process, non persistenza). Tieni separati i due piani: **persistenza a eventi** (event store) vs **notifica a eventi** (Observer per tracking/metrics).

### E. Hai DUE classi Coordinates (deliveries + fleet) e DUE Main da fondere
- `domain.deliveries.Coordinates` (errore "Invalid coordinates") e `domain.fleet.Coordinates` (errore "Invalid position") sono **distinte di proposito** (confini di context). Mantienile separate anche in A2 — è coerenza DDD, non duplicazione da eliminare.
- `SessionServiceMain` confluisce nel nuovo `APIGatewayMain`: porta gateway + i proxy `AccountServiceProxy` e `DeliveryServiceProxy` (che già conosce host/porte di account, delivery e fleet). I proxy del session-service sono il punto di partenza dei proxy del gateway.

### F. I test usano porte dedicate (9080/9082/9083) e seeding
`TestServices` boota i servizi in-process su porte di test (account 9080, delivery 9082, fleet 9083) con `AdminSeeder` e `FleetSeeder` (DRN-1/2/3). Quando ristrutturi in moduli, gli acceptance test e i loro support (`World`, `TestServices`, `FleetTestFixture`, fixtures) vanno **ridistribuiti nel modulo del servizio testato**. Attenzione: `TestServices` oggi boota *tutti e tre* i servizi insieme (è un test multi-servizio in-process) — in A2 i component test sono per-servizio black-box, mentre questo stile multi-servizio diventa semmai e2e.

### G. ArchUnit per modulo
I test architetturali (dominio non dipende da application/infrastructure, direzione dipendenze, porte/adapter nei layer giusti) vanno **replicati in ogni modulo**, perché ogni servizio è ora autonomo e deve auto-verificare il proprio rispetto dell'esagonale.

---

## 7sexies. Modifiche ai diagrammi C&C (da rifare per l'A2)

I C&C (Component & Connector) diagram dell'A1 vanno aggiornati per riflettere la nuova architettura. Ecco, diagramma per diagramma, cosa cambia.

### Cosa SPARISCE
- **Il C&C del session-service** va eliminato come diagramma a sé: il session-service non esiste più come microservizio.

### Cosa NASCE: C&C dell'api-gateway (NUOVO diagramma)
Il diagramma più importante da creare. Deve mostrare:
- **Inbound port** `GatewayService` (o equivalente) implementata dall'adapter **`APIGatewayController`** (driving adapter REST + WebSocket server-side handler).
- **Outbound port** verso i servizi a valle: `AccountService` e `DeliveryService` (port dell'application), implementate dagli adapter **`AccountServiceProxy`** e **`DeliveryServiceProxy`** (driven adapter HTTP, eredi di `HTTPSyncBaseProxy`).
- Il **Circuit Breaker** dentro/attorno a ciascun proxy (componente che avvolge la chiamata sincrona, con stato open/closed).
- Il **relay WebSocket**: il `WebSocketClient` interno che apre la tratta gateway↔delivery, accanto al `webSocketHandler` lato client.
- L'adapter di metriche **`PrometheusControllerObserver`** che implementa `ControllerObserver` osservato dall'`APIGatewayController`.
- L'endpoint **health** `/api/v1/health`.
- La gestione **sessione** (repository in-memory delle sessioni, ex session-service).

### Cosa CAMBIA: C&C del delivery-service (aggiornare l'esistente)
Partendo dal diagramma A1 (DeliveryService inbound; DeliveryRepository + TrackingSessionEventObserver outbound; FleetModule interno), **aggiungi**:
- L'**Event Store** come nuovo outbound adapter: `DeliveryRepository` ora è implementato da un repository **event-sourced** (es. `EventSourcedDeliveryRepository`) appoggiato a un `FileBasedEventStore` (al posto di / accanto al `FileBasedDeliveryRepository`). Mostra che la persistenza è a eventi.
- L'adapter di metriche **`PrometheusDeliveryServiceObserver`** che implementa `DeliveryServiceEventObserver` → `DeliveryObserver`.
- L'endpoint **health**.
- La tratta WebSocket lato server che ora dialoga con il **gateway** (non più direttamente col client) — aggiorna l'etichetta del connettore.
- Resta invariato: Fleet interno (porte interne + Observer in-process), Scheduler, le due porte REST (delivery + admin/fleet).

### Cosa CAMBIA: C&C dell'account-service (ritocco minimo)
Sostanzialmente invariato (AccountService inbound + FileBasedAccountRepository outbound). **Aggiungi** solo: endpoint **health** e — se decidi di esporre metriche anche qui (non richiesto) — l'eventuale observer. Il connettore in ingresso ora proviene dal **gateway**, non dal session-service: aggiorna l'etichetta.

### Diagramma di sistema (C&C di alto livello / connector view)
Aggiorna (o crea) la vista d'insieme che mostra i **connettori** del nuovo sistema:
- Client → **api-gateway**: REST sincrono (tutti i comandi) + **WebSocket** (tracking).
- api-gateway → account-service: proxy REST sincrono **con circuit breaker**.
- api-gateway → delivery-service: proxy REST sincrono **con circuit breaker** + **WebSocket relay** (tracking).
- Dentro delivery-service: Deliveries ↔ Fleet in-process (porte interne + Observer), Scheduler a timer.
- **Prometheus** → scrape su delivery-service:9400 e api-gateway:9401.
- **Docker network condivisa** + **DNS service discovery** (i proxy usano i DNS name).
- **Autoheal** + healthcheck sui container.

> Suggerimento per i diagrammi: usa lo stesso strumento/stile dell'A1 per coerenza. Il connettore "circuit breaker" e quello "websocket relay" sono le due novità visive più importanti da rendere evidenti, perché sono i pattern che il prof cerca.

---

## 8. Scelte residue (tutte chiuse tranne una micro-conferma)

- **Vert.x 4.5.13** → DECISO (§2.12).
- **Java 25 ovunque, anche Docker** → DECISO (§2.13).
- **Unica micro-conferma:** numeri esatti delle due porte del delivery (proposta: **9002** delivery + **9003** admin/fleet). Confermabili allo STEP 3. Niente di bloccante.

Tutto il resto è chiuso. Si può iniziare dallo STEP 0.

---

## 9. Mappa consegna → soluzione
API Gateway §3.1 · Health Check §3.2 · Application Metrics §3.3 · Event Sourcing §3.4 (delivery) · Circuit Breaker §3.5 · Service Discovery §3.6 · container §4 · testing pyramid §5 · observability↔QA §6 · API per servizio §7ter · doc §7quater · report = tutto il documento.

---

## 10. Assetto finale A2
- **3 microservizi deployabili:** account-service, delivery-service (Fleet interno), api-gateway (`common` duplicato in ciascuno, opzione B). + modulo `system-end-to-end` (solo test).
- **3 bounded context:** Users (account), Deliveries+Fleet (delivery), entrypoint/orchestrazione nel gateway.
- **Stack:** Vert.x 4.5.13, Java 25 (anche base image Docker).
- **Pattern:** API Gateway, Health Check (tutti i servizi), Application Metrics (Prometheus, no Grafana), Event Sourcing (delivery), Circuit Breaker (gateway), Service Discovery (Docker DNS).
- **Deployment:** un container/servizio, Compose (`build: context`), rete condivisa, Prometheus, autoheal/restart, porte 9000 / 9002+9003 / 8080, metrics 9400/9401, prom 9090.
- **Testing:** unit + integration + component (Cucumber) per servizio + e2e nel modulo dedicato (2 user journey: creazione + tracciamento; + PerformanceTest + CircuitBreakerTest), ArchUnit per servizio.
- **QA scenario:** responsiveness (metriche) + partial failures (circuit breaker + metrica), testati e2e.
- **Doc:** Ubiquitous Language + User Stories + OpenAPI per servizio + C&C diagram rifatti (§7sexies).

---

## 11. Usare Claude Code sulla repo (applicare gli step uno alla volta)

Claude Code è lo strumento giusto per eseguire questi step direttamente sul codice. Idea: questo documento diventa il **contesto permanente** e tu fai eseguire **uno step alla volta**, verificando il DoD prima del successivo.

### Setup iniziale
1. **Installa Claude Code.** Serve Node.js 18+. Due metodi:
   - **Native installer** (raccomandato da Anthropic, più stabile): vedi la pagina di setup ufficiale `https://code.claude.com/docs/en/setup` (comandi per macOS/Linux/Windows).
   - **npm**: `npm install -g @anthropic-ai/claude-code` (non usare `sudo`; in caso di permessi usa nvm o una dir npm-global nel tuo home).
   La procedura cambia nel tempo: fai riferimento alla doc ufficiale `https://docs.claude.com/en/docs/claude-code/overview`.
2. **Autenticati**: al primo avvio `claude` apre il browser per il login al tuo account Anthropic (serve un piano/credito attivo).
3. Dalla cartella della repo: `cd SAP-Assignment2 && claude`. Claude Code legge i file del progetto come contesto. (`claude doctor` diagnostica problemi di setup.)
4. **Metti questo documento nella repo** (es. `doc/ASSIGNMENT-02-SOURCE-OF-TRUTH.md`) così Claude Code può leggerlo.
5. Crea un file **`CLAUDE.md`** nella root: Claude Code lo legge automaticamente a ogni sessione. Mettici un riassunto + il puntatore al source-of-truth. Esempio di contenuto:
   ```
   # Progetto: SAP Assignment 02 — Shipping on the Air with Patterns
   La fonte di verità completa è in doc/ASSIGNMENT-02-SOURCE-OF-TRUTH.md: leggila prima di agire.
   Vincoli fissi: Vert.x 4.5.13, Java 25, multi-modulo opzione B (servizi autonomi, common duplicato,
   NIENTE parent POM), Fleet interno al delivery-service, porte: account 9000, delivery 9002 + admin 9003,
   gateway 8080, metrics 9400/9401, prometheus 9090.
   Lavora UNO STEP ALLA VOLTA seguendo §7. Dopo ogni step fermati e mostra come verificare il DoD.
   Non avviare step successivi senza conferma.
   ```

### Workflow consigliato (uno step alla volta)
Per ogni step della §7, un prompt tipo:
> "Esegui lo STEP 2 della roadmap in doc/ASSIGNMENT-02-SOURCE-OF-TRUTH.md (estrazione account-service). Rispetta i vincoli in CLAUDE.md. Quando hai finito, fermati e dimmi i comandi per verificare il DoD. Non procedere allo step 3."

Principi:
- **Un task per volta**: non chiedere "fai tutto". Step atomici = revisione più facile, meno errori a cascata.
- **Verifica il DoD** dopo ogni step (compila? test verdi? servizio parte?) prima di continuare.
- **Commit per step**: chiedi a Claude Code di proporre un commit a fine step (`git commit` con messaggio chiaro, es. "STEP 2: extract autonomous account-service"). Così hai checkpoint per tornare indietro.
- **Mostra prima di applicare** su step delicati (event sourcing, websocket): chiedi un piano o una diff prima di scrivere, poi approva.
- **Lascia girare i test**: Claude Code può eseguire `mvn test` / `docker compose up` e leggere gli errori per correggersi.

### Cosa NON delegare alla cieca
- I **diagrammi C&C** (§7sexies): falli tu o con uno strumento di diagrammi; Claude Code può aiutarti a generarne una versione testuale/Mermaid ma il diagramma finale è tua responsabilità documentale.
- Il **report**: Claude Code può bozzarlo, ma rivedi che le motivazioni architetturali siano le tue e coerenti con questo documento.

### Suggerimento pratico
Procedi in quest'ordine di delega: gli step **0-3** (scaffolding + estrazioni) sono molto adatti a Claude Code (lavoro meccanico di refactoring). Gli step **5-6** (websocket relay, event sourcing) richiedono più supervisione. Gli step **9-11** (Docker, metrics, circuit breaker) sono ben definiti e delegabili con verifica del DoD.

---

*Riferimenti: appunti del corso; `lab-activity-08` (clonato e ispezionato: servizi autonomi, APIGatewayController + proxy + HTTPSyncBaseProxy, relay WebSocket via createWebSocketClient, PrometheusControllerObserver/ControllerObserver, healthcheck+autoheal, docker-compose, prometheus.yml, porte 9000/9001/9002/8080/9400/9401/9090); `lab-activity-09` (modulo system-end-to-end separato, distribuzione test per servizio); repo colleghi `FilippoPracucci/sap-assignment-02` (opzione B confermata, build:context nel compose, user journey, Vert.x 5.0.5); il tuo Assignment 1 (dominio drone delivery, Vert.x 4.5.13, Java 25, hexagonal+DDD, Cucumber, ArchUnit, 3 microservizi con Fleet interno al delivery, GeocodingService fake SHA-256, delivery a 3 Verticle).*
