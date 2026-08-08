# ADR-001 — Database per Service

## Status

Accepted.

---

## Date

2026-08-08

---

# Context

GarageBid initially used a single PostgreSQL instance.

The first topology was:

```text
PostgreSQL
└── garagebid
    ├── catalog schema
    └── auction schema
```

Each service used a separate schema.

This approach provided logical separation while keeping the local development environment simple.

The design also ensured that each service could later move to an independent database by changing configuration rather than business code.

As the project reached synchronous service-to-service communication, stronger physical isolation became more valuable for learning.

The goal was to make accidental cross-service data access impossible and expose the real consistency trade-offs of microservice data ownership.

---

# Decision

GarageBid will use an independent PostgreSQL database for each service.

Current topology:

```text
catalog-service
    ↓
catalog-db
    database: catalog
    schema: public

auction-service
    ↓
auction-db
    database: auction
    schema: public
```

Local development currently runs each PostgreSQL database in a separate Docker container.

Ports:

```text
catalog-db → 5433
auction-db → 5434
```

A service must not directly access another service's database.

---

# Data Ownership Rule

The owning service is the only component allowed to directly read or write its database.

Therefore:

```text
catalog-service
→ catalog-db
```

is valid.

```text
auction-service
→ auction-db
```

is valid.

The following is forbidden:

```text
auction-service
→ catalog-db
```

If auction-service needs catalog-owned information, it must communicate through an explicit service contract.

Currently:

```text
auction-service
→ HTTP
→ catalog-service
```

---

# Why Independent Databases?

The primary motivation is not simply infrastructure isolation.

The primary motivation is:

```text
explicit data ownership
```

Independent databases make several invalid architectural shortcuts impossible.

For example:

```text
cross-service SQL JOIN
```

cannot be used to bypass service boundaries.

Similarly:

```text
cross-service foreign keys
```

cannot be created.

This forces distributed consistency problems to be handled explicitly.

---

# Alternatives Considered

## Option 1 — Shared Database and Shared Schema

Example:

```text
garagebid
└── public
    ├── cars
    └── auctions
```

### Advantages

- simplest infrastructure,
- easy joins,
- easy foreign keys,
- simple local transactions.

### Disadvantages

- weak service ownership,
- services can modify each other's data,
- schema changes couple deployments,
- encourages distributed-monolith behavior,
- cross-service SQL bypasses service contracts.

### Decision

Rejected.

---

## Option 2 — Shared PostgreSQL Database with Schema per Service

Example:

```text
garagebid
├── catalog
└── auction
```

### Advantages

- logical separation,
- low infrastructure cost,
- convenient local development,
- simple transition from a monolith.

### Disadvantages

- physical infrastructure is still shared,
- accidental cross-schema access remains technically possible,
- database lifecycle remains coupled,
- does not fully expose microservice data-isolation trade-offs.

### Decision

Used initially, then replaced.

This was intentionally an intermediate learning step.

---

## Option 3 — Separate Database per Service

Example:

```text
catalog-service
→ catalog-db

auction-service
→ auction-db
```

### Advantages

- explicit ownership,
- stronger isolation,
- independent migrations,
- prevents cross-service joins,
- prevents cross-service foreign keys,
- independent lifecycle,
- closer to real microservice boundaries.

### Disadvantages

- more infrastructure,
- no distributed ACID transaction,
- consistency problems become explicit,
- cross-service reads require communication,
- local environment is slightly heavier.

### Decision

Accepted.

---

# Why Use `public` Schema?

Once each service owns an independent database, an additional service-named schema is unnecessary for the current project.

For example:

```text
catalog database
└── catalog schema
    └── cars
```

would duplicate an ownership boundary already expressed by the database itself.

GarageBid therefore uses:

```text
catalog database
└── public.cars
```

and:

```text
auction database
└── public.auctions
```

This keeps configuration simpler while preserving isolation.

---

# Consequences

## Positive

### Clear ownership

It is obvious which service owns each table.

### No cross-service SQL

Services cannot depend on another service's tables through joins.

### Independent migrations

Each service owns its own Flyway history and schema lifecycle.

### Infrastructure changes remain configuration-driven

Moving from shared-schema isolation to separate databases did not require changing business logic.

### Distributed-system problems become visible

The architecture now naturally exposes:

- eventual consistency,
- distributed validation,
- failure propagation,
- temporal coupling.

These are important learning goals of GarageBid.

---

## Negative

### No cross-service foreign keys

For example:

```text
auction.car_id
```

cannot have a database foreign key to:

```text
catalog.cars.id
```

### No shared ACID transaction

Changes across two service databases cannot be committed using a normal local database transaction.

### More infrastructure

Developers must run multiple PostgreSQL instances or databases.

### Cross-service information requires communication

Auction-service must ask catalog-service for catalog-owned information.

This introduces:

- latency,
- network failures,
- availability dependencies.

---

# Distributed Consistency Consequence

The following race is possible:

```text
T1 auction asks whether a car exists
T2 catalog responds yes
T3 car is deleted
T4 auction persists car_id
```

The synchronous existence check is not equivalent to a foreign key.

It provides only a point-in-time validation.

Future phases will explore patterns for distributed consistency, including:

- domain events,
- transactional outbox,
- Saga,
- CQRS,
- eventual consistency.

---

# Operational Note

Database per service does not necessarily mean:

> Every service must always run a dedicated physical PostgreSQL server.

In a production environment, services may use different databases on the same managed PostgreSQL infrastructure while maintaining ownership boundaries.

The important architectural property is independent data ownership.

GarageBid uses separate containers locally because the physical boundary makes the learning objective explicit.

---

# Reconsideration Criteria

This decision may be reconsidered if:

- infrastructure cost becomes unreasonable,
- the project moves to a managed database platform,
- operational requirements favor database consolidation,
- service boundaries change significantly.

Any alternative must preserve the core ownership rule:

> A service must not directly access another service's data model.