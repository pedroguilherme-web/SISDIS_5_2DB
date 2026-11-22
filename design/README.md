# AISafe Flight Management System - C4 & C4+1 Architectural Documentation

**Course:** Sistemas Distribuídos (LETI-SIDIS / PSOFT 2026/2027) — ISEP  
**Professor:** Orlando Sousa  
**Component:** `aircraft-maintenance-service/`  
**Author / Student:** Martim Alves (1240690)  

---

## 1. Executive Summary & Distributed Evolution

The **AISafe Flight Management System** was initially conceived as a monolithic Spring Boot enterprise system. To meet modern distributed computing requirements, fault tolerance, and independent scalability, it was re-architected across two major milestones:

1. **Assignment 1 — Distributed Decomposition & P2P Data Forwarding:**
   - **Shared-Nothing Architecture:** The monolith was divided into autonomous microservices (`aircraft-maintenance-service`, `airports-routes-service`, `flight-operations-service`) with **zero shared databases**.
   - **Multi-Instance Replication:** 3 cloned replicas per service running on dedicated HTTP ports (e.g., `8081`, `8082`, `8083`) with independent H2 datastores.
   - **Peer-to-Peer HTTP GET Unicast Forwarding:** Transparent query forwarding across peer replicas using AOP (`PeerForwardingAspect`) with loop prevention headers (`X-Peer-Forwarded: true`) and graceful timeout handling.

2. **Assignment 2 — CQRS, AMQP Messaging (RabbitMQ) & Specialized Read Projections:**
   - **Command Query Responsibility Segregation (CQRS):** Decoupling write commands (enforcing domain invariants, ACID, and `@Version` optimistic locking) from denormalized read models (`AircraftReadModel`) optimized for $O(1)$ lookups.
   - **Enterprise AMQP Broker (RabbitMQ in Docker):** Asynchronous event dissemination using topic exchange `aisafe.exchange`, durable queues, and wildcard routing keys (`aircraft.*`, `maintenance.*`, `flight.*`).
   - **Specialized Read Projection (`/api/v2/fleet`):** Query endpoint projecting technical maintenance metrics into fleet operational readiness (`READY_FOR_FLIGHT`, `UNDER_MAINTENANCE`, `MAINTENANCE_DUE`, `GROUNDED`).
   - **C4 Level 4 Code & Design Model:** Exhaustive class-level architectural diagrams detailing DDD aggregates, value objects, use cases, and projection listeners.

---

## 2. Architectural Hierarchy: The C4 Model & C4+1 Process View

We adopt Simon Brown's **C4 Model** extended with Philippe Kruchten's **"+1" Scenarios / Process View** to represent the system at multiple levels of abstraction:

```
                      +---------------------------------------+
                      |       C4 Level 1: System Context       |  --> Users, actors & external authorities
                      +---------------------------------------+
                                          │
                                          ▼
                      +---------------------------------------+
                      |        C4 Level 2: Containers         |  --> Microservices, H2 DBs & RabbitMQ Docker
                      +---------------------------------------+
                                          │
                                          ▼
                      +---------------------------------------+
                      |        C4 Level 3: Components         |  --> Inside Aircraft & Maintenance Service
                      +---------------------------------------+
                                          │
                                          ▼
                      +---------------------------------------+
                      |   C4 Level 4: Code & Design Model     |  --> Classes, Aggregates, DTOs & Listeners
                      +---------------------------------------+
                                          │
                                          ▼
                      +---------------------------------------+
                      |        C4+1: Dynamic Scenarios        |  --> Sequence diagrams (P2P & CQRS AMQP)
                      +---------------------------------------+
```

---

## 3. C4 Level 1: System Context Diagram

The System Context diagram establishes the system boundaries, human actors, and external regulatory and meteorological services.

* **Diagram File:** [`design/diagrams/c4/_level1/_context.puml`](file:///c:/Users/marti/SISDIS_5_2DB/design/diagrams/c4/_level1/_context.puml)

### Key Actors & External Systems
* **System Administrator:** Manages security policies, user credentials, roles (RBAC), and monitors distributed service health.
* **Backoffice Operator:** Manages the aircraft catalog, registers physical fleet aircraft, airports, and flight routes.
* **Air Traffic Control Officer (ATCC):** Inspects aircraft operational status, schedules flights, and verifies aircraft-to-route compatibility.
* **Maintenance Technician / Supervisor:** Configures maintenance templates, schedules work orders, executes repairs, logs parts, and monitors fleet readiness and turnaround times.
* **Civil Aviation Authority (CAA / ICAO):** External regulatory body providing registration standards and aircraft safety norms.
* **Aviation Weather Service:** External meteorological data provider for route flight planning.

---

## 4. C4 Level 2: Container Diagram (Distributed Architecture)

The Container diagram illustrates the deployable runtime units, network ports, databases, the central RabbitMQ message broker, and communication protocols.

* **Diagram File:** [`design/diagrams/c4/_level2/_containers.puml`](file:///c:/Users/marti/SISDIS_5_2DB/design/diagrams/c4/_level2/_containers.puml)

### Service Topology & Runtime Components

| Container / Unit | Replica / Host | Network Port | Datastore / Protocol | Responsibility |
| :--- | :--- | :--- | :--- | :--- |
| **Aircraft & Maintenance Service** | `Instance 1` | `8081` | H2 (`aircraft_maintenance_db_1`) | Write commands, CQRS projections, `/api/v2/fleet`, AMQP publisher/subscriber. |
| **Aircraft & Maintenance Service** | `Instance 2` | `8082` | H2 (`aircraft_maintenance_db_2`) | Cloned replica for scalability, fault tolerance, and P2P query forwarding. |
| **Aircraft & Maintenance Service** | `Instance 3` | `8083` | H2 (`aircraft_maintenance_db_3`) | Cloned replica completing the 3-node P2P forwarding mesh. |
| **Airports & Routes Service** | `Instance 1 & 2` | `8091`, `8092` | H2 (`airports_routes_db_1/2`) | Airport infrastructure, certifications, runways, and route distance histories. |
| **Flight Operations Service** | `Instance 1 & 2` | `8071`, `8072` | H2 (`flight_operations_db_1/2`) | Flight scheduling, route assignments, operational telemetry (`flight.completed`). |
| **RabbitMQ Message Broker** | `Docker Container` | `5672` (AMQP)<br>`15672` (Mgmt) | In-Memory / Durable queues | Central event broker hosting topic exchange `aisafe.exchange` and durable queues. |

### Communication Protocols & Interaction Modes
1. **Synchronous REST / HTTP:**
   - Clients send commands (POST, PATCH) with JWT tokens and `If-Match` ETags.
   - Clients query CQRS projections (`GET /api/v2/fleet`) for pre-calculated operational readiness.
2. **Synchronous Unicast P2P Forwarding:**
   - On a local cache miss (`NotFoundException`), the instance forwards the HTTP GET request to peer instances (`:8082`, `:8083`) with header `X-Peer-Forwarded: true` to prevent loops.
3. **Asynchronous AMQP Messaging (RabbitMQ):**
   - Write commands publish domain events to topic exchange `aisafe.exchange`.
   - Queues (`aisafe.cqrs.projections.queue`, `aisafe.aircraft.events.queue`, `aisafe.maintenance.events.queue`) route events to subscribers across instances for eventual consistency.

---

## 5. C4 Level 3: Component Diagram (Aircraft & Maintenance Service)

Zooms directly into the internal architecture of the **`aircraft-maintenance-service`** container.

* **Diagram File:** [`design/diagrams/c4/_level3/_components.puml`](file:///c:/Users/marti/SISDIS_5_2DB/design/diagrams/c4/_level3/_components.puml)

### Component Subsystems
1. **Interface Layer (REST Controllers):**
   - `AuthController` (`/api/auth/**`): User registration, credential authentication, and JWT issuance.
   - `AircraftController` (`/api/aircrafts/**`): Fleet CRUD and status updates with ETag validation.
   - `AircraftModelController` (`/api/aircraftModels/**`): Aircraft model specification catalog.
   - `MaintenanceController` (`/api/maintenance/**`): Maintenance templates, work orders, parts inventory, and turnaround metrics.
   - `FleetReadModelController` (`/api/v2/fleet/**`): Dedicated CQRS read endpoint returning fleet operational readiness.
2. **Cross-Cutting AOP Layer:**
   - `PeerForwardingAspect` (`@Around` advice): Intercepts `@GetMapping` endpoints to forward requests upon local `NotFoundException`.
3. **Command / Write Side (Application Services & Aggregates):**
   - Use Cases: `RegisterAircraftUseCase`, `UpdateAircraftUseCase`, `CreateMaintenanceRecordUseCase`, `UpdateMaintenanceRecordUseCase`.
   - Repositories: `AircraftRepository`, `AircraftModelRepository`, `MaintenanceRecordRepository`.
   - Optimistic Concurrency Control: `@Version` fields and `ETagUtils.parseVersion()` reject stale edits with `409 Conflict`.
4. **Query / Read Side (CQRS Projections):**
   - `AircraftReadModel`: Denormalized entity in table `aircraft_read_model`.
   - `AircraftReadModelRepository`: Spring Data JPA repository for $O(1)$ single-table reads.
   - `AircraftProjectionListener`: Event subscriber with dual `@EventListener` (local) and `@RabbitListener` (AMQP queue `aisafe.cqrs.projections.queue`).
5. **Messaging & AMQP Infrastructure:**
   - `RabbitMQConfig`: Declares topic exchange `aisafe.exchange`, durable queues, and routing bindings.
   - `DomainEventPublisher`: Publishes events to both local Spring subscribers and RabbitMQ with non-blocking error handling.

---

## 6. C4 Level 4: Code & Design Model (Deep Dive)

The Code & Design Model zooms into the class structures, method contracts, inheritance hierarchies, and tactical DDD design patterns of the service.

* **Master Document:** [`design/C4_LEVEL_4_CODE_DESIGN.md`](file:///c:/Users/marti/SISDIS_5_2DB/design/C4_LEVEL_4_CODE_DESIGN.md)
* **CQRS Read Model & Projections Diagram:** [`design/diagrams/c4/_level4/_code_cqrs_projections.puml`](file:///c:/Users/marti/SISDIS_5_2DB/design/diagrams/c4/_level4/_code_cqrs_projections.puml)
* **Command Write Model & Domain Aggregates Diagram:** [`design/diagrams/c4/_level4/_code_write_model_domain.puml`](file:///c:/Users/marti/SISDIS_5_2DB/design/diagrams/c4/_level4/_code_write_model_domain.puml)

### Key Architectural Elements at Level 4:
1. **Readiness State Evaluation (`AircraftReadModel.recalculateReadinessStatus()`):**
   $$\text{Readiness} = \begin{cases} 
   \text{UNDER\_MAINTENANCE}, & \text{if status} = \text{UNDER\_MAINTENANCE} \\
   \text{GROUNDED}, & \text{if status} = \text{INACTIVE} \\
   \text{MAINTENANCE\_DUE}, & \text{if pendingMaintenanceCount} > 0 \\
   \text{READY\_FOR\_FLIGHT}, & \text{otherwise}
   \end{cases}$$
2. **Domain Event Hierarchy:**
   - `abstract class DomainEvent` (`eventId`, `timestamp`, `eventType`)
   - Subclasses: `AircraftRegisteredEvent`, `AircraftStatusChangedEvent`, `MaintenanceScheduledEvent`, `MaintenanceCompletedEvent`, `FlightCompletedEvent`.
3. **Tactical DDD Building Blocks:**
   - Aggregate Roots: `Aircraft`, `MaintenanceRecord`.
   - Value Objects: `RegistrationNumber`.
   - Concurrency: `@Version private Long version` with `ETagUtils`.

---

## 7. C4+1 Dynamic Scenarios (Process View)

### Scenario 1: P2P HTTP GET Forwarding with Failure Handling
* **Diagram File:** [`design/diagrams/c4/sequence/_p2p/_forwarding.puml`](file:///c:/Users/marti/SISDIS_5_2DB/design/diagrams/c4/sequence/_p2p/_forwarding.puml)
* **Flows:**
  - **Scenario A (Success):** Instance 1 misses local DB $\rightarrow$ forwards to Instance 2 with `X-Peer-Forwarded: true` $\rightarrow$ Instance 2 finds entity $\rightarrow$ returns `200 OK` to Instance 1 $\rightarrow$ returned to client.
  - **Scenario B (Global 404):** Entity missing across all peers $\rightarrow$ cycle stopped by `X-Peer-Forwarded` header $\rightarrow$ returns `404 Not Found`.
  - **Scenario C (Fault Tolerance):** Peer is unreachable or times out $\rightarrow$ `ResourceAccessException` caught gracefully without crashing instance.

### Scenario 2: Asynchronous CQRS Event-Driven Projection & Fleet Readiness Query
* **Diagram File:** [`design/diagrams/c4/sequence/_cqrs/_event_projection.puml`](file:///c:/Users/marti/SISDIS_5_2DB/design/diagrams/c4/sequence/_cqrs/_event_projection.puml)
* **Flows:**
  1. Client sends `PATCH /api/aircrafts/CS-TKA` (status $\rightarrow$ `UNDER_MAINTENANCE`, `If-Match: "0"`).
  2. `UpdateAircraftUseCase` validates `@Version`, updates entity in H2, and commits write transaction.
  3. `DomainEventPublisher` sends `AircraftStatusChangedEvent` to RabbitMQ topic exchange `aisafe.exchange` (`aircraft.status.changed`).
  4. RabbitMQ routes message to queue `aisafe.cqrs.projections.queue`.
  5. `AircraftProjectionListener` consumes event asynchronously via `@RabbitListener`.
  6. Listener calls `model.recalculateReadinessStatus()` $\rightarrow$ sets readiness to `UNDER_MAINTENANCE`.
  7. Read model saved in `aircraft_read_model` table.
  8. Client queries `GET /api/v2/fleet/CS-TKA` on Instance 2 (`:8082`) $\rightarrow$ returns `200 OK` in $O(1)$ without locking write tables!

---

## 8. Architectural Decisions & Trade-Offs

1. **CQRS vs. Traditional CRUD:**
   - *Decision:* Separate read model (`aircraft_read_model`) from write aggregates (`aircrafts`).
   - *Rationale:* Eliminates object-relational impedance mismatch and enables lock-free query scaling for high read/write ratio workloads.
2. **Smart Endpoints, Dumb Pipes (AMQP Messaging):**
   - *Decision:* Utilize RabbitMQ as a pure message router/buffer; keep business logic inside Spring service components.
   - *Rationale:* Minimizes broker coupling, enables temporal decoupling, and protects system during burst write traffic.
3. **Eventual Consistency (BASE) vs. Immediate Consistency (ACID):**
   - *Decision:* Write model guarantees local ACID; read projection updates asynchronously within milliseconds (BASE).
   - *Rationale:* Unblocks write commands immediately while delivering near-real-time read views to clients.
4. **Optimistic Concurrency Control (OCC):**
   - *Decision:* JPA `@Version` with ETag / `If-Match` headers instead of pessimistic row locking (`SELECT FOR UPDATE`).
   - *Rationale:* Eliminates distributed deadlock risks and maximizes horizontal scalability.

---

## 9. Verification & How to View Diagrams

All diagrams are written in standard PlantUML using the official C4-PlantUML library:
* **IntelliJ IDEA / VS Code:** Install the *PlantUML* plugin to preview `.puml` files with real-time rendering.
* **Online Rendering:** Paste the diagram content into any PlantUML viewer (e.g. [PlantText](https://www.planttext.com/) or [PlantUML Web Server](http://www.plantuml.com/plantuml/uml/)).
* **Automated Test Suite:** CQRS projections, readiness state transitions, and event listeners are validated 100% green by tests in `AircraftReadModelTest`, `AircraftProjectionListenerTest`, and `FleetReadModelControllerTest` (12 tests total).
