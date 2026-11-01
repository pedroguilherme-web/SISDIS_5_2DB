# Guia Rápido de Comandos — AISafe (SISDIS_5_2DB)

Este documento reúne os comandos essenciais para compilar, testar, executar as instâncias distribuídas (P2P) e gerir o repositório do projeto AISafe.

---

## 1. Compilação e Testes (Maven)

* **Compilar e instalar todos os módulos (ignorando testes):**
  ```powershell
  ./mvnw clean install -DskipTests
  ```

* **Forçar atualização de dependências e limpar cache do Maven:**
  ```powershell
  ./mvnw clean install -U -DskipTests
  ```

* **Correr todos os testes do projeto (todos os módulos):**
  ```powershell
  ./mvnw test
  ```

* **Correr apenas os testes do `flight-operations-service`:**
  ```powershell
  ./mvnw test -pl flight-operations-service
  ```

* **Correr uma classe de teste específica:**
  ```powershell
  ./mvnw test -pl flight-operations-service -Dtest=ScheduledFlightJpaRepositoryTest
  ```

---

## 2. Execução Distribuída (Clones & P2P Forwarding)

Para testar o reencaminhamento P2P e a comunicação entre microsserviços, abre terminais separados na raiz do projeto e executa os comandos correspondentes:

### Flight Operations Service (Portas 8071 e 8072)
* **Instância 1 (Porta 8071):**
  ```powershell
  ./mvnw spring-boot:run -pl flight-operations-service -D"spring-boot.run.arguments=--server.port=8071 --spring.datasource.url=jdbc:h2:mem:flight_operations_db_1 --peers.urls=http://localhost:8072 --aisafe.bootstrap.admin.username=admin --aisafe.bootstrap.admin.password=admin123"
  ```

* **Instância 2 (Porta 8072):**
  ```powershell
  ./mvnw spring-boot:run -pl flight-operations-service -D"spring-boot.run.arguments=--server.port=8072 --spring.datasource.url=jdbc:h2:mem:flight_operations_db_2 --peers.urls=http://localhost:8071 --aisafe.bootstrap.admin.username=admin --aisafe.bootstrap.admin.password=admin123"
  ```

### Aircraft Maintenance Service (Portas 8081 e 8082)
* **Instância 1 (Porta 8081):**
  ```powershell
  ./mvnw spring-boot:run -pl aircraft-maintenance-service -D"spring-boot.run.arguments=--server.port=8081 --spring.datasource.url=jdbc:h2:mem:aircraft_db_1 --peers.urls=http://localhost:8082 --aisafe.bootstrap.admin.username=admin --aisafe.bootstrap.admin.password=admin123"
  ```

* **Instância 2 (Porta 8082):**
  ```powershell
  ./mvnw spring-boot:run -pl aircraft-maintenance-service -D"spring-boot.run.arguments=--server.port=8082 --spring.datasource.url=jdbc:h2:mem:aircraft_db_2 --peers.urls=http://localhost:8081 --aisafe.bootstrap.admin.username=admin --aisafe.bootstrap.admin.password=admin123"
  ```

### Airports & Routes Service (Portas 8091 e 8092)
* **Instância 1 (Porta 8091):**
  ```powershell
  ./mvnw spring-boot:run -pl airports-routes-service -D"spring-boot.run.arguments=--server.port=8091 --spring.datasource.url=jdbc:h2:mem:airports_routes_db_1 --peers.urls=http://localhost:8092 --aisafe.bootstrap.admin.username=admin --aisafe.bootstrap.admin.password=admin123"
  ```

* **Instância 2 (Porta 8092):**
  ```powershell
  ./mvnw spring-boot:run -pl airports-routes-service -D"spring-boot.run.arguments=--server.port=8092 --spring.datasource.url=jdbc:h2:mem:airports_routes_db_2 --peers.urls=http://localhost:8091 --aisafe.bootstrap.admin.username=admin --aisafe.bootstrap.admin.password=admin123"
  ```

---

## 3. URLs Úteis (Flight Operations Service)

* **Swagger UI (Instância 1):** `http://localhost:8071/swagger-ui/index.html`
* **Swagger UI (Instância 2):** `http://localhost:8072/swagger-ui/index.html`
* **OpenAPI Spec (JSON):** `http://localhost:8071/v3/api-docs`
* **H2 Console (Instância 1):** `http://localhost:8071/h2-console` (JDBC URL: `jdbc:h2:mem:flight_operations_db_1`)
* **Coleção Postman:** `docs/flight-operations-postman-collection.json`
