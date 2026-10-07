# Logistics Platform

A cloud-native **logistics management system** built on a microservice architecture.  
This repository is a Maven multi-module monorepo containing all platform services, shared libraries, local infrastructure orchestration, isolated persistence layers, event-driven messaging, and in-memory caching.

---

## Table of Contents

1. [Architecture Overview](#architecture-overview)  
2. [Service Decomposition](#service-decomposition)  
3. [Database-per-Service Architecture (PostgreSQL)](#database-per-service-architecture-postgresql)  
4. [Event-Driven Architecture (Apache Kafka)](#event-driven-architecture-apache-kafka)  
5. [In-Memory Store & Caching (Redis)](#in-memory-store--caching-redis)  
6. [Infrastructure Stack (Docker Compose)](#infrastructure-stack-docker-compose)  
7. [Repository Structure](#repository-structure)  
8. [Prerequisites](#prerequisites)  
9. [Build & Run](#build--run)  
10. [API Gateway](#api-gateway)  
11. [Testing](#testing)  
12. [Configuration](#configuration)  
13. [Architecture Decisions (ADRs)](#architecture-decisions-adrs)  
14. [Roadmap](#roadmap)

---

## Architecture Overview

```
┌───────────────────────────────────────────────────────────────────┐
│                        Client Applications                        │
│                 (Web / Mobile / Third-party APIs)                 │
└─────────────────────────────────┬─────────────────────────────────┘
                                  │  HTTP/REST  (port 8080)
                    ┌─────────────▼─────────────┐
                    │       API Gateway         │  ← PB-002 ✅
                    │   Spring Cloud Gateway    │
                    │   /api/<resource>/**      │
                    └──────┬──────┬──────┬──────┘
                           │      │      │
                 ┌─────────┘  ┌───┘  ┌──┘
                 ▼            ▼      ▼
           ┌──────────┐ ┌──────────┐ ┌──────────────┐ ┌──────────────────┐
           │  user-   │ │  order-  │ │  shipment-   │ │  notification-   │
           │ service  │ │ service  │ │   service    │ │     service      │
           │ :8081    │ │  :8082   │ │    :8083     │ │      :8084       │
           └────┬─────┘ └────┬─────┘ └──────┬───────┘ └────────┬─────────┘
                │            │              │                  │
════════════════╪════════════╪══════════════╪══════════════════╪═════════════════════════
   PERSISTENCE LAYER — Database-per-Service Isolation (PostgreSQL 16 — PB-004 ✅)
                │            │              │                  │
                ▼            ▼              ▼                  ▼
           ┌──────────┐ ┌──────────┐ ┌──────────────┐ ┌──────────────────┐
           │ user_db  │ │ order_db │ │ shipment_db  │ │ notification_db  │
           │ (user_   │ │ (order_  │ │ (shipment_   │ │ (notification_   │
           │  user)   │ │  user)   │ │  user)       │ │  user)           │
           └──────────┘ └──────────┘ └──────────────┘ └──────────────────┘
            [NO CROSS-DATABASE ACCESS ALLOWED — ENFORCED AT DB ROLE LEVEL]
═════════════════════════════════════════════════════════════════════════════════════════
   ASYNC EVENT BUS — Apache Kafka 3.8 KRaft (PB-005 ✅)
                │            │              │                  │
                └────────────┴──────────────┴──────────────────┘
                 ▲ (publish events)           │ (consume events)
                 │                            ▼
  ┌──────────────────────────────────────────────────────────────────────────┐
  │                 Apache Kafka 3.8 (KRaft Mode :9092)                      │
  │  Topics:                                                                 │
  │    • logistics.order.events        • logistics.shipment.events           │
  │    • logistics.notification.events • logistics.user.events               │
  │                   + Kafka UI Dashboard (:8090)                           │
  └──────────────────────────────────────────────────────────────────────────┘
═════════════════════════════════════════════════════════════════════════════════════════
   IN-MEMORY STORE & CACHE — Redis 7 (PB-006 ✅)
  ┌──────────────────────────────────────────────────────────────────────────┐
  │                 Redis 7 (Standalone, AOF Enabled :6379)                  │
  │  Used for session tokens, distributed caching, and rate limiting        │
  └──────────────────────────────────────────────────────────────────────────┘
═════════════════════════════════════════════════════════════════════════════════════════
   INFRASTRUCTURE LAYER (Docker Compose — PB-003 ✅)
  ┌──────────────────────────────────────────────────────────────────────────┐
  │                 Mailpit (SMTP :1025 | Web UI :8025)                      │
  └──────────────────────────────────────────────────────────────────────────┘
```

**External clients talk ONLY to the API Gateway (port 8080). Backend services are internal.**

Each service is **independently deployable**, has its own isolated database schema (Database-per-Service pattern), communicates asynchronously via Apache Kafka, and utilizes Redis for high-speed in-memory state.

---

## Service Decomposition

| Service | Port | Purpose | Database | Messaging | In-Memory Cache |
|---|---|---|---|---|---|
| **`api-gateway`** | **8080** | **Single entry point for all external clients. Routes requests to backend services by path prefix.** | – *(Stateless)* | – | Optional Rate Limiting |
| `user-service` | 8081 | User accounts, roles (`admin / dispatcher / driver / customer`), authentication identity | `user_db` (`user_service_user`) | Producer (`logistics.user.events`) | Session & Token Cache (`Redis 7`) |
| `order-service` | 8082 | Order lifecycle: create → assign → in-transit → delivered / cancelled | `order_db` (`order_service_user`) | Producer (`logistics.order.events`) | – |
| `shipment-service` | 8083 | Physical shipment runs: vehicle & driver assignment, real-time location, proof-of-delivery | `shipment_db` (`shipment_service_user`) | Producer / Consumer (`logistics.shipment.events`) | – |
| `notification-service` | 8084 | Outbound notifications (email, SMS, push) triggered by platform events | `notification_db` (`notification_service_user`) | Consumer (`logistics.*.events`) | – |
| `common-lib` | – | Shared library: `ApiResponse<T>`, `LogisticsPlatformException`, `DomainEvent<T>`, `KafkaTopics`, `CommonUtils` | – | Shared envelopes | – |

---

## Database-per-Service Architecture (PostgreSQL)

To preserve loose coupling and strict bounded contexts, the persistence layer implements the **Database-per-Service** pattern with physical and logical role isolation.

### Service Credentials & Access Isolation

| Service | Database Name | Dedicated Role | Default Password | Default JDBC URL |
|---|---|---|---|---|
| `user-service` | `user_db` | `user_service_user` | `user_password` | `jdbc:postgresql://localhost:5432/user_db` |
| `order-service` | `order_db` | `order_service_user` | `order_password` | `jdbc:postgresql://localhost:5432/order_db` |
| `shipment-service` | `shipment_db` | `shipment_service_user` | `shipment_password` | `jdbc:postgresql://localhost:5432/shipment_db` |
| `notification-service` | `notification_db` | `notification_service_user` | `notification_password` | `jdbc:postgresql://localhost:5432/notification_db` |

### Strict Cross-Service Access Control
- `REVOKE CONNECT ON DATABASE <db> FROM PUBLIC` is executed on all databases.
- Only the owning role is granted `CONNECT` privilege on its database.
- Attempting cross-service access (e.g. `user_service_user` trying to connect to `order_db`) results in an immediate PostgreSQL rejection:
  ```
  FATAL: permission denied for database "order_db"
  DETAIL: User does not have CONNECT privilege.
  ```

---

## Event-Driven Architecture (Apache Kafka)

Asynchronous inter-service communication and event broadcasting are managed via Apache Kafka 3.8 running in native **KRaft mode** (no ZooKeeper required).

### Foundation Topic Structure

All topic names follow the standard domain hierarchy convention defined in `KafkaTopics`:

| Topic Name | Purpose | Producers | Consumers |
|---|---|---|---|
| `logistics.order.events` | Order status changes (`OrderCreated`, `OrderCancelled`, etc.) | `order-service` | `shipment-service`, `notification-service` |
| `logistics.shipment.events` | Shipment tracking updates (`ShipmentDispatched`, `DeliveryCompleted`) | `shipment-service` | `order-service`, `notification-service` |
| `logistics.notification.events` | Notification triggers and audit records | Domain services | `notification-service` |
| `logistics.user.events` | User lifecycle events (`UserRegistered`, `DriverAssigned`) | `user-service` | `notification-service` |

### Standard Domain Event Envelope

All events share the universal `DomainEvent<T>` envelope defined in `common-lib`:
```json
{
  "eventId": "c86a7d55-7fc7-458b-967b-12d8a39a7b93",
  "eventType": "OrderCreated",
  "aggregateId": "ORD-10023",
  "timestamp": "2026-10-07T03:58:00Z",
  "payload": { ... }
}
```

---

## In-Memory Store & Caching (Redis)

Redis 7 is deployed as the foundational in-memory store for low-latency operations, temporary state, distributed session management, and upcoming rate-limiting capabilities.

### Redis Configuration & Port
- **Host Port**: `6379`
- **Internal Port**: `6379`
- **Volume**: `logistics_redis_data` (AOF persistence enabled: `redis-server --appendonly yes`)
- **Container Name**: `logistics-redis`

### Spring Boot Data Redis Integration
- Microservices connect via Spring Data Redis (`Lettuce` driver).
- Environment variables:
  - `spring.data.redis.host: ${REDIS_HOST:localhost}`
  - `spring.data.redis.port: ${REDIS_PORT:6379}`
  - `spring.data.redis.password: ${REDIS_PASSWORD:}`
- Actuator Health check enabled: `management.health.redis.enabled=true`.
- Bean `RedisTemplate<String, Object>` is pre-configured with JSON serialization (`GenericJackson2JsonRedisSerializer`).

### How to Inspect & Test Redis
```bash
# 1. Ping Redis container
docker exec logistics-redis redis-cli ping
# Output: PONG

# 2. Test SET & GET via CLI
docker exec logistics-redis redis-cli set sample_key "Hello Redis"
docker exec logistics-redis redis-cli get sample_key
# Output: Hello Redis

# 3. Check Redis Health via Actuator
curl http://localhost:8081/actuator/health | grep redis
```

---

## Infrastructure Stack (Docker Compose)

All foundational infrastructure required for local development is containerized using Docker Compose (`docker-compose.yml`) under the dedicated bridge network `logistics-network`.

### Services & Port Mappings

| Component | Container Name | Host Port | Internal Port | Persistent Volume | Description |
|---|---|---|---|---|---|
| **PostgreSQL 16** | `logistics-postgres` | `5432` | `5432` | `logistics_postgres_data` | Relational DB; auto-provisions `user_db`, `order_db`, `shipment_db`, `notification_db` |
| **Apache Kafka 3.8** | `logistics-kafka` | `9092` | `9092`, `29092` | `logistics_kafka_data` | Distributed event streaming in native **KRaft mode** (no ZooKeeper) |
| **Kafka UI** | `logistics-kafka-ui` | `8090` | `8080` | – | Web dashboard for managing Kafka topics, consumer groups & messages |
| **Redis 7** | `logistics-redis` | `6379` | `6379` | `logistics_redis_data` | High-performance in-memory cache and session store (AOF enabled) |
| **Mailpit** | `logistics-mailpit` | `1025` (SMTP)<br>`8025` (UI) | `1025`<br>`8025` | – | Mock SMTP server and webmail inspector for notification-service testing |

### Infrastructure Management Commands

```bash
# Start all infrastructure
docker compose up -d

# Check status & health
docker compose ps

# View logs
docker compose logs -f [service]

# Stop infrastructure
docker compose down

# Reset and delete all data
docker compose down -v
```

---

## Repository Structure

```
logistics-platform/
├── pom.xml                               # Parent POM (multi-module build root)
├── docker-compose.yml                    # Infrastructure orchestration (Postgres, Kafka KRaft, Redis, Mailpit)
├── .env.example                          # Environment variables template (no secrets)
├── .gitignore
├── README.md
│
├── docker/
│   └── postgres/
│       └── init-databases.sql            # Role & DB provisioning per service
│
├── shared/
│   └── common-lib/                       # Shared library
│       ├── pom.xml
│       └── src/
│           ├── main/java/com/logistics/common/
│           │   ├── dto/ApiResponse.java
│           │   ├── event/DomainEvent.java           # Universal event envelope
│           │   ├── event/KafkaTopics.java           # Foundation topic constants
│           │   ├── exception/LogisticsPlatformException.java
│           │   └── util/CommonUtils.java
│           └── test/java/com/logistics/common/
│               ├── event/DomainEventTest.java
│               └── util/CommonUtilsTest.java
│
└── services/
    ├── api-gateway/                      # API Gateway (Spring Cloud Gateway)
    │   ├── pom.xml
    │   └── src/
    │       ├── main/java/com/logistics/gateway/ApiGatewayApplication.java
    │       ├── main/resources/application.yml        ← route definitions
    │       ├── test/java/com/logistics/gateway/ApiGatewayApplicationTest.java
    │       ├── test/java/com/logistics/gateway/GatewayRoutesConfigTest.java
    │       └── test/resources/application-test.yml
    │
    ├── user-service/                     # PostgreSQL + Kafka + Redis 7 + Liquibase
    │   ├── pom.xml
    │   └── src/
    │       ├── main/java/com/logistics/user/
    │       │   ├── UserServiceApplication.java
    │       │   └── config/RedisConfig.java
    │       ├── main/resources/
    │       │   ├── application.yml
    │       │   └── db/changelog/
    │       │       ├── db.changelog-master.yaml
    │       │       └── changes/001-create-users-table.yaml
    │       └── test/
    │           ├── java/com/logistics/user/
    │           │   ├── UserServiceApplicationTest.java
    │           │   ├── UserDatabaseConnectionTest.java
    │           │   ├── UserLiquibaseMigrationTest.java
    │           │   └── RedisSmokeIntegrationTest.java
    │           └── resources/application-test.yml
    │
    ├── order-service/                    # order_db persistence + Kafka + Liquibase
    │   ├── pom.xml
    │   └── src/
    │       ├── main/java/com/logistics/order/
    │       │   ├── OrderServiceApplication.java
    │       │   └── config/KafkaTopicConfig.java
    │       ├── main/resources/
    │       │   ├── application.yml
    │       │   └── db/changelog/
    │       │       ├── db.changelog-master.yaml
    │       │       └── changes/001-create-orders-table.yaml
    │       └── test/
    │           ├── java/com/logistics/order/
    │           │   ├── OrderServiceApplicationTest.java
    │           │   ├── OrderDatabaseConnectionTest.java
    │           │   ├── OrderLiquibaseMigrationTest.java
    │           │   └── KafkaProducerConsumerIntegrationTest.java
    │           └── resources/application-test.yml
    │
    ├── shipment-service/                 # shipment_db persistence + Kafka + Liquibase
    │   ├── pom.xml
    │   └── src/  (includes db/changelog/ & ShipmentLiquibaseMigrationTest)
    │
    └── notification-service/             # notification_db persistence + Kafka + Liquibase
        ├── pom.xml
        └── src/  (includes db/changelog/ & NotificationLiquibaseMigrationTest)
```

---

## Prerequisites

| Tool | Minimum version | Purpose |
|---|---|---|
| Java (JDK) | 21 | Microservices runtime |
| Apache Maven | 3.9 | Multi-module build system |
| Docker & Docker Compose | Docker 24+, Compose v2+ | Local infrastructure orchestration |

---

## Build & Run

### Build all modules from the repository root

```bash
mvn clean package -DskipTests
```

### Run local development environment

1. **Start infrastructure (PostgreSQL, Kafka, Redis, Mailpit):**
   ```bash
   docker compose up -d
   ```
2. **Start microservices (in individual terminals or IDE):**
   ```bash
   mvn spring-boot:run -pl services/user-service
   mvn spring-boot:run -pl services/order-service
   mvn spring-boot:run -pl services/shipment-service
   mvn spring-boot:run -pl services/notification-service
   mvn spring-boot:run -pl services/api-gateway
   ```

---

## Database Migrations (Liquibase)

Every service with persistent storage manages its database schema migrations independently using Liquibase:

- **Isolated Master Changelog**: Located at `classpath:db/changelog/db.changelog-master.yaml`.
- **Modular Changeset Directory**: Granular changesets reside in `db/changelog/changes/<seq>-<description>.yaml` and are included by the master.
- **Automated Startup Migration**: Spring Boot runs pending migrations at service startup (`spring.liquibase.enabled: true`).
- **DDL Governance**: Hibernate `ddl-auto` is set to `validate` to guarantee Liquibase is the single source of truth for DDL changes.
- **Repeatable & Version Controlled**: Re-running migrations on fresh instances creates the exact schema with checksum tracking.

### Inspect Migration Status

1. **Via Spring Boot Actuator**:
   ```bash
   curl http://localhost:8081/actuator/liquibase | jq .
   ```
2. **Via PostgreSQL Metadata Tables**:
   ```bash
   docker exec -e PGPASSWORD=user_password logistics-postgres psql -U user_service_user -d user_db \
     -c "SELECT id, author, exectype, orderexecuted FROM databasechangelog;"
   ```

---

## Testing

### Run all tests from the root

```bash
mvn test
```

### Test coverage per module

| Module | Tests | Descriptions |
|---|---|---|
| `common-lib` | 7 unit tests | `CommonUtilsTest` (6) + `DomainEventTest` (1) |
| `api-gateway` | 6 tests | 1 context smoke test + 5 route configuration tests (`GatewayRoutesConfigTest`) |
| `user-service` | 4 tests | 1 context smoke test + 1 DB connection + 1 Redis smoke test + 1 Liquibase migration test |
| `order-service` | 4 tests | 1 context smoke test + 1 DB connection + 1 Kafka integration test + 1 Liquibase migration test |
| `shipment-service` | 3 tests | 1 context smoke test + 1 DB connection + 1 Liquibase migration test |
| `notification-service` | 3 tests | 1 context smoke test + 1 DB connection + 1 Liquibase migration test |

**Total: 27 tests passing 100%.**

---

## Continuous Integration (CI)

The repository is configured with an automated Continuous Integration pipeline using **GitHub Actions** ([`.github/workflows/ci.yml`](.github/workflows/ci.yml)):

- **Triggers**:
  - `push` to the `main` branch.
  - `pull_request` targeting the `main` branch.
  - `workflow_dispatch` for manual triggering from the GitHub Actions console.
- **Environment**:
  - Runner: `ubuntu-latest`.
  - Runtime: Eclipse Temurin JDK 21 LTS (`actions/setup-java@v4`).
  - Dependency Caching: Automated Maven cache (`cache: 'maven'`) for rapid feedback loops.
- **Pipeline Stages**:
  1. **Checkout Repository**: Full workspace fetch (`actions/checkout@v4`).
  2. **Environment Verification**: Verifies `java -version` and `mvn -version`.
  3. **Multi-module Reactor Build & Test**: Runs `mvn -B clean verify --file pom.xml`, ensuring clean builds and executing all unit/integration tests across all 7 modules (`parent`, `common-lib`, `api-gateway`, `user-service`, `order-service`, `shipment-service`, `notification-service`).
  4. **Strict Gating**: Any failure during compilation, package packaging, or test assertions exits with non-zero code, failing the CI run and blocking Pull Request merge.
  5. **Step Summary**: Automatically generates a Markdown summary of the build run in the GitHub Actions summary tab.

---

## Configuration

All configuration is managed via `application.yml` in each service's `src/main/resources/` and overridable via environment variables.  
**No secrets are hard-coded.** Every sensitive value uses safe local defaults or environment overrides.

Copy `.env.example` to `.env` and fill in your values:

```bash
cp .env.example .env
```

### Key environment variables

| Variable | Default | Description |
|---|---|---|
| `GATEWAY_PORT` | `8080` | HTTP port for the API Gateway |
| `USER_SERVICE_PORT` | `8081` | HTTP port for user-service |
| `ORDER_SERVICE_PORT` | `8082` | HTTP port for order-service |
| `SHIPMENT_SERVICE_PORT` | `8083` | HTTP port for shipment-service |
| `NOTIFICATION_SERVICE_PORT` | `8084` | HTTP port for notification-service |
| `POSTGRES_PORT` | `5432` | PostgreSQL host port |
| `REDIS_PORT` | `6379` | Redis host port |
| `REDIS_HOST` | `localhost` | Redis host address |
| `KAFKA_PORT` | `9092` | Kafka external broker port |
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` | Kafka cluster bootstrap servers address |
| `MAILPIT_SMTP_PORT` | `1025` | Mailpit SMTP server port |
| `MAILPIT_UI_PORT` | `8025` | Mailpit Web inbox UI port |

---

## Architecture Decisions (ADRs)

### ADR-001: Maven Multi-module Monorepo
**Decision**: Use a single Git repository with Maven multi-module build.  
**Rationale**: Simplifies cross-service refactoring, enforces single dependency version governance, and enables atomic commits across services.

### ADR-002: Java 21 + Spring Boot 3.3
**Decision**: All services use Java 21 LTS and Spring Boot 3.3.x.  
**Rationale**: Java 21 LTS is standard across modern cloud providers. Spring Boot 3.3 brings virtual threads and Spring Framework 6.1 features.

### ADR-003: Spring Cloud Gateway for API Gateway
**Decision**: `spring-cloud-starter-gateway` (reactive, Netty-based) is used for the API Gateway module.  
**Rationale**: Non-blocking asynchronous event loop delivers high throughput with minimal thread overhead. Configuration-as-code in `application.yml`.

### ADR-004: Path-prefix Routing with StripPrefix
**Decision**: Routes are defined by `/api/<resource>/**` prefix; `StripPrefix=1` removes `/api` before forwarding.  
**Rationale**: Clean, unified public namespace for clients. Backend microservices retain clean domain paths without gateway coupling.

### ADR-005: Local Infrastructure via Docker Compose
**Decision**: Centralize all backing infrastructure in a root `docker-compose.yml` with named volumes and dedicated bridge network.  
**Rationale**: Developers can spin up the full infrastructure stack (databases, message broker, cache, mock SMTP) with a single command without installing heavy native daemons on their host machines.

### ADR-006: Apache Kafka in Native KRaft Mode
**Decision**: Deploy Kafka 3.8 in KRaft (Kafka Raft Metadata) mode without ZooKeeper.  
**Rationale**: KRaft eliminates ZooKeeper management overhead, accelerates metadata propagation, reduces container footprint, and represents the modern production standard for Apache Kafka.

### ADR-007: Strict Database-per-Service Isolation & Role Access Control
**Decision**: Each microservice is assigned its own PostgreSQL database and dedicated database user with password. Direct access across services is explicitly revoked (`REVOKE CONNECT ON DATABASE FROM PUBLIC`).  
**Rationale**: Prevents data coupling, enforces domain boundary integrity, and eliminates hidden dependencies across bounded contexts.

### ADR-008: Kubernetes-ready Liveness & Readiness Probes
**Decision**: Actuator probes are enabled with database health verification attached to readiness state.  
**Rationale**: Ensures traffic is only routed to application instances when the database connection pool is healthy and responsive.

### ADR-009: Standard Event Envelope & Domain Topic Hierarchy
**Decision**: Microservices exchange asynchronous events using the typed `DomainEvent<T>` envelope across hierarchical topic channels (`logistics.<domain>.events`).  
**Rationale**: Standardizes event payload headers (`eventId`, `eventType`, `aggregateId`, `timestamp`) and simplifies auditing, tracing, and dead-letter queue routing without leaking service-internal data models.

### ADR-010: Redis In-Memory Store Foundation
**Decision**: Redis 7 is adopted as the dedicated in-memory key-value store using Lettuce client, JSON serializer, and Actuator health indicator.  
**Rationale**: Provides single-digit millisecond read/write latency for ephemeral session state, tokens, distributed cache, and rate-limiting counters.

### ADR-011: Liquibase Schema Versioning & Isolation
**Decision**: Each persistent service manages its own database schema versioning via Liquibase changelogs (`db.changelog-master.yaml` with modular changesets under `changes/`). Migrations run automatically on application startup, and Hibernate DDL generation is restricted to `validate`.  
**Rationale**: Guarantees reproducible, auditable database schemas, prevents cross-service schema coupling, enables zero-downtime evolutionary database design, and eliminates desynchronization between code and database state.

### ADR-012: Continuous Integration via GitHub Actions
**Decision**: Automate repository-level build and test execution via GitHub Actions (`ci.yml`) on Ubuntu runners with Temurin JDK 21 and Maven caching.  
**Rationale**: Guarantees that every Push and Pull Request triggers a clean reactor verification (`mvn clean verify`) covering all 7 modules without depending on developer-local machine state. Any regression or test failure immediately fails the CI pipeline.

---

## Roadmap

| PB Item | Scope | Status |
|---|---|---|
| **PB-001** | Repository initialization, monorepo structure, service skeletons | ✅ Completed |
| **PB-002** | API Gateway (Spring Cloud Gateway), routing to all backend services | ✅ Completed |
| **PB-003** | Docker Compose for infrastructure stack (Postgres, Kafka KRaft, Redis, Mailpit, Kafka UI) | ✅ Completed |
| **PB-004** | PostgreSQL Database-per-Service with strict role isolation & connection verification | ✅ Completed |
| **PB-005** | Kafka asynchronous event foundation, topic provisioning & integration tests | ✅ Completed |
| **PB-006** | Redis in-memory store foundation, configuration & set/get smoke tests | ✅ Completed |
| **PB-007** | Liquibase database migrations, changelog modularity, metadata verification | ✅ Completed |
| **PB-008** | Continuous Integration (CI) automated build/test pipeline via GitHub Actions | ✅ Completed |
| **PB-009+** | Business features implementation per domain service | ⏳ Planned |
