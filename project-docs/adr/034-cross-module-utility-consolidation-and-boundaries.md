# ADR-034: 跨模块工具类收口与「必要差异」边界固化

## 背景

全仓排查 utilities 层面的跨模块重复实现（点名两项：gateway 的 `ClientIpUtil` vs ia-common 的 `IpUtil`；trade 的 `SensitiveMaskUtil` vs ia-common 的 `DesensitizeUtil`），并复核历史收口项是否复发。结论：**真重复只有账号掩码一处**；IP 解析是**类型系统 + 信任边界双重差异**，不可合并，但原名与 `IpUtil` 同形不同义，存在误用风险。同时发现任务书所述「`MyMetaObjectHandler` 已归并」与事实不符（仓库仍有 8 份副本），本轮不动、单独立项。

## 决策

1. **账号掩码归并（真重复 → 收口）**：trade-service 本地 `util/SensitiveMaskUtil#maskBankAccount`（T11 为避开并行冲突所建、仅 trade 一处调用）语义逐字符上提到 ia-common `DesensitizeUtil#maskAccount`（长度 ≤ 4 → `****` 全遮；> 4 → `****` + 末 4 位；null/空串原样返回）。trade 删除本地类与本地测试，MapStruct 表达式改指向 ia-common，掩码规则由 ia-common 单测锁定。
2. **IP 解析保留双实现（必要差异 → 保留 + 消歧命名 + 双向互链）**：
   - gateway 侧 `ClientIpUtil` 更名 **`EdgePeerIpResolver`**（`org.icedamericanomall.gateway.util`），表达「边缘对端地址」口径：输入 `ServerWebExchange`，只取 TCP 对端地址，**绝不读任何入站代理头**；结果用于网关限流 Key / AI 配额 Key，并覆写 `X-Client-Ip` 下发。
   - ia-common `IpUtil` 保持「下游消费口径」：优先信任网关注入的 `X-Client-Ip`，再回落代理头，最后 `remoteAddr`。
   - 两类 Javadoc 双向互链，写明**不可合并的理由**（Reactive vs Servlet 类型系统；信任边界相反；ia-common 不引 webflux、网关不引 servlet）与**安全红线**（不得让网关侧改为信任 XFF）。
3. **固化「保留差异」登记**（不合并、只在 Javadoc 写清职责，避免后人重复排查）：
   - `TraceIdFilter` × 2：gateway 为 reactive `GlobalFilter` 且不写 MDC（事件循环下 MDC 会串号）；ia-common 为 servlet `OncePerRequestFilter` 且写 MDC 供日志/ELK。
   - `UserContextFilter#clientIp` 不回落到 XFF：该字段是审计/MDC 展示口径，刻意只认「网关注入头 + remoteAddr」，比 `IpUtil` 的信任链更严。
   - ia-integration 的微信回调 AES-GCM / 极验 HMAC：厂商协议与密钥体系独立，不复用 PII 侧的 `AesGcmUtil` 体系。
   - `PageDTO`（ia-common，含 MyBatis-Plus 类型）vs notification `domain/model/PageResult`（纯 JDK 领域值对象）：层级意图不同，非重复。
4. **代理头回落分支保留（复核结论：非死代码）**：网关恒覆写 `X-Client-Ip`，但「网关对端地址解析失败 → 下发 `unknown`」与「本地/灰度直连服务端口绕过网关」两类路径仍会命中回落，故保留并注释。
5. **`MyMetaObjectHandler`（8 份副本）：本轮不改**。落点跨 8 个业务模块，超出本轮授权模块集（ia-common / gateway / trade）；正确做法是 ia-common 提供 `@ConditionalOnClass(MetaObjectHandler.class)` 的条件装配实现并注册进 `AutoConfiguration.imports`，**先删 8 份副本再启用**（同名 bean 冲突）。作为独立任务交接。

## 备选方案

| 方案 | 优点 | 缺点 |
| :--- | :--- | :--- |
| 账号掩码：归并 ia-common（选定） | 掩码规则单一定义，商家端/管理端口径不会漂移 | 需同步 trade 的 MapStruct 表达式与测试归属 |
| 账号掩码：保留 trade 本地副本 | 零改动 | 与手机号/邮箱掩码形成两套口径，后续必然分叉 |
| IP 解析：合并为单一 ia-common 工具 | 表面「零重复」 | 不可能——ia-common 引 webflux 或网关引 servlet，且会让下游信任口径污染边缘 |
| IP 解析：保留原名 + 仅注释（未选） | 零改名 | `ClientIpUtil` 与 `IpUtil` 同形不同义，误用即安全漏洞 |
| IP 解析：改名 + 双向互链（选定） | 名字自解释、边界写在类注释；改面仅 4 个文件 | 需要一次全仓引用改名（已完成，无残留引用） |

## 后果

- **正面**：掩码规则唯一出口；IP 解析的两种口径有名字、有边界、有互链；「有意保留的差异」形成登记表，减少重复排查；网关 66 / ia-common 54 / trade 75 项测试全绿（clean run）。
- **负面**：`EdgePeerIpResolver` 改名会打断指向旧类名的外部书签；`DesensitizeUtil` 新增方法后 trade 编译依赖 ia-common 新版本（同仓同批构建，无版本障碍）。
- **风险**：①`MyMetaObjectHandler` 8 份副本仍存续——审计字段填充行为不一致的风险仍在（user 多填 `registerTime`），已单独立项；②`X-User-Id` / `X-Client-Ip` 头名字面量仍散布 5 个模块，统一常量化需跨 ia-api / ai-service，本轮未动（详见变更交接摘要）。

## 日期

2026-10-03

## 参考

- 关联 ADR：`024-distributed-lock-consolidation-in-ia-common.md`（同类「能力归并共享内核」先例）、`019-merchant-shop-1n-split.md`（其附带修复引入 merchant 的 `MyMetaObjectHandler`）
- 代码：`ia-common/common/utils/IpUtil.java`、`ia-common/common/utils/crypto/DesensitizeUtil.java`、`gateway/gateway/util/EdgePeerIpResolver.java`、`gateway/gateway/filter/UntrustedHeaderSanitizerFilter.java`、`trade/convert/TradeConverter.java`
- 契约面：HTTP 契约零变化（trade 提现出参脱敏号形状不变）
