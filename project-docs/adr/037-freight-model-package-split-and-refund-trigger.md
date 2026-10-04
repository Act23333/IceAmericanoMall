# ADR-037: 金额不变式 V2（`payAmount` 计入 `postFee`）+ 拆单发货包语义 + 退款触发时机纠正

## 背景

履约线两个 P0 缺口（F2 运费模型 / F3 拆单发货）落地，同时暴露 ADR-035 的一个触发时机缺口：

1. **运费缺失**：`orders` 无运费列，`payAmount = totalAmount − discountAmount` 里没有运费项；
   商品无运费模板概念，平台无法向买家收取运费（规格：04-Data-Model §十二 12.2、
   05-API-Specification §4.4.9 / §4.14.17）。
2. **发货粒度单一**：`order_logistics` 以 `order_id` **复用为主键**（1:1），一张订单只能发一个包裹；
   商家无法在部分明细到货时先行发货（规格：04 §十二 12.3、05 §4.6.3 T12 / §4.8.1 L1）。
3. **退款触发时机缺口（ADR-035 遗留）**：ADR-035 把 I42 定义为
   `APPROVED(2) → REFUNDED(5)`，且审核同意即触发退款 —— 对**退货退款（type=1）**语义错误：
   货还没退回来就放款；且 `RETURNED(4) → REFUNDED(5)` 这条边缺失（ADR-035「后果」已自我登记该风险）。

## 决策

### 1. 金额不变式 V2：`payAmount = totalAmount − discountAmount + postFee`

- `orders` 新增 `post_fee INT NOT NULL DEFAULT 0`，下单时计算并**快照**（模板后续改价/停用不影响已建订单）；
- `totalAmount` 仍严格等于 `Σ order_item.sub_total`（**运费不进商品小计**）——
  这是「订单原价」与「应付」两个语义继续分离的前提；
- 运费计价为**纯函数** `PostageCalculator`（trade `domain/service`，零框架依赖）：由调用方把
  「每个订单项 → 生效模板」解析为 `Rule(fee, freeThreshold)` 列表后传入，函数内只做
  「取最高 fee 计一次 + 满额免邮 + 兜底 0」。

**Phase 1 计价口径**（与规格逐条对齐）：

| # | 规则 | 实现落点 |
| --- | --- | --- |
| 1 | 同单同商家，运费只计一次 | 跨店下单已禁（`CROSS_STORE_FORBIDDEN(3014)`），一单仅传 `sellerId` 一次 |
| 2 | 取各生效模板中 `fee` **最高**者计一次（不叠加） | `PostageCalculator` 取 max |
| 3 | 总额 ≥ 被选中模板 `free_threshold`（>0）→ 免邮 | `PostageCalculator` 用**被选中规则**的阈值判定 |
| 4 | 未绑定 / 绑定模板已停用或已删 → 回落商家默认模板 | 由 I43 返回集合（仅启用）解析：命中则用绑定，否则用 `isDefault` |
| 5 | 商家无任何启用模板 → `postFee = 0`（不阻断下单） | 解析器空集合短路 |
| 6 | 秒杀订单（orderType=3）`postFee = 0` | `FlashSaleOrderStrategy` 恒置 0，不调解析器 |

### 2. 运费模板归属商品域（item-service），计价经 I43 一次性取回

- `freight_template` 表 + 商家端 CRUD 走 `/api/item/freight-template/**` —— **刻意不用
  `/api/seller/**`**：该前缀在网关路由到 merchant-service，而模板属商品域；`/api/item/**`
  已有路由，零网关改动。
- 商品绑定 `product.freight_template_id`（FK `ON DELETE SET NULL`）——模板删除即自动回落默认模板，
  无需应用层兜底写。
- I43 `POST /internal/item/freight-template/query` 返回**启用模板集合**，并**兜底补入**商家默认模板
  （`isDefault=true`），使调用方**一次往返**即可完成「绑定 → 回落默认」解析（避免二次查询与竞态窗口）。
- **降级语义（ADR-011 定类）**：本查询为 **② 判别型查询 → fail-closed（抛异常）**。理由：
  返回值会被解释为业务事实「商家有无启用模板」，空集合即免邮；若 item-service 抖动时静默降级为空，
  等于「故障伪装成免邮」，平台替买家承担运费且无告警 —— 属错误信息污染 + 资损。
  *（规格 §4.14.17 正文草拟的「降级为空 → postFee=0」与此相反，实现按 ADR-011 收敛；契约待同步。）*

### 3. 拆单发货：`order_logistics` 1:1 → 1:N，`order_item.package_no` 标记归属

- `order_logistics` 收敛「主键复用 order_id」的历史偏差：新增代理主键 `id`，
  `order_id` 降为普通外键，新增 `package_no` + 唯一键 `uk_order_package(order_id, package_no)`
  （存量数据回填 `package_no = 1`）。
  **不建 `uk_order_id`**：规格 §12.3 表格里并列写到 `uk_order_id(order_id)`，与同段
  「`order_id` 由 1:1 主键变为 1:N 外键」自相矛盾 —— 单列唯一会直接禁止一单多包裹，故按语义取舍。
- `order_item.package_no`（NULL = 未发货）标记明细归属；**一明细只发一次**。
- **发货是本地事务**：`OrderService.shipPackage` 在事务内完成「校验 → 解析包裹明细 → 条件更新打
  `package_no` → 判定是否发完 → 订单状态迁移」，返回 `ShipPackageResult`；物流记录创建（Feign）
  与领域事件（MQ）由 `OrderLifecycleManager` 在**事务外**完成（Feign 严禁进事务）。
- **状态机变化**（不新增状态，复用 §九 状态机）：

```
改前:  待发货(2) --发货(一次全部明细)--> 待收货(3)          （consignTime 一并写入）
改后:  待发货(2) --发货[部分明细]--> 待发货(2)  （consignTime 首包写入；不动 status）
               --发货[最后一批明细]--> 待收货(3)
      空 skuIds = 全部剩余明细（单包裹语义，与改前等价）
```

- **错误语义**：`skuIds` 含非本订单 sku → `PARAM_ERROR(1101)`；含已发货明细 / 订单状态不支持
  → `ORDER_STATUS_INVALID(3010)`；并发下条件更新未全命中 → `3010`（刷新重试）。
- **L1 破坏性变更**：`GET /api/logistics/{orderId}` 由单对象改为 `List<LogisticsVO>`
  （按 `packageNo` 升序），VO 增 `packageNo`；`OrderVO.items[].packageNo` 同步暴露。

### 4. 退款触发时机纠正（修订 ADR-035 的触发点，不改其资金链路）

| 售后类型 | 退款触发点（改后） | 说明 |
| --- | --- | --- |
| **2 仅退款** | 审核同意（`APPROVED(2)`）后 | 与 ADR-035 一致：不需退货，立即退款 |
| **1 退货退款** | **确认收到退货**（`APPROVED(2) → RETURNED(4)`）后 | 新增 `PUT /api/admin/after-sale/{id}/confirm-return`；货未退回不放款 |

- `markRefunded`（I42 回写）来源由单一 `APPROVED` 扩展为 `APPROVED | RETURNED`（两条边共用资金终态
  `REFUNDED(5)`），用 `UPDATE ... WHERE id=? AND status IN (...)` 条件更新，保持「0 行 = 幂等空操作」。
- **重复触发防护复用既有守卫**：`confirm-return` 对已 `REFUNDED` 抛 `REFUND_STATUS_INVALID(3025)`；
  已 `RETURNED` 幂等空操作；资金侧另有 `refund_order.uk_after_sale` 一售后单一次退款兜底。
- 触发仍在**事务外**（控制器 → 领域服务本地事务 → `RefundManager` Feign），沿用 ADR-035 §6 的纪律。

### 5. ErrorCode 与既有权重的取舍

商家端模板重名/不存在用**已预置**的 `FREIGHT_TEMPLATE_NAME_EXISTS(3040)` /
`FREIGHT_TEMPLATE_NOT_FOUND(3041)`，商品绑定他人模板统一按 3041 拒绝（不区分「不存在」与「归属他人」，
避免探测他人模板是否存在）。

- 规格 §4.4.9 草拟的重名 → `3006`、不存在 → `NO_RESOURCE_FOUND(1103)` 未采纳（3006 语义为
  「重复提交」的幂等提示，1103 语义为静态资源缺失），已登记为契约待同步项。
- 模板列表端点按契约 §4.4.9 的 `GET /api/item/freight-template/list` → `List<FreightTemplateVO>`
  （**非分页**）；实现期任务简报提到的「list 分页」未采纳，如需分页属契约变更（05 归 product-manager）。

## 备选方案

| 方案 | 优点 | 缺点 |
| :--- | :--- | :--- |
| 运费按商品逐条叠加计费 | 单件计费直觉 | 与规格「取最高者计一次」冲突；同店多商品运费畸高，转化率受损 |
| 运费模板放 merchant-service（店铺域） | 模板是商家资产 | 计价需商品→模板绑定（`product` 在 item-service），跨域绑定会引入双向依赖；且 `/api/seller/**` 网关归属冲突需改网关 |
| 每商品建一次运费模板查询（不做批量 I43） | 端点更简单 | N 件商品 N 次跨服务往返；且默认模板解析与绑定模板解析分两次调用会有竞态窗口 |
| I43 降级返回空集合（postFee=0） | 下单永不因运费失败 | 故障伪装成免邮（资损 + 无告警），违反 ADR-011 判别型查询定类 |
| `order_logistics` 保持 1:1，包裹信息落新表 | 不动既有主键偏差 | 两张「物流」表并存，语义分裂；查询需 join；存量偏差延后爆发 |
| 发货把 `package_no` 与订单状态迁移分两个事务 | 实现直白 | 存在「明细已标记但订单未迁移」的中间态窗口；合并为一个本地事务更安全 |
| 退货退款的退款仍在审核同意时触发 | 不改 ADR-035 | 货未退回即放款，商家资损；ADR-035 已把 `RETURNED→REFUNDED` 缺失登记为遗留风险 |
| 售后单新增 `REFUND_TRIGGERED` 标记列做触发幂等 | 显式 | 新增列 + 迁移；资金侧 `uk_after_sale` 已天然幂等，属重复防护 |
| **不变式 V2 + 纯函数计价 + 1:N 包裹 + 触发点按类型分派（采纳）** | 与规格逐条对齐；计价可单测锁定；包语义与状态机不新增状态；资金风险与触发点匹配 | 破坏性变更（L1 列表化）+ 一次迁移改主键 |

## 后果

**正面**

- 运费成为订单的一等金额项，`totalAmount`（原价）与 `payAmount`（应付）语义在含运费后仍清晰分离；
- `PostageCalculator` 是零依赖纯函数 —— 6 条计价规则由 12 个单测直接锁定（含阈值边界、并列 fee、
  负值、缺 fee 等），无需起 Spring；
- 商家可部分发货：一单多包裹、明细归属可追溯（`order_item.package_no` ↔ `order_logistics.package_no`），
  且**不新增订单状态**（复用待发货/待收货既有状态机）；
- 退款触发点与货物流转一致：仅退款即时放款、退货退款货到放款，堵住 ADR-035 的遗留资损口子；
- 包裹记录创建 `(order_id, package_no)` 幂等，重复发货事件不产生第二条物流记录。

**负面 / 风险**

- **L1 破坏性变更**：`GET /api/logistics/{orderId}` 由单对象变列表，前端须按数组消费（规格已声明
  「随 V5.6 联调一次性切换」，前端需同一批次改造）；
- **`orders.post_fee` 与 `order_item.package_no` 为新增列**：存量库需跑 `V5.6` 迁移；未跑迁移的库写入会报未知列；
- **运费模板无「按件/按重」计费**：`charge_mode` 目前仅 1（固定运费），Phase 2 扩展需再走一版契约与迁移；
- **I43 无缓存**：每次下单一次跨服务查询（商品件数不放大往返，但高频下单会成为 item-service 的读热点），
  可后续在 trade 侧加短 TTL 本地缓存（Caffeine，仅适合不变数据，模板可变需带失效）；
  **→ 已收敛（2026-10-04，[ADR-041](./041-refund-resume-aftersale-guard-freight-cache.md)）**：trade 侧 Caffeine 60s TTL 本地缓存（key=sellerId），fail-closed 保持。
- **`confirm-return` 是新增管理端端点**：若管理端页面未同步，退货退款单会停在 `APPROVED`（退款不触发）——
  状态可见、可重试，不会误放款（fail-safe 方向正确）；
- **`review` 对 `RETURNED` 无守卫**：管理员仍可把 `RETURNED` 售后单改回其它状态（ADR-035 仅守 `REFUNDED`）。
  资金不会重复（`uk_after_sale` 兜底），但状态语义可被管理端扰动 —— 留待后续统一收紧售后状态机；
  **→ 已收敛（2026-10-04，[ADR-041](./041-refund-resume-aftersale-guard-freight-cache.md)）**：review 禁改来源扩至 RETURNED（`REFUND_STATUS_INVALID`），RETURNED 唯一合法去向是 I42 回写资金终态。
- **契约偏差待同步**：§4.14.17 降级口径、§4.4.9 错误码（3006/1103 → 3040/3041）与列表分页形态三处，
  实现已按 ADR/规格语义取舍，需由 doc-engineer / product-manager 回写 05 契约。
  **→ 已同步（05 规格 V5.6.1 落地说明）**：降级口径 fail-closed、错误码 3040/3041、FT3 非分页均已进正文。

## 日期

2026-10-03

## 参考

- `Implementation/back-end/database/migrations/V5.6__freight_template_and_package_split.sql`（建表 + 主键/包裹改造 + 存量回填）
- `Implementation/back-end/item-service/src/main/java/org/icedamericanomall/item/`（`freight_template` 实体/服务/Manager、`InternalFreightTemplateController`、商品绑定）
- `Implementation/back-end/trade-service/src/main/java/org/icedamericanomall/trade/domain/service/PostageCalculator.java`（纯函数计价）
- `Implementation/back-end/trade-service/src/main/java/org/icedamericanomall/trade/strategy/FreightPostageResolver.java`（生效模板解析 + I43 调用）
- `Implementation/back-end/trade-service/src/main/java/org/icedamericanomall/trade/service/impl/OrderServiceImpl.java`（`shipPackage` 拆单发货本地事务）
- `Implementation/back-end/trade-service/src/main/java/org/icedamericanomall/trade/adapter/admin/AdminAfterSaleController.java`（按类型分派退款触发 + `confirm-return`）
- `Implementation/back-end/logistics-service/src/main/java/org/icedamericanomall/logistics/`（一单多包裹服务 + L1 列表化）
- ADR-035（买家退款资金流 —— 本 ADR 修订其触发时机；其资金链路决策不变）
- ADR-011（Feign 降级语义三分法 —— I43 fail-closed 定类依据）
- ADR-020（SKU 原子 SQL）、ADR-025（Manager 用例族划分）、ADR-004（内部端点异常透出）
