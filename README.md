# SAP Assignment 2 — Shipping Platform

Sistema a microservizi (multi-modulo Maven) per la gestione di spedizioni con droni:

- **account-service** — anagrafica utenti.
- **session-service** — custode dell'identità: autentica al login ed espone l'introspezione delle sessioni.
- **delivery-service** — bounded context *Deliveries* (event-sourced) + modulo *Fleet* (droni, in-memory).
- **api-gateway** — edge router puro verso i servizi interni.
- **system-end-to-end** — test di sistema (BDD + circuit breaker + performance).

## Avvio

```bash
docker compose up -d --build
```

## Persistenza dell'event store e semantica dei volumi

Il `delivery-service` è **event-sourced** sul contesto *Deliveries*: lo stato delle consegne è
ricostruito replayando l'event store su file (`FileBasedEventStore`, path
`DELIVERY_EVENTS_FILE`, default `data/delivery-events.json`). Il file è persistito su un **named
volume** Docker (`delivery-data`), così la storia degli eventi sopravvive al riavvio del container.

Al boot, dopo la ricostruzione, il servizio applica una **recovery policy** sulle consegne attive
(vedi documentazione del delivery-service).

Anche l'`account-service` è **persistente allo stesso modo**: gli account sono scritti su file
(`FileBasedAccountRepository`, path `ACCOUNT_STORE_FILE`, default `data/accounts.json`) montato sul
named volume `account-data`. Così, dopo un riavvio, l'utente ritrova il **proprio account con lo
stesso `accountId`** e può accedere alle consegne persistite senza doversi ri-registrare. Le sessioni
del `session-service` restano invece **in-memory per design** (dopo il riavvio si rifà il login e si
ottiene una nuova `sessionId`, ma lo stesso `accountId`).

| Comando | Effetto sui dati |
|---|---|
| `docker compose down` | Ferma tutto ma **conserva** i named volume (`account-data`, `delivery-data`): al prossimo `up` gli account sono ricaricati e il delivery-service fa recovery dalla storia degli eventi. |
| `docker compose down -v` | Rimuove i named volume: **reset totale**, sistema pulito (nessun account, nessuna delivery). Usare per test puliti su Postman. |

Override manuale (bypassano il volume, azzerano lo store allo start):

```bash
DELIVERY_RESET_STORE=true docker compose up -d
ACCOUNT_RESET_STORE=true docker compose up -d
```
