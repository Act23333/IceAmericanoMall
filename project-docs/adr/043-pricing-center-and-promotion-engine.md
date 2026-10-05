# ADR-043: 价格计算中心与促销引擎 —— 满减/满折统一计价、叠加口径与分摊边界

## 背景

金额不变式 V2（ADR-037）落定 `payAmount = totalAmount − discountAmount + postFee` 之后，`discountAmount` 的**来源**只有用户券一种（单券，策略层 `applyCoupons` 计算）：促销侧只有秒杀（直改 SKU 价）与优惠券，缺大厂电商第一促销形态「满减/满折」；且优惠计算逻辑散在 trade 下单策略里，未来多券叠加（开放项 O1）、会员价等优惠源没有统一归宿。P1 规划「促销引擎 + 价格计算中心」即在 trade 下单链路内抽统一计价入口（`PricingService`），并新增店铺级满减/满折活动模型（marketing `promotion` 表）。

## 决策（2026-10-05）

### 1. 促销活动模型：店铺级、时间窗推导状态（marketing `promotion` 表）

- `type`：1=满减（FIXED）/ 2=满折（PERCENTAGE）——语义命名与券 `CouponTypeEnum` 对齐；`threshold`（门槛，分）+ `discountValue`（满减=减免分；满折=万分比 8500=8.5折）+ `maxDiscount`（满折封顶分，0=不封顶）。
- **Phase 1 店铺级**（`seller_id` 必填）：跨店下单已禁（`CROSS_STORE_FORBIDDEN`），一单仅触达单商家，店铺级即闭环；平台级/商品级 scope 留 Phase 2。
- 状态由 `startTime`/`endTime` 时间窗推导（1/2/3），复用 AD40-42 秒杀先例（含已结束禁编、进行中禁删、`@OperationLog` 审计）。

### 2. 计价口径（PricingService，trade 价格计算中心）

- **优惠源与叠加**：店铺促销（I44）与会员折扣（I45）与用户券三者**可叠加**、逐项独立计算；**同类型促销取减免金额最大者**（同店铺命中多个生效活动不叠加——与券叠加是两回事）；总额守卫 **`discountAmount ≤ totalAmount`**（合计超限时以 totalAmount 封顶，payAmount 恒 ≥ 0）。
- **秒杀单不叠加**：秒杀价本身已是深度优惠，维持现状 `discountAmount = 0`（`FlashSaleOrderStrategy` 不进入计价中心）。
- **计算次序**：促销（基于 totalAmount）→ 会员折扣（基于 totalAmount）→ 券（基于 totalAmount 门槛，收编既有 `applyCoupons`）→ 合计封顶。各项优惠基于原价门槛而非逐级递减后金额——口径简单、用户可理解、可单测锁定。
- **分摊边界（Phase 1 订单头汇总）**：优惠合计落 `orders.discount_amount`（既有列），**不做 order_item 级分摊**——item 级分摊（影响按明细退款金额）与多券分摊规则同源，一并挂账开放项 O1 /《多券金额分摊规则》PRD。
- **形态**：纯函数计算器（trade `domain/service`，零框架依赖，对齐 `PostageCalculator` 先例）+ app 层解析器（Feign 解析生效促销/会员等级，事务外）。既有券计算 `applyCoupons` 收编进计价中心，策略层不再散落优惠逻辑。

### 3. I44 降级定类：fail-closed（与 I43 同口径）

`POST /internal/promotion/query`（trade → marketing）返回值被解释为业务事实「该店铺有/没有生效促销」。静默降级空集合 = 「故障伪装成无促销」：用户应得优惠被吞、且与促销配置产生价格不一致。故 fallback 抛 `BizException(SYSTEM_BUSY)` 使下单失败可重试；「无生效活动 → 空集合」是合法业务态，与查询失败严格区分。

## 备选方案

| 方案 | 优点 | 缺点 |
| :--- | :--- | :--- |
| 满折用 `BigDecimal` 存折扣率 | 直观 | 违反「金额一律整数分、不用 BigDecimal」硬约束；万分比整数无精度分支 |
| 多活动叠加（同类型也叠） | 优惠力度大 | 优惠叠加无边界、易被凑单套利；Phase 1 取最优者口径简单可控 |
| 优惠直接改 subTotal（秒杀模式） | 复用现结构 | 破坏「totalAmount = Σ subTotal 原价」不变式；快照语义混乱 |
| I44 fail-open（降级无促销） | 下单可用性高 | 故障伪装成无促销，用户应得优惠静默丢失（判别型查询，ADR-011 定类 fail-closed） |
| item 级优惠分摊随本期落地 | 退款精确到明细 | 与多券分摊规则同源，规格未定稿（O1）；Phase 1 订单头汇总够用 |
| **店铺级 + 时间窗 + 取最优 + 订单头汇总 + I44 fail-closed（采纳）** | 口径简单可单测；与既有先例（flash/I43）同构；多券/会员价有统一归宿 | C 端商品页促销标签未做（Phase 2）；item 级分摊挂账 O1 |

## 后果

**正面**

- 优惠计算有唯一归宿（PricingService），策略层不再散落；多券（O1）、会员价、未来优惠源全部接同一入口；
- 计价口径可被纯函数单测逐条锁定（门槛边界/封顶/封顶叠加/守卫）；
- 促销活动管理复用秒杀全套先例（时间窗推导/守卫/审计），零新模式。

**负面 / 风险**

- **C 端商品页无促销标签**（Phase 1 优惠在下单时自动应用、OrderVO 可见）——用户在下单前不可见满减信息，转化率收益打折；
- 优惠汇总在订单头，**按明细退款的金额精度**依赖退款申请额校验（≤实付），非精确到 item（O1 挂账）；
- 满折万分比口径需要管理端录入者理解（8500=8.5折）；
- 促销查询每单一次跨服务调用（marketing 读热点），缓存待按 I43 缓存先例（ADR-041）按需补。

## 日期

2026-10-05

## 参考

- `Implementation/back-end/marketing-service/src/main/java/org/icedamericanomall/marketing/`（`promotion` 域 + AD48-50 + I44）
- `Implementation/back-end/trade-service/src/main/java/org/icedamericanomall/trade/domain/service/PricingCalculator.java` + `app/order/`（价格计算中心）
- `Implementation/back-end/ia-api/`（`PromotionClient` fail-closed / `MemberClient` fail-open）
- ADR-037（金额不变式 V2 / I43 fail-closed 先例）、ADR-041（I43 Caffeine 缓存先例）、ADR-011（降级三分法）、ADR-025（用例族）、ADR-038（四层）
