# ADR-063: 对外渠道号与内部业务键分离 —— 唯一寻址口径与承载层解耦

## 状态

**已裁决并已落码** —— 代码落码 2026-10-17（`256abbf3`「fix(pay)!: 对外渠道号与内部业务键分离（修复资金回调链路必然失败）」，提交报告全量 1931 测试绿 / 0 failures，**本批（B6）未复跑该全量**）；
文档同步 2026-10-18（批次 B6）。

本 ADR 是 [ADR-062](./062-external-identifier-and-addressing.md)（对外标识与寻址口径）的**第四条腿**：
062 管「内部业务键怎么存储、怎么编解码、怎么出现在 URL 里」，本条管
「**内部业务键绝不出现在渠道协议里** —— 对外渠道号是另一类标识，须独立成列、独立生成、独立寻址」。

> ⚠ **授权边界说明（登记用）**：按 [ADR-053](./053-doc-ownership-by-nature-and-two-layer-standards.md) 的分权，
> 决策类 ADR 归该域决策者（本条属后端/资金链路口径，正常归 java-architect）。
> 本条由 **doc-engineer 在批次 B6 的显式指令下代笔**（主 agent 派单已授权 `project-docs/**` 写入），
> **裁决内容由用户（项目方）拍定并已落码**，非作者自行判断。

## 背景

### 1. 缺陷本体：两条链路用两个键，交集为空（静态可证）

修复前的资金链路：

| 链路 | 键 | 取值来源 |
| :-- | :-- | :-- |
| **发起侧**（把 `out_trade_no` 发给渠道） | `orders.order_no` | trade 域订单号 |
| **回调侧**（收到 `out_trade_no` 后查库） | `pay_order.pay_order_no` | pay 域支付单号 |
| **对账侧**（按账单「商户订单号」比对） | `pay_order.pay_order_no` | 同上 |

`order_no` 与 `pay_order_no` 是**两个互不相关的 UUIDv7**（均由 `IdGenerator.nextId()` 生成，无衍生关系），
因此两条链路的键集合**交集恒为空**。后果不是「偶发查不到」，而是**必然**：

- 真实微信回调 → `PayManager#confirmPaid` 按渠道回传的 `out_trade_no`（其实是 `order_no`）查支付单 → **恒 `ORDER_NOT_FOUND`**；
- 回调恒 500 → 违反 ADR-029「500 触发渠道重推」→ 渠道**无限重推**、订单**永不确认**；
- 对账同理：账单里的「商户订单号」列是 `order_no`，拿它比对 `pay_order_no` → **全量误判「本地有渠道无」**；
- **退款侧另一个同构缺陷**：退款 API 的 `out_trade_no` 传的是内部 `pay_order_no`，而渠道侧那笔交易登记的是 `order_no`
  → 原路退回**必然找不到原交易**，退款全额失败。

> 这一条不是「某处写错了一个变量」，而是**两个键在有缺陷的形态下被系统性混用**：
> 只要「对外发什么号」与「内部按什么号查」分别定义，就必然允许它们分叉。

### 2. 为何测试没发现：按错误键喂数据，再按错误键放行（本会话第 4 次）

修复前 `PayManagerTest` 的写法（实测 diff，`git show 256abbf3 -- .../PayManagerTest.java`）：

```java
// ① 用「内部支付单号的字面量」伪装渠道回调回传的 out_trade_no
when(...).thenReturn(wechatResult("PAY-1", true, "SUCCESS"));   // out_trade_no = "PAY-1"
// ② 再用同一个「内部键」把查库桩住
when(payOrderService.getByPayOrderNo("PAY-1")).thenReturn(paid);
```

即：**测试自己把「对外发的号」填成了内部键，又按这个内部键桩好了查询** —— 于是
「发起侧发的是 `order_no`」这件事**从未被任何断言触达**（`PayManagerTest` 对 `initiatePayment`
那一次外发的字面量没有任何 `verify`/`argThat`）。用例全绿，链路全断。

**这是本会话第 4 次「mock 掩盖真缺陷」**，四个案例同一机理 —— **测试把缺陷两侧用同一份假数据圆上**：

| # | 缺陷 | 文档/证据 | 掩盖方式 |
| :- | :-- | :-- | :-- |
| 1 | `SkuCO.productName`（及 `seller_id`/`category_id`/`freight_template_id`）**恒 null** | [ADR-057](./057-display-field-read-model-ports.md) §「I13 根因与收口」：纯 `sku` 表查询 + MapStruct 投影，**无源可映射 → 四字段恒 null**；「上层单测因 mock `SkuCO` 而看不见」 | 单测 mock 掉整个 `SkuCO`，字段是 null 也照样断言通过 |
| 2 | `CartItemCO.cartItemId` **恒 null** | `cart-service` `CartConverter`（`@Mapping(target="cartItemId", source="id")` 处的 javadoc 原话）：**「缺失时恒 null，下单后『删除已购购物车项』会静默跳过，购物车永不清除」** | MapStruct 静默产出 null，用例不校验该字段 |
| 3 | `categoryId` **静默失效** | [ADR-056](./056-wire-naming-snake-case.md)：线上名 `category_id` 与代码字段 `categoryId` 两套方言，POJO 绑定不生效 → **筛选条件被静默忽略**（返回未过滤结果，不报错） | 用例只断言「有结果」，不断言「筛选生效」 |
| 4 | **本次：渠道号与内部键混用** | 本条 | 用例按错误键喂数据、再按错误键放行 |

**共同点（写在 ADR 里，因为它决定后续所有守卫的形状）**：
**这四个缺陷都不是「断言写错了」，而是「没有一个断言站在缺陷的接缝上」**。
接缝是「A 产生的东西交给 B」的那一行 —— 只要测试给 A 和 B 各喂一份自己编的数据，接缝就永远不被触碰。

### 3. 为什么这属于「标识口径」问题，而不是一次性实现 bug

因为缺陷的成因是**「一个概念，两类标识」没有被显式区分**：

- 「商户单号」在**渠道协议**里是一个**契约字段**（微信 `out_trade_no`，6–32 字符，商户侧生成、渠道侧幂等键）；
- 「支付单号」在**本平台内部**是一个**业务唯一键**（`pay_order_no`，32-hex UUIDv7，`UNIQUE`）；
- 二者**语义、生命周期、可见边界、长度约束全部不同**，却被写成同一个值。

这与 ADR-062 §背景 3「两套词汇并存 → 静默失效」是同一类病，只是这次并存的不是两个*词*，而是两个*号*。

## 决策

### 1. 对外渠道号独立成列（一列一语义，不与内部键共用）

| 载体 | 列 | 类型 / 约束 | 生成口径 |
| :-- | :-- | :-- | :-- |
| 支付 | `pay_order.channel_trade_no` | `VARCHAR(32)`，`UNIQUE KEY uk_channel_trade_no`，**余额渠道为 NULL** | `IdGenerator.randomHex(20)` —— **20 位随机 hex（80 bit 熵）**，`SecureRandom`，**无时间序**，**跨重试稳定** |
| 退款 | `refund_order.out_refund_no` | `VARCHAR(64)`，`UNIQUE KEY uk_out_refund_no` | `IdGenerator.randomHex(20)` —— 同上 |

口径要点（逐条对应实现，均可静态核验）：

1. **生成位置内聚在聚合工厂**：`PayOrder#newPending` 一次性给出「支付单号 + 渠道号 + 初始状态 + 超时窗口」
   （`PayOrder.java:132-145`）；`RefundOrder#newProcessing` / `#newOrderProcessing` 同构给出 `outRefundNo`
   （`RefundOrder.java:128-172`）。三条发起路径（微信 / 支付宝 / 余额）与两条退款创建路径**口径不可能分叉**。
2. **落库早于调渠道**：`PayManager#initiatePayment` 先 `createPending`/`resetPending`（渠道号在此定稿）再调渠道
   （`PayManager.java:99-102`）。理由写在方法 javadoc：顺序颠倒会在「渠道已受理、本地尚未落库」的窗口内**必然丢回调**。
3. **跨重试稳定**：复用待支付单时 `applyPendingReset` 刻意**不碰** `channelTradeNo`
   （`PayOrder.java:193-212`）。否则渠道侧同一笔交易会出现两个商户单号，退款与对账都对不上。
4. **余额渠道为 NULL**：余额本地闭环、不对外发起（`PayOrder#newChannelTradeNo`，`PayOrder.java:158-163`）。
   NULL 在 `UNIQUE` 下可重复（MySQL 语义），不影响多笔余额单共存。
5. **长度 20 而不是 32**：见 §决策 3。

### 2. 唯一寻址入口：发起 / 回调 / 对账一律按渠道号 —— **不保留「按内部键兜底查」的第二口径**

| 链路 | 入口 | 实现 |
| :-- | :-- | :-- |
| 发起（微信 Native / JSAPI） | `channelTradeNo` → 渠道 `out_trade_no` | `PayManager.java:101`、`:133` |
| 支付回调（微信 / 支付宝） | 渠道回传号 → `getByChannelTradeNo(...)` | `PayManager.java:222`、`:240`、`:254` |
| 退款发起 | 原交易渠道号 + 对外退款项号 | `RefundManager.java:245` → `refund(channel, payOrder.getChannelTradeNo(), refundOrder.getOutRefundNo(), ...)` |
| 退款回调 | 渠道回传号 → `getByOutRefundNo(...)` | `RefundManager.java:181` |
| 悬挂退款补偿查询 | 按 `out_refund_no` 查渠道 | `RefundResumeManager` → `PaymentClient#queryRefund(outRefundNo)` |
| 对账比对 | 渠道号 | `ReconciliationComparer.java:69-99` |

**为什么不加「按 `order_no` / `pay_order_no` 兜底查」的过渡分支**（哪怕它能「兼容历史回调」）：

> **第二口径会让双键混用复发。** 兜底分支把「按哪个键查」变成运行时二选一，
> 一旦保留，任何人都能凭一个历史包袱，把「发起侧发什么号」重新变回可协商的；
> 而缺陷的成因正是**两个键都能查到**。**单口径是这次修复的核心资产，不是可优化项。**

代价是**显式接受**的：若存在历史行为 NULL 渠道号的支付单，其回调**查不到** —— 见 §后果 R1。

### 3. 为何不用 UUIDv7 作对外号：两条硬理由 + 两个显式入口

`IdGenerator` 现在有 **三个**生成入口，语义边界必须背下来（`IdGenerator.java:9-60`）：

| 入口 | 产物 | 熵 | 有/无时间序 | **只许用于** |
| :-- | :-- | :-- | :-- | :-- |
| `nextId()` | 32-hex UUIDv7 | 74 随机位 + 48 位毫秒时间戳 | **有时间序**（B+ 索引友好，ADR-050 的本意） | **内部业务键**（存储/寻址，ADR-062） |
| `secureToken()` | 64-hex（32 字节） | 256 bit | 无 | **凭据**（refreshToken / JWT jti） |
| `randomHex(len)` | `len` 位 hex（8–64） | 4 bit/位（20 位 = 80 bit） | 无 | **对外渠道单号**（`out_trade_no` / `out_refund_no`） |

**理由 A（信息泄露）**：UUIDv7 的前 48 位是**毫秒时间戳**。对外单号会流经**渠道后台、对账文件、客服工单、
第三方排查群**等本方不可控的介质；任何人拿到号即可**反推下单时刻**，长期累积即可做**订单量 / 流量时段画像**。
对内部业务键这无所谓（不出服务），对**对外**单号这是不应提供的元数据。

**理由 B（长度零余量）**：微信 `out_trade_no` 协议上限 **32 字符**（固化在适配层
`PaymentClient.MAX_OUT_TRADE_NO_LENGTH`，`PaymentClient.java:32`），而 UUIDv7 的 32-hex **正好占满 32 位 ——
零余量**。任何前缀、分隔、后缀或将来任何一位追加都**立即超限、渠道拒单**。
取 20 位后留下 **12 位余量**，且 80 bit 熵的碰撞概率在本平台量级下可忽略（见 §后果 R2）。

> **因此「凭据类」与「对外单号类」是两个**显式**入口，不是一个入口的两组参数**：
> 凭据要「不可预测 + 不可推断」（256 bit 全随机），对外单号要「短 + 不可推断」（长度可控的纯随机）。
> 把两者合并会同时破坏两侧：凭据缩短即降熵，单号取 64 位即超渠道上限。

### 4. 退款 API 的 `out_trade_no` 语义：**原交易的对外渠道号**

微信 / 支付宝退款接口的 `out_trade_no` 指的是**那笔要退的原交易的商户单号**
（= 支付时我们发出去的 `out_trade_no` = `pay_order.channel_trade_no`），
**不是**内部 `pay_order_no`，也**不是** `refund_order.refund_no`。三个号在退款请求里各就各位：

| 渠道字段 | 本方取值 | 绝不可以是 |
| :-- | :-- | :-- |
| `out_trade_no`（定位原交易） | `pay_order.channel_trade_no` | `pay_order_no` / `order_no` |
| `out_refund_no`（本次退款的渠道幂等键） | `refund_order.out_refund_no` | `refund_no` / `pay_order_no` |
| `refund_id`（**渠道回填**的退款单号） | 落 `refund_order.channel_refund_no` | 对外退款项号 |

契约原文已同步到接口层（`PaymentClient#refund` javadoc：`outTradeNo`「**不是**内部 `pay_order_no`」、
`outRefundNo`「**不是**内部 `refund_no`」），属**渠道协议知识收在适配层**的既有口径（ADR-033）。

`queryRefund(outRefundNo)` 同理：查询键是**对外退款项号**，重试必须复用同号
（`RefundOrder` 的 `outRefundNo` 跨重试不变，`RefundManager#retry` 走 `execute` 复用既有聚合）。

### 5. 对账比对键 = 渠道号；「渠道有本地无」**不伪造本地单号**

`ReconciliationComparer` 的三条口径（`ReconciliationComparer.java:19-29`）：

1. **比对键** = 渠道账单的「商户订单号」列 = `pay_order.channel_trade_no`。
   **内部 `pay_order_no` 从不外发，账单里不可能出现** —— 拿它比对在数学上就恒不匹配（这正是修复前的形态）。
2. **四态判定**不变（1 匹配 / 2 渠道有本地无 / 3 本地有渠道无 / 4 金额不一致），仍 record-only（ADR-036）。
3. **明细两侧标识**：命中本地单的行同时回填 `payOrderNo`（内部定位用）与 `channelTradeNo`（与账单核对用）；
   **只有渠道有的行 `payOrderNo` 为 `null`** —— 这类差异的语义正是「渠道侧有、本地未落账」，
   **伪造一个本地单号会让管理端去追一个不存在的支付单**，比空值危险得多（`ReconciliationComparer.java:81-83`）。

### 6. 一个必须留档的命名决策：为什么新增列叫 `out_refund_no` 而不是 `channel_refund_no`

> **本条的判断过程比结论更值得留档** —— 它是「命名冲突导致正确性问题」的典型样本。

**任务原要求**：新增列叫 `channel_refund_no`，用于承载对外退款项号。

**实测冲突**：`refund_order.channel_refund_no` **已经存在**（`Initialize.sql:957`，V5.5 起），
且语义是**「渠道回填的退款单号」= 微信 `refund_id`**，被两处真实使用：

- `RefundVO.channelRefundNo`（退款单出参，管理端 AD43 看的就是它）；
- `RefundOrderRepository#markSuccessIfProcessing(String refundNo, String channelRefundNo)`（条件置成功时回填）。

**若复用同名**：回调处理会把渠道 `refund_id` **写进**这个列，于是**对外退款项号被覆盖**。
覆盖之后：① 重试（需复用同号才能命中渠道幂等）**立刻失效** → 可能重复出款 / 渠道拒单；
② 退款单查询（按 `out_refund_no` 查渠道）**查不到** → 悬挂补偿永久卡死。
**这不是命名偏好问题，是正确性问题**：一个列不能同时承载「我方发出去的号」与「渠道回填的号」，
二者生命周期反向（先出后回），必有一方被另一方覆盖。

**结论**：新增列用**微信协议原名 `out_refund_no`**（对外退款项号），`channel_refund_no` **保持原语义不变**。
**三个号**自此同时存在于同一行：`refund_no`（内部定位）/ `out_refund_no`（对外退款幂等键）/ `channel_refund_no`（渠道 refund_id），
**互不替代**（术语表见 `backend/03-Domain-Model.md` §六）。

### 7. 承载层解耦规则（本条一并裁决 —— 为何并入 063 而非 062）

> **规则**：**只有当承载位「天然表达实体身份」且「对外可寻址」时，才放业务键；
> 否则放内容派生值（内容哈希 / 短随机 / 短码）。**

**四类承载位落到「非实体寻址」一侧**（逐条给现状与落点）：

| # | 承载位 | 长度 / 形态约束（实测） | 业务键放进去会怎样 | 裁决 |
| :- | :-- | :-- | :-- | :-- |
| 1 | **渠道号**（`out_trade_no` / `out_refund_no`） | 微信 `out_trade_no` ≤ **32**（`PaymentClient.java:32`）；`out_refund_no` ≤ 64 | 32-hex 业务键**正好零余量**，任何前缀即超限 | ✅ **已由本条 §决策 1/3 解决**：独立成列 + 20 位纯随机 |
| 2 | **`scene`**（小程序码，P37） | 微信 `wxacode.getUnlimited` 的 `scene` ≤ **32 可见字符** | `"p=" + 32-hex` = **34 字符 → 超限**（负 2）；截断会**指向另一个商品** | ⏳ **短码映射**（业务键 → 短码 → 小程序端用短码换回业务键）。B5 以「保技术键」寄存并登记为唯一例外（ADR-062 §后果 E1），**B7 落地短码**（落点见交付清单） |
| 3 | **objectKey**（MinIO 对象键等） | 存储侧宽松，但**对象一旦生成即被长期引用**（CDN / 预签名 URL / 缓存） | **业务键不得作对象键**：① 换内部 ID 方案即令全部对象孤立；② 业务键是「身份」而非「内容」，内容不变而键变（如改名）会重复生成；③ 对外对象键可被枚举/反推 | ⏳ **改内容哈希或短随机**（B7 落点），当前 `wxa-code/product/{技术键}.png` 为寄存形态（ADR-062 §决策 2 表 B #3） |
| 4 | **短链 / 分享码** | 载体（二维码 / 短信 / 卡片文案）长度受限、人眼/手工可抄 | 业务键过长且暴露内部标识；换 ID 方案即令已投放物料失效 | ⏳ **短码映射**（与 `scene` 同一条通道，落点见交付清单） |

**为何并入本 ADR 而不是 ADR-062**：

1. **分权**：ADR-062 是 java-architect 的决策记录（ADR-053：决策类归该域决策者）。本条是**新决策**，
   不是对 062 的修订 —— 062 §后果 E1 只登记了 `scene` **一项**残余风险，而这里要立的是**一条覆盖四类承载位的通用规则**。
2. **同源**：四类承载位里**渠道号这一项正是本条 §决策 1/3 的产物** —— 规则与它的第一个实例必须写在一起，
   否则下一批只会记得「有个 scene 的坑」，记不住「承载位要先判是否表达实体身份」。
3. **可检索性**：`channel_trade_no` / `out_refund_no` / `scene` / objectKey 四件事，在本 ADR 里能一次读全。

**并登记一条前缀预算硬约束**：

> **若将来给业务键加值前缀（如 `spu_…`，ADR-062 §决策 5 保留了该能力），
> 所有承载位的长度余量必须**再减前缀长度**，且必须与承载解耦同批落地。**

**因为现有余量已经归零或为负**（实测算术，不做乐观假设）：

| 承载位 | 容量 | 业务键形态占用 | 余量 |
| :-- | :-- | :-- | :-- |
| 微信 `out_trade_no`（修复前） | 32 | 32（32-hex 业务键） | **0**（加 4 位前缀即 `spu_`+32 = 36 → 超限 4） |
| 微信 `scene`（P37） | 32 | 34（`p=` + 32-hex） | **−2（已超限）** |
| `chat_message.conversation_id`（`VARCHAR(36)`，`Initialize.sql:815`） | 36 | 57（`buyer_` + id + `_seller_` + id + `_product_` + 32-hex，取最小 id 已 57） | **−21（已超限）** |

> 结论：**加前缀不是「加几个字符」，而是把三个承载位从「勉强/已经不够」推成「确定不够」** ——
> 因此「加前缀」与「承载解耦」**必须同批**，且**先解耦、后加前缀**。

## 守卫（防复发）

| # | 守卫 | 断言 / 落点 | 现状 |
| :- | :-- | :-- | :-- |
| 1 | **不变量：对外渠道号与内部业务键不得共用 / 混用** | 领域层不变量（`PayOrder`/`RefundOrder` javadoc 已写定）+ 文档硬约束（`collaboration/09-constraints.md` §三·五「对外渠道号与内部业务键不得共用/混用」）。**评审必查项** | ✅ 已登记 |
| 2 | **发起侧传给渠道的号 ≠ 内部 `order_no` / `pay_order_no`** | 记录型渠道客户端（`RecordingPaymentClient`）断言**实际外发**的 `outTradeNo` 与 `payOrder.getChannelTradeNo()` 相等，且 ≠ 内部两键；`PayChannelTradeNoFlowTest`（6 例）覆盖 | ✅ 已实现 |
| 3 | **回调携带内部键 → 必须查不到** | 用内部 `order_no` / `pay_order_no` 作为回调 `out_trade_no` → 断言 `ORDER_NOT_FOUND`（即「两键已彻底分离」的反证）；`PayChannelTradeNoFlowTest`。退款侧同构：`RefundChannelNoFlowTest`（4 例） | ✅ 已实现 |
| 4 | **变异可捕获**（证明守卫非空转） | 已实测两轮：① 回调改回按 `pay_order_no` 查 → **8 例红**；② 退款回调改回按 `refund_no` 查 → **8 例红**；恢复后全绿。**新增渠道号相关路径必须沿用「生产工厂生成号 + 记录型客户端断言外发」的写法，禁止硬编码号再桩查询** | ✅ 变异已验证 |

> **守卫 4 的真正含义**：本次缺陷能被四类守卫抓住，唯一原因是**测试不再自行编造两侧数据** ——
> 号由生产工厂（`PayOrder#newChannelTradeNo`）生成，渠道侧由 `RecordingPaymentClient` 记录**真实外发值**，
> 断言站在接缝上。**任何新增资金链路用例都应遵守这一点**，而不是回到 `Map.of("out_trade_no","PAY-1")`。

## 备选方案

| 方案 | 优点 | 缺点 |
| :-- | :-- | :-- |
| **对外号 = 内部 `pay_order_no`（复用一列）**（近似缺陷前形态） | 零新增列、零代码改动 | 内部键被外发 → 长度/形态/换算法全部变成对外契约（BREAKING 风险）；且「发起发什么、回调查什么」仍可各自漂移 —— **正是本次缺陷** |
| **对外号 = `orders.order_no`** | 与业务订单一一对应，客服友好 | 把 trade 域标识变成 pay 域的渠道契约；退款、对账、部分退款都会绕回跨域耦合；且 `order_no` 本身是 32-hex，零余量 |
| **UUIDv7 截断作对外号** | 复用既有生成器 | 前 48 位是毫秒时间戳，**截断后仍可反推时刻**；且 32 位对 32 上限零余量 |
| **`secureToken()` 全量 64-hex 作对外号** | 熵最高 | 微信 `out_trade_no` 上限 32 → **直接超限**；单号也不需要 256 bit |
| **本条：独立列 + `randomHex(20)` + 单一口径寻址** | 语义一列一义；长度留 12 位余量；无时间序；跨重试稳定；碰撞由 `UNIQUE` 硬失败 | 多一列、多一个索引；历史 NULL 渠道号无回调（R1）；渠道号不可复用为「人读单号」 |

## 后果

### 正面

- **回调能查到单**：这条链路从「必然失败」变成「必然命中」，且**不以任何兜底分支为代价**；
- **退款能原路退回**：`out_trade_no` / `out_refund_no` 两个号各就各位，重试复用同号 → 渠道幂等键稳定；
- **对账可对**：比对键与账单列同源，「渠道有本地无」不再被伪造成本地单号；
- **语义隔离**：内部键换算法（长度/形态）不再是对外契约的 BREAKING —— 该列与内部键彻底解耦；
- **命名决策留档**：`channel_refund_no` 冲突的推理过程成文，避免下一次「按任务字面复用一个已占用的列」。

### 负面 / 成本

| 成本 | 说明 |
| :-- | :-- |
| **多两列 + 两索引** | `pay_order.channel_trade_no` / `refund_order.out_refund_no`；`UNIQUE` 均有实际用途（碰撞硬失败 + 回调寻址） |
| **口径要靠纪律维持** | 「单口径寻址」是**约定**而非编译器强制；靠 §守卫 2/3 + 评审清单兜住 |
| **文档三处联动** | 04（列）/ 05（AD45 明细视图）/ 09（不变量）任一漏改即自相矛盾（本批已同步，见交付报告） |

### 已知残余风险（须显式登记，不得视为已解决）

| # | 风险 | 触发条件 | 后果 | 收敛方向 |
| :- | :-- | :-- | :-- | :-- |
| **R1** | **历史 NULL 渠道号的支付单，其回调查不到** | 若存在「修复前创建、且当时未外发对外号」的支付单 | 回调 `ORDER_NOT_FOUND` → 渠道重推 | **本平台无存量数据**（[ADR-045](./045-db-snapshot-single-source.md)：库无存量数据、无迁移）；一旦确实出现，**按渠道对账单回填** `channel_trade_no` 后用对账口径收敛，**不做按内部键兜底查**（§决策 2） |
| **R2** | **渠道号碰撞** | 两条记录生成同一 20 位 hex | 依赖 `UNIQUE KEY uk_channel_trade_no` / `uk_out_refund_no` **硬失败**（插入异常），不做静默重试 | 概率 2⁻⁸⁰ 量级；接受硬失败（重试由上层人工/重放流程处理），**不引入「碰撞后自动换号」的隐藏分支** |
| **R3** | **超时后重新发起会换新渠道号** | 支付单超时取消（`PayTimeoutJob`）后用户重新下单支付 | 渠道侧会看到同一业务订单的两个商户单号 | **既有策略**：旧单已被条件取消（`PENDING→TIMEOUT_CANCEL`），新单是另一笔支付单；由**对账**兜底（本地有渠道无 / 渠道有本地无 均可见）。**不改**「复用已取消单」的口径 |
| **R4** | **`out_refund_no` 只对本渠道幂等** | 跨渠道（微信 → 支付宝）退款 | 渠道侧幂等键不通 | 退款按**原支付渠道**原路退回（ADR-035），跨渠道退款不在范围内 |
| **R5** | **承载层规则尚未全量落码** | `scene` / objectKey / 短链仍为寄存形态（技术键） | 与 ADR-062 §后果 E1 同一笔账 | **B7 后端实施清单**（见交付报告），逐项落点已给 |
| **R6** | **前缀预算未强制执行** | 若将来加值前缀而不解耦承载层 | 三处承载位确定超限（§决策 7 表） | 已登记为硬约束（`09-constraints.md`）；**加前缀必须与承载解耦同批** |

## 日期

2026-10-18（文档定稿）；代码落码 2026-10-17（`256abbf3`）

## 参考

- **代码（实测依据，2026-10-17/18）**：
  - `pay-service`：`domain/model/payorder/PayOrder.java`（`:53` `CHANNEL_TRADE_NO_LENGTH=20`、`:77` `channelTradeNo`、`:158-163` 余额为 NULL、`:205-212` 复用不换号）、
    `domain/model/refundorder/RefundOrder.java`（`:48` `OUT_REFUND_NO_LENGTH=20`、`:63` `outRefundNo`、`:90` `channelRefundNo`、`:184-186` 生成）、
    `app/PayManager.java`（`:30-34` 链路口径、`:101`/`:133` 发起、`:254` 回调寻址）、
    `app/RefundManager.java`（`:38-43` 三号口径、`:181` 退款回调寻址、`:186` `markSuccessIfProcessing`、`:245` 渠道退款两号）、
    `app/RefundResumeManager.java`（按 `out_refund_no` 查询收敛）、
    `domain/service/ReconciliationComparer.java`（`:19-29` 比对键、`:81-83` 不伪造本地单号）、
    `domain/repository/PayOrderRepository#getByChannelTradeNo`、`domain/repository/RefundOrderRepository#getByOutRefundNo`
  - `ia-common`：`common/utils/IdGenerator.java`（`:9-60` 三入口语义、`:198` `secureToken`、`:221-229` `randomHex`、`:91` 最短 8 位）
  - `ia-integration`：`payment/PaymentClient.java`（`:32` `MAX_OUT_TRADE_NO_LENGTH=32`、`:94-106` 退款两号 javadoc）、
    `payment/WechatPaymentClient.java`（`:259` `out_refund_no` 渠道幂等键、`:261` 退款寻址说明、`:269-294` 退款请求：`setOutTradeNo`/`setOutRefundNo`/`setReason` 在 `:272-275`、渠道调用 `:282`）
  - 测试：`pay-service` `PayChannelTradeNoFlowTest`（6）、`RefundChannelNoFlowTest`（4）、`support/RecordingPaymentClient`、`support/InMemory{Pay,Refund}OrderRepository`
  - DB：`Implementation/back-end/database/Initialize.sql`（`pay_order.channel_trade_no` + `uk_channel_trade_no`；`refund_order.out_refund_no` + `uk_out_refund_no`；`reconciliation_detail.channel_trade_no` 且 `pay_order_no` 放开 NULL）
- **契约**：`contract/05-API-Specification.md` §1.9（渠道号不出现在支付/退款报文）、§4.13.14（AD44/AD45）、§8.6（`ReconciliationDetailVO` 样例）
- **ADR**：[ADR-002](./002-dual-primary-key-and-money.md)（双主键 + 金额用分 + 订单快照）、
  [ADR-029](./029-channel-callback-protocol-over-result-envelope.md)（渠道回调协议，500 触发重推）、
  [ADR-033](./033-wechat-callback-decrypt-in-adapter.md)（回调验签/解密收在适配层）、
  [ADR-035](./035-refund-fund-flow-and-refunded-activation.md)（退款资金流 / 原路退回）、
  [ADR-036](./036-channel-reconciliation-record-only-and-rewrite-idempotency.md)（对账 record-only 与四态）、
  [ADR-045](./045-db-snapshot-single-source.md)（库无存量数据 / 无迁移）、
  [ADR-050](./050-business-id-binary16-uuidv7.md)（`BINARY(16)` + UUIDv7 + `IdGenerator` 唯一出口）、
  [ADR-053](./053-doc-ownership-by-nature-and-two-layer-standards.md)（文档分权）、
  [ADR-061](./061-order-item-business-key-snapshot-and-price-compare.md)（订单项业务键 + 快照）、
  [ADR-062](./062-external-identifier-and-addressing.md)（对外标识与寻址口径；**本条是其第四条腿**，`scene`/objectKey 残余风险见其 §后果 E1）
