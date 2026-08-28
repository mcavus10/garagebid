# ADR-002 — Use Spring HTTP Interfaces for Synchronous Service Communication

## Status

Superseded by [ADR-003 — Use OpenFeign for Discovered Synchronous HTTP Clients](./003-openfeign-for-discovered-synchronous-clients.md).

---

## Date

2026-08-08

---

## Superseded

2026-08-28

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

At the time this decision was made, the project used:

- Java 21
- Spring Boot 4.1
- Spring Framework 7

Spring Cloud was not yet required by the architecture.

Introducing Spring Cloud only to obtain a declarative HTTP client would have added an unnecessary platform dependency.

---

# Decision

GarageBid will initially use Spring HTTP Interfaces with:

```text
@HttpExchange
```

and:

```text
RestClient
```

for synchronous service-to-service HTTP communication.

The catalog contract is represented by:

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

## Application-Facing Contract

```text
CarLookupPort
```

This represents what the auction application needs:

> Determine whether a car exists.

It contains no HTTP details.

---

## Technology-Facing Contract

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

# Why OpenFeign Was Initially Deferred

OpenFeign was considered a valid declarative HTTP client.

It was deliberately not selected at this stage.

Reasons:

1. GarageBid used Spring Boot 4 and Spring Framework 7.
2. Spring HTTP Interfaces are part of Spring Framework itself.
3. The project did not yet require Spring Cloud.
4. Avoiding an unnecessary dependency reduced compatibility risk.
5. `@HttpExchange` integrated naturally with the hexagonal outbound adapter.

OpenFeign was explicitly left as a future option if Spring Cloud later became a platform dependency.

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

Original mapping:

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

Only an actual:

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

The web adapter later maps that failure to:

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

If catalog-service is unavailable, auction-service would incorrectly conclude:

> The car does not exist.

This would transform an infrastructure failure into a false business fact.

The chosen design keeps those cases separate.

---

# Original Configuration Model

The catalog base URL was externalized.

Example:

```text
clients.catalog.base-url
```

Local default:

```text
http://localhost:8081
```

A deployment environment could override it.

This followed configuration-externalization principles, but the caller still depended on a physical service location.

---

# Synchronous Communication Trade-Off

The design introduces temporal coupling.

To open an auction:

```text
auction-service
```

requires:

```text
catalog-service
```

to be available at that moment.

## Advantages

- simple implementation,
- immediate validation,
- straightforward mental model,
- useful introduction to distributed communication.

## Disadvantages

- auction availability depends on catalog availability,
- remote latency affects auction latency,
- remote failures propagate into the request,
- cascading failure becomes possible.

These trade-offs remain true after ADR-003.

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

### Original Decision

Deferred.

At the time, Spring Cloud was not required by the project.

This alternative was later selected in ADR-003 after the platform architecture introduced Spring Cloud for:

- Consul,
- service discovery,
- client-side load balancing,
- API Gateway.

---

## Asynchronous Local Projection

Catalog could publish events and auction-service could maintain a local projection of cars.

### Advantages

- no synchronous runtime dependency,
- improved availability,
- reduced temporal coupling.

### Disadvantages

- eventual consistency,
- event infrastructure,
- duplicated read data,
- more operational complexity.

### Decision

Deferred.

This remains relevant for future event-driven phases.

---

# Timeout and Resilience

The first implementation intentionally did not attempt to solve all resilience concerns.

The project wanted to observe raw distributed failure behavior before hiding it behind resilience mechanisms.

Future phases will introduce:

- explicit timeouts,
- retry,
- exponential backoff,
- jitter,
- circuit breaker,
- bulkhead,
- fallback.

Those concerns remain deferred to the dedicated resilience phase.

---

# Consequences of the Original Decision

## Positive

- no Spring Cloud dependency was required,
- declarative HTTP contract,
- clean hexagonal integration,
- application remained HTTP-independent,
- remote semantics were translated explicitly,
- configuration was externalized.

## Negative

- physical service location still had to be configured,
- synchronous temporal coupling remained,
- generated proxy added runtime indirection,
- network failures became part of the auction use case,
- service availability became interdependent.

---

# Reason for Supersession

The original decision was valid under its original context.

During Phase 03, the context changed.

GarageBid introduced Spring Cloud as a platform dependency for:

```text
Consul Service Discovery
Spring Cloud LoadBalancer
Spring Cloud Gateway
Consul Config
```

The original reason for avoiding Spring Cloud therefore no longer applied.

The system also moved from:

```text
physical service URL
```

to:

```text
logical service identity
```

and required first-class integration with:

```text
service discovery
client-side load balancing
```

OpenFeign became a better fit for the new platform context.

The application-facing contract:

```text
CarLookupPort
```

remained unchanged.

This demonstrated that the hexagonal boundary successfully isolated the application core from the HTTP client technology.

---

# Superseding Decision

See:

```text
ADR-003 — Use OpenFeign for Discovered Synchronous HTTP Clients
```

The superseding decision changes the infrastructure implementation from:

```text
@HttpExchange + RestClient
```

to:

```text
OpenFeign + Spring Cloud LoadBalancer + Consul
```

while preserving:

```text
AuctionService
CarLookupPort
CatalogHttpAdapter boundary
domain model
```