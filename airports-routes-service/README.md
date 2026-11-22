# Airports & Routes Service — P2

## CQRS read models and Unicorns endpoint

`GET /api/v2/unicorns` returns JSON airplane mood statuses from the
`airplane_mood_read_model` table. This is a query-side projection: callers read
the projection rather than querying the aircraft service's write model.
The projection is populated only by the aircraft mood event stream. It remains
empty until a message is consumed; no local demo entries are inserted. The query endpoint is read-only and protected by the
service's normal JWT authentication.

With AMQP enabled, the service consumes `aircraft.mood.changed` messages from
the durable `aisafe.aircraft.events` topic exchange. The JSON event contract is:

```json
{
  "eventId": "event-uuid",
  "correlationId": "correlation-uuid",
  "registration": "DEMO-001",
  "moodStatus": "AVAILABLE",
  "aircraftStatus": "AVAILABLE",
  "timestamp": "2026-11-03T10:00:00Z"
}
```

The consumer updates only the read model and ignores an event older than the
projection's current `timestamp`, so delayed or repeated deliveries do not
roll back a newer airplane mood. The aircraft service publishes this contract to the same exchange and routing key.

Import `airports-routes-p2.postman_collection.json` (or `docs/postman/airports-routes-p2.postman_collection.json`) and set
`jwt_token` to a valid service JWT to exercise the required endpoint.

Routes also expose CQRS endpoints: commands are available under
`/api/v1/routes`, while `GET /api/v2/routes` queries the separate
`route_read_model` projection. Route commands publish events to the durable
`aisafe.routes.events` exchange using `routes.route.changed`; each instance
consumes from its own durable queue (`aisafe.airports.instance1.routes-read`
or `aisafe.airports.instance2.routes-read`). Create, update, deactivate and
delete events update the projection asynchronously, with stale events ignored
and deleted routes retained as tombstones but excluded from queries.

## Two isolated instances

Each profile uses its own in-memory H2 database and distinct durable RabbitMQ
queues. Distinct queues ensure both instances receive the same published mood
events rather than competing for messages on one shared queue:

Run these commands from the repository root with Java 21 installed, in separate
terminals:

```bash
JWT_SECRET='<base64-key-with-at-least-32-bytes>' \
P2P_JWT_SECRET='<a-different-base64-key-with-at-least-32-bytes>' \
bash ./mvnw -pl airports-routes-service spring-boot:run \
  -Dspring-boot.run.profiles=jpa,instance1

JWT_SECRET='<base64-key-with-at-least-32-bytes>' \
P2P_JWT_SECRET='<a-different-base64-key-with-at-least-32-bytes>' \
bash ./mvnw -pl airports-routes-service spring-boot:run \
  -Dspring-boot.run.profiles=jpa,instance2
```

Instance 1 listens on port `8092` with database `airports_routes_db_1`;
instance 2 listens on `8093` with database `airports_routes_db_2`. Configure
`RABBITMQ_HOST`, `RABBITMQ_PORT`, `RABBITMQ_USERNAME`, and
`RABBITMQ_PASSWORD` for the broker. Messaging is disabled by default so the
service remains runnable in a local environment without RabbitMQ.

## P2 final run instructions

Install JDK 21 and start RabbitMQ only when AMQP scenarios are needed.
The following commands use two isolated H2 databases and one peer per instance:

```bash
JWT_SECRET='<base64-32-byte-key>' \
P2P_JWT_SECRET='<base64-32-byte-key>' \
bash ./mvnw -pl airports-routes-service spring-boot:run \
  -Dspring-boot.run.profiles=jpa,instance1
```

```bash
JWT_SECRET='<base64-32-byte-key>' \
P2P_JWT_SECRET='<base64-32-byte-key>' \
bash ./mvnw -pl airports-routes-service spring-boot:run \
  -Dspring-boot.run.profiles=jpa,instance2
```

Use the same `JWT_SECRET` and `P2P_JWT_SECRET` in both replicas. The P2P
peer configuration is `peer-id=url`; instance 1 points to `8093` and instance
2 points to `8092`. Forwarding stops after receiving `X-Peer-Forwarded: true`,
uses 2-second connect/read timeouts and continues gracefully after `404` or
network failure.

| Resource | Instance 1 | Instance 2 |
|---|---|---|
| API | http://localhost:8092 | http://localhost:8093 |
| Swagger | http://localhost:8092/swagger-ui/index.html | http://localhost:8093/swagger-ui/index.html |
| H2 | `airports_routes_db_1` | `airports_routes_db_2` |
| Airport queue | `aisafe.airports.instance1.airport-read` | `aisafe.airports.instance2.airport-read` |
| Aircraft mood queue | `aisafe.airports-routes.instance1.aircraft-mood` | `aisafe.airports-routes.instance2.aircraft-mood` |
| Route queue | `aisafe.airports.instance1.routes-read` | `aisafe.airports.instance2.routes-read` |

The command API remains available at both `/api/airports` and
`/api/v1/airports`. The CQRS read endpoint is `GET /api/v2/airports`.
Import `airports-routes-p2.postman_collection.json` and run Login
before the write and query requests. The collection includes tests for:
- Authentication (login & token extraction)
- CQRS airport write (`/api/v1/airports`) & read model query (`/api/v2/airports`)
- P2P forwarding & cluster 404 loop prevention
- AMQP status update event publication
- CQRS query-side `/api/v2/unicorns` mood projection verification
- P2P local hit, remote hit, forwarded-loop prevention and global 404
- Event projection persistence, replay idempotence, stale-event rejection and delete tombstones

## CQRS read model for airports

Airport commands and queries are deliberately exposed through separate paths:

* Commands use `POST`, `PATCH` and `DELETE` under `/api/airports`.
* Queries use `GET /api/v2/airports`.
* The command side stores the aggregate in the normal airport write model.
* The query side stores a separate `airport_read_model` JPA entity.
* When AMQP is enabled, successful airport commands publish
  `AirportRegistered`, `AirportStatusUpdated` or `AirportDetailsUpdated` events
  to the `aisafe.airports.events` topic exchange.
* The projection listener consumes the `airports.airport.changed`
  routing key and updates only the read model.

Each event contains `eventId`, `correlationId`, `eventType`, `iataCode` and
`occurredAt`. The AMQP message also carries `correlationId`, `eventType` and
`aggregateId` as message properties.

The instances use distinct durable queues:

* Instance 1: `aisafe.airports.instance1.airport-read`
* Instance 2: `aisafe.airports.instance2.airport-read`

For a local P2 demonstration, enable AMQP and start RabbitMQ first:

```bash
AMQP_ENABLED=true JWT_SECRET='<base64-jwt-key>' P2P_JWT_SECRET='<base64-p2p-key>' \
bash ./mvnw -pl airports-routes-service spring-boot:run \
  -Dspring-boot.run.profiles=jpa,instance1
```

Create or update an airport, wait briefly for the event to be consumed, and
query `GET /api/v2/airports?name=LIS` with a valid JWT. A broker outage does not
block the command endpoint, but the read model will remain temporarily stale
until the event is redelivered; this is eventual consistency.
