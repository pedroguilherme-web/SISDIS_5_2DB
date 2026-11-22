# C4 Level 4: Code & Design Model Documentation
## Aircraft & Maintenance Service — Microservices Architecture

**Course:** Sistemas Distribuídos (LETI-SIDIS / PSOFT 2026/2027) — ISEP  
**Professor:** Orlando Sousa  
**Component:** `aircraft-maintenance-service/`  
**Author / Student:** Martim Alves (1240690)  

---

## 1. Introduction & Place in the C4 Hierarchy

The **C4 Model** (created by Simon Brown) uses hierarchical zoom levels to explain software systems:
- **Level 1 (System Context):** High-level view of users (ATCC operators, technicians) and collaborating external systems.
- **Level 2 (Containers):** Deployable units, network ports, databases (`aircraft_maintenance_db_1/2/3`), and message brokers (`RabbitMQ`).
- **Level 3 (Components):** Controllers, Use Cases, Repositories, and Aspects inside the container.
- **Level 4 (Code / Design Model):** Zooms directly into the **implementation details of the components**. It reveals class structures, interfaces, inheritance hierarchies, design patterns, Domain-Driven Design (DDD) building blocks, and concurrency controls.

```
+-----------------------------------------------------------+
| C4 Level 1: System Context                                |
+-----------------------------------------------------------+
                             │
                             ▼
+-----------------------------------------------------------+
| C4 Level 2: Containers (Microservices, DBs, RabbitMQ)     |
+-----------------------------------------------------------+
                             │
                             ▼
+-----------------------------------------------------------+
| C4 Level 3: Components (Controllers, UseCases, Listeners) |
+-----------------------------------------------------------+
                             │
                             ▼
+-----------------------------------------------------------+
| C4 Level 4: Code & Class Design (Aggregates, DTOs, CQRS)  |  <-- THIS DOCUMENT
+-----------------------------------------------------------+
```

---

## 2. Diagram 1: CQRS Read Model & Event-Driven Projections

* **PlantUML File:** [`design/diagrams/c4/_level4/_code_cqrs_projections.puml`](file:///c:/Users/marti/SISDIS_5_2DB/design/diagrams/c4/_level4/_code_cqrs_projections.puml)

### 2.1. Structural Overview
The query/read side of the CQRS implementation decouples client reads from write-side relational tables. It provides pre-calculated, denormalized read models optimized for $O(1)$ lookups without complex JOIN operations.

```
[ RabbitMQ Topic Exchange: aisafe.exchange ]
                     │
                     ▼ (AMQP Queue: aisafe.cqrs.projections.queue)
      [ AircraftProjectionListener ]
                     │
                     ▼ (save / update)
      [ AircraftReadModelRepository ]
                     │
                     ▼ (manages)
        [ AircraftReadModel Entity ]
                     ▲
                     │ (queries)
          [ UnicornsController ]
                     │
                     ▼ (returns)
        [ AircraftMoodResponse DTO ]
```

### 2.2. Detailed Class Breakdown

#### 1. `UnicornsController` (`@RestController`, `@RequestMapping("/api/v2/unicorns")`)
- **Responsibility:** Exposes RESTful query endpoints required by the Assignment 2 specification.
- **Dependencies:** Injects `AircraftReadModelRepository` and `AircraftRepository`.
- **Key Methods:**
  - `getAllAirplanesMood(): ResponseEntity<List<AircraftMoodResponse>>`: Returns fleet-wide mood projections.
  - `getAirplaneMoodByRegistration(registration: String): ResponseEntity<AircraftMoodResponse>`: Returns specific aircraft mood or `404 Not Found`.
  - `ensureReadModelPopulated(): void`: Self-healing synchronization mechanism that seeds the read model from the write repository upon initial startup.

#### 2. `AircraftMoodResponse` (Java `record`)
- **Responsibility:** Immutable Data Transfer Object (DTO) defining the JSON contract for `/api/v2/unicorns`.
- **Fields:** `registrationNumber`, `model`, `operationalStatus`, `mood`, `sparkleLevel`, `moodReason`, `lastUpdated`.
- **Factory Method:** `fromReadModel(AircraftReadModel model)` converts the internal JPA entity to the public API response.

#### 3. `AircraftReadModel` (`@Entity`, `@Table(name = "aircraft_read_model")`)
- **Responsibility:** Denormalized read entity optimized for ultra-fast query performance.
- **Fields:**
  - `registrationNumber` (`@Id`, length 32)
  - `modelName`, `status`, `seatCapacity`, `range`
  - `totalOperationalHours` (accumulated flight time)
  - `pendingMaintenanceCount` (counter of active work orders)
  - `moodStatus` (`HAPPY`, `TIRED`, `UNDER_CARE`, `GRUMPY`, `PROUD`)
  - `sparkleLevel` (Integer 0 to 100)
  - `moodReason` (Human-readable explanation of current sentiment)
  - `lastUpdated` (`Instant` timestamp)
- **State Machine (`recalculateMood()`):**
  $$\text{Mood} = \begin{cases} 
  \text{UNDER\_CARE (sparkle 45)}, & \text{if status} = \text{UNDER\_MAINTENANCE} \\
  \text{GRUMPY (sparkle 15)}, & \text{if status} = \text{INACTIVE} \\
  \text{TIRED (sparkle 60)}, & \text{if pendingMaintenanceCount} > 0 \\
  \text{PROUD (sparkle 88)}, & \text{if totalOperationalHours} > 1000.0 \\
  \text{HAPPY (sparkle 98)}, & \text{otherwise}
  \end{cases}$$

#### 4. `AircraftProjectionListener` (`@Component`)
- **Responsibility:** Event subscriber updating the `AircraftReadModel` whenever domain events occur.
- **Dual Subscription:**
  - `@EventListener`: Subscribes to local in-process Spring events (instant testing and standalone resilience).
  - `@RabbitListener(queues = "aisafe.cqrs.projections.queue")`: Consumes asynchronous AMQP messages across distributed replicas.
- **Handlers:**
  - `onAircraftRegistered(AircraftRegisteredEvent event)`: Inserts new aircraft projection with initial `HAPPY` mood.
  - `onAircraftStatusChanged(AircraftStatusChangedEvent event)`: Updates status and re-evaluates mood (e.g. `UNDER_MAINTENANCE` $\rightarrow$ `UNDER_CARE`).
  - `onMaintenanceScheduled(MaintenanceScheduledEvent event)`: Increments `pendingMaintenanceCount` and updates mood to `TIRED`.
  - `onMaintenanceCompleted(MaintenanceCompletedEvent event)`: Decrements `pendingMaintenanceCount` and restores optimal mood.
  - `onFlightCompleted(FlightCompletedEvent event)`: Consumes inter-service flight telemetry, adds operational flight hours, and triggers `PROUD` mood if hours $> 1000$.

#### 5. `DomainEventPublisher` (`@Component`)
- **Responsibility:** Resilient publisher ensuring non-blocking event dissemination.
- **Mechanism:**
  - First dispatches locally via Spring `ApplicationEventPublisher`.
  - Then converts to JSON and sends over AMQP via `RabbitTemplate.convertAndSend(exchangeName, routingKey, event)`.
  - Traps socket exceptions with graceful logging so that local tests continue functioning even if the external RabbitMQ broker is offline.

#### 6. Domain Event Hierarchy
- **Base Class:** `DomainEvent` (`abstract`, contains `eventId: UUID`, `timestamp: Instant`, `eventType: String`).
- **Concrete Subclasses:**
  - `AircraftRegisteredEvent`
  - `AircraftStatusChangedEvent`
  - `MaintenanceScheduledEvent`
  - `MaintenanceCompletedEvent`
  - `FlightCompletedEvent`

---

## 3. Diagram 2: Command / Write Model & Domain Aggregates

* **PlantUML File:** [`design/diagrams/c4/_level4/_code_write_model_domain.puml`](file:///c:/Users/marti\SISDIS_5_2DB\design\diagrams\c4\_level4\_code_write_model_domain.puml)

### 3.1. Structural Overview
The command/write side enforces business domain invariants, transactional integrity (ACID), and optimistic concurrency control.

```
       [ Client PATCH /api/aircrafts/{reg} (If-Match: "2") ]
                                 │
                                 ▼
                     [ AircraftController ]
                                 │
                                 ▼
                    [ UpdateAircraftUseCase ]
                      │                   │
                      ▼                   ▼
           [ AircraftRepository ]  [ DomainEventPublisher ]
           (Verifies @Version=2)    (Emits StatusChangedEvent)
                      │
                      ▼
            [ H2 Local Database ]
```

### 3.2. Detailed Class Breakdown

#### 1. Aggregate Roots & Value Objects (Tactical DDD)
- **`Aircraft` (Aggregate Root):**
  - Encapsulates aircraft identity, status, model, and physical capabilities.
  - Guarantees invariants: seat capacity cannot exceed model maximum; status transitions must follow legitimate state rules.
  - `@Version private Long version`: Enables Hibernate Optimistic Locking.
- **`RegistrationNumber` (Value Object):**
  - Immutable value object wrapping the tail number (e.g., `CS-TKA`).
  - Self-validates format upon instantiation, preventing primitive obsession.
- **`MaintenanceRecord` (Aggregate Root):**
  - Manages maintenance lifecycle: `description`, `startDate`, `expectedDuration`, `cost`, `notes`, `status`.
  - `@Version private Long version`: Prevents lost updates when multiple technicians or supervisors record notes simultaneously.

#### 2. Application Services (Use Cases)
- `RegisterAircraftUseCase`: Validates uniqueness of `RegistrationNumber`, checks model capacity, persists the entity, and delegates event publishing to `DomainEventPublisher`.
- `UpdateAircraftUseCase`: Verifies client ETag / version against database `@Version` to reject concurrent edits (`ConcurrencyException` $\rightarrow$ `409 Conflict`), executes state mutation, and publishes `AircraftStatusChangedEvent`.
- `CreateMaintenanceRecordUseCase`: Verifies aircraft existence, links replacement parts, persists maintenance record, and emits `MaintenanceScheduledEvent`.
- `UpdateMaintenanceRecordUseCase`: Verifies `@Version`, updates work order status (e.g., `COMPLETED`), and emits `MaintenanceCompletedEvent`.

---

## 4. Key Design Patterns & Architectural Mechanisms

### 4.1. Command Query Responsibility Segregation (CQRS)
| Aspect | Command / Write Model | Query / Read Model |
|---|---|---|
| **Classes** | `Aircraft`, `MaintenanceRecord` | `AircraftReadModel`, `AircraftMoodResponse` |
| **Database Tables** | `aircrafts`, `maintenance_record` | `aircraft_read_model` |
| **Optimization Goal** | Invariant validation, transactional consistency | Low-latency queries, specialized client projections |
| **Concurrency Control** | Optimistic Locking (`@Version`, `If-Match`) | Lock-free reads, denormalized single-table queries |
| **Consistency** | ACID (immediate local consistency) | BASE (eventual consistency via domain events) |

### 4.2. Smart Endpoints, Dumb Pipes (AMQP Messaging)
- **Broker (RabbitMQ):** Functions as a "dumb pipe". It does not evaluate business rules; it merely routes and buffers messages reliably.
- **Microservice:** Acts as a "smart endpoint". `AircraftProjectionListener` and Use Cases contain all the domain intelligence and state machine evaluations.

### 4.3. Optimistic Concurrency Control (OCC)
- Avoids database-level pessimistic row locking (`SELECT FOR UPDATE`), which impairs horizontal scalability and causes deadlock hazards in distributed systems.
- Every write mutation verifies:
  $$\text{Database.version} == \text{Client.clientVersion}$$
  If a mismatch is detected, an `ObjectOptimisticLockingFailureException` is converted by `GlobalExceptionHandler` to HTTP `409 Conflict`, prompting the client to refresh and retry.

---

## 5. Summary Table of Level 4 Artifacts

| Level 4 Artifact | Purpose | File Location |
|---|---|---|
| **CQRS PlantUML Diagram** | Visualizes Read Model, Projections, Controller, and Events | [`design/diagrams/c4/_level4/_code_cqrs_projections.puml`](file:///c:/Users/marti/SISDIS_5_2DB/design/diagrams/c4/_level4/_code_cqrs_projections.puml) |
| **Write Model PlantUML Diagram** | Visualizes Aggregates, Value Objects, Use Cases, and OCC | [`design/diagrams/c4/_level4/_code_write_model_domain.puml`](file:///c:/Users/marti/SISDIS_5_2DB/design/diagrams/c4/_level4/_code_write_model_domain.puml) |
| **Read Model Entity** | Denormalized table entity with mood calculation | [AircraftReadModel.java](file:///c:/Users/marti/SISDIS_5_2DB/aircraft-maintenance-service/src/main/java/aisafe/aircrafts/cqrs/projections/AircraftReadModel.java) |
| **REST Controller** | Exposes `/api/v2/unicorns` query endpoint | [UnicornsController.java](file:///c:/Users/marti/SISDIS_5_2DB/aircraft-maintenance-service/src/main/java/aisafe/aircrafts/infrastructure/UnicornsController.java) |
| **Projection Listener** | Dual Spring/AMQP subscriber for eventual consistency | [AircraftProjectionListener.java](file:///c:/Users/marti/SISDIS_5_2DB/aircraft-maintenance-service/src/main/java/aisafe/aircrafts/cqrs/projections/AircraftProjectionListener.java) |
| **AMQP Topology** | Topic exchange, durable queues, routing keys, bindings | [RabbitMQConfig.java](file:///c:/Users/marti/SISDIS_5_2DB/aircraft-maintenance-service/src/main/java/aisafe/shared/infrastructure/messaging/RabbitMQConfig.java) |
