# GarageBid — Pattern Catalog

This document is a living catalog of architectural and distributed-system patterns implemented in GarageBid.

The purpose is not to collect pattern names.

Each entry should answer four questions:

1. What is the pattern?
2. What problem does it solve?
3. Where is it implemented?
4. What trade-off does it introduce?

The catalog grows as the project evolves.

---

## Implemented Patterns

| Pattern | Category | Problem Solved | Implementation Evidence |
|---|---|---|---|
| Layered Architecture | Architecture | Provides a simple separation between HTTP, business logic, and persistence for CRUD-oriented services | `catalog/src/main/java/com/garagebid/catalog/web`, `service`, `repository` |
| Hexagonal Architecture | Architecture | Keeps the application core independent from HTTP, persistence, and remote-service technologies | `auction/src/main/java/com/garagebid/auction/application/port`, `adapter` |
| Ports and Adapters | Architecture | Defines explicit boundaries between the application core and external systems | `auction/.../application/port`, `auction/.../adapter` |
| Inbound Port | Hexagonal | Defines application capabilities independently from transport mechanisms | `OpenAuctionUseCase`, `PlaceBidUseCase`, `CloseAuctionUseCase` |
| Outbound Port | Hexagonal | Defines dependencies required by the application without depending on their implementation | `LoadAuctionPort`, `SaveAuctionPort`, `CarLookupPort` |
| Dependency Inversion | SOLID | Makes infrastructure depend on application-defined abstractions instead of the application depending on infrastructure | `AuctionService` → ports, adapters → ports |
| Interface Segregation | SOLID | Prevents broad infrastructure interfaces and keeps capabilities focused | `LoadAuctionPort`, `SaveAuctionPort` |
| Rich Domain Model | DDD | Keeps business rules close to the state they protect instead of placing them in service classes | `Auction.placeBid()`, `Auction.close()` |
| Aggregate Root | DDD | Protects auction invariants behind a single consistency boundary | `Auction` |
| Value Object | DDD | Models immutable domain concepts with validation and value-based equality | `Money`, `Bid` |
| Factory Method | DDD | Creates valid domain objects while preserving construction invariants | `Auction.open()` |
| Rehydration Factory | DDD / Persistence | Reconstructs an aggregate from persisted state without treating persistence as new business creation | `Auction.rehydrate()` |
| Domain / Persistence Model Separation | Architecture | Prevents JPA requirements from shaping the domain model | `Auction` vs `AuctionJpaEntity` |
| Persistence Mapper | Persistence | Translates between the domain model and JPA representation | `AuctionPersistenceMapper` |
| Repository Adapter | Hexagonal / Persistence | Implements application persistence ports using Spring Data JPA | `AuctionPersistenceAdapter` |
| Database per Service | Data | Establishes explicit data ownership and prevents cross-service SQL access | `catalog-db`, `auction-db` in `docker-compose.yml` |
| Schema Migration | Data | Makes schema evolution explicit and version-controlled | Flyway migrations under each service |
| Schema Validation | Data | Detects mismatches between JPA mappings and the Flyway-managed schema | `spring.jpa.hibernate.ddl-auto=validate` |
| DTO Boundary | API | Prevents persistence/domain objects from becoming public HTTP contracts | `CreateCarRequest`, `CarResponse`, `OpenAuctionRequest`, `PlaceBidRequest` |
| Command Object | Application | Separates use-case input contracts from HTTP request contracts | `OpenAuctionCommand`, `PlaceBidCommand` |
| Problem Details | API | Provides a standardized representation for HTTP errors | `GlobalExceptionHandler`, Spring `ProblemDetail` |
| Declarative HTTP Client | Communication | Describes synchronous HTTP contracts as Java interfaces instead of imperative request-building code | `CatalogFeignClient` with Spring Cloud OpenFeign |
| Anti-Corruption Layer | Integration | Translates catalog-service semantics into auction-service semantics | `CatalogHttpAdapter` |
| Synchronous Service Communication | Communication | Allows auction-service to validate data owned by catalog-service | `CarLookupPort` → `CatalogHttpAdapter` → `CatalogFeignClient` |
| Remote Failure Translation | Distributed Systems | Prevents infrastructure failures from being interpreted as business facts | `CatalogFeignErrorDecoder`, `CatalogHttpAdapter`, `CatalogUnavailableException` |
| Client-Specific HTTP Configuration | Integration | Keeps remote-client technical policy scoped to a specific downstream integration | `CatalogFeignConfiguration` |
| Configuration Externalization | 12-Factor | Allows environment and platform configuration to change without modifying application code | datasource environment variables, Consul configuration, Spring external configuration |
| Dependency Injection of Time | Testability | Makes time-dependent domain behavior deterministic during tests | `Clock` injection through `TimeConfig` |
| Code-First OpenAPI | API Documentation | Generates API documentation from the real HTTP implementation | Springdoc annotations on controllers |
| Fake Adapter Testing | Testing | Tests application orchestration without Spring, HTTP, Docker, or PostgreSQL | `AuctionServiceTest` fake port implementations |
| Service Registry | Platform | Maintains a catalog of running service instances and their locations | Consul service catalog |
| Service Registration | Platform | Allows running applications to advertise themselves to the service registry | Spring Cloud Consul discovery configuration in Catalog, Auction, and Gateway |
| Service Discovery | Platform | Resolves a logical service identity into currently available service instances | Consul + Spring Cloud DiscoveryClient |
| Health-Based Discovery | Platform | Prevents unhealthy service instances from being preferred for normal traffic | Consul `/actuator/health` checks and `query-passing` discovery configuration |
| Logical Service Identity | Distributed Systems | Decouples callers from physical service hosts and ports | `catalog-service`, `auction` |
| Client-Side Load Balancing | Communication | Selects one service instance from multiple discovered instances | Spring Cloud LoadBalancer |
| Horizontal Service Instances | Scalability | Allows multiple instances to provide the same logical service | Catalog instances on ports `8081` and `8091` registered as `catalog-service` |
| API Gateway | Platform / Edge | Provides a single external entry point and hides internal service topology from clients | `gateway` service using Spring Cloud Gateway |
| Discovery-Based Routing | Platform / Edge | Routes Gateway traffic using logical service identities instead of physical URLs | `lb://catalog-service`, `lb://auction` |
| Centralized Configuration | Platform / Configuration | Moves runtime configuration outside the application artifact and into a shared configuration store | Consul KV `config/gateway/data` |
| Fail-Fast Configuration | Reliability / Configuration | Prevents an application from starting successfully when required centralized configuration is unavailable | required `spring.config.import` for Consul Config |
| Control Plane / Data Plane Separation | Distributed Systems | Separates platform metadata operations from business request traffic | Consul for discovery/configuration; Gateway, Auction, and Catalog for HTTP business traffic |

---

# Layered Architecture

Implemented in:

```text
catalog-service
```

Flow:

```text
HTTP
  ↓
CarController
  ↓
CarService
  ↓
CarRepository
  ↓
PostgreSQL
```

The catalog service intentionally uses a conventional layered architecture.

This provides a baseline for comparison with the auction service.

### Benefits

- Simple
- Low ceremony
- Easy to understand
- Appropriate for CRUD-heavy services

### Trade-offs

- Framework and persistence concerns are closer to the business model
- Domain and persistence representations may become coupled
- Infrastructure boundaries are less explicit

---

# Hexagonal Architecture

Implemented in:

```text
auction-service
```

High-level flow:

```text
Inbound Adapter
      ↓
Inbound Port
      ↓
Application Service
      ↓
Domain
      ↓
Outbound Port
      ↑
Outbound Adapter
```

Examples:

```text
AuctionController
      ↓
OpenAuctionUseCase
      ↓
AuctionService
      ↓
CarLookupPort
      ↑
CatalogHttpAdapter
```

and:

```text
AuctionService
      ↓
SaveAuctionPort
      ↑
AuctionPersistenceAdapter
      ↓
Spring Data JPA
```

The main architectural property is dependency direction.

The application core does not depend on:

- JPA
- HTTP
- PostgreSQL
- OpenFeign
- Consul
- Spring Cloud LoadBalancer
- catalog-service implementation details

Infrastructure adapters depend on abstractions defined by the application.

This boundary was tested directly during Phase 03.

The Catalog HTTP implementation changed from:

```text
Spring HTTP Interface + RestClient
```

to:

```text
OpenFeign + LoadBalancer + Consul
```

without changing:

```text
AuctionService
CarLookupPort
Auction domain model
```

---

# Database per Service

Current topology:

```text
catalog-service
    ↓
catalog-db

auction-service
    ↓
auction-db
```

A service may only access its own database.

This intentionally prevents:

```text
auction-service → catalog-db
```

The auction service must use the Catalog API instead.

### What it solves

- Data ownership ambiguity
- Cross-service SQL joins
- Shared-schema coupling
- Accidental distributed monolith behavior

### Trade-offs

- No cross-service foreign keys
- No cross-service ACID transaction
- Distributed consistency becomes explicit
- Network communication becomes necessary

---

# Anti-Corruption Layer

Implemented by:

```text
CatalogHttpAdapter
```

The Catalog HTTP integration speaks in infrastructure semantics:

```text
2xx
404
5xx
connection failure
no discovered instance
```

The Auction application speaks in application semantics:

```text
car exists
car does not exist
catalog unavailable
```

The adapter translates between those worlds.

Current mapping:

```text
Catalog 2xx
→ true

Catalog 404
→ false

Catalog 5xx
→ CatalogUnavailableException

Transport failure
→ CatalogUnavailableException

No available Catalog instance
→ CatalogUnavailableException
```

This prevents infrastructure failures from becoming incorrect business facts.

---

# Rich Domain Model

The auction aggregate owns auction rules.

Example responsibilities:

```text
placeBid()
close()
hasWinner()
```

The application service does not implement rules such as:

```text
"new bid must be higher than the current bid"
```

That rule belongs to the aggregate because it protects aggregate state.

The application service instead orchestrates:

```text
load
→ execute domain behavior
→ save
```

---

# Value Objects

Examples:

```text
Money
Bid
```

Value objects are immutable and validate their own state.

`Money` owns:

- amount
- currency
- normalization of decimal scale
- same-currency comparison rules

This avoids spreading money-related rules across services and controllers.

---

# DTO vs Command vs Domain Model

GarageBid intentionally distinguishes several representations.

Example:

```text
HTTP JSON
   ↓
OpenAuctionRequest
   ↓
OpenAuctionCommand
   ↓
Auction / Money
```

Each model belongs to a different boundary.

### Request DTO

Represents the HTTP contract.

### Command

Represents a use-case contract.

### Domain Model

Represents business concepts and invariants.

This separation allows the HTTP contract to change without forcing the application or domain model to change.

---

# Declarative HTTP Client

The current synchronous Catalog contract is represented by:

```text
CatalogFeignClient
```

Conceptually:

```text
@FeignClient(name = "catalog-service")
```

The client describes the remote HTTP contract declaratively.

The caller does not manually construct:

```text
host
port
URL
HTTP request
```

The physical service location is resolved through the platform.

Current flow:

```text
CatalogFeignClient
      ↓
OpenFeign
      ↓
Spring Cloud LoadBalancer
      ↓
Service Discovery
      ↓
Consul
      ↓
catalog-service instance
```

The previous Spring HTTP Interface implementation remains documented in ADR-002.

It was superseded when Spring Cloud became an intentional platform dependency.

---

# Remote Failure Translation

A remote dependency can fail at several different layers.

Examples:

```text
Catalog returns 404
Catalog returns 500
connection refused
network I/O failure
no healthy service instance
```

These infrastructure failures should not leak directly into the application core.

The Catalog integration uses:

```text
CatalogFeignErrorDecoder
CatalogHttpAdapter
```

to normalize those failures.

HTTP failures:

```text
404
→ CatalogCarNotFoundException

5xx
→ CatalogUnavailableException
```

Transport-level failures where no HTTP response exists are translated by the adapter into:

```text
CatalogUnavailableException
```

The application therefore reasons using stable semantics instead of Feign-specific exception types.

---

# Service Registry

GarageBid uses:

```text
Consul
```

as the current service registry.

A service registry stores information about running service instances.

Example:

```text
catalog-service
├── catalog-service-8081
└── catalog-service-8091

auction
└── auction-8082

gateway
└── gateway-8080
```

The registry stores information such as:

```text
service name
instance ID
host
port
health state
```

The service registry itself does not proxy normal business traffic.

---

# Service Registration

Applications register themselves with Consul through Spring Cloud.

Conceptually:

```text
Catalog starts
    ↓
Spring Cloud Consul
    ↓
register catalog-service
    ↓
Consul
```

The same mechanism is used by:

```text
Auction
Gateway
```

A registration describes a running instance of a logical service.

For example:

```text
catalog-service
```

is the logical service identity.

```text
catalog-service-8081
```

is one physical instance.

---

# Service Discovery

A caller should not need to know:

```text
127.0.0.1:8081
127.0.0.1:8091
```

It should depend on:

```text
catalog-service
```

Conceptually:

```text
catalog-service
      ↓
DiscoveryClient
      ↓
Consul
      ↓
available instances
```

Example result:

```text
catalog-service
├── 127.0.0.1:8081
└── 127.0.0.1:8091
```

Service discovery answers:

> Which instances currently provide this service?

It does not decide which instance receives the request.

---

# Logical Service Identity

GarageBid distinguishes:

```text
service identity
```

from:

```text
physical service location
```

Example logical identity:

```text
catalog-service
```

Possible physical instances:

```text
127.0.0.1:8081
127.0.0.1:8091
```

The logical identity remains stable even when:

- instances restart,
- ports change,
- instances are added,
- instances are removed.

This reduces location coupling between services.

---

# Health-Based Discovery

A registered service instance is not automatically considered usable forever.

Consul checks:

```text
/actuator/health
```

and tracks instance health.

Conceptually:

```text
registered
+
passing health check
=
available discovery candidate
```

GarageBid configures discovery to prefer passing instances.

This allows a failing Catalog process to stop receiving normal traffic while another healthy instance remains available.

---

# Client-Side Load Balancing

Service discovery may return more than one instance.

Example:

```text
catalog-service
├── :8081
└── :8091
```

Spring Cloud LoadBalancer is responsible for selecting one of those instances.

Flow:

```text
Caller
   ↓
catalog-service
   ↓
Discovery
   ↓
[:8081, :8091]
   ↓
LoadBalancer
   ↓
selected instance
```

The business request then goes directly to the selected Catalog instance.

Consul does not sit in the business-request data path.

---

# Horizontal Service Instances

GarageBid runs two Catalog instances during load-balancing experiments:

```text
catalog-service :8081
catalog-service :8091
```

Both instances:

- execute the same application code,
- register under the same logical service identity,
- currently use the same Catalog database.

Verified behavior:

```text
2 healthy Catalog instances
→ request succeeds

1 healthy Catalog instance
→ request succeeds

0 healthy Catalog instances
→ request fails
```

This demonstrates process-level horizontal scaling.

It does not provide full high availability.

For example:

```text
two Catalog instances
+
one shared catalog-db
```

still means the database is a shared dependency.

Application replication and dependency replication solve different problems.

---

# API Gateway

GarageBid uses:

```text
Spring Cloud Gateway
```

as the external application entry point.

Before the Gateway:

```text
Client
├── Catalog :8081
└── Auction :8082
```

After the Gateway:

```text
Client
   ↓
Gateway :8080
   ├── /api/v1/cars/**
   │        ↓
   │   catalog-service
   │
   └── /api/v1/auctions/**
            ↓
          auction
```

External clients no longer need to know the internal service ports.

The Gateway is responsible for edge concerns, not domain behavior.

Possible future responsibilities include:

- authentication
- rate limiting
- CORS
- correlation IDs
- tracing
- edge-level request filters

Auction and Catalog business rules remain inside their owning services.

---

# Discovery-Based Routing

Gateway routes use logical service identities.

Examples:

```text
lb://catalog-service
lb://auction
```

Conceptually:

```text
Gateway
   ↓
route definition
   ↓
logical service identity
   ↓
Spring Cloud LoadBalancer
   ↓
Consul Discovery
   ↓
healthy service instance
```

This avoids hard-coded service URLs inside Gateway routing.

---

# Centralized Configuration

Gateway route definitions are stored outside the application artifact.

Current configuration location:

```text
Consul KV
```

Key:

```text
config/gateway/data
```

Conceptually:

```text
Gateway starts
   ↓
Consul Config
   ↓
config/gateway/data
   ↓
Spring Environment
   ↓
Gateway route definitions
```

This separates:

```text
application code / JAR
```

from:

```text
runtime configuration
```

The current implementation loads centralized configuration during startup.

Runtime ConfigWatch is intentionally disabled.

Therefore:

```text
Consul KV changed
→ Gateway restart required
```

Dynamic hot refresh is not currently required.

---

# Configuration Externalization

GarageBid externalizes configuration that varies by environment or runtime topology.

Examples include:

```text
database URL
database username
database password
Consul connection
Gateway route definitions
```

Application code should not contain deployment-specific values when those values can be supplied through configuration.

Centralized configuration extends this principle by moving selected runtime configuration into a shared store.

Not every property belongs in Consul.

Bootstrap configuration such as:

```text
application name
Consul host
Consul port
config import
```

must still exist locally so the application can locate the centralized configuration system.

---

# Fail-Fast Configuration

Some configuration is required for an application to fulfill its primary responsibility.

Gateway routes are treated as required configuration.

Conceptually:

```text
Consul configuration available
→ Gateway starts normally

required Consul configuration unavailable
→ Gateway startup fails
```

This prevents the Gateway from appearing healthy while having no usable routing configuration.

Fail-fast behavior trades availability for correctness and predictable startup semantics.

---

# Control Plane vs Data Plane

Phase 03 introduces an early example of control-plane and data-plane separation.

Control-plane information includes:

```text
service registrations
service locations
health information
Gateway configuration
```

Consul participates in this layer.

Business traffic includes:

```text
GET /api/v1/cars
POST /api/v1/auctions
```

This traffic flows through:

```text
Client
→ Gateway
→ service
```

or:

```text
Auction
→ Catalog
```

Consul is consulted for topology/configuration information but does not proxy every business request.

Conceptually:

```text
Control Plane
→ Consul

Data Plane
→ Gateway / Auction / Catalog HTTP traffic
```

---

# Remote Service Calls Are Not Local Method Calls

This code:

```text
carLookupPort.existsById(carId)
```

looks like an ordinary local method call.

At runtime it may depend on:

- TCP connectivity
- service discovery
- load-balancer state
- remote application availability
- remote thread pools
- remote database availability
- HTTP parsing
- timeouts
- deployments
- network latency

It may also fail before an HTTP connection is established.

For example:

```text
no healthy catalog-service instance
```

can fail at the discovery/load-balancing layer.

GarageBid intentionally exposes these failure modes instead of pretending remote communication behaves like an in-process function call.

Future resilience phases will add:

- explicit timeouts
- retries
- circuit breakers
- bulkheads
- rate limiting

---

# Patterns Planned for Future Phases

The following patterns are part of the GarageBid roadmap but are not considered implemented yet.

| Pattern | Planned Phase |
|---|---|
| Domain Event | Phase 04 |
| Event-Driven Architecture | Phase 04 |
| Transactional Outbox | Phase 04 |
| At-Least-Once Delivery | Phase 04 |
| Idempotent Consumer | Phase 04 |
| Eventual Consistency | Phase 04 |
| Saga | Phase 05 |
| Compensation | Phase 05 |
| WebSocket | Phase 06 |
| gRPC | Phase 06 |
| Redis-backed Real-Time State | Phase 06 |
| Circuit Breaker | Phase 07 |
| Retry with Backoff and Jitter | Phase 07 |
| Bulkhead | Phase 07 |
| Rate Limiting | Phase 07 |
| Distributed Tracing | Phase 08 |
| Structured Logging | Phase 08 |
| CQRS | Phase 09 |
| Materialized View | Phase 09 |
| OAuth2 / OIDC | Phase 10 |
| JWT Propagation | Phase 10 |
| Testcontainers | Phase 11 |
| Contract Testing | Phase 11 |
| Kubernetes | Phase 12 |
| Helm | Phase 12 |
| CI/CD | Phase 12 |

---

# Maintenance Rule

A pattern should only be added to the **Implemented Patterns** section after there is concrete code demonstrating it.

Every entry should have implementation evidence.

GarageBid should never claim a pattern simply because it appears in the roadmap.

When an implementation changes but the architectural pattern remains, the catalog should be updated to reflect the current implementation.

Historical technology decisions should remain documented through ADRs rather than being presented as the current architecture.