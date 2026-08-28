# Phase 02 — Auction Service

## Status

Completed.

---

# Goal

Phase 02 introduces the auction bounded context using Hexagonal Architecture and a richer domain model.

The primary learning objective is to compare a conventional layered service with an application whose business core is explicitly isolated from infrastructure.

The auction service is intentionally more complex than the catalog service because auction behavior contains meaningful business invariants.

---

# Responsibilities

The auction service supports:

- opening an auction,
- validating the referenced vehicle through catalog-service,
- placing bids,
- rejecting invalid bids,
- closing auctions,
- translating business errors to HTTP responses,
- persisting auction state,
- documenting its API through OpenAPI.

---

# Architecture

The service uses Hexagonal Architecture, also known as Ports and Adapters.

High-level structure:

```text
                    Outside World
                         │
                         ▼
                  Inbound Adapter
                         │
                         ▼
                    Inbound Port
                         │
                         ▼
                 Application Service
                         │
                         ▼
                       Domain
                         │
                         ▼
                   Outbound Ports
                    ▲           ▲
                    │           │
          Persistence Adapter   Catalog HTTP Adapter
                    │           │
                    ▼           ▼
                PostgreSQL   catalog-service
```

Package structure:

```text
com.garagebid.auction
├── adapter
│   ├── in
│   │   └── web
│   └── out
│       ├── persistence
│       └── catalog
│
├── application
│   ├── port
│   │   ├── in
│   │   └── out
│   └── service
│
├── config
│
└── domain
    └── model
```

---

# Dependency Direction

The most important architectural property is dependency direction.

The application core defines the contracts.

Infrastructure implements them.

For example:

```text
AuctionService
      ↓
SaveAuctionPort
      ↑
AuctionPersistenceAdapter
```

The application service does not depend on:

```text
AuctionPersistenceAdapter
```

Instead, the adapter depends on the interface defined by the application.

The same pattern is used for service-to-service communication:

```text
AuctionService
      ↓
CarLookupPort
      ↑
CatalogHttpAdapter
```

---

# Domain Model

The auction domain is pure Java and contains no Spring or JPA dependencies.

Core types:

```text
Auction
Money
Bid
AuctionStatus
```

The aggregate root is:

```text
Auction
```

---

# Aggregate Behavior

The aggregate exposes behaviors such as:

```text
Auction.open(...)
Auction.placeBid(...)
Auction.close()
Auction.hasWinner()
```

Business rules live inside the aggregate.

Examples:

- An auction must be open to accept bids.
- A bid cannot be placed after the auction end time.
- The first bid must be greater than or equal to the starting price.
- Every later bid must be strictly greater than the current highest bid.
- A closed auction cannot be closed again.

These rules are not implemented inside the controller or application service.

---

# Value Objects

## Money

`Money` represents:

- amount,
- currency.

Responsibilities include:

- null validation,
- non-negative amount validation,
- currency-aware decimal scale,
- same-currency comparison.

Example operations:

```text
isGreaterThan()
isGreaterThanOrEqual()
```

---

## Bid

`Bid` represents:

- bidder ID,
- amount,
- placement time.

It is immutable.

---

# Domain vs Persistence Model

The auction service intentionally separates:

```text
Auction
```

from:

```text
AuctionJpaEntity
```

The domain model contains business behavior.

The JPA entity contains persistence mappings.

Translation is performed by:

```text
AuctionPersistenceMapper
```

Flow when saving:

```text
Auction
   ↓
AuctionPersistenceMapper
   ↓
AuctionJpaEntity
   ↓
JpaRepository
```

Flow when loading:

```text
JpaRepository
   ↓
AuctionJpaEntity
   ↓
AuctionPersistenceMapper
   ↓
Auction.rehydrate(...)
```

This prevents Hibernate requirements from shaping the domain API.

---

# Inbound Ports

Inbound ports define application capabilities.

Implemented ports:

```text
OpenAuctionUseCase
PlaceBidUseCase
CloseAuctionUseCase
```

These interfaces do not depend on HTTP.

A future adapter could invoke the same use case from:

- Kafka,
- CLI,
- WebSocket,
- scheduled jobs,

without changing the application contract.

---

# Commands

Use-case inputs are represented as commands.

Examples:

```text
OpenAuctionCommand
PlaceBidCommand
```

These are different from web request DTOs.

Example flow:

```text
HTTP JSON
   ↓
OpenAuctionRequest
   ↓
OpenAuctionCommand
   ↓
OpenAuctionUseCase
```

The separation exists because:

```text
HTTP contract != application contract
```

---

# Outbound Ports

Implemented outbound ports:

```text
LoadAuctionPort
SaveAuctionPort
CarLookupPort
```

They represent application needs.

They do not describe how those needs are implemented.

For example:

```text
CarLookupPort
```

means:

> The auction application needs to determine whether a car exists.

It does not mean:

> Perform an HTTP GET against localhost:8081.

The latter is infrastructure.

---

# Persistence Adapter

Persistence is implemented by:

```text
AuctionPersistenceAdapter
```

It implements:

```text
LoadAuctionPort
SaveAuctionPort
```

The adapter depends on:

```text
AuctionJpaRepository
```

which is package-private because it is considered an infrastructure implementation detail.

---

# Database per Service

During Phase 02, GarageBid moved from logical schema isolation to independent PostgreSQL databases.

Current topology:

```text
catalog-service
    ↓
catalog-db :5433

auction-service
    ↓
auction-db :5434
```

The auction service cannot directly query the catalog database.

This makes data ownership explicit.

Cross-service validation must happen through the catalog API.

---

# Synchronous Catalog Validation

Before an auction is opened, the auction service verifies that the referenced car currently exists.

Flow:

```text
POST /api/v1/auctions
        ↓
AuctionController
        ↓
OpenAuctionUseCase
        ↓
AuctionService
        ↓
CarLookupPort
        ↓
CatalogHttpAdapter
        ↓
CatalogHttpClient
        ↓
@HttpExchange
        ↓
RestClient
        ↓
catalog-service
        ↓
catalog-db
```

---

# Spring HTTP Interface

The catalog HTTP contract is represented by a declarative client interface.

Conceptually:

```java
@HttpExchange("/api/v1/cars")
interface CatalogHttpClient {
    ...
}
```

Spring creates the runtime implementation through:

```text
HttpServiceProxyFactory
```

backed by:

```text
RestClient
```

The application does not depend on this HTTP-specific interface.

Only the outbound adapter knows about it.

---

# Why Not OpenFeign?

OpenFeign was deliberately not used in this phase.

GarageBid currently uses:

- Spring Boot 4
- Spring Framework 7

The project avoids introducing Spring Cloud dependencies before confirming version alignment.

Spring HTTP Interfaces provide declarative synchronous HTTP communication using Spring Framework itself.

This decision can be revisited if future platform requirements justify Spring Cloud.

---

# Anti-Corruption Layer

`CatalogHttpAdapter` translates catalog-service behavior into auction-service semantics.

Remote behavior:

```text
Catalog 2xx
Catalog 404
Catalog 5xx
Connection failure
```

Local application behavior:

```text
true
false
CatalogUnavailableException
```

Mapping:

```text
2xx
→ car exists

404
→ car does not exist

5xx / network failure
→ catalog unavailable
```

This prevents the auction application from depending directly on remote HTTP semantics.

---

# Remote Failure Is Not a Business Negative

One of the most important lessons in this phase is:

```text
catalog unavailable
!=
car does not exist
```

The following implementation would be dangerous:

```text
catch every remote exception
→ return false
```

That would convert infrastructure failure into incorrect business information.

Instead:

```text
404
→ false

network / server failure
→ CatalogUnavailableException
```

---

# HTTP Error Semantics

Current mappings include:

```text
Auction does not exist
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

The domain model contains no HTTP status codes.

The application model contains no HTTP status codes.

HTTP translation belongs to:

```text
GlobalExceptionHandler
```

inside the inbound web adapter.

---

# Distributed Validation Is Not a Foreign Key

The auction database stores:

```text
car_id
```

but no database foreign key points to the catalog database.

Such a cross-service foreign key would violate independent database ownership.

The synchronous validation gives only a time-local guarantee.

Example race:

```text
T1 auction asks catalog whether car exists
T2 catalog responds yes
T3 car is deleted
T4 auction is persisted
```

Therefore:

> Successful synchronous validation does not create a distributed foreign key.

This is an introduction to distributed consistency.

---

# Temporal Coupling

Synchronous catalog validation introduces temporal coupling.

To open an auction:

```text
auction-service
```

currently depends on:

```text
catalog-service
```

being available at the same time.

Benefits:

- simple model,
- immediate validation,
- easy to understand.

Costs:

- auction availability depends on catalog availability,
- network latency enters the use case,
- remote failures become part of the request flow.

Alternative designs may later include local projections built from events.

---

# Transaction Boundaries

Remote HTTP calls should not be unnecessarily performed while holding database transactions open.

The `openAuction` flow validates the remote car before persisting the auction.

The goal is to avoid holding a database connection while waiting on an external network dependency.

This concern becomes more important as:

- traffic grows,
- latency increases,
- connection pools become constrained,
- downstream failures become slower.

---

# Web Adapter

Implemented endpoints:

```http
POST /api/v1/auctions
POST /api/v1/auctions/{auctionId}/bids
POST /api/v1/auctions/{auctionId}/close
```

The controller depends on inbound ports rather than directly depending on the concrete application service.

Example:

```text
AuctionController
→ OpenAuctionUseCase
```

instead of:

```text
AuctionController
→ AuctionService
```

---

# HTTP Semantics

## Open Auction

```http
POST /api/v1/auctions
```

Successful result:

```text
201 Created
```

The created auction URI is returned through the:

```text
Location
```

header.

---

## Place Bid

```http
POST /api/v1/auctions/{auctionId}/bids
```

Successful result:

```text
204 No Content
```

The auction ID belongs in the URL path rather than being duplicated in the request body.

---

## Close Auction

```http
POST /api/v1/auctions/{auctionId}/close
```

Successful result:

```text
204 No Content
```

The command-style endpoint is intentionally explicit about domain behavior.

Closing an auction is more than assigning:

```text
status = CLOSED
```

It represents a domain operation that may enforce invariants.

---

# OpenAPI

Swagger UI:

```text
http://localhost:8082/swagger-ui/index.html
```

OpenAPI JSON:

```text
http://localhost:8082/v3/api-docs
```

The controller is documented using:

```text
@Tag
@Operation
```

OpenAPI annotations stay in the web adapter because API documentation is an HTTP concern.

---

# Testing

The auction service contains pure domain tests and application-service tests.

One major benefit of the architecture is that application behavior can be tested without:

- Spring context,
- HTTP,
- PostgreSQL,
- Docker.

`AuctionServiceTest` supplies fake implementations of:

```text
LoadAuctionPort
SaveAuctionPort
CarLookupPort
```

and uses:

```text
Clock.fixed(...)
```

for deterministic time behavior.

---

# Failure Scenarios Observed

During Phase 02 the following failure modes were intentionally exercised.

## Low bid

```text
result → 409 Conflict
```

## Closing an already closed auction

```text
result → 409 Conflict
```

## Referencing a missing catalog car

```text
catalog → 404
auction → 422
```

## Catalog unavailable

```text
connection failure
→ CatalogUnavailableException
→ 503 Service Unavailable
```

## Persistence schema mismatch

```text
Hibernate validate
→ application startup failure
```

These failures are considered part of the learning process rather than accidental interruptions.

---

# Key Lessons

## 1. Hexagonal architecture is about dependency direction

Creating folders called:

```text
port
adapter
```

does not make an application hexagonal.

The important property is:

```text
Infrastructure → Application abstractions
```

rather than:

```text
Application → Infrastructure implementations
```

---

## 2. Application services should orchestrate

The application service coordinates:

```text
load
validate
invoke domain behavior
save
```

Business invariants remain in the aggregate.

---

## 3. Not every dependency needs an interface

Ports should represent meaningful boundaries or external dependencies.

Creating interfaces for every class would produce unnecessary abstraction.

---

## 4. Network calls introduce new failure modes

A method such as:

```text
existsById()
```

may look local but can represent:

- another process,
- another database,
- TCP,
- latency,
- remote failures.

Distributed calls must be designed with failure in mind.

---

## 5. Database per service exposes distributed consistency

Removing shared database access makes service boundaries stronger.

It also removes:

- cross-service foreign keys,
- cross-service transactions,
- cross-service joins.

The resulting consistency problems are real distributed-system problems that later patterns must address.

---

# Phase Acceptance Criteria

Phase 02 is considered complete when the following behaviors work:

```text
catalog health
→ UP

auction health
→ UP

GET catalog cars
→ 200

open auction with real car
→ 201

open auction with missing car
→ 422

place valid bid
→ 204

place low bid
→ 409

close auction
→ 204

close auction again
→ 409

catalog unavailable while opening auction
→ 503

auction tests
→ green
```

---

# Phase Outcome

At the end of Phase 02, GarageBid has demonstrated:

- Hexagonal Architecture
- Ports and Adapters
- Dependency Inversion
- Interface Segregation
- Rich Domain Model
- Aggregate Root
- Value Objects
- Domain/persistence separation
- Database per Service
- Synchronous service communication
- Declarative HTTP clients
- Anti-Corruption Layer
- Distributed validation
- Remote failure semantics
- Testable application boundaries
- OpenAPI documentation

The system has crossed the point where it is no longer simply multiple Spring Boot applications.

It now contains real distributed-system boundaries and real cross-service failure modes.

---

# Next Phase

Phase 03 introduces the platform layer.

Planned topics:

- API Gateway
- Service Discovery
- Centralized Configuration
- Client-Side Load Balancing
- Spring Boot 4 compatibility considerations