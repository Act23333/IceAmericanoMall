# AGENTS.md

This file provides guidance to Codex (Codex.ai/code) when working with code in this repository.

## Before Writing Any Code (Mandatory)

1. **Identify task type** → read the corresponding doc (see table below)
2. **Confirm API spec exists** — if not, define it first (SDD principle)
3. **Write BDD scenario** (Given-When-Then) from PRD §5 or derive one
4. **Write failing test** → minimal implementation → refactor (TDD cycle)
5. **Check `09-constraints.md` anti-patterns** — before committing
6. **Self-review** against `10-review.md` checklist

### 文档-代码一致性规则（Doc-Code Consistency Rule）

```
┌──────────────────────────────────────────────────────────────┐
│ 文档已定稿 + 符合大厂规范 → 以文档为准生成代码，有问题提出来讨论   │
│ 文档有争议/待定/自相矛盾  → 先讨论确认再动手                    │
│ 文档过时/与代码矛盾       → 先修正文档，再改代码                 │
│ 代码有但文档无 → 代码是例外  │ 文档有但代码无 → 代码缺失需补齐     │
└──────────────────────────────────────────────────────────────┘
```

**原则**：项目文档（`project-docs/`）是唯一可信的规格来源。代码实现必须与文档一致。当发现不一致时，判断文档是否正确——正确则改代码，有问题则先提出来讨论。

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

# Run tests (2460 tests across all 19 modules, 0 failures, 1 pre-existing @Disabled skip — strict four-layer DDD everywhere, ADR-038/039; business IDs BINARY(16)+UUIDv7 per ADR-050)
mvn -f Implementation/back-end/pom.xml test

# Run tests for a specific module
mvn -f Implementation/back-end/pom.xml -pl trade-service test

# Before running any service, build shared modules first:
mvn -f Implementation/back-end/pom.xml -pl ia-common,ia-api,ia-integration -am clean install -DskipTests

# Run a service (pattern: mvn -f <service-dir> -DskipTests spring-boot:run)
mvn -f Implementation/back-end/user-service -DskipTests spring-boot:run          # port configured per-service
mvn -f Implementation/back-end/authorization-service -DskipTests spring-boot:run  # port 9000
mvn -f Implementation/back-end/gateway-service -DskipTests spring-boot:run           # port 8080
```

### Runtime prerequisites

Running services locally requires these to be up:

| Dependency | Config location                                      | Notes                                                     |
| ---------- | ---------------------------------------------------- | --------------------------------------------------------- |
| Nacos      | `application.yml` — `spring.cloud.nacos.server-addr` | Default: `192.168.10.128:8848`                            |
| Redis      | `application.yml` — `spring.data.redis.host`         | Used for token caching, rate limiting (Lua scripts)       |
| MySQL      | `application.yml` — `spring.datasource.url`          | Schema: `Implementation/back-end/database/Initialize.sql`（**全量唯一结构真相源，60 张表**，装库一次到位；2026-10-05 起迁移文件机制废除（ADR-045，库无存量数据）——结构变更直接修改本文件，`database/migrations/` 目录已不存在） |

Docker Compose infra exists: `Implementation/back-end/docker-compose.yml` + `docker/` (MySQL / Redis / Nacos / ES / RocketMQ / MinIO / Prometheus / Grafana / ELK / SkyWalking). Backend service processes run locally (IDE / CLI), not via compose.

## Architecture Overview

IceAmericanoMall is a B2B2C e-commerce platform (similar to Taobao/JD) built as a distributed microservices system. **Backend is fully implemented** (35/35 PRD features + P1/P2 growth lines + P3 拼团/发票/知识图谱/多币种 Phase 1 + 需求对账缺口收敛 + 小程序后端增量 + ADR-050 业务标识存储升级 + ADR-052 search 直读退出 + ArchUnit 架构守卫; **19 Maven modules** = 15 services + `architecture-tests` + 3 shared libs (`ia-common`/`ia-api`/`ia-integration`; `database/` is a script dir, **not** a module), **289 HTTP endpoints** measured (226 public + **63** `/internal/**`) — `project-docs/contract/05-API-Specification.md` §四 is the single source of truth (code↔contract diff closed: 0 un-documented), **2460 tests green** (all services strict four-layer DDD, ADR-038/039); re-measured 2026-10-15). The core transaction flow (register → browse → cart → order → pay → ship → confirm) is implemented end-to-end; module boundaries were finalized in the 2026-10 boundary refactor, round 6 (merchant-service) included (see `project-docs/backend/02-Architecture.md` §3).

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
gateway-service (API Gateway — Spring Cloud Gateway + Nacos + JWT/header filters) ✅ COMPLETE
  ├── authorization-service (auth + RBAC: login, user_auth credentials,            ✅ COMPLETE
  │                          sys_* role/permission management, permission cache)
  ├── user-service (profile domain: profile/avatar/address;                        ✅ COMPLETE
  │                 favorite/history/oplog)
  ├── merchant-service (merchant domain: onboarding apply/approve — approve        ✅ COMPLETE
  │                    auto-grants ROLE_SELLER; merchant identity (`seller` table)
  │                    + shop profile (`shop` table, 1:N — ADR-019)/public pages;
  │                    store follow (Redis Sets))
  ├── verification-service (codes + captcha — the sole verification outlet)        ✅ COMPLETE
  ├── notification-service (SMS/email/in-app; in-app messages persisted)           ✅ COMPLETE
  ├── wallet-service (balance account — NEW in 2026-10 boundary refactor)          ✅ COMPLETE
  ├── marketing-service (coupons, flash sales, points ledger, sign-in)             ✅ COMPLETE
  ├── ai-service (AI assistant/CS + human IM chat + merchant knowledge base)       ✅ COMPLETE
  ├── item-service (products/SKUs/categories/reviews, stock)                       ✅ COMPLETE
  ├── cart-service (shopping cart, dedup merge)                                    ✅ COMPLETE
  ├── trade-service (orders, Saga compensation, OrderManager; after-sale)          ✅ COMPLETE
  ├── pay-service (WeChat Pay, callback, multi-channel incl. balance)              ✅ COMPLETE
  ├── logistics-service (shipping records, status tracking)                        ✅ COMPLETE
  ├── search-service (ES full search + MySQL LIKE fallback, dual impl)             ✅ COMPLETE
  ├── ia-api / ia-common / ia-integration (shared modules)                         ✅ COMPLETE
  └── architecture-tests (ArchUnit guards — 19th module, no service code)          ✅ COMPLETE
```

**Status legend**: ✅ COMPLETE = business logic fully implemented, compiles, tested | 🟡 SKELETON = minimal scaffold only (none currently — every module is implemented)

`ia-common` is the shared kernel — it defines `Result<T>` (in `common/api`, ADR-040; identity holders `UserContext`/`UserInfo` live in `common/security`), `ErrorCode` enum, exception hierarchy (`CommonException`, `BadRequestException`, `BizException`, `DBException`, `ForbiddenException`, `UnauthorizedException`), `@RateLimit` annotation + `RateLimitAspect` (rate-limit key includes method dimension: `rate:limit:{Class.method}:{key}`, FREQUENT_ERROR=429), `GlobalExceptionHandler` (`/internal/**` errors return real HTTP status + minimal `{"code","msg"}` JSON; `ConstraintViolationException` → 400), `common/lock/DistributedLock` + `RedissonDistributedLockAdapter` (conditional; ADR-024 — auth/user copies deleted), `OptimisticLockerInnerInterceptor` (registered V5.3), Lua scripts (`lua/rate_limit.lua` only — `check_limit`/`login_rate_limit`/`del_redisKey` were zero-call and removed), `CryptoKeyConfig` (PII AES/HMAC key loading), `IpUtil` (client-IP resolution, trusts gateway-injected `X-Client-Ip`), `@OperationLog` + `OperationLogAspect` (audit-only aspect, ADR-032: single-line JSON to logger `AUDIT`, registered via `AutoConfiguration.imports`, zero business dependencies, params not recorded by default), `AuditFieldMetaObjectHandler` (audit-field fill for `createTime`/`updateTime`; auto-registered via `MyMetaObjectHandlerAutoConfiguration` with `@ConditionalOnMissingBean` back-off, ADR-030 — seven service copies deleted, user-service keeps its own variant for `registerTime`), `SecurityHeaders` (14 trusted/proxy header-name constants with trust-boundary docs, ADR-034), `DesensitizeUtil` mask family (`maskPhone`/`maskEmail`/`maskAccount` — single definition for masking rules, ADR-034), MyBatis-Plus config, Json config. (V5.2: SMS/Geetest third-party integration moved out of ia-common into `ia-integration`; V5.3: dead SMS/`FieldPermission` AOP code removed; V5.4: `@OperationLog` audit aspect restored for the audit dual pipeline — the `operation_log` DB ledger is written explicitly by user-service only, not by the aspect.)

`ia-api` contains 16 Feign client interfaces (e.g., `UserClient`, `SkuClient`, `AuthContextClient`, `BalanceClient`, `NotificationClient`, `PointsClient`, `VerificationClient`, `ItemSearchClient`), shared DTOs, and fallback implementations so services can call each other without duplicating contract definitions.

**Package naming note (V5.2 unified)**: All modules share the base package `org.icedamericanomall`. Service implementations are segmented by service: `org.icedamericanomall.<service>` (e.g., `user`, `item`, `auth`, `gateway`, `wallet`); shared modules live in dedicated segments: `ia-common` = `org.icedamericanomall.common`, `ia-integration` = `org.icedamericanomall.integration.*`, `ia-api` = `org.icedamericanomall.{client,config,dto,fallback}`. Never create a sub-package in one module that shares a name with another service's segment (e.g., an `auth/` package inside item-service). Because service base packages no longer cover shared-module packages, service main classes must declare explicit `scanBasePackages` for ia-api fallback / ia-integration beans they use (see ADR-009).

### Package Structure (strict four-layer DDD — unified since ADR-038)

All 14 business services follow the same four-layer layout; the lightweight COLA shape (top-level `service/` `mapper/` `entity/` `convert/` `job/` `consumer/` `producer/` `strategy/` packages) no longer exists anywhere (ADR-038 abolished ADR-023's "two shapes" clause).

```
org.icedamericanomall.<service>
├── adapter/           — REST endpoints by audience: web / admin / internal
│   └── …              — + request (XxxReq) / response (XxxVO) / assembler (MapStruct, renamed from convert)
├── app/               — orchestration managers, one per use-case family (complex families get sub-packages,
│                        e.g. marketing app/{coupon,flash,points,sign}, trade app/{order,aftersale,finance,dashboard})
├── domain/            — model/<aggregate>/ / service/ (pure functions) / repository/ (ports) / port/ / enums/ (framework-free)
└── infrastructure/    — persistence/ (PO + Mapper + RepositoryImpl; multi-statement writes carry @Transactional here),
                          adapter/ (Redis/HTTP anti-corruption), messaging/ (producer+consumer), scheduler/ (jobs), config/
```

**Key rules:**

- Controller: only param validation + call Manager + wrap in `Result`. Never write business logic or operate Redis/locks directly. Controllers do not hold converters — Managers return VOs.
- Orchestration, distributed locks, multi-service aggregation, Feign and MQ live in the app layer (`app/*Manager` or sub-package Managers, e.g. trade `OrderCreationManager` full Saga: validate→buildOrder→deductStock→persist→afterCreate). Controllers stay thin. One Manager per aggregate/use-case family; split mixed-responsibility classes (ADR-025).
- Adapter packages are organized by audience only: `adapter/web` / `adapter/admin` / `adapter/internal`, plus `adapter/request` / `adapter/response` / `adapter/assembler` (ADR-023, package renamed from `convert` per user decision 2026-10). Inbound bodies must be typed `XxxReq` with validation (never `Map`/raw scalar bodies); VOs must not expose technical fields (`version`/`cipher`/`hash`).
- Domain aggregates are plain POJOs (no Spring/MyBatis/Feign imports); persistence POs keep MyBatis annotations (`@TableName`/`@Version`/`@TableField`). PO↔aggregate conversion is inlined in the RepositoryImpl (static toPO/toDomain); aggregates never leak past the app layer.
- State-machine transitions use conditional updates (`UPDATE ... WHERE id=? AND status=expected`) owned by RepositoryImpl — explicit SQL on the Mapper when the invariant needs to be contractual (trade `AfterSaleMapper.updateStatusConditionally`, `OrderItemMapper.markPackageShipped`).
- Managers that call Feign must NOT be `@Transactional` (hard constraint); multi-statement local writes sink `@Transactional` into the repository impl (pay `ReconciliationRepositoryImpl` precedent). Managers may carry `@Transactional` only when the whole method is local.
- Persistence POs must NOT leak to controller or external services — always convert to DTO/VO.
- Inter-service calls use `ia-api` Feign interfaces; request/response use dedicated Feign DTOs.
- Internal endpoints (path contains `/internal/`) bypass `Result` wrapping on exceptions: business exceptions return the real HTTP status + minimal `{"code","msg"}` JSON (ADR-004, revised V5.3).
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
- **Captcha (vendor-neutral)**: callers send `provider` + `params` (`CaptchaVerifyReqCO`) to verification-service — vendor field unpacking exists only inside verification-service; never share verification Redis keys across services.
- **Adapter structure (ADR-023)**: `adapter/` 按受众分组 —— `web`（前台与商家端）/ `admin`（`/api/admin/**`）/ `internal`（`/internal/**`），配 `request`（`XxxReq`）/ `response`（`XxxVO`）/ `assembler`（MapStruct）子包；入参禁止 `Map`/裸标量 body。
- **Feign circuit breaker**: resilience4j (`spring.cloud.openfeign.circuitbreaker.enabled=true`, TimeLimiter 5s, failure-rate 50%) enabled in authorization / user / merchant / verification / marketing / ai / pay / logistics (ADR-011/022); trade keeps `feign.sentinel.enabled` with its rules now **registered and active** — `SentinelConfig` is keyed on `spring.cloud.sentinel.enabled` (previously mis-keyed, so rules never registered; fixed per ADR-028): flow QPS 100 + exception-ratio 0.5 degrade on `createOrder`, blockHandler → `FREQUENT_ERROR` 429; dashboard stays off.
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
    private final UserClient userClient;  // NO — belongs in the orchestration layer (Manager)
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

#### ✅ 完全实现 (19 modules — business logic complete, compiles, tested)

| Module | Key Features | Test Count |
|--------|-------------|------------|
| `ia-common` | `Result<T>`, `ErrorCode`, 6 exception types, `GlobalExceptionHandler` (`/internal/**` → real HTTP status + minimal JSON; `ConstraintViolationException` → 400; ✅ 补测 3 例：缺参 / 类型不符 / 请求体不可读三个出口), `@RateLimit` + AOP (method-scoped key), `common/lock/DistributedLock` (ADR-024), `lua/rate_limit.lua` only, `CryptoKeyConfig` (PII keys), `IpUtil` client-IP (trusts gateway `X-Client-Ip`), `AuditFieldMetaObjectHandler` audit-field fill (auto-registered, ADR-030; user-service keeps its own `registerTime` variant), `SecurityHeaders` header constants (ADR-034), `DesensitizeUtil` mask family (ADR-034), **`utils/IdGenerator` (UUIDv7 业务 ID + `secureToken` 凭据, ADR-050)**, MyBatis-Plus config (+ `OptimisticLockerInnerInterceptor`) | 79 |
| `ia-api` | 16 Feign clients (`UserClient`, `SkuClient`, `AuthContextClient`, `BalanceClient`, `NotificationClient`, `PointsClient`, `VerificationClient`, `ItemSearchClient`, ...); shared DTOs; fallback factories (ADR-011) | 14 |
| `ia-integration` | Third-party SDK hub: WeChat Pay v3 (Native + **JSAPI 小程序拉起 V5.18**) / Alipay mock / Aliyun SMS / email (SMTP+Mock) / Geetest v4 / MinIO / WeChat OAuth / **WeChat Mini API (stable_token + wxacode 分享码 V5.18)** | 66 |
| `gateway-service` | Spring Cloud Gateway routes (**20 route blocks / 48 path predicates**, re-measured 2026-10-15; `/api/shops/**` + `/api/home/**` patched 2026-10; **admin/seller-specific prefixes must precede the `/api/admin/**` fallback — `/api/admin/{home,kg,invoice,roles,permissions,seller,pay,currency,flash,promotion,task,group-buy}/**`; ADR-035/036**), `JwtBlacklistFilter` (reactive, order -700, fail-open; one MGET merging `auth:blacklist:{jti}` + `auth:tokenver:{userId}` per ADR-031; write side in authorization-service), `WsHandshakeTokenFilter` (WebFilter, order -900: swaps WS handshake `?token=` into `Authorization: Bearer` and strips the query param, ADR-026), `UntrustedHeaderSanitizerFilter` (order -1100, strips XFF + overwrites `X-Client-Ip`), method-level GET-only whitelist, auth/verification Redis `RequestRateLimiter`, `AiTokenQuotaFilter` | 74 |
| `authorization-service` | Login (password/SMS/email/WeChat) + register + reset-password, `user_auth` credential ownership, **RBAC five tables + `user:perms:` cache (write / evict / warm-up)**, JWKS, refresh rotation, **tokenVer revocation (password reset / admin disable → all-device logout via `revokeUserTokens`, ADR-031)**, `AuthContextClient` internal contract | 137 |
| `user-service` | Profile (PII-encrypted phone/email), avatar upload, address CRUD + default, favorite/history, **`operation_log` audit ledger writer for user-domain admin ops (status/role changes, ADR-032 pipe 2)**, **member level engine (growth log `uk_order_no` idempotent, rise-only recalc, I45 level benefits / I46 growth — ADR-044)**, idempotent `getOrCreate` provisioning; **oplog four-layer clean-up (`OperationLogRepository` port + domain model, 2026-10)** | 78 |
| `merchant-service` | Merchant onboarding (`POST /api/seller/apply`, approve auto-grants ROLE_SELLER via `AuthContextClient`), merchant identity (`seller` table) + **shop profile (`shop` table, 1 merchant : N shops, ADR-019)**, public shop pages (`/api/shop/{sellerId}` legacy + `/api/shops/{shopId}`), store follow (Redis Sets), admin review (`/api/admin/seller/**`) | 73 |
| `architecture-tests` | ArchUnit 架构守卫（第 19 模块）：domain 框架无关 / PO 不出 persistence / adapter 不触 persistence / UUID 生成白名单（ADR-038/050）；存量债务 FreezingArchRule 冻结 25 条（只减不增，`allowStoreCreation=false` 红线） | 4 |
| `verification-service` | Code issue/verify (single outlet, fail-closed, **scene-isolated pools + global daily cap** ADR-051) + captcha (`provider`+`params` vendor-neutral contract) | 30 |
| `notification-service` | SMS/email channel delivery + **in-app messages persisted (`notification` table, `/api/notifications/**`, self-send ignored)** | 9 |
| `wallet-service` | Balance account (`wallet_account`), **balance transactions (`wallet_transaction`, idempotent `transaction_no`, refund credit via `/internal/balance/refund` — ADR-035)**, `GET/POST /api/wallet(/recharge)`, `/internal/balance/deduct` (consumed by pay-service balance channel), **currency/exchange-rate data plane (P36/I61/AD66-68 + daily pull job, fixed-point `rate_scaled` — PRD §十七)** | 54 |
| `marketing-service` | Coupons (5-dimension model, Lua grab + DB-failure compensation), flash sales (**`/api/flash/buy` → Manager: Lua pre-deduct (explicit warm-up + lazy fallback) → Feign order → MQ stats**), **points ledger (`points_log`, `/internal/points/add`)**, **sign-in (Redis Bitmap, 10/15/20 policy)**, **admin flash management (AD40-42, time-window status)**, **promotion engine (AD48-50 store-level 满减/满折 + I44 active-promotion query, ADR-043)**, **daily task system (task_def table-driven, M14 task center, I49 idempotent completion — ADR-047)**, **activity center (M12/M13 over live promotions) + coupon count (M7)**, **admin task CRUD (AD51-53) — V5.13**, **shop decoration (S1c/S1d, 1:1 shop_decoration — ADR-048)**, **group buy (AD54-56/M15-17 two-phase join + expire-refund job — ADR-049)**; **couponId normalized at create (ADR-050 revision)** | 194 |
| `ai-service` | AI assistant + customer service, **human IM chat (STOMP `/ws/chat` + REST `/api/chat/**`, sender identity from session Principal)**, **merchant knowledge base (`/api/seller/knowledge`)**, **RAG wiring (opt-in via `ai.rag.enabled`; vectors ingested to search-service `knowledge_vectors`, retriever injects topK into CS replies, fail-open — T14)**, domain ports (6) + infrastructure anti-corruption adapters + LangChain4j agents/tools under `infrastructure/{agent,adapter/tool,rag,routing}` (four-layer close-out, ADR-039) | 52 |
| `item-service` | Product CRUD + paginated list (with `minPrice`), category tree, SKU management, **stock via single-statement atomic SQL deduction (ADR-020)**, **freight templates (`freight_template` CRUD `/api/item/freight-template/**` + product binding + internal I43 pricing query, ADR-037)**, reviews, home config, internal Feign endpoints (**internal product-search data plane `/internal/item/search/**` — I62-65, ADR-052**), **similar-product recommendation (P27, fail-open vector assembly — ADR-046)**, **daily review task trigger (afterCommit, ADR-047)**, **detail promotion tags (P1/P2 `promotions` via I44, fail-open + 60s cache)**, **content notes (P28-P33 种草笔记, content aggregate — ADR-048)**, **note vectors + P34 similar notes (ADR-046 reuse) — V5.13**, **KG pairing card (P35, graph-first + vector fallback fail-open via I58) + admin pairing-rule governance (AD61-65 incl. neighbors view — `pairing_rule` truth source + `kg_edges` ES projection — PRD §十六)**, **product share wxa-code (P37 via WeChat Mini API — V5.18)** | 181 |
| `cart-service` | Add to cart (SKU dedup merge, concurrent-insert idempotent merge), quantity update (≤0 → delete), select/deselect toggle, clear cart, **read-time view assembly (`CartManager` batch enrichment + `allSelected` semantics, ADR-021)**, internal endpoints | 16 |
| `trade-service` | **OrderManager** (Saga: cart→SKU→address→stock→order), order CRUD, cancel/confirm/ship, **freight pricing (`PostageCalculator` pure function, max-fee-once + free-shipping threshold, flash-sale free; I43 fail-closed) + package-split shipping (`order_item.package_no`, partial shipment stays 待发货 — ADR-037)**, **pricing center (`PricingCalculator` pure function + `PricingService`: promotion I44 fail-closed → member I45 fail-open → coupon, stack + best-of + total-cap guard, ADR-043)**, after-sale (6-state enum) **with refund triggering (review for type=2, confirm-return for type=1)**, settlement, member growth/points-rate/task wiring on confirm-receipt (I46/I49 fail-open, ADR-044/047), **group buy (`GROUP_BUY(5)` strategy + T27 + pay-success join hook + I54 refund-team loop — ADR-049)**, **invoices (INV1-5 buyer / AD57-60 admin mock-issue via `InvoiceIssuePort`, full-refund auto red-flush I56 — PRD §十五)**, seller/admin dashboards, timeout cancel (RocketMQ delay + `AbnormalOrderJob` XXL-Job fallback), **invoice titles CRUD (INV6-9) + partial-refund flag trigger (I60 — V5.16.1 对账缺口收敛)**, **display-currency snapshot locking (T1/T6/T27 `displayCurrency` + `orders.display_*` fail-open via I61 — PRD §十七)**, **mini-program pay (PAY7 JSAPI 拉起参数集) + product share wxa-code (P37 — V5.18)** | 342 |
| `pay-service` | WeChat Pay API v3, callback verification, idempotent processing (`markSuccessIfPending` conditional update), multi-channel routing (WECHAT/ALIPAY/BALANCE→BalanceClient), **refund execution (`RefundManager` 3-channel original-route refund, `refund_order` `biz_ref`/`uk_after_sale` idempotency — ADR-035/049)**, **channel reconciliation (4-state diff record-only, pure comparer in `domain/service/ReconciliationComparer`, ADR-036)**, **stuck-refund resume (`RefundResumeJob`: wechat refund-query settle / balance-alipay idempotent replay — ADR-041)**, buyer in-app notify on pay/refund success + ship/after-sale review (`PayEventNotifier`/`TradeEventNotifier`, ADR-042), **order-level refund without after-sale (`OrderRefundManager` I55, `biz_ref` generalized idempotency — ADR-049)**, **full-refund invoice linkage (`TradeInvoiceClient` void/red-flush trigger — PRD §十五 D4)**, **partial-refund flag dispatch (I60, V5.16.1)**, **mini-program JSAPI initiation (PAY7, `wechat.pay.app-id` binding — V5.18)**, timeout job (four-layer close-out; `@MapperScan` fix) | 128 |
| `logistics-service` | Logistics records (via Feign from trade), status tracking (PENDING→SHIPPED→DELIVERED↔RETURNED, monotonic + idempotent same-state replay), internal update endpoint | 33 |
| `search-service` | ES full search + **item-service internal-search delegation fallback (no direct DB reads since ADR-052; ES opt-in via `search.elasticsearch.enabled`; self-owned `SearchPageVO` envelope)**, **product vector recommendation (I47 upsert / I48 similar kNN, reindex rebuilds with knn mapping + vectors — ADR-046)**, **search facets + brand/price/sort filters (SE6 aggregation, SE1 filter parity across both chains — V5.10)**, **search suggest (SE5 hot-keyword + product-name prefix) — V5.13**, **KG retrieval plane (`kg_edges` edge index + I57/I58/I59, ≤2-hop terms — PRD §十六)** | 172 |
| `database` | `Initialize.sql` — the single source of truth for the whole schema (60 tables; migrations mechanism removed 2026-10-05 per ADR-045 — schema changes edit this file directly) | 0 |

#### 🔵 Roadmap status

Authoritative roadmap: `project-docs/backend/02-Architecture.md` §七. Delivered in V1.x–V4.x (verified in repo): Docker Compose dev stack, RocketMQ (replacing RabbitMQ), ElasticSearch search, MinIO storage, WebSocket/STOMP chat, Prometheus/Grafana/ELK/SkyWalking configs, GitHub Actions CI (`.github/workflows/ci.yml`). Not yet implemented: K8s orchestration, Seata, ShardingSphere, Canal CDC, multi-instance Nacos cluster.

#### ⚪ Not built (do not start without a PRD/spec update)

mini-program/App（**前端工程为主，后端接口全量共享**——微信小程序登录通道 V5.1 已实现（`POST /api/auth/wechat/mini/login` + `WechatMiniLoginStrategy`，`user_auth.wx_openid`），业务契约零改动；**后端增量已全部补齐（V5.18）**：PAY7 JSAPI 拉起支付（含 `wechat.pay.app-id` 同源绑定修正）+ P37 商品分享小程序码；对接清单见 `product/02-Frontend-Integration-Checklist.md`，小程序前端本体落 16-Frontend-Product-Design）。If asked to build the mini-program frontend, flag that it is outside this backend repo's scope. (Delivered and removed from this list: channel reconciliation 对账 V5.5 — ADR-036; group buys 拼团 V5.14 — ADR-049; invoices 发票 V5.15 — PRD §十五 定稿; knowledge graph Phase 1 商品关系导购 V5.16 — PRD §十六 定稿; multi-currency Phase 1 展示换算+订单快照锁汇 V5.17 — PRD §十七 定稿.)

### Hard Constraints (from `project-docs/collaboration/09-constraints.md`)

These are non-negotiable rules. Violating any of them = PR rejection.

| Rule | Detail |
|------|--------|
| Transactions | `@Transactional(rollbackFor = Exception.class)` — never omit `rollbackFor` |
| Money | `INTEGER` (cents). Never `float`/`double`/`BigDecimal` for amounts |
| Method size | ≤ 50 lines. Class ≤ 300 lines. Split if exceeded |
| Internal APIs | `/internal/**` business exceptions = real HTTP status + minimal `{"code","msg"}` JSON (no `Result` wrap) |
| Soft delete | Use `@TableLogic` for recoverable data; hard delete requires review |
| Service → Feign | ❌ Forbidden. Feign calls belong in the orchestration layer (`manager/` or `app/*Manager`) |
| Cross-service state | ❌ No shared Redis keys between services (old `sms:code:` cross-service contract dissolved); verification via verification-service only, balance ops via wallet-service |
| Exceptions | Only `ia-common` exceptions (`BadRequestException`, `BizException`, etc.). Never bare `RuntimeException` |
| Magic values | All status codes, config values, error messages → constants/enums/ErrorCode |

### Key Domain Invariants (must hold in all code paths)

| Invariant | Rule |
|---|---|
| Order total | `payAmount = totalAmount − discountAmount + postFee` (V2, ADR-037); `totalAmount` still = Σ(orderItem.subTotal) — freight never enters item subtotals, snapshotted in `orders.post_fee`; flash-sale orders carry `postFee = 0` |
| Stock non-negative | `sku.stock >= 0` — enforced by the atomic `UPDATE ... WHERE stock >= ?` (0 rows = failure, ADR-020); stock restored on cancel/timeout (idempotent) |
| Optimistic locking | `orders` uses `version` + `@Version` (interceptor registered in ia-common); `sku` uses single-statement atomic SQL instead (ADR-020) — its `version` column is audit-only |
| Snapshot at order time | `product_name`, `price`, `sku_spec`, `image`, receiver info — all copied into `order_item`/`orders`; never reference live tables |
| One user → one merchant max | Enforced by `MerchantRegistrar` (merchant-service) + `seller.user_id` unique key |
| Cart dedup | `(user_id, sku_id)` unique — same SKU increments quantity, never creates second row |
| Order status flow | 待付款→待发货→待收货→已完成→已取消→待评价 (see `OrderStatusEnum` + `OrderLifecycleManager`, 03-Domain-Model §7) |
| Internal APIs | `/internal/**` = real HTTP status + minimal `{"code","msg"}` JSON on business exceptions (no `Result` wrap), called only within cluster by Feign |
| Wallet ops | Balance changes go through wallet-service (`/internal/balance/deduct`); `user.balance` is frozen (never read/write) |
| Verification outlet | Code/captcha checks go through verification-service only — no local checks, no shared Redis keys |

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

**Current test coverage**: **2460 tests** across all **19 Maven modules** (0 failures, 0 errors, **1** pre-existing `@Disabled` skip; `database/` ships SQL only, is **not** a module, and carries no tests). ⚠️ **口径说明**：上表 per-module 列为 2026-10-10 surefire 快照（合计 1747，**与新基线不可直接对齐**——模块归属有调整且新增测试未分摊）；**2460 为 2026-10-15 全量实测基线，repo 总数以此为准**。需要精确 per-module 拆分时以 `mvn -f Implementation/back-end/pom.xml clean test` + surefire 报告为准。Tests are mostly JUnit 5 + Mockito unit tests; several services carry self-contained H2-backed persistence tests (`UserProvisioningH2Test` in user, `OrderRepositoryImplH2Test`/`InvoiceRepositoryImplH2Test` in trade, `FlashSaleManagerH2Test` in marketing, `CartManagerH2Test` in cart, etc. — manual MyBatis-Plus wiring against repository impls). No `@SpringBootTest`/Testcontainers integration tests yet.

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
- [ ] ID invariants gate passes (`bash Implementation/back-end/scripts/check-id-invariants.sh`, enforced in CI)

### Scope Boundaries (post-2026-10 boundary refactor)

**In scope (all delivered)**: the full MVP flow plus V-series expansions — registration/login (password/SMS/email/WeChat), profile/address/merchant/shop/store-follow, sign-in & points ledger, coupons & flash sales, wallet balance, in-app notifications, IM & AI customer service + merchant knowledge base, orders/payment (WeChat/Alipay-mock/balance)/logistics, after-sales, settlement, seller & admin consoles, reviews/favorites/browsing history, ES search, home decoration, RBAC.

**Out of scope / not built** (flag as future work before starting): mini-program/app, multi-currency (完整 PRD 已产出待评审——`01-PRD.md` §十七), knowledge graph Phase 2 政策问答/可视化 (触发器见 PRD §16.9). Delivered and removed from this list: 对账 (V5.5) / 拼团 (V5.14) / 发票 (V5.15) / 知识图谱 Phase 1 (V5.16). See the roadmap status above and `project-docs/backend/02-Architecture.md`.

### Database

Single MySQL schema under `Implementation/back-end/database/`. **`Initialize.sql` is the single source of truth for the whole schema (60 tables)** — one-shot install. **The migrations mechanism was removed on 2026-10-05 (ADR-045: no existing data, no upgrade path needed)** — every schema change (new table / new column / constraint / seed) edits `Initialize.sql` directly; there are no migration files. Tables include `user`/`address`/`seller`/`shop`/`category`/`product`/`sku`/`orders`/`order_item`/`cart`/`pay_order`/`refund_order`/`wallet_transaction`/`reconciliation_batch`/`order_logistics`/`freight_template`/`promotion`/`member_level_rule`/`member_growth_log`/`user_auth`/`wallet_account`/`notification`/`points_log`/`chat_message`/`merchant_knowledge_base`/`sys_*` RBAC tables etc. Every **business** table uses a technical `BIGINT AUTO_INCREMENT` primary key and a business unique identifier (UUID-based) where needed — the **three pure relation tables** (`sys_user_role` / `sys_role_permission` / `sys_user_permission`) are the only exception: they use a two-column composite PK (the pair is the constraint, a surrogate key would never be referenced). ⚠️ `xxx_id` is **same name, two types** (`user_id`/`spu_id`/`sku_id`/`coupon_id`): `BINARY(16)` = business key (aggregate roots + the `order_item` snapshot, ADR-050/061), `BIGINT` = technical FK — tell them apart by the presence of `FOREIGN KEY ... REFERENCES`, **not** by column name (`Initialize.sql` header §5; read-only report: `bash scripts/check-schema-keys.sh`) — **`BINARY(16)` + UUIDv7 since ADR-050** (generated via `ia-common` `IdGenerator.nextId()`, PO boundary `byte[]`, domain/API all-String 32-hex; kept-VARCHAR exceptions and wrapper-query `toBytes`/`toBytesOrNull` rules see ADR-050 and 09-constraints §三·五) (the former `order_logistics` PK deviation — `order_id` as PK — was resolved in V5.6: surrogate `id` PK + `uk_order_package(order_id, package_no)`, ADR-037). Monetary values are stored as `INT` (cents). Orders store address snapshots (not foreign keys) to preserve historical data. RBAC seed: `ROLE_ADMIN` holds the `*:*:*` super permission.

### Project Documentation

> **⚠️ 镜像说明**：本文件是根 `CLAUDE.md` 的**镜像副本**（面向 Codex/其他 agent 宿主），两者内容基本一致。**计数类事实（表数 / 模块数 / 端点数 / 路由块数 / 测试数）以 `CLAUDE.md` 为准**；修改其中任一份时请同步另一份，避免再次出现「57 张表 vs 62 tables」式的自相矛盾。完整 ADR 索引见 `project-docs/adr/README.md`（当前 001–067）。

- `doc/methodology/` — Generic analysis methodology (11-phase pipeline: BDD→DDD→SDD→TDD→SPC), reusable for any project
- `project-docs/adr/` — Architecture Decision Records (**001–067**, canonical index in `project-docs/adr/README.md`): DDD layering, dual primary keys, DO/DTO/VO isolation, internal API exception passthrough, unified identity + RBAC ownership (ADR-018), merchant/shop 1:N (ADR-019), SKU atomic SQL (ADR-020), cart read-time enrichment (ADR-021), Feign circuit breaker enablement (ADR-022), adapter layer structure (ADR-023), distributed lock consolidation (ADR-024), aggregate packaging (ADR-025), WS handshake token swap (ADR-026), jti blacklist (ADR-027), trade Sentinel wiring (ADR-028), channel callback protocol (ADR-029), MyMetaObjectHandler consolidation (ADR-030), multi-granularity token revocation (ADR-031), audit dual pipeline (ADR-032), cross-module utility consolidation (ADR-034), refund fund flow (ADR-035), channel reconciliation record-only (ADR-036), freight model + package split + refund trigger (ADR-037), full four-layer DDD unification (ADR-038), ai-service DDD completion (ADR-039), ia-common shared-kernel package rename (ADR-040), refund-resume/aftersale guard (ADR-041), buyer notification wiring (ADR-042), pricing center (ADR-043), member level (ADR-044), DB snapshot single source (ADR-045), product vector recommendation (ADR-046), daily task system (ADR-047), content notes (ADR-048), group buy (ADR-049), BINARY(16)+UUIDv7 (ADR-050), register fast-reject (ADR-051), search DB-fallback exemption (ADR-052), doc ownership by nature (ADR-053), frontend multi-platform doc axis (ADR-054), coupon scope/stack materialization (ADR-055), 线上字段与查询参数命名统一为 snake_case (ADR-056), 展示字段回填的应用层读模型端口 (ADR-057), VO 标识字段的语义唯一性（ADR-058）、SPU/SKU 词汇消歧（ADR-059）、券核销「三网」（事实标记补偿 + 定时兜底对账，ADR-060）、订单项业务键标识 + 快照与比价读时增强（ADR-061）、对外标识与寻址口径（业务键单形态 + SPU 资源段改名 + 编解码唯一出口，ADR-062）、对外渠道号与内部业务键分离（渠道号独立成列 + 唯一寻址口径 + 承载层解耦，ADR-063）.
- `project-docs/` — IceAmericanoMall-specific deliverables (PRD, Architecture, Domain Model, API Spec, Data Model, Workflow, Test Strategy, Security Model)
- `project-docs/collaboration/08-agents.md` — **AI coding instructions** (layer templates, naming, test patterns, forbidden patterns). Read before writing any code.
- `project-docs/collaboration/09-constraints.md` — **Hard constraints & anti-patterns** (version locks, layer rules, security rules, coding rules). Check before committing.
- `project-docs/drawio/` — Architecture diagrams (use case, ER, sequence, domain class, C4 container, security DFD — both `.drawio` and `.mmd` formats)

### Available MCP Tools (this project)

| MCP Server | Tools | When to use |
|---|---|---|
| **memory** | `create_entities`, `create_relations`, `search_nodes`, `open_nodes` | Store/retrieve key decisions, patterns, and rules across sessions. Use after completing a service/phase to persist what was learned. |
| **sequential-thinking** | `sequentialthinking` | Complex multi-service features (e.g., order payment flow spanning trade→pay→logistics). Externalizes reasoning into visible, revisable steps. Use when implementing features that touch ≥3 services or involve state machines. |

**Memory usage pattern**: After each non-trivial implementation session, store the key decisions:
- "user-service uses BCrypt via Spring Security's PasswordEncoder, not raw BCrypt API"
- "trade-service order state machine: PENDING_PAY→PENDING_SHIP→PENDING_RECEIPT→COMPLETED/CANCELLED"
- "Feign client for user-service lives in ia-api module, interface: UserClient"

This prevents Codex from rediscovering architecture through grep tours in future sessions.
