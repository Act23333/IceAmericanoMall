# ADR-022: marketing-service / ai-service 开启 Feign 熔断（resilience4j）与参数口径

## 背景

ADR-011 已完成 15/15 `@FeignClient` 的 `fallbackFactory` 全覆盖，并明确「是否启用熔断是各服务对下游可用性容忍度的运行时决策」，落地时**仅 authorization-service 开启了** `spring.cloud.openfeign.circuitbreaker.enabled=true`。

该状态留下两处缺口：

1. **降级永不生效**：`fallbackFactory` 只在服务打开 `circuitbreaker.enabled` 后才生效。marketing-service（用 `OrderClient` 落秒杀订单）与 ai-service（用 `OrderClient`/`ProductDetailClient`（**现名 `SpuDetailClient`**，ADR-059）/`SearchClient`/`UserProfileClient` 支撑 Agent 工具链）都声明了降级实现，但从未开启熔断——下游不可用时调用只能一路阻塞到网络超时，秒杀链路把「长时间挂起」暴露给用户，AI 工具链把「挂起」放大会话延迟。
2. **直接开启会误伤（关键陷阱）**：Spring Cloud 的 `CircuitBreaker` 集成会把 Feign 调用包进 resilience4j 的 TimeLimiter，**缺省超时仅 1 秒**。按 ADR-011 的三分法，写命令与判别型查询的降级是 fail-closed（抛 `BizException`），这意味着任何超过 1 秒的正常慢调用都会被判失败并降级——对 marketing 而言是「订单已落库但秒杀接口报失败」的假故障，对 AI 而言是工具链被强行截断返回空数据。因此「开启」必须与「放宽超时并显式给出熔断阈值」同时发生。

前提条件已满足（无需改动）：两个服务的 `@SpringBootApplication(scanBasePackages=...)` 均包含 `org.icedamericanomall.fallback`（ADR-009/011 的启动期前提）。

## 决策

1. **开启熔断**：marketing-service、ai-service 的 `application.yml` 增加
   `spring.cloud.openfeign.circuitbreaker.enabled: true`。
2. **依赖（BOM 管版本）**：两服务 POM 增加
   `org.springframework.cloud:spring-cloud-starter-circuitbreaker-resilience4j`，不写版本号（由 Spring Cloud 2025.0.1 BOM 管理），与 authorization-service 一致。
3. **参数口径显式化**（避免缺省 1s 误降级），与 authorization-service 保持同一口径：

   | 参数 | 取值 | 理由 |
   | :--- | :--- | :--- |
   | `timelimiter.configs.default.timeout-duration` | `5s` | 覆盖「写库落单」「商品/搜索慢查询」的正常耗时带上限（缺省 1s 过短） |
   | `circuitbreaker.configs.default.sliding-window-size` | `20` | 统计窗口（调用次数） |
   | `minimum-number-of-calls` | `10` | 达到该样本量才计算失败率，避免冷启动误熔断 |
   | `failure-rate-threshold` | `50`（%） | 失败率阈值 |
   | `wait-duration-in-open-state` | `30s` | 熔断保持时长 |
   | `permitted-number-of-calls-in-half-open-state` | `3` | 半开试探调用数 |

4. **不改降级语义**：降级行为仍由 ia-api 的 `fallbackFactory` 按 ADR-011 三分法决定；本次只让既有语义「能够触发」。

## 备选方案

| 方案 | 优点 | 缺点 |
| :--- | :--- | :--- |
| 维持不开启（沿用 ADR-011 落地时的状态） | 行为零变化、无回归风险 | 降级实现继续是死代码；下游故障时调用长挂起（秒杀接口挂起 / AI 会话卡顿），无快速失败 |
| 开启但使用 resilience4j 缺省参数 | 改动最小（一行开关） | 缺省 TimeLimiter 1s 会把正常慢调用判为失败，fail-closed 降级直接把「成功」变「失败」——秒杀落单假失败、AI 工具链假空数据，不可接受 |
| 只对个别 Client 关闭容错 | 精细 | Spring Cloud 的 `circuitbreaker.enabled` 是服务级开关，不支持按 Client 粒度关闭；服务内也无法对个别 Client 单独设置（需自建 `CircuitBreakerFactory` 定制，收益不抵成本） |
| **开启 + 显式参数口径 + 两服务同口径（采用）** | 降级真正生效且只在真实不可用时触发；与既有 authorization-service 口径统一，运维认知一致 | 熔断打开期间失败会以 `BizException` 快速返回，需靠监控区分「真故障」与「熔断中」 |

## 后果

- ✅ marketing 秒杀落单（`OrderClient.createOrder`，降级 fail-closed）从「挂起」变为「快速失败 + 明确错误码 `FLASH_SALE_FAILED`」，与既有的「预扣库存由超时/对账兜底」设计一致
- ✅ ai-service 四个 Client 的展示型/判别型降级按 ADR-011 语义真实生效（展示型返回空、判别型抛异常），不再把网络故障转嫁给会话延迟
- ✅ 两服务与 authorization-service 熔断参数口径一致，避免「同名不同行为」的运维歧义
- ⚠️ ADR-011 「当前仅 authorization-service 开启」的事实陈述已过期，需 doc-engineer 同步（本 ADR 记录新的服务清单：authorization / marketing / ai）
- ⚠️ 仍未做实机熔断验证（同 ADR-011 遗留项）：建议后续在 marketing-service 上以 Chaos 方式（停 trade-service 实例）验证 `FLASH_SALE_FAILED` 快速失败路径与熔断半开恢复
- ⚠️ 熔断打开时用户侧看到的仍是业务错误码，需要在告警侧区分（resilience4j 指标已随 Actuator/Prometheus 暴露）

## 补记（2026-10-03）

交接核对补记：与 marketing / ai **同决策、同参数口径**开启 Feign 熔断的服务还有 **pay-service、logistics-service**（本 ADR 交付摘要未单独记录，特此补记）；连同更早批次已开启的 user / merchant / verification，**当前启用清单为 authorization / user / merchant / verification / marketing / ai / pay / logistics（8 个）**。ADR-011 的「当前仅 authorization-service 开启」陈述已由 doc-engineer 按本清单修订。

## 日期

2026-10-03

## 参考

- `project-docs/adr/011-feign-fallback-degradation-semantics.md`（降级语义三分法 + 服务级 opt-in 决策）
- `project-docs/adr/009-package-namespace-unification.md`（`scanBasePackages` 前提）
- `Implementation/back-end/authorization-service/src/main/resources/application.yml`（参数口径来源）
- `ia-api` 的 `org.icedamericanomall.fallback/package-info.java`（逐 Client 降级语义速查表）
