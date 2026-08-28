# ADR-004 — Use Consul and API Gateway for Platform-Level Service Connectivity

## Status

Accepted.

---

## Date

2026-08-28

---

# Context

GarageBid initially consisted of independently running services communicating through explicitly configured URLs.

For example:

```text
auction-service
→ http://localhost:8081
→ catalog-service
```

This approach is simple while there is only one instance of each service.

It becomes increasingly problematic when:

- services have multiple instances,
- instance ports change,
- instances fail,
- clients must know several service addresses,
- configuration differs between environments.

Phase 03 introduces a platform layer to separate application behavior from runtime topology.

The platform requires capabilities for:

- service registration,
- service discovery,
- health-based instance visibility,
- client-side load balancing,
- external request routing,
- centralized configuration.

---

# Decision

GarageBid will use:

```text
HashiCorp Consul
```

for:

```text
service registration
service discovery
health information
centralized configuration
```

and:

```text
Spring Cloud Gateway
```

as the external API gateway.

Spring Cloud LoadBalancer will use discovered service instances for client-side instance selection.

The resulting platform model is:

```text
Client
   ↓
Gateway
   ↓
logical service name
   ↓
LoadBalancer
   ↓
Consul discovery
   ↓
healthy service instance
```

Internal synchronous calls use the same logical service model.

Example:

```text
Auction
   ↓
catalog-service
   ↓
LoadBalancer
   ↓
Consul
   ↓
Catalog instance
```

---

# Service Identity

Applications should depend on logical service identities rather than physical locations.

Instead of:

```text
http://localhost:8081
```

the platform uses:

```text
catalog-service
```

A logical service may have multiple physical instances.

Example:

```text
catalog-service
├── 127.0.0.1:8081
└── 127.0.0.1:8091
```

The application depends on the stable identity:

```text
catalog-service
```

while the platform manages the changing topology.

---

# Service Registration

Applications register themselves with Consul through Spring Cloud Consul.

Examples:

```text
catalog-service
auction
gateway
```

Registration contains information such as:

- service name,
- instance ID,
- host,
- port,
- health-check information.

Example:

```text
catalog-service
├── catalog-service-8081
└── catalog-service-8091
```

This distinguishes:

```text
logical service
```

from:

```text
running service instance
```

---

# Health Checks

Registration alone does not mean that an instance should receive traffic.

Consul checks application health through:

```text
/actuator/health
```

and maintains instance health state.

GarageBid configures discovery to prefer passing instances.

Conceptually:

```text
registered instance
+
passing health check
=
candidate for discovery
```

An unhealthy instance can therefore remain known to Consul without being treated as a healthy target.

---

# Service Discovery

Consumers no longer need a statically configured list of service addresses.

Conceptually:

```text
catalog-service
       ↓
DiscoveryClient
       ↓
Consul
       ↓
available catalog instances
```

The discovery mechanism answers:

> Which instances currently provide this logical service?

It does not choose the final instance for the request.

---

# Client-Side Load Balancing

Spring Cloud LoadBalancer is responsible for selecting an instance from the discovery result.

Example:

```text
Consul discovery result

catalog-service
├── instance A
└── instance B

        ↓

Spring Cloud LoadBalancer

        ↓

instance B
```

The caller then communicates directly with the selected service instance.

---

# Consul Is Not a Business-Traffic Proxy

Consul participates in control-plane operations.

The following is not the normal request path:

```text
Auction
→ Consul
→ Catalog
```

Instead:

```text
Auction
→ Consul to discover available instances

Auction
→ selected Catalog instance directly
```

The same applies to Gateway routing.

Consul provides topology information.

It does not proxy each application request.

---

# API Gateway

GarageBid introduces Spring Cloud Gateway as the external entry point.

Before the gateway:

```text
Client
├── Catalog :8081
└── Auction :8082
```

The client must know individual service addresses.

After the gateway:

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

The client only needs to know the Gateway address.

---

# Discovery-Based Gateway Routing

Gateway routes use logical service identities.

Example:

```text
lb://catalog-service
```

The `lb://` route represents:

```text
Gateway
   ↓
Spring Cloud LoadBalancer
   ↓
service discovery
   ↓
healthy service instance
```

The Gateway therefore does not require a hard-coded Catalog URL.

---

# Gateway Responsibility Boundary

The Gateway is an edge/platform component.

Appropriate responsibilities include:

- routing,
- authentication entry point,
- CORS,
- rate limiting,
- correlation IDs,
- edge-level metrics and tracing,
- request filters.

Domain behavior must remain inside the owning service.

For example:

```text
"Is this bid high enough?"
```

belongs to:

```text
auction-service
```

not:

```text
gateway
```

GarageBid deliberately avoids turning the Gateway into a business orchestration layer.

---

# Centralized Configuration

Gateway route configuration was initially stored inside:

```text
gateway/application.yaml
```

GarageBid moves those route definitions to:

```text
Consul KV
```

under:

```text
config/gateway/data
```

The Gateway imports configuration using Spring's external configuration mechanism.

Conceptually:

```text
Gateway starts
   ↓
Consul KV
   ↓
config/gateway/data
   ↓
Spring Environment
   ↓
Gateway route definitions
```

This separates:

```text
application artifact
```

from:

```text
runtime configuration
```

---

# Bootstrap Configuration

Not all configuration can be moved into the centralized configuration store.

The application must first know how to reach that store.

Therefore configuration such as:

```text
spring.application.name
Consul host
Consul port
spring.config.import
```

remains local bootstrap configuration.

Configuration such as:

```text
Gateway route definitions
```

can be stored centrally.

This avoids a circular dependency where the application would require Consul configuration in order to discover Consul itself.

---

# Configuration Availability

Gateway routing configuration is considered required configuration.

GarageBid therefore uses fail-fast startup semantics.

Conceptually:

```text
required Consul configuration available
→ Gateway starts

required Consul configuration unavailable
→ Gateway startup fails
```

Starting successfully with missing routes would produce an application that is technically running but unable to perform its primary responsibility.

Failing early is preferable.

---

# Runtime Configuration Refresh

Spring Cloud Consul supports watching KV configuration for changes.

In the current Gateway stack, background ConfigWatch polling produced repeated timeout failures while initial configuration loading remained successful.

GarageBid therefore disables runtime ConfigWatch.

Current behavior is:

```text
Gateway startup
→ load routes from Consul

Consul KV changes
→ Gateway restart required
```

Dynamic hot refresh is not currently an architectural requirement.

The project prefers predictable startup behavior over additional complexity for runtime refresh.

---

# Local Development Networking

The current local topology is hybrid:

```text
Windows host
├── Gateway
├── Auction
├── Catalog :8081
└── Catalog :8091

Docker
├── Consul
├── catalog-db
└── auction-db
```

This requires different addresses depending on which component is performing the network request.

For locally running Java applications:

```text
127.0.0.1
```

is a valid service address.

For Consul running inside Docker:

```text
127.0.0.1
```

would refer to the Consul container itself.

Therefore health checks use:

```text
host.docker.internal
```

while service consumers receive:

```text
127.0.0.1
```

as the advertised application address.

This is explicitly a local-development workaround.

It is not intended as a production networking model.

---

# Multiple Service Instances

Phase 03 verifies discovery and load balancing using two Catalog instances:

```text
catalog-service :8081
catalog-service :8091
```

Both register under:

```text
catalog-service
```

Verified behavior:

```text
2 healthy instances
→ requests succeed

1 healthy instance
→ requests succeed

0 healthy instances
→ requests fail with 503
```

This demonstrates instance-level availability.

It does not provide complete high availability because both Catalog instances currently depend on shared infrastructure such as:

```text
catalog-db
```

Application replication and dependency replication are separate concerns.

---

# Alternatives Considered

## Hard-Coded Service URLs

Example:

```text
clients.catalog.base-url=http://localhost:8081
```

### Advantages

- simple,
- minimal infrastructure,
- easy during early development.

### Disadvantages

- physical topology leaks into configuration,
- awkward with multiple instances,
- no automatic failed-instance removal,
- environment-specific addresses must be managed manually.

### Decision

Rejected as the primary Phase 03 communication model.

It was appropriate for the earlier learning phase but does not satisfy the platform requirements.

---

## Eureka

Eureka is a service registry commonly associated with Spring Cloud systems.

### Advantages

- strong Spring ecosystem history,
- familiar service-discovery model,
- client-side discovery support.

### Disadvantages

- another dedicated infrastructure component,
- does not provide the same KV configuration capability used in this phase.

### Decision

Not selected.

Consul allows GarageBid to explore both service discovery and centralized KV configuration with one platform component.

---

## Spring Cloud Config Server

A dedicated Config Server could provide centralized configuration.

### Advantages

- purpose-built Spring configuration solution,
- strong Git-backed configuration model,
- configuration history can integrate naturally with version control.

### Disadvantages

- introduces another runtime service,
- Consul is already required for discovery,
- additional infrastructure is unnecessary for the current learning scope.

### Decision

Not selected for Phase 03.

The version-control advantages remain relevant and may be reconsidered later.

---

## Direct Client-to-Service Access

Clients could continue calling each service directly.

### Advantages

- fewer infrastructure components,
- simple request path.

### Disadvantages

- clients must know internal service topology,
- cross-cutting edge concerns become duplicated,
- internal services become directly exposed.

### Decision

Rejected for the target architecture.

Gateway becomes the intended external entry point.

---

## Kubernetes-Native Service Discovery

Kubernetes Services and DNS can provide service discovery and platform-level load balancing.

### Advantages

- discovery is provided by the deployment platform,
- services can communicate through stable DNS names,
- application-level service registries may become unnecessary.

### Disadvantages in Current Context

- significantly higher local CPU and memory usage,
- larger operational learning surface,
- Kubernetes is not yet required for the current microservice phase.

### Decision

Deferred.

Kubernetes remains part of the deployment roadmap and may replace parts of the current discovery architecture in a later phase.

---

## Service Mesh

A service mesh could move capabilities such as service discovery, traffic management, retries, and telemetry further into the infrastructure layer.

### Decision

Deferred.

The project should first understand these concerns at the application/platform level before introducing a service mesh abstraction.

---

# Consequences

## Positive

- services use logical identities instead of hard-coded locations,
- multiple instances can register under one service name,
- unhealthy instances can be excluded from normal discovery,
- client-side load balancing becomes possible,
- clients have a single external entry point,
- Gateway routes can be managed outside the application artifact,
- service topology becomes a platform concern,
- application code is less coupled to runtime instance locations.

## Negative

- Consul becomes additional infrastructure to operate,
- availability of the registry/configuration system matters,
- networking configuration is more complex,
- centralized configuration introduces versioning and auditing concerns,
- the Gateway becomes an important availability dependency,
- debugging now requires understanding discovery and load-balancing layers.

---

# Architectural Constraints

GarageBid should preserve the following boundaries:

```text
Consul
→ platform metadata and configuration

Gateway
→ edge concerns

LoadBalancer
→ instance selection

Domain services
→ business behavior
```

The following should be avoided:

```text
Consul
→ business logic

Gateway
→ auction rules

Gateway
→ domain orchestration

application core
→ Consul-specific APIs
```

Platform technology must remain outside the business core whenever possible.

---

# Reconsideration Criteria

This decision should be reconsidered if:

- GarageBid moves to Kubernetes-native service discovery,
- a service mesh becomes responsible for service connectivity,
- Consul creates unacceptable operational overhead,
- centralized configuration requires Git-backed versioning and auditing,
- Gateway becomes a scaling or availability bottleneck,
- deployment infrastructure provides equivalent capabilities more naturally.

Logical service identity should remain an architectural concept even if the underlying platform implementation changes.