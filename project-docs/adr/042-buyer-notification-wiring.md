# ADR-042: 买家消息触达接线 —— 四事件站内信、事务外 fail-open、单出口 notification-service

## 背景

notification-service 的通道能力早已齐备（SMS/email 渠道 + 站内信落库 U28-U32 + 内部发送口 I30-I32），但业务侧几乎未接线：仅 item `ReviewManager`（评价通知）与 merchant `StoreFollowManager`（关注通知）两处调用。交易主链路的四个关键状态变化——支付成功、包裹发货、退款成功、售后审核结果——买家均无主动触达，只能自行刷新查询，与大厂电商「状态变化主动触达」的标配体验不符。

历史上 trade 曾有 `OrderShippedConsumer` 占位（死代码，已删）；RocketMQ 基建在（trade `DomainEventProducer` + `RocketMqTopics`），但事件化触达需要消费端幂等去重与顺序设计，超出「接线」定位。

## 决策（2026-10-04）

### 1. 触发矩阵（Phase 1 四事件，全部站内信，收件人 = 买家 userId）

| 业务事件 | 发起服务与落点 | 触达内容 | `type` 常量 |
| --- | --- | --- | --- |
| 支付成功 | pay `PayManager`（`markSuccessIfPending` 条件置成功命中后） | 订单号 + 实付金额 | `PAY_SUCCESS` |
| 包裹发货 | trade `OrderLifecycleManager`（`shipPackage` + 物流记录创建后） | 订单号 + 包裹号 | `ORDER_SHIPPED` |
| 退款成功 | pay `RefundManager`（退款单条件置成功后） | 订单号 + 退款金额 + 原路渠道 | `REFUND_SUCCESS` |
| 售后审核结果 | trade `AfterSaleManager.review`（落库后） | 同意/拒绝 + 审核备注 | `AFTER_SALE_REVIEWED` |

### 2. 通道口径：Phase 1 只走站内信

I32 落库持久化（U28-U32 买家侧已可查询/已读）；SMS/email 需渠道模板审批与成本核算，Phase 2 按事件分级接入（发货/退款优先）。

### 3. 失败语义：fail-open + log.warn

触达是锦上添花，**绝不影响交易主流程**——发送失败仅告警、不重试、不回滚（复用 ReviewManager / StoreFollowManager 先例）；站内信未读列表（U28）本身就是买家主动查询的兜底。收件人缺失/非法时跳过不发送。

### 4. 事务纪律：本地事务外发送

Feign 严禁进事务（硬约束）——发送一律在本地落库**成功之后**、事务边界之外执行。

### 5. 代码形态：每服务一个触达协作器

pay / trade 各新增一个 app 层触达协作器（封装 `NotificationClient` 调用、type 常量与 fail-open 样板），Manager 只做一行委托——避免 Manager 超行数上限（`RefundManager` 已达 300 行上限，ADR-041 已登记），样板不重复。

### 6. MQ 事件化留 roadmap

`DomainEventProducer` 扩事件 + notification 消费端 + 幂等去重的完整事件化，待事件总线统一设计时一并落地；Phase 1 直接 Feign（dev `rocketmq.enabled=false` 也不受影响）。

## 备选方案

| 方案 | 优点 | 缺点 |
| :--- | :--- | :--- |
| MQ 事件化触达（producer → consumer → notification） | 彻底解耦、削峰 | 需消费端幂等/去重/顺序设计，超出接线定位；dev 关 MQ 时断链 |
| 事务内同步发送 | 时序强一致 | 违反「事务内禁 Feign」硬约束 |
| fail-closed（发送失败回滚主流程） | 触达强保证 | 通知失败拖垮支付/发货主流程，本末倒置 |
| 各 Manager 内联 NotificationClient 调用 | 少一个类 | `RefundManager` 已达 300 行上限；type 常量与 fail-open 样板在多 Manager 重复 |
| **协作器 + 事务外 fail-open（采纳）** | 主流程零风险、样板收敛、边界清晰 | 通知可能丢失（买家可经 U28 主动查询兜底） |

## 后果

**正面**

- 交易主链路四个关键状态变化主动触达买家，体验对齐大厂标配；
- 触达样板收敛到每服务单类（type 常量 + fail-open + 收件人守卫一处实现）；
- 主流程零影响（fail-open + 事务外），notification-service 单出口不被绕过。

**负面 / 风险**

- 站内信可能丢失（fail-open 无重试）——买家可经 U28 查询兜底，Phase 1 接受；
- SMS/email 未接入（Phase 2，需模板审批）；
- 四事件之外的状态变化（确认收货/取消/超时关单）未接线，留待按需追加；
- `type` 为字符串常量、按服务自治，notification 侧不校验语义（跨服务枚举约束待事件化时统一）。

## 日期

2026-10-04

## 参考

- `Implementation/back-end/pay-service/src/main/java/org/icedamericanomall/pay/app/`（触达协作器 + `PayManager` / `RefundManager` 接线点）
- `Implementation/back-end/trade-service/src/main/java/org/icedamericanomall/trade/app/`（触达协作器 + `OrderLifecycleManager` / `AfterSaleManager` 接线点）
- `item-service ReviewManager` / `merchant-service StoreFollowManager`（fail-open 触达先例）
- ADR-032（审计双管道——本 ADR 是业务触达，非审计，不写 `operation_log`）、ADR-011（降级语义——触达按 fail-open 定类）、ADR-025（用例族）
