# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Development Commands

The back-end is a Maven multi-module project rooted at `Implementation/back-end/`.

```bash
# Build all modules (skip tests for speed)
mvn -f Implementation/back-end/pom.xml clean install -DskipTests

# Build a single service (and its dependencies)
mvn -f Implementation/back-end/pom.xml -pl user-service -am clean install

# Run tests (none written yet — test dirs exist but are empty)
mvn -f Implementation/back-end/pom.xml test

# Before running any service, build shared modules first:
mvn -f Implementation/back-end/pom.xml -pl ia-common,ia-api -am clean install -DskipTests

# Run a service (pattern: mvn -f <service-dir> -DskipTests spring-boot:run)
mvn -f Implementation/back-end/user-service -DskipTests spring-boot:run          # port configured per-service
mvn -f Implementation/back-end/authorization-service -DskipTests spring-boot:run  # port 9000
mvn -f Implementation/back-end/gate-service -DskipTests spring-boot:run           # port 8080
```

### Runtime prerequisites

Running services locally requires these to be up:

| Dependency | Config location                                      | Notes                                                     |
| ---------- | ---------------------------------------------------- | --------------------------------------------------------- |
| Nacos      | `application.yml` — `spring.cloud.nacos.server-addr` | Default: `192.168.10.128:8848`                            |
| Redis      | `application.yml` — `spring.data.redis.host`         | Used for token caching, rate limiting (Lua scripts)       |
| MySQL      | `application.yml` — `spring.datasource.url`          | Schema: `Implementation/back-end/database/Initialize.sql` |

There are currently no Docker Compose or infra-as-code files. Each developer sets these up manually.

## Architecture Overview

IceAmericanoMall is a B2B2C e-commerce platform (similar to Taobao/JD) built as a distributed microservices system. The project is in early MVP phase — `user-service` and `authorization-service` are the most implemented services; other services are scaffolded shells with empty `main` methods.

### Technical Stack

| Layer               | Technology                                                                          |
| ------------------- | ----------------------------------------------------------------------------------- |
| Runtime             | Java 21, Spring Boot 3.5.4                                                          |
| Cloud               | Spring Cloud 2025.0.1, Spring Cloud Alibaba 2025.0.0.0 (Nacos for discovery/config) |
| Persistence         | MySQL 9.3.0 via MyBatis-Plus 3.5.11                                                 |
| Cache               | Redis (Lua scripts for rate limiting, token caching)                                |
| Auth                | Spring Security OAuth2 Authorization Server, JWT (HS256 + RS256 via JJWT 0.13.0)    |
| Inter-service calls | OpenFeign + Spring Cloud LoadBalancer                                               |
| API docs            | Knife4j 4.5.0 (OpenAPI 3)                                                           |
| Object mapping      | MapStruct 1.5.5 + Lombok 1.18.42                                                    |
| Utilities           | Hutool 5.8.43                                                                       |

### Service Map

```
gate-service (API Gateway — Spring Cloud Gateway + Nacos)
  ├── authorization-service (OAuth2 auth server, login/register, JWT issuance)  ← most complete
  ├── user-service (user CRUD, addresses, sign-in)                               ← most complete
  ├── ia-api (shared Feign client interfaces + DTOs for inter-service calls)
  ├── ia-common (shared lib: exceptions, Result wrapper, utils, config, annotations)
  ├── item-service (products/SKUs/categories)                                     ← scaffolded
  ├── cart-service (shopping cart)                                                ← scaffolded
  ├── trade-service (orders)                                                      ← scaffolded
  ├── pay-service (payment processing)                                            ← scaffolded
  ├── logistics-service (shipping)                                                ← scaffolded
  └── search-service (ElasticSearch search)                                      ← scaffolded
```

The gate-service currently only has auth-service and user-service routes wired in `application.yml`; other service routes are commented out.

`ia-common` is the shared kernel — it defines `Result<T>`, `ErrorCode` enum, exception hierarchy (`CommonException`, `BadRequestException`, `BizException`, `DBException`, `ForbiddenException`, `UnauthorizedException`), `@RateLimit` annotation + `RateLimitAspect`, `GlobalExceptionHandler`, Lua scripts (`rate_limit.lua`, `check_limit.lua`, `login_rate_limit.lua`, `del_redisKey.lua`), SMS utilities, Geetest captcha integration, MyBatis-Plus config, Json config.

`ia-api` contains Feign client interfaces (e.g., `UserClient`, `SkuClient`), shared DTOs, and fallback implementations so services can call each other without duplicating contract definitions.

**Package naming note**: `ia-common` uses the base package `org.noLazy.common`, while all other services use `org.icedAmericanoMall`. This inconsistency exists because `ia-common` was bootstrapped separately. New code should follow the service's existing convention.

### Package Structure (Alibaba Lightweight DDD)

Each service follows this package layout (minimal version for small/medium services):

```
org.icedAmericanoMall
├── controller/    — REST endpoints, param validation (no business logic, no Redis/lock ops)
├── manager/       — Flow orchestration, distributed locks, rate limiting, multi-service aggregation
├── service/       — Atomic business logic, single-domain operations
│   └── impl/      — Service implementations
├── domain/
│   ├── dto/       — req/resp/feign DTOs
│   ├── entity/    — DB-mapped DOs (MyBatis-Plus entities)
│   └── vo/        — Frontend view objects
├── mapper/        — MyBatis-Plus BaseMapper interfaces
├── convert/       — MapStruct converters (Entity ↔ DTO/VO)
├── config/        — Service-specific Spring config
├── constants/     — Enums and constants (may also be named `enums/`)
├── group/         — Validation group marker interfaces (CreateGroup, UpdateGroup)
├── aspect/        — Service-local AOP aspects
├── exception/     — Service-specific exceptions (rare; prefer ia-common exceptions)
└── util/          — Service-local utilities
```

**Key rules:**

- Controller: only param validation + call service + wrap in `Result`. Never write business logic or operate Redis/locks directly.
- `manager/` is the prescribed layer for orchestration, distributed locks, and multi-service aggregation. Currently no service has implemented it yet — rate limiting is handled by `ia-common`'s `@RateLimit` aspect.
- `domain/entity` DOs must NOT leak to controller or external services — always convert to DTO/VO.
- Inter-service calls use `ia-api` Feign interfaces; request/response use dedicated Feign DTOs.
- Internal endpoints (path contains `/internal/`) bypass `Result` wrapping on exceptions so callers receive raw HTTP errors.
- Validation groups (`CreateGroup`/`UpdateGroup`) are marker interfaces in `group/` for conditional `@Validated` checks.

### Naming Conventions

| Layer      | Pattern                                                | Examples                                                                      |
| ---------- | ------------------------------------------------------ | ----------------------------------------------------------------------------- |
| Mapper     | `select`/`insert`/`update`/`delete` + entity/condition | `selectByPhone`, `updateStatusById`                                           |
| Service    | Business verb + noun                                   | `register`, `login`, `getUserById`, `pageUsers`, `disableUser`                |
| Controller | HTTP-resource style                                    | `register` (POST), `getUser` (GET), `updateUser` (PUT), `deleteUser` (DELETE) |

### Key Patterns

- **Global exception handling**: `GlobalExceptionHandler` in `ia-common` returns `ResponseEntity<Result<Void>>` with the HTTP status from `ErrorCode`. No `@ExceptionHandler` needed in individual services.
- **Uniform response**: `Result<T>` with static factories `Result.success(data)` and `Result.error(exception)`.
- **Rate limiting**: `@RateLimit` annotation + `RateLimitAspect` in `ia-common` using Lua scripts on Redis. Gateway-level rate limiting also configured via `RequestRateLimiter` filter.
- **JWT**: authorization-service supports both HS256 and RS256; public keys exposed via JWKS endpoint at `/oauth2/jwks`.
- **Password encoding**: BCrypt via Spring Security's `PasswordEncoder`.
- **Annotation processing order**: Lombok must precede MapStruct in the compiler annotation processor chain (configured in parent POM).
- **DTO validation groups**: `CreateGroup`/`UpdateGroup` marker interfaces in `group/` package for conditional `@Validated` checks.

### What's Already Implemented

- `ia-common`: Full shared library (exceptions, Result, global handler, rate limiting, JWT utilities, SMS utilities, Geetest captcha integration, Redis Lua scripts, MyBatis-Plus config, Json config, WebConfig)
- `user-service`: User registration/login, address CRUD, sign-in, JWT handling, rate-limited endpoints
- `authorization-service`: OAuth2 authorization server, login/token issuance, JWKS endpoint, refresh token support
- `gate-service`: Spring Cloud Gateway with Nacos service discovery, JWT validation, rate limiting; only auth + user routes wired
- Database schema: Full MySQL schema in `Implementation/back-end/database/Initialize.sql`

### Hard Constraints (from `project-docs/09-constraints.md`)

These are non-negotiable rules. Violating any of them = PR rejection.

| Rule | Detail |
|------|--------|
| Transactions | `@Transactional(rollbackFor = Exception.class)` — never omit `rollbackFor` |
| Money | `INTEGER` (cents). Never `float`/`double`/`BigDecimal` for amounts |
| Method size | ≤ 50 lines. Class ≤ 300 lines. Split if exceeded |
| Internal APIs | `/internal/**` path = raw exception passthrough, NEVER wrap in `Result` |
| Soft delete | Use `@TableLogic` for recoverable data; hard delete requires review |
| Service → Feign | ❌ Forbidden. Feign calls belong in `manager/` layer |
| Exceptions | Only `ia-common` exceptions (`BadRequestException`, `BizException`, etc.). Never bare `RuntimeException` |
| Magic values | All status codes, config values, error messages → constants/enums/ErrorCode |

### MVP Scope Boundaries

**In scope**: user registration/login, address CRUD, sign-in, category (1-level), product/SKU browsing, cart, order, payment (WeChat), seller admin, admin dashboard.

**Out of scope** (do NOT build): coupons, flash sales, group buys, points (except sign-in), homepage decoration, AI customer service, after-sales tickets, reconciliation/settlement, invoices, mini-program/app, multi-level categories, ElasticSearch full-text search, complex marketing. If asked to build these, flag that they're post-MVP.

### Database

Single MySQL schema (`Initialize.sql`) with tables: `user`, `address`, `seller`, `category`, `product`, `sku`, `order`, `order_item`, `cart`, `pay_order`, `order_logistics`. Every table uses a technical `BIGINT AUTO_INCREMENT` primary key and a business unique identifier (UUID-based) where needed. Monetary values are stored as `INT` (cents). Orders store address snapshots (not foreign keys) to preserve historical data.

### Project Documentation

- `doc/methodology/` — Generic analysis methodology (11-phase pipeline: BDD→DDD→SDD→TDD→SPC), reusable for any project
- `doc/adr/` — Architecture Decision Records: why DDD layering, dual primary keys, DO/DTO/VO isolation, internal API exception passthrough were chosen
- `project-docs/` — IceAmericanoMall-specific deliverables (PRD, Architecture, Domain Model, API Spec, Data Model, Workflow, Test Strategy, Security Model)
- `project-docs/08-agents.md` — **AI coding instructions** (layer templates, naming, test patterns, forbidden patterns). Read before writing any code.
- `project-docs/09-constraints.md` — **Hard constraints & anti-patterns** (version locks, layer rules, security rules, coding rules). Check before committing.
- `drawio/` — Architecture diagrams (use case, ER, sequence, domain class, C4 container, security DFD — both `.drawio` and `.mmd` formats)
- Use the `knowledge-rag` MCP tools (e.g., `search_knowledge`) to search `project-docs/` semantically — useful for cross-document questions and spec-checking code changes
