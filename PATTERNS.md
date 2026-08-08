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
| Declarative HTTP Client | Communication | Describes synchronous HTTP contracts as Java interfaces instead of imperative request-building code | `CatalogHttpClient` with `@HttpExchange` |
| Anti-Corruption Layer | Integration | Translates catalog-service semantics into auction-service semantics | `CatalogHttpAdapter` |
| Synchronous Service Communication | Communication | Allows auction-service to validate data owned by catalog-service | `CarLookupPort` → `CatalogHttpAdapter` |
| Remote Failure Translation | Distributed Systems | Prevents infrastructure failures from being interpreted as business facts | `CatalogUnavailableException` |
| Configuration Externalization | 12-Factor | Allows infrastructure topology to change without changing application code | `DB_URL`, `CATALOG_BASE_URL`, datasource configuration |
| Dependency Injection of Time | Testability | Makes time-dependent domain behavior deterministic during tests | `Clock` injection through `TimeConfig` |
| Code-First OpenAPI | API Documentation | Generates API documentation from the real HTTP implementation | Springdoc annotations on controllers |
| Fake Adapter Testing | Testing | Tests application orchestration without Spring, HTTP, Docker, or PostgreSQL | `AuctionServiceTest` fake port implementations |

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
- RestClient
- catalog-service implementation details

Infrastructure adapters depend on abstractions defined by the application.

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

The auction service must use the catalog API instead.

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

The catalog HTTP API speaks in HTTP semantics:

```text
200
404
500
connection failure
```

The auction application speaks in business/application semantics:

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

Catalog 5xx / connection failure
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

# Remote Service Calls Are Not Local Method Calls

This code:

```text
carLookupPort.existsById(carId)
```

looks like an ordinary local method call.

At runtime it may depend on:

- TCP connectivity
- DNS
- remote application availability
- remote thread pools
- remote database availability
- HTTP parsing
- timeouts
- deployments
- network latency

GarageBid intentionally exposes these failure modes instead of hiding them.

Future resilience phases will add:

- timeouts
- retries
- circuit breakers
- bulkheads
- rate limiting

---

# Patterns Planned for Future Phases

The following patterns are part of the GarageBid roadmap but are not considered implemented yet.

| Pattern | Planned Phase |
|---|---|
| API Gateway | Phase 03 |
| Service Discovery | Phase 03 |
| Centralized Configuration | Phase 03 |
| Client-Side Load Balancing | Phase 03 |
| Domain Event | Phase 04 |
| Event-Driven Architecture | Phase 04 |
| Transactional Outbox | Phase 04 |
| At-Least-Once Delivery | Phase 04 |
| Idempotent Consumer | Phase 04 |
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