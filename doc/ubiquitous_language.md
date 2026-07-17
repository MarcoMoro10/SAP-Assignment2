#### Software Architecture and Platforms - a.y. 2025-2026
## Drone Delivery Service Case Study - Ubiquitous Language

### Glossary

#### General

- **Drone Delivery Service**
  - the online system that allows registered users to request, schedule and track parcel deliveries performed by a fleet of autonomous drones
  - it is organized into three bounded contexts: **Users Context**, **Deliveries Context** and **Fleet Context**
- **Read Model (projection)**
  - a query-optimized view of an aggregate, built and kept up to date from domain events, and kept separate from the write model
  - the read models of this domain are the **Delivery Tracking View**, the **Delivery Scheduling View** and the **Fleet Monitoring View**
- **Slot**
  - the unit of drone time that a **scheduled** delivery occupies: a specific future date/time on a specific drone
  - a drone holds at most one delivery per slot; a slot is **free** if no other delivery already occupies it on that drone
  - a slot exists only for scheduled deliveries: an **immediate** delivery does not occupy a slot, it takes the drone directly
- **Canonical state values**
  - **Delivery lifecycle (`DeliveryStatus`)**: `REQUESTED` → `VALIDATED` → `SCHEDULED` → `ASSIGNED` → `IN_PROGRESS` → `DELIVERED`; with the terminal off-paths `REJECTED` (validation failed) and `CANCELLED` (withdrawn by the sender before flight)
    - `VALIDATED` is an **internal, transient** state: it is not observable by the sender, because it immediately collapses into `ASSIGNED` (immediate delivery) or `SCHEDULED` (scheduled delivery), or into `REJECTED` on failure. It remains in the model as a logical step of the pipeline, not as a publicly asserted state
    - for an **immediate** delivery `ASSIGNED` is itself **transient**: the system assigns the nearest drone and begins the delivery within the same use case, so the creation of the delivery already reports `IN_PROGRESS`. `ASSIGNED` remains a real lifecycle state — and the cancellable state for a *scheduled* delivery — but is never observed by the sender for an immediate request
  - **Drone lifecycle (`DroneStatus`)**: `AVAILABLE`, `RESERVED`, `ASSIGNED`, `IN_DELIVERY`, `ARRIVED`, `OUT_OF_SERVICE`
  - **On the two `ASSIGNED`**: `ASSIGNED` names a state of the **Delivery** aggregate (Deliveries Context) *and* a state of the **Drone** aggregate (Fleet Context). They are distinct concepts in distinct contexts: the first says that a delivery has been given a drone, the second says that a drone has been given a delivery. They normally co-occur but are never the same value
  - these are the single source of truth for the state names used across the domain model, the event storming and the acceptance tests

---

### Users Context

- **Account**
  - the aggregate through which a person accesses the service; in order to use the features of the service a user must register, creating an account by filling a registration form
  - users that have an account are also referred to as registered users
  - an Account carries a **`role`** attribute (`SENDER` | `ADMIN`): there is a single `Account` aggregate, not two distinct types. Identity and role are **coined here**; authorization — what each role may actually do — is enforced downstream, when the user acts
- **Sender**
  - an Account with `role = SENDER`: the originator of a delivery request
- **Admin**
  - an Account with `role = ADMIN`: the user who observes the fleet and the delivery scheduling
  - the Admin account is **pre-loaded** at service start-up: it is **not** created through public registration and can only **log in**. Public registration produces only `SENDER` accounts. There is no admin-registration action and no role-promotion logic
- **Register Form**
  - the form that a user fills in to provide the data needed to create a new account
- **To register an account**
  - the action by which a user submits the register form to create a new account
  - it produces either an **Account Created** event or a **Registration Failed** event
- **To login**
  - the action a registered user (Sender or Admin) performs, specifying credentials, in order to be recognized by the system and start interacting with it
  - the credentials are verified against the Account; on success the **identity** and the **role** of the user are established, on failure the user cannot interact with the service
  - it produces either a **User Logged In** event or a **Login Failed** event
- **Domain Events**
  - **Account Created**: a new account has been successfully created
  - **Registration Failed**: the registration of a new account could not be completed
  - **User Logged In**: a registered user has successfully authenticated
  - **Login Failed**: a login attempt did not succeed

---

### Deliveries Context

- **Delivery**
  - the core aggregate of this context: the transport of a parcel from the sender to a destination performed by a drone
  - a delivery has a lifecycle that goes from request and validation, through scheduling and execution, up to completion
- **Package**
  - the parcel to be delivered, characterized by its **weight**
- **Package Weight** / **Payload Capacity**
  - the two sides of the carrying invariant: the **weight** of the package (Deliveries Context) and the maximum transportable weight of a drone, its **payload capacity** (Fleet Context)
  - **invariant**: a delivery can be performed only if some drone of the fleet has a payload capacity greater than or equal to the package weight
- **Delivery Request**
  - the request, issued by a sender, to perform a new delivery; it carries the data needed to evaluate and perform the delivery:
    - **Package**: the parcel to deliver, with its **weight**
    - **Pickup Location**: the place where the package must be collected
    - **Destination**: the place where the package must be delivered
    - **Requested Date/Time**: when the delivery should take place; it can be **Immediate** (as soon as possible) or **Scheduled** (a specific future date/time, which becomes the **slot**)
    - **Deadline**: the maximum amount of time within which the delivery must be completed
- **Location vs Position**
  - **Location** (Deliveries Context) is an *address-bearing place* — coordinates plus a human address — used for **Pickup Location** and **Destination**; it is static for a given request
  - **Position** (Fleet Context) is a *timestamped point* — coordinates plus the instant they were observed — describing where a **drone** currently is; it changes continuously as the drone moves
  - a delivery's endpoints and a drone's live whereabouts are different concepts, even though both are built on coordinates
- **Deadline**
  - the Deadline is a **duration** — a maximum elapsed time — not a calendar instant: *deliver within 30 minutes* is a Deadline, whereas *deliver on 2026-06-10 at 10:00* is a **Requested Date/Time**
  - the Deadline is a **pre-flight feasibility condition**: it is evaluated **once**, before the delivery is accepted, by comparing the estimated delivery duration against it. A delivery whose estimated duration exceeds its Deadline is **rejected** and never departs
  - the Deadline is **not** re-evaluated once the delivery is in flight: there is no runtime enforcement, and a delivery already `IN_PROGRESS` is never rejected or cancelled for exceeding it
- **Estimated Time Remaining (ETR)**
  - the value object that expresses how much time is still expected before a delivery in progress is completed
  - it is computed as a function of the drone's **current position**, the delivery **destination** and the drone's travel speed
  - it is part of the **Delivery Tracking View** and is the value the sender reads while tracking the package
  - it is **recalculated every time a Position Updated event is received** from the Fleet Context, producing an **Estimated Time Updated** event
  - when the delivery is completed, the Estimated Time Remaining is **zero**
- **SenderId**
  - the identity of the sender of a delivery: a typed value object that **refers to the identity coined in the Users Context**
  - the Deliveries Context knows the identity of a sender, never its credentials
- **Ownership**
  - the rule by which a Sender may act on, and observe, **only** the deliveries it originated
  - the **SenderId** of a delivery is taken from the authenticated user, never from the request payload
- **To create a delivery request**
  - the action a sender performs to ask the system for a new delivery
  - it produces a **Delivery Request Created** event
- **To validate a delivery**
  - the action by which the system checks whether a delivery request can be accepted, evaluating the attributes it carries:
    - **Weight feasibility**: whether the package weight is within the payload capacity of some drone of the fleet
    - **Route feasibility**: whether the pickup location and the destination are valid places that a drone can cover
    - **Time feasibility**: whether the requested date/time can be served — a free **slot** for a scheduled delivery, an available drone for an immediate one
    - **Deadline feasibility**: whether the estimated delivery duration fits within the requested deadline
  - it produces either a **Validation Delivery Passed** event or a **Validation Delivery Rejected** event
  - the rejection is a **single event type**, but it **carries the reason** that caused it, so that the sender learns *which* condition failed
- **To cancel a delivery**
  - the action a sender performs to withdraw a delivery **before its flight has started** (status `REQUESTED`, `SCHEDULED` or `ASSIGNED`)
  - it produces a **Delivery Cancelled** event and, if a drone had already been reserved or assigned, it triggers the **release of the drone reservation** in the Fleet Context
  - a delivery already **in flight** (`IN_PROGRESS`) **cannot** be cancelled by the sender
- **To schedule a delivery**
  - the action by which the **system** schedules a validated delivery for its requested future **slot**, producing a **Delivery Scheduled** event
  - scheduling is **automatic**: it is driven by the system's policies, and the assignment is triggered when the slot is due. It is **not** an Admin command — the Admin only *observes* the scheduling
- **To begin a delivery**
  - the action by which the system starts the execution of an assigned delivery, moving it to `IN_PROGRESS`
  - it produces a **Delivery Begun** event
- **To complete a delivery**
  - the action by which the system marks a delivery as finished, moving it to `DELIVERED`
  - it produces a **Delivery Completed** event
- **To request fleet feasibility**
  - the action by which the Deliveries Context asks the Fleet Context whether the delivery can be served by a drone
- **Delivery Tracking View**
  - the read model that lets a sender follow the progress of a delivery: its current status, its destination and its **Estimated Time Remaining**
- **To track a delivery**
  - the action a sender performs to observe, through the **Delivery Tracking View**, a delivery in progress
- **Delivery Scheduling View**
  - the read model that lets an Admin observe the scheduled deliveries: which deliveries are planned, on which drone, in which slot
  - it is **read-only**: it does not let the Admin alter the schedule
- **To view the scheduling**
  - the **read-only** action an Admin performs to inspect the scheduled deliveries through the **Delivery Scheduling View**
  - the Admin does not create, move or reassign slots: slot assignment is automatic, and in-flight reassignment is out of scope. The Admin's role on scheduling is observational
- **Policies**
  - **Whenever Delivery Duration Estimated, then check it against the requested deadline**
  - **Whenever Validation Delivery Rejected, then reject the delivery request, reporting the reason**
  - **Whenever Drone Assigned, then begin the delivery**
  - **Whenever Position Updated, then recalculate the Estimated Time Remaining and emit Estimated Time Updated**
  - **Whenever Drone Arrived, then complete the delivery**
  - **Whenever a sender cancels a delivery before its flight, then request the release of the drone reservation**
- **Domain Events**
  - **Delivery Request Created**: a sender has issued a new delivery request
  - **Validation Delivery Passed**: a delivery request passed validation
  - **Validation Delivery Rejected**: a delivery request failed validation; the event **carries the reason** of the failure — the package exceeds the capacity of every drone, no drone is available, no drone is free for the requested slot, the estimated duration exceeds the deadline, or the requested time is beyond the scheduling horizon
  - **Delivery Scheduled**: a validated delivery has been scheduled on a slot
  - **Delivery Cancelled**: a delivery has been cancelled by the sender before its flight started; it triggers the release of the drone reservation
  - **Delivery Begun**: the execution of a delivery has started; the delivery enters `IN_PROGRESS`
  - **Estimated Time Updated**: the estimated time remaining of a delivery has been recalculated, after a Position Updated event
  - **Delivery Completed**: a delivery has been successfully finished; the delivery enters `DELIVERED`

---

### Fleet Context

- **Drone**
  - the core aggregate of this context: an autonomous vehicle of the fleet that physically performs deliveries
  - a drone has a lifecycle through availability checking, reservation, assignment, execution and arrival
  - it is characterized by a **payload capacity**, the maximum transportable weight, against which the package weight is checked during validation
  - **invariant**: a drone accepts at most one delivery per **slot**
  - **invariant**: an update referring to an unknown drone is a violated precondition: the Fleet does not apply it and signals an error
- **Position**
  - a *timestamped point* — coordinates plus the instant they were observed — describing where a drone currently is
  - **invariant**: a Position cannot be constructed with out-of-range coordinates; the value object rejects illegal values. This is a domain invariant, not input validation
- **Admin**
  - within the Fleet Context, the Admin is the actor that **observes** the fleet, through the **Fleet Monitoring View**. It does not carry credentials here, and it does not manage the scheduling
  - the Admin's identity — Account with `role = ADMIN`, credentials — lives in the Users Context; the Fleet knows at most the identifier and the role. These are two views of the same actor in two different contexts
- **Fleet Monitoring View**
  - the read model that lets an Admin observe the state of the whole fleet: the status and the position of each drone
- **To track drones**
  - the action an Admin performs to observe, through the **Fleet Monitoring View**, the drones of the fleet
- **To check drone availability**
  - the action by which the system verifies whether a drone able to carry the package is available, producing a **Drone Availability Checked** event
- **To check slot feasibility**
  - the action by which the system verifies whether a drone able to carry the package has the requested **slot** free
- **To reserve a slot for a drone**
  - the action by which the system reserves a **slot** on a drone for a scheduled delivery
  - it produces either a **Drone Reserved** event or a **No Drone Available For Slot** event
- **To release a reservation**
  - the action by which the system frees a previously reserved slot, making the drone available again
  - it is triggered when a delivery is **cancelled** by the sender before its flight
  - it produces a **Reservation Released** event
- **To estimate delivery duration**
  - the action by which the system computes the expected duration of a delivery, from the distance between pickup and destination and the drone's travel speed
  - it produces a **Delivery Duration Estimated** event, which is the value checked against the **Deadline**
- **To assign a drone**
  - the action by which the system assigns a drone to a validated delivery
  - for an **immediate** delivery, the **nearest** available drone able to carry the package is chosen; for a **scheduled** one, the drone already reserved on the slot is taken
  - it produces either a **Drone Assigned** event or a **Drone Not Available** event
- **Policies**
  - **Whenever feasibility is requested, then check availability**
  - **Whenever Validation Delivery Passed and the delivery is immediate, then assign the nearest drone**
  - **Whenever a delivery is scheduled, then reserve a slot**
  - **Whenever a slot is due, then assign the reserved drone**
  - **Whenever a delivery is cancelled by the sender, then release the drone reservation**
- **Domain Events**
  - **Drone Availability Checked**: the availability of drones has been verified
  - **Drone Reserved**: a slot on a drone has been successfully reserved
  - **No Drone Available For Slot**: no drone could be reserved for the requested slot
  - **Reservation Released**: a previously reserved slot on a drone has been freed, making the drone available again
  - **Delivery Duration Estimated**: the duration of a delivery has been estimated
  - **Drone Assigned**: a drone has been assigned to a delivery
  - **Drone Not Available**: no drone could be assigned to a delivery
  - **Status Updated**: the status of a drone has changed
  - **Position Updated**: the position of a drone has changed
  - **Drone Arrived**: the drone reached the delivery destination
  - **Drone Out Of Service**: a drone became unavailable and can no longer be assigned deliveries

---

### Cross-Cutting Concepts

*The following terms belong to the shared vocabulary of the team, but to **no** bounded context: they do not model the business, they model the way a user interacts with the system. They are recorded here so that the language stays unambiguous, and kept out of the three contexts so that the domain model stays free of them.*

- **Session**
  - the authenticated interaction of a registered user with the service: it binds the **identity** and the **role** established at login to the sequence of commands the user issues
  - it is opened by a successful **login** and lasts as long as the user interacts with the service; it is **transient** and is not persisted
  - a Session is not a domain concept: it carries no business rule and belongs to no bounded context. It is the vehicle through which a user reaches the domain
- **Authorization**
  - the check that the Session is active and that the **role** of the user allows the requested command, applied *before* the command reaches a bounded context
  - a Sender may create, cancel and track its own deliveries; an Admin may observe the fleet and the scheduling. Neither may perform the other's commands
  - identity and role are **coined** in the Users Context; they are **enforced** here
- **Tracking Session**
  - the open channel through which a sender receives the live updates of a delivery in progress: its position and its **Estimated Time Remaining**
  - it exists only while the sender is actively tracking; like a Session, it is transient and is not persisted
  - it is the mechanism by which the **Estimated Time Updated** events reach the sender: it *carries* domain events, it does not produce any