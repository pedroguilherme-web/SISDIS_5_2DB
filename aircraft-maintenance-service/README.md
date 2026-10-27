# Aircraft & Maintenance Service (Autonomous Component)

**Assignment 1 – Sistemas Distribuídos (LETI-SIDIS / PSOFT)**  
**Student:** Martim Alves (1240690)  
**Academic Year:** 2026/2027  

---

## 1. Overview & Domain Boundaries

This component manages:
* **Aircraft Models:** Specifications (range, fuel, speed, passenger capacity) and catalog images.
* **Aircraft Fleet Instances:** Registration numbers, manufacturing dates, operational statuses (`AVAILABLE`, `UNDER_MAINTENANCE`, `INACTIVE`).
* **Maintenance Management:** Maintenance templates (intervals in flight hours and days, task checklists), parts inventory, work orders, turnaround metrics, and maintenance due alerts.

### Distributed Characteristics
* **Autonomous Spring Boot project:** Runs with its own `pom.xml` and isolated dependencies.
* **Zero Shared Database:** Each replica instance runs an independent in-memory H2 database.
* **Multi-Instance Replication (3 Replicas):** Supports parallel execution on distinct ports (`8081`, `8082`, `8083`).
* **Peer-to-Peer HTTP GET Forwarding:** Unicast query propagation across a 3-node cluster. If Instance 1 misses, it checks Instance 2; if Instance 2 misses, it queries Instance 3.
* **Loop Prevention:** Employs the `X-Peer-Forwarded: true` header to prevent infinite circular forwarding.
* **Fault Tolerance:** Graceful handling of network timeouts and offline peer instances.
* **Optimistic Concurrency Control:** Enforced via JPA `@Version` on entities.

---

## 2. Configuration & Ports (3 Instances)

| Instance Profile | Server Port | Database URL | Configured Peers (`peers.urls`) |
| :--- | :--- | :--- | :--- |
| `instance1` | `8081` | `jdbc:h2:mem:aircraft_maintenance_db_1` | `http://localhost:8082,http://localhost:8083` |
| `instance2` | `8082` | `jdbc:h2:mem:aircraft_maintenance_db_2` | `http://localhost:8081,http://localhost:8083` |
| `instance3` | `8083` | `jdbc:h2:mem:aircraft_maintenance_db_3` | `http://localhost:8081,http://localhost:8082` |

---

## 3. How to Run the Instances

From the `aircraft-maintenance-service` directory (or directly via the pre-configured run configurations in IntelliJ):

### Terminal 1 (Start Instance 1 on Port 8081):
```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=instance1
```

### Terminal 2 (Start Instance 2 on Port 8082):
```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=instance2
```

### Terminal 3 (Start Instance 3 on Port 8083):
```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=instance3
```

---

## 4. API Documentation & Health Checks

Once running:
* **Swagger UI (Instance 1):** [http://localhost:8081/swagger-ui/index.html](http://localhost:8081/swagger-ui/index.html)
* **Swagger UI (Instance 2):** [http://localhost:8082/swagger-ui/index.html](http://localhost:8082/swagger-ui/index.html)
* **Swagger UI (Instance 3):** [http://localhost:8083/swagger-ui/index.html](http://localhost:8083/swagger-ui/index.html)
* **Actuator Health (Instance 1):** [http://localhost:8081/actuator/health](http://localhost:8081/actuator/health)
* **H2 Console:** [http://localhost:8081/h2-console](http://localhost:8081/h2-console), [http://localhost:8082/h2-console](http://localhost:8082/h2-console), [http://localhost:8083/h2-console](http://localhost:8083/h2-console) (Credentials: `admin` / `admin`)

---

## 5. Postman Collection & Evaluation Scenarios

Import the file [`aisafe-aircraft-maintenance.postman_collection.json`](aisafe-aircraft-maintenance.postman_collection.json) into Postman:

1. **Auth Login:** Logs in as `atcc` (password `atcc123`) and automatically saves the JWT token into the collection environment variable.
2. **Local Data Retrieval:** Executes `GET http://localhost:8081/api/aircrafts/CS-TKA`. Data is found locally on Instance 1 and returned with `200 OK`.
3. **Multi-Peer Forwarding (Demonstration for evaluation):**
   * Register a new aircraft `CS-PTB` on **Instance 2** (`POST http://localhost:8082/api/aircrafts`).
   * Query `CS-PTB` through **Instance 1** (`GET http://localhost:8081/api/aircrafts/CS-PTB`) $\rightarrow$ Instance 1 forwards unicast to Instance 2 $\rightarrow$ returns `200 OK`.
   * Register a new aircraft `CS-PTC` on **Instance 3** (`POST http://localhost:8083/api/aircrafts`).
   * Query `CS-PTC` through **Instance 1** (`GET http://localhost:8081/api/aircrafts/CS-PTC`) $\rightarrow$ Instance 1 misses, checks Instance 2 (misses), queries Instance 3 $\rightarrow$ returns `200 OK`.
4. **Resilience & Fault Tolerance:**
   * Query non-existent aircraft `CS-ZZZ` on Instance 1 $\rightarrow$ queries all configured peers $\rightarrow$ none has it $\rightarrow$ returns clean `404 Not Found` without circular storms.
   * Stop an instance, query from another $\rightarrow$ timeout is caught and logged, remaining peers are queried, returning `404 Not Found` gracefully without crashing.
