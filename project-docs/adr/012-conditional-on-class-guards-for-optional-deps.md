# ADR-012: ia-common 可选依赖的 `@ConditionalOnClass` 守卫策略

## 背景

ia-common 作为共享内核，把多数中间件依赖声明为 `<optional>true</optional>`（Redis / MyBatis-Plus / XXL-Job / Seata / Spring Security），设计意图是「谁用谁引」。但 R-1 遗留问题表明该意图没有真正落地：`@Configuration` 类无条件注册，条件注解只覆盖了「功能开关」，没覆盖「依赖是否存在」。

具体缺口（V5.2 审计结论）：

| 组件 | 守卫现状 | 后果 |
| :--- | :--- | :--- |
| `RedisConfig` | 无 | 无 Redis 的服务启动即 `NoClassDefFoundError` |
| `RedisLuaScriptConfig` | 无 | 同上，且会去加载 4 个 `lua/*.lua` |
| `RateLimitUtils` | 只有 `@ConditionalOnWebApplication(SERVLET)` | Web 应用（如 notification-service）仍会注册 → 字段类型 `StringRedisTemplate` 缺失 → 类加载失败 |
| `RateLimitAspect` | 只有 `@ConditionalOnWebApplication(SERVLET)` | 强依赖 `RateLimitUtils`，单独缺席会从「类找不到」变成「Bean 找不到」 |
| `GlobalExceptionHandler` | 无（且持有一个**从未使用**的 `RateLimitUtils` 字段） | 全局异常处理隐式依赖 Redis —— 最隐蔽的一条 |

后果是 notification-service（不用 Redis）被迫在 pom 里保留 `spring-boot-starter-data-redis`，并在注释里写下「必须保留（详见交付摘要 TODO）」。

## 决策

1. **依赖存在性与功能开关分离**：凡是引用了 optional 依赖类型的自动配置 Bean，一律补 `@ConditionalOnClass`；功能开关（`xxl.job.enabled` / `seata.enabled`）继续用 `@ConditionalOnProperty`。
2. **配套组件使用同一个条件表达式**，保证「切面存在 ⟺ 工具存在」，不出现单边注册：
   - `RedisConfig` → `@ConditionalOnClass({RedisConnectionFactory, RedisTemplate})`
   - `RedisLuaScriptConfig` → `@ConditionalOnClass(RedisScript)`
   - `RateLimitUtils` → `@ConditionalOnClass({StringRedisTemplate, RedisScript})`
   - `RateLimitAspect` → `@ConditionalOnClass(StringRedisTemplate)`
3. **同步消除隐性依赖**：删除 `GlobalExceptionHandler` 中未使用的 `RateLimitUtils` 字段（死字段 → 假依赖）。
4. **静态工具类不设守卫**：`HttpUtils`（httpclient5）等静态工具无法作为 Bean 条件化，其依赖由引入方模块的**非 optional** 依赖保证（httpclient5 在 ia-integration 中为 compile 依赖，已随依赖传递）。
5. **验证方式**：新增 `RedisConditionGuardTest` / `RateLimitConditionGuardTest`，用 `FilteredClassLoader` 把 Redis 类从类路径「藏掉」，断言上下文**正常启动**且相关 Bean 缺席；同时用反向用例锁死「守卫不过度收紧」。
6. **摘除下游无用依赖**：notification-service 的 `spring-boot-starter-data-redis` 与 `spring.data.redis.*` 配置随之删除（该服务零 Redis 使用、零 `@RateLimit`）。

## 备选方案

| 方案 | 优点 | 缺点 |
| :--- | :--- | :--- |
| 各服务自行保留无用依赖（现状） | 零改动 | 依赖膨胀；actuator 暴露与职责无关的 redis health 指标；与「optional 依赖」的初衷互相矛盾 |
| 把可选依赖改为非 optional（全局传递） | 一行改动即可 | 违背共享内核设计意图，所有服务被迫携带全部中间件 |
| 用 `spring.autoconfigure.exclude` 逐个服务排除 | 不改 ia-common | 每新增一个服务都要配一遍，容易漏；不解决类加载阶段的失败 |
| **`@ConditionalOnClass` 守卫（采用）** | 一次收口、对下游零配置、故障前移到「不注册」而非「崩溃」 | 需保证成对组件的条件表达式一致（已用同表达式 + 测试锁死） |

## 后果

- ✅ ia-common 的可选依赖语义名副其实：不引 Redis 的服务不会再因 Redis 相关 Bean 崩溃
- ✅ notification-service 摘除 Redis 依赖与配置，pom 注释中的历史 TODO 关闭
- ✅ +4 个守卫回归测试（ia-common 8 → 12），锁死「无 Redis 也能启动」与「有 Redis 仍注册」
- ⚠️ **未做实机启动验证**（本地缺 Nacos/Redis/MySQL）：守卫有效性由 `FilteredClassLoader` 测试间接证明，建议合并前 smoke 启动 notification-service 与一个用 Redis 的服务（如 user-service）
- ⚠️ 若后续有人给 `RateLimitUtils` / `RateLimitAspect` 增加新的 optional 依赖类型，必须同步扩展条件表达式 —— 成对约束无法由编译器保证，只能靠本 ADR 与测试守护

## 日期

2026-09-29

## 参考

- `Implementation/back-end/ia-common/src/test/java/org/icedamericanomall/common/config/RedisConditionGuardTest.java`
- `Implementation/back-end/ia-common/src/test/java/org/icedamericanomall/common/aspect/RateLimitConditionGuardTest.java`
- `ADR-009`（Bean 注册 scan/imports 二选一策略）
