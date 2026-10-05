# ADR-044: 会员等级升降级与权益 —— 成长值只升不降、会员折扣接入定价中心、积分倍率接线

## 背景

`user.member_level` 字段已枚举化（`MemberLevelEnum`：NORMAL/SILVER/GOLD/PLATINUM）但全仓无升降级逻辑、无任何权益消费——等级是死字段。P1 规划「会员等级升降级 + 权益（会员价/运费券/积分倍率）」，且与促销引擎联动（会员价在 ADR-043 价格计算中心实现）。积分侧现状：trade `OrderLifecycleManager` 在订单完成后按实付发放奖励积分（`points = payAmount / POINTS_RATE_DIVISOR`，fail-open）——倍率的天然接线点。

## 决策（2026-10-05）

### 1. 成长值 = 累计实付金额，订单完成入账，只升不降（Phase 1）

- trade 确认收货（订单转 COMPLETED）后，经 I46 `POST /internal/user/member/growth` 把 `{userId, orderNo, payAmount}` 通知 user-service；**幂等锚点 = `member_growth_log.uk_order_no`**（一单一次成长，重复请求返回当前等级，不报错）。
- 等级重算：`新等级 = max(当前等级, 门槛匹配等级)`——**只升不降**（年度降级考核涉及周期快照与清零口径，留 roadmap）；成长值流水按单累计，天然可审计。
- 门槛规则落 `member_level_rule` 表（seed：NORMAL 0 / SILVER 1000 元 / GOLD 5000 元 / PLATINUM 20000 元），Phase 1 不开管理端编辑（规则是产品决策，改表 seed 即生效）。

### 2. 权益 Phase 1 两项：会员折扣率 + 积分倍率

- **会员折扣率**（`discount_rate` 万分比）：接入 ADR-043 价格计算中心——下单时 I45 取等级折扣率，按 `totalAmount × (1 − rate/10000)` 计算会员优惠，与店铺促销、用户券叠加（总额守卫见 ADR-043）。SKU 级会员价（每商品配会员价）留 Phase 2——需要商品域建模，Phase 1 等级整单折扣已构成完整闭环。
- **积分倍率**（`points_rate` 百分比）：`OrderLifecycleManager` 既有积分发放点乘以倍率（`points = payAmount / divisor × pointsRate / 100`，整数取整口径与既有 `POINTS_RATE_DIVISOR` 纪律一致）。
- 运费券/专属客服等权益留 roadmap。

### 3. I45/I46 降级定类：fail-open（与 I43/I44 fail-closed 刻意不同）

| 调用 | 方向 | 语义 | 理由 |
| --- | --- | --- | --- |
| I45 `GET /internal/user/member/level` | trade → user | **fail-open**（降级默认 NORMAL：不折扣/1 倍积分 + warn） | 等级是准静态数据；查询失败的方向是「用户少得优惠」（体验损失、可补偿），**不是平台资损**；下单可用性优先。与 I43（运费）/I44（促销）fail-closed 的差异在于后者静默失效会伪装成业务事实造成价格不一致/资损 |
| I46 `POST /internal/user/member/growth` | trade → user | **fail-open**（warn，Phase 1 不补账） | 成长值入账是交易旁路（与触达接线 ADR-042 同口径）；入账失败不阻断确认收货，等级成长可由后续订单继续累计 |

### 4. 代码形态

- user 域：`app/member/MemberLevelManager`（成长值入账 + 等级重算 + 权益查询）+ `domain/model/member/`（`MemberGrowthLog` 聚合）+ 仓储端口化；`InternalMemberController`（I45/I46）。
- trade 侧：确认收货路径一行委托（I46，fail-open）；定价中心经 `MemberClient.getLevel`（I45，fail-open + 短 TTL 缓存可后续按 ADR-041 先例补）。

## 备选方案

| 方案 | 优点 | 缺点 |
| :--- | :--- | :--- |
| 成长值独立于消费金额（签到/评价等行为分） | 激励行为面广 | 行为分口径多源、易刷；Phase 1 消费额口径客观可审计 |
| 支持降级（年度考核） | 等级含金量高 | 周期快照/清零/通知一套口径，Phase 1 不值；只升不降已构成完整闭环 |
| SKU 级会员价（商品配价） | 大厂淘宝同款 | 需商品域建模 + 管理端配价面；Phase 1 等级折扣率闭环快、可后演进 |
| I45 fail-closed | 价格强一致 | user 抖动即下单失败——会员折扣不是成交必要条件，可用性优先 |
| 事件化入账（MQ 替代 I46） | 解耦 | dev 关 MQ 断链；与触达接线同为旁路，直接 Feign fail-open 足够 |
| **消费额成长值 + 只升不降 + 两项权益 + I45/I46 fail-open（采纳）** | 口径客观可审计；权益有真实消费点；主流程零风险 | 等级只升不降（Phase 1 接受）；积分倍率取整有精度损耗 |

## 后果

**正面**

- `member_level` 从死字段变为有真实升降级引擎与两项可消费权益的活模型；
- 会员折扣接入统一定价中心（ADR-043），未来 SKU 级会员价/运费券有归宿；
- 成长值流水幂等可审计（uk_order_no），重算规则纯函数可单测；
- trade 主流程零风险（I45/I46 全 fail-open）。

**负面 / 风险**

- 等级只升不降（Phase 1 接受，年度考核 roadmap）；
- 积分倍率整数取整有精度损耗（与既有 `POINTS_RATE_DIVISOR` 同纪律）；
- 成长值入账失败不补账（Phase 1），长期少计成长值——补账任务留 roadmap；
- 会员折扣无 C 端展示承诺（fail-open 丢失仅少优惠），SKU 级会员价 Phase 2。

## 日期

2026-10-05

## 参考

- `Implementation/back-end/user-service/src/main/java/org/icedamericanomall/user/app/member/`（`MemberLevelManager`）+ `domain/model/member/`
- `Implementation/back-end/user-service/src/main/java/org/icedamericanomall/user/adapter/internal/`（I45/I46）
- `Implementation/back-end/trade-service/src/main/java/org/icedamericanomall/trade/app/order/OrderLifecycleManager.java`（I46/积分倍率接线点）
- `Implementation/back-end/ia-api/`（`MemberClient` fail-open / `MemberLevelCO` / `MemberGrowthCO`）
- ADR-043（价格计算中心——会员折扣的消费点）、ADR-011（降级三分法）、ADR-042（旁路 fail-open 先例）、ADR-038（四层）
