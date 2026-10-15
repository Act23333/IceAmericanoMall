# 08 — AI Agent 开发指令

> 本文档定义 AI 编码助手（Claude Code 等）在冰美商城项目中的行为规范。
> 所有代码生成必须遵循本文档 + 项目文档体系。
> 最后更新: 2026-10-14（新增 §十 各平台编码规范：五平台命名与惯用法 —— 防 React 写法被搬进 SwiftUI/Compose；§一–§九 后端分层/命名/约束模板未变）
> 归属域: collaboration | Owner: doc-engineer | 更新触发条件: 分层/命名/约束模板变化时（含新增平台）

---

## 本文在规范体系中的位置（双层模型，见 ADR-053）

本文是**项目层**的权威 —— 本项目实际怎么分层、怎么命名、怎么测试、什么绝对不能做。**方法论层**是各角色的 skill 定义（DDD 原则与「为什么」，跨项目可复用）。两者不是竞争关系，是不同抽象层级。

**冲突裁决**：

1. 本文若是对方法论层的**有意收窄 / 特化** → **本文生效**；
2. 本文若与方法论层的**原则相悖**（是矛盾而非收窄）→ 视为**缺陷**，报出并交主 agent 分派裁决，**不许就地二选一**；
3. 本文**未规定**的事项 → 回落到方法论层。

**去重要求**：本文只写项目事实与收窄，不重述方法论；角色 skill 不复制本文已有的具体内容。两侧各只写一份。

> 实例：app 层禁用 `CmdExe` / `QryExe`（本文 §3.2 + [ADR-025](../adr/025-domain-aggregate-packaging-and-app-usecase-granularity.md)）属第 1 类的**项目收窄** —— 角色 skill 必须让位于本文，而不应各自表述。

---

## 〇、文档-代码一致性规则（最高优先级）

**项目文档（`project-docs/`）是唯一可信的规格来源。代码实现必须与文档一致。**

```
文档已定稿 + 符合规范 → 以文档为准生成代码，有疑问提出来讨论
文档自相矛盾/待定     → 先讨论确认再动手
文档过时/与代码矛盾   → 先修正文档，再改代码
代码有但文档无        → 代码是例外，需补文档
文档有但代码无        → 代码缺失，需补代码
```

**判断流程**：
1. 读文档 → 确认规格
2. 看代码 → 是否与规格一致？
3. 一致 → 直接开发
4. 不一致 → 判断哪边正确 → 修正错误的一方 → 再开发
5. 不确定 → 提出来讨论

---

## 一、文档查阅规则

开始任何编码任务前，必须先查阅对应文档：

| 任务类型      | 必读文档                           | 补充文档                 |
| --------- | ------------------------------ | -------------------- |
| 创建新微服务    | 02-Architecture, 04-Data-Model | 06-Workflow §3       |
| 实现 API 端点 | 05-API-Specification           | 01-PRD (验收场景)        |
| 编写数据访问    | 04-Data-Model                  | 03-Domain-Model      |
| 编写测试      | 05-API-Specification (BDD 契约)  | 07-Tasks (验收场景)      |
| 重构代码      | 02-Architecture §4 (分层铁律)      | 09-Constraints       |
| Bug 修复    | 10-Review (排查清单)               | 09-Constraints (反模式) |
| 新增业务实体    | 03-Domain-Model                | 04-Data-Model        |

---

## 二、编码流程（强制执行）

```
1. 查阅对应项目文档，确认业务规则和边界
2. 确认 API 规格已定义（若无，先写）
3. 编写 BDD 验收场景（Given-When-Then）
4. 编写失败测试 (TDD Red)
5. 最小实现使测试通过 (TDD Green)
6. 重构优化 (TDD Refactor)
7. 对照 09-Constraints 检查是否触犯反模式
8. 对照 10-Review 自检
```

---

## 三、分层编码规则

### 3.1 Controller 层

```java
// 模板：每个 Controller 方法必须遵循此结构
@RestController
@RequestMapping("/api/{resource}")
@RequiredArgsConstructor
@Tag(name = "资源名称")  // Knife4j 分组
public class XxxController {
    private final XxxService xxxService;
    // 若涉及编排/锁/限流 → 注入 XxxManager
    // ❌ 绝不注入 Mapper
    // ❌ 绝不注入 RedisTemplate

    @GetMapping("/{id}")
    public Result<XxxVO> getXxx(@PathVariable Long id) {
        // 1. 参数校验（简单校验可在此，复杂校验在 DTO 用 @Validated）
        // 2. 调用 Service/Manager
        // 3. 用 Converter 将 DTO → VO
        // 4. 返回 Result.success(vo)
        return Result.success(converter.toVO(service.getById(id)));
    }

    @PostMapping
    public Result<Long> createXxx(@Validated(CreateGroup.class) @RequestBody XxxReq req) {
        return Result.success(service.create(req));
    }
}
```

**禁止清单**：

- 写任何业务判断（`if (balance < price)` 应当在 Service/Manager）
- 直接操作 Redis / 分布式锁
- 直接调用 Mapper
- 返回 Entity (DO)
- catch 异常后吞掉

### 3.2 Manager 层（编排层）

```java
// 用途：分布式逻辑收口
@Component  // 或 @Service
@RequiredArgsConstructor
public class XxxManager {
    private final XxxService xxxService;
    private final YyyClient yyyClient;    // Feign
    private final RedissonClient redisson; // 分布式锁
    // ✅ 可注入 RedisTemplate、RateLimiter

    // 多服务编排 + 分布式锁示例
    public OrderDTO createOrderWithLock(Long userId, OrderReq req) {
        RLock lock = redisson.getLock("lock:order:" + userId);
        try {
            lock.lock(10, TimeUnit.SECONDS);
            // 1. 业务校验
            // 2. 调用 Service（原子操作）
            // 3. Feign 调用其他服务
            // 4. 组装结果
        } finally {
            lock.unlock();
        }
    }
}
```

**何时需要 Manager**：

- 操作涉及多个 Service 编排
- 需要分布式锁
- 需要限流保护
- 跨服务 Feign 调用聚合
- 事务组合（多表/多服务）

**Manager 粒度（[ADR-025](../adr/025-domain-aggregate-packaging-and-app-usecase-granularity.md)，强制）**：

- 一个 Manager 只归属**一个聚合或一个用例族**；一个类混装多个用例族必须拆（如"读取 / 管理端运营 / 跨服务开通"属三类用例族，不得共用一个类）；
- 类超 300 行必拆；拆分后沿用 `XxxManager` 命名（用例族 + Manager），**不采用** `XxxCmdExe` / `XxxQryExe`；
- Controller 注入面随拆分调整，**URL 与报文不变**；`@Transactional(rollbackFor = Exception.class)` 随原方法迁移，不丢失、不新增；
- 已单一职责的 Manager 不动（避免无收益 churn）。参考：user `UserManager` → `UserQueryManager`（读取+统计）/ `UserAdminManager`（管理端运营）/ `UserProvisionManager`（幂等开通）；notification `NotificationManager` → `ChannelDispatchManager` / `InAppMessageManager`；marketing `CouponService` → `CouponClaimService` / `CouponUsageService`。

### 3.3 Service 层（原子业务）

```java
@Service
@RequiredArgsConstructor
public class XxxServiceImpl implements XxxService {
    private final XxxMapper xxxMapper;
    // ✅ 可注入同一服务的其他 Service
    // ✅ 可注入 Converter
    // ❌ 绝不注入其他服务的 Feign Client → 交给 Manager
    // ❌ 绝不注入 RedisTemplate → 交给 Manager

    @Override
    @Transactional(rollbackFor = Exception.class)
    public XxxDTO create(XxxReq req) {
        // 1. 业务规则校验
        // 2. 数据操作（通过 Mapper）
        // 3. 对象转换（通过 Converter）
        // 4. 返回 DTO
    }
}
```

### 3.4 Mapper 层

```java
@Mapper
public interface XxxMapper extends BaseMapper<XxxEntity> {
    // 命名：select/insert/update/delete + By条件
    XxxEntity selectByPhone(@Param("phone") String phone);
    int updateStatusById(@Param("id") Long id, @Param("status") Integer status);

    // 分页用 MyBatis-Plus 内置 selectPage + LambdaQueryWrapper
    // ❌ 方法名不要用 get/find/query
}
```

### 3.4·1 业务 ID 规范（ADR-050，全层适用）

```java
// 生成：全项目唯一出口（UUIDv7，32 位小写 hex）——业务 ID；凭据（refreshToken/jti）用 secureToken()
order.setOrderNo(IdGenerator.nextId());            // org.icedamericanomall.common.utils.IdGenerator

// PO：业务标识列 BINARY(16) ↔ PO 字段 byte[]（MyBatis 默认 ByteArrayTypeHandler，无需任何注解）
private byte[] orderNo;                            // domain 层仍是 String，转换只发生在仓储边界

// 仓储边界转换（toPO/toDomain 与查询参数）
po.setOrderNo(IdGenerator.toBytes(order.getOrderNo()));        // 聚合 → PO
order.setOrderNo(IdGenerator.toHexString(po.getOrderNo()));    // PO → 聚合
wrapper.eq(OrderPO::getOrderNo, IdGenerator.toBytes(orderNo));         // 内部已校验值
wrapper.eq(OrderPO::getOrderNo, IdGenerator.toBytesOrNull(orderNo));   // 外部输入（URL/回调）→ 解析失败落空=未命中

// ❌ 反模式：eq 传裸 String（静默查空）、业务代码散落 UUID.randomUUID()/IdUtil.fastSimpleUUID()、
//    自定义 TypeHandler / autoResultMap 路线、用 nextId() 生成凭据
```

### 3.5 Converter 层（MapStruct）

```java
@Mapper(componentModel = "spring")
public interface XxxConverter {
    XxxVO toVO(XxxDTO dto);
    XxxDTO toDTO(XxxEntity entity);
    XxxEntity toEntity(XxxReq req);
    List<XxxVO> toVOList(List<XxxDTO> list);
}
```

**注意**：Lombok 必须排在 MapStruct 之前（已在父 POM 配置），否则编译报错。

### 3.6 Domain 层（四层 DDD 服务专属，零框架依赖）

`domain/model` 按聚合分包（[ADR-025](../adr/025-domain-aggregate-packaging-and-app-usecase-granularity.md)）：一个聚合一个包，包边界 = 聚合边界 = 未来服务拆分边界。

```
domain/
├── model/
│   └── <聚合名小写>/   ← 聚合根 / 实体 / 值对象（如 user/、address/、shop/、userauth/）
├── service/            ← 跨聚合领域服务（如 UserProvisioner）
├── repository/         ← 仓储接口（按业务键操作；不出现 Mapper / PO 等框架类型）
├── port/               ← 端口（框架无关接口，实现放 infrastructure）
└── enums/              ← 服务级共享枚举
```

- 例外①：服务只有 1 个聚合（或单族值对象）时保持平铺，不强行嵌套（如 verification `model/VerificationCode` / `model/VerificationPolicy`）；
- 例外②：跨能力族共享、被 app/infrastructure 消费的值对象留 `model/` 根，不为单类造包（如 authorization `LoginSpec` / `EmailAddress`）；
- 例外③：枚举默认留 `domain/enums/`；仅聚合私有状态机随聚合下沉（如 merchant `ShopStatusEnum` → `model/shop/`）；
- 无领域模型的模块不强行造聚合（ai / marketing 无 `domain/model`；wallet 余额不变式由仓储条件 UPDATE 承载）；
- **零框架依赖**：domain 包内禁止 Spring / MyBatis / Feign import（ADR-005）；Mapper / PO / Converter 只允许出现在 `infrastructure/persistence`。

---

## 四、对象转换规则

```
Controller 入参: Req (DTO) ──converter──→ Entity/Service参数
Controller 出参: ServiceDTO ──converter──→ VO
Mapper 操作:     Entity (DO) ←→ 数据库
Feign 调用:      XxxCO (ia-api 定义) ← Feign 出入参 = XxxCO

禁止:
  ❌ Controller 直接返回 Entity
  ❌ Service 入参用 Entity
  ❌ Feign 直接传 Entity
  ❌ VO 越过 Controller 传到 Service
```

> 对象命名规范（[ADR-015](../adr/015-package-and-contract-naming.md)）：应用层包名 = `app`（COLA 4.0 原生命名，`application.yml` 等资源文件名不变）；跨服务契约对象 = `XxxCO`（ia-api，Feign 出入参）；应用层内部传输对象 = `XxxDTO`（服务内 `app/dto`）；VO 归 Adapter response。

---

## 五、命名速查（AI 必须遵循）

| 层          | 动词前缀                                       | 示例                                                          |
| ---------- | ------------------------------------------ | ----------------------------------------------------------- |
| Mapper     | `select`/`insert`/`update`/`delete` + by条件 | `selectByPhone`, `updateStatusById`                         |
| Service    | 业务动词 + 领域名词                                | `register`, `getUserById`, `pageUsers`, `disableUser`       |
| Manager（编排） | 用例族名词 + `Manager`（一个 Manager 一个聚合/用例族，[ADR-025](../adr/025-domain-aggregate-packaging-and-app-usecase-granularity.md)） | `UserQueryManager`, `UserProvisionManager`, `ChannelDispatchManager` |
| Controller | HTTP-资源风格                                  | `getUser`(GET), `createProduct`(POST), `updateAddress`(PUT) |
| 内部控制器      | 同上 + `/internal/` 路径前缀                     | `getUserById`(GET /internal/user/{id})                      |

---

## 六、异常处理规则

```java
// ✅ 正确：使用 ia-common 异常体系
throw new BadRequestException(ErrorCode.PARAM_ERROR);
throw new BizException(ErrorCode.PHONE_ALREADY_REGISTERED);
throw new UnauthorizedException(ErrorCode.TOKEN_EXPIRED);

// ❌ 禁止：
throw new RuntimeException("出错了");           // 裸异常
throw new IllegalArgumentException("xxx");      // 无法被统一处理
try { ... } catch (Exception e) { e.printStackTrace(); }  // 吞异常
```

**新增 ErrorCode**: 在 `ia-common` 的 `ErrorCode` 枚举中添加，不得在服务内硬编码错误码。

---

## 七、关键模式速查

### 7.1 @Version 乐观锁

`orders` 和 `sku` 表使用 `@Version` 进行乐观锁并发控制。更新前必须先查出当前 version，更新时 MyBatis-Plus 自动 `WHERE version = ?` 并自增。

```java
// ✅ 正确：通过 Service 的 getById 查出实体（含 version），修改后 updateById
SkuEntity sku = skuService.getById(skuId);
sku.setStock(sku.getStock() - quantity);
skuService.updateById(sku);  // MyBatis-Plus: UPDATE ... SET stock=? WHERE id=? AND version=sku.getVersion()

// ❌ 禁止：直接 lambdaUpdate 跳过 version 检查（会破坏乐观锁）
lambdaUpdate().eq(SkuEntity::getId, skuId).setSql("stock = stock - " + quantity).update();
```

### 7.2 Validation Groups（CreateGroup / UpdateGroup）

DTO 校验使用标记接口区分创建/更新场景：

```java
// group/CreateGroup.java
public interface CreateGroup {}

// group/UpdateGroup.java
public interface UpdateGroup {}

// DTO 中按场景标注
@NotBlank(groups = CreateGroup.class)  // 创建时必填
@NotBlank(groups = {CreateGroup.class, UpdateGroup.class})  // 创建和更新都需要

// Controller 中按场景激活
public Result create(@RequestBody @Validated(CreateGroup.class) ProductReq req) { ... }
```

### 7.3 /internal 端点异常规则

`/internal/**` 路径被 Gateway 拦截禁止外网访问，同时**异常不包装 Result**：

- Controller 上使用 `@RestController`（非 `@ResponseBody` + `Result`）
- 方法返回原始类型（`UserEntity`, `void`, `long`）
- 异常由 `@ControllerAdvice` 中的 `GlobalExceptionHandler` 处理，但**不包装为 Result 格式**
- 这是为了让 Feign 调用方可以读取原始 HTTP 状态码

```java
// ✅ 正确：内部 Controller 不包装 Result
@RestController
@RequestMapping("/internal/user")
public class InternalUserController {
    @GetMapping("/{id}")
    public UserEntity getUser(@PathVariable Long id) { ... }  // 直接返回 entity
}

// ❌ 禁止：内部接口包装 Result
@GetMapping("/{id}")
public Result<UserEntity> getUser(@PathVariable Long id) { return Result.success(...); }
```

---

## 八、测试生成规则 (TDD)

### 单元测试模板

```java
@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {
    @Mock private UserMapper userMapper;
    @Mock private PasswordEncoder passwordEncoder;
    @InjectMocks private UserServiceImpl userService;

    @Test
    void shouldRegisterSuccessfully() {
        // Given
        RegisterDTO dto = new RegisterDTO();
        dto.setPhone("13800138000");
        when(userMapper.selectCount(any())).thenReturn(0L);

        // When
        UserDTO result = userService.register(dto);

        // Then
        assertNotNull(result);
        verify(userMapper).insert(any());
    }
}
```

### BDD 集成测试模板

```gherkin
# 参考 01-PRD §5 和 07-Tasks 的验收场景
Scenario: 成功注册
  Given 手机号 "13800138000" 未被注册
  And 验证码 "123456" 在 Redis 中存在且未过期
  When POST /api/auth/register
  Then 响应 HTTP 200
  And 响应 data 包含 accessToken
```

---

## 九、绝对不能做的事情

1. **跳过文档直接写代码**——必须先查 `project-docs/` 确认规格
2. **在 Controller 写业务逻辑**——包括 Redis 操作、锁操作、业务判断
3. **Entity (DO) 外泄**——返回给前端、传给 Feign、作为 Service 入参 均禁止
4. **绕过 ia-common 异常体系**——禁止裸 `RuntimeException`、禁止吞异常
5. **手写 set/get/BeanUtils.copyProperties**——必须用 MapStruct Converter
6. **破坏分层依赖方向**——禁止 Controller→Mapper, Service→Controller, Domain→Infra
7. **硬编码魔法值**——状态码、配置值、错误消息 全部提取到常量/枚举/ErrorCode
8. **大方法 / 混合职责**——超过 50 行的方法必须拆分；超过 300 行的类必须拆分；一个 Manager 混装多个聚合/用例族必须按用例族拆分（ADR-025，如 `UserManager` 拆为读取 / 管理端 / 开通三类）
9. **金额用浮点数**——所有金额必须用 `Integer`（分），计算/存储/传输一律如此
10. **内部接口包装异常**——`/internal/**` 路径不得包装 `Result`，必须直接抛异常
11. **重造共享内核已有能力**——审计字段填充（`createTime`/`updateTime`）由 ia-common `AuditFieldMetaObjectHandler` 自动装配统一提供（`@ConditionalOnMissingBean` 退避，[ADR-030](../adr/030-metaobjecthandler-consolidation-autoconfig.md)）；**新服务禁止自建 `MetaObjectHandler` 副本，除非有特有字段**（如 user-service 的 `registerTime`，退避共存）

---

## 十、各平台编码规范（前端多平台）

> **为什么单列一节**：前端已从单一 Web 扩展到五平台。**跨生态套用写法是本项目最容易发生的 AI 错误** ——
> 最典型的是把 React/Web 惯用法（Hooks 条件调用、`useEffect` 依赖数组、`className`、DOM API、CSS 变量）
> 直接搬进 SwiftUI / Jetpack Compose / React Native，代码看起来"很熟"但**是本生态的错误写法**。
>
> **本节的强制力**：§二 编码流程的「查阅对应项目文档」在此扩展为 ——
> **动手前先确认当前文件属于哪个平台，并只使用该平台的惯用法。跨平台只共享「设计 token 与 API 契约」，不共享组件代码。**
>
> 平台轴与实施状态见 [18-Frontend-Implementation.md](../frontend/18-Frontend-Implementation.md)（总览 §二）。
>
> | 平台 | 承载 | 本文规范状态 | 代码库现状（取证：`quality/20` §七） |
> | :-- | :-- | :-- | :-- |
> | Web | `apps/web` / `apps/admin` / `apps/seller` | 🟢 **实码核实** | `web` 59 文件 / 5,128 行；`admin` 9/93、`seller` 8/85（纯骨架） |
> | 小程序 | `apps/miniprogram`（Taro 4） | 🟢 **实码核实** | 48 文件 / 1,977 行 |
> | React Native | `apps/mobile` | 🟠 **本生态惯用法，未核实**（仓库仅占位，1 文件 / 0 行） | 空占位，未激活 |
> | Android 原生 | 无独立工程 | 🔴 **本生态惯用法，未核实** | 不存在；是否另立未决策 |
> | iOS 原生 | 无独立工程 | 🔴 **本生态惯用法，未核实** | 不存在；是否另立未决策 |
> | 桌面端 | 无 | ⚪ **技术栈未定** | 不存在；是否设立未决策 |

### 10.1 平台识别（先做这一步）

```
文件在 apps/web | apps/admin | apps/seller   → 读 §10.2 Web 规范
文件在 apps/miniprogram                      → 读 §10.3 小程序规范
文件在 apps/mobile                           → 读 §10.4 React Native 规范
文件在 android/ 或 *.kt / *.java（前端侧）    → 读 §10.5 Android 原生规范
文件在 ios/ 或 *.swift                       → 读 §10.6 iOS 原生规范
桌面包（main / tauri.conf 等）                → ⚪ 无规范：技术栈未定，先提出来讨论
```

**⚠️ 路径已变**：Web 用户端是 `apps/web`（**不是** `apps/storefront`）；路由是平铺真实路径（**无** `(brand)` / `(marketplace)` 路由组）。早期文档中的 `storefront` 已废弃。

### 10.2 Web 平台规范（`apps/web` / `admin` / `seller`）

#### 10.2.1 命名（**已核实**）

| 对象 | 规则 | 实例 |
| :-- | :-- | :-- |
| 文件（组件） | **kebab-case `.tsx`** | `glass-card.tsx`、`product-card.tsx`、`sku-selector.tsx`、`search-bar.tsx` |
| 文件（hook） | **kebab-case `use-*.ts`** | `use-cart.ts`、`use-products.ts`、`use-flash-sale.ts` |
| 组件（导出） | **PascalCase** 函数组件 | `GlassCard`、`ProductCard`、`SkuSelector` |
| Hook（导出） | **camelCase `useX`** | `useCart`、`useAddToCart`、`useToggleSelect` |
| 路由目录 | kebab-case / 真实路径 | `marketplace/`、`product/[id]/`、`shop/checkout/`、`order-detail` 非 Web |
| 路由文件 | Next.js 约定名 | `page.tsx`、`layout.tsx`、`loading.tsx`、`error.tsx`、`not-found.tsx` |
| 非路由组件 | 与被服务页面同目录 | `marketplace/category-tabs.tsx`、`marketplace/product-scroll.tsx` |
| 客户端组件 | 后缀 `-client.tsx` | `marketplace/infinite-client.tsx` |
| 共享包 | `@icedmall/<pkg>` | `@icedmall/ui`、`@icedmall/api`、`@icedmall/utils` |
| query key | 集中对象 `queryKeys.<域>.<粒度>` | `queryKeys.products.list(filters)` |

> ❌ **反例**（早期文档写法，**不是**本仓库约定）：`packages/ui/components/shared/GlassCard.tsx`（PascalCase 文件名 + 分目录）、`useProducts.ts`（camelCase 文件名）。

#### 10.2.2 惯用法（**已核实**）

| 项 | 要求 |
| :-- | :-- |
| 组件模型 | **Server Component 优先**；需要状态/浏览器 API 才用 `'use client'`，并拆成 `*-client.tsx` |
| 数据获取 | 服务端用 `@icedmall/api` 的服务端函数；客户端用 TanStack Query hooks，**不裸写 `fetch`** |
| 路由缓存 | 用 `export const revalidate = <秒>` 声明 ISR（如商城列表 60s） |
| 样式 | Tailwind v4 utility + `cn()`；**禁止裸写 hex 色值**，用 token（见 §10.9） |
| 图片 | `next/image`，不用裸 `<img>` |
| 导航 | `next/link`，不用裸 `<a>` 做站内跳转 |
| 表单 | `react-hook-form` + `zod` 校验 |
| 状态 | `zustand`（客户端全局态）；服务端态一律 TanStack Query |
| 后台（admin/seller） | Ant Design 5 + ProComponents；**纯客户端渲染**，不需要 SEO |

#### 10.2.3 禁止

`any`（TS strict）、裸 `<img>`/`<a>` 站内跳转、硬编码 `/api/` 路径字面量、在组件里直接操作 Cookie、把业务逻辑写进 Server Component 的渲染路径。

### 10.3 小程序平台规范（`apps/miniprogram`，Taro 4）

> ⚠️ **这是与 Web 差异最大的一节** —— 同是 React 语法，但**页面组织、路由、组件标签、生命周期完全不同**。

#### 10.3.1 命名（**已核实**）

| 对象 | 规则 | 实例 |
| :-- | :-- | :-- |
| 页面目录 | **每组页面一个目录**，目录名起语义 | `pages/cart/`、`pages/order-detail/` |
| 页面三件套 | **固定名 `index.tsx` + `index.config.ts` + `index.scss`** | `pages/cart/index.tsx` |
| 页面组件（导出） | PascalCase + `Page` 后缀 | `CartPage`（`export default function CartPage()`） |
| 页面注册 | `src/app.config.ts` 的 `pages` 数组 | `'pages/cart/index'` |
| TabBar 页 | `app.config.ts` 的 `tabBar.list` | 首页 / 商城 / 购物车 / 我的 |
| 组件 | kebab-case `.tsx`，放 `src/components/` | `product-card.tsx` |
| 工具 | kebab-case `.ts`，放 `src/utils/` | `api.ts`、`format.ts` |
| 全局样式 | `src/app.scss`（**现充当事实上的 token 层**） | — |
| API 调用 | `src/utils/api.ts` 的 `apiClient` | — |

> ❌ **反例**（Web 写法，**不要用**）：`pages/cart.tsx` 单文件页面、`CartPage.tsx` 路由文件、按 `app/<route>/page.tsx` 组织。

#### 10.3.2 惯用法（**已核实**）

| 项 | 要求 |
| :-- | :-- |
| 组件标签 | **必须用 `@tarojs/components`**（`View` / `Text` / `Image`）—— 不是 `div` / `span` / `img` |
| 生命周期 | **必须用 Taro 的**（`useDidShow` / `useDidHide` / `useReady`）—— 不是 `useEffect` 订阅 DOM 事件 |
| 平台 API | 从 `@tarojs/taro` 取（`Taro.xxx`）；**禁止 DOM/BOM API**（`window` / `document` / `localStorage`） |
| 路由 | `Taro.navigateTo` / `switchTab` / `redirectTo`；**没有 URL 路由** |
| 样式 | 全局 `app.scss` + 页面 `index.scss`；设计宽度 375（`config/index.ts` 的 `designWidth`） |
| 分包 | 非首屏页面放分包；**主包超宿主上限则无法提交** |
| 状态存储 | 用 `Taro.setStorageSync` / `getStorageSync`，**不是** `localStorage` |
| 宿主分支 | 微信与支付宝的登录/支付/分享 API **不是同一套**，必须逐宿主判断与验证 |

#### 10.3.3 现状待改进项（**AI 不得照抄**）

现有小程序代码中存在以下写法，属**待收敛的存量**，新代码**不得模仿**：

| # | 现象 | 应改为 |
| :-- | :-- | :-- |
| 1 | `useState<any[]>`、`(i: any)` 等 `any` 泛滥（如 `pages/cart/index.tsx`） | 用 `@icedmall/api` 的生成类型 / 显式接口 |
| 2 | `catch {}` 空捕获（吞异常） | 至少上报/提示；对齐 §六 异常处理的禁止项 |
| 3 | 直接 `apiClient('/api/cart')` 硬编码路径 | 走共享 `packages/api` 的类型化调用 |
| 4 | `app.config.ts` 里写死 `#FAFAF8` / `#2D8B6E` 等色值 | 收敛到 token（见 §10.9） |

> 见 `Implementation/front-end/scripts/check-design-tokens.mjs` 的 R3/R4 现状注释与 `quality/21` §五（阶段 4）。

### 10.4 React Native 规范（`apps/mobile`）

> 🟠 **本仓库仅占位（1 文件 / 0 行），无实码可核实 —— 以下为本生态惯用法，属「未核实」内容。**
> 激活该工程时（触发条件见 `Implementation/front-end/apps/mobile/README.md`），须先按实码校正本节。

| 项 | 惯用法 |
| :-- | :-- |
| 文件（组件） | **PascalCase `.tsx`**（如 `ProductCard.tsx`）—— **与 Web 的 kebab-case 不同** |
| 组件 | 函数组件 + Hooks（本生态与 Web 共享 React 心智模型，**这是唯一可以共享写法的地方**） |
| 基础组件 | **必须用 RN 组件**（`View` / `Text` / `Image` / `Pressable`），不是 `div`/`span`/`img` |
| 样式 | **`StyleSheet.create`**，不是 CSS / Tailwind class（`className` 不可用） |
| 导航 | `react-navigation` 的导航栈 —— **没有 URL 路由** |
| 存储 | `AsyncStorage` / Keychain(KeyStore) —— 不是 `localStorage` |
| 平台差异 | `Platform.select` / `.ios.tsx` / `.android.tsx` 后缀 |
| 禁止 | DOM API、CSS 变量、`next/*` 导入、复用 `@icedmall/ui`（DOM 组件，**RN 不可用**） |

> **可共享**：`@icedmall/api` 的类型与查询逻辑、`@icedmall/utils` 的纯函数、设计 token 的数值。
> **不可共享**：`@icedmall/ui` 的任何组件、`@icedmall/auth`（含 Next.js middleware 与浏览器 Cookie 操作）。

### 10.5 Android 原生规范

> 🔴 **本仓库不存在 Android 工程。以下为本生态惯用法（未核实）；若走 RN 则改读 §10.4。**
> 另立原生工程须先定案（见 [18-platforms/android.md](../frontend/18-platforms/android.md) §九）。

| 项 | 惯用法 |
| :-- | :-- |
| 语言/UI | Kotlin + **Jetpack Compose**（声明式，**不是 XML 布局 + findViewById**） |
| 文件 | PascalCase `.kt`，一个 Composable 一个文件为主 |
| Composable | **PascalCase 动词/名词短语**（`ProductCard`、`CheckoutScreen`） |
| 参数 | `modifier: Modifier = Modifier` **作为第一个可选参数**（本生态硬性惯例） |
| 状态 | `remember` / `mutableStateOf` / `StateFlow`；**不在 Composable 里做耗时 IO** |
| 导航 | Navigation Compose 的导航图；**没有 URL 路由**；处理系统返回手势 |
| 分层 | UI(Compose) → ViewModel → Repository → DataSource；**UI 不直接调网络** |
| 主题 | Material 3 `MaterialTheme` / `ColorScheme` 注入 token |
| 协程 | `suspend` 函数 + `viewModelScope`；**禁止在主线程做网络/磁盘 IO** |
| 禁止 | 在 Composable 里写业务判断与网络调用、把 Web 的 Hooks 依赖数组写法搬进来、硬编码色值（用主题） |
| 发布 | `keystore` 签名（**密钥丢失即无法更新**）+ Play Console 内测轨道 |

### 10.6 iOS 原生规范

> 🔴 **本仓库不存在 iOS 工程。以下为本生态惯用法（未核实）；若走 RN 则改读 §10.4。**
> 另立原生工程须先定案（见 [18-platforms/ios.md](../frontend/18-platforms/ios.md) §九）。

| 项 | 惯用法 |
| :-- | :-- |
| 语言/UI | Swift + **SwiftUI**（声明式，**不是 UIKit + Storyboard**） |
| 文件 | PascalCase `.swift`，一个 View 一个文件为主 |
| View | **PascalCase 名词短语**（`ProductCard`、`CheckoutView`、`CartRow`） |
| 状态 | `@State` / `@Binding` / `@StateObject` / `@ObservedObject` / `@Environment` —— **有明确的职责划分，不是"随便选一个能编译的"** |
| 导航 | `NavigationStack` + 值驱动导航；**没有 URL 路由**；处理边缘返回手势 |
| 分层 | View → ViewModel(`ObservableObject`) → Service/Repository；**网络层用 `async/await` + `URLSession` 封装** |
| 主题 | SwiftUI `Environment` / Asset Catalog 注入 token（不用 hex 硬编码） |
| 并发 | `async/await` + `@MainActor` 标注 UI 更新；**禁止主线程网络请求** |
| 平台能力 | Keychain 存凭据；APNs 推送需真实设备的 device token |
| 禁止 | 把 React 的 Hooks 条件调用/依赖数组心智搬进来、`className`、DOM API、硬编码色值 |
| 发布 | 证书 + Provisioning Profile → Archive → **TestFlight** → App Store 审核；**已发布版本无法回退** |

### 10.7 五平台惯用法对照（**禁跨生态套用**）

| 关注点 | Web | 小程序 | React Native | Android 原生 | iOS 原生 |
| :-- | :-- | :-- | :-- | :-- | :-- |
| 组件文件名 | kebab-case `.tsx` | kebab-case `.tsx` | **PascalCase `.tsx`** | PascalCase `.kt` | PascalCase `.swift` |
| 组件标签 | `<div>`/语义 HTML | **`@tarojs/components`** | **RN `View`/`Text`** | **Composable** | **SwiftUI View** |
| 样式 | Tailwind class | `.scss` | **`StyleSheet`** | **`Modifier`** | **`ViewModifier`** |
| 状态 | zustand + TanStack Query | Taro 生命周期 + state | RN state + 存储 | `StateFlow` + ViewModel | `@State`/`@ObservedObject` |
| 路由 | **URL 路由**（Next.js） | 栈式 + TabBar（无 URL） | 导航栈（无 URL） | 导航图（无 URL） | `NavigationStack`（无 URL） |
| 存储 | Cookie / localStorage | **`Taro.setStorageSync`** | AsyncStorage | DataStore | UserDefaults / Keychain |
| 主题注入 | CSS 变量（`@theme`） | 样式变量（`app.scss`） | JS token 常量 | Material 3 `ColorScheme` | `Environment` / Asset Catalog |
| 禁止的偷懒 | 裸 `<img>` / 硬编码路径 | DOM API / `localStorage` | CSS / `className` | XML 布局写法 | UIKit/Storyboard 混用 |

### 10.8 跨平台共享边界（**别改错地方**）

| 包 | 归属 | 可被哪些平台消费 |
| :-- | :-- | :-- |
| `@icedmall/api` | **跨平台** | 全部（**类型与查询逻辑**共享；fetch 实现按平台各自封装） |
| `@icedmall/utils` | **跨平台** | 全部（**必须零平台依赖**：金额格式化、校验、常量） |
| `@icedmall/config` | 构建期 | 各平台构建配置 |
| `@icedmall/ui` | **仅 Web 生态** | Web / admin / seller。**RN、Compose、SwiftUI、小程序均不可复用** |
| `@icedmall/auth` | **仅 Web 生态** | 含 Next.js `middleware` 与浏览器 Cookie 操作 |

> ⚠️ **最常见的越界**：往 `@icedmall/utils` 里加依赖 `window` / `document` / Node API 的代码 ——
> 它会被小程序与 RN（未来的 Android/iOS）一并引用，加错一次全平台编译失败。
> **动手改 `packages/*` 前先问：这个平台能跑吗？**

### 10.9 全平台硬性约束（对齐既有章节）

以下约束**不因平台而变**，各平台实现方式不同但要求相同：

| # | 约束 | 依据 |
| :-- | :-- | :-- |
| 1 | **金额一律按「分」整数**，禁止浮点；`/100` 仅用于展示 | §九.9；多平台换算公式见 `frontend/21` §六.7 |
| 2 | **设计 token 单源**：唯一的数值源是 `frontend/17`；各平台只做注入，**禁止硬编码色值/尺寸** | `frontend/17` §2.4；`scripts/check-design-tokens.mjs` |
| 3 | **API 契约单源**：`contract/05`；**禁止硬编码 `/api/` 路径字面量** | §一；`contract/05` |
| 4 | **禁止 `any` / 空 `catch` / 吞异常**（各平台等价项） | §六、§九.4 |
| 5 | **禁止 `/internal/**` 出现在前端**（任何平台） | `contract/05` §六 |
| 6 | **不绕过网关直连服务** | `gateway-service/application.yml` 为准（`quality/20` §A5） |
| 7 | **改 token 的顺序：先改代码、再改 `17 §2.4`**，然后再跑 token 检查 | `frontend/17` §2.4 的定位说明 |

### 10.10 新增平台的登记要求

新增一个平台时，**编码规范不是可选项** —— 未登记规范的平台**不得开工**：

1. `frontend/15` 的平台矩阵登记该平台选型（归 frontend-engineer）
2. 本节增补该平台小节（命名 + 惯用法 + 禁止项），并在 §10.7 对照表加一行
3. `frontend/18-platforms/<平台>.md` 建立平台细则
4. `quality/11` §9.1 设备矩阵加该平台三档；`quality/19` §十一 加执行流程
5. `project-docs/00-README.md` 登记新目录（归 doc-engineer）
