# Phase 03 — Platform Layer

## Status

Completed.

---

# Goal

Phase 03 introduces platform-level capabilities that become necessary once multiple independently running services need to communicate reliably.

The main goal is to remove hard-coded service locations and introduce infrastructure responsible for:

- service registration,
- service discovery,
- client-side load balancing,
- API routing,
- centralized configuration.

Before this phase, the auction service knew the physical location of the catalog service:

```text
http://localhost:8081
```

After this phase, services communicate using logical service identities such as:

```text
catalog-service
```

The physical location of service instances becomes a platform concern rather than an application concern.

---

# Architecture

The system now contains three Spring Boot applications:

```text
gateway
catalog-service
auction-service
```

and Consul as platform infrastructure.

High-level topology:

```text
                         Client
                           |
                           v
                    Gateway :8080
                     /         \
                    /           \
                   v             v
         catalog-service       auction
          /         \             |
      :8081         :8091         |
                                   |
                                   | Feign
                                   v
                            catalog-service
                             /          \
                         :8081          :8091


                     +----------------+
                     |     Consul     |
                     |     :8500      |
                     +----------------+
                      /       |       \
                     /        |        \
              registration  discovery  config
```

Consul currently provides two independent platform capabilities:

```text
Service Registry / Discovery
Centralized Configuration
```

---

# Service Registry

Each application registers itself with Consul.

Registered services include:

```text
catalog-service
auction
gateway
```

Consul stores information such as:

- service name,
- instance ID,
- host,
- port,
- health state.

Example:

```text
catalog-service
├── catalog-service-8081
└── catalog-service-8091
```

This represents:

```text
1 logical service
2 running instances
```

The service name is stable while physical instances may change.

---

# Service Registration

Applications register through Spring Cloud Consul.

For example:

```yaml
spring:
  application:
    name: catalog-service
```

defines the logical service identity.

The application does not manually call the Consul HTTP API.

Spring Cloud handles registration through auto-configuration.

Conceptually:

```text
Catalog starts
    ↓
Spring Cloud Consul
    ↓
register catalog-service
    ↓
Consul service catalog
```

---

# Health Checks

Service registration alone is not enough.

A registry must also know whether an instance is healthy.

Consul periodically checks:

```text
/actuator/health
```

and marks instances as:

```text
passing
critical
```

Discovery is configured to prefer passing instances.

This means an instance may still exist in the registry while no longer being considered suitable for traffic.

---

# Local Development Networking

GarageBid currently uses a hybrid local environment:

```text
Java applications
→ Windows host

PostgreSQL + Consul
→ Docker
```

This creates two different network perspectives.

For Java applications:

```text
127.0.0.1
```

can be used to communicate with other locally running Java applications.

For Consul running inside Docker:

```text
127.0.0.1
```

would refer to the Consul container itself.

Therefore local development uses separate service and health-check addresses.

Example:

```text
Service address:
127.0.0.1:8081

Health-check address:
host.docker.internal:8081
```

This is a development-environment workaround and not a production topology.

The important lesson is:

> A service registry must advertise an address that service consumers can actually reach.

---

# Service Discovery

Before Phase 03, auction-service called catalog-service through:

```text
http://localhost:8081
```

This creates location coupling.

The caller knows:

```text
host
port
```

After introducing discovery, the caller only knows:

```text
catalog-service
```

Conceptually:

```text
Auction
   ↓
"Find catalog-service"
   ↓
DiscoveryClient
   ↓
Consul
   ↓
healthy catalog instances
```

Example result:

```text
catalog-service
├── 127.0.0.1:8081
└── 127.0.0.1:8091
```

The application no longer owns the physical topology.

---

# Service Registry vs Service Discovery

These concepts are related but different.

## Service Registry

Stores the current service-instance catalog.

Example:

```text
catalog-service
→ instance A
→ instance B
```

## Service Discovery

Uses the registry to resolve a logical service identity into available instances.

Example:

```text
catalog-service

        ↓ discovery

127.0.0.1:8081
127.0.0.1:8091
```

The registry stores information.

Discovery consumes that information.

---

# Client-Side Load Balancing

Once multiple service instances exist, discovery alone is not enough.

The caller must choose one instance.

GarageBid uses:

```text
Spring Cloud LoadBalancer
```

for client-side load balancing.

Conceptually:

```text
Auction
   ↓
catalog-service
   ↓
LoadBalancer
   ↓
Consul discovery result
   ↓
[8081, 8091]
   ↓
select one instance
```

The business request then goes directly from auction-service to the selected catalog-service instance.

Consul does not proxy the business request.

---

# Discovery vs Load Balancing

The responsibilities are different.

```text
Consul
→ Which instances exist?

LoadBalancer
→ Which instance should receive this request?
```

For example:

```text
catalog-service
├── instance A
├── instance B
└── instance C
```

Discovery returns:

```text
[A, B, C]
```

The load balancer chooses:

```text
B
```

---

# Multi-Instance Catalog

Phase 03 runs two copies of the catalog application:

```text
catalog-service :8081
catalog-service :8091
```

Both instances:

- use the same application code,
- register with the same logical service name,
- currently use the same catalog database.

Consul therefore sees:

```text
catalog-service
├── instance :8081
└── instance :8091
```

This provides the first practical example of horizontal application scaling.

---

# Instance-Level Failure Experiment

The following behavior was verified.

With two healthy catalog instances:

```text
8081 UP
8091 UP

auction request
→ success
```

With one instance unavailable:

```text
8081 DOWN
8091 UP

auction request
→ success
```

With no healthy catalog instance:

```text
8081 DOWN
8091 DOWN

auction request
→ 503 Service Unavailable
```

Result:

| Healthy Catalog Instances | Auction Result |
|---:|---|
| 2 | Success |
| 1 | Success |
| 0 | 503 Service Unavailable |

This demonstrates that multiple application instances improve availability for instance-level failures.

It does not solve every availability problem.

Both catalog instances currently depend on the same PostgreSQL database.

Therefore:

```text
application replication
!=
dependency replication
```

If the shared database fails, both catalog instances may become unavailable.

---

# OpenFeign

During Phase 02, synchronous catalog communication used:

```text
Spring HTTP Interface
@HttpExchange
RestClient
```

After Spring Cloud became a platform dependency in Phase 03, the catalog integration was migrated to:

```text
Spring Cloud OpenFeign
```

The remote HTTP contract is now represented by:

```text
CatalogFeignClient
```

Conceptually:

```java
@FeignClient(name = "catalog-service")
```

No physical URL is configured.

The Feign client refers only to the logical service identity.

---

# Feign + LoadBalancer + Consul

The complete client-side call flow is:

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
healthy catalog-service instance
      ↓
HTTP request
```

Each component has a different responsibility.

```text
CarLookupPort
→ application dependency contract

CatalogHttpAdapter
→ integration semantic translation

CatalogFeignClient
→ remote HTTP contract

Feign
→ declarative HTTP client

LoadBalancer
→ instance selection

Consul
→ service registry/discovery
```

---

# Why Catalog Did Not Need Feign Changes

Feign is a client-side technology.

The catalog service remains a normal HTTP server.

It does not care whether its caller uses:

- OpenFeign,
- RestClient,
- WebClient,
- curl,
- Postman,
- Go HTTP client.

The integration boundary remains standard HTTP.

This preserves service implementation independence.

---

# Remote Error Translation

The auction application must not depend directly on Feign exceptions.

Remote behavior is translated inside the outbound integration.

Examples:

```text
Catalog HTTP 404
→ CatalogCarNotFoundException
→ CarLookupPort returns false

Catalog HTTP 5xx
→ CatalogUnavailableException

No healthy service instance
→ load-balanced Feign receives 503
→ CatalogUnavailableException

Transport failure
→ RetryableException
→ CatalogUnavailableException
```

The application continues to reason in its own language:

```text
car exists
car does not exist
catalog unavailable
```

instead of:

```text
FeignException
HTTP status
LoadBalancer exception
```

---

# ErrorDecoder

Feign's `ErrorDecoder` is used to translate HTTP failure responses.

Conceptually:

```text
HTTP response
    ↓
CatalogFeignErrorDecoder
    ↓
integration-specific exception
```

Examples:

```text
404
→ CatalogCarNotFoundException

5xx
→ CatalogUnavailableException
```

Transport failures where no HTTP response exists are handled separately.

This distinction is important:

```text
HTTP 500
→ communication succeeded, remote service returned an error

connection refused
→ HTTP communication was never established
```

---

# Retry Behavior

Although Feign may expose:

```text
RetryableException
```

GarageBid does not currently enable automatic retries.

Retry behavior is intentionally deferred to the dedicated resilience phase.

This prevents retry semantics from being introduced without first discussing:

- idempotency,
- duplicate operations,
- backoff,
- jitter,
- retry storms.

---

# API Gateway

Phase 03 introduces a dedicated gateway application.

Port:

```text
8080
```

Before the gateway, clients needed to know:

```text
catalog → :8081
auction → :8082
```

After the gateway:

```text
Client
→ :8080
```

The gateway becomes the external entry point.

---

# Gateway Routes

Current routes:

```text
/api/v1/cars/**
→ catalog-service

/api/v1/auctions/**
→ auction
```

The routes use logical service names.

Conceptually:

```yaml
uri: lb://catalog-service
```

The `lb://` scheme means:

```text
resolve this service through Spring Cloud LoadBalancer
```

The gateway therefore does not need the physical Catalog address.

---

# Gateway Request Flow

Catalog request:

```text
Client
   ↓
Gateway
   ↓
catalog-route
   ↓
lb://catalog-service
   ↓
LoadBalancer
   ↓
Consul
   ↓
Catalog instance
```

Auction request:

```text
Client
   ↓
Gateway
   ↓
auction-route
   ↓
lb://auction
   ↓
LoadBalancer
   ↓
Consul
   ↓
Auction
```

If opening an auction requires Catalog validation, a second service-to-service hop occurs:

```text
Client
   ↓
Gateway
   ↓
Auction
   ↓
Feign
   ↓
Catalog
```

---

# Gateway Responsibilities

The gateway is a platform/edge component.

Appropriate responsibilities include:

- request routing,
- authentication entry point,
- rate limiting,
- CORS,
- correlation identifiers,
- edge metrics,
- request/response filters.

Business rules should remain inside domain services.

For example:

```text
"Is this bid high enough?"
```

must not be implemented in the gateway.

---

# God Gateway Risk

Centralizing entry traffic can create a temptation to centralize business logic as well.

GarageBid intentionally avoids:

```text
Gateway
→ business orchestration
→ domain rules
→ service-specific decisions
```

The gateway should remain focused on edge concerns.

Otherwise it can become a new central coupling point.

---

# Centralized Configuration

Gateway route definitions were initially stored inside:

```text
gateway/application.yaml
```

They were later moved to:

```text
Consul KV
```

The gateway now imports configuration through:

```text
spring.config.import
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
application artifact
```

from:

```text
runtime configuration
```

---

# Consul KV Structure

The Gateway configuration is stored under:

```text
config/gateway/data
```

and interpreted as YAML.

Conceptually:

```text
Consul KV
└── config
    └── gateway
        └── data
            └── Gateway YAML configuration
```

The key is associated with:

```yaml
spring:
  application:
    name: gateway
```

---

# Bootstrap Configuration vs Centralized Configuration

Not every configuration value belongs in Consul.

The application still needs enough local configuration to locate Consul itself.

For example:

```text
application name
Consul host
Consul port
config import
```

are bootstrap concerns.

Meanwhile configuration such as Gateway routes can be centralized.

This distinction prevents a circular dependency where the application would need centralized configuration in order to discover the centralized configuration system.

---

# Fail-Fast Configuration

Gateway configuration is considered required.

Therefore Consul Config is imported without:

```text
optional:
```

The intention is:

```text
required centralized config unavailable
→ application startup should fail
```

rather than silently starting with incomplete routing configuration.

This is a deliberate fail-fast decision.

---

# ConfigWatch

Spring Cloud Consul can monitor KV configuration for runtime changes.

During this phase, the current WebFlux-based Gateway stack produced repeated `ReadTimeoutException` failures while ConfigWatch performed background polling.

Initial configuration loading worked correctly.

Dynamic runtime watching was therefore disabled:

```yaml
spring:
  cloud:
    consul:
      config:
        watch:
          enabled: false
```

Current behavior:

```text
Gateway starts
→ reads configuration from Consul

Consul configuration changes later
→ Gateway restart required
```

Dynamic configuration refresh is not a learning requirement for the current phase.

The project prefers a stable startup configuration model instead of introducing additional complexity only to support hot reload.

---

# Consul Responsibilities

Consul currently provides two distinct capabilities.

## Service Registry

Answers:

> Which services and instances are currently available?

Example:

```text
catalog-service
├── :8081
└── :8091
```

## Configuration Store

Answers:

> What runtime configuration should the Gateway use?

Example:

```text
config/gateway/data
```

These are different responsibilities even though the same infrastructure component currently provides both.

---

# What Consul Does Not Do

In the current GarageBid architecture, Consul is not a reverse proxy for business traffic.

For example:

```text
Auction
→ Consul
→ Catalog
```

is not the actual data path.

Instead:

```text
Auction
→ Consul for discovery information

Auction
→ Catalog directly for business traffic
```

Likewise:

```text
Gateway
→ Consul for discovery information

Gateway
→ selected service instance directly
```

Consul participates in service discovery, not in every business request.

---

# Control Plane vs Data Plane

Phase 03 introduces an early example of this distinction.

Control information includes:

```text
service instance locations
health state
routing configuration
```

These are provided by platform components such as Consul.

Business traffic includes:

```text
GET /api/v1/cars
POST /api/v1/auctions
```

This traffic flows directly between runtime components.

Conceptually:

```text
Control plane
→ Consul

Data plane
→ Gateway / Auction / Catalog HTTP traffic
```

This distinction becomes more important in later infrastructure and service-mesh topics.

---

# Current Runtime Topology

Local development currently runs:

```text
Docker:
├── Consul
├── catalog-db
└── auction-db

Windows / IntelliJ:
├── Gateway :8080
├── Auction :8082
├── Catalog :8081
└── Catalog :8091
```

This hybrid topology is intentionally lightweight because the local machine has limited resources.

Kubernetes was evaluated briefly but deferred to a later phase.

The current objective remains learning microservice architecture and distributed-system behavior without unnecessarily increasing local infrastructure cost.

---

# Failure Scenarios Observed

## Catalog instance failure

```text
2 healthy instances
→ success

1 healthy instance
→ success

0 healthy instances
→ 503
```

---

## No discovered Catalog instance

Feign and Spring Cloud LoadBalancer translate the lack of an available service instance into a service-unavailable failure.

Auction exposes:

```text
503 Service Unavailable
```

rather than treating the missing dependency as:

```text
car does not exist
```

---

## Gateway cannot resolve service

If the gateway cannot find a healthy instance for an `lb://` route:

```text
request
→ 503 Service Unavailable
```

---

## Incorrect advertised service address

A service may successfully register in Consul while advertising an unreachable address.

This was observed when the Windows hostname resolved to an address that locally running service consumers could not use.

Lesson:

> Successful registration does not prove reachability.

---

## Runtime ConfigWatch timeout

The centralized configuration was successfully loaded during startup, while background ConfigWatch polling produced timeout failures.

Dynamic watching was disabled because runtime hot refresh is not required by the current architecture.

---

# Key Lessons

## 1. Service identity should be stable

Applications should depend on:

```text
catalog-service
```

instead of:

```text
localhost:8081
```

Physical topology should be managed by the platform.

---

## 2. Discovery and load balancing solve different problems

Discovery answers:

```text
Which instances exist?
```

Load balancing answers:

```text
Which instance should receive this request?
```

---

## 3. Registration does not guarantee reachability

An instance can exist in a registry while advertising the wrong network address.

Service discovery requires correct network topology as well as correct registration.

---

## 4. Multiple instances improve only certain failure modes

Two Catalog application instances protect against one Catalog process failing.

They do not protect against:

```text
shared database failure
shared host failure
shared network failure
```

High availability must be evaluated across the entire dependency graph.

---

## 5. Infrastructure changes may change failure surfaces

Before service discovery, Catalog failure appeared primarily as transport-level connection errors.

After LoadBalancer integration, missing instances can fail before an HTTP connection is attempted.

Infrastructure-specific failures must still be translated into stable application semantics.

---

## 6. Hexagonal boundaries protect the application core

The catalog HTTP technology changed from:

```text
@HttpExchange + RestClient
```

to:

```text
OpenFeign
```

without requiring changes to:

```text
Auction domain
AuctionService
CarLookupPort
```

Only the outbound infrastructure adapter changed.

This is a practical demonstration of dependency inversion.

---

## 7. Gateway is an edge component

The gateway centralizes external routing without centralizing domain behavior.

It should simplify access to services, not become a replacement for service boundaries.

---

## 8. Centralized configuration has operational trade-offs

Moving configuration out of application artifacts provides centralized management.

It also introduces new concerns:

- availability of the configuration store,
- versioning,
- reproducibility,
- change auditing,
- refresh behavior.

Centralized configuration is not automatically simpler.

---

# Phase Acceptance Criteria

Phase 03 is considered complete when the following behaviors work.

```text
Consul
→ running

catalog-service
→ registered

auction
→ registered

gateway
→ registered

two Catalog instances
→ visible in Consul

Gateway GET /api/v1/cars
→ 200

Gateway POST /api/v1/auctions
→ 201

one Catalog instance unavailable
→ requests still succeed

all Catalog instances unavailable
→ 503

Auction → Catalog
→ OpenFeign

Service selection
→ Spring Cloud LoadBalancer

Service discovery
→ Consul

Gateway routes
→ loaded from Consul KV

Gateway actuator routes
→ catalog-route
→ auction-route

application tests
→ green
```

---

# Phase Outcome

At the end of Phase 03, GarageBid has introduced:

- Service Registry
- Service Registration
- Service Discovery
- Health Checks
- Client-Side Load Balancing
- Horizontal Application Instances
- OpenFeign
- Feign ErrorDecoder
- API Gateway
- Discovery-Based Gateway Routing
- Centralized Configuration
- Fail-Fast Configuration
- Logical Service Identity
- Early Control Plane / Data Plane concepts

The architecture has evolved from:

```text
service
→ hard-coded service URL
```

to:

```text
service
→ logical service identity
→ discovery
→ load balancing
→ healthy instance
```

External clients have also evolved from:

```text
client
→ individual service ports
```

to:

```text
client
→ gateway
→ discovered services
```

---

# Next Phase

Phase 04 introduces event-driven communication.

Planned topics:

- Apache Kafka
- Domain Events
- Event-Driven Architecture
- Producer / Consumer
- Consumer Groups
- Partitions
- Ordering
- At-Least-Once Delivery
- Idempotency
- Dual-Write Problem
- Transactional Outbox
- Choreography
- Eventual Consistency

The main architectural question changes from:

> How does one service synchronously find and call another service?

to:

> How can services communicate without requiring both services to be available at the same time?