# Phase 01 — Catalog Service

## Status

Completed.

---

# Goal

The first phase establishes the basic development environment and introduces a conventional Spring Boot service using layered architecture.

The catalog service owns exotic vehicle listings and acts as the simplest bounded context in GarageBid.

The purpose of this phase is not to demonstrate advanced architecture.

The purpose is to establish a baseline that can later be compared with the hexagonal auction service.

---

# Responsibilities

The catalog service currently supports:

- listing cars,
- retrieving a car by ID,
- creating cars,
- validating incoming HTTP requests,
- persisting car data,
- exposing API documentation,
- managing its schema through Flyway.

---

# Architecture

The service uses a traditional layered architecture.

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

Package structure:

```text
com.garagebid.catalog
├── domain
├── repository
├── service
└── web
    ├── dto
    └── mapper
```

---

# HTTP API

Base path:

```text
/api/v1/cars
```

Endpoints:

```http
GET  /api/v1/cars
GET  /api/v1/cars/{id}
POST /api/v1/cars
```

---

# DTO Boundary

The service does not directly expose JPA entities through the HTTP API.

Incoming requests use:

```text
CreateCarRequest
```

Responses use:

```text
CarResponse
```

The conversion is performed by:

```text
CarMapper
```

Flow:

```text
HTTP JSON
   ↓
CreateCarRequest
   ↓
CarMapper
   ↓
Car entity
```

and:

```text
Car entity
   ↓
CarMapper
   ↓
CarResponse
   ↓
HTTP JSON
```

The mapper is intentionally handwritten.

The purpose is to make the boundary between persistence/domain representation and HTTP representation visible before introducing mapping frameworks.

---

# Persistence

The service uses:

- Spring Data JPA
- Hibernate
- PostgreSQL
- Flyway

The repository abstraction is:

```text
CarRepository
```

which extends Spring Data JPA's:

```text
JpaRepository<Car, UUID>
```

The catalog service intentionally keeps the domain and persistence representation together.

`Car` is also a JPA entity.

This is a deliberate contrast with Phase 02, where the auction domain and persistence models are separated.

---

# Database Migrations

The database schema is owned by Flyway.

Migration files:

```text
V1__init_catalog.sql
V2__seed_cars.sql
```

`V1` creates the cars table.

`V2` inserts twelve exotic vehicles used as development data.

Hibernate configuration uses:

```text
ddl-auto: validate
```

This means Hibernate does not create or update the schema.

Instead:

```text
Flyway
→ creates/evolves schema

Hibernate
→ validates mappings against schema
```

This makes schema evolution explicit and version-controlled.

---

# Seed Data

The development environment includes twelve exotic vehicles such as:

- Ferrari 488 Pista
- Lamborghini Huracan STO
- McLaren 720S
- Porsche 911 GT3 RS
- Bugatti Chiron
- Pagani Huayra
- Koenigsegg Regera
- Ford GT

UUID values are generated during migration.

Because IDs are generated dynamically, recreating the database creates new car IDs.

Clients should retrieve current IDs through:

```http
GET /api/v1/cars
```

instead of depending on previously observed IDs.

---

# Validation

Incoming create requests use Jakarta Bean Validation.

Examples include:

- non-empty manufacturer and model,
- supported model-year range,
- non-negative mileage,
- non-negative price,
- required vehicle condition.

The HTTP layer owns transport-level validation.

---

# Error Handling

Missing cars produce:

```text
CarNotFoundException
```

The web layer translates that exception into:

```text
404 Not Found
```

using Spring's:

```text
ProblemDetail
```

The service does not expose raw persistence exceptions as its normal HTTP contract.

---

# OpenAPI

The service exposes Swagger UI and OpenAPI documentation.

Swagger UI:

```text
http://localhost:8081/swagger-ui/index.html
```

OpenAPI JSON:

```text
http://localhost:8081/v3/api-docs
```

The project uses code-first API documentation.

The OpenAPI document is derived from the actual controller implementation.

---

# Database Ownership Evolution

The project originally started with one PostgreSQL instance and separate schemas:

```text
garagebid
├── catalog
└── auction
```

This was useful during the earliest development stage because it provided logical isolation with minimal infrastructure.

During Phase 02, GarageBid moved to independent databases:

```text
catalog-service
    ↓
catalog-db

auction-service
    ↓
auction-db
```

The catalog service now connects to its own database:

```text
database: catalog
schema: public
```

This change required infrastructure configuration changes but did not require business-code changes.

That was an important demonstration of configuration-driven infrastructure.

---

# Lessons Learned

## 1. Layered architecture is not inherently bad

For simple CRUD-oriented services, the traditional:

```text
Controller
→ Service
→ Repository
```

structure is understandable and productive.

Architecture should be chosen based on complexity rather than fashion.

---

## 2. HTTP models should not automatically become persistence models

Even though `Car` is a JPA entity, the API uses dedicated request and response DTOs.

This prevents the HTTP contract from being accidentally coupled to database implementation details.

---

## 3. Flyway should own the schema

Using:

```text
ddl-auto=create
```

or:

```text
ddl-auto=update
```

would allow Hibernate to modify the schema implicitly.

GarageBid instead uses:

```text
ddl-auto=validate
```

to detect mismatches.

---

## 4. Configuration errors can look like persistence errors

During development, several important configuration lessons were observed:

- Flyway and Hibernate must target the same schema.
- Flyway history represents migration state, not the actual correctness of manually modified tables.
- Docker initialization scripts only run on empty volumes.
- Old Docker volumes can survive Compose renames.
- `spring.jpa.properties` is passed through to Hibernate and incorrect nesting can be silently ignored.

These failures were intentionally treated as learning opportunities.

---

## 5. Database ownership is more important than database technology

The important principle is not merely running multiple PostgreSQL containers.

The important principle is:

> Only the owning service accesses its data directly.

Other services must communicate through explicit contracts.

---

# Phase Outcome

At the end of Phase 01, GarageBid had:

- a working Spring Boot service,
- REST endpoints,
- DTO boundaries,
- validation,
- JPA persistence,
- Flyway migrations,
- seed data,
- Problem Details,
- health endpoints,
- OpenAPI documentation,
- an explicit layered architecture.

This service later became the comparison baseline for Phase 02's hexagonal architecture.

---

# Next Phase

Phase 02 introduces the auction bounded context using:

- Hexagonal Architecture
- Ports and Adapters
- Rich Domain Model
- Aggregate Root
- Value Objects
- Dependency Inversion
- Database per Service
- Synchronous service-to-service communication