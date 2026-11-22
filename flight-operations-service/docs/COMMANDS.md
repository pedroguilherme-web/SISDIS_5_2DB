# Guia Rápido de Comandos — Flight Operations Service

Comandos para compilar, testar e executar as réplicas do `flight-operations-service` em conjunto com os outros serviços do AISafe.

O projeto exige **Java 21**. Se o `java -version` do terminal mostrar outra versão, aponta o `JAVA_HOME` para um JDK 21 antes de correr o Maven.

---

## 1. Compilação e testes

* **Compilar e instalar todos os módulos (sem testes):**
  ```powershell
  ./mvnw clean install -DskipTests
  ```

* **Correr os testes deste serviço:**
  ```powershell
  ./mvnw test -pl flight-operations-service
  ```

* **Correr uma classe de teste específica:**
  ```powershell
  ./mvnw test -pl flight-operations-service -Dtest=HttpFlightPeerClientTest
  ```

---

## 2. Execução distribuída

### Broker de mensagens (assignment 2)

O serviço publica e consome eventos num broker AMQP em `localhost:5672` (utilizador `guest`/`guest`). Arranca-o antes dos serviços.

* **Com Docker (RabbitMQ, igual ao resto da equipa):**
  ```powershell
  docker compose -f docker-compose-rabbitmq.yml up -d
  ```
  Consola de gestão em `http://localhost:15672`.

* **Sem Docker:** o broker embebido usado nos testes (Apache Qpid, protocolo AMQP 0-9-1 como o RabbitMQ) também corre como processo:
  ```powershell
  ./mvnw -q -pl flight-operations-service test-compile dependency:build-classpath "-Dmdep.outputFile=target/cp.txt" "-Dmdep.includeScope=test"
  java -cp "flight-operations-service/target/test-classes;$(Get-Content flight-operations-service/target/cp.txt)" aisafe.flights.infrastructure.messaging.EmbeddedAmqpBroker 5672
  ```

Sem broker, o serviço também funciona: os comandos atualizam o modelo de leitura da própria réplica, mas as outras réplicas e serviços não são avisados (fica um aviso no log). Para desligar a mensageria por completo, usa `AMQP_ENABLED=false`.

### Serviços

Abre um terminal por instância, na raiz do projeto. As portas e os URLs abaixo são os que cada serviço usa por omissão.

| Serviço | Instâncias |
|---|---|
| Flight Operations (este) | 8071, 8072 |
| Aircraft & Maintenance | 8081, 8082, 8083 |
| Airports & Routes | 8092, 8093 |

### Flight Operations (perfis `instance1` e `instance2`)

Cada perfil define a porta, uma base de dados H2 própria e a réplica par (`peers.urls`).

* **Réplica 1 (8071):**
  ```powershell
  ./mvnw spring-boot:run -pl flight-operations-service -D"spring-boot.run.profiles=jpa,instance1"
  ```

* **Réplica 2 (8072):**
  ```powershell
  ./mvnw spring-boot:run -pl flight-operations-service -D"spring-boot.run.profiles=jpa,instance2"
  ```

### Aircraft & Maintenance

```powershell
./mvnw spring-boot:run -pl aircraft-maintenance-service -D"spring-boot.run.profiles=jpa,instance1"
```

### Airports & Routes

Os URLs por omissão deste serviço para voos e aeronaves estão trocados; define-os explicitamente. A mensageria deste serviço só liga com `AMQP_ENABLED=true`:

```powershell
$env:AMQP_ENABLED="true"
$env:FLIGHTS_SERVICE_URLS="http://localhost:8071,http://localhost:8072"
$env:AIRCRAFTS_SERVICE_URLS="http://localhost:8081,http://localhost:8082,http://localhost:8083"
./mvnw spring-boot:run -pl airports-routes-service -D"spring-boot.run.profiles=jpa,instance1"
```

---

## 3. Variáveis de ambiente do Flight Operations

| Variável | Por omissão | Para quê |
|---|---|---|
| `SERVER_PORT` | 8071 / 8072 (perfil) | Porta da réplica |
| `PEERS_URLS` | a outra réplica (perfil) | Réplicas deste serviço, separadas por vírgulas |
| `AIRCRAFT_SERVICE_URLS` | `http://localhost:8081,...8082,...8083` | Instâncias do serviço de aeronaves, tentadas por ordem |
| `ROUTES_SERVICE_URLS` | `http://localhost:8092,http://localhost:8093` | Instâncias do serviço de rotas, tentadas por ordem |
| `TRUSTED_NETWORKS` | `127.0.0.1/32,::1/128` | Redes (CIDR) que podem chamar os endpoints entre serviços sem token |
| `JWT_SECRET` | igual aos outros serviços | Chave dos tokens; tem de ser a mesma em todos os serviços |
| `H2_CONSOLE_ENABLED` | `false` | Liga a consola H2 em `/h2-console` |
| `BOOTSTRAP_ADMIN_USERNAME` / `_PASSWORD` | `admin` / `admin123` | Conta ADMIN criada no primeiro arranque |
| `AMQP_ENABLED` | `true` | Publica e consome eventos no broker |
| `RABBITMQ_HOST` / `RABBITMQ_PORT` | `localhost` / `5672` | Endereço do broker |
| `CATALOG_MAX_AGE` | `PT10M` | Quanto tempo uma aeronave/rota copiada localmente é usada sem perguntar ao serviço dono |
| `READ_MODEL_REBUILD_ON_STARTUP` | `true` | Ao arrancar, preenche o modelo de leitura a partir das outras réplicas |
| `CLUSTER_SELF_URL` | o URL da réplica (perfil) | Usado para escolher a réplica dona de cada avião; tem de corresponder a um dos `PEERS_URLS` das outras réplicas |
| `OUTBOX_RELAY_INTERVAL` | `PT2S` | Intervalo entre tentativas de publicar as mensagens pendentes do outbox |

### Perfis opcionais

* **`durable`**: guarda a base de dados (voos, utilizadores e audit log) em ficheiro, em `./data`, para sobreviver a um reinício.
  ```powershell
  ./mvnw spring-boot:run -pl flight-operations-service -D"spring-boot.run.profiles=jpa,instance1,durable"
  ```
* **`tls`**: HTTPS com TLS 1.3. Exige `TLS_KEYSTORE`, `TLS_KEYSTORE_PASSWORD`, `JWT_SECRET`, `P2P_JWT_SECRET` e as credenciais de bootstrap, sem valores por omissão. Os URLs das réplicas e dos outros serviços passam a `https://`, e os certificados deles têm de estar num truststore (`-Djavax.net.ssl.trustStore=...`).

---

## 4. Endpoints

| Método e caminho | Quem usa | Acesso |
|---|---|---|
| `POST /api/flights` | Clientes (US212) | ATCC |
| `GET /api/flights?aircraftId=` | Clientes (US213) | ATCC |
| `PATCH /api/flights/{id}/status` | Clientes | ATCC |
| `POST /api/flights/import` | Clientes (US225) | BACKOFFICE_OPERATOR |
| `GET /api/flights/reports/utilization` | Clientes (US229) | BACKOFFICE_OPERATOR |
| `GET /api/flights/aircraft/{reg}/hours` | Aircraft & Maintenance | perfil operacional ou rede de confiança |
| `GET /api/flights/aircraft/{reg}/hours-since?since=` | Aircraft & Maintenance | idem |
| `GET /api/flights/aircraft/{reg}/exists` | Aircraft & Maintenance | idem |
| `GET /api/flights/aircraft/{reg}/timeline` | Aircraft & Maintenance | idem |
| `GET /api/flights/top-models/hours?limit=` | Aircraft & Maintenance | idem |
| `GET /api/flights/top-models/assignments?limit=` | Aircraft & Maintenance | idem |
| `GET /api/flights/count?origin=&destination=` | Airports & Routes | idem |
| `GET /api/flights/aircraft/{reg}/conflicts?departure=&arrival=` | Réplicas deste serviço | idem |

Os estados de um voo seguem o ciclo `SCHEDULED → DELAYED → IN_FLIGHT → COMPLETED`, com `CANCELED` possível a partir de `SCHEDULED` ou `DELAYED`. As horas de voo e os rankings só contam voos `COMPLETED`.

### Consultas CQRS (assignment 2)

Respondidas só a partir do modelo de leitura da réplica, que contém os voos de todas as réplicas, recebidos por eventos (consistência eventual).

| Método e caminho | Acesso |
|---|---|
| `GET /api/v2/flights?aircraftId=&origin=&destination=&status=&page=&size=` | perfil operacional |
| `GET /api/v2/flights/{id}` | perfil operacional |
| `GET /api/v2/flights/aircraft/{reg}/activity` | perfil operacional |
| `GET /api/v2/unicorns` e `GET /api/v2/unicorns/{reg}` | público |
| `POST /api/v2/flights/read-model/rebuild` | ADMIN |

As decisões de desenho estão em [`design/ASSIGNMENT2_FLIGHT_OPERATIONS.md`](../../design/ASSIGNMENT2_FLIGHT_OPERATIONS.md).

---

## 4b. Testes de integração (Postman / Newman)

Com o broker e os serviços a correr, a partir da raiz do projeto:

```powershell
npx newman run flight-operations-service/docs/flight-operations-postman-collection.json --folder "0. Setup" --folder "1. Scheduling across replicas" --folder "2. Flight lifecycle" --folder "3. Inter-service endpoints" --folder "4. Reports and import" --folder "5. Assignment 2 - CQRS and AMQP"
```

A pasta "Manual - Fault tolerance" corre-se à parte, depois de desligar o serviço indicado em cada pedido.

---

## 5. URLs úteis

* **Swagger UI:** `http://localhost:8071/swagger-ui/index.html` (e `8072`)
* **OpenAPI (JSON):** `http://localhost:8071/v3/api-docs`
* **Coleção Postman:** `flight-operations-service/docs/flight-operations-postman-collection.json`
