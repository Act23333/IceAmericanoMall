# 09 — 技术约束与反模式

> 本文档定义编码过程中必须遵守的硬性约束，以及常见错误模式的纠正方式。
> 编码前必读，Code Review 时逐条对照。

---

## 一、技术栈版本锁定

| 组件 | 锁定版本 | 升级约束 |
|------|---------|---------|
| Java | **21** | 只能使用 LTS 版本，升级需评估所有依赖兼容性 |
| Spring Boot | **3.5.4** | 由父 POM 统一管理，子模块不得覆盖 |
| Spring Cloud | **2025.0.1** | 与 Spring Boot 版本绑定 |
| Spring Cloud Alibaba | **2025.0.0.0** | 与 Spring Cloud 版本绑定 |
| MyBatis-Plus | **3.5.11** | 父 POM dependencyManagement 管理，子模块不加版本 |
| MySQL Connector | **9.3.0** | |
| Hutool | **5.8.43** | 仅使用 `cn.hutool` 包，禁止引入 hutool-v2 |
| MapStruct | **1.5.5.Final** | Lombok 必须在 processor path 中先于 MapStruct |
| Lombok | **1.18.42** | scope=provided，子模块需显式声明 |
| JJWT | **0.13.0** | |

**子模块版本原则**：所有版本号在父 POM `<dependencyManagement>` 中统一管理，子模块只声明 groupId:artifactId，不加 version。

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
ia-common  ←  所有服务（单向依赖，ia-common 不依赖任何服务）
ia-api     ←  服务间 Feign 调用（ia-api 只依赖 ia-common）
```

- `ia-common` **不得**依赖任何业务服务模块
- `ia-api` **只**包含 Feign 接口 + Feign DTO，**不得**包含业务逻辑
- 服务 A **不得**直接依赖服务 B 的模块，只能通过 `ia-api` 的 Feign 接口调用

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

## 四、安全约束

| 规则 | 说明 |
|------|------|
| 密码加密 | 必须使用 BCrypt，禁止 MD5/SHA 直接哈希（`PasswordEncoder.encode()`） |
| SQL 注入防护 | 使用 MyBatis-Plus `LambdaQueryWrapper`，禁止字符串拼接 SQL |
| 敏感信息脱敏 | 日志中禁止打印密码/Token/手机号完整值 |
| 内部接口隔离 | `/internal/**` 路径仅限集群内调用，Gateway 必须拦截外部请求 |
| 越权防护 | 商家只能操作自己的商品/订单；用户只能操作自己的数据 |
| CORS | 跨域配置统一在 gate-service，业务服务不配置 CORS |
| 限流 | 发短信、登录、注册 等接口必须加 `@RateLimit` |

---

## 五、数据约束

| 规则 | 说明 |
|------|------|
| 金额存储 | `INT`，单位「分」。计算时用 `int` 乘除，禁止用 `float/double/BigDecimal` |
| 主键对外 | API 返回业务标识（`userId`, `orderNo`），不暴露技术主键 `id` |
| 地址/商品快照 | 下单时必须复制到订单表，不引用原表。防止用户/商家修改后历史数据变更 |
| 软删除 | 需要可恢复的数据使用 `@TableLogic`，硬删除需评审 |
| 字段默认值 | 数据库层面设置 DEFAULT，禁止仅在后端代码中赋默认值 |
| 事务回滚 | `@Transactional(rollbackFor = Exception.class)`，必须指定 rollbackFor |

---

## 六、MVP 范围约束

**MVP 可以做的**：
- user, address, seller, category, product, sku, order, order_item, cart, pay_order, order_logistics 相关功能
- 手机号+验证码注册/登录、密码登录
- 一级商品分类、商品列表/详情
- 购物车 CRUD
- 下单支付（微信支付）
- 商家后台（商品/订单管理、发货）
- 管理员后台（查看/管理）

**MVP 不可以做的**（留给后续迭代）：
- 优惠券、秒杀、拼团、积分（签到除外）
- 首页装修、广告位
- 在线客服、售后工单
- 对账、结算、发票
- 小程序/App
- 多级分类（MVP 仅一级）
- 全文搜索（ElasticSearch）
- 复杂营销活动

**原则**：MVP 只做能跑通「注册→浏览商品→加入购物车→下单→支付→商家发货→用户确认收货」完整闭环的最小功能集。

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
