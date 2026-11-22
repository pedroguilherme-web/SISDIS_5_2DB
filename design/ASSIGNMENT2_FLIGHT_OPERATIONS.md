# Assignment 2 — Flight Operations Service: CQRS, AMQP e Database-per-Instance

**Unidade curricular:** Sistemas Distribuídos (LETI-SIDIS 2026/2027) — ISEP
**Componente:** `flight-operations-service/` (réplicas 8071 e 8072)

Este documento descreve como o serviço de operações de voo evoluiu no Assignment 2, que decisões foram tomadas, que alternativas foram consideradas e que limites ficam. Os comandos para executar estão em [`flight-operations-service/docs/COMMANDS.md`](../flight-operations-service/docs/COMMANDS.md).

---

## 1. Ponto de partida (Assignment 1)

- Três serviços, cada réplica com a sua base de dados H2.
- Cada voo vive numa só réplica (dados particionados). As leituras perguntavam às outras réplicas por HTTP e juntavam as respostas (scatter-gather).
- O agendamento chamava o serviço de aeronaves e o de rotas por HTTP **síncrono**, a cada pedido. Se um deles estivesse em baixo, não se agendava nada (acoplamento temporal).
- Os outros serviços liam horas de voo e contagens por HTTP.

---

## 2. O que mudou

```
             comandos (HTTP REST)                         consultas (HTTP REST)
 POST /api/flights  PATCH /api/flights/{id}/status        GET /api/v2/flights...  GET /api/v2/unicorns
            │                                                          ▲
            ▼                                                          │
   ┌──────────────────┐   evento após commit   ┌─────────────────┐     │
   │  Write model     │ ─────────────────────► │  FlightProjector│ ────┤ flight_read_model
   │  scheduled_flight│   (in-process)         │  (idempotente)  │     │ aircraft_activity_read_model
   └──────────────────┘                        └─────────────────┘     │
            │                                          ▲               │
            │ flight.scheduled / flight.status.changed │               │
            │ flight.completed                         │               │
            ▼                                          │               │
   ┌───────────────────────────────── RabbitMQ: aisafe.exchange (topic) ──────────────────────────────┐
   │  fila por réplica: aisafe.flights.<id>.flight-read-model   ◄── flight.scheduled, flight.status.* │
   │  fila por réplica: aisafe.flights.<id>.aircraft-catalog    ◄── aircraft.registered, aircraft.status.changed (Aircraft & Maintenance)
   │  aisafe.routes.events ──► aisafe.flights.<id>.route-catalog ◄── routes.# (Airports & Routes)      │
   │  flight.completed ──► fila do Aircraft & Maintenance (soma horas de voo na projeção dele)        │
   └───────────────────────────────────────────────────────────────────────────────────────────────────┘
```

### 2.1 CQRS

| Lado | Onde | O que guarda | Quem escreve |
|---|---|---|---|
| **Escrita** (commands) | `scheduled_flight` | Só os voos criados nesta réplica, com regras de negócio, `@Version` e `revision` | Casos de uso `ScheduleFlight`, `ChangeFlightStatus`, `ImportFlights` |
| **Leitura** (queries) | `flight_read_model` | Os voos de **todas** as réplicas, desnormalizados (duração já calculada) | Só o `FlightProjector`, a partir de eventos |
| **Leitura** | `aircraft_activity_read_model` | Por aeronave: nº de voos, concluídos, cancelados, horas, último estado, humor | Só o `FlightProjector` |

- Os comandos nunca leem o modelo de leitura e as consultas `/api/v2` nunca leem o de escrita (`FlightQueryService` só conhece as tabelas de leitura).
- O modelo de leitura está otimizado para as perguntas que se fazem: filtros indexados (aeronave, rota, estado), agregados já calculados, uma linha por resposta, sem JOINs nem chamadas a outras réplicas.
- As rotas `/api/flights` (v1) continuam a existir para os clientes que já as usam, incluindo o Martim e o Pedro. A v2 é a API de consultas CQRS, como fez o Pedro com `/api/v2/routes`.

### 2.2 Eventos

| Routing key | Payload | Produtor → consumidores |
|---|---|---|
| `flight.scheduled` | `FlightEvent` (estado completo do voo + `revision`) | Flight Ops → todas as réplicas de Flight Ops |
| `flight.status.changed` | `FlightEvent` (com `previousStatus`) | Flight Ops → todas as réplicas de Flight Ops |
| `flight.completed` | `{flightId, aircraftRegistration, flightDurationHours}` | Flight Ops → Aircraft & Maintenance (contrato dele) |
| `aircraft.registered`, `aircraft.status.changed` | eventos do Martim | Aircraft & Maintenance → Flight Ops (catálogo + humor) |
| `routes.route.changed` (exchange `aisafe.routes.events`) | `RouteChangedEvent` do Pedro | Airports & Routes → Flight Ops (catálogo de rotas) |

Decisões:

- **Event-carried state transfer.** O evento leva o estado inteiro do voo, por isso um consumidor constrói a vista sem voltar a perguntar ao produtor.
- **JSON puro**, com `content-type: application/json` e sem cabeçalho de tipo Java. Qualquer serviço o lê, e campos desconhecidos são ignorados (tolerant reader), por isso o produtor pode acrescentar campos sem partir os consumidores.
- **Metadados nas propriedades AMQP**: `messageId`, `type`, `timestamp` e o cabeçalho `sourceInstance`. O `flight.completed` leva no corpo exatamente os 3 campos que a classe do Martim aceita.
- **Transactional outbox.** O comando não publica diretamente no broker: grava as mensagens na tabela `outbox_message`, **na mesma transação** que o voo. Ou ficam gravados os dois, ou nenhum. Depois do commit, o `OutboxRelay` publica-as por ordem e só as marca como publicadas quando o broker confirma (publisher confirms). Se o broker estiver em baixo, ficam pendentes e o relay volta a tentar a cada 2 s. A entrega é "pelo menos uma vez": um eventual duplicado é ignorado pela revisão.

### 2.3 Topologia: publish/subscribe por réplica

Cada réplica declara as **suas** filas (o nome inclui o id da instância) e liga-as à exchange. Assim:

- **todas** as réplicas recebem **todos** os eventos (publish/subscribe), em vez de competirem pela mesma mensagem (competing consumers);
- é isso que põe os voos do cluster inteiro em cada modelo de leitura, uma forma de replicação assíncrona do lado das leituras;
- as filas são **duráveis**: enquanto uma réplica está em baixo, os eventos ficam à espera no broker e são consumidos quando ela volta (desacoplamento temporal).

### 2.4 Comunicação entre serviços: de HTTP síncrono para eventos

O agendamento precisa do estado, lugares e alcance da aeronave e dos mínimos da rota. Passou a usar **catálogos locais** (`aircraft_catalog`, `route_catalog`), alimentados pelos eventos do Martim e do Pedro:

1. Entrada atualizada há menos de `aisafe.catalog.max-age` (10 min por omissão): responde localmente, **sem HTTP**, mesmo que o outro serviço esteja em baixo.
2. Entrada em falta ou antiga: pergunta por HTTP (com failover entre instâncias) e guarda a resposta.
3. Entrada antiga e serviço em baixo: **recusa com 503**. Aceitar um voo para um avião que pode estar em manutenção é pior do que recusar.
4. Com a mensageria desligada, os catálogos não são usados, porque nada os mantém atualizados.

Os endpoints HTTP que o Martim e o Pedro chamam (horas, contagens) continuam disponíveis. O Martim recebe agora também `flight.completed` por AMQP.

---

## 3. Modelo de consistência

| Dados | Garantia |
|---|---|
| Write model de cada réplica | **ACID** local (transação + `@Version`) |
| Read model da própria réplica | Atualizado **logo após o commit**, no mesmo processo: o cliente lê o que acabou de escrever, mesmo sem broker |
| Read model das outras réplicas | **Consistência eventual** (BASE): milissegundos com o broker ligado; com o broker em baixo, até à reconstrução |
| Catálogos de aeronaves/rotas | Eventual, com idade máxima configurável |

**Idempotência e ordem.** Cada voo tem uma `revision` (0 ao criar, +1 por mudança de estado), incluída em cada evento. O projetor só aplica um evento se a revisão for maior do que a guardada. Por isso:

- o mesmo evento recebido duas vezes (no processo e pelo broker) não tem efeito;
- um evento atrasado (por exemplo `SCHEDULED` depois de `IN_FLIGHT`) é ignorado;
- não depende dos relógios das máquinas.

Os agregados por aeronave são **recalculados** a partir das vistas dos voos, em vez de incrementados, por isso também são idempotentes.

**Outbox transacional**: nenhum evento descreve uma alteração desfeita (rollback), e nenhuma alteração confirmada fica sem o seu evento, mesmo com o broker em baixo.

**Uma réplica dona por aeronave.** A dona é escolhida por `hash(matrícula) mod n` sobre a lista ordenada das réplicas, igual em todas. Todos os voos de uma aeronave são criados na dona (um pedido a outra réplica é reencaminhado), e na dona um bloqueio por aeronave na base de dados (`SELECT ... FOR UPDATE`) serializa "verificar sobreposição e inserir". Se a dona estiver em baixo, agendar essa aeronave dá 503. Para esta regra (um avião não voa dois voos ao mesmo tempo) escolheu-se **consistência** em vez de disponibilidade; as leituras e os outros aviões continuam disponíveis.

No teorema CAP o sistema é **AP**. Com uma partição, cada réplica continua a aceitar comandos e a responder a consultas com os dados que tem, e converge quando a rede volta.

---

## 4. Tolerância a falhas

| Falha | Comportamento | Demonstrado em |
|---|---|---|
| Broker em baixo | Comandos funcionam e o read model local é atualizado; as mensagens ficam no outbox e são publicadas quando o broker volta | `OutboxBrokerOutageIntegrationTest` |
| Pedidos simultâneos para o mesmo avião | Reencaminhados para a réplica dona e serializados pelo bloqueio: só um é aceite | `ScheduleConcurrencyIntegrationTest` (50 pares), 20 pares reais nas duas réplicas |
| Réplica dona em baixo | Agendar os aviões dela dá 503; as leituras e os outros aviões funcionam | Postman M.5 |
| Réplica em baixo | Os eventos esperam na fila durável dela; ao voltar, consome-os e corre a reconstrução no arranque | Teste manual de ponta a ponta |
| Réplica arranca vazia (H2 em memória) | `ReadModelRebuilder` lê o write model de todas as réplicas pelo P2P do `shared-core` e reprojeta, ignorando o que já tem pela revisão | `POST /api/v2/flights/read-model/rebuild`, Postman 5.14 |
| Mensagem malformada | Rejeitada sem reenvio (seria rejeitada para sempre); o consumidor continua | `AmqpMessagingIntegrationTest` |
| Falha temporária ao projetar | 3 tentativas (retry do listener) e depois descartada | Configuração `spring.rabbitmq.listener.simple.retry.*` |
| Serviço de aeronaves em baixo | Aeronave vista há pouco: agenda a partir do catálogo. Nunca vista: 503 com `Retry-After` | Postman M.3 e M.4 |

---

## 5. Database-per-Service e várias instâncias

- Cada **instância** tem a sua base de dados própria: os perfis `instance1` e `instance2` usam `flight_operations_db_1` e `_2`. Nenhuma réplica lê a base de outra; os dados só passam por HTTP ou AMQP.
- Dentro de cada base, as tabelas de escrita e de leitura são separadas.
- O perfil `durable` passa a base para ficheiro, para que o write model, e com ele a fonte da verdade, sobreviva a um reinício.

---

## 6. `/api/v2/unicorns` e o CQRS

O enunciado pede um endpoint `/api/v2/unicorns` com o humor dos aviões. No serviço de voos:

1. O **write model não sabe nada de humores**: `ScheduledFlight` só tem regras de aviação.
2. O humor é calculado pelo **projetor** quando chegam eventos (`AirplaneMood`, uma função pura), combinando dois tipos de factos:
   - eventos de voo de todas as réplicas: em voo → `SOARING`, atrasado → `IMPATIENT`, último cancelado → `DISAPPOINTED`, voos concluídos → `HAPPY`, mais de 1000 h → `PROUD`, sem voos concluídos → `EXCITED`;
   - eventos de aeronave do Martim: manutenção → `UNDER_CARE`, inativo → `GRUMPY`.
3. O resultado fica em `aircraft_activity_read_model`. O `GET /api/v2/unicorns` **só lê essa tabela**: zero cálculos, zero JOINs e zero chamadas a outros serviços no momento do pedido.

É o exemplo típico de CQRS: uma vista feita para um cliente específico, que se acrescenta sem tocar no modelo de escrita. Usa os mesmos nomes de campos do `/api/v2/unicorns` do Martim (`registrationNumber`, `mood`, `sparkleLevel`, `moodReason`) e acrescenta os factos de voo.

---

## 7. Alternativas consideradas

| Alternativa | Porque não (por agora) |
|---|---|
| Publicar diretamente após o commit (sem outbox) | Era a versão inicial: se o broker estivesse em baixo no commit, o evento perdia-se. Substituída pelo **outbox transacional** |
| Lock distribuído (ex.: Redis, ZooKeeper) para os conflitos | Exigiria mais um componente. Uma réplica dona por aeronave dá o mesmo resultado com o que já existe |
| **Event Sourcing** (o estado é a sequência de eventos) | Os eventos aqui são de integração, não a fonte da verdade. Exigiria guardar e reproduzir eventos para sempre |
| **Base de dados de leitura separada** (outra H2 ou Redis) | Separaria também a escalabilidade física. As tabelas separadas na mesma base já isolam os modelos, e o enunciado pede uma base por instância |
| **Replicar o write model** (cada voo em todas as réplicas) | Obrigaria a coordenar escritas concorrentes (consenso ou locks distribuídos). O CQRS replica só o lado de leitura, que tolera atrasos |
| **Fila partilhada entre réplicas** (como o Martim) | Competing consumers: cada evento chegaria a uma só réplica e os modelos de leitura divergiriam |
| **Kafka** | Retenção e replay dos eventos ajudariam a reconstruir, mas a equipa e o enunciado usam AMQP/RabbitMQ |

---

## 8. Limites conhecidos

- **Membros fixos.** A lista de réplicas é configuração. Acrescentar uma réplica muda a dona de alguns aviões e exige reiniciar todas com a mesma lista (o habitual seria consistent hashing com um serviço de descoberta).
- **Mensagens sem fila de destino.** Se um consumidor ainda não declarou a sua fila (por exemplo, um broker em memória acabado de reiniciar), a mensagem é confirmada mas não chega a ninguém. Com o RabbitMQ, filas duráveis e mensagens persistentes evitam isto; o `flight_read_model` recupera-se sempre com a reconstrução.
- **Consumidor do Martim não idempotente.** Com entrega "pelo menos uma vez", um `flight.completed` duplicado somaria as horas duas vezes na projeção dele.
- **Write model em memória.** Por omissão, os voos de uma réplica perdem-se se ela reiniciar. O perfil `durable` resolve.
- **Topologia da equipa.** O Martim usa uma só fila para todas as réplicas e vários listeners nessa fila, por isso um `flight.completed` pode ser consumido por um método que não é o dele. O Pedro espera eventos de humor numa exchange (`aisafe.aircraft.events`) onde ninguém publica. O serviço de voos encaixa nas duas convenções que existem.

---

## 9. Testes

| Teste | O que prova |
|---|---|
| `AmqpMessagingIntegrationTest` | Aplicação completa contra um **broker AMQP real**: Qpid embebido localmente e **RabbitMQ** na CI do GitHub (contentor de serviço, variável `RABBITMQ_TEST_PORT`): evento de outra réplica chega ao read model, evento do Martim muda catálogo e humor, evento do Pedro marca uma rota apagada, concluir um voo envia `flight.completed` com o contrato do Martim, mensagem malformada não para o consumidor |
| `FlightProjectorTest` | Idempotência, eventos fora de ordem, agregados, humor (H2 real) |
| `FlightEventDispatcherTest`, `OutboxRelayTest` | Mensagens gravadas na transação, entregues só após o commit, por ordem, com confirmação; paragem na primeira falha |
| `OutboxBrokerOutageIntegrationTest` | Broker desligado durante um comando: o evento fica no outbox e é entregue quando o broker volta |
| `ScheduleConcurrencyIntegrationTest`, `HashFlightOwnershipTest` | 50 pares de agendamentos simultâneos nunca aceitam dois voos sobrepostos; todas as réplicas escolhem a mesma dona |
| `CatalogClientsTest` | Quando o catálogo local é confiado e quando se recorre a HTTP |
| `FlightSecurityIntegrationTest` | Acesso a `/api/v2/flights`, `/api/v2/unicorns` (público) e à reconstrução (ADMIN) |
| Postman, pasta "5. Assignment 2" | Os mesmos cenários de ponta a ponta com os três serviços e o broker |
