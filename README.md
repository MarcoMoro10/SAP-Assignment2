# SAP Assignment 02 — Shipping on the Air with Patterns

Evoluzione dell'Assignment 01 (drone delivery system) con l'applicazione di un sottoinsieme
di microservices pattern: API Gateway, Observability (Health Check + Application Metrics),
Event Sourcing, Circuit Breaker, Service Discovery. Deployment a container (Docker + Compose),
strategia di testing su tutta la test pyramid, e due Quality Attribute Scenario osservabili.

> La fonte di verità con tutte le decisioni architetturali e la roadmap è in
> [`doc/ASSIGNMENT-02-SOURCE-OF-TRUTH.md`](doc/ASSIGNMENT-02-SOURCE-OF-TRUTH.md).

## Architettura

Tre microservizi deployabili + un modulo di test e2e:

| Modulo | Ruolo | Porta REST | Metrics |
|---|---|---|---|
| `account-service` | Users Context (registrazione/login) | 9000 | — |
| `delivery-service` | Deliveries + Fleet interno | 9002 (delivery) + 9003 (admin) | 9400 |
| `api-gateway` | Unico entrypoint client (assorbe la facciata) | 8080 | 9401 |
| `system-end-to-end` | Test e2e (no deploy) | — | — |

Prometheus su 9090. Ogni servizio e' un progetto Maven autonomo (opzione B): i building block
DDD/hexagonal (`common`) sono duplicati in ciascun servizio; non c'e' parent POM.

## Stack

Vert.x 4.5.13 · Java 25 · Maven · Docker / Docker Compose · Prometheus.

## Build & Run

Build di un singolo servizio:
```
cd account-service
mvn package
```

Avvio dell'intero sistema (dopo aver implementato i Dockerfile e il compose — STEP 9):
```
docker compose up --build
```

## Testing

- **Unit / Integration / Component**: dentro `src/test` di ogni servizio (Cucumber per i component).
- **End-to-end**: nel modulo `system-end-to-end` (user journey + PerformanceTest + CircuitBreakerTest),
  eseguiti contro il sistema avviato con Docker.

## Documentazione

- `doc/ASSIGNMENT-02-SOURCE-OF-TRUTH.md` — decisioni architetturali e roadmap.
- `doc/ubiquitous_language.md`, `doc/user_stories.md` — contesto di dominio.
- OpenAPI di ciascun servizio in `<servizio>/doc/`.
