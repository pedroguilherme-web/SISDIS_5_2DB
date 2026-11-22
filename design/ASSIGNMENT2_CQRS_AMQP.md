# AISafe - Assignment 2 Technical & Theoretical Architecture
## CQRS, Asynchronous AMQP Messaging (RabbitMQ) & Specialized Read Projections

**Course:** Sistemas Distribuídos (LETI-SIDIS / PSOFT 2026/2027) — ISEP  
**Professor:** Orlando Sousa  
**Component:** `aircraft-maintenance-service/`  
**Author / Student:** Martim Alves (1240690)  

---

## 1. Context & Architectural Evolution

In **Assignment 1**, our microservices architecture focused on:
- Domain segregation with zero shared database (*Database-per-Service* pattern).
- Horizontal instance replication (3 replicas per service running on ports 8081, 8082, 8083).
- Synchronous Peer-to-Peer HTTP GET unicast data forwarding with loop prevention (`X-Peer-Forwarded` headers).

In **Assignment 2**, we elevate the distributed architecture to handle **enterprise-grade scalability, decoupling, and high read/write asymmetry** by introducing three foundational distributed computing patterns:
1. **CQRS (Command Query Responsibility Segregation):** Segregating the domain write model (responsible for command validation, domain invariants, and persistence) from specialized, denormalized read models (optimized for latency, queries, and client views).
2. **Asynchronous Event-Driven Messaging with AMQP / RabbitMQ:** Decoupling producers and consumers through a robust message broker using Topic Exchanges, durable queues, and routing keys.
3. **Dedicated Read Projection Endpoint (`/api/v2/fleet`):** Projecting aircraft technical status and maintenance workload into an operational readiness read model via event listeners.

---

## 2. Theoretical Foundations (SISDIS Alignment)

### 2.1. Why CQRS? (Command Query Responsibility Segregation)
In traditional CRUD architectures, the same database schema and domain entities are used for both writes (inserts/updates) and reads (queries). This leads to critical distributed bottlenecks:
- **Object-Relational Impedance Mismatch:** Complex queries require multiple JOINs, aggregations, and pessimistic locks that degrade write performance.
- **Resource Contention:** Heavy analytical or reporting queries lock relational rows or tables needed by critical transactional commands (e.g., flight operations, aircraft status changes).
- **Asymmetric Scaling:** In real-world aviation systems, queries outnumber write commands by orders of magnitude (e.g., 100:1 ratio). Scaling the entire database cluster for read traffic is economically and computationally inefficient.

**CQRS Solution in `aircraft-maintenance-service`:**
- **Command Side (Write Model):** `Aircraft`, `MaintenanceRecord` JPA entities. Operates under ACID transactions with Optimistic Concurrency Control (`@Version`), enforcing business rules (e.g., status transitions, component integrity).
- **Events Emitted:** Any state transition produces an immutable Domain Event (`AircraftRegisteredEvent`, `AircraftStatusChangedEvent`, `MaintenanceScheduledEvent`, `MaintenanceCompletedEvent`).
- **Query Side (Read Model):** `AircraftReadModel` (`aircraft_read_model` table). Denormalized projection updated asynchronously by listening to domain events. Contains pre-calculated fields (`readinessStatus`, `pendingMaintenanceCount`, `totalOperationalHours`, `operationalSummary`) enabling $O(1)$ single-table lookups without complex JOINs.

```
       [ Client Commands: POST / PATCH ]
                     │
                     ▼
           [ Command Handlers / UseCases ]
                     │
         ┌───────────┴───────────┐
         ▼                       ▼
 [ Write Model DB ]     [ Publish Event ]
 (Aircraft / Maint)              │
                         (RabbitMQ Exchange)
                                 │
                                 ▼
                    [ Event Projection Listener ]
                                 │
                                 ▼
                       [ Read Model DB / Cache ]
                       (AircraftReadModel Entity)
                                 ▲
                                 │
                 [ Client Queries: GET /api/v2/fleet ]
```

### 2.2. Asynchronous AMQP Messaging with RabbitMQ
- **Smart Endpoints, Dumb Pipes:** Microservices own the domain logic; the broker (`RabbitMQ`) is solely responsible for reliable message delivery.
- **Temporal Decoupling:** The publisher does not wait for consumers to finish processing. If consumers are temporarily offline or under heavy load, messages safely accumulate in durable queues (*buffering / backpressure*).
- **Topic Exchange Topology (`aisafe.exchange`):**
  - Messages are routed using hierarchical wildcard routing keys:
    - `aircraft.registered`
    - `aircraft.status.changed`
    - `maintenance.scheduled`
    - `maintenance.completed`
    - `flight.completed`
  - Queues bind with patterns: `aircraft.#`, `maintenance.#`, or `#` (for projections).

### 2.3. Eventual Consistency & BASE vs ACID
- The Command side maintains **ACID** guarantees inside its local transaction boundary.
- The Query projection adheres to **BASE** (*Basically Available, Soft-state, Eventual consistency*).
- After a command finishes, the read projection is updated within milliseconds via the event subscriber, guaranteeing eventual consistency across all replicas.

---

## 3. Implementation of `/api/v2/fleet` & CQRS Integration

### 3.1. What does the endpoint do?
The CQRS read endpoint returns the operational readiness status of airplanes in JSON format:
- `GET /api/v2/fleet` — Returns operational readiness summary for all aircraft in the fleet.
- `GET /api/v2/fleet/{registration}` — Returns operational readiness projection for a specific airplane.

### 3.2. Response Contract (JSON)
```json
[
  {
    "registrationNumber": "CS-TKA",
    "model": "Airbus A320neo",
    "operationalStatus": "AVAILABLE",
    "readinessStatus": "READY_FOR_FLIGHT",
    "pendingMaintenanceCount": 0,
    "totalOperationalHours": 50.0,
    "operationalSummary": "Optimal operational conditions; ready for scheduled flights",
    "lastUpdated": "2026-11-22T17:00:00.000Z"
  },
  {
    "registrationNumber": "CS-TKB",
    "model": "Boeing 737 MAX",
    "operationalStatus": "UNDER_MAINTENANCE",
    "readinessStatus": "UNDER_MAINTENANCE",
    "pendingMaintenanceCount": 1,
    "totalOperationalHours": 120.0,
    "operationalSummary": "Currently undergoing scheduled maintenance servicing in hangar",
    "lastUpdated": "2026-11-22T17:00:00.000Z"
  }
]
```

### 3.3. How `/api/v2/fleet` Proves CQRS Architecture
1. **Clean Separation of Concerns:**
   The write aggregate `Aircraft` knows only about domain rules (`AircraftStatus`, `RegistrationNumber`, `@Version`). It does **not** contain projection or reporting fields.
2. **Event-Driven Projection:**
   The `AircraftProjectionListener` intercepts domain events and evaluates operational readiness:
   - Status `UNDER_MAINTENANCE` $\rightarrow$ `UNDER_MAINTENANCE`
   - Status `INACTIVE` $\rightarrow$ `GROUNDED`
   - Pending maintenance count $> 0$ $\rightarrow$ `MAINTENANCE_DUE`
   - Normal operational conditions $\rightarrow$ `READY_FOR_FLIGHT`
3. **Lock-Free Read Performance:**
   Because operational metrics are pre-projected into `AircraftReadModel`, the query requires zero runtime computations, zero joins with maintenance tables, and zero locking against write transactions.

---

## 4. Key Java Classes & Artifacts

| Component | Class Path | Architectural Role |
|---|---|---|
| **Read Model Entity** | `aircrafts/cqrs/projections/AircraftReadModel.java` | Denormalized entity with readiness evaluation |
| **Read Model DTO** | `aircrafts/cqrs/projections/AircraftFleetSummaryResponse.java` | Immutable DTO defining query JSON response |
| **REST Controller** | `aircrafts/infrastructure/FleetReadModelController.java` | Exposes `GET /api/v2/fleet` and Swagger documentation |
| **Projection Listener** | `aircrafts/cqrs/projections/AircraftProjectionListener.java` | Dual Spring/AMQP subscriber for eventual consistency |
| **Read Repository** | `aircrafts/cqrs/projections/AircraftReadModelRepository.java` | Spring Data JPA repository for $O(1)$ lookups |
| **Event Publisher** | `shared/infrastructure/messaging/DomainEventPublisher.java` | Dual-dispatch publisher (Spring ApplicationEvent + AMQP) |
| **AMQP Topology** | `shared/infrastructure/messaging/RabbitMQConfig.java` | Topic exchange, durable queues, bindings, JSON converter |

---

## 5. Verification & Testing

### 5.1. Automated Unit Tests (12/12 Tests Pass - 100% Green)
Run via Maven wrapper:
```bash
./mvnw test -Dtest="AircraftReadModelTest,AircraftProjectionListenerTest,FleetReadModelControllerTest" -pl aircraft-maintenance-service
```
- `AircraftReadModelTest`: 4/4 passed
- `AircraftProjectionListenerTest`: 5/5 passed
- `FleetReadModelControllerTest`: 3/3 passed

### 5.2. Postman Integration Suite
The Postman collection is organized to validate the full CQRS lifecycle:
- **Folder `5. CQRS & Assignment 2 Tests (RabbitMQ AMQP & Fleet Readiness Projection)`:**
  1. `1. GET /api/v2/fleet` — Verifies array response with readiness fields.
  2. `2. GET /api/v2/fleet/CS-TKA` — Verifies specific aircraft readiness retrieval on Instance 1 (:8081).
  3. `3. Command: Update Aircraft CS-TKA to UNDER_MAINTENANCE` — Modifies write model.
  4. `4. Query (CQRS): Verify CS-TKA Readiness Updated to UNDER_MAINTENANCE` — Asserts that projection immediately updated to `UNDER_MAINTENANCE`.
  5. `5. Multi-Instance Query: GET /api/v2/fleet/CS-TKA on Instance 2 (:8082)` — Asserts multi-instance CQRS query capability across replicated instances.
  6. `6. GET /api/v2/fleet/UNKNOWN-PLANE-999` — Asserts 404 Not Found handling.

---

## 6. Oral Defense Defense Prep (Q&A)

### Q1: Como é que garantem que a réplica 2 tem os dados atualizados se a escrita foi feita na réplica 1?
> Através do **RabbitMQ**. Quando a réplica 1 processa uma alteração, o `DomainEventPublisher` envia um evento para a Topic Exchange `aisafe.exchange`. O RabbitMQ encaminha esse evento para a fila de projeções durável, que é consumida pelo `AircraftProjectionListener` de todas as réplicas ativas, garantindo **Consistência Eventual (BASE)** em milissegundos.

### Q2: Onde se enquadra o endpoint `/api/v2/fleet` na arquitetura?
> O endpoint `/api/v2/fleet` é o exemplo perfeito de uma **Read Model Projection** do CQRS.
> Na camada de domínio de escrita, a entidade `Aircraft` foca-se na consistência transacional (ACID) e controlo de versão (`@Version`).
> O endpoint `/api/v2/fleet` consulta a tabela desnormalizada `aircraft_read_model`, permitindo leituras ultrarrápidas em $O(1)$ sem joins nem bloqueios sobre as tabelas de escrita.
