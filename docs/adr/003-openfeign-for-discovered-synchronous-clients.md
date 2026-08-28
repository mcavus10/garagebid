# ADR-003 — Use OpenFeign for Discovered Synchronous HTTP Clients

## Status

Accepted.

---

## Date

2026-08-28

---

# Context

GarageBid originally used Spring HTTP Interfaces and `RestClient` for synchronous communication from auction-service to catalog-service.

That decision is documented in:

```text
ADR-002 — Use Spring HTTP Interfaces for Synchronous Service Communication
```

At that time, Spring Cloud was not yet required by the project.

During Phase 03, the platform architecture introduced:

- Consul service registration,
- Consul service discovery,
- Spring Cloud LoadBalancer,
- Spring Cloud Gateway,
- Consul centralized configuration.

Spring Cloud therefore became an intentional platform dependency.

At the same time, service communication evolved from:

```text
auction-service
→ http://localhost:8081
→ catalog-service
```

to:

```text
auction-service
→ catalog-service
→ discovery
→ load balancing
→ healthy service instance
```

The catalog client now needs to integrate naturally with logical service identities and client-side load balancing.

---

# Decision

GarageBid will use Spring Cloud OpenFeign for discovered synchronous HTTP clients.

The auction-to-catalog integration will use:

```text
CatalogFeignClient
```

with:

```java
@FeignClient(name = "catalog-service")
```

No physical URL will be configured for the client.

The service name:

```text
catalog-service
```

is treated as a logical service identity.

Spring Cloud LoadBalancer resolves that identity through the configured discovery mechanism.

Currently:

```text
Consul
```

provides service discovery.

---

# Architecture

The resulting dependency flow is:

```text
AuctionService
      ↓
CarLookupPort
      ↑
CatalogHttpAdapter
      ↓
CatalogFeignClient
      ↓
OpenFeign
      ↓
Spring Cloud LoadBalancer
      ↓
DiscoveryClient
      ↓
Consul
      ↓
catalog-service instance
```

The application core does not depend on:

- Feign,
- Consul,
- Spring Cloud LoadBalancer,
- URLs,
- HTTP status codes.

---

# Application-Facing Contract

The application continues to depend on:

```text
CarLookupPort
```

Conceptually:

```java
boolean existsById(UUID carId);
```

This represents the business/application need:

> Determine whether the referenced car exists.

The port does not describe how that information is obtained.

---

# Remote HTTP Contract

The remote transport contract is represented by:

```text
CatalogFeignClient
```

Conceptually:

```java
@FeignClient(
    name = "catalog-service",
    path = "/api/v1/cars"
)
interface CatalogFeignClient {

    @GetMapping("/{carId}")
    void getCar(UUID carId);
}
```

The interface belongs to the outbound infrastructure adapter.

---

# Why No URL Is Configured

The Feign client deliberately does not contain:

```text
http://localhost:8081
```

or any other physical endpoint.

Instead:

```text
name = "catalog-service"
```

represents the logical service identity.

Conceptually:

```text
Feign
   ↓
catalog-service
   ↓
LoadBalancer
   ↓
DiscoveryClient
   ↓
Consul
   ↓
healthy instances
```

Example discovery result:

```text
catalog-service
├── 127.0.0.1:8081
└── 127.0.0.1:8091
```

The load balancer selects one available instance.

---

# Why OpenFeign

## Declarative HTTP Contract

Feign allows the remote HTTP API to be described as an interface.

Instead of imperative request construction:

```text
build client
build URI
execute GET
decode response
```

the client declares:

```text
GET /api/v1/cars/{carId}
```

---

## Spring Cloud Integration

GarageBid already uses Spring Cloud.

OpenFeign integrates naturally with:

```text
Spring Cloud LoadBalancer
Service Discovery
client-specific configuration
ErrorDecoder
request interceptors
```

This makes it suitable for the Phase 03 platform architecture.

---

## Logical Service Identity

The client can use:

```text
catalog-service
```

instead of:

```text
localhost:8081
```

Physical topology is no longer owned by the application.

---

# Why Not Continue Using Spring HTTP Interfaces?

Spring HTTP Interfaces remain a valid technology.

They were successfully used during Phase 02.

The decision changed because the architectural context changed.

The original advantage:

```text
avoid introducing Spring Cloud
```

no longer exists because Spring Cloud is already required by the platform layer.

In addition, the OpenFeign integration provides a direct model for:

```text
declarative client
+
discovery
+
client-side load balancing
```

which is an explicit learning goal of Phase 03.

---

# Why Not Use Feign Directly from `AuctionService`?

The following design is rejected:

```text
AuctionService
→ CatalogFeignClient
```

because it would make the application layer depend on an infrastructure technology.

Instead:

```text
AuctionService
→ CarLookupPort
```

and:

```text
CatalogHttpAdapter
→ CatalogFeignClient
```

The adapter protects the application from Feign-specific details.

---

# Error Translation

Remote HTTP behavior should not leak into the application layer.

The outbound integration translates remote failures into local semantics.

Current behavior:

```text
Catalog 2xx
→ car exists

Catalog 404
→ car does not exist

Catalog 5xx
→ catalog unavailable

No healthy instance
→ catalog unavailable

Transport failure
→ catalog unavailable
```

Application-level behavior remains:

```text
boolean
CatalogUnavailableException
```

---

# Feign ErrorDecoder

Feign's:

```text
ErrorDecoder
```

is used for HTTP responses that represent failure.

Example:

```text
404
→ CatalogCarNotFoundException

5xx
→ CatalogUnavailableException
```

This translation happens at the infrastructure boundary.

The application does not see:

```text
FeignException.NotFound
FeignException.InternalServerError
```

---

# Transport Failures

Some failures occur before any HTTP response exists.

Examples:

```text
connection refused
connection timeout
network I/O failure
```

These may surface as:

```text
RetryableException
```

The adapter translates these failures into:

```text
CatalogUnavailableException
```

The exception name does not mean that GarageBid currently performs automatic retries.

---

# Retry Policy

Automatic retries are not enabled as part of this decision.

Retry behavior is intentionally deferred.

Before enabling retry, GarageBid will explicitly consider:

- idempotency,
- duplicate operations,
- retry storms,
- backoff,
- jitter,
- timeout budgets.

Retry belongs to the resilience phase.

---

# Client-Specific Configuration

Feign-specific behavior should remain scoped to the remote client.

Example configuration concerns include:

```text
ErrorDecoder
timeouts
logging
request interceptors
authentication propagation
```

GarageBid uses:

```text
CatalogFeignConfiguration
```

to isolate catalog-specific client behavior.

A future payment integration may use:

```text
PaymentFeignConfiguration
```

with different policies.

---

# Integration Structure

The catalog integration currently follows:

```text
adapter/out/catalog
├── CatalogFeignClient
├── CatalogFeignConfiguration
├── CatalogFeignErrorDecoder
├── CatalogCarNotFoundException
└── CatalogHttpAdapter
```

Each component has a focused responsibility.

```text
CatalogFeignClient
→ remote HTTP contract

CatalogFeignConfiguration
→ Feign-specific technical configuration

CatalogFeignErrorDecoder
→ HTTP failure translation

CatalogCarNotFoundException
→ adapter-internal failure representation

CatalogHttpAdapter
→ application semantic translation
```

Not every future integration must contain exactly the same number of classes.

The structure should grow only when the integration requires those responsibilities.

---

# Service Discovery

The Feign client does not know the physical Catalog address.

The logical service ID:

```text
catalog-service
```

is resolved using Spring Cloud's discovery abstraction.

Current implementation:

```text
Spring Cloud LoadBalancer
→ DiscoveryClient
→ Consul
```

This allows Catalog instances to change without modifying auction-service configuration.

---

# Client-Side Load Balancing

When discovery returns multiple healthy instances:

```text
catalog-service
├── instance A
└── instance B
```

the caller selects an instance through Spring Cloud LoadBalancer.

The business request then flows directly to the selected instance.

Consul does not proxy the request.

---

# Failure Experiment

The following behavior was verified.

```text
2 healthy Catalog instances
→ auction request succeeds

1 healthy Catalog instance
→ auction request succeeds

0 healthy Catalog instances
→ auction returns 503 Service Unavailable
```

This demonstrates the combination of:

```text
Feign
+
LoadBalancer
+
Service Discovery
```

---

# Alternatives Considered

## Spring HTTP Interfaces

### Advantages

- part of Spring Framework,
- no Feign dependency,
- modern declarative HTTP API,
- works well without Spring Cloud.

### Disadvantages in Current Context

- GarageBid already depends on Spring Cloud,
- additional integration plumbing was required for the chosen discovery/load-balancing experiment.

### Decision

Superseded for the current discovered synchronous client.

Spring HTTP Interfaces remain a valid technology and may still be used in other contexts.

---

## Direct `RestClient`

### Advantages

- explicit,
- simple,
- part of Spring Framework.

### Disadvantages

- more imperative client code,
- transport concerns become more visible inside the adapter,
- less convenient for the current declarative discovery-based client model.

### Decision

Rejected for the current integration.

---

## WebClient

### Advantages

- non-blocking,
- natural fit for reactive applications,
- useful for high-concurrency reactive workloads.

### Disadvantages

- auction-service is currently Spring MVC/blocking,
- introducing reactive programming only for this integration would add unnecessary complexity.

### Decision

Rejected for the current use case.

---

## Asynchronous Local Projection

Auction-service could maintain local catalog data through events.

### Advantages

- removes synchronous runtime dependency,
- improved autonomy,
- better availability.

### Disadvantages

- eventual consistency,
- duplicated data,
- messaging infrastructure,
- more complex consistency semantics.

### Decision

Deferred to the event-driven architecture phase.

---

# Consequences

## Positive

- declarative HTTP contracts,
- clean Spring Cloud integration,
- logical service names instead of physical URLs,
- native client-side load-balancing integration,
- Feign details remain outside the application core,
- client-specific error translation,
- easier extension with interceptors and client configuration.

## Negative

- additional framework dependency,
- generated proxy behavior adds indirection,
- synchronous temporal coupling still exists,
- remote failures remain part of the use case,
- Feign-specific concepts must be understood and maintained.

---

# Important Architectural Constraint

OpenFeign must remain an infrastructure concern.

The following dependency direction should be preserved:

```text
Application
→ CarLookupPort

Adapter
→ CatalogFeignClient
```

The following should be avoided:

```text
Application
→ Feign
```

Infrastructure technology must not define application semantics.

---

# Reconsideration Criteria

This decision may be reconsidered if:

- the platform standardizes Spring HTTP Service Clients,
- the system moves primarily to Kubernetes-native service networking,
- a service mesh moves discovery/load balancing out of the application,
- communication changes from REST to gRPC,
- the Catalog dependency is replaced by an asynchronous local projection,
- another HTTP client becomes the organization-wide standard.

The application-facing contract:

```text
CarLookupPort
```

should remain stable whenever possible.

---

# Supersedes

This decision supersedes:

```text
ADR-002 — Use Spring HTTP Interfaces for Synchronous Service Communication
```

The original ADR remains in the repository to preserve the architectural decision history.