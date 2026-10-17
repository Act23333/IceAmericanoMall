# 09 — 技术约束与反模式

> 本文档定义编码过程中必须遵守的硬性约束，以及常见错误模式的纠正方式。
> 编码前必读，Code Review 时逐条对照。
> **2026-10-18（B6 批次）**：§三·五 新增两条硬约束 —— 「**对外渠道号与内部业务键不得共用 / 混用**」与「**对外单号类用 `IdGenerator.randomHex`、凭据类用 `secureToken`、对外单号禁 UUIDv7**」（依据 [ADR-063](../adr/063-external-channel-number-separation.md)，代码 `256abbf3`）。
> 归属域: collaboration | Owner: doc-engineer | 更新触发条件: 新增硬约束时（须有 ADR 或主 agent 依据）

---

## 一、技术栈版本锁定

| 组件 | 锁定版本 | 升级约束 |
|------|---------|---------|
| Java | **21** | 只能使用 LTS 版本，升级需评估所有依赖兼容性 |
| Spring Boot | **3.5.4** | 由父 POM 统一管理，子模块不得覆盖 |
| Spring Cloud | **2025.0.1** | 与 Spring Boot 版本绑定 |
| Spring Cloud Alibaba | **2025.0.0.0** | 与 Spring Cloud 版本绑定；含 Sentinel、Nacos |
| MyBatis-Plus | **3.5.11** | 父 POM dependencyManagement 管理，子模块不加版本 |
| MySQL Connector | **9.3.0** | |
| Hutool | **5.8.43** | 仅使用 `cn.hutool` 包，禁止引入 hutool-v2 |
| MapStruct | **1.5.5.Final** | Lombok 必须在 processor path 中先于 MapStruct |
| Lombok | **1.18.42** | scope=provided，子模块需显式声明 |
| JJWT | **0.13.0** | |
| Redisson | **3.26.0** | 分布式锁能力**已归并 ia-common 共享内核**（`common/lock/DistributedLock` + `RedissonDistributedLockAdapter`，`@ConditionalOnClass` 条件装配；[ADR-024](../adr/024-distributed-lock-consolidation-in-ia-common.md)）——调用方 authorization-service（登录/建档）、user-service（幂等建档）按业务键串行查建；服务内重复副本已删，禁止再建副本 |
| 微信支付 SDK | **0.2.17** | wechatpay-java，封装在 `ia-integration` payment 模块 |
| 阿里云短信 SDK | **3.1.0** | dysmsapi20170525，封装在 `ia-integration` sms 模块 |
| ElasticSearch | **7.17.25** | search-service 双实现（ES + MySQL fallback），opt-in via `search.elasticsearch.enabled` |

**子模块版本原则**：所有版本号在父 POM `<dependencyManagement>` 中统一管理，子模块只声明 groupId:artifactId，不加 version。

### 计划引入的技术版本（V1.1+）

| 组件 | 预计版本 | 引入时机 | 替换目标 |
|------|---------|---------|---------|
| Sentinel | 与 SCA 绑定 | V1.1 | 补充 `@RateLimit`，增加熔断降级 —— **trade-service 已落地**（`spring.cloud.sentinel.enabled` 激活键 + `SentinelConfig` 规则注册，[ADR-028](../adr/028-trade-sentinel-wiring-over-resilience4j.md)）；其余服务走 resilience4j Feign 熔断（ADR-022） |
| Seata | 2.x | V1.1 | 替换手动 Saga 补偿 |
| XXL-Job | 2.4+ | V1.1 | 替换 `@Scheduled` 定时任务 |
| SkyWalking | 9.x | V1.1 | 新增，无替换 |
| RabbitMQ | 3.13+ | V1.1 | 新增，领域事件异步化 |
| Canal | 1.1+ | V1.2 | 新增，MySQL→ES/Redis 同步 |
| ShardingSphere | 5.x | V2.0 | 分库分表（触发条件：单表 > 500 万行） |

---

## 二、架构硬约束

### 2.1 分层依赖方向（单向，不可逆）

```
Controller → Manager → Service → Domain(Service) → Mapper → DB
                       ↓
                      Infra (Redis/Feign/MQ)
```

**违反此方向的实例**：

| 禁止模式 | 原因 |
|----------|------|
| Controller 直接调 Mapper | 绕过业务逻辑，安全/事务/校验全丢 |
| Service 调 Controller | 循环依赖 + 职责混乱 |
| Domain Service 调 Mapper | 违反 DDD：领域层不应依赖基础设施 |
| Service 直接调 Redis | 缓存逻辑散落，难以统一管理 → 应通过 Manager |
| Mapper 返回 DTO | Mapper 只应操作 Entity (DO) |

### 2.2 模块依赖约束

```
ia-common      ← 所有模块（单向依赖，ia-common 不依赖任何业务模块）
ia-api         ← 服务间 Feign 调用（ia-api 只依赖 ia-common）
ia-integration ← 需要第三方集成的服务（第三方 SDK 收口；只依赖 ia-common）
```

- `ia-common` **不得**依赖任何业务服务模块
- `ia-api` **只**包含 Feign 接口 + Feign DTO，**不得**包含业务逻辑
- 服务 A **不得**直接依赖服务 B 的模块，只能通过 `ia-api` 的 Feign 接口调用

### 2.3 包名与 Bean 注册约束（V5.2）

- 全模块统一基础包 `org.icedamericanomall`；服务实现必须按服务分段：`org.icedamericanomall.<service>`（user/item/cart/trade/pay/logistics/search/ai/marketing/gateway/auth/notification/verification/wallet/merchant）
- 共享模块专用段：`ia-common` = `org.icedamericanomall.common`、`ia-integration` = `org.icedamericanomall.integration.*`、`ia-api` = `org.icedamericanomall.{client,config,dto,fallback}`
- **禁止**在任何模块内创建与其他服务段重名的子包（如非 auth 模块出现 `auth/`——V5.1 迁移期曾因此产生拆分包冲突）
- 服务启动类必须声明显式 `scanBasePackages`（本服务段 + 实际用到的 `org.icedamericanomall.fallback` / `integration` 子包；不要扫 `org.icedamericanomall.config`，ia-api 的 `DefaultFeignConfig` 仅由需要的服务显式 import）
- ia-integration 第三方客户端注册"**AutoConfiguration.imports 或组件扫描二选一**"：同一组 Bean 只允许一个来源；imports 条目的 FQCN 必须与真实类路径逐字一致（大小写敏感，jar/Docker 形态下错误条目会启动失败）
- **审计字段填充器（`MetaObjectHandler`）已归并 ia-common 共享内核唯一实现**：`AuditFieldMetaObjectHandler` 经 `MyMetaObjectHandlerAutoConfiguration` 自动装配（`@ConditionalOnClass` 守卫 + `@ConditionalOnMissingBean(MetaObjectHandler.class)` 退避，注册 `AutoConfiguration.imports`；[ADR-030](../adr/030-metaobjecthandler-consolidation-autoconfig.md)）——cart/item/logistics/merchant/pay/search/trade 七份服务内副本已删，**新服务禁止自建填充器副本，除非有特有字段**（user-service 例外：额外填充 `registerTime`，保留自有实现并经退避共存）

### 2.4 adapter 层与契约对象约束（V5.3，[ADR-023](../adr/023-adapter-layer-structure-and-request-object-naming.md)）

- **adapter 子包仅六类**：`web` / `admin` / `internal`（按受众分组）+ `request` / `response` / `assembler`——禁止按聚合/服务名分子包（如 `adapter/seller/`），禁止控制器直接平铺在 `adapter/` 根下（转换器包 2026-10 由 `convert` 更名 `assembler`，见 ADR-038）
- **入参对象必须为类型化 `XxxReq`**（`@Validated` + 字段校验注解，`adapter/request/`）；**禁止 `@RequestBody Map<String,Object>` 与裸标量 body**（字段拼错不报错、无类型与校验约束）
- **VO 不得暴露技术字段**：`version` / `cipher` / `hash` / 内部状态列等禁止出参；出参一律为 `adapter/response/` 下强类型 `XxxVO`（面向展示裁剪）

---

## 三、反模式速查

### 3.1 Controller 反模式

```java
// ❌ 反模式 1: Controller 中写业务逻辑
@PostMapping("/order")
public Result<OrderVO> createOrder(@RequestBody OrderReq req) {
    // 这些逻辑应该在 Service/Manager 中
    if (req.getAmount() <= 0) throw new BizException("金额无效");
    SkuEntity sku = skuMapper.selectById(req.getSkuId());   // Controller 直接调 Mapper!
    if (sku.getStock() < req.getQuantity()) { ... }
    // ...
}

// ✅ 正确
@PostMapping("/order")
public Result<OrderVO> createOrder(@Validated @RequestBody OrderReq req) {
    return Result.success(orderManager.createOrder(req));
}
```

```java
// ❌ 反模式 2: Controller 直接操作 Redis
@GetMapping("/user/{id}")
public Result<UserVO> getUser(@PathVariable Long id) {
    String cache = redisTemplate.opsForValue().get("user:" + id);  // ❌
    if (cache != null) return Result.success(JSON.parseObject(cache, UserVO.class));
    // ...
}

// ✅ 正确：缓存逻辑放在 Manager 或 Infra 层
```

```java
// ❌ 反模式 3: 返回 Entity
@GetMapping("/{id}")
public Result<UserEntity> getUser(@PathVariable Long id) {  // ❌ 暴露 DO
    return Result.success(userService.getById(id));
}

// ✅ 正确
@GetMapping("/{id}")
public Result<UserVO> getUser(@PathVariable Long id) {
    return Result.success(userConverter.toVO(userService.getById(id)));
}
```

### 3.2 Service 反模式

```java
// ❌ 反模式 4: Service 参数或返回值使用 Entity
public UserEntity register(UserEntity entity) { ... }          // ❌
public OrderEntity createOrder(OrderEntity entity) { ... }     // ❌

// ✅ 正确：使用 DTO
public UserDTO register(RegisterReq req) { ... }
```

```java
// ❌ 反模式 5: Service 直接调用 Feign
@Service
public class OrderServiceImpl {
    private final UserClient userClient;  // ❌ Service 不应直接调 Feign
    public OrderDTO create(OrderReq req) {
        UserDTO user = userClient.getUser(req.getUserId());  // ❌
    }
}

// ✅ 正确：Feign 调用放在 Manager 层
```

```java
// ❌ 反模式 6: 大方法
public OrderDTO createOrder(OrderReq req) {
    // 200 行逻辑...
    // 库存检查 + 价格计算 + 优惠券 + 积分 + 创建订单 + 发送通知 + 记录日志...
}

// ✅ 正确：拆分为多个私有方法，每个方法单一职责
public OrderDTO createOrder(OrderReq req) {
    validateStock(req);
    AmountDTO amount = calculateAmount(req);
    OrderDTO order = insertOrder(req, amount);
    return order;
}
```

### 3.3 通用反模式

```java
// ❌ 反模式 7: 吞异常
try {
    smsService.send(phone, code);
} catch (Exception e) {
    e.printStackTrace();  // 既不处理也不上报
}

// ✅ 正确：要么处理，要么向上抛（让 GlobalExceptionHandler 处理）
try {
    smsService.send(phone, code);
} catch (SmsException e) {
    log.error("短信发送失败 phone={}", phone, e);
    throw new BizException(ErrorCode.SMS_SEND_FAILED, e);
}
```

```java
// ❌ 反模式 8: 裸异常
throw new RuntimeException("用户不存在");

// ✅ 正确：使用 ia-common 异常体系
throw new BadRequestException(ErrorCode.USER_NOT_FOUND);
```

```java
// ❌ 反模式 9: 金额用浮点
BigDecimal price = new BigDecimal("12.50");   // ❌
double total = price * quantity;               // ❌

// ✅ 正确：整数（分）
Integer price = 1250;  // 12.50 元 = 1250 分
Integer total = price * quantity;
```

```java
// ❌ 反模式 10: 硬编码魔法值
if (order.getStatus() == 2) { ... }           // 2 是什么？
redisTemplate.expire("token:" + token, 1800, TimeUnit.SECONDS); // 1800 是什么？

// ✅ 正确：使用常量/枚举
if (order.getStatus() == OrderStatusEnum.PAID.getCode()) { ... }
redisTemplate.expire(TokenConstants.ACCESS_TOKEN_KEY + token,
                     TokenConstants.ACCESS_TOKEN_TTL_SECONDS,
                     TimeUnit.SECONDS);
```

---

## 三·五、业务标识（ID）约束（ADR-050，2026-10-08）

| 规则 | 说明 |
|------|------|
| 生成唯一出口 | 业务 ID 一律 `IdGenerator.nextId()`（UUIDv7，32 位小写 hex）；**禁止**业务代码散落 `UUID.randomUUID()` / `IdUtil.fastSimpleUUID()`；凭据（refreshToken / JWT jti）一律 `IdGenerator.secureToken()`（256 位熵，禁止用时间有序 v7 生成凭据） |
| PO 边界形态 | 业务标识列 `BINARY(16)`，PO 字段 `byte[]`（MyBatis 默认 ByteArrayTypeHandler）；**禁止**为业务 ID 引入自定义 TypeHandler / `autoResultMap` / String PO 路线——包装器查询参数按运行时类型解析，会**绕过** `@TableField(typeHandler)` 导致 String 对 BINARY 列**静默查空** |
| 查询参数 | 包装器 `eq/in/ne` 与显式 SQL 参数对 BINARY(16) 列必须传 `IdGenerator.toBytes(...)`；**外部输入**（URL path、渠道回调单号）必须用 `toBytesOrNull(...)`（解析失败落空 = 未命中语义），漏传 = 静默查空，**评审必查项** |
| 保留列 | 管理端输入分组（`stack_group`）、复合会话 ID（`chat_message.conversation_id`）、枚举/语义编码（`notification.type`/`identity_type`/task code）、复合/外部格式（`biz_ref`/`biz_no`/`channel_*`/`embedding_id`）保持 VARCHAR——新表同类列沿用，例外登记进 ADR-050 清单；**券业务码（`coupon_id`）创建端必须归一**：省略→系统生成 UUIDv7 / 提供→须 32-hex 或 36-hyphen（归一 32-hex 落库），可读编码 fail-fast（ADR-050 修订） |
| 新表默认 | 新表业务标识列默认 `BINARY(16)` + `IdGenerator.nextId()`；运维 SQL 需 `HEX(col)` / `UNHEX('...')` 包裹；种子数据业务 ID 用 32-hex 定值 + `UNHEX()` |
| 验证码场景隔离 | 验证码 issue/verify 的 `scene` **必填且两侧一致**（REGISTER / LOGIN / RESET_PASSWORD，ADR-051）：场景参与 Redis Key（`sms:code:{scene}:{target}`）与重发限流/失败计数隔离，跨场景验证码互不可用；scene 编码契约 `[A-Z][A-Z0-9_]{0,31}`（非法即 PARAM_INVALID）；目标**全局日配额** `sms:daily:{target}`（跨场景共享，默认 10 条/日，ADR-051 修订）；注册接口对「已注册（带密码凭证）」手机号**前置快拒绝**（不消费验证码，`PHONE_ALREADY_REGISTERED`） |
| 双表示禁直连 | 复合/多格式 VARCHAR 列仍承载业务 ID 的 hex 字符串形态（如 `wallet_transaction.biz_no` 内含订单号/退款单号；`refund_order.biz_ref` 内嵌 `GB:{orderNo}`）——与对应 `BINARY(16)` 列**跨表 SQL JOIN/比较会静默不匹配**；需要跨表示匹配时，在同一表示层进行（应用层 String，或 SQL 内显式 `HEX()`/`UNHEX()` 转换，ADR-050；`transaction_no` 已转 BINARY，不再属此列） |
| **对外渠道号与内部业务键不得共用 / 混用**（[ADR-063](../adr/063-external-channel-number-separation.md)，2026-10-18） | **对外渠道号**（`pay_order.channel_trade_no` / `refund_order.out_refund_no`）与**内部业务键**（`orders.order_no` / `pay_order.pay_order_no` / `refund_order.refund_no`）是**两类标识**：前者进渠道协议、可被渠道/对账单/客服工单反复外传，后者只在本服务内定位。硬约束：① **一列一语义**，不得让同一列既载「我方发出的号」又载「渠道回填的号」（`channel_refund_no` = 渠道 `refund_id`，**不承载**对外退款项号）；② **发起 / 回调 / 对账一律按渠道号唯一寻址**，❌ **不得保留「按内部键兜底查」的第二口径**（第二口径让双键混用复发，接缝处必然再次分叉）；③ 退款 API 的 `out_trade_no` = **原交易的对外渠道号**，不是 `pay_order_no`；④ 对账「渠道有本地无」的明细 **不伪造**本地单号（`pay_order_no` 为 NULL）。**新增资金链路用例必须断言「发起侧外发值 = 渠道号 ≠ 内部键」与「回调携带内部键必须查不到」**——缺陷前形态正是测试按错误键喂数据再按错误键放行 |
| **对外单号类与凭据类的生成入口分离**（[ADR-063](../adr/063-external-channel-number-separation.md)，2026-10-18） | `IdGenerator` 三入口**按用途锁定**：内部业务键 → `nextId()`（UUIDv7，有时间序）；**凭据 → `secureToken()`**（256 bit，refreshToken / JWT jti）；**对外渠道单号 → `randomHex(len)`**（长度可控的纯随机，`out_trade_no`/`out_refund_no` 取 20 位 = 80 bit）。**❌ 对外单号禁用 UUIDv7**：① 前 48 位是毫秒时间戳，对外号会流经渠道/对账文件/工单，可**反推下单时刻做流量画像**；② v7 固定 32-hex，与微信 `out_trade_no` 上限 32 **零余量**（任何前缀/拼接即超限，见 `PaymentClient.MAX_OUT_TRADE_NO_LENGTH`）。凭据与对外单号也**不得共用一个入口**（凭据缩短即降熵、单号取 64 位即超渠道上限） |
| 编解码异常口径 | 业务错误一律 ia-common 异常；**编解码不变量违例**（如 `IdGenerator.toBytes` 收到非 hex 输入、`toHexString` 收到非 16 字节）属编程错误，用 `IllegalArgumentException` **fail-fast**（仓储/PO 映射路径的入参在领域内必然合法，出现即缺陷）；外部输入查询入口用 `toBytesOrNull` 落空为「未命中」，不得放大为异常 |
| ID 不变量 CI 门禁 | `bash Implementation/back-end/scripts/check-id-invariants.sh`（UUID 生成唯一出口 / 包装器 byte[] 参数 / SQL 字面量禁令，ADR-050/051）——CI 编译前强制运行，本地提交前应复跑；修改 `product`/`sku` 表结构或 item 检索端点契约（`/internal/item/search/**`）时必须联动评审 search-service 消费面（ADR-052：直读已于 2026-10-14 退出，schema 联动义务转为端点契约评审） |

---

## 四、安全约束

| 规则 | 说明 |
|------|------|
| 密码加密 | 必须使用 BCrypt，禁止 MD5/SHA 直接哈希（`PasswordEncoder.encode()`） |
| SQL 注入防护 | 使用 MyBatis-Plus `LambdaQueryWrapper`，禁止字符串拼接 SQL |
| 敏感信息脱敏 | 日志中禁止打印密码/Token/手机号完整值 |
| 内部接口隔离 | `/internal/**` 路径仅限集群内调用，Gateway 必须拦截外部请求 |
| 越权防护 | 商家只能操作自己的商品/订单；用户只能操作自己的数据 |
| CORS | 跨域配置统一在 gateway-service，业务服务不配置 CORS |
| 限流 | 发短信、登录、注册 等接口必须加 `@RateLimit`；限流键含方法维度（`rate:limit:{类.方法}:{业务键}`，V5.3 修正），超限返回 `FREQUENT_ERROR`(429) |
| 验证码存储（verification-service 私有） | `sms:code:{scene}:{target}`(TTL 5min，**场景隔离**，ADR-051)、`sms:limit:{scene}:`、`email:code:{scene}:` 等同构键与失败计数为 verification-service **服务私有 Key 空间**，仅该服务读写；签发/校验/消费一律经 `/internal/verification/**` 在线完成（fail-closed）。原「三端同步」要求已随边界重构解除（守护测试 `verification-service/.../VerificationContractTest` 随服务保留） |
| 服务间状态传递 | ❌ 禁止以共享 Redis Key 作为跨服务契约（原 `sms:code:` 跨服务契约已解除）；跨服务一律走 Feign 在线调用，各服务 Key 空间互不直读。**唯一例外：显式豁免契约键（仅限「auth 写、网关只读」）**——`user:info:{userId}` / `user:perms:{userId}`（ADR-018）、`auth:blacklist:{jti}`（[ADR-027](../adr/027-jwt-blacklist-writer-and-contract-key.md)，TTL=max(access token 剩余有效期, 1s)）与 `auth:tokenver:{userId}`（[ADR-031](../adr/031-multi-granularity-token-revocation.md)，**不设 TTL**——TTL 过期会复活已吊销令牌）；第三方服务不得读写，改名或改 TTL 语义必须两侧同步发布 |
| 校验降级 fail-closed | verification-service 不可用时，验证码/人机验证一律拒绝放行（Feign fallback 返回失败），禁止降级为"跳过校验"；Feign 熔断已启用（resilience4j），TimeLimiter 不得低于 5s |
| 审计日志 PII | `@OperationLog` 审计**默认不记参数值**：`recordParams=true` 仅限「路径/查询参数且只含标识/状态」的端点，输出「类型 + id 摘要」且截断 200 字符；失败路径仅记异常类名（不含 message）；审计落库（user 域 `operation_log`）走**显式业务调用**，注解只写日志不落库（[ADR-032](../adr/032-operation-audit-dual-pipeline.md)） |

---

## 五、数据约束

| 规则 | 说明 |
|------|------|
| 金额存储 | `INT`，单位「分」。计算时用 `int` 乘除，禁止用 `float/double/BigDecimal` |
| 金额不变式 V2 | `payAmount = totalAmount − discountAmount + postFee`（`totalAmount` 仍 = Σ `order_item.subTotal`，**运费不进商品小计**、单列 `orders.post_fee` 下单快照；秒杀单 `postFee = 0`；运费模板查询 I43 **fail-closed**，故障不得降级为免邮，[ADR-037](../adr/037-freight-model-package-split-and-refund-trigger.md)） |
| 主键对外 | API 返回业务标识（`userId`, `orderNo`），不暴露技术主键 `id` |
| 地址/商品快照 | 下单时必须复制到订单表，不引用原表。防止用户/商家修改后历史数据变更 |
| 软删除 | 需要可恢复的数据使用 `@TableLogic`，硬删除需评审 |
| 字段默认值 | 数据库层面设置 DEFAULT，禁止仅在后端代码中赋默认值 |
| 事务回滚 | `@Transactional(rollbackFor = Exception.class)`，必须指定 rollbackFor |

---

## 六、范围边界（2026-10 边界重构后基线）

**已交付基线**：完整交易闭环（注册→浏览→购物车→下单→支付→发货→收货）+ V1.x–V4.x 扩展（优惠券/秒杀/积分账本+签到/钱包余额/站内信/人工 IM+AI 客服/商家知识库/评价/收藏/浏览历史/售后/结算/入驻审核/ES 搜索/首页装修/RBAC）。

**未实现（启动前须评审补规格）**：

- 拼团/团购、发票（渠道对账已于 V5.5 落地——四态差异只记录不调账，[ADR-036](../adr/036-channel-reconciliation-record-only-and-rewrite-idempotency.md)，移出本清单）
- 小程序/App 端
- 知识图谱（NebulaGraph，V3.x 前瞻）
- 多币种

**原则**：先保证「注册→浏览商品→加入购物车→下单→支付→商家发货→用户确认收货」闭环及其已交付扩展的稳定性；新增功能先过 05 契约与 ADR 流程。

---

## 七、Java 编码约束

| 规则 | 说明 |
|------|------|
| 空值处理 | 禁止用 `if (x != null)` 散布，优先用 `Optional`、`ObjectUtils`、提前 return |
| 字符串拼接 | 多段拼接用 `String.format()` 或 `StringBuilder`，禁止循环内用 `+` |
| 集合操作 | 优先用 Stream API + 方法引用，避免 for-each 嵌套 |
| 日期处理 | 使用 `java.time.*`（`LocalDateTime`, `Instant`），禁止 `java.util.Date`/`SimpleDateFormat` |
| 资源关闭 | 使用 try-with-resources，禁止手动 close/finally |
| 序列化 | DTO/VO 必须实现 `Serializable`，显式声明 `serialVersionUID` |
| Lombok | Entity 用 `@Data` + `@TableName`；DTO/VO 用 `@Data`；Config 用 `@ConfigurationProperties` |
| 记录日志 | 用 `@Slf4j` + `log.info/error`，禁止 `System.out.println` |

---

## 八、依赖引入约束

**服务模块允许引入的依赖**：
- `ia-common`（必须）
- `ia-api`（需要 Feign 调用其他服务时）
- `spring-boot-starter-web`
- `mybatis-plus-spring-boot3-starter`
- `mysql-connector-j`
- `lombok` (scope=provided)
- `mapstruct` + `mapstruct-processor`
- `knife4j-openapi3-jakarta-spring-boot-starter`
- `spring-cloud-starter-alibaba-nacos-discovery`
- 服务特有的基础设施依赖（如 `redisson`）

**不允许引入的依赖**：
- 其他服务的模块 jar（如 user-service 不能依赖 item-service 的 jar）
- 未经父 POM 版本管理的第三方库（需先评审）
- 已废弃的库（如 `springfox-swagger` → 已由 Knife4j 替代）
- 与 ia-common 已提供功能重复的库

---

## 九、分布式事务约束（当前：手动 Saga / 未来：Seata）

| 规则 | 说明 |
|------|------|
| 补偿必须幂等 | 库存回滚 `restoreStock()` 使用 `stock = stock + quantity` 而非 `stock = oldValue`，防止重复补偿 |
| 补偿链完整 | 下单涉及 N 步远程调用，每一步失败必须有对应的补偿操作，不能只补偿第一步 |
| 事务边界明确 | 跨服务调用不在同一本地事务中 —— 先远程扣库存，再本地创订单；本地失败立即远程补偿 |
| Seata 引入后 | AT 模式用于支付回调（强一致）；TCC 模式用于库存扣减（二阶段）；Saga 模式用于下单全链路（长事务） |
| 禁止 XA | 不使用 JTA/XA 分布式事务（性能差，MySQL 锁粒度大） |

## 十、定时任务约束（当前：@Scheduled / 未来：XXL-Job）

| 规则 | 说明 |
|------|------|
| 禁止单机 @Scheduled 做关键业务 | `PayTimeoutJob`、`OrderTimeoutJob` 等涉及钱/库存的定时任务，在多实例下会重复执行。MVP 单机可接受，V1.1 必须迁移至 XXL-Job |
| 分片策略 | 多实例时按 `order_id % instanceCount` 分片，避免同一订单被多个实例处理 |
| 故障转移 | XXL-Job 故障转移：执行器宕机 → 调度中心自动路由到备用执行器 |
| 幂等设计 | 定时任务处理函数必须幂等（可能重复触发），使用状态机 + 乐观锁保证 |

## 十一、链路追踪约束（未来：SkyWalking）

| 规则 | 说明 |
|------|------|
| TraceId 传递 | Feign 拦截器自动传递 TraceId（`X-Trace-Id` header）；自己 `new Thread()` 的场景必须手动传递 |
| 日志包含 TraceId | 所有 `log.info/error` 格式中包含 `[traceId:%s]`，方便 ELK 串联 |
| 不追踪健康检查 | `/actuator/health`、`/health` 等端点排除在追踪之外，避免噪音 |
| 敏感数据不上报 | Span Tag 中不得包含手机号、密码、Token 等 PII |

## 十二、消息队列约束（计划 V1.1：RabbitMQ）

| 规则 | 说明 |
|------|------|
| 消息必达 | 核心事件（订单创建/支付成功）使用 Publisher Confirm + 持久化队列，保证不丢消息 |
| 消费幂等 | 消费者必须幂等（可能重复投递），使用 `bizOrderNo + eventType` 做去重 |
| 顺序性 | 同一订单的事件必须顺序消费（通过 Routing Key 路由到同一 Queue） |
| 死信队列 | 消费失败 N 次后进入死信队列，人工介入处理 |
