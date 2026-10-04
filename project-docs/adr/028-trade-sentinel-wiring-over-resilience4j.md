# ADR-028: trade-service 走 Sentinel 接线修复而非迁移 resilience4j

## 背景

平台的服务熔断/限流栈在 2026-10 前后**收敛到 resilience4j**（ADR-011 / ADR-022：authorization、user、
merchant、verification、marketing、ai、pay、logistics 均启用
`spring.cloud.openfeign.circuitbreaker.enabled=true`）。**trade-service 是唯一保留
Sentinel 的服务**：`application.yml` 里 `feign.sentinel.enabled: true`，
下单入口 `OrderCreationManager#createOrder` 用 `@SentinelResource(value = "createOrder",
blockHandler = "createOrderBlockHandler")` 保护。

排查下单入口保护时发现一个**接线缺陷（P1，保护静默失效）**：

1. `SentinelConfig` 的激活条件写作 `@ConditionalOnProperty(name = "sentinel.enabled")`，
   而真正生效的属性是 Spring Cloud Alibaba 的 **`spring.cloud.sentinel.enabled`**
   （yml 中配置在 `spring.cloud.sentinel` 下）。键名不一致 → **配置类从未被装配**
   → `FlowRule` / `DegradeRule` 从未注册；
2. 规则没有注册时，`@SentinelResource` 无规则可判 → **不 block、不降级**，
   `blockHandler` 永不触发。也就是说 `createOrder` 的「QPS 流控 + 异常比例熔断」
   在 V5.4 之前**完全不生效**，而下单链路恰恰是全平台最需要入口保护的写热点。

该缺陷的定性是**配置键写错导致的接线失效（dead wiring）**，不是「选错了技术」。

## 决策

**在 trade-service 就地修复 Sentinel 接线，继续使用 Sentinel；本轮不做 resilience4j 迁移。**

1. 激活条件对齐 SCA 属性键：`@ConditionalOnProperty(name = "spring.cloud.sentinel.enabled",
   havingValue = "true", matchIfMissing = true)`（`matchIfMissing` 保持「未显式声明即启用」的既有语义）；
2. `@PostConstruct` 注册两条规则（阈值一律常量化，无魔法值）：
   - **flow**：资源 `createOrder`，`FLOW_GRADE_QPS`，阈值 `100`；
   - **degrade**：`DEGRADE_GRADE_EXCEPTION_RATIO`，阈值 `0.5`，`timeWindow=10s`，
     `minRequestAmount=5`，`statIntervalMs=10s`；
3. 流控与熔断**共用同一个 blockHandler**，命中即抛 `BizException(ErrorCode.FREQUENT_ERROR)`
   （HTTP 429）；`BlockException` 不参与 Sentinel 自身异常统计，故 blockHandler 抛出的业务异常
   不会回灌熔断统计（避免「限流 → 熔断」级联误判）；
4. 资源名以常量 `SentinelConfig.RESOURCE_CREATE_ORDER` 单一来源，注解与配置类不得各写一份字面量；
5. **不动 Feign 侧语义**：`feign.sentinel.enabled=true` 与 ia-api 的 fallbackFactory 组合保持不变
   （ADR-011 的降级三分法在 trade 侧继续由 Sentinel 承载）。

## 备选方案

| 方案 | 优点 | 缺点 |
| :--- | :--- | :--- |
| 维持现状（文档记为「规则未注册、保护惰性」） | 零改动 | 下单入口对流量尖峰与异常放大**毫无保护**；`@SentinelResource` + 规则类成为误导性死代码，比没有更危险 |
| 删除 Sentinel（只留网关限流） | 单一入口治理、代码最简 | 网关限流是粗粒度路径级，无法表达「下单资源 QPS」与「下单异常比例熔断」；异常放大（下游库存/优惠券抖动）时缺少本地兜底 |
| **修复接线，保留 Sentinel（采纳）** | 改动局部（一个属性键 + 规则注册已存在）；flow + degrade 一次配齐、统一 block 出口（429）；不触碰 Saga 与 Feign 降级语义 | 平台内并存两套熔断栈（Sentinel + resilience4j），认知成本与依赖体积双份 |
| 迁移 trade 到 resilience4j（对齐其他服务） | 全平台单一熔断栈 | 需引入 `resilience4j-ratelimiter`（resilience4j 无「QPS 流控」原生概念，与 circuitbreaker 语义不同）并用 `@CircuitBreaker`/`@RateLimiter` 拆分注解；需同时翻转 `spring.cloud.openfeign.circuitbreaker.enabled`，**改变订单 Saga 的降级语义与超时口径**（原 Sentinel 侧 fallback 行为随之中断）；为修一个属性键缺陷而引入跨切面行为变更，风险与收益不成比例 |
| 只改属性键，不注册规则（依赖 Dashboard 下发） | 无需代码内规则 | 项目明确不启用 Sentinel Dashboard（`transport` 注释掉），规则无处下发 → 保护仍为空 |

## 后果

**正面**

- 下单入口的 QPS 流控与异常比例熔断**真正生效**，`blockHandler` 从死代码变为有效出口（429）；
- 阈值、窗口、最小请求数全部常量化（`SentinelConfig` 静态常量），避免魔法值；
- 缺陷成因（属性键不匹配导致配置类静默不加载）以注释形式固留在 `SentinelConfig` 与 `application.yml`，
  防止后续「顺手改名」再次静默失效；
- 不触碰 Saga 补偿与 Feign 降级语义，订单链路回归风险最小。

**负面 / 风险**

- **双栈并存**：trade（Sentinel）与其余服务（resilience4j）技术栈不统一，属于**已接受的既有不一致**；
  收敛路径见下；
- 规则为进程内静态规则（无 Dashboard、无持久化），重启后依赖 `@PostConstruct` 重新注册；
  多实例各自限制（阈值是**单机** QPS，不是集群总量），扩容后集群总阈值线性放大 —— 需容量规划时知晓；
- 规则变更需改代码重新发布（无动态数据源），适合当前「阈值稳定」的现状，不适合频繁调参场景；
- `minRequestAmount=5` 意味着低流量下不会熔断（避免误熔断），也意味着极低频的异常不会被熔断保护。

**收敛路径（本次不做，明确留档）**：若后续要求全平台单一熔断栈，应先确认「下单入口 QPS 流控」
能否由网关层承载（网关已有 `RequestRateLimiter`），把粗粒度流控上移、本地只保留熔断，
再以 resilience4j 替换 Sentinel 的 degrade 能力，并同步评估 ia-api fallback 语义与订单 Saga 降级口径。

## 日期

2026-10-03

## 参考

- `Implementation/back-end/trade-service/src/main/java/org/icedamericanomall/trade/config/SentinelConfig.java`（属性键修复 + 规则常量）
- `Implementation/back-end/trade-service/src/main/resources/application.yml`（`spring.cloud.sentinel.*` 与 `feign.sentinel.enabled`）
- `Implementation/back-end/trade-service/src/main/java/org/icedamericanomall/trade/manager/OrderCreationManager.java`（`@SentinelResource` + `blockHandler`）
- `Implementation/back-end/trade-service/src/test/java/org/icedamericanomall/trade/config/SentinelConfigTest.java`
- ADR-011（Feign fallback 降级语义三分法）、ADR-022（marketing / ai 开启 resilience4j 熔断的参数口径）
