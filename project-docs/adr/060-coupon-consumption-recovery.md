# ADR-060: 券核销「三网」—— 事实标记驱动补偿 + 定时兜底对账 + 键空间纪律

## 状态

已实施（trade-service，2026-10-16，提交 `bcf93e9a`；口径适用于全部「远程副作用先于本地落库」的下单类链路）

## 背景

### 原缺陷（资损：用户券被静默吞掉）

券核销是**下单管道内部的远程副作用**：`PricingService.applyCoupons` 调 I22 `POST /internal/coupon/use`，marketing 侧 `CouponUsageManager#useCoupon` 自带 `@Transactional` —— **Feign 正常返回即表示对端事务已提交，券此刻已被占用**。而 `buildOrder` 内部其后仍有 fail-closed 步骤（I43 运费模板解析），两策略（`NormalCartOrderStrategy` / `DirectOrderStrategy`）同构。

原实现的补偿链路有两道独立的失效点，任一道都足以让券无人回收：

1. **代码路径失效**：`OrderCreationManager.createOrder` 的 `strategy.buildOrder(ctx)` 位于 `try` **之外** —— 券在 buildOrder 内已核销，随后 `buildOrder` 抛错（I43/I44 降级码）时异常直接穿出方法，**`compensateOnFailure` 根本不被调用**。
2. **补偿门失效**：即便把 `buildOrder` 包进 `try`，当时的补偿门是 `discountAmount > 0` 推导，而 `ctx.discountAmount` 在 `freightPostageResolver.resolve(...)` **之后**才写入 —— 运费抛错的时点它**仍是 0**，门恒为假 → 跳过 `rollbackByOrderNo`。

**综合后果**：订单没落库、库存没扣减、券已核销且无人回收 —— 用户资产静默损失，日志里连一条 WARN 都没有。

### 被覆盖的失败形态（决定了本 ADR 为何要「三网」而不是「两网」）

| 失败形态 | 谁负责回收 |
| --- | --- |
| 异常可捕获（业务异常、Feign 超时、DB 写失败） | 第 1 网：Saga 补偿（`compensateOnFailure`） |
| 异常不可捕获（容器驱逐 / OOM / JVM 崩溃 / kill -9） | 第 2 网：定时兜底对账（`CouponReconcileManager`） |
| 订单落库后一直未支付（非失败，属正常业务） | 第 3 网：既有超时取消链路（`AbnormalOrderJob` / RocketMQ 延时，本 ADR 不新增） |

前两网之间存在一个**语义空洞**：Saga 补偿依赖「有执行者活着」，而进程死亡恰好把执行者一起带走。`CouponConsumptionLedger` 就是为填这个洞存在的——它是**跨进程的崩溃可恢复凭证**。

## 决策

### 1. `buildOrder` 与 `deductStock` 一并纳入补偿作用域

`createOrder` 的步骤 4 改为一个 `try` 块，捕获 `RuntimeException`：

```java
try {
    order = strategy.buildOrder(ctx);   // ★ 券核销发生在这一步内部
    stockOps = buildStockOps(ctx);
    deductStock(stockOps);
} catch (RuntimeException e) {
    compensateOnFailure(false, List.of(), null, ctx, e);   // 库存必然未扣成功
    throw e;                                              // 原始异常原样上抛，保持既有错误码语义
}
```

- `stockDeducted = false` 是**事实**而非保守估计：`buildOrder` 抛错时 `deductStock` 尚未执行。
- 原始异常**原样上抛**（不包装）—— I43/I44 的降级错误码、`STOCK_INSUFFICIENT` 等语义不得被补偿逻辑吃掉。

### 2. 补偿由「核销事实的显式标记」驱动，而非推导金额

`OrderCreateContext` 新增事实标记与幂等锚点：

| 成员 | 语义 |
| --- | --- |
| `boolean couponConsumed` | 「本次下单**已发生**券核销」——由 `PricingService.applyCoupons` 在 `couponClient.useCoupon(...)` **正常返回的那一刻**调用 `markCouponConsumed(...)` 置位（`useCoupon` 抛异常则不置位：对端事务未提交） |
| `List<String> couponConsumedOrderNos` | 发生核销的订单号（去重、按发生顺序），供 `rollbackByOrderNo` 精确定位；多券下单复用同一订单号，故恒为单元素 |

`compensateOnFailure` → `reclaimConsumedCoupons(ctx, orderNo)`：

- `ctx.isCouponConsumed() == false` → 本次无券核销，直接返回「券侧已结清」；
- `== true` → 逐号 `couponClient.rollbackByOrderNo(orderNo)`；**判据与 `discountAmount` 彻底解耦**（金额可能为 0——降级回调口径，也可能尚未回填）。
- 订单号取值优先级：`ctx.couponConsumedOrderNos` → 订单对象 `orderNo` → `ctx.orderNo`；三者皆空则记 ERROR 并**保留标记**（不静默放弃）。

同时把「订单号确定」上提到**任何远程副作用之前**（步骤 0），使 `orderNo` 既是核销幂等锚点，也是补偿/对账在订单对象丢失时定位该单的唯一依据。

### 3. 定时兜底对账（第 2 网）

| 组件 | 层次 | 职责 |
| --- | --- | --- |
| `CouponRollbackReconcileJob` | `infrastructure/scheduler` | XXL-Job 触发（`@XxlJob("reconcileCouponConsumption")`，**建议调度频率 5 分钟**）；只做触发与日志，任务级异常只记录不抛出（下轮重试） |
| `CouponReconcileManager` | `app/order` | 对账逻辑本体（`reconcile()`）；依赖方向 infrastructure → app |
| `CouponConsumptionLedger` | `app/order` | 标记读写（`markPending` / `clear` / `listPendingBefore`） |

与既有 `AbnormalOrderJob`（超时订单第三层兜底）同构 —— trade 侧「兜底任务只触发、逻辑在应用层」的既定形态。

### 4. 键空间：`coupon:consumed:pending`（ZSET）

- **键名**：`coupon:consumed:pending` —— trade 侧**自管**键前缀，与其他服务无共享（跨服务共享 Redis 键是硬约束禁止项）。
- **结构**：ZSET，`member = 订单号`，`score = 尝试时间戳（epoch 毫秒）`。选 ZSET 而非普通字符串键的理由：既能按时间窗扫描（宽限窗口过滤），又**天然按订单号去重**。
- **写入方**：`OrderCreationManager.markCouponPending(ctx)` —— 在步骤 3（进入「可能核销券」的阶段**之前**）调用；判据 `ctx.hasCouponRequested()`（单券兼容字段 `userCouponId` 或多券列表任一非空），**无券请求不写标记**。
- **清除方**：①订单已落库（`clearCouponPending`，步骤 6）；②Saga 补偿确认券已结清；③对账处理完毕。清除失败仅记日志。
- **宽限窗口（代码常量，以代码为准）**：`CouponReconcileManager.GRACE = Duration.ofMinutes(10)` —— 只处理 score 早于 `now - 10min` 的标记。更年轻的标记对应的下单请求**可能仍在进行中**，对账不参与快路径、不与之竞态。
- **单轮上限（代码常量，以代码为准）**：`CouponReconcileManager.BATCH_SIZE = 200` —— 防一次调度打爆下游。

### 5. 判定方向：仅当 trade 侧**查不到**订单才 `rollbackByOrderNo`

标记的语义被刻意限定为「本次下单**可能**已核销券」——写入发生在核销**之前**，必然同时包含「核销未发生」（如核销前的 I44 促销失败）与「核销已发生」两种情形。因此对账侧**绝不**凭标记回滚：

| trade 侧查询结果 | 动作 |
| --- | --- |
| **查得到**该订单 → 券核销是合法占用 | **只清标记，绝不回滚**（防反向资损：用户既得单又得券） |
| **查不到**该订单 → 订单确实未创建 | 调 `couponClient.rollbackByOrderNo(orderNo)`（marketing 侧 `findUsedByOrderNo` + restore **幂等**，未命中即空操作）→ 清标记 |
| 查询或回滚任一步异常 | **保留标记**下轮重试 —— 宁可不处理，也绝不在状态不确定时清除标记（清除即等价于放弃回收） |

**方向性保证**：误报只产生一次无害的空回滚；绝不出现「订单已成交却被回滚券」的反向资损。可重入：标记清除与券回滚均幂等，任务重复调度 / 并发触发不会重复回收。

### 6. fail-open 边界（写标记本身 fail-open）

`markCouponPending` / `clearCouponPending` 均 `try/catch` 包裹并降级为 WARN —— **兜底标记写入失败不阻断下单**。理由：主流程内的 Saga 补偿仍然生效，标记只覆盖「进程异常退出」这一补偿到不了的情形；为一个旁路兜底把主交易链路变成 fail-closed 是不划算的交换。

## 备选方案

| 方案 | 优点 | 缺点 |
| :--- | :--- | :--- |
| 维持原状，只把 `buildOrder` 包进 `try`（保留 `discountAmount` 门） | 改动最小 | **无效**——金额门在运费抛错时恒为 0，缺陷仍在（原缺陷的第二道失效点） |
| 补偿门改为「下单请求带券即回滚」 | 无需新标记、无新基数 | **过度补偿**：核销前的 I44 促销失败、`useCoupon` 已抛异常（券未提交）等情形会调一次空回滚；虽幂等无害，但把「未发生」与「已发生」混为一谈，误导排障 |
| 用本地消息表 / Outbox 替代 Redis 标记 | 与订单同库、不依赖 Redis 可用性 | 需要新表 + 同事务写入，而核销是**跨服务**副作用、订单尚未存在，无「本地事务」可挂；成本远超收益 |
| 核销与建单做成分布式事务（Seata） | 理论上原子 | 本项目未引入 Seata（见 02-Architecture 路线图），且违背「Feign 不进事务」硬约束 |
| 定时全量比对 marketing `user_coupon` 与 trade `orders` 表 | 不依赖标记，无写失败问题 | 跨服务全库扫描 + 无法区分「已核销待支付」与「已核销无订单」，需产品级口径定义；量级不可接受 |
| **事实标记 + Saga 补偿扩域 + 定时兜底对账（采纳）** | 覆盖三种失败形态；判据是事实不是推导；对账方向安全（宁漏不错） | 引入一个 Redis 键空间 + 一个定时任务；标记写入本身 fail-open（见下） |

## 后果

**正面**

- 原缺陷的两道失效点同时关闭：`buildOrder` 在补偿作用域内 + 补偿判据改为事实标记；
- 首次覆盖「进程崩溃」这一补偿到不了的失败形态（此前完全无治理）；
- 判定方向经单测锁定（订单存在 → 绝不回滚），反向资损不可能发生；
- 键空间自管、无跨服务共享，符合硬约束；
- 对账回收时打 `ALERT` 级 WARN 日志（`reclaimed > 0` → 建议人工核查），资损事件首次**可观测**。

**负面 / 风险**

- 新增一个 Redis 键空间与一个 XXL-Job 任务（运维面 +1；任务未配置时第 2 网失效，需纳入部署清单）。
- 对账是**分钟级**兜底，不是实时回收：最坏情形下券在 `GRACE(10min)` + 调度间隔（建议 5min）内处于「已扣未确认」状态。

**已知残余风险（须显式登记，不得视为已解决）**

| # | 残余风险 | 触发条件 | 后果 | 收敛方向 |
| --- | --- | --- | --- | --- |
| R1 | **标记写入本身 fail-open** | Redis 在 `markCouponPending` 时不可用（抛异常被吞）**且**进程随后异常退出 | 该笔核销**既无 Saga 补偿、也无对账标记** → 券仍可能流失 —— 与修复前同形态，只是窗口收窄 | 唯一可彻底收敛的方向是让「核销凭证」与券核销同事务（对端或本地消息表），属独立工程；当前接受该残余并以告警日志兜底 |
| R2 | **Feign 降级是空操作，对账侧会误判为成功** | `CouponClientFallback#rollbackByOrderNo` 只打 ERROR 日志、**不抛异常**（ADR-011 三分法给命令类的语义应是 fail-closed，此处实现与语义不符）；则对账侧看到「正常返回」→ `clear(orderNo)` → **券未回收但标记已丢** | 券流失，且此后无重试机会 | 双向可选：①`rollbackByOrderNo` 的降级改为抛异常（对齐 ADR-011 命令类语义）；②`CouponReconcileManager` 在回滚后显式校验（如复用 `findUsedByOrderNo` 语义的只读端点）。**建议由 java-architect 裁决取 ①**；在收敛前该路径的可靠性不得声称已验证 |
| R3 | 对账清除标记与在途请求写标记的竞态 | 同一订单号极端交错：对账在「查不到订单」的瞬间清标记，而该请求随后才写标记 | 标记丢失、券靠券侧幂等兜住 | 宽限窗口已大幅收窄窗口；彻底消除需版本化标记（Phase 2 评估） |

> **R2 的取证边界**：本条依据的是 `CouponClientFallback#rollbackByOrderNo` 的源码语义（降级返回 `void`、不抛）与 `CouponReconcileManager` 的 `catch (Exception e)` 结构（降级不抛即不进 catch）。**未做**实机熔断/降级路径的联调验证（ADR-011 亦登记了同一缺口）——按「不声称未验证的结论」，此处只声明**代码路径风险**成立，实际发生率待真实故障演练确认。

## 守卫（防复发）

| 守卫 | 断言/测试 |
| --- | --- |
| 订单存在 → 绝不回滚 | `CouponReconcileManagerTest#shouldRollbackCoupon_whenOrderMissing` 的对偶：订单已成交只清标记 |
| 状态不确定 → 保留标记 | `CouponReconcileManagerTest` 覆盖「订单查询失败（DB 不可用）」与「券回滚失败」两条 —— 均不得清除标记 |
| 宽限窗口生效 | `CouponReconcileManagerTest` 断言只扫描早于 `now-10min` 的标记（cutoff 参数原样下传） |
| 单条异常不中断整轮 | `CouponReconcileManagerTest` 多条标记部分失败场景：失败项保留、其余照常回收 |
| 标记读写键空间与成员 | `CouponConsumptionLedgerTest`：ZSET 以时间戳为 score、member=订单号；空订单号不写；Redis 返回 null 归为空列表 |
| 补偿判据与金额解耦 | `OrderCreationManagerTest` / `PricingServiceTest` 补例：核销返回后即置位 `couponConsumed`（金额为 0 亦置位） |
| **变异验证** | F-5 提交时执行三次变异（①cutoff 退化为不过滤宽限窗口；②单条异常中断整轮；③假装回收但不真调券侧）—— 三次均能被上表测试捕获（挂测） |
| 知识守护（文字） | 「标记写入发生在核销之前，故标记只表示**可能**已核销」——对账侧凭此行禁止把标记当事实；该注释是判定方向的唯一依据，改动须走评审 |

## 日期

2026-10-16

## 参考

- 代码：`Implementation/back-end/trade-service/src/main/java/org/icedamericanomall/trade/`
  - `app/order/CouponConsumptionLedger.java`（标记键空间与读写）
  - `app/order/CouponReconcileManager.java`（`GRACE` / `BATCH_SIZE` / 判定方向）
  - `app/order/OrderCreationManager.java`（补偿作用域、`compensateOnFailure`、`mark/clearCouponPending`）
  - `app/order/strategy/OrderCreateContext.java`（`couponConsumed` / `markCouponConsumed` / `hasCouponRequested`）
  - `app/order/pricing/PricingService.java`（核销返回即置位事实标记）
  - `app/order/strategy/{NormalCartOrderStrategy,DirectOrderStrategy}.java`（两策略同构）
  - `infrastructure/scheduler/CouponRollbackReconcileJob.java`（XXL-Job 触发）
- 测试：`trade-service/src/test/java/org/icedamericanomall/trade/app/order/{CouponConsumptionLedgerTest,CouponReconcileManagerTest,OrderCreationManagerTest}.java`、`app/order/pricing/PricingServiceTest.java`
- ADR-011（降级三分法：命令类 fail-closed —— **R2 即本条的实现偏差**）、ADR-043（价格计算中心，券核销收编进 `PricingService`）、ADR-025（Manager 按用例族）、ADR-038（四层依赖方向 infrastructure → app）、ADR-041（同类「悬挂补偿」先例：退款悬挂 resume）
- 契约：I22 `POST /internal/coupon/use`、I23 `POST /internal/coupon/rollback`（`05-API-Specification.md` §4.14）
- 提交：`bcf93e9a`（fix(trade): F-5 券已核销但订单创建失败时不进入 Saga 补偿）
