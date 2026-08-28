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
├── gateway/
│   ├── pom.xml
│   └── src/
│
├── docs/
│   ├── adr/
│   └── phases/
│
├── docker-compose.yml
├── PATTERNS.md
└── README.md
```

This structure keeps the convenience of a monorepo while preserving service independence.

---

# Current Architecture

GarageBid currently contains three Spring Boot applications and Consul as platform infrastructure.

```text
                         Client
                           |
                           v
                    Gateway :8080
                     /         \
                    /           \
                   v             v
         catalog-service       auction
          /         \            :8082
      :8081         :8091          |
                                   |
                                   | OpenFeign
                                   | +
                                   | LoadBalancer
                                   v
                            catalog-service


                    ┌───────────────────┐
                    │      Consul       │
                    │       :8500       │
                    │                   │
                    │ Registry          │
                    │ Discovery         │
                    │ Health Checks     │
                    │ Configuration KV  │
                    └───────────────────┘
```

External clients communicate through:

```text
gateway :8080
```

Internal synchronous service communication uses logical service identities instead of hard-coded physical URLs.

For example:

```text
auction
→ catalog-service
```

Spring Cloud LoadBalancer resolves healthy Catalog instances through Consul.

Each business service continues to own its own PostgreSQL database:

```text
catalog-service
    ↓
catalog-db
    ↓
public.cars

auction
    ↓
auction-db
    ↓
public.auctions
```

Direct cross-service database access is not allowed.

Consul is platform infrastructure and does not proxy business traffic.

---

# Services

## Gateway Service

Port:

```text
8080
```

Technology:

```text
Spring Cloud Gateway
Spring WebFlux
Spring Cloud LoadBalancer
Spring Cloud Consul
```

Responsibilities:

- Provide a single external entry point
- Route external requests to internal services
- Resolve services through logical service identities
- Integrate with service discovery and client-side load balancing
- Keep edge concerns outside business services

Current routes:

```text
/api/v1/cars/**
→ catalog-service

/api/v1/auctions/**
→ auction
```

The Gateway uses logical service identities rather than hard-coded service URLs.

For example:

```text
lb://catalog-service
```

is resolved through:

```text
Spring Cloud LoadBalancer
→ Consul Discovery
→ healthy catalog-service instance
```

The Gateway contains no auction or catalog business logic.

Future edge concerns may include:

- authentication
- rate limiting
- CORS
- correlation IDs
- edge-level metrics and tracing

---

## Catalog Service

Port:

```text
8081
```

An additional instance can be started on:

```text
8091
```

Both instances register under the logical service name:

```text
catalog-service
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

For synchronous Catalog communication, the Auction service uses:

```text
CarLookupPort
→ CatalogHttpAdapter
→ CatalogFeignClient
→ Spring Cloud LoadBalancer
→ Consul Discovery
→ catalog-service
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
- OpenFeign
- HTTP client implementations
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

The application-facing dependency remains:

```text
CarLookupPort
```

The current implementation uses OpenFeign together with service discovery and client-side load balancing.

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
CatalogFeignClient
      │
      ▼
OpenFeign
      │
      ▼
Spring Cloud LoadBalancer
      │
      ▼
Consul Discovery
      │
      ▼
catalog-service
```

The Feign client uses the logical service identity:

```text
catalog-service
```

rather than a physical URL such as:

```text
http://localhost:8081
```

When multiple Catalog instances are registered:

```text
catalog-service
├── :8081
└── :8091
```

Spring Cloud LoadBalancer selects an available instance.

The original Spring HTTP Interface implementation is preserved as an architectural decision in ADR-002.

That decision was later superseded by ADR-003 after Spring Cloud became an intentional platform dependency.

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
- Spring WebFlux for Gateway
- Spring Data JPA
- Hibernate
- PostgreSQL 16
- Flyway
- Spring Cloud OpenFeign
- Spring Cloud LoadBalancer
- Spring Cloud Gateway
- Spring Cloud Consul
- Consul KV
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

# Running Locally

## Requirements

Install:

- Java 21
- Docker Desktop
- Git

The project includes Maven Wrapper scripts, so a separate Maven installation is not required.

---

## Start Infrastructure

From the repository root:

```bash
docker compose up -d
```

Expected containers:

```text
catalog-db
auction-db
consul
```

Check:

```bash
docker compose ps
```

Ports:

| Component | Port |
|---|---:|
| Gateway | 8080 |
| Catalog Service | 8081 |
| Catalog Service — second instance | 8091 |
| Auction Service | 8082 |
| Consul | 8500 |
| Catalog PostgreSQL | 5433 |
| Auction PostgreSQL | 5434 |

Consul UI:

```text
http://localhost:8500
```

---

## Configure Gateway Routes in Consul

Gateway routes are stored in Consul KV instead of the Gateway application artifact.

Open:

```text
http://localhost:8500
```

Go to:

```text
Key / Value
```

Create the following key:

```text
config/gateway/data
```

Use this YAML document as the value:

```yaml
spring:
  cloud:
    gateway:
      server:
        webflux:
          routes:
            - id: catalog-route
              uri: lb://catalog-service
              predicates:
                - Path=/api/v1/cars/**

            - id: auction-route
              uri: lb://auction
              predicates:
                - Path=/api/v1/auctions/**
```

Gateway loads this configuration from Consul during startup.

Runtime ConfigWatch is currently disabled, so changing this value requires restarting the Gateway.

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

After startup, Catalog should appear in Consul as:

```text
catalog-service
```

---

## Start a Second Catalog Instance

A second Catalog instance can be used to experiment with service discovery and client-side load balancing.

From the `catalog` directory:

```powershell
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.arguments=--server.port=8091"
```

Alternatively, when running through an IDE, start another Catalog configuration with:

```text
--server.port=8091
```

Consul should then contain:

```text
catalog-service
├── catalog-service-8081
└── catalog-service-8091
```

Both instances use the same Catalog database during the current local-development phase.

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

After startup, Auction should appear in Consul as:

```text
auction
```

---

## Start Gateway

Windows:

```powershell
cd gateway
.\mvnw.cmd spring-boot:run
```

Unix:

```bash
cd gateway
./mvnw spring-boot:run
```

Health:

```text
http://localhost:8080/actuator/health
```

Gateway routes:

```text
http://localhost:8080/actuator/gateway/routes
```

Expected route IDs:

```text
catalog-route
auction-route
```

After startup, Gateway should also appear in Consul as:

```text
gateway
```

The Gateway is the intended external entry point for application traffic.

Application requests should normally use:

```text
http://localhost:8080
```

rather than calling the individual service ports directly.

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

Gateway:

```powershell
cd gateway
.\mvnw.cmd test
```

The auction tests intentionally demonstrate one of the main benefits of hexagonal architecture.

Domain and application behavior can be tested without:

- Spring context
- PostgreSQL
- Docker
- HTTP

Fake port implementations can replace infrastructure dependencies during unit tests.

Infrastructure-oriented behavior such as service discovery, load balancing, Gateway routing, and Consul integration is currently verified through local integration experiments.

Later phases will introduce stronger integration and contract testing using tools such as Testcontainers and contract-testing frameworks.

---

# Example Flow

The following requests use the Gateway as the external application entry point.

---

## 1. List Cars

```http
GET http://localhost:8080/api/v1/cars
```

Expected:

```text
200 OK
```

Request flow:

```text
Client
→ Gateway
→ catalog-service
```

Copy one of the returned car IDs.

---

## 2. Open Auction

```http
POST http://localhost:8080/api/v1/auctions
Content-Type: application/json
```

```json
{
  "carId": "<catalog-car-id>",
  "sellerId": "22222222-2222-2222-2222-222222222222",
  "startingAmount": 395000.00,
  "currency": "USD",
  "endsAt": "2027-08-20T18:00:00Z"
}
```

Expected:

```text
201 Created
```

with a `Location` header containing the created auction ID.

The complete synchronous request flow is:

```text
Client
→ Gateway
→ Auction
→ CarLookupPort
→ CatalogHttpAdapter
→ CatalogFeignClient
→ LoadBalancer
→ Consul Discovery
→ Catalog
```

---

## 3. Place Bid

```http
POST http://localhost:8080/api/v1/auctions/{auctionId}/bids
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

```http
POST http://localhost:8080/api/v1/auctions/{auctionId}/bids
Content-Type: application/json
```

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

The bid is rejected by the Auction domain model.

---

## 5. Close Auction

```http
POST http://localhost:8080/api/v1/auctions/{auctionId}/close
```

Expected:

```text
204 No Content
```

Closing the same auction again results in:

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

Status: **Completed**

Topics:

- API Gateway
- Service Registry
- Service Registration
- Service Discovery
- Health Checks
- Centralized Configuration
- Client-side Load Balancing
- Logical Service Identity
- Multi-instance services
- OpenFeign
- Remote failure translation
- Spring Boot 4 / Spring Cloud integration considerations

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
Phase 03 — Platform Layer
Completed
```

Next:

```text
Phase 04 — Event-Driven Architecture

Apache Kafka
Domain Events
Transactional Outbox
At-least-once delivery
Idempotency
```

---

## License

This project is currently maintained as an educational and portfolio project.