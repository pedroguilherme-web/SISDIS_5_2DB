# AISafe Flight Management System - C4 & C4+1 Architectural Documentation

**Course:** Sistemas Distribuídos (LETI-SIDIS / PSOFT)  
**Academic Year:** 2026/2027  
**Assignment:** Assignment 1 – Distributed Refactoring, Replication & P2P Forwarding  
**Author / Responsibility:** Martim Alves (1240690) – *Aircraft & Maintenance Service*  

---

## 1. Executive Summary & Context

The **AISafe Flight Management System** was initially designed as a monolithic Spring Boot application during the *Projeto de Software* (PSOFT) course. While functionally robust, the monolithic architecture exhibited inherent distributed limitations:
* **Single Point of Failure (SPOF):** A crash or overload in one domain (e.g., intense maintenance reporting) brought down the entire flight management platform.
* **Tight Coupling & Shared Database:** Direct entity relationships and foreign keys across domains hindered independent evolution, scaling, and database optimizations.
* **Limited Scalability:** Inability to selectively replicate only the high-load services.

In accordance with the **Assignment 1** specifications and classroom teachings (LETI-SIDIS, Prof. Orlando Sousa), the system was redesigned into a **decentralized, distributed microservices architecture** governed by three core pillars:
1. **Domain-Driven Segregation:** Splitting the monolith into three autonomous business services interacting via lightweight HTTP/REST APIs with **zero shared databases**.
2. **Instance Replication & Cloning:** Running multiple concurrent replicas per service on distinct ports with independent datastores.
3. **Peer-to-Peer HTTP GET Forwarding (Unicast):** Seamless query propagation across peer replicas with loop prevention and fault-tolerant timeout/exception handling.

---

## 2. Architectural Representation: The C4 & C4+1 Model

We adopt Simon Brown's **C4 Model** extended with Philippe Kruchten's **"+1" Scenarios / Process View** to communicate system architecture across different abstraction levels.

```
                      +-----------------------------+
                      |   C4 Level 1: Context       |  --> System boundaries & external actors
                      +-----------------------------+
                                     |
                                     v
                      +-----------------------------+
                      |   C4 Level 2: Containers    |  --> Deployable units, ports, DBs & clusters
                      +-----------------------------+
                                     |
                                     v
                      +-----------------------------+
                      |   C4 Level 3: Components    |  --> Inside Aircraft & Maintenance Service
                      +-----------------------------+
                                     |
                                     v
                      +-----------------------------+
                      |   C4+1: Dynamic Scenarios   |  --> Sequence diagrams for P2P Forwarding
                      +-----------------------------+
```

---

## 3. C4 Level 1: System Context Diagram

The System Context diagram establishes the system boundaries and depicts how different human actors and external systems interact with the AISafe platform.

* **Diagram file:** [`design/diagrams/c4/_level1/_context.puml`](file:///c:/Users/marti/SISDIS_5_2DB/design/diagrams/c4/_level1/_context.puml)

### Key Actors & Systems
* **System Administrator:** Manages security credentials, user roles (RBAC), and monitors distributed service health.
* **Backoffice Operator:** Manages the aircraft catalog, registers physical fleet aircraft, airports, and flight routes.
* **Air Traffic Control Officer (ATCC):** Inspects aircraft operational status, schedules flights, and verifies aircraft-to-route constraints.
* **Maintenance Technician / Supervisor:** Configures maintenance templates, plans and records maintenance work orders, logs replaced parts, and monitors aircraft turnaround metrics.
* **Civil Aviation Authority (CAA / ICAO):** External regulatory body providing registration standards and aircraft safety norms.
* **Aviation Weather Service:** External meteorological data provider for route planning.

---

## 4. C4 Level 2: Container Diagram (Distributed Architecture)

The Container diagram illustrates the deployable runtime units, network ports, databases, and inter-service communication protocols.

* **Diagram file:** [`design/diagrams/c4/_level2/_containers.puml`](file:///c:/Users/marti/SISDIS_5_2DB/design/diagrams/c4/_level2/_containers.puml)

### Service Topology & Port Allocation

| Service Container | Instance | HTTP Port | Datastore | Responsibility |
| :--- | :--- | :--- | :--- | :--- |
| **Aircraft & Maintenance Service** | `Instance 1` | `8081` | H2 (`aircraft_maintenance_db_1`) | Aircraft models, fleet status, maintenance templates, work orders, parts inventory. |
| **Aircraft & Maintenance Service** | `Instance 2` | `8082` | H2 (`aircraft_maintenance_db_2`) | Cloned replica for fault tolerance, replication, and peer query forwarding. |
| **Aircraft & Maintenance Service** | `Instance 3` | `8083` | H2 (`aircraft_maintenance_db_3`) | Additional cloned replica for non-binary multi-peer forwarding mesh. |
| **Airports & Routes Service** | `Instance 1` | `8091` | H2 (`airports_routes_db_1`) | Airport infrastructure, runways, certifications, route distances and histories. |
| **Airports & Routes Service** | `Instance 2` | `8092` | H2 (`airports_routes_db_2`) | Cloned replica for airport/route queries. |
| **Flight Operations Service** | `Instance 1` | `8071` | H2 (`flight_operations_db_1`) | Flight schedules, route assignments, operational telemetry, availability queries. |
| **Flight Operations Service** | `Instance 2` | `8072` | H2 (`flight_operations_db_2`) | Cloned replica for flight dispatching. |

### Communication Protocols
1. **Client to Services:** RESTful APIs over HTTP/HTTPS with JSON payloads and Bearer JWT authentication tokens.
2. **Inter-Service Communication (Loose Coupling):**
   * *Flight Operations* queries *Aircraft & Maintenance* (`GET /api/aircrafts/{reg}`) to check aircraft operational availability (`AVAILABLE` vs `UNDER_MAINTENANCE`).
   * *Flight Operations* queries *Airports & Routes* (`GET /api/routes/{id}`) to check airport runway and route distance feasibility.
   * *Aircraft & Maintenance* queries *Flight Operations* (`GET /api/flights/aircraft/{reg}/hours`) to compute cumulative operational flight hours for periodic maintenance thresholds.
3. **Intra-Service P2P Forwarding (Unicast):**
   * Peer-to-peer unicast HTTP GET requests across `Instance 1` (`:8081`), `Instance 2` (`:8082`), and `Instance 3` (`:8083`) when an entity is not found in the local database.

---

## 5. C4 Level 3: Component Diagram (Aircraft & Maintenance Service)

Focuses specifically on the internal architecture of the **Aircraft & Maintenance Service** container (responsibility of Martim Alves, branch `1240690`).

* **Diagram file:** [`design/diagrams/c4/_level3/_components.puml`](file:///c:/Users/marti/SISDIS_5_2DB/design/diagrams/c4/_level3/_components.puml)

### Internal Modules
1. **Interface Layer (REST Controllers):**
   * `AuthController` (`/api/auth/**`): User registration, credential authentication, and JWT issuance.
   * `AircraftController` (`/api/aircrafts/**`): CRUD operations, fleet listing, and status changes (`AVAILABLE`, `UNDER_MAINTENANCE`, `INACTIVE`).
   * `AircraftModelController` (`/api/aircraftModels/**`): Registration and queries of manufacturer models and technical specs.
   * `MaintenanceController` (`/api/maintenance/**`): Maintenance templates, work order tracking, component wear, and inventory.
2. **Cross-Cutting Forwarding Layer:**
   * `PeerForwardingAspect` (`@Around` advice): Intercepts `@GetMapping` endpoints. If the local use case throws a `NotFoundException`, the aspect queries configured peer URLs (`peers.urls`), injecting `X-Peer-Forwarded: true` to prevent loops, and preserves the `Authorization` header.
3. **Application Services (Use Cases):**
   * `RegisterAircraftUseCase`, `UpdateAircraftStatusUseCase`, `ViewAircraftDetailsUseCase`.
   * `ScheduleMaintenanceUseCase`, `CompleteMaintenanceRecordUseCase`, `ViewMaintenanceDueAircraftUseCase`.
4. **Inter-Service Decoupling Clients:**
   * `FlightServiceHttpClient` (implements `FlightServiceClient`): Uses Spring `RestClient` to consume `http://localhost:8071` asynchronously/synchronously.
   * `RouteServiceHttpClient` (implements `RouteServiceClient`): Consumes `http://localhost:8092` (or `http://localhost:8091`).
5. **Persistence Layer & Concurrency Control:**
   * Spring Data JPA Repositories with Hibernate.
   * **Optimistic Locking:** JPA entities (`AircraftJpaEntity`, `MaintenanceRecordJpaEntity`) feature an `@Version` field to prevent lost updates during concurrent edits.

---

## 6. C4+1 Scenarios: Process View (P2P Forwarding with Failure Handling)

The process view details the runtime interaction when querying distributed/partitioned data across service replicas.

* **Diagram file:** [`design/diagrams/c4/sequence/_p2p/_forwarding.puml`](file:///c:/Users/marti/SISDIS_5_2DB/design/diagrams/c4/sequence/_p2p/_forwarding.puml)

### Execution Flows

#### Scenario A: Local Miss -> Peer Hit (Success Flow)
1. Client requests `GET /api/aircrafts/CS-TRP` on `Instance 1` (`:8081`).
2. `Instance 1` queries local H2 database: entity not found -> throws `AircraftNotFoundException`.
3. `PeerForwardingAspect` catches the exception.
4. Aspect detects that `X-Peer-Forwarded` header is absent (originating request).
5. Aspect issues unicast `GET /api/aircrafts/CS-TRP` to `Instance 2` (`:8082`), attaching `X-Peer-Forwarded: true` and propagating the `Authorization` header.
6. `Instance 2` finds the record in its local database and returns `200 OK` with JSON payload.
7. `Instance 1` receives the peer response and returns `200 OK` transparently to the client.

#### Scenario B: Local Miss -> Peer Miss (Global 404 Flow)
1. `Instance 1` queries local DB -> not found.
2. `Instance 1` forwards request with `X-Peer-Forwarded: true` to `Instance 2`.
3. `Instance 2` queries its DB -> not found -> throws `AircraftNotFoundException`.
4. `Instance 2` detects `X-Peer-Forwarded: true` -> **Loop Prevention**: stops forwarding, rethrows exception.
5. `Instance 2` returns `404 Not Found`.
6. `Instance 1` exhausts peer list and returns `404 Not Found` to the client.

#### Scenario C: Peer Unreachable / Timeout (Fault Tolerance Flow)
1. `Instance 1` attempts to connect to `Instance 2`.
2. `Instance 2` is offline, crashed, or experiencing a network partition.
3. `PeerForwardingAspect` catches `ResourceAccessException` / `SocketTimeoutException`.
4. Aspect logs a warning, handles the network failure gracefully, and returns `404 Not Found` (or cached/graceful degradation) without crashing the calling instance.

---

## 7. Architectural Decisions & Trade-Offs

1. **CAP Theorem Alignment (AP System):**
   * Prioritizes **Availability (A)** and **Partition Tolerance (P)** over strict immediate consistency.
   * Replicas achieve **Eventual Consistency**; status updates made on one instance will be retrieved by peers via forwarding.
2. **Zero Shared Database Architecture:**
   * Eliminates single points of failure at the data tier. Each microservice instance operates its own H2 database file/memory space.
3. **Loop Prevention via Custom Headers:**
   * The `X-Peer-Forwarded: true` header ensures that query propagation is single-hop (unicast) and avoids distributed cascading storms or infinite forwarding loops.
4. **Optimistic Concurrency Control:**
   * Utilizes JPA `@Version` counters. If two operators simultaneously alter the operational status of the same aircraft, the second transaction is rejected with an HTTP `409 Conflict` (or `ConcurrencyException`), preserving data integrity.

---

## 8. Verification & How to View Diagrams

All diagrams are written in standard PlantUML using the official C4-PlantUML library:
* In **IntelliJ IDEA** or **VS Code**, install the *PlantUML* extension to preview `.puml` files with real-time rendering.
* Alternatively, paste the diagram code into any online PlantUML server (e.g., [PlantText](https://www.planttext.com/) or [PlantUML Web Server](http://www.plantuml.com/plantuml/uml/)).
