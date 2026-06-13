# 10 — 代码评审标准 (Code Review Checklist)

> 本文档定义代码评审的检查标准，支撑 SPC (Software Process Control) 的质量门禁阶段。
> 每次 PR 必须逐条通过，不得跳过。

---

## 一、提交前自检（开发者）

在发起 PR 前，开发者必须确认以下项：

```
□ mvn clean install 编译通过（当前模块）
□ 新增代码有对应的单元测试
□ BDD 场景覆盖了所有核心路径（正常 + 异常 + 边界）
□ 对照 09-constraints 排查了反模式
□ 对照 08-agents 确认分层合规
□ 没有遗留的 TODO（除非关联了 Issue 编号）
□ commit message 符合规范（feat:/fix:/refactor: 等）
```

---

## 二、架构分层检查

### 2.1 Controller 层

```
□ 方法体不超过 10 行（核心逻辑行数，不含空行和注解）
□ 只做三件事：参数校验 → 调用 Service/Manager → 返回 Result
□ 没有直接调用 Mapper
□ 没有直接操作 Redis / Redisson / 分布式锁
□ 没有 try-catch（异常交给 GlobalExceptionHandler）
□ 返回类型为 Result<T>（T 是 VO，不是 Entity）
□ 参数使用 @Validated 校验（复杂校验用 DTO validation 注解）
□ 路径使用 RESTful 风格，不含动词（/api/user 而不是 /api/getUser）
```

### 2.2 Manager 层

```
□ 只包含编排逻辑：分布式锁、限流、跨服务调用、多 Service 组合
□ 不包含原子业务规则（原子规则应在 Service 中）
□ 不直接操作 Mapper
□ 分布式锁有 finally unlock
□ Feign 调用有 fallback/降级处理
□ 事务边界合理（跨服务调用不在同一事务中）
```

### 2.3 Service 层

```
□ 方法名使用业务动词（register, createOrder, disableUser）
□ 参数和返回值使用 DTO，不是 Entity
□ 只调用 Mapper 和同服务的其他 Service，不调 Feign
□ 事务注解完整：@Transactional(rollbackFor = Exception.class)
□ 不包含 Redis/锁操作
□ 不包含 Controller 层概念（HttpServletRequest, HttpServletResponse）
```

### 2.4 Mapper 层

```
□ 接口继承 BaseMapper<XxxEntity>
□ 方法名以 select/insert/update/delete 开头
□ 参数使用 @Param 注解
□ 复杂查询优先用 LambdaQueryWrapper，避免手写 SQL
□ 没有返回值包含 DTO/VO（只返回 Entity 或基本类型）
```

---

## 三、命名规范检查

```
□ Mapper 方法: select/insert/update/delete + By条件
□ Service 方法: 业务动词 + 领域名词 (register, login, disableUser)
□ Controller 方法: HTTP-资源风格 (getUser, createOrder, pageUsers)
□ 类名: 名词，准确描述职责 (UserService, OrderManager, AddressConverter)
□ 包名: 全小写，按分层归位 (controller/manager/service/mapper/...)
□ 无拼音命名、无过度缩写、无语义不明
```

---

## 四、对象隔离检查

这是最容易出问题的检查项，重点排查：

```
□ 搜索 "import *.domain.entity.*" —— 确认没有出现在 controller/domain/dto 包外
□ 搜索 "import *.entity.*" in Controller —— 逐条确认是否 Entity 外泄
□ Controller 返回类型检查 —— 必须为 VO，禁止 Entity 和 DTO
□ Service 参数/返回值 —— 必须为 DTO，禁止 Entity
□ Feign 接口传参 —— 必须使用 FeignDTO（定义在 ia-api）
□ Converter 是否覆盖了所有 Entity↔DTO↔VO 转换场景（禁止手写 BeanUtils.copyProperties）
```

**快速排查命令**:
```bash
# 检查 Controller 是否引用了 Entity
grep -r "import.*\.domain\.entity\." --include="*Controller.java"

# 检查是否手写了 BeanUtils
grep -r "BeanUtils\.copyProperties\|BeanUtils\.copyBean" --include="*.java"

# 检查是否直接返回了 Entity
grep -r "Result<.*Entity>" --include="*Controller.java"
```

---

## 五、异常处理检查

```
□ 抛出的异常都是 ia-common 异常体系的子类（CommonException 及其子类）
□ ErrorCode 枚举值已定义，业务码不重复
□ 没有 throw new RuntimeException("xxx")
□ 没有 throw new IllegalArgumentException / IllegalStateException
□ 没有空的 catch 块
□ 没有 catch 后只 printStackTrace()
□ 内部接口 (/internal/**) 的异常没有被 GlobalExceptionHandler 包装为 Result
□ 日志: 异常必须记录 log.error(..., e)，包含完整堆栈
```

---

## 六、安全检查

```
□ 密码字段未出现在日志中
□ Token/手机号 在日志中脱敏（中间 4 位打码）
□ SQL 查询使用参数化（LambdaQueryWrapper），无字符串拼接
□ 无硬编码密钥/密码/Token（必须从配置文件或环境变量读取）
□ 商家接口有角色校验（@PreAuthorize 或 Manager 层校验）
□ 用户只能操作自己的数据（userId 从 JWT 中提取，不信任前端传入）
□ /internal/** 路径不应暴露到外网（Gateway 有拦截规则）
□ 限流注解 @RateLimit 已加在 发短信/登录/注册 接口上
```

---

## 七、测试覆盖检查

```
□ 核心 Service 方法有单元测试（Mock Mapper）
□ 每个 BDD Scenario 至少对应一个集成测试
□ 测试覆盖了正常路径 + 异常路径 + 边界条件
□ 测试方法名描述场景（shouldXxxWhenYyy）
□ 测试可重复执行（不依赖外部状态）
□ Mock 合理：不 Mock 被测试对象本身
```

### 测试覆盖率目标

| 层级 | 覆盖率目标 | 说明 |
|------|-----------|------|
| Service 层 | ≥ 80% | 核心业务逻辑必须覆盖 |
| Manager 层 | ≥ 60% | 编排逻辑 + Feign Mock |
| Controller 层 | ≥ 50% | API 契约校验为主 |
| Mapper 层 | 不强制 | MyBatis-Plus 内置方法无需测试 |

---

## 八、数据与性能检查

```
□ 金额字段使用 Integer（分），无 float/double/BigDecimal
□ 分页查询有索引支持（检查 WHERE 条件的字段是否有索引）
□ 没有 SELECT * + 全表扫描（必要时加 LIMIT）
□ 循环内没有数据库调用（N+1 问题）
□ 下单/支付等关键操作有幂等保护
□ 大数据量操作分批处理（禁止一次性加载全部数据到内存）
□ Redis Key 有 TTL（禁止永不过期的 Key）
```

---

## 九、代码质量检查

```
□ 单个方法不超过 50 行（超过则需说明理由并拆分）
□ 单个类不超过 300 行
□ if-else 嵌套不超过 3 层（超过用提前 return / 策略模式重构）
□ 没有重复代码块（同一逻辑出现 2 次以上抽取为私有方法）
□ 变量名准确描述含义，不用 a/b/c/temp
□ 注释少而精：只写 WHY，不写 WHAT（代码本身就说明了 WHAT）
□ 没有注释掉的代码块（删除，不要注释）
```

---

## 十、PR 评审清单（评审者使用）

### 第一遍：快速扫描（5 分钟）
```
□ CI 是否通过（编译 + 测试）
□ Commit message 是否符合规范
□ PR 描述是否说明了 WHY 和 WHAT
□ 文件变更数量是否异常（超过 20 个文件需要关注）
```

### 第二遍：架构审查（10 分钟）
```
□ 分层是否合规（Controller → Manager → Service → Mapper）
□ 新的类/方法放在了正确的包中
□ 是否有 Entity 外泄
□ 是否有跨层依赖
```

### 第三遍：业务审查（10 分钟）
```
□ 业务逻辑是否符合 PRD/API-Spec 定义
□ 边界条件是否处理（空值、负数、超限）
□ 事务边界是否正确
□ 并发场景是否考虑（锁、幂等、CAS）
```

### 第四遍：细节审查（10 分钟）
```
□ 命名是否符合规范
□ 异常处理是否正确
□ 日志是否合适
□ 是否有安全漏洞
□ 测试是否覆盖了变更
```

---

## 十一、常见违规速查

| 违规 | 检测方式 | 严重程度 |
|------|---------|---------|
| Controller 返回 Entity | grep `Result<.*Entity>` in *Controller | 🔴 阻断 |
| Service 调 Feign | grep `Client` in *ServiceImpl | 🔴 阻断 |
| 裸 RuntimeException | grep `new RuntimeException` | 🔴 阻断 |
| 金额用浮点 | grep `double\|float\|BigDecimal` in 金额相关字段 | 🔴 阻断 |
| 硬编码魔法值 | grep `== [0-9]\|\"[0-9].*\"` 排除常量引用 | 🟡 警告 |
| 吞异常 | grep `catch.*Exception.*{[^}]*$` | 🔴 阻断 |
| 方法超 50 行 | 人工或工具统计 | 🟡 警告 |
| import Entity in Controller | grep `import.*entity` in *Controller | 🔴 阻断 |
| System.out.println | grep `System.out` | 🟡 警告 |

**处理原则**：🔴 阻断 → 必须修复才能合并；🟡 警告 → 建议修复，需说明理由方可豁免。

---

## 十二、评审结果记录模板

```markdown
## PR Review: #[PR号] [标题]

**评审者**: [姓名]
**日期**: YYYY-MM-DD
**结论**: ✅ 通过 / 🔴 需修改 / 💬 仅建议

### 发现的问题
1. [ ] 🔴 [问题描述] — [文件:行号]
2. [ ] 🟡 [建议描述] — [文件:行号]

### 亮点
- [值得肯定的代码]

### 测试覆盖
- 单元测试: [N] 个
- 集成测试: [N] 个
- BDD 场景覆盖: [已覆盖的场景]
```
