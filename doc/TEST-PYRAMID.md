# Testing pyramid — certificazione per servizio (STEP 12)

Mappa "**1 esempio canonico per livello**" da citare nel report (§5.3 della fonte di verità).
Tutti i livelli girano in fase `test` con **Surefire al default** (nessun Failsafe, nessun profilo
separato): le suite Cucumber sono eseguite come normali test JUnit Platform.

## Verde reale (eseguito, non solo compilato)

| Servizio | `mvn test` | di cui scenari Cucumber eseguiti |
|---|---|---|
| account-service  | **25** test, 0 fail | **6** scenari (1+ successo **e** 1+ fallimento) |
| delivery-service | **54** test, 0 fail | **10** scenari (1+ successo **e** 1+ fallimento) |
| api-gateway      | **31** test, 0 fail | — (nessun component: scelta allineata, vedi sotto) |

Gli scenari eseguiti sono > 0 in entrambi i servizi con `.feature`: niente "falso verde" da glue o
resource sbagliati. Account include p.es. *Successful registration* (successo) e *Registration fails
with an already used username* / *Login fails with a wrong password* (fallimento); delivery include
*Successful immediate delivery creation* (successo) e *Delivery rejected because the package is too
heavy* / *...invalid address* (fallimento).

## Esempio canonico per livello

| Livello | account-service | delivery-service | api-gateway | Motivazione |
|---|---|---|---|---|
| **Unit — solitary** (collaboratori sostituiti da doppi) | `AccountServiceImplTest` (repo in-memory fake) | `DeliveryServiceImplTest` (fake di repository, fleet, geocoding, tracking) | `SessionServiceImplTest` (porte account/delivery mockate, session-repo in-memory) | Logica applicativa isolata da HTTP/file/rete: i collaboratori sono test double. |
| **Unit — sociable** (collaboratori reali) | `AccountDomainTest` (entity `Account` + `Role` reali) | `DeliveryAggregateTest` (aggregate `Delivery` + VO reali: `Package`, `Location`, `Coordinates`, `DeliveryRequest`) | *(dominio sottile: vedi nota gateway)* | Oggetti di dominio testati con i loro veri VO/entity, senza mock. |
| **Integration** (adapter ↔ tecnologia reale, no app intera) | `AccountServiceHealthTest` (controller Vert.x via HTTP reale) · *persistenza:* `FileBasedAccountRepositoryTest` (round-trip su file reale) | `EventSourcedDeliveryRepositoryTest` (event store su file reale) · `DeliveryServiceHealthTest` (HTTP reale) | `DeliveryServiceProxyIntegrationTest` (proxy ↔ stub HTTP del delivery-service) | Un adapter contro la sua tecnologia reale (HTTP/file), senza orchestrare l'intero servizio. |
| **Component** (black-box via API, Cucumber) | `RunAccountComponentTest` + `account_registration_and_access.feature` (6 scenari) | `RunDeliveryComponentTest` + `create_and_track_delivery.feature` (10 scenari) | **— assente per scelta** | Comportamento end-to-end del singolo servizio via API pubblica. |
| **ArchUnit** (regole esagonali/clean, per servizio) | `ArchitectureTests` | `ArchitectureTests` | `ArchitectureTests` | Dominio non dipende da application/infrastructure; porte/adapter al posto giusto. |
| **End-to-end** (sistema dockerizzato) | → modulo `system-end-to-end` (**STEP 13**) | → `system-end-to-end` (**STEP 13**) | → `system-end-to-end` (**STEP 13**) | User journey + `PerformanceTest` + `CircuitBreakerTest` dopo `docker compose up`. |

## Note di certificazione

- **Gateway senza component test (scelta, non lacuna).** Il gateway è coperto a **livello integration**
  (`DeliveryServiceProxyIntegrationTest`, `AccountServiceCircuitBreakerTest`,
  `GatewayHealthIntegrationTest`, `TrackingRelayIntegrationTest`, `PrometheusGatewayMetricsTest`).
  È la stessa collocazione adottata dal materiale del prof (lab-activity-09) e dai colleghi
  (FilippoPracucci/sap-assignment-02), dove il loro `DeliveryServiceProxyTest` corrisponde al mio
  `DeliveryServiceProxyIntegrationTest`. La riga "Component" del gateway resta volutamente vuota.
- **Dominio gateway sottile → niente sociable-unit dedicato.** Il gateway non ha un aggregate ricco
  (solo `Session`/`SessionId`); il suo cuore è l'orchestrazione, coperta in solitary
  (`SessionServiceImplTest`). In aggiunta, `CircuitBreakerTest` è uno unit isolato puro della macchina
  a stati del Circuit Breaker (clock iniettato), senza I/O.
- **Nessun `.feature` o step orfano.** Esistono esattamente due feature, ognuna referenziata dal suo
  runner: `account_registration_and_access.feature` ← `RunAccountComponentTest`;
  `create_and_track_delivery.feature` ← `RunDeliveryComponentTest`. Le molteplici feature A1
  (`track_delivery`, `fleet_monitoring`, `scheduling_management`, `telemetry_ingestion`) sono già
  **consolidate** negli scenari di `create_and_track_delivery.feature` (creazione, tracciamento, vista
  flotta, comparsa drone): nessuna feature morta da rimuovere.
- **Esempi aggiuntivi per livello** (non canonici ma presenti, utili al report):
  delivery integration anche su WebSocket/telemetria (`ArrivalTerminalFrameIntegrationTest`,
  `TrackingSocketCloseIntegrationTest`) e metriche (`PrometheusDeliveryMetricsTest`,
  `PrometheusGatewayMetricsTest`); delivery sociable-unit anche `DeliveryValueObjectsTest`,
  `DroneTest`, `DeliveryReplayTest` (replay event-sourced del dominio).
