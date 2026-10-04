# ADR-035: 买家退款资金流 —— 三通道原路退回、`refund_order` 幂等锚点与 `REFUNDED` 状态机激活

> **修订指针（2026-10-03）**：退款**触发时机**已被 [ADR-037](./037-freight-model-package-split-and-refund-trigger.md) 修订 ——
> 审核同意仅对**仅退款（type=2）**触发退款；**退货退款（type=1）**改在「确认收到退货」
> （`APPROVED(2) → RETURNED(4)`）后触发，并补齐本 ADR「后果」中登记的
> `RETURNED(4) → REFUNDED(5)` 缺失边。本 ADR 的资金链路决策（三通道、幂等锚点、降级定类）不变。

## 背景

售后审核同意（`APPROVED(2)`）此前**没有任何资金动作**：`after_sale.status = 5 REFUNDED` 是**死状态**
（全仓无任何退款实现），`pay-service` 无退款代码，`wallet-service` 只有余额列没有流水表。
即「管理端点同意 = 什么都不发生」，买家拿不到钱，资金链路在售后环节**断开**。

规格已先行定稿（`project-docs/backend/04-Data-Model.md` §十二 12.1、
`project-docs/contract/05-API-Specification.md` §4.7.3 / §4.13.14 / §4.14.15-16），本 ADR 记录实现落地的
技术决策与被规格留白的取舍点。

三条渠道的资金语义天然不同，必须在同一条链路上统一：

| 渠道 | 退款语义 |
| --- | --- |
| `BALANCE` | 本地闭环：wallet 加余额即完成，**无渠道回调** |
| `WECHAT` | 异步：微信受理返回 `PROCESSING`，终态由**退款结果通知**（PAY6）确认 |
| `ALIPAY` | 当前为 Mock：**同步**返回退款结果（无异步分支） |

## 决策

### 1. 退款单 `refund_order` 为唯一事实源，`uk_after_sale` 是幂等锚点

- 一次退款 = 一行 `refund_order`；`after_sale_id` 唯一约束表达不变量「**一售后单一次退款**」。
- `refund_no`（UUID）为业务唯一键：对外定位键、渠道侧幂等键（`out_refund_no`）、
  I41 流水幂等键（`wallet_transaction.transaction_no`）**三处同源同号**，一个号贯穿全链路。
- **幂等点**：I40 先按 `afterSaleId` 查既有单（命中即返回，不新建）；并发建单由唯一约束兜底，
  捕获 `DuplicateKeyException` 后回落为「返回既有单」（天然幂等，不报错给调用方）。
- 状态机 `0-PROCESSING → 1-SUCCESS` / `0 → 2-FAILED` / `2 → 0`（管理端重试），
  全部迁移为**条件更新**（`UPDATE ... WHERE status = 期望值`），返回受影响行数判断是否命中——
  与 `pay_order` 同一套纪律，回调重复 / 重试连点天然安全。

### 2. 三通道在 `RefundManager` 内收敛为一条执行路径，差异只在「是否同步终态」

`ChannelRefundResult(channelRefundNo, settled)` 统一渠道受理结果：
`settled=true` → 立即置成功；`settled=false` → 保持处理中，等回调。

- `BALANCE` → 调 I41 `wallet.refund`（同步入账）→ `settled=true`；
- `ALIPAY` → `AlipayPaymentClient.refund` 同步成功 → `settled=true`；
- `WECHAT` → 微信 v3 退款 API → `PROCESSING` → `settled=false`，由 PAY6 确认。

**渠道协议知识不越界**：退款 API 路径、`amount.total`（必须等于原支付单金额）与
`amount.refund`（本次退款额）的区分、`refund_id`、账单列序与「元 → 分」换算，
全部收口在 `ia-integration` 的通道适配层；`pay-service` 只见强类型结果。

### 3. 退款回调解析：`WechatCallbackResult` **不**扩展，新增独立 `WechatRefundCallbackResult`

退款通知的字段域（`out_refund_no` / `refund_id` / `refund_status`）与支付通知
（`out_trade_no` / `transaction_id` / `trade_state`）完全不同。把两组字段塞进同一个 record
会让「字段在哪个场景有意义」再次含混——正是 ADR-033 要消除的跨服务魔法串问题。

因此：**共用同一套验签 + AES-GCM 解密基建**（`WechatCallbackDecoder` 抽出
`decryptOuterResource` 作为支付/退款回调的公共前置），**分道提取**（`extract` / `extractRefund`），
对外两个 record、两个 `PaymentClient` 入口。PAY6 与 PAY2 应答协议完全一致（ADR-029）。

### 4. 状态口径：处理中/失败**停留 APPROVED**，只有成功才迁移 `REFUNDED(5)`

- 售后单 `APPROVED(2) → REFUNDED(5)` 由 pay-service 退款成功后经 I42
  `PUT /internal/trade/after-sale/{id}/refunded` 条件迁移（0 行 = 幂等空操作）——激活死状态。
- 退款**发起失败**：`refund_order` 置失败 + 记录 `fail_reason`，**异常向上透出**
  （I40 → trade → 管理端），售后单停留 `APPROVED`，由管理端 AD43 重试收敛。
- 新增守卫：`REFUNDED` 是资金终态，`review` 不得再把已退款售后单改回其它状态
  （否则等于「撤销退款」）→ `REFUND_STATUS_INVALID`。

### 5. 降级语义按 ADR-011 三分法逐条定类（**不是一刀切**）

| 调用 | 方向 | 语义 | 理由 |
| --- | --- | --- | --- |
| `RefundClient.refund`（I40） | trade → pay | **fail-closed** | 资金命令；静默降级 = 管理端以为已退、钱未退 |
| `BalanceClient.refund` / `deduct`（I41） | pay → wallet | **fail-closed** | 资金命令；静默成功 = 退款单判成功但钱没到账 |
| `AfterSaleClient.markRefunded`（I42） | pay → trade | **fail-open + 告警** | 下游状态回写；fail-closed 会把**已成功**的退款反判为失败，制造更严重的不一致 |

退款成功路径上的 I42 回写被刻意设计为 fail-open：**宁让售后单晚一点变 REFUNDED
（可由对账/重试补写），也不让一次成功的退款被撤回**。

### 6. 触发器放在**事务外**：控制器 → review（本地事务）→ RefundManager（Feign）

Feign 严禁进事务（硬约束）。`AfterSaleServiceImpl.review` 只做本地状态落库，
`AdminAfterSaleController` 在其返回后调用 `RefundManager.triggerRefund(afterSaleId)`，
`RefundManager` **无 `@Transactional`**。退款编排独立成新 Manager 而不并入
`OrderLifecycleManager`（ADR-025 用例族）：前者是「订单状态机」，后者是「售后资金链路」，
失败语义相反（多数订单补偿 fail-open vs 资金 fail-closed）。

### 7. 跨服务命名策略桥接：`BalanceRefundCO` 显式钉死线字段名

`wallet-service` 的 Jackson 全局策略是 `SNAKE_CASE`，而调用方 `pay-service` 是默认 camelCase。
若两侧各自按全局策略编解码，字段会**静默为 null**（不报错、直接算 0 元）。
因此 I41 的请求对象（ia-api `BalanceRefundCO` 与 wallet 侧 `BalanceRefundReq`）
**两侧都用 `@JsonProperty` 显式钉死线字段名**（snake_case），线协议不再依赖任一服务的全局配置。
I40/I42 两端均为 camelCase，无此问题（契约中已注明）。

## 备选方案

| 方案 | 优点 | 缺点 |
| :--- | :--- | :--- |
| 把退款塞进 trade（售后直接改余额） | 少一个服务一跳 | 违反聚合/限界上下文：资金域归 pay + wallet；且微信退款 SDK 依赖会污染 trade |
| 扩展 `WechatCallbackResult` 增加退款字段 | 不新增类型 | 两组字段域混在同一 record，语义含混（ADR-033 的反面） |
| 退款单按 `order_no` 做幂等（允许多次部分退款） | 支持多次部分退款 | 与已定稿规格（`uk_after_sale` 一售后单一次退款）冲突；需先改规格 |
| I40 由 trade **同步**等待渠道终态 | 调用方一次拿到终态 | 微信退款是异步的，同步等待会长期占用连接（且 Feign 超时 5s） |
| I42 回写 fail-closed | 状态强一致 | 会把已成功的退款判失败（钱已退、状态被改回），更糟 |
| **`refund_order` + 三通道 `settled` 收敛 + 分类降级（采纳）** | 一条链路覆盖三通道；幂等点单点清晰；降级语义与资金风险匹配 | 新增 1 表 + 1 Manager + 2 个 Feign 契约 + 1 个回调端点 |

## 后果

**正面**

- 售后环节的资金断链修复：`REFUNDED(5)` 从死状态变为可激活的终态；
- 幂等点单点清晰且全链路同号复用：`uk_after_sale`（一次退款）、`refund_no`（渠道 + 流水同号）、
  条件更新（状态迁移）、I41 `transaction_no`（入账一次）——重复请求/重复回调/连点重试全部安全；
- 渠道协议知识零越界：退款 API、金额双字段口径、账单列序与元转分都在 `ia-integration`；
- 降级语义与资金风险逐条匹配（资金 fail-closed / 回写 fail-open），且各有单测锁死；
- 微信退款回调解密复用 ADR-033 基建，可用固定密钥做全链路固件测试（不依赖证书/线上）。

**负面 / 风险**

- **退货退款链路的 `REFUNDED` 仍未激活**：规格把 I42 定义为 `APPROVED(2) → REFUNDED(5)`，
  而 `RETURNED(4) → REFUNDED(5)` 这条边（退货退款）不在本次范围内——退货退款售后单走到
  `RETURNED` 后，退款成功回写会是「0 行幂等空操作」，售后单停在 `RETURNED`。
  需后续任务补齐（或在 status 条件中加入 `RETURNED`），本次严格按契约实现未擅自放宽；
- **pay 侧看不到售后单**：I40 的「售后单不存在 → NO_RESOURCE_FOUND」在 pay 侧以
  **支付单存在性 + 金额 ≤ 支付单金额**代理校验（pay 不持有售后域）。售后单的存在性由 trade 侧保证；
- **`BALANCE` 渠道无回调兜底**：若「wallet 入账成功」后 `markSuccessIfProcessing` 本地失败，
  退款单会停在处理中且没有回调来推进（AD43 只允许失败单重试）。该窗口需要本地库不可用才出现，
  属极端情形；已在对账（F4）里留了人工兜底的手工处置入口；
- **I41 并发同号入账：wallet 侧选择「抛出让本地事务回滚」而非「直接返回成功」**。
  规格（04 §十二 12.1）表述为「`transaction_no` 冲突视为重复请求，直接幂等返回成功」，
  实现分两段：**顺序重放**（`existsByTransactionNo` 命中）按规格幂等成功返回；
  **真并发**（两请求同时穿过前置判定、其中一个 `uk_transaction_no` 冲突）则抛
  `BizException` 让本次「加余额 + 写流水」整体回滚。原因：`credit` 与 `insert` 已在同一事务里，
  捕获冲突后要「只撤销本次加余额、却提交流水」在无保存点的单事务内无法可靠表达
  （要么双记、要么事务被标记 rollback-only 而提交期报错）。代价是一次**可收敛的伪失败**——
  pay-service 把退款单置失败(2)，管理端 AD43 重试即命中幂等分支转成功，**资金不会重复入账**
  （数据正确性优先于调用方一次成功）。此为有意取舍，已由 `WalletManagerTest` 双向锁死
  （顺序重放幂等成功 / 并发冲突回滚）；若后续要求严格对齐规格字面，需改为
  「先插流水（幂等锚点）后加余额」的次序；
- **`fail_reason` 承载渠道错误摘要**：已截断到 200 字符且只取 message（不含渠道报文细节），
  但渠道文案仍可能过于笼统（排查需结合日志的 `refundNo`）。

## 日期

2026-10-03

## 参考

- `Implementation/back-end/pay-service/src/main/java/org/icedamericanomall/pay/app/RefundManager.java`（退款编排：幂等、三通道、失败补偿、I42 回写）
- `Implementation/back-end/pay-service/src/main/java/org/icedamericanomall/pay/service/impl/RefundOrderServiceImpl.java`（条件更新状态机）
- `Implementation/back-end/pay-service/src/main/java/org/icedamericanomall/pay/adapter/web/RefundController.java` + `WechatCallbackSupport`（PAY6 应答协议）
- `Implementation/back-end/ia-integration/src/main/java/org/icedamericanomall/integration/payment/`（`WechatRefundCallbackResult` / `ChannelRefundResult` / `WechatCallbackDecoder.decodeRefund` / `PaymentClient.refund`）
- `Implementation/back-end/wallet-service/src/main/java/org/icedamericanomall/wallet/app/WalletManager.java`（I41 幂等入账 + 流水同事务）
- `Implementation/back-end/trade-service/src/main/java/org/icedamericanomall/trade/manager/RefundManager.java` + `adapter/internal/InternalAfterSaleController.java`（触发与回写）
- `Implementation/back-end/database/migrations/V5.5__refund_wallet_tx_and_reconciliation.sql`（`refund_order` / `wallet_transaction`）
- ADR-033（微信 v3 回调验签/解密归适配层 —— 本 ADR 的退款回调复用其基建）
- ADR-029（渠道回调协议优先于 Result 信封 —— PAY6 应答口径）
- ADR-011（Feign 降级语义三分法 —— 本 ADR 的降级定类依据）
- ADR-004（内部端点异常透出）、ADR-025（Manager 按用例族划分）
