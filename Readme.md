# GarageBid

GarageBid is an educational microservices project for an exotic car auction platform.

The project is built as a hands-on playground for learning microservice architecture, distributed systems, Domain-Driven Design, resilience, observability, asynchronous messaging, security, testing, and deployment practices.

The goal is not to build a collection of disconnected Spring Boot applications. The goal is to experience the architectural trade-offs and failure modes that appear when a system is split across independently owned services.

---

## Why GarageBid?

A car auction domain gives each service a meaningful reason to exist.

The project intentionally avoids the typical "users-products-orders" demo architecture and uses a domain where different architectural and technological choices can emerge naturally.

For example, live bidding will eventually require:

- high concurrency,
- WebSocket communication,
- low-latency state management,
- Redis,
- and a dedicated Go bidding engine.

This makes the polyglot architecture intentional rather than decorative.

---

## Project Goals

GarageBid is designed to provide hands-on experience with:

- Microservice boundaries
- Database per service
- Layered architecture
- Hexagonal architecture
- Domain-Driven Design
- Synchronous service-to-service communication
- Event-driven architecture
- Transactional Outbox
- Kafka
- Saga
- Eventual consistency
- Idempotency
- CQRS
- WebSocket
- gRPC
- Redis
- Resilience patterns
- Distributed tracing
- Metrics and centralized logging
- OAuth2 / OpenID Connect
- Contract testing
- Docker
- Kubernetes
- Helm
- CI/CD

Every major architectural decision is intended to answer:

> Why does this pattern exist, and what problem does it solve?

---

## Repository Structure

GarageBid is a **monorepo**, but it is intentionally **not a Maven multi-module project**.

Each service is independently buildable and owns:

- its own `pom.xml`,
- its own Maven wrapper,
- its own application configuration,
- its own database,
- and eventually its own container image.

```text
garagebid/
├── auction/
│   ├── pom.xml
│   └── src/
│
├── catalog/
│   ├── pom.xml
│   └── src/
│
├── docs/
│   └── architecture/
│
├── docker-compose.yml
├── PATTERNS.md
└── README.md
```

This structure keeps the convenience of a monorepo while preserving service independence.

---

# Current Architecture

GarageBid currently contains two services.

```text
                         ┌───────────────────────┐
                         │       Client          │
                         │ Postman / Frontend    │
                         └───────────┬───────────┘
                                     │
                                     │ HTTP
                                     ▼
                         ┌───────────────────────┐
                         │   auction-service     │
                         │       :8082           │
                         │                       │
                         │ Hexagonal Architecture│
                         └───────┬───────────────┘
                                 │
                    HTTP         │
                 @HttpExchange   │
                                 ▼
                         ┌───────────────────────┐
                         │   catalog-service     │
                         │       :8081           │
                         │                       │
                         │ Layered Architecture  │
                         └───────────┬───────────┘
                                     │
                                     ▼
                              catalog-db
                                :5433


auction-service
      │
      ▼
 auction-db
   :5434
```

Each service owns its own PostgreSQL database.

```text
catalog-service
    └── catalog-db
        └── public.cars

auction-service
    └── auction-db
        └── public.auctions
```

Direct cross-service database access is not allowed.

If the auction service needs information owned by the catalog service, it must communicate through the catalog service API.

---

# Services

## Catalog Service

Port:

```text
8081
```

Database:

```text
PostgreSQL :5433
database: catalog
```

Architecture:

```text
Layered Architecture
```

Structure:

```text
Controller
    ↓
Service
    ↓
Repository
    ↓
PostgreSQL
```

Responsibilities:

- Store exotic car listings
- Retrieve cars
- Create cars
- Provide car information to other services

Current endpoints:

```http
GET  /api/v1/cars
GET  /api/v1/cars/{id}
POST /api/v1/cars
```

The catalog service intentionally uses a simpler layered architecture so it can later be compared directly with the hexagonal auction service.

---

## Auction Service

Port:

```text
8082
```

Database:

```text
PostgreSQL :5434
database: auction
```

Architecture:

```text
Hexagonal Architecture
```

Responsibilities:

- Open auctions
- Validate cars through the catalog service
- Place bids
- Enforce bidding rules
- Close auctions

Current endpoints:

```http
POST /api/v1/auctions
POST /api/v1/auctions/{auctionId}/bids
POST /api/v1/auctions/{auctionId}/close
```

The auction service uses a rich domain model and Ports & Adapters architecture.

---

# Auction Hexagonal Architecture

The auction service is organized around a framework-independent application core.

```text
HTTP Request
     │
     ▼
Inbound Web Adapter
AuctionController
     │
     ▼
Inbound Port
OpenAuctionUseCase
PlaceBidUseCase
CloseAuctionUseCase
     │
     ▼
Application Service
AuctionService
     │
     ├───────────────┐
     │               │
     ▼               ▼
Domain              Outbound Ports
Auction             LoadAuctionPort
Money               SaveAuctionPort
Bid                 CarLookupPort
     │               │
     │               ▼
     │          Outbound Adapters
     │          ├── Persistence
     │          └── Catalog HTTP
     │
     ▼
Business Rules
```

The most important rule is dependency direction:

```text
Infrastructure depends on the application core.

The application core does not depend on infrastructure.
```

For example, `AuctionService` does not know about:

- `JpaRepository`
- `RestClient`
- PostgreSQL
- HTTP status codes
- controllers

It only knows the ports defined by the application.

---

# Domain Model

The auction domain is intentionally independent from Spring and JPA.

The aggregate root is:

```text
Auction
```

Domain behavior includes:

```text
Auction.open(...)
Auction.placeBid(...)
Auction.close()
Auction.hasWinner()
```

Value objects include:

```text
Money
Bid
```

Examples of domain invariants:

- An auction must be open before accepting bids.
- The first bid must be greater than or equal to the starting price.
- A later bid must be strictly greater than the current highest bid.
- A closed auction cannot accept new bids.
- Auctions cannot be closed twice.

These rules live in the domain model instead of controllers or persistence code.

---

# Domain Model vs Persistence Model

The auction service intentionally separates:

```text
Auction
```

from:

```text
AuctionJpaEntity
```

The domain object models business behavior.

The JPA entity models persistence.

They are translated through:

```text
AuctionPersistenceMapper
```

This prevents JPA and Hibernate requirements from shaping the domain model.

---

# Ports and Adapters

## Inbound Ports

Inbound ports define what the application can do.

Examples:

```text
OpenAuctionUseCase
PlaceBidUseCase
CloseAuctionUseCase
```

They represent application capabilities without depending on HTTP.

---

## Outbound Ports

Outbound ports define what the application needs from the outside world.

Examples:

```text
LoadAuctionPort
SaveAuctionPort
CarLookupPort
```

The application defines these interfaces.

Infrastructure adapters implement them.

---

# Synchronous Service Communication

The auction service validates a car before opening an auction.

The flow is:

```text
AuctionService
      │
      ▼
CarLookupPort
      ▲
      │
CatalogHttpAdapter
      │
      ▼
CatalogHttpClient
      │
      ▼
@HttpExchange
      │
      ▼
RestClient
      │
      ▼
catalog-service
```

The project uses Spring HTTP Interfaces:

```java
@HttpExchange
```

instead of OpenFeign.

This keeps synchronous HTTP communication inside Spring Framework without introducing a Spring Cloud dependency at this stage.

---

# Remote Failure Semantics

A remote service call is not treated like a local method call.

The catalog integration currently distinguishes between three cases:

```text
Catalog returns 2xx
    → car exists
    → auction may be opened

Catalog returns 404
    → car does not exist
    → auction returns 422

Catalog is unavailable / returns unexpected failure
    → dependency failure
    → auction returns 503
```

An unavailable catalog service is deliberately **not** interpreted as "car does not exist".

This distinction prevents infrastructure failures from becoming incorrect business facts.

---

# Distributed Validation Is Not a Foreign Key

The auction database stores:

```text
car_id
```

but does not have a foreign key to the catalog database.

The databases belong to different services.

A synchronous catalog lookup only means:

> The car existed when the catalog service was checked.

It does not guarantee that the car will exist forever.

For example:

```text
T1  auction asks catalog if car exists
T2  catalog responds yes
T3  car is deleted from catalog
T4  auction is persisted
```

This is one of the first distributed consistency problems intentionally exposed by the project.

Later phases will explore techniques such as:

- domain events,
- eventual consistency,
- Saga,
- CQRS,
- and local projections.

---

# Database Per Service

GarageBid initially used logical schema isolation during early development.

The architecture was later changed to independent PostgreSQL databases.

Current topology:

```text
catalog-service
    ↓
catalog-db

auction-service
    ↓
auction-db
```

Benefits:

- Clear data ownership
- No cross-service SQL joins
- No shared database transactions
- Independent schema evolution
- More realistic microservice boundaries

Trade-offs:

- Distributed consistency becomes explicit
- Cross-service foreign keys are impossible
- Infrastructure becomes more complex
- Network communication is required for cross-service data

These are intentional trade-offs.

---

# Database Migrations

Database schemas are managed using Flyway.

Hibernate is configured with:

```text
ddl-auto: validate
```

Flyway owns schema creation and evolution.

Hibernate only validates that entity mappings match the migrated schema.

This prevents application startup from silently modifying production schemas.

---

# Error Handling

The services use RFC 9457-style Problem Details through Spring's `ProblemDetail`.

Examples:

```text
Auction not found
→ 404 Not Found

Referenced car does not exist
→ 422 Unprocessable Content

Bid is too low
→ 409 Conflict

Auction is not open
→ 409 Conflict

Catalog service unavailable
→ 503 Service Unavailable
```

Domain and application exceptions do not contain HTTP concepts.

HTTP status mapping belongs to the web adapter.

---

# OpenAPI

Both services expose OpenAPI documentation using Springdoc.

Catalog:

```text
http://localhost:8081/swagger-ui/index.html
http://localhost:8081/v3/api-docs
```

Auction:

```text
http://localhost:8082/swagger-ui/index.html
http://localhost:8082/v3/api-docs
```

The project uses a code-first OpenAPI approach.

API documentation is generated from the actual controllers rather than maintained as a separate handwritten API specification.

---

# Technology Stack

Current:

- Java 21
- Spring Boot 4.1
- Spring Framework 7
- Spring MVC
- Spring Data JPA
- Hibernate
- PostgreSQL 16
- Flyway
- Spring HTTP Interface (`@HttpExchange`)
- RestClient
- Springdoc OpenAPI
- Maven
- Docker Compose
- JUnit 5

Planned:

- Apache Kafka
- Redis
- Go
- WebSocket
- gRPC
- Protocol Buffers
- Resilience4j
- OpenTelemetry
- Prometheus
- Grafana
- Loki
- Keycloak
- Testcontainers
- Contract testing
- Kubernetes
- Helm
- GitHub Actions

---

# Running Locally

## Requirements

Install:

- Java 21
- Docker Desktop
- Git

The project includes Maven Wrapper scripts, so a separate Maven installation is not required.

---

## Start Databases

From the repository root:

```bash
docker compose up -d
```

Expected containers:

```text
catalog-db
auction-db
```

Check:

```bash
docker compose ps
```

Ports:

| Component | Port |
|---|---:|
| Catalog Service | 8081 |
| Auction Service | 8082 |
| Catalog PostgreSQL | 5433 |
| Auction PostgreSQL | 5434 |

---

## Start Catalog Service

Windows:

```powershell
cd catalog
.\mvnw.cmd spring-boot:run
```

Unix:

```bash
cd catalog
./mvnw spring-boot:run
```

Health:

```text
http://localhost:8081/actuator/health
```

---

## Start Auction Service

Windows:

```powershell
cd auction
.\mvnw.cmd spring-boot:run
```

Unix:

```bash
cd auction
./mvnw spring-boot:run
```

Health:

```text
http://localhost:8082/actuator/health
```

---

# Running Tests

Catalog:

```powershell
cd catalog
.\mvnw.cmd test
```

Auction:

```powershell
cd auction
.\mvnw.cmd test
```

The auction tests intentionally demonstrate one of the main benefits of hexagonal architecture.

Domain and application behavior can be tested without:

- Spring context,
- PostgreSQL,
- Docker,
- or HTTP.

Fake port implementations can replace infrastructure dependencies during unit tests.

---

# Example Flow

## 1. List Cars

```http
GET http://localhost:8081/api/v1/cars
```

Copy one of the returned car IDs.

---

## 2. Open Auction

```http
POST http://localhost:8082/api/v1/auctions
Content-Type: application/json
```

```json
{
  "carId": "<catalog-car-id>",
  "sellerId": "22222222-2222-2222-2222-222222222222",
  "startingAmount": 395000.00,
  "currency": "USD",
  "endsAt": "2026-08-20T18:00:00Z"
}
```

Expected:

```text
201 Created
```

with a `Location` header containing the created auction ID.

---

## 3. Place Bid

```http
POST http://localhost:8082/api/v1/auctions/{auctionId}/bids
Content-Type: application/json
```

```json
{
  "bidderId": "33333333-3333-3333-3333-333333333333",
  "amount": 400000.00,
  "currency": "USD"
}
```

Expected:

```text
204 No Content
```

---

## 4. Try a Lower Bid

```json
{
  "bidderId": "44444444-4444-4444-4444-444444444444",
  "amount": 390000.00,
  "currency": "USD"
}
```

Expected:

```text
409 Conflict
```

---

## 5. Close Auction

```http
POST http://localhost:8082/api/v1/auctions/{auctionId}/close
```

Expected:

```text
204 No Content
```

Closing it again results in:

```text
409 Conflict
```

---

# Development Philosophy

The project follows several rules intentionally.

## Business Logic Belongs to the Domain

Controllers should not implement auction rules.

Application services should orchestrate use cases.

Aggregates should enforce invariants.

---

## Frameworks Stay at the Edges

The domain should not know about:

- Spring MVC
- JPA
- PostgreSQL
- HTTP
- Kafka
- Redis

Infrastructure should adapt to the application core.

---

## Prefer Explicit Boundaries

Transport models, application commands, domain models, and persistence models are intentionally separated when the distinction teaches an architectural boundary.

---

## Failure Is Part of the Design

Failures are deliberately tested and observed.

Examples include:

- schema validation failures,
- invalid Flyway state,
- invalid bids,
- closed auctions,
- missing remote resources,
- unavailable downstream services.

The goal is to understand why architectural patterns exist rather than memorize definitions.

---

# Roadmap

## Phase 01 — Catalog Service

Status: **Completed**

Topics:

- Layered architecture
- REST API
- DTO mapping
- PostgreSQL
- Flyway
- Schema ownership
- Validation
- Problem Details
- 12-factor configuration

---

## Phase 02 — Auction Service

Status: **Completed**

Topics:

- Hexagonal Architecture
- Ports and Adapters
- Rich Domain Model
- Aggregate Root
- Value Objects
- Dependency Inversion
- Interface Segregation
- Database per Service
- Synchronous service communication
- Spring HTTP Interfaces
- Anti-Corruption Layer
- Remote failure semantics
- OpenAPI

---

## Phase 03 — Platform Layer

Status: **Planned**

Topics:

- API Gateway
- Service Discovery
- Centralized Configuration
- Client-side Load Balancing
- Spring Boot 4 compatibility considerations

---

## Phase 04 — Event-Driven Architecture

Status: **Planned**

Topics:

- Apache Kafka
- Domain Events
- Transactional Outbox
- At-least-once delivery
- Idempotency
- Choreography

---

## Phase 05 — Saga

Status: **Planned**

Topics:

- Distributed business transactions
- Saga orchestration
- Saga choreography
- Compensation
- Why not 2PC

---

## Phase 06 — Go Bid Engine

Status: **Planned**

Topics:

- Go microservice
- WebSocket
- Real-time bidding
- Redis
- gRPC
- Protocol Buffers
- Java-Go interoperability

---

## Phase 07 — Resilience

Status: **Planned**

Topics:

- Timeouts
- Circuit Breaker
- Retry
- Exponential backoff
- Jitter
- Bulkhead
- Rate limiting
- Fallback
- Cascading failures

---

## Phase 08 — Observability

Status: **Planned**

Topics:

- OpenTelemetry
- Distributed tracing
- Correlation IDs
- Prometheus
- Grafana
- Loki
- Structured logging
- RED method
- USE method

---

## Phase 09 — CQRS

Status: **Planned**

Topics:

- Separate write and read models
- Bid history projections
- Materialized views
- Eventual consistency

---

## Phase 10 — Security

Status: **Planned**

Topics:

- Keycloak
- OAuth2
- OpenID Connect
- JWT
- Token propagation
- Authorization
- mTLS awareness

---

## Phase 11 — Testing

Status: **Planned**

Topics:

- Testcontainers
- Integration tests
- Contract testing
- Microservice testing pyramid
- Failure-oriented testing

---

## Phase 12 — Kubernetes and CI/CD

Status: **Planned**

Topics:

- Multi-stage Docker builds
- Kubernetes
- Helm
- ConfigMap
- Secrets
- Liveness probes
- Readiness probes
- Graceful shutdown
- GitHub Actions
- CI/CD

---

# Advanced Topics

Possible future extensions:

- Debezium CDC
- Event Sourcing
- Distributed Rate Limiting
- Chaos Engineering
- Service Mesh
- Istio / Linkerd
- GitOps
- Blue-Green deployments
- Canary deployments
- SLI / SLO / SLA
- Error budgets

---

# Architectural Documentation

Architectural knowledge is documented in two forms.

## Pattern Catalog

See:

```text
PATTERNS.md
```

It records:

```text
pattern
category
problem solved
implementation evidence
```

---

## Architecture Decision Records

Important architectural decisions are stored under:

```text
docs/adr/
```

ADRs explain:

- the context,
- the decision,
- alternatives,
- trade-offs,
- consequences,
- and when the decision should be reconsidered.

---

# Learning Outcome

GarageBid is intentionally built to move beyond framework-level microservice knowledge.

By the end of the project, the expected outcome is practical understanding of questions such as:

- How should service boundaries be chosen?
- Who owns data in a distributed system?
- Why should services not share databases?
- When should communication be synchronous or asynchronous?
- How do network failures affect business flows?
- Why are retries dangerous without idempotency?
- Why does eventual consistency exist?
- How does the Transactional Outbox solve the dual-write problem?
- When is Saga required?
- Why is 2PC often avoided?
- How do circuit breakers prevent cascading failures?
- How do distributed traces follow a request across services?
- How are contracts tested independently?
- How are microservices secured and deployed?

The objective is not merely to know the names of these patterns.

The objective is to understand **why they exist, what they cost, and when they should be used**.

---

# Status

Current milestone:

```text
Phase 02 — Auction Service
Completed
```

Next:

```text
Phase 03 — Platform Layer
API Gateway
Service Discovery
Centralized Configuration
```

---

## License

This project is currently maintained as an educational and portfolio project.