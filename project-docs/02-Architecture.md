# 02 — 系统架构文档

> 来源：`doc/项目难题/冰美商城的最终架构.md`、`doc/项目难题/架构分层、工程结构、微服务规范、分布式设计选择.md`、`doc/IDEA.md`、`doc/项目流程/登录流程详解.md`、`doc/项目的分析流程/IcedAmericanoMall的MVP.md`、`doc/项目难题/大厂的微服务项目结构.md`

---

## 一、架构风格

**分布式微服务架构**，采用 **阿里轻量化 DDD 分层体系**作为底层结构规范，融合 **字节跳动代码风格**（小类、小方法、单一职责）作为编码习惯。

参考标准：

- 架构分层、工程结构、微服务规范、分布式设计 → **学阿里**
- 代码整洁、类设计、方法拆分 → **学字节**
- 开发效率、不过度设计 → **借鉴腾讯**

---

## 二、技术栈

| 层级         | 技术                                            | 版本         | 用途                 |
| ---------- | --------------------------------------------- | ---------- | ------------------ |
| **语言**     | Java                                          | 21         |                    |
| **框架**     | Spring Boot                                   | 3.5.4      | 微服务基础框架            |
| **云原生**    | Spring Cloud                                  | 2025.0.1   | 服务治理               |
|            | Spring Cloud Alibaba                          | 2025.0.0.0 | Nacos 注册/配置中心      |
| **网关**     | Spring Cloud Gateway                          | —          | API 网关             |
| **认证**     | Spring Security + OAuth2 Authorization Server | 1.3.0      | 认证授权               |
|            | JWT (JJWT)                                    | 0.13.0     | HS256 + RS256 令牌   |
| **持久层**    | MyBatis-Plus                                  | 3.5.11     | ORM                |
| **数据库**    | MySQL                                         | 9.3.0      | 关系型存储              |
| **缓存**     | Redis                                         | —          | 缓存、分布式锁、限流         |
| **搜索**     | ElasticSearch                                 | 7.17.25    | 全文检索               |
| **消息队列**   | RabbitMQ / RocketMQ                           | —          | 异步解耦（计划中）          |
| **AI**      | LLM (通义千问/DeepSeek)                         | —          | 智能对话、商品推荐（计划中）           |
|            | Spring AI / 自研 Agent                         | —          | AI Agent 框架（计划中）            |
|            | Milvus                                        | —          | 向量检索（计划中）                |
| **对象映射**   | MapStruct                                     | 1.5.5      | Entity ↔ DTO/VO 转换 |
| **工具库**    | Lombok                                        | 1.18.42    | 代码简化               |
|            | Hutool                                        | 5.8.43     | 通用工具               |
| **API 文档** | Knife4j (OpenAPI 3)                           | 4.5.0      | Swagger 增强         |
| **服务调用**   | OpenFeign + LoadBalancer                      | —          | 微服务间 RPC           |
| **可观测性**   | Prometheus + Grafana                          | —          | 监控告警（计划中）          |
|            | ELK (Logstash + Kibana)                       | —          | 日志采集（计划中）          |
| **容器化**    | Docker                                        | —          | 部署                 |
| **前端**     | Vue3 + Vant UI（H5）/ Element Plus（后台）          | —          | 计划中                |

---

## 三、微服务拆分

### 3.1 服务清单

| 服务                      | 职责                        | MVP 优先级 | 数据源           |
| ----------------------- | ------------------------- | ------- | ------------- |
| `gate-service`          | API 网关，路由转发，统一入口          | P0      | 无状态           |
| `authorization-service` | OAuth2 认证授权，登录注册，Token 签发 | P0      | Redis (token) |
| `user-service`          | 用户 CRUD，收货地址，签到           | P0      | MySQL         |
| `item-service`          | 商品 (SPU)、规格 (SKU)、类目管理    | P1      | MySQL         |
| `cart-service`          | 购物车                       | P1      | MySQL         |
| `trade-service`         | 订单管理                      | P1      | MySQL         |
| `pay-service`           | 支付处理，第三方支付对接              | P1      | MySQL         |
| `logistics-service`     | 物流信息管理                    | P1      | MySQL         |
| `search-service`        | 商品全文检索                    | P2      | ElasticSearch |
| `ai-service`            | AI客服、AI商品助手、智能搜索           | P2      | Milvus + LLM API |
| `ia-common`             | 共享库：异常、Result、工具、注解       | 基础模块    | —             |
| `ia-api`                | 共享 Feign 接口定义             | 基础模块    | —             |

### 3.2 服务关系图

```
                    ┌─────────────┐
                    │  Nginx/网关  │
                    └──────┬──────┘
                           │
                    ┌──────▼──────┐
                    │ gate-service │  ← API 网关 (Spring Cloud Gateway)
                    └──────┬──────┘
                           │
          ┌────────────────┼────────────────────┐
          │                │                    │
   ┌──────▼──────┐  ┌──────▼──────┐    ┌───────▼──────┐
   │authorization│  │ user-service│    │ item-service │
   │  -service   │  │             │    │              │
   └──────┬──────┘  └──────┬──────┘    └───────┬──────┘
          │                │                    │
          │           ┌────▼────┐         ┌─────▼──────┐
          │           │  cart   │         │  search    │
          │           │ -service│         │  -service  │
          │           └────┬────┘         └────────────┘
          │                │
          │           ┌────▼────┐
          │           │  trade  │
          │           │ -service│
          │           └────┬────┘
          │                │
          │     ┌──────────┼──────────┐
          │     │          │          │
          │  ┌──▼───┐ ┌───▼───┐ ┌───▼──────────┐
          │  │ pay  │ │  log  │ │notifications │
          │  │-serv │ │-serv  │ │  (future)    │
          │  └──────┘ └───────┘ └──────────────┘
          │
    ┌─────▼─────┐
    │   Nacos   │  ← 注册中心 + 配置中心
    └───────────┘
```

### 3.3 共享模块

**ia-common** 包含：

- 统一返回体 `Result<T>`
- 异常体系：`CommonException`, `BadRequestException`, `BizException`, `DBException`, `ForbiddenException`, `UnauthorizedException`
- 全局异常处理器 `GlobalExceptionHandler`（返回 `ResponseEntity<Result<Void>>`）
- `ErrorCode` 枚举（业务码 + HTTP 状态码映射）
- `@RateLimit` 限流注解 + AOP
- 通用工具：JWT 辅助、Redis Lua 脚本、SMS、Geetest 验证、用户上下文

**ia-api** 包含：

- 各服务间调用的 Feign 客户端接口定义
- 共享 DTO（避免每个服务重复定义）

---

## 四、分层架构（Alibaba 轻量化 DDD）

### 4.1 调用链路

```
Controller → Manager → Service(原子业务) → Domain → Mapper → Infra
```

**单向依赖，严禁反向调用。**

### 4.2 各层职责

| 层         | 包名            | 职责                                                        | 必选                       |
| --------- | ------------- | --------------------------------------------------------- | ------------------------ |
| **接入层**   | `controller`  | 请求入口，参数校验（`@Validated`），调用 Service/Manager，封装 `Result<T>` | ✅                        |
| **编排层**   | `manager`     | 流程编排、分布式锁、限流、幂等、多服务组合、跨服务调用聚合                             | ✅                        |
| **应用层**   | `application` | 薄业务层，调度领域能力，任务编排                                          | 🟡 可省略                   |
| **领域层**   | `domain`      | 领域枚举、领域服务（核心业务规则）、聚合根、值对象、领域事件                            | ✅（精简版仅需 service + enums） |
| **数据传输层** | `dto`         | `req`（入参）、`resp`（出参）、`feign`（内部调用 DTO）                    | ✅                        |
| **转换层**   | `convert`     | Entity ↔ DTO/VO 转换（MapStruct）                             | 🟡 可省略                   |
| **基础设施层** | `infra`       | Redis、分布式锁、Feign 客户端、MQ、第三方 SDK                           | ✅                        |
| **持久层**   | `mapper`      | MyBatis-Plus `BaseMapper` 接口                              | DB 服务必选                  |
| **实体层**   | `entity`      | 数据库 DO/PO（仅 Mapper 使用）                                    | DB 服务必选                  |
| **配置层**   | `config`      | Spring 配置、切面配置                                            | ✅                        |
| **异常层**   | `exception`   | 服务特有异常（优先用 ia-common 的异常）                                 | ✅                        |
| **公共层**   | `common`      | Result 包装、基础常量                                            | ✅                        |

### 4.3 精简版包结构（中小服务使用）

```
org.icedAmericanoMall
├── controller    ← 对外 REST + 对内 Feign 控制器
├── manager       ← 分布式逻辑收口（锁/限流/多服务编排）
├── domain
│   ├── dto       ← req/resp/feign DTO
│   ├── vo        ← 前端视图对象
│   ├── entity    ← 数据库 DO
│   └── enums     ← 业务枚举
├── mapper        ← MyBatis-Plus Mapper
├── convert       ← MapStruct 转换器
├── infra         ← Redis/Lock/Feign
├── config        ← 服务配置
├── exception     ← 服务异常
└── util          ← 工具类
```

### 4.4 分层铁律

1. **领域层不依赖基础设施/DB**
2. **数据库 DO 只允许在 mapper/infra 出现，禁止外泄到 Controller**
3. **外部入参、出参、微服务调用全部用 DTO 隔离**
4. **分布式问题（锁/限流/幂等）统一收口到 Manager**
5. **业务核心规则内聚到 Domain Service**
6. **Controller 绝不写业务逻辑、绝不直接操作 Redis/锁**

---

## 五、安全架构

### 5.1 认证体系

```
            ┌─────────────┐
            │  前端 (H5)   │
            └──────┬──────┘
                   │ POST /api/auth/login
            ┌──────▼──────────────┐
            │ authorization-service│
            │  OAuth2 Auth Server  │
            │  + JWT Token 签发    │
            └──────┬──────────────┘
                   │ JWT (HS256/RS256)
                   │ + Refresh Token (Redis)
            ┌──────▼──────┐
            │ gate-service │ ← Token 校验
            └──────┬──────┘
                   │ 用户上下文传递
            ┌──────▼──────────┐
            │  各业务微服务     │
            │  (Resource Server)│
            └─────────────────┘
```

### 5.2 登录架构

- **用户端**：`https://mall.icedamericano.com/login` → H5 应用
- **商家端**：`https://seller.icedamericano.com/login` → PC 后台应用
- **两套前端、同一套账号、统一认证后端**
- 商家端所有接口需校验 `seller` 角色，且只能操作自己店铺数据
- Token 包含角色信息，前端/网关根据角色判断权限

### 5.3 密钥管理

- RS256 非对称加密：私钥签发，公钥通过 JWKS 端点暴露（`/.well-known/jwks.json`）
- HS256 对称加密：共享密钥，用于内部服务间调用
- 支持多密钥切换（大厂做法：平滑滚动更新）

---

## 六、数据架构决策

| 决策   | 方案                           | 依据                  |
| ---- | ---------------------------- | ------------------- |
| 主键策略 | 技术主键（自增 ID）+ 业务标识（UUID）      | 自增 ID 性能好，UUID 对外友好 |
| 金额存储 | INT（单位：分）                    | 避免浮点精度问题            |
| 地址快照 | 下单时复制地址到订单表                  | 防止用户修改地址影响历史订单可追溯性  |
| 商品快照 | 下单时复制商品名、价格、图片到订单项           | 保证历史订单数据准确          |
| 逻辑删除 | `deleted` 字段 + `@TableLogic` | 数据可恢复               |
| 扩展字段 | `expand_json` (JSON 类型)      | 预留灵活性，避免频繁改表        |
| 对象隔离 | DO / DTO / VO 三层隔离           | 禁止将 DO 直接返回给前端      |

---

## 七、部署架构（演进路线）

### MVP 阶段（当前）

```
Docker 单机部署
├── Nginx (反向代理 + 静态资源)
├── MySQL (单实例)
├── Redis (单实例)
├── Nacos (单实例)
├── gate-service
├── authorization-service
├── user-service
├── ... (其他微服务)
└── (前端 H5 + 后台)
```

### 后续演进

- **V1.1**：MySQL 读写分离，Redis Cluster
- **V1.2**：Nacos 集群，服务多实例 + 负载均衡
- **V2.0**：K8s 容器编排，CI/CD 流水线，ElasticSearch 集群

---

## 八、关键设计原则

1. **去形容词化**：不用「高可用」「易维护」等空洞词，用技术方案和数据说话
2. **大厂对标**：分层架构对标阿里，代码风格对标字节
3. **不过度设计**：中小服务使用精简版分层，超大复杂模块才开完整 DDD
4. **内部接口隔离**：`/internal/**` 路径的 Feign 内部调用不包装 `Result`，直接抛异常给调用方处理
5. **配置外挂**：通过 Nacos 统一管理，配置变更无需重启
