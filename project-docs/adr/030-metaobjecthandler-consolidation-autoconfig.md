# ADR-030: MyMetaObjectHandler 收口为 ia-common 自动装配唯一实现（条件退避）

## 背景

审计（T17，2026-10-03）发现此前"T4a 已归并 MyMetaObjectHandler"的结论不成立：cart / item / logistics / merchant / pay / search / trade / user 八个模块仍各持一份 `config/MyMetaObjectHandler`，其中 5 份逐字相同、merchant 仅多一段 Javadoc、search 仅风格差异（内联两次 `LocalDateTime.now()`）、user 额外填充 `registerTime`。8 份副本属典型的重复功能代码，且存在口径漂移风险（如 user 独有的 `registerTime` 无法被其他服务复用，新服务需自行复制）。

## 决策

1. ia-common 新建共享内核唯一实现 `AuditFieldMetaObjectHandler`（insertFill 填 `createTime`/`updateTime`，updateFill 填 `updateTime`，严格模式 `strictInsertFill/strictUpdateFill`，与既有副本逐字段对齐）。
2. 经 `MyMetaObjectHandlerAutoConfiguration` 自动装配：`@ConditionalOnClass(MetaObjectHandler.class)`（mybatis-plus 在 ia-common 为 optional，无依赖的服务不受影响）+ `@Bean @ConditionalOnMissingBean(MetaObjectHandler.class)`（业务服务自定义 Bean 存在时整体退避），注册进 `AutoConfiguration.imports`——与 `RateLimitAspect`、`DistributedLockConfig` 同一装配机制（ADR-012/024 口径）。
3. 删除 cart / item / logistics / merchant / pay / search / trade 七份副本；**user-service 保留自有实现**（额外填充 `registerTime`），依靠 `@ConditionalOnMissingBean` 退避 + Bean 名不同（`myMetaObjectHandler` vs `auditFieldMetaObjectHandler`）双保险共存。

## 备选方案

- **逐服务保留**（现状）：副本继续漂移，新服务重复复制，否决。
- **user 变体也归并**（共享实现加 `registerTime` 条件填充）：`registerTime` 是 user 域特有口径，进入共享内核会引入业务字段泄漏，违反"共享内核无业务语义"原则，否决。
- **强制各服务删除且 user 改用共享 + 单独补填 registerTime**：为省一个类引入显式补填代码，复杂度高于保留，否决。

## 后果

- 正向：8 → 1 共享 + 1 服务自有；审计字段口径单点定义；新服务零成本获得填充能力；user 变体语义不变。
- 影响面：authorization / marketing / notification / wallet / ai 五个原先"有 `@TableField(fill=...)` 注解但无填充器"的服务**首次获得应用侧真实填充**（此前依赖 DB 列默认值）——属收口目的（审计字段口径统一），无 HTTP 契约变化；不希望扩散时可在自动配置上加激活开关（当前未加）。
- 风险：若未来某服务注册了第二个 `MetaObjectHandler` Bean 且未退避预期，将出现双填充器覆盖问题——由 `MyMetaObjectHandlerAutoConfigurationTest.shouldBackOff_whenCustomHandlerPresent` 锁定退避语义。

## 日期

2026-10-03

## 参考

- ADR-012（可选依赖 @ConditionalOnClass 守卫）、ADR-024（DistributedLock 归并，同装配机制）、ADR-034（跨模块工具收口与"必要差异"登记，本决策为其待办项 a 的落地）
