# Hermes — Distributed Order Fulfillment Engine


A microservices order processor built for the thing enterprise backends actually
get judged on: **reliable, high-throughput transaction processing.** An API accepts
orders and never blocks on inventory; a pool of workers reserves stock inside
real database transactions; and a Grafana dashboard lets you *watch* the Kafka
queue back up and drain as you blast it with load.

```
            POST /api/orders                Kafka topic                 @Transactional
 client ─────────────────────▶  order-api ───────────────▶  fulfillment-worker ──▶  PostgreSQL
                                  (202)      orders.placed     (consumer group,         (orders +
                                   │                            row-locked deduct)       inventory)
                                   └── persists PENDING                  │
                                                                         └── retries ▶ orders.placed.DLT
        Prometheus  ◀── /actuator/prometheus + kafka-exporter ──▶  Grafana dashboard
```

## Why it's built this way

- **The API returns `202 Accepted`, not `200`.** It persists the order as `PENDING`
  and publishes to Kafka — it never waits for inventory. That decoupling is the
  whole point: the API stays fast and available under a load spike while the
  workers drain the backlog at their own pace. The lag you see in Grafana *is*
  that backlog.
- **Inventory is deducted under a pessimistic row lock**
  (`SELECT … FOR UPDATE`, see [`ProductRepository`](common/src/main/java/com/hermes/common/repository/ProductRepository.java)).
  Many workers can process orders for the same SKU concurrently without
  overselling, because they serialise on the product row. There's also an
  optimistic `@Version` column as a second line of defence.
- **Consumers are idempotent.** Kafka gives at-least-once delivery, so a message
  can arrive twice. The worker checks the order is still `PENDING` before acting,
  making redelivery a safe no-op — verified by a unit test.
- **Poison messages don't wedge the partition.** A failing message is retried with
  back-off, then parked on a dead-letter topic (`orders.placed.DLT`) — the same
  DLQ pattern as Argus, expressed in Spring Kafka's `DefaultErrorHandler` +
  `DeadLetterPublishingRecoverer`.
- **Producer is idempotent + `acks=all`.** No duplicate or lost events on the
  publish side either.
- **Orders are keyed by SKU on the topic**, so all orders for one product land on
  one partition and are processed in order — minimising lock contention.

## The stack

| Concern            | Choice                                             |
|--------------------|----------------------------------------------------|
| Language / runtime | Java 17                                             |
| Framework          | Spring Boot 3.3 (Web, Data JPA, Actuator, Kafka)    |
| Persistence        | PostgreSQL 16 + Hibernate, pessimistic + optimistic locking |
| Messaging          | Apache Kafka 3.8 (KRaft, no ZooKeeper)             |
| Metrics            | Micrometer → Prometheus, kafka-exporter for lag    |
| Dashboards         | Grafana (datasource + dashboard auto-provisioned)  |
| Load testing       | k6                                                 |
| Build              | Multi-module Maven, multi-stage Docker builds      |

Three modules: [`common`](common) (entities, repos, the `OrderPlacedEvent`),
[`order-api`](order-api) (REST + producer), [`fulfillment-worker`](fulfillment-worker)
(consumer + transactional fulfilment).

## Run it

Everything builds and runs in Docker — no local JDK/Maven needed.

```bash
docker compose up --build
```

This brings up Postgres, Kafka, both services, kafka-exporter, Prometheus and
Grafana. The API seeds a 200-SKU synthetic catalogue on first boot.

| Service    | URL                                            |
|------------|------------------------------------------------|
| Order API  | http://localhost:8080/api/orders               |
| Grafana    | http://localhost:3000 (anonymous, or admin/admin) |
| Prometheus | http://localhost:9090                          |

Place one order:

```bash
curl -i -X POST http://localhost:8080/api/orders \
  -H 'Content-Type: application/json' \
  -d '{"customerId":"cust-1","sku":"SKU-0007","quantity":2}'

# then check it was fulfilled by the worker:
curl http://localhost:8080/api/orders/stats
```

## The demo: blast it and watch the lag

```bash
# 200 orders/sec for 60s (defaults)
k6 run loadtest/k6-blast.js

# crank it to make the queue visibly back up
k6 run -e RATE=800 -e DURATION=2m loadtest/k6-blast.js
```

Open the **Hermes — Order Fulfillment Engine** dashboard in Grafana and watch:

- **Order ingest rate** climb to the k6 rate,
- **Kafka consumer lag** spike as orders queue up, then drain,
- **Fulfilment outcomes** split into `fulfilled` vs `rejected_out_of_stock` as
  popular SKUs sell out.

Scale the workers to drain faster and watch the lag fall:

```bash
docker compose up -d --scale fulfillment-worker=3
```

## Using the real Olist dataset

The synthetic catalogue makes the repo run instantly. To use the real
**Brazilian E-Commerce Public Dataset by Olist** (~33k products, ~100k orders),
download the CSVs and run `scripts/load_olist.py` — see [`data/README.md`](data/README.md).

## Tests

```bash
mvn test
```

- [`FulfillmentServiceTest`](fulfillment-worker/src/test/java/com/hermes/worker/service/FulfillmentServiceTest.java)
  — fulfilment, out-of-stock rejection, unknown product, and **idempotent
  redelivery**, all against an in-memory DB (no Kafka/Postgres needed).
- [`OrderControllerTest`](order-api/src/test/java/com/hermes/orderapi/web/OrderControllerTest.java)
  — validation + that accepting an order publishes the right event.

CI runs `mvn verify` on every push ([ci.yml](.github/workflows/ci.yml)).

## What I'd do next

- **Transactional outbox** on the API side: today it saves then publishes in one
  `@Transactional` method, but a crash between commit and publish could lose an
  event. An outbox table + relay (or Debezium CDC) makes publish exactly-once.
- **Saga / compensation**: reservation → payment → shipping as separate steps,
  with compensating actions if a later step fails.
- **Per-service databases**: split orders and inventory into their own schemas to
  make the microservice boundary real, syncing via events.
- **Schema registry** (Avro/Protobuf) instead of JSON for forward/backward-compatible
  event evolution.
- **Autoscale workers on lag** — drive replica count off the `kafka_consumergroup_lag`
  metric the dashboard already exposes.
```
