# ADR-011: Feign fallback 降级语义三分法（全 Client 覆盖 + 判别型查询 fail-closed）

## 背景

ADR-007 为 verification-service 定下了「安全优先 fail-closed」的降级原则，但该原则未推广到其余 Client。V5.2 审计发现两类问题：

1. **覆盖缺口**：15 个 `@FeignClient` 中只有 9 个声明了 `fallbackFactory`。其中 `CartClient` / `OrderClient` 的降级类**已存在但未挂载**（死代码，永不生效），`SkuClient` / `ProductDetailClient` / `SearchClient` / `UserProfileClient` 则完全缺失降级实现。
2. **语义错配**：既有降级实现里存在「静默成功」，后果落在数据一致性上：
   - `SkuClient.deductStock` 若静默返回，订单会正常落库但库存从未扣减 → **超卖**；
   - `OrderClient.updateOrderStatus` 若静默返回，用户已付款而订单停在「待付款」，超时任务会按未支付取消 → **钱货两空**；
   - `CartClient.deleteByIds` 若静默返回，已下单商品仍留在购物车 → **可重复下单**。

更隐蔽的是第三类：**判别型查询**。这类查询的「空结果」会被调用方解读为一条业务事实，降级返回空值等于向用户谎报事实：

- `CartClient.getSelectedItems` 返回空列表 → 调用方报「购物车为空，无法下单」（用户明明有货）；
- `SkuClient.getSkuListByIds` 返回空列表 → 三个下单策略齐报「SKU 不存在」；
- `ProductDetailClient.getProductDetail` 返回 `data=null` → AI 助手答「未找到该商品」。

## 决策

**按调用方对「空结果」的解释方式分三类，逐方法确定降级行为**（详见 `ia-api` 的 `org.icedamericanomall.fallback/package-info.java`）：

| 类别 | 判定标准 | 策略 |
| :--- | :--- | :--- |
| ① 展示型查询 | 空结果只让调用方「少展示一点」，不产生错误业务判断 | 返回空集合 / 非成功 `Result` + WARN |
| ② 判别型查询 | 空结果会被解释为业务事实（购物车为空 / SKU 不存在 / 订单不存在） | 抛 `BizException`（fail-closed） |
| ③ 命令（写） | 任何状态变更：落库、扣减、投递、状态流转 | 一律抛 `BizException`，禁止静默成功 |

补充约束：

1. **全量覆盖**：15 个 Client 全部声明 `fallbackFactory`，并统一补 `contextId`（同名服务多 Client 必须区分，否则 `IllegalStateException: Multiple Feign Clients with the same name`）。
2. **不全局开启熔断**：`fallbackFactory` 只在服务自行打开 `spring.cloud.openfeign.circuitbreaker.enabled=true` 时生效。是否启用熔断是各服务对下游可用性容忍度的运行时决策，ia-api 只声明契约侧语义，不替服务决定（**发布时**仅 authorization-service 开启；2026-10-03 已扩至 8 服务，见文末补记与 [ADR-022](./022-feign-circuitbreaker-enablement-marketing-ai.md)）。
3. **降级日志为 ERROR 级**：降级是可观测性事件，WARN 不足以触发告警；日志须带可定位的业务键且禁止打印完整手机号 / 邮箱。
4. **Bean 扫描前提**：启用熔断的服务必须把 `org.icedamericanomall.fallback` 纳入 `scanBasePackages`，否则启动期报 `No fallback instance of type ... found`。

## 备选方案

| 方案 | 优点 | 缺点 |
| :--- | :--- | :--- |
| 全部 fail-soft（查询返回空、命令静默成功） | 实现最简、调用方无感知 | 制造错误信息污染与数据不一致（超卖 / 重复下单），不可接受 |
| 全部 fail-closed（一律抛异常） | 语义统一、绝不错报 | 展示型查询（搜索 / 用户画像）会让 AI 助手整轮对话失败，收益为负 |
| **三分法 + 逐方法说明理由（采用）** | 既不错报事实也不误伤可降级能力 | 需要逐个 Client 分析调用方语义，评审成本略高 |

## 后果

- ✅ 15/15 Client 具备降级实现且语义显式化；`package-info` 内维护「逐 Client 降级语义速查表」作为单一事实来源
- ✅ 修复 3 处「静默成功即数据不一致」的潜在事故（超卖 / 钱货两空 / 重复下单）与 3 处错误信息污染
- ✅ 本轮为**纯声明式变更**：未开启熔断的服务行为完全不变（**发布时**仓内仅 authorization-service 开启，且它只使用原已挂载的 3 个 Client；2026-10-03 状态见补记）
- ⚠️ 启用熔断的服务若漏配 `scanBasePackages` 会启动失败（属 fail-fast，已在 package-info 与各 Client Javadoc 标注）
- ⚠️ 未做实机熔断验证（需先开启 circuitbreaker），建议后续在 authorization-service 上以 Chaos 方式验证降级路径

## 补记（2026-10-03）

「当前仅 authorization-service 开启熔断」的事实陈述已于 2026-10-03 过期。经三轮修复（含 [ADR-022](./022-feign-circuitbreaker-enablement-marketing-ai.md)）后，**已开启 `spring.cloud.openfeign.circuitbreaker.enabled=true` 的服务清单为：authorization / user / merchant / verification / marketing / ai / pay / logistics（8 个）**，统一参数口径 TimeLimiter 5s + 失败率阈值 50%。trade-service 维持 `feign.sentinel.enabled`（其 SentinelConfig 属性键不匹配、规则从未注册——降级可用、熔断参数未生效）。

## 日期

2026-09-29

## 参考

- `ADR-007`（verification-service fail-closed 先例）
- `Implementation/back-end/ia-api/src/main/java/org/icedamericanomall/fallback/package-info.java`（降级规范正文）
- `project-docs/collaboration/09-constraints.md`（日志脱敏 / 异常体系约束）
