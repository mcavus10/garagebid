# ADR-002 — Use Spring HTTP Interfaces for Synchronous Service Communication

## Status

Accepted.

---

## Date

2026-08-08

---

# Context

The auction service needs to validate whether a referenced vehicle exists before opening an auction.

Vehicle data belongs to:

```text
catalog-service
```

Auction data belongs to:

```text
auction-service
```

Because GarageBid uses database per service, auction-service cannot directly query the catalog database.

A service-to-service communication mechanism is therefore required.

The first integration is synchronous:

```text
auction-service
→ HTTP
→ catalog-service
```

The project uses:

- Java 21
- Spring Boot 4.1
- Spring Framework 7

Spring Cloud compatibility with the newest Spring Boot generation may evolve independently from Spring Framework.

The project therefore wants to avoid introducing unnecessary Spring Cloud coupling during this phase.

---

# Decision

GarageBid will use Spring HTTP Interfaces with:

```text
@HttpExchange
```

and:

```text
RestClient
```

for synchronous service-to-service HTTP communication.

The catalog contract is represented by an interface similar to:

```text
CatalogHttpClient
```

The runtime implementation is generated through:

```text
HttpServiceProxyFactory
```

backed by:

```text
RestClient
```

---

# Architecture

The integration is deliberately separated into two contracts.

## Application-facing contract

```text
CarLookupPort
```

This represents what the auction application needs:

> Determine whether a car exists.

It contains no HTTP details.

---

## Technology-facing contract

```text
CatalogHttpClient
```

This describes how catalog-service is called through HTTP.

Conceptually:

```text
@HttpExchange("/api/v1/cars")
GET /{carId}
```

This interface belongs to the outbound adapter.

---

# Dependency Flow

```text
AuctionService
      ↓
CarLookupPort
      ↑
CatalogHttpAdapter
      ↓
CatalogHttpClient
      ↓
Spring HTTP Interface Proxy
      ↓
RestClient
      ↓
HTTP
      ↓
catalog-service
```

The application service does not know about:

- `RestClient`,
- `@HttpExchange`,
- URLs,
- HTTP status codes.

---

# Why Not Call `RestClient` Directly from `AuctionService`?

Direct usage would look conceptually like:

```text
AuctionService
→ RestClient
→ HTTP
```

This would make the application service responsible for infrastructure concerns such as:

- URL construction,
- HTTP methods,
- status-code handling,
- network exceptions.

That would violate the intended hexagonal boundary.

Instead:

```text
AuctionService
→ CarLookupPort
```

keeps the application technology-independent.

---

# Why Not Use `RestClient` Directly Inside the Adapter?

Using `RestClient` directly inside `CatalogHttpAdapter` would still be architecturally valid.

For example:

```text
CatalogHttpAdapter
→ RestClient.get()
→ uri(...)
→ retrieve(...)
```

However, Spring HTTP Interfaces provide a clearer declarative representation of the remote HTTP contract.

Benefits include:

- less repetitive request-building code,
- API paths visible in one interface,
- easier reading of the remote contract,
- clean separation between semantic translation and HTTP declaration.

The adapter can focus on semantic translation while the HTTP interface describes transport details.

---

# Why Not OpenFeign?

OpenFeign is a valid declarative HTTP client and is widely used in Spring-based microservices.

It was not rejected as a technology.

It was deliberately not selected for the current phase.

Reasons:

1. GarageBid uses Spring Boot 4 and Spring Framework 7.
2. Spring HTTP Interfaces are part of Spring Framework itself.
3. The project does not currently require Spring Cloud features for this client.
4. Avoiding an unnecessary dependency reduces compatibility risk.
5. `@HttpExchange` integrates naturally with the hexagonal outbound adapter.

OpenFeign may be reconsidered later if Spring Cloud becomes necessary for platform-level features.

---

# Remote Semantics

Catalog-service exposes HTTP behavior.

Possible outcomes include:

```text
2xx
404
5xx
connection failure
timeout
```

The auction application should not reason directly in those terms.

The outbound adapter translates remote HTTP behavior into local application semantics.

Current mapping:

```text
Catalog 2xx
→ car exists

Catalog 404
→ car does not exist

Catalog 5xx
→ dependency unavailable

Connection failure
→ dependency unavailable
```

---

# Anti-Corruption Layer

The following adapter acts as a small Anti-Corruption Layer:

```text
CatalogHttpAdapter
```

The external language is:

```text
HTTP status / network exception
```

The auction language is:

```text
boolean existence
CatalogUnavailableException
```

This prevents the auction application from becoming coupled to catalog-service's transport representation.

---

# Failure Semantics

A key decision is:

```text
catalog unavailable
!=
car does not exist
```

Only an actual catalog:

```text
404 Not Found
```

is translated into:

```text
false
```

Unexpected remote failures are translated into:

```text
CatalogUnavailableException
```

The web adapter later maps that application failure to:

```text
503 Service Unavailable
```

---

# Why This Matters

Consider an unsafe implementation:

```text
try remote call

catch any exception
    return false
```

If catalog-service is down, the auction service would incorrectly conclude:

> The car does not exist.

This would transform an infrastructure failure into a false business fact.

The chosen design keeps those cases separate.

---

# Configuration

The catalog base URL is externalized.

Example:

```text
clients.catalog.base-url
```

Local default:

```text
http://localhost:8081
```

A deployment environment may override it.

For example:

```text
http://catalog-service:8081
```

Application code does not change.

This follows configuration-externalization principles.

---

# Synchronous Communication Trade-Off

The current design introduces temporal coupling.

To open an auction:

```text
auction-service
```

requires:

```text
catalog-service
```

to be available at that moment.

### Advantages

- simple implementation,
- immediate validation,
- straightforward mental model,
- useful introduction to distributed communication.

### Disadvantages

- auction availability depends on catalog availability,
- remote latency affects auction latency,
- remote failures propagate into the request,
- cascading failure becomes possible.

These trade-offs are intentional.

---

# Alternatives Considered

## Direct Database Access

```text
auction-service
→ catalog-db
```

### Decision

Rejected.

This violates database ownership and creates strong coupling between service data models.

---

## Shared Library with Catalog Repository

Auction-service could theoretically reuse catalog persistence classes.

### Decision

Rejected.

This would create compile-time coupling and destroy service independence.

---

## OpenFeign

### Decision

Deferred.

It remains a valid alternative but introduces Spring Cloud dependencies that are not currently required.

---

## Asynchronous Local Projection

Catalog could publish events and auction-service could maintain a local projection of cars.

### Advantages

- no synchronous runtime dependency,
- improved availability,
- reduced temporal coupling.

### Disadvantages

- eventual consistency,
- Kafka/event infrastructure,
- duplicated read data,
- more operational complexity.

### Decision

Deferred.

This pattern may be introduced in a later event-driven phase.

---

# Timeout and Resilience

The first implementation intentionally does not attempt to solve all resilience concerns.

The project wants to observe raw distributed failure behavior before hiding it behind resilience mechanisms.

Future phases will introduce:

- explicit timeouts,
- retry,
- exponential backoff,
- jitter,
- circuit breaker,
- bulkhead,
- fallback.

Those concerns belong to the dedicated resilience phase.

---

# Consequences

## Positive

- no Spring Cloud dependency required,
- declarative HTTP contract,
- clean hexagonal integration,
- application remains HTTP-independent,
- remote semantics are translated explicitly,
- configuration remains externalized.

## Negative

- synchronous temporal coupling,
- generated proxy adds runtime indirection,
- network failures are now part of the auction use case,
- service availability becomes interdependent.

---

# Reconsideration Criteria

This decision may be reconsidered if:

- Spring Cloud becomes a core platform dependency,
- OpenFeign provides required capabilities not available through HTTP Interfaces,
- communication moves from REST to gRPC,
- auction-service adopts an asynchronous local catalog projection,
- platform architecture standardizes another HTTP client.

The application-facing contract:

```text
CarLookupPort
```

should remain stable whenever possible, even if the transport implementation changes.