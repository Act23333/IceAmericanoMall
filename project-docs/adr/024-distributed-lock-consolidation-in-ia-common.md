# ADR-024: 业务键互斥锁能力归并到 ia-common 共享内核（条件装配）

## 背景

`authorization-service` 与 `user-service` 各自持有一份**逐字重复**的业务键互斥锁实现：

```
auth-service: domain/port/DistributedLock.java + infrastructure/adapter/RedissonDistributedLockAdapter.java
user-service: domain/port/DistributedLock.java + infrastructure/adapter/RedissonDistributedLockAdapter.java
```

两份代码 `diff` 结果只有包名与 Javadoc 字面差异（等待 3s / 租期 5s / 不可重入解锁校验完全一致）。调用方分别是 auth 的 `UserAuthProvisioner` 与 user 的 `UserProvisioner`，语义同为「注册/登录时按业务键（手机号 hash 等）串行查建，避免并发重复建号」。

重复的成本不是行数，而是**演化分叉**：锁参数、解锁前的持有者校验、获取失败时的错误码任一处被单独修改，两个服务的并发行为就不再一致，且这种不一致极难被测试发现。

同时，ia-common 已有成熟的**可选依赖条件装配**范式（ADR-012：`@Configuration` + `@ConditionalOnClass` + `AutoConfiguration.imports` 注册，见 `RedisConfig` / `RateLimitAspect`），足以承载「只有引入 Redisson 的服务才装配该 Bean」。

## 决策

在 ia-common 新建共享内核包 `org.icedamericanomall.common.lock`：

- `DistributedLock`（接口，能力契约；`<T> T executeWithLock(String lockKey, Supplier<T> action)`）；
- `RedissonDistributedLockAdapter`（Redisson 实现，**不加** `@Component`）；
- `org.icedamericanomall.common.config.DistributedLockConfig`：`@Configuration` + `@ConditionalOnClass(RedissonClient.class)` + `@ConditionalOnMissingBean(DistributedLock.class)` 装配上述 Bean，并登记进 `META-INF/spring/...AutoConfiguration.imports`。

ia-common 的 `redisson-spring-boot-starter` 声明为 `<optional>true</optional>`（版本与两服务现有声明一致 3.26.0，不向外传递）。

两个服务**删除各自的副本**（接口 + 适配器共 4 个文件），改注入 ia-common 的 `DistributedLock`；auth/user 的 `pom.xml` 仍保留 `redisson-spring-boot-starter`（提供 `RedissonClient`）。

## 备选方案

| 方案 | 优点 | 缺点 |
| :--- | :--- | :--- |
| 维持两份副本 | 零改动、无跨模块耦合 | 演化分叉无人察觉；每加一个需要互斥的服务就多一份拷贝 |
| 抽到 ia-api（跨服务契约模块） | 已有共享定位 | ia-api 定位是「跨服务调用契约（Feign + DTO）」，塞技术能力会污染契约层语义 |
| **抽到 ia-common + 条件装配（本决策）** | 一份实现、按需装配；未引入 Redisson 的服务零影响 | ia-common 增加一个 optional 依赖；需要一次性的 imports 登记与扫描路径核对 |
| 抽到新的 `ia-infra` 模块 | 职责最纯粹 | 为一个类新增 Maven 模块，构建与 CI 成本不划算 |

## 后果

**正面**

- 互斥锁语义（等待/租期/解锁校验/失败错误码）只有一处权威实现，行为天然一致；
- 条件装配使「类路径无 Redisson」的服务（marketing / ai / trade / pay / ...）完全不受影响，不需要改任何配置；
- 调用方（领域/应用层）依赖的是 ia-common 的接口，领域层仍然零框架依赖（Redisson 只在基础设施侧）。

**负面 / 风险**

- 锁能力现在位于共享内核：**修改 `RedissonDistributedLockAdapter` 会影响所有使用者**（目前 auth / user 两个服务），需按公共组件标准评审；
- `DistributedLockConfig` 依赖 `@ConditionalOnClass` 的 ASM 守卫生效（ADR-012 同款前提）；若某服务把 Redisson 引入类路径却未提供 `RedissonClient` Bean，该装配会因构造依赖缺失而失败——当前仅 auth/user 引入 Redisson，且两者均由 `redisson-spring-boot-starter` 自动配置提供 `RedissonClient`。

## 日期

2026-10-03

## 参考

- `project-docs/adr/012-conditional-on-class-guards-for-optional-deps.md`（可选依赖条件装配范式）
- `project-docs/adr/005-domain-purity-ports-and-eventual-consistency.md`（领域层零框架依赖、能力端口化）
- `ia-common/src/main/java/org/icedamericanomall/common/lock/`（实现）
- `ia-common/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
