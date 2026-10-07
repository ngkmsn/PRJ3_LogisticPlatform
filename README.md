# Logistics Platform

A cloud-native **logistics management system** built on a microservice architecture.  
This repository is a Maven multi-module monorepo containing all platform services, shared libraries, and local infrastructure setup.

---

## Table of Contents

1. [Architecture Overview](#architecture-overview)  
2. [Service Decomposition](#service-decomposition)  
3. [Infrastructure Stack (Docker Compose)](#infrastructure-stack-docker-compose)  
4. [Repository Structure](#repository-structure)  
5. [Prerequisites](#prerequisites)  
6. [Build & Run](#build--run)  
7. [API Gateway](#api-gateway)  
8. [Testing](#testing)  
9. [Configuration](#configuration)  
10. [Architecture Decisions (ADRs)](#architecture-decisions-adrs)  
11. [Roadmap](#roadmap)

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
   INFRASTRUCTURE LAYER (Docker Compose — PB-003 ✅)
                │            │              │                  │
  ┌─────────────▼────────────▼──────────────▼──────────────────▼─────────────┐
  │                           PostgreSQL 16                                  │
  │     (user_db        order_db         shipment_db        notification_db) │
  └──────────────────────────────────────────────────────────────────────────┘
  ┌──────────────────────────────────────────────────────────────────────────┐
  │                 Apache Kafka 3.8 (KRaft Mode :9092)                      │
  │                   + Kafka UI Dashboard (:8090)                           │
  └──────────────────────────────────────────────────────────────────────────┘
  ┌──────────────────────────────────────────────────────────────────────────┐
  │                 Redis 7 (Cache & Rate Limiting :6379)                    │
  └──────────────────────────────────────────────────────────────────────────┘
  ┌──────────────────────────────────────────────────────────────────────────┐
  │                 Mailpit (SMTP :1025 | Web UI :8025)                      │
  └──────────────────────────────────────────────────────────────────────────┘
```

**External clients talk ONLY to the API Gateway (port 8080). Backend services are internal.**

Each service is **independently deployable**, has its own isolated database schema (Database-per-Service pattern), and communicates over HTTP (synchronous) or Apache Kafka (asynchronous events).

---

## Service Decomposition

| Service | Port | Purpose |
|---|---|---|
| **`api-gateway`** | **8080** | **Single entry point for all external clients. Routes requests to backend services by path prefix.** |
| `user-service` | 8081 | User accounts, roles (`admin / dispatcher / driver / customer`), authentication identity |
| `order-service` | 8082 | Order lifecycle: create → assign → in-transit → delivered / cancelled |
| `shipment-service` | 8083 | Physical shipment runs: vehicle & driver assignment, real-time location, proof-of-delivery |
| `notification-service` | 8084 | Outbound notifications (email, SMS, push) triggered by platform events |
| `common-lib` | – | Shared library: `ApiResponse<T>`, `LogisticsPlatformException`, `CommonUtils` |

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

#### 1. Start Infrastructure
Start all infrastructure containers in detached mode:
```bash
docker compose up -d
```

#### 2. Check Service Status & Health
```bash
docker compose ps
```
All containers should display status `Up` or `Up (healthy)`.

#### 3. View Logs
View combined logs:
```bash
docker compose logs -f
```
Or view logs for a specific container:
```bash
docker compose logs -f postgres
docker compose logs -f kafka
docker compose logs -f redis
```

#### 4. Stop Infrastructure
Stop containers while preserving volume data:
```bash
docker compose down
```

#### 5. Reset Infrastructure (Delete all data)
Stop containers and completely remove all persistent volumes:
```bash
docker compose down -v
```

#### 6. Validate Compose Configuration
```bash
docker compose config
```

### Accessing Local UIs
- **Kafka UI**: [http://localhost:8090](http://localhost:8090)
- **Mailpit Web UI**: [http://localhost:8025](http://localhost:8025)

---

## Repository Structure

```
logistics-platform/
├── pom.xml                               # Parent POM (multi-module build root)
├── docker-compose.yml                    # ← PB-003: Infrastructure orchestration
├── .env.example                          # Environment variables template (no secrets)
├── .gitignore
├── README.md
│
├── docker/
│   └── postgres/
│       └── init-databases.sql            # Auto-creates user_db, order_db, shipment_db, notification_db
│
├── shared/
│   └── common-lib/                       # Shared library (jar, no Spring Boot main)
│       ├── pom.xml
│       └── src/
│           ├── main/java/com/logistics/common/
│           │   ├── dto/ApiResponse.java
│           │   ├── exception/LogisticsPlatformException.java
│           │   └── util/CommonUtils.java
│           └── test/java/com/logistics/common/
│               └── util/CommonUtilsTest.java
│
└── services/
    ├── api-gateway/                      # ← PB-002: API Gateway (Spring Cloud Gateway)
    │   ├── pom.xml
    │   └── src/
    │       ├── main/java/com/logistics/gateway/ApiGatewayApplication.java
    │       ├── main/resources/application.yml        ← route definitions
    │       ├── test/java/com/logistics/gateway/ApiGatewayApplicationTest.java
    │       ├── test/java/com/logistics/gateway/GatewayRoutesConfigTest.java
    │       └── test/resources/application-test.yml
    │
    ├── user-service/
    │   ├── pom.xml
    │   └── src/
    │       ├── main/java/com/logistics/user/UserServiceApplication.java
    │       ├── main/resources/application.yml
    │       ├── test/java/com/logistics/user/UserServiceApplicationTest.java
    │       └── test/resources/application-test.yml
    │
    ├── order-service/
    │   ├── pom.xml
    │   └── src/  (same layout as user-service)
    │
    ├── shipment-service/
    │   ├── pom.xml
    │   └── src/  (same layout as user-service)
    │
    └── notification-service/
        ├── pom.xml
        └── src/  (same layout as user-service)
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

### Build a single module (with its dependencies)

```bash
mvn clean package -DskipTests -pl services/api-gateway -am
```

### Run local development environment

1. **Start infrastructure:**
   ```bash
   docker compose up -d
   ```
2. **Start microservices (in individual terminals or IDE):**
   ```bash
   # Terminal 1: User Service (:8081)
   mvn spring-boot:run -pl services/user-service

   # Terminal 2: Order Service (:8082)
   mvn spring-boot:run -pl services/order-service

   # Terminal 3: Shipment Service (:8083)
   mvn spring-boot:run -pl services/shipment-service

   # Terminal 4: Notification Service (:8084)
   mvn spring-boot:run -pl services/notification-service

   # Terminal 5: API Gateway (:8080)
   mvn spring-boot:run -pl services/api-gateway
   ```

---

## API Gateway

### Role

The API Gateway is the **only entry point for external clients**. It listens on port **8080** and forwards requests to the appropriate backend service based on the URL path prefix.

### Route Table

| Request path (client → Gateway) | Forwarded to | Backend service |
|---|---|---|
| `GET /api/users/**` | `http://<USER_SERVICE_URL>/users/**` | user-service (:8081) |
| `GET /api/orders/**` | `http://<ORDER_SERVICE_URL>/orders/**` | order-service (:8082) |
| `GET /api/shipments/**` | `http://<SHIPMENT_SERVICE_URL>/shipments/**` | shipment-service (:8083) |
| `GET /api/notifications/**` | `http://<NOTIFICATION_SERVICE_URL>/notifications/**` | notification-service (:8084) |

> `StripPrefix=1` removes the `/api` segment before forwarding, so backend services receive `/users/**`, `/orders/**`, etc. directly.

### Health & Diagnostics

```bash
# Gateway health
curl http://localhost:8080/actuator/health

# Registered route table (requires management.endpoint.gateway.enabled=true)
curl http://localhost:8080/actuator/gateway/routes
```

### Testing a request through the Gateway

With all services running:

```bash
# Routes to user-service
curl http://localhost:8080/api/users/actuator/health

# Routes to order-service
curl http://localhost:8080/api/orders/actuator/health

# Routes to shipment-service
curl http://localhost:8080/api/shipments/actuator/health

# Routes to notification-service
curl http://localhost:8080/api/notifications/actuator/health
```

---

## Testing

### Run all tests from the root

```bash
mvn test
```

### Run tests for a single module

```bash
mvn test -pl services/api-gateway -am
```

### Test coverage per module

| Module | Tests |
|---|---|
| `common-lib` | 6 unit tests (`CommonUtilsTest`) |
| `api-gateway` | 1 context smoke test + 5 route configuration tests (`GatewayRoutesConfigTest`) |
| `user-service` | 1 context smoke test |
| `order-service` | 1 context smoke test |
| `shipment-service` | 1 context smoke test |
| `notification-service` | 1 context smoke test |

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
| `USER_SERVICE_URL` | `http://localhost:8081` | Gateway → user-service backend URL |
| `ORDER_SERVICE_URL` | `http://localhost:8082` | Gateway → order-service backend URL |
| `SHIPMENT_SERVICE_URL` | `http://localhost:8083` | Gateway → shipment-service backend URL |
| `NOTIFICATION_SERVICE_URL` | `http://localhost:8084` | Gateway → notification-service backend URL |
| `POSTGRES_PORT` | `5432` | PostgreSQL host port |
| `POSTGRES_USER` | `postgres` | PostgreSQL username |
| `POSTGRES_PASSWORD` | `postgres` | PostgreSQL password |
| `REDIS_PORT` | `6379` | Redis host port |
| `KAFKA_PORT` | `9092` | Kafka external broker port |
| `KAFKA_UI_PORT` | `8090` | Kafka UI Web console port |
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

### ADR-007: Database-per-Service Isolation
**Decision**: PostgreSQL container automatically creates isolated databases (`user_db`, `order_db`, `shipment_db`, `notification_db`) via `init-databases.sql`.  
**Rationale**: Enforces bounded contexts and loose coupling between services at the data tier, paving the way for independent database migrations.

---

## Roadmap

| PB Item | Scope | Status |
|---|---|---|
| **PB-001** | Repository initialization, monorepo structure, service skeletons | ✅ Completed |
| **PB-002** | API Gateway (Spring Cloud Gateway), routing to all backend services | ✅ Completed |
| **PB-003** | Docker Compose for infrastructure stack (Postgres, Kafka KRaft, Redis, Mailpit, Kafka UI) | ✅ Completed |
| **PB-004** | PostgreSQL migrations (Liquibase/Flyway) & Persistence layer | ⏳ Next |
| **PB-005** | Kafka asynchronous event integration between domain services | ⏳ Planned |
| **PB-006** | Redis distributed caching & rate limiting | ⏳ Planned |
| **PB-007+** | Business features implementation per domain service | ⏳ Planned |
