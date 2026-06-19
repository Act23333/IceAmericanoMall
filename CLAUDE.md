# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Before Writing Any Code (Mandatory)

1. **Identify task type** → read the corresponding doc (see table below)
2. **Confirm API spec exists** — if not, define it first (SDD principle)
3. **Write BDD scenario** (Given-When-Then) from PRD §5 or derive one
4. **Write failing test** → minimal implementation → refactor (TDD cycle)
5. **Check `09-constraints.md` anti-patterns** — before committing
6. **Self-review** against `10-review.md` checklist

| Task type | Must-read docs |
|---|---|
| New microservice | 02-Architecture, 04-Data-Model, 06-Workflow §3 |
| API endpoint | 05-API-Specification, 01-PRD (BDD scenarios) |
| Data access | 04-Data-Model, 03-Domain-Model |
| Tests | 11-Test-Strategy, 05-API-Specification (BDD contract) |
| Refactoring | 02-Architecture §4 (layering), 09-Constraints |
| New entity | 03-Domain-Model → 04-Data-Model |
| Bug fix | 10-Review, 09-Constraints (anti-patterns) |

## Build & Development Commands

The back-end is a Maven multi-module project rooted at `Implementation/back-end/`.

```bash
# Build all modules (skip tests for speed)
mvn -f Implementation/back-end/pom.xml clean install -DskipTests

# Build a single service (and its dependencies)
mvn -f Implementation/back-end/pom.xml -pl user-service -am clean install

# Run tests (77 tests across 7 modules, 0 failures)
mvn -f Implementation/back-end/pom.xml test

# Run tests for a specific module
mvn -f Implementation/back-end/pom.xml -pl trade-service test

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

IceAmericanoMall is a B2B2C e-commerce platform (similar to Taobao/JD) built as a distributed microservices system. **Backend MVP is 100% complete** (35/35 PRD features, 62 API endpoints). The core transaction flow (register → browse → cart → order → pay → ship → confirm) is fully implemented end-to-end.

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
gate-service (API Gateway — Spring Cloud Gateway + Nacos)             ✅ COMPLETE
  ├── authorization-service (OAuth2 auth server, login/register, JWT) ✅ COMPLETE
  ├── user-service (user CRUD, addresses, sign-in, seller, admin)     ✅ COMPLETE
  ├── ia-api (shared Feign client interfaces + DTOs)                  ✅ COMPLETE
  ├── ia-common (shared lib: exceptions, Result, utils, config)       ✅ COMPLETE
  ├── item-service (products/SKUs/categories, stock)                  ✅ COMPLETE
  ├── cart-service (shopping cart, dedup merge)                       ✅ COMPLETE
  ├── trade-service (orders, Saga compensation, OrderManager)         ✅ COMPLETE
  ├── pay-service (WeChat Pay, callback, timeout)                     ✅ COMPLETE
  ├── logistics-service (shipping records, status tracking)           ✅ COMPLETE (V1.1: courier API)
  └── search-service (ElasticSearch search)                           🟡 SKELETON (V1.1)
```

**Status legend**: ✅ COMPLETE = business logic fully implemented, compiles, tested | 🟡 SKELETON = minimal scaffold only, no business logic

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
- `manager/` is the prescribed layer for orchestration, distributed locks, and multi-service aggregation. Currently **trade-service** has implemented `OrderManager` (full Saga orchestration: cart→SKU→address→stock→order). Other services delegate cross-cutting concerns to `ia-common`'s `@RateLimit` aspect.
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

### Object Conversion Flow (DO/DTO/VO/FeignDTO)

```
Controller REQ  →  Req (DTO)  ──converter──→  Service params
Service result  →  ServiceDTO ──converter──→  VO  →  Controller RESP
Mapper          →  Entity(DO) ←→ DB
Feign call      →  FeignDTO (from ia-api)

❌ NEVER: Controller returns Entity | Service takes Entity | Feign passes Entity | VO goes past Controller
```

### Common Anti-Patterns (instant PR rejection)

```java
// ❌ Controller with business logic or Redis
@GetMapping("/{id}")
public Result<UserVO> getUser(@PathVariable Long id) {
    String cached = redisTemplate.opsForValue().get("user:" + id); // NO
    if (sku.getStock() < req.getQty()) { ... }                      // NO
    return userMapper.selectById(id);                                // NO (returning Entity)
}
// ✅ Correct
@GetMapping("/{id}")
public Result<UserVO> getUser(@PathVariable Long id) {
    return Result.success(userConverter.toVO(userService.getById(id)));
}

// ❌ Service calling Feign directly
@Service
public class OrderServiceImpl {
    private final UserClient userClient;  // NO — belongs in Manager
}

// ❌ Money as float/double/BigDecimal
double total = price * quantity;           // NO
// ✅ Money in cents (Integer)
Integer total = price * quantity;          // 1250 = 12.50 元

// ❌ Bare exception or swallowing
throw new RuntimeException("error");       // NO
try { ... } catch (Exception e) { e.printStackTrace(); }  // NO
// ✅ ia-common exceptions
throw new BadRequestException(ErrorCode.USER_NOT_FOUND);
```

### What's Already Implemented

#### ✅ 完全实现 (11 modules — business logic complete, compiles, tested)

| Module | Key Features | Test Count |
|--------|-------------|------------|
| `ia-common` | `Result<T>`, `ErrorCode` enum, 6 exception types, `GlobalExceptionHandler`, `@RateLimit` + AOP, Lua scripts (rate_limit/check_limit/login_rate_limit), Aliyun SMS SDK (`@ConditionalOnProperty` switch), Geetest captcha, JWT utils, MyBatis-Plus config | 8 |
| `ia-api` | Feign clients: `UserClient`, `SkuClient`, `LogisticsClient`, `OrderClient`; shared DTOs; fallback factories | 0 |
| `gate-service` | Spring Cloud Gateway routes (all services), `JwtAuthenticationFilter` (userId/username header forwarding), `SecurityConfig` (public path whitelist), rate limiting | 0 |
| `authorization-service` | Password + SMS login (single endpoint, `loginType` param), registration, JWT HS256/RS256 issuance, JWKS endpoint, refresh token rotation, logout (Token blacklist), rate limiting, distributed lock (Redisson) | 0 |
| `user-service` | Registration (password+SMS), login validation, profile update (null-safe partial), address CRUD + default management, daily sign-in (Redis Bitmap), seller registration + shop management, admin user/role/status management, Aliyun SMS + Geetest captcha | 9 |
| `item-service` | Product CRUD + paginated list (search/sort/filter), category tree (level-1), SKU management, stock deduction/restore with **optimistic locking** (`@Version`), internal Feign endpoints for stock operations | 5 |
| `cart-service` | Add to cart (SKU dedup merge), quantity update (≤0 → delete), select/deselect toggle, clear cart, total/selected-price calculation, internal endpoints for order creation | 12 |
| `trade-service` | **OrderManager** (Saga orchestration: cart→SKU→address→stock→order), order CRUD, cancel (stock restore), confirm receipt, ship (→logistics Feign), seller dashboard, admin dashboard/order-list, `OrderTimeoutJob` (`@Scheduled`) | 16 |
| `pay-service` | WeChat Pay API v3 Native payment, callback with signature verification, idempotent processing, payment timeout (`PayTimeoutJob`), order status sync via Feign | 14 |
| `logistics-service` | Logistics record creation (via Feign from trade-service), status tracking (PENDING→SHIPPED→DELIVERED→RETURNED), internal status update endpoint, DB migration SQL | 13 |
| `database` | Full MySQL schema (`Initialize.sql`) with 11 tables; migration SQL for logistics status | 0 |

#### 🟡 骨架 (1 module — scaffold only, no business logic)

| Module | Status | Plan |
|--------|--------|------|
| `search-service` | Correct `SearchApplication` main class, `application.yml` configured, dependencies added. No controllers, services, or mappers. | V1.1 — ElasticSearch integration |

#### 🔵 V1.1 规划但未实现

Docker Compose, XXL-Job, SkyWalking, Prometheus/Grafana, ELK, RabbitMQ, Sentinel, MinIO, Canal, ShardingSphere, GitHub Actions CI/CD, WebSocket.

#### ⚪ V2.0+ 规划但未实现

Coupons, flash sales, after-sales, AI customer service, knowledge graph, multi-level categories, store decoration, reconciliation, invoices, mini-program.

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

### Key Domain Invariants (must hold in all code paths)

| Invariant | Rule |
|---|---|
| Order total | `totalAmount = Σ(orderItem.subTotal) - discountAmount` |
| Stock non-negative | `sku.stock >= 0` — check before deduct, rollback on cancel |
| Optimistic locking | `orders` and `sku` tables use `version` column + `@Version` |
| Snapshot at order time | `product_name`, `price`, `sku_spec`, `image`, receiver info — all copied into `order_item`/`orders`; never reference live tables |
| One user → one seller max | Check `seller.user_id` uniqueness before creating seller |
| Cart dedup | `(user_id, sku_id)` unique — same SKU increments quantity, never creates second row |
| Order status flow | 待付款→待发货→待收货→已完成→已取消→待评价 (see `OrderStateMachine` in 03-Domain-Model §7) |
| Internal APIs | `/internal/**` = raw exceptions (no `Result` wrap), called only within cluster by Feign |

### Test Conventions

**Naming**: `should_[ExpectedBehavior]_when_[Condition]`

```java
@Test
@DisplayName("注册 — 新手机号+有效验证码 → 返回Token")
void shouldReturnToken_whenNewPhoneAndValidCode() { ... }
```

**BDD → test mapping** (from PRD §5 scenarios):
- Every BDD `Scenario:` → ≥1 integration test
- Happy path: 1 test | Alternative: 1 per branch | Error: 1 per error case
- Service/Domain unit tests use JUnit 5 + Mockito (`@ExtendWith(MockitoExtension.class)`)
- Controller integration tests use `@SpringBootTest` + Testcontainers

**Current test coverage**: 77 unit tests across 7 modules (0 failures). Modules with zero tests: authorization-service, gate-service, ia-api, search-service, database. All existing tests are entity/enum/DTO validation or Mockito-based service tests. No `@SpringBootTest` integration tests exist yet.

**MyBatis-Plus testing limitation**: Methods using `lambdaQuery()`/`lambdaUpdate()` chains cannot be unit-tested with Mockito mocks (the mapper proxy's `currentModelClass()` reads generic type info lost on Mockito proxies). These methods require `@SpringBootTest` with H2 or Testcontainers. Methods using only standard `BaseMapper` methods (`save()`, `getById()`, `updateById()`) are fully testable with Mockito.

### Before Commit Checklist

- [ ] No Entity (DO) returned from Controller or passed as Service param
- [ ] Amounts in `Integer` (cents), never float/double
- [ ] All `@Transactional` have `rollbackFor = Exception.class`
- [ ] No `RuntimeException` — use `ia-common` exceptions with `ErrorCode`
- [ ] No Controller → Mapper or Service → Feign calls
- [ ] Magic values extracted to constants/enums
- [ ] `mvn clean install -DskipTests` passes
- [ ] BDD scenario covered by ≥1 test

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

### Available MCP Tools (this project)

| MCP Server | Tools | When to use |
|---|---|---|
| **memory** | `create_entities`, `create_relations`, `search_nodes`, `open_nodes` | Store/retrieve key decisions, patterns, and rules across sessions. Use after completing a service/phase to persist what was learned. |
| **sequential-thinking** | `sequentialthinking` | Complex multi-service features (e.g., order payment flow spanning trade→pay→logistics). Externalizes reasoning into visible, revisable steps. Use when implementing features that touch ≥3 services or involve state machines. |

**Memory usage pattern**: After each non-trivial implementation session, store the key decisions:
- "user-service uses BCrypt via Spring Security's PasswordEncoder, not raw BCrypt API"
- "trade-service order state machine: PENDING_PAY→PENDING_SHIP→PENDING_RECEIPT→COMPLETED/CANCELLED"
- "Feign client for user-service lives in ia-api module, interface: UserClient"

This prevents Claude from rediscovering architecture through grep tours in future sessions.
