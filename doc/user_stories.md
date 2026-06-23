# Shipping on the Air — User Stories

**Drone Delivery System**

Personas:
- **Sender**: a registered user who originates a delivery request.
- **Admin**: an already-registered user with administrative privileges who monitors the fleet and **observes** delivery scheduling. The Admin account is pre-loaded: it does not register, it only logs in.

> **Note on the drone.** In this prototype the drone is **not an external actor**. It is simulated internally by the delivery-service (a Virtual Thread per active drone), and its position/status updates are **internal domain events** of the Fleet Context (`Position Updated`, `Drone Arrived`, `Drone Out Of Service`), propagated in-process via the Observer pattern, not telemetry ingested over the network from an external device. There is therefore no "Drone" persona and no telemetry user story directed at an external system; the drone's behaviour is captured by the delivery lifecycle and fleet-monitoring stories below.

---

## Registration and Access

```
As a new user,
I want to register an account on the delivery service
so that I can request drone deliveries.
```

```
As a registered Sender,
I want to log into the system
so that I can create deliveries and track them.
```

```
As an Admin,
I want to log into the system with my existing (pre-loaded) account
so that I can monitor the fleet and review the scheduled deliveries.
```

---

## Delivery Request Creation

```
As a logged-in Sender,
I want to create a delivery request specifying pickup location and destination
so that my package can be shipped from one place to another.
```

```
As a logged-in Sender,
I want to specify the weight of my package
so that the system can assign a drone able to carry it.
```

```
As a logged-in Sender,
I want to choose between immediate or scheduled delivery
so that my package is picked up either now or at a date/time I decide.
```

```
As a logged-in Sender,
I want to define a maximum delivery time (deadline)
so that I am only committed to a shipment that arrives within my deadline.
```

```
As a logged-in Sender,
I want to know immediately whether my request is accepted or rejected when I submit it
so that I do not commit to a shipment the system cannot fulfil.
```

---

## Delivery Tracking

```
As a logged-in Sender,
I want to track my delivery in real time
so that I know the current position of my package.
```

```
As a logged-in Sender,
I want to see the estimated time remaining for my delivery
so that I know when the package will arrive.
```

---

## Delivery Cancellation

```
As a logged-in Sender,
I want to cancel a delivery request before it is in flight
so that I can stop a shipment I no longer need.
```

> **Scope note.** Cancellation covers both the *early* withdrawal of a request still in the request/validation phase (before any drone is reserved or assigned) and the *late* cancellation of a `SCHEDULED`/`ASSIGNED` delivery before its flight starts; the latter triggers the release of the drone reservation in the Fleet context. A delivery already `IN_PROGRESS` cannot be cancelled by the sender. The forced termination of an in-flight delivery (`ABOLISHED`) is **system-initiated**, not a sender action, and is therefore not modelled as a user story.

---

## Fleet Monitoring (Admin)

```
As an Admin,
I want to monitor the position of every drone in real time
so that I have an overview of the fleet on the map.
```

```
As an Admin,
I want to see the operational status of every drone, including whether it is carrying a package,
so that I can supervise fleet operations.
```

---

## Scheduling (Admin observes; the system schedules automatically)

> **Note on scheduling.** Scheduling is **automatic**: a validated scheduled delivery is planned by the system. The Admin does **not** create, move or reassign slots, the Admin's role on scheduling is **observational**.

```
As an Admin,
I want to view the deliveries scheduled for each drone,
so that I have an overview of the planned daily route of every drone.
```

```
As the system owner,
I want a scheduled delivery to automatically reserve a drone for the requested time slot
so that the drone is available to pick up the package when the Sender requested it.
```

---

## Quality Features (Non-Functional)

```
As a Sender,
I want to use the system from any modern web browser
so that I can request deliveries without installing anything.
```

```
As a Sender,
I want the tracking view to reflect drone movement with low latency
so that the position and the estimated time remaining I see are trustworthy.
```

```
As the system owner,
I want each bounded context to keep clear logical boundaries
so that the domain stays decoupled and the system can evolve (e.g. extracting the Fleet context as its own service in a later iteration) without rewriting the core.
```

```
As the system owner,
I want the in-process domain events that drive tracking and monitoring to stay responsive as the number of active drones grows
so that real-time tracking and fleet monitoring remain trustworthy.
```

```
As a user,
I want my credentials and my deliveries to be protected
so that only I (or an authorised Admin) can access my data.
```