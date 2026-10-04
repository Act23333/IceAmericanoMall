# ADR-041: P0 资金线风险收敛 —— 退款悬挂补偿、售后审核守卫收紧、I43 计价缓存

## 背景

[ADR-035](./035-refund-fund-flow-and-refunded-activation.md) / [ADR-036](./036-channel-reconciliation-record-only-and-rewrite-idempotency.md) / [ADR-037](./037-freight-model-package-split-and-refund-trigger.md) 三份 P0 ADR 的「后果/负面风险」中，三项可行动缺点在交付后仍处于开放状态：

1. **退款单 PROCESSING 悬挂无兜底（ADR-035 风险 3）**：微信渠道退款受理后 `settled=false`，终态只靠 PAY6 回调推进；回调丢失/未达即永久停留处理中，而管理端 AD43 只允许「失败(2)」单重试（聚合不变量 `assertRetryable`），处理中单没有任何收敛路径。ADR-035 登记的「BALANCE 渠道无回调兜底」与微信回调丢失是同一缺口的两种形态。
2. **review 对 RETURNED 无守卫（ADR-037 风险 6）**：`AfterSaleManager.review` 仅守 `REFUNDED`，管理员可把已确认退货（RETURNED）的售后单改回其它状态——资金不重复（`uk_after_sale` 兜底），但状态语义可被管理端扰动。
3. **I43 运费模板查询无缓存（ADR-037 风险 4）**：每次下单一次跨服务查询，高频下单使 item-service 形成读热点。

其余登记项保持原状：`RETURNED(4) → REFUNDED(5)` 缺失边已由 ADR-037 §4 关闭；契约偏差（AD46 批次列表、I43 fail-closed、错误码 3040/3041、FT3 非分页）已在 05 规格 V5.6.1 落地说明中同步；I41 并发伪失败、pay 侧代理校验、真实账单/真实渠道链路联调属有意取舍或需外部条件，继续按原 ADR 登记。

## 决策（2026-10-04）

### 1. 悬挂补偿任务：渠道查询三态收敛 + 同步通道幂等重放（`RefundResumeManager` + `RefundResumeJob`）

- **筛选口径**：`status=0` 且 `create_time < now()-10min`，按创建时间升序、单轮 ≤100 单——悬挂时长容忍一次正常回调窗口（微信退款通知通常秒级达），批量上限防任务长跑；两值均为带理由注释的常量。
- **WECHAT → 查询收敛**：新增 `PaymentClient.queryRefund(outRefundNo)`（微信 v3 `GET /v3/refund/domestic/refunds/{out_refund_no}`，`WechatPaymentClient` 实现；无查询能力的渠道接口 default 抛 `UnsupportedOperationException`）。渠道状态归一化为 `ChannelRefundState`（`SUCCESS` / `FAILED` / `PROCESSING`；微信 `CLOSED`/`ABNORMAL` 归 FAILED），**原始状态串不出 ia-integration**：
  - SUCCESS → 复用既有共享结算路径（`markSuccessIfProcessing` 条件置成功 + I42 fail-open 回写），与 PAY6 回调同一入口，不复制粘贴；
  - FAILED → `markFailedIfProcessing`（fail_reason 记「渠道退款查询终止: <state> refundId=<id>」，走既有截断），收敛为可重试失败单交管理端 AD43；
  - PROCESSING → 跳过（log.debug），下轮再看。
- **BALANCE / ALIPAY → 幂等重放**：两渠道本应同步终态，悬挂即「渠道成功与本地落账之间崩溃」的窗口——重新走既有 `requestChannel`（wallet `transaction_no=refundNo` 重放幂等返回 / 支付宝 Mock 同步成功）→ 条件置成功 + 回写。支付单缺失 → 置失败（"支付单不存在"）使 AD43 可见；未知渠道 → log.error 跳过，不自动置失败。
- **不自动出款**：FAILED 不自动重试，收敛依赖管理端 AD43（补偿任务不做无人审批的二次出款申请）。
- **调度**：XXL-Job `resumeStuckRefunds`（`RefundResumeJob`，dev `xxl.job.enabled=false` 不自动运行，与 `ReconciliationJob` 同口径）；批量逐单 try/catch，单笔失败 log.error 后继续，不中断整批。
- **幂等性**：全部落账走既有条件更新（非处理中命中 0 行即空操作），重复调度 / 与回调并发安全。

### 2. 售后审核守卫收紧：RETURNED 列入禁改来源

`review` 在既有 `REFUNDED` 守卫后增加 `RETURNED(4)` 守卫（复用 `REFUND_STATUS_INVALID(3039)`，文案「售后单已确认退货、退款流程进行中，不可再审核」）。RETURNED 的唯一合法去向是 `markRefunded`（I42）回写为资金终态 `REFUNDED(5)`；审核动作只对 `PENDING` 有意义。错误码复用不新增（管理端仅多一类可读拒绝文案，无契约变化）。

### 3. I43 计价查询加 Caffeine 短 TTL 本地缓存（trade 侧）

- `FreightPostageResolver` 内建 `LoadingCache<Long, List<FreightTemplateCO>>`（key=sellerId，`expireAfterWrite=60s`、`maximumSize=10_000`，均为带理由常量），loader 以「sellerId + 空 templateIds」调 I43 取**全量启用集合**（I43 契约既有语义：空数组=仅按 sellerId 查，返回启用集合+兜底默认模板）；「绑定命中→默认模板回落→无模板=0」本地解析逻辑不变；
- **fail-closed 保持（ADR-011 判别型查询）**：loader 异常向上传播且不缓存失败（下次调用重新发起查询），查询失败仍使下单失败，绝不静默免邮；
- 空集合（商家无启用模板）为合法业务态，同样缓存；模板变更最长 60s 生效——订单侧 `post_fee` 本就快照，陈旧窗口无资金影响，Phase 1 不引入跨服务缓存失效契约。

## 备选方案

| 方案 | 优点 | 缺点 |
| :--- | :--- | :--- |
| 悬挂单直接自动重试（重新发起渠道退款） | 全自动闭环 | 未经渠道状态确认即二次出款申请，绕过 AD43 人工口径 |
| 悬挂单只查询告警不收敛状态 | 实现最简 | 悬挂单仍需人工逐笔处理，兜底价值有限 |
| FAILED 后自动重试 | 全自动 | 出款需人工审批（采纳：FAILED 交 AD43） |
| review 改条件更新消除读-改-写 | 消 TOCTOU | 目标状态多元、条件面复杂化；本次只收紧守卫，售后状态机整体收紧另立任务 |
| 缓存事件失效（item-service 发模板变更事件） | 强一致 | 新增 MQ 契约 + 消费端，Phase 1 不值；60s TTL 已满足「分钟级生效」 |
| 缓存挂 item-service 侧（Redis） | 全局一致 | 跨服务共享缓存键违反硬约束；trade 本地缓存即够 |
| **查询三态收敛 + 幂等重放 + 守卫收紧 + 本地 TTL 缓存（采纳）** | 悬挂有界收敛且不自动出款；状态机语义闭环；下单运费查询按商家 N:1 | 真实微信退款查询链路需联调回归；60s 缓存陈旧窗口 |

## 后果

**正面**

- 退款单不再存在「无路径可收敛」的状态：PROCESSING 由补偿任务兜底、FAILED 由 AD43 收敛，与 PAY6 回调互为冗余；
- 售后状态机语义闭环：RETURNED 只能走向资金终态，管理端无法扰动退货退款在途单；
- 高频下单的运费模板查询按商家收敛（N 单 1 查询/分钟），item-service 读热点消除；
- 渠道退款状态归一化收口 ia-integration，pay-service 零微信状态串（与 ADR-033 同纪律）。

**负面 / 风险**

- **真实微信退款查询链路未联调**（依赖商户证书环境，与 ADR-036 账单解析同一遗留口径）：状态映射已由 `WechatRefundQueryMappingTest` 锁定，接入真实商户后须回归；
- FAILED 收敛依赖管理端 AD43 人工重试（有意不自动出款）；对账差异不自动调账（ADR-036 Phase 1）同理；
- I43 缓存 60s 陈旧窗口：模板刚改价/停用后下单仍按旧价计运费（快照语义内可接受）；空集合缓存 60s，商家新建模板最长延迟 60s 生效；
- `RefundManager` 已达 300 行类上限（共享结算入口 package-private 化），退款用例族再扩需按 ADR-025 拆分。

## 日期

2026-10-04

## 参考

- `Implementation/back-end/pay-service/src/main/java/org/icedamericanomall/pay/app/RefundResumeManager.java`（悬挂补偿编排）
- `Implementation/back-end/pay-service/src/main/java/org/icedamericanomall/pay/infrastructure/scheduler/RefundResumeJob.java`（XXL-Job `resumeStuckRefunds`）
- `Implementation/back-end/ia-integration/src/main/java/org/icedamericanomall/integration/payment/`（`ChannelRefundState` / `ChannelRefundQueryResult` / `PaymentClient.queryRefund`）
- `Implementation/back-end/trade-service/src/main/java/org/icedamericanomall/trade/app/aftersale/AfterSaleManager.java`（review 守卫）
- `Implementation/back-end/trade-service/src/main/java/org/icedamericanomall/trade/app/order/strategy/FreightPostageResolver.java`（Caffeine 缓存）
- ADR-035（退款资金流——风险 3 处置）、ADR-036（对账——真实账单联调同口径遗留）、ADR-037（风险 4/6 处置）、ADR-011（fail-closed 定类）、ADR-025（用例族划分）、ADR-038（四层 DDD）
