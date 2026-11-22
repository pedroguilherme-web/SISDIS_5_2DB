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

In **Assignment 2**, we elevate the distributed architecture to handle **enterprise-grade scalability, decoupling, and high read/write asymmetry** by introducing two foundational distributed computing patterns:
1. **CQRS (Command Query Responsibility Segregation):** Segregating the domain write model (responsible for command validation, domain invariants, and persistence) from specialized, denormalized read models (optimized for latency, queries, and client views).
2. **Asynchronous Event-Driven Messaging with AMQP / RabbitMQ:** Decoupling producers and consumers through a robust message broker using Topic Exchanges, durable queues, and routing keys.
3. **Dedicated Read Projection Mock Endpoint (`/api/v2/unicorns`):** Fulfilling the mandatory course requirement by projecting aircraft technical status into sentiment/mood analytics via event listeners.

---

## 2. Theoretical Foundations (SISDIS Slides Alignment)

### 2.1. Why CQRS? (Command Query Responsibility Segregation)
In traditional CRUD architectures, the same database schema and domain entities are used for both writes (inserts/updates) and reads (queries). This leads to critical distributed bottlenecks:
- **Object-Relational Impedance Mismatch:** Complex queries require multiple JOINs, aggregations, and pessimistic locks that degrade write performance.
- **Resource Contention:** Heavy analytical or reporting queries lock relational rows or tables needed by critical transactional commands (e.g., flight operations, aircraft status changes).
- **Asymmetric Scaling:** In real-world aviation systems, queries outnumber write commands by orders of magnitude (e.g., 100:1 ratio). Scaling the entire database cluster for read traffic is economically and computationally inefficient.

**CQRS Solution in `aircraft-maintenance-service`:**
- **Command Side (Write Model):** `Aircraft`, `MaintenanceRecord` JPA entities. Operates under ACID transactions with Optimistic Concurrency Control (`@Version`), enforcing business rules (e.g., status transitions, component integrity).
- **Events Emitted:** Any state transition produces an immutable Domain Event (`AircraftRegisteredEvent`, `AircraftStatusChangedEvent`, `MaintenanceScheduledEvent`, `MaintenanceCompletedEvent`).
- **Query Side (Read Model):** `AircraftReadModel` (`aircraft_read_model` table). Denormalized projection updated asynchronously by listening to domain events. Contains pre-calculated fields (`moodStatus`, `sparkleLevel`, `pendingMaintenanceCount`, `totalOperationalHours`) enabling $O(1)$ single-table lookups without complex JOINs.

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
                 [ Client Queries: GET /api/v2/unicorns ]
```

### 2.2. Asynchronous AMQP Messaging with RabbitMQ
As demonstrated in the *Advanced Microservices Communication* and *RabbitMQ Installation & Docker Tutorial* lectures:
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

## 3. Implementation of `/api/v2/unicorns` & CQRS Integration

### 3.1. What does the endpoint do?
The mandatory endpoint returns the mood status of airplanes in JSON format:
- `GET /api/v2/unicorns` — Returns mood and sparkle levels for all aircraft in the fleet.
- `GET /api/v2/unicorns/{registration}` — Returns mood details for a specific airplane.

### 3.2. Response Contract (JSON)
```json
[
  {
    "registrationNumber": "CS-TKA",
    "model": "Airbus A320neo",
    "operationalStatus": "AVAILABLE",
    "mood": "HAPPY",
    "sparkleLevel": 98,
    "moodReason": "Optimal flight conditions; eager for the next destination!",
    "lastUpdated": "2026-11-21T13:00:00.000Z"
  },
  {
    "registrationNumber": "CS-TKB",
    "model": "Boeing 737 MAX",
    "operationalStatus": "UNDER_MAINTENANCE",
    "mood": "UNDER_CARE",
    "sparkleLevel": 45,
    "moodReason": "Currently undergoing maintenance servicing in the hangar",
    "lastUpdated": "2026-11-21T13:00:00.000Z"
  }
]
```

### 3.3. How `/api/v2/unicorns` Proves CQRS Architecture
1. **Separation of Domain Concerns:**
   The write aggregate `Aircraft` knows only about aviation rules (`flightHours`, `AircraftStatus`, `RegistrationNumber`). It does **not** contain "unicorns", "sparkleLevel", or "mood" fields. Polluting the write aggregate with client-specific UI metrics would violate Domain-Driven Design (DDD) and Single Responsibility Principle (SRP).
2. **Projection Event Transformation:**
   The `AircraftProjectionListener` intercepts domain events and executes the mood state machine:
   - Status `UNDER_MAINTENANCE` $\rightarrow$ Mood `UNDER_CARE` (sparkle: 45)
   - Status `INACTIVE` $\rightarrow$ Mood `GRUMPY` (sparkle: 15)
   - Pending maintenance count $> 0$ $\rightarrow$ Mood `TIRED` (sparkle: 60)
   - Total operational flight hours $> 1000$ $\rightarrow$ Mood `PROUD` (sparkle: 88)
   - Normal operational conditions $\rightarrow$ Mood `HAPPY` (sparkle: 98)
3. **High Read Throughput:**
   Because mood status and sparkle metrics are pre-projected into `AircraftReadModel`, the query requires zero runtime computations, zero joins with maintenance tables, and zero locking against write transactions.

---

## 4. Key Source Code Artifacts

| Component | File Path | Purpose |
|---|---|---|
| **Domain Events** | `shared/domain/events/DomainEvent.java`<br>`shared/domain/events/FlightCompletedEvent.java` | Base abstract domain event & inter-service event from Flight Operations |
| **Aircraft Events** | `aircrafts/cqrs/events/AircraftRegisteredEvent.java`<br>`aircrafts/cqrs/events/AircraftStatusChangedEvent.java` | Immutable events emitted upon write commands |
| **Maintenance Events** | `maintenance/cqrs/events/MaintenanceScheduledEvent.java`<br>`maintenance/cqrs/events/MaintenanceCompletedEvent.java` | Events signaling maintenance state changes |
| **AMQP Config** | `shared/infrastructure/messaging/RabbitMQConfig.java` | Topic Exchange (`aisafe.exchange`), durable queues, bindings, JSON converter |
| **Event Publisher** | `shared/infrastructure/messaging/DomainEventPublisher.java` | Dual-publishing to RabbitMQ (AMQP) and local Spring `ApplicationEventPublisher` |
| **Read Model Entity** | `aircrafts/cqrs/projections/AircraftReadModel.java` | Denormalized entity with mood state machine and sparkle metrics |
| **Projection Listener** | `aircrafts/cqrs/projections/AircraftProjectionListener.java` | `@EventListener` / `@RabbitListener` subscriber updating read projections |
| **REST Controller** | `aircrafts/infrastructure/UnicornsController.java` | Exposes `GET /api/v2/unicorns` and Swagger documentation |
| **Docker Compose** | `docker-compose-rabbitmq.yml` | Starts RabbitMQ broker with management UI on 5672 & 15672 |

---

## 5. Verification & Testing

### 5.1. Automated Unit & Integration Tests
Run via Maven wrapper:
```bash
./mvnw test -Dtest="AircraftReadModelTest,AircraftProjectionListenerTest,UnicornsControllerTest" -pl aircraft-maintenance-service
```
**Results:** `Tests run: 13, Failures: 0, Errors: 0, Skipped: 0` (BUILD SUCCESS).

### 5.2. Postman Test Suite
Located in `aircraft-maintenance-service/aisafe-aircraft-maintenance.postman_collection.json`:
- **Folder `5. CQRS & Assignment 2 Tests (RabbitMQ AMQP & Mood Projection)`:**
  1. `1. GET /api/v2/unicorns` — Verifies array response with mood and sparkle fields.
  2. `2. GET /api/v2/unicorns/CS-TKA` — Verifies specific airplane mood retrieval on Instance 1 (:8081).
  3. `3. Command: Update Aircraft CS-TKA to UNDER_MAINTENANCE` — Triggers write command and event publication.
  4. `4. Query (CQRS): Verify CS-TKA Mood Updated to UNDER_CARE` — Asserts that mood immediately projected to `UNDER_CARE` (sparkle: 45).
  5. `5. Multi-Instance Query: GET /api/v2/unicorns/CS-TKA on Instance 2 (:8082)` — Asserts multi-instance CQRS query capability across replicated instances.
  6. `6. GET /api/v2/unicorns/UNKNOWN-PLANE-999` — Asserts 404 Not Found handling.

---

## 6. Oral Defense Guide (Questions & Answers for Prof. Orlando Sousa)

### Q1: O que é o padrão CQRS e porque é que foi necessário implementá-lo no Assignment 2?
> **Resposta:**
> CQRS significa *Command Query Responsibility Segregation*. Consiste em separar o modelo de escrita (Commands) do modelo de leitura (Queries). 
> No nosso serviço de aeronaves e manutenção, os comandos (registar aeronave, agendar manutenção, alterar estado) têm regras de negócio estritas, transações ACID e controlo de concorrência otimista (`@Version`). 
> Se usássemos o mesmo modelo para leituras frequentes e dashboards, teríamos *database contention* e *impedance mismatch*. Com o CQRS, as escritas produzem Eventos de Domínio e os leitores consultam projeções pré-calculadas e desnormalizadas (`AircraftReadModel`), reduzindo o tempo de consulta a $O(1)$ sem bloqueios nem JOINs pesados.

### Q2: Onde se enquadra o endpoint `/api/v2/unicorns` na arquitetura? Não é apenas um mock?
> **Resposta:**
> O endpoint `/api/v2/unicorns` é o exemplo perfeito de uma **Read Model Projection** do CQRS.
> Na camada de domínio de escrita, a entidade `Aircraft` não tem qualquer noção de unicórnios, humor (*mood*) ou níveis de purpurina (*sparkleLevel*), respeitando o princípio da Responsabilidade Única (SRP) e DDD.
> Quem calcula o humor é a projeção (`AircraftProjectionListener`), que escuta eventos assíncronos (`AircraftStatusChangedEvent`, `MaintenanceScheduledEvent`, `MaintenanceCompletedEvent`, `FlightCompletedEvent`). Quando uma aeronave entra em manutenção, o listener atualiza o `AircraftReadModel` para `UNDER_CARE`. Isto demonstra que o CQRS permite criar vistas de dados customizadas para clientes específicos sem alterar o modelo de escrita.

### Q3: Qual a diferença entre comunicação síncrona REST (Assignment 1) e assíncrona AMQP com RabbitMQ (Assignment 2)?
> **Resposta:**
> No Assignment 1 usamos REST síncrono: se a réplica remota estivesse lenta ou inacessível, o cliente sofria com timeouts ou bloqueios (*temporal coupling*).
> No Assignment 2 introduzimos mensageria assíncrona AMQP via RabbitMQ (*Smart Endpoints, Dumb Pipes*). O emissor publica o evento na Topic Exchange `aisafe.exchange` e liberta a thread imediatamente. O RabbitMQ armazena as mensagens em filas duráveis (*queues*). Se um consumidor ou réplica estiver em baixo ou com picos de tráfego, as mensagens ficam em buffer (*backpressure*), garantindo tolerância a falhas e desacoplamento temporal.

### Q4: Como é garantida a consistência entre o modelo de escrita e o modelo de leitura?
> **Resposta:**
> É garantida através de **Consistência Eventual** (*Eventual Consistency* sob o modelo BASE).
> A escrita local é atómica na base de dados relacional. Assim que a transação é confirmada (*commit*), o evento é publicado. O `AircraftProjectionListener` consome a mensagem e atualiza o `AircraftReadModel`. Há uma janela de milissegundos onde a leitura pode estar ligeiramente desatualizada, o que é perfeitamente aceitável e padrão em sistemas distribuídos de alta escala (segundo o Teorema CAP, privilegiamos AP - Alta Disponibilidade e Tolerância a Partições).
