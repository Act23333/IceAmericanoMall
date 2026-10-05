# ADR-049: 拼团完整闭环 —— 两段式参团、幂等锚点泛化（修订 ADR-035）、结团退款联动

## 背景

P3 清单（⚪ 未建）中拼团（group buy）是大厂电商标配营销形态，也是清单里链路最重的一项：需要**成团状态机**（开团→凑员→成团/超时失败）、**超时自动退款**（资金闭环）、拼团价下单与支付回调的联动。既有地基：`OrderTypeEnum` 策略模式（V4.1，NORMAL/DIRECT/FLASH_SALE/PRESALE）可扩展；`refund_order` 退款链路成熟（ADR-035 三通道 + ADR-041 悬挂补偿）但幂等锚点 `uk_after_sale` 与售后单强绑定——拼团退款没有售后单，锚点必须泛化。规格先行：05 V5.14.0（M15-M17/T27/AD54-56/I52-I55）、04 §十二 12.5、PRD §十四（F13）。

## 决策（2026-10-05）

### 1. 两段式参团：下单不占团位，支付成功才入团（大厂标准）

- **T27 下单**（trade）：`POST /api/trade/order/group-buy`（`{activityId, teamNo?, addressId, remark?}`，quantity 恒 1）——先经 **I52 validate**（marketing：活动进行中 + 参团时团有效未满 + 用户未在该团）拿拼团价，创建 `orderType=GROUP_BUY(5)` 订单（拼团价快照进 subTotal，**不叠加**券/促销/会员：`discountAmount=0`，与秒杀同口径；运费走常规计价）。此阶段**不占团位**——未支付订单被既有 30 分钟超时取消，不产生团残留。
- **支付成功参团**（trade 的支付成功回调处理内，订单 1→2 落库后事务外）：经 **I53 join**（marketing）：`teamNo` 空 = 开团（创建团，leader=本人，`expire_time = now + timeLimitHours`）；非空 = 参团（`current_members` **条件更新 +1**，`WHERE status=1 AND current_members < required`，0 行 = 团满/已结 → 参团失败）。满员瞬间条件置 `status=2 已成团`。成员行以 `uk_order_no` + `uk_team_user` 双唯一键兜幂等。
- **参团失败 → 自动退款闭环**：join 抛异常（fail-closed，参团是拼团价生效的前提业务事实）时，trade 对该订单执行「条件取消（2→5）+ 恢复库存 + **I55 按订单退款**」——用户拿回全款，无资损窗口；I55 失败（资金命令 fail-closed）向上告警，退款单停留失败态可由既有补偿语义收敛。

### 2. 幂等锚点泛化（修订 ADR-035 §1）：`refund_order.biz_ref`

- `after_sale_id` 改为**可空**（保留 `uk_after_sale`，售后场景语义不变）；新增 `biz_ref VARCHAR(64) NOT NULL` + `uk_biz_ref` 作为**统一幂等业务键**：售后退款 `AS:{afterSaleId}`，拼团退款 `GB:{orderNo}`（一单一次退款）。`RefundManager` 幂等查询统一走 `biz_ref`，I40 调用方无感知（内部构造）。
- **I55** `POST /internal/pay/refund/order`（trade→pay）：`OrderRefundCO { orderNo, sceneRef, reason }` → `RefundResultCO`；无售后单的退款命令入口，复用既有三通道执行路径与条件状态机。fail-closed（ADR-035 §5 定类不变）。

### 3. 结团与退款联动：XXL-Job 扫描 + I54 逐单退款

- marketing `GroupBuyExpireJob`（XXL-Job，dev 默认关，与 `ReconciliationJob`/`RefundResumeJob` 同口径）：扫 `status=1 AND expire_time < now` → 条件置 `3 已失败` → 收集成员订单号 → **I54** `POST /internal/trade/group-buy/refund-team`（marketing→trade，Body `{teamNo, orderNos[]}`，fail-open + 告警）。
- trade I54 侧逐单执行「取消 + 恢复库存 + I55 退款」，单笔失败 try/catch 继续（不中断整团），失败单依赖 `refund_order` 失败态 + 后续补偿（挂账 roadmap）。
- **团成功不额外触达**：买家经 M17 团详情查询进度；退款成功走既有 `REFUND_SUCCESS` 站内信触达（ADR-042）。

### 4. Phase 1 边界

- 一人一单一件（`uk_order_no` 天然限购）；拼团价不叠加券/促销/会员（与秒杀同口径）；虚拟团主动解散不做（失败只有超时一种路径）；跨团参团不限制（只限同团重复）；MQ 事件化（团成功/失败事件）挂账，Phase 1 同步 Feign；退款失败补偿任务挂账。

## 备选方案

| 方案 | 优点 | 缺点 |
| :--- | :--- | :--- |
| 下单即占团位（未支付占坑） | 团进度实时 | 未支付订单占坑+超时释放一套新状态机；超时窗口与 30 分钟支付超时竞态 |
| 支付后 MQ 异步参团 | 解耦 | dev 关 MQ 断链；参团失败的退款闭环被异步化，复杂度陡增 |
| 拼团退款走系统影子售后单 | 复用 I40 零改动 | 售后域被资金场景污染（用户列表出现系统售后单）；状态机语义混用 |
| refund_order 按场景拆表 | 边界清晰 | 两条退款链路两套状态机/补偿，重复建设 |
| 超时结团用 RocketMQ 延迟消息 | 实时 | 延迟消息不可撤销，开团后取消场景难处理；XXL-Job 扫描与既有 Job 先例一致 |
| **两段式参团 + biz_ref 泛化 + XXL-Job 结团 + I54 闭环（采纳）** | 支付超时零残留；退款锚点统一；与既有 Job/退款先例同构 | 参团失败的自动退款路径新增（测试锁定）；结团扫描分钟级延迟 |

## 后果

**正面**

- 拼团从 ⚪ 清单进入可运营状态：活动管理（AD54-56）/ C 端浏览与参团（M15-17/T27）/ 成团状态机 / 超时自动退款全链路闭环；
- `refund_order` 幂等锚点泛化后，未来无售后单的退款场景（价格保护、补偿性退款等）有了统一入口（I55）；
- 参团竞态由条件更新与唯一键兜底，无需分布式锁。

**负面 / 风险**

- 参团失败的自动退款是新增资金路径（测试锁定 + fail-closed 告警）；
- 结团扫描分钟级延迟（Job 口径，dev 默认关）；
- 团失败的退款补偿（I55 失败单重试）Phase 1 靠人工/挂账；
- 一人一单一件与拼团价不叠加是产品简化（PRD 注明），放宽需契约变更。

## 日期

2026-10-05

## 参考

- `Implementation/back-end/marketing-service/`（`groupbuy` 域：活动/团/成员三聚合 + AD54-56 + M15-17 + I52/I53 + `GroupBuyExpireJob`）
- `Implementation/back-end/trade-service/`（`GroupBuyOrderStrategy` + T27 + 支付成功参团钩子 + I54 + 退款闭环）
- `Implementation/back-end/pay-service/`（I55 `refundByOrder` + `biz_ref` 泛化）
- `Implementation/back-end/ia-api/`（`GroupBuyClient` / `GroupBuyTeamClient` / `OrderRefundClient` + CO）
- ADR-035（退款资金流——§1 幂等锚点被本 ADR 泛化）、ADR-041（Job 先例）、ADR-042（退款成功触达复用）、ADR-043（拼团单不进定价中心的口径）、ADR-045（schema 直改 Initialize.sql）
