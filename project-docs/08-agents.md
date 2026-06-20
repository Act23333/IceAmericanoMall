# 08 — AI Agent 开发指令

> 本文档定义 AI 编码助手（Claude Code 等）在冰美商城项目中的行为规范。
> 所有代码生成必须遵循本文档 + 项目文档体系。

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

---

## 四、对象转换规则

```
Controller 入参: Req (DTO) ──converter──→ Entity/Service参数
Controller 出参: ServiceDTO ──converter──→ VO
Mapper 操作:     Entity (DO) ←→ 数据库
Feign 调用:      FeignDTO (ia-api 定义)

禁止:
  ❌ Controller 直接返回 Entity
  ❌ Service 入参用 Entity
  ❌ Feign 直接传 Entity
  ❌ VO 越过 Controller 传到 Service
```

---

## 五、命名速查（AI 必须遵循）

| 层          | 动词前缀                                       | 示例                                                          |
| ---------- | ------------------------------------------ | ----------------------------------------------------------- |
| Mapper     | `select`/`insert`/`update`/`delete` + by条件 | `selectByPhone`, `updateStatusById`                         |
| Service    | 业务动词 + 领域名词                                | `register`, `getUserById`, `pageUsers`, `disableUser`       |
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

## 七、测试生成规则 (TDD)

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

## 八、绝对不能做的事情

1. **跳过文档直接写代码**——必须先查 `project-docs/` 确认规格
2. **在 Controller 写业务逻辑**——包括 Redis 操作、锁操作、业务判断
3. **Entity (DO) 外泄**——返回给前端、传给 Feign、作为 Service 入参 均禁止
4. **绕过 ia-common 异常体系**——禁止裸 `RuntimeException`、禁止吞异常
5. **手写 set/get/BeanUtils.copyProperties**——必须用 MapStruct Converter
6. **破坏分层依赖方向**——禁止 Controller→Mapper, Service→Controller, Domain→Infra
7. **硬编码魔法值**——状态码、配置值、错误消息 全部提取到常量/枚举/ErrorCode
8. **大方法**——超过 50 行的方法必须拆分；超过 300 行的类必须拆分
9. **金额用浮点数**——所有金额必须用 `Integer`（分），计算/存储/传输一律如此
10. **内部接口包装异常**——`/internal/**` 路径不得包装 `Result`，必须直接抛异常
