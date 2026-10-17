# ADR-063: 对外渠道号与内部业务键分离 —— 唯一寻址口径与承载层解耦

## 状态

**已裁决并已落码** —— 代码落码 2026-10-17（`256abbf3`「fix(pay)!: 对外渠道号与内部业务键分离（修复资金回调链路必然失败）」，提交报告全量 1931 测试绿 / 0 failures，**本批（B6）未复跑该全量**）；
文档同步 2026-10-18（批次 B6）。

**§决策 7 / §决策 8 的批次链（登记，避免误读为「已全量落码」）**：
B6 立规则（§决策 1-7）→ **B7a 裁决落点并写定契约**（§决策 8 短码方案、新增 **P38** 端点、承载层六项条款、objectKey 内容哈希 + `v1/` 灰度）
→ **B7b 代码落码**（`SpuShareManager` 的 `scene`/objectKey、`spu.short_code` 列、`MAX_CSV_IDS` 常量族等；清单见契约 §十二 12.2 的 B7a 新增行）。

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
| 2 | **`scene`**（小程序码，P37） | 微信 `wxacode.getUnlimited` 的 `scene` ≤ **32 可见字符** | `"p=" + 32-hex` = **34 字符 → 超限**（负 2）；截断会**指向另一个商品** | ✅ **已裁决（B7a）**：**短码**（`scene = "p=<短码>"`，共 12 字符）+ **新增 P38** `GET /api/item/spu/by-code/{code}` 换回 SPU；短码细则见 §决策 8，**落码批次 B7b** |
| 3 | **objectKey**（MinIO 对象键等） | 存储侧宽松，但**对象一旦生成即被长期引用**（CDN / 预签名 URL / 缓存） | **业务键不得作对象键**：① 换内部 ID 方案即令全部对象孤立；② 业务键是「身份」而非「内容」，内容不变而键变（如改名）会重复生成；③ 对外对象键可被枚举/反推 | ✅ **已裁决（B7a）**：**内容派生值** —— `wxa-code/v1/product/{sha256(scene\|page) 前 16 hex}.png`；`v1/` 版本段承载旧键灰度与清理，**落码批次 B7b**（条款原文见契约 §1.9⑦-2） |
| 4 | **短链 / 分享码** | 载体（二维码 / 短信 / 卡片文案）长度受限、人眼/手工抄 | 业务键过长且暴露内部标识；换 ID 方案即令已投放物料失效 | ✅ **已裁决（B7a）**：与 #2 **同一条通道、同一套短码**（§决策 8）—— 短码即分享码的对外形态，**不另设第二套映射**（两套映射 = 两处可漂移的编码表） |

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

### 8. 短码（`short_code`）：存储 / 生成 / 生命周期 —— 唯一方案（B7a 裁决，B7b 落码）

**结论先给**：**在 `spu` 表加短码列**（`short_code VARCHAR(16) NOT NULL` + `UNIQUE KEY uk_short_code`），**不建独立映射表**；**不过期**，与商品同生命周期；**永不复用**；库**无存量数据 ⇒ 无迁移、无回填**。

本节是 §决策 7 表 #2（`scene`）的落地方案，也是 §决策 7 规则在**「短 + 不可推断 + 必须可寻址」**这类承载位上的第一个实例。

#### 8.1 为什么「加列」而不是「独立映射表」

| 判据 | 加列（采纳） | 独立映射表（否决） |
| :-- | :-- | :-- |
| 关系 | 1 SPU : 1 短码，**同生同灭** | 名义 1:1，却**允许空窗**：表里没有行时 SPU 就没有短码 |
| 生成时机可控性 | `NOT NULL` ⇒ **必须有值** ⇒ 生成只能发生在「商品创建」这一处 | 可空 ⇒ 逼出「首次分享时**懒生成**」⇒ 同一 SPU 在不同请求可能拿到**不同短码**，已投放物料随之失效 —— 与 §决策 1 第 3 点「跨重试稳定」同一机理 |
| 热路径成本 | 一次等值查（`uk_short_code`），**零 join**（小程序码落地页是 C 端高频只读） | 多一次 join；想省就得加缓存，等于把一致性问题挪到缓存 |
| 一致性义务 | 单表单行，**无跨表一致性义务** | 两表须同事务 + orphan 清理 + 「有 SPU 无映射」的兜底语义 |
| 职责归属 | 短码是 **SPU 自己的对外别名（alias）**：不可变、唯一、随创建生成、无独立生命周期 ⇒ **聚合内属性**，**不侵 `spu` 聚合职责** | 映射表只在「1 SPU 多短码」或「短码独立轮换/过期」时才更合适 —— 本方案**两者皆否**，故不该预付复杂度 |

> **残余风险 R7（新增登记）**：若将来需要「按渠道分发不同短码」或「短码轮换 / 召回」，本方案需迁表。
> 触发器是「要按短码统计扫码来源」或「物料泄露需整体作废」——**目前两者皆无**（分享码是纯曝光入口，无归因统计）。

#### 8.2 生成口径

| 项 | 口径 | 理由 |
| :-- | :-- | :-- |
| 算法 | **`IdGenerator.randomHex(10)`** —— 10 位小写 hex = **40 bit 熵** | 复用既有**唯一出口**（不新造字母表）；纯 `SecureRandom`、**无时间序**；**不复用业务键任何子串**（不截取、不加前缀/后缀，与 `spu_id` 无派生关系 ⇒ 不可反解） |
| 长度 | 契约写 **8–10 位**，实现取 **10 位** | 8 位 = 32 bit：n=10⁵ 商品时期望碰撞 ≈ **1.2 次**（重试链必然被触发）；10 位 = 40 bit：同规模 ≈ **0.0045 次**。再长无收益（只增加手抄/OCR 成本） |
| `scene` 预算 | `p=` + 10 = **12 字符**，占 32 上限的 37.5%，**余 20 字符** | 给将来前缀变更留余量；与 §决策 7 的前缀预算禁令一致（加前缀必须与承载解耦同批） |
| 字符集 | 小写 `[0-9a-f]` | `spu` 表排序规则是 `utf8mb4_unicode_ci`（**大小写不敏感**）：只有**单一大小写形态**才不会被 CI 折叠成「看似不同、实为同一码」。将来若换 base62，**必须**同时把该列改 `_bin`/`_as_cs`，否则 `AbC` 与 `abc` 会被唯一键**判等**（静默冲突） |
| 生成时刻 | **商品创建时、同一事务内定稿**（`Spu#create` 聚合工厂，口径同 `PayOrder#newPending`） | 一次定稿 ⇒ **跨请求稳定** ⇒ 已生成的小程序码永远有效。**禁止懒生成**（见 8.1 第二行） |
| 冲突 | **有界重试 ≤ 3 次**（每次取新随机值），仍冲突 → **硬失败**（500 `INTERNAL_ERROR`） | 见 8.3 |

#### 8.3 冲突处理：为何这里「重试」而渠道号「硬失败」，两者不是不一致

- **渠道号**重试会**改变渠道侧幂等键**（`out_trade_no` 已在或即将发给渠道）—— 换号 ⇒ 渠道看到两笔不同交易 ⇒ 与 §决策 1 第 3 点直接冲突，故**只许硬失败**（§后果 R2）。
- **短码**重试发生在**发布之前**（此刻没有任何物料 / 小程序码引用它），不承载幂等语义，换号**无外部可见后果**，故**有界重试**更快收敛，避免「商家发布商品」因 40 bit 碰撞失败。
- **共同禁令**：两者都**禁止**「碰撞后回落到派生值」（截取业务键、加时间戳、自增序号）—— 派生值会退化为**可枚举、可预测**，等于把承载位重新绑回业务键，正是本次解耦要消除的病根。

#### 8.4 生命周期

- **不过期（采纳）**，与商品同生命周期。短码不是凭据：不承载权限、不承载金额、不暴露内部标识（40 bit 随机与 `spu_id` 无派生关系）。给它设过期只会让**已印在物料上的码**在某一刻突然失效，收益为零、成本是一次静默断链。
- **永不复用（硬约束）**：商品软删（`status=3`）时短码**保留占位**，不回收、不重新分配。理由：小程序码 / 二维码会印在包装、海报、名片上，**物理介质无法撤回** —— 复用会把旧物料指向**另一个商品**，与「`scene` 截断」是同一类不可接受错误（§决策 7 表 #2）。
- 短码存在但商品已删 / 不存在 ⇒ **P38 返回 404 `NO_RESOURCE_FOUND(1103)`**，短码仍算「已占用」。

#### 8.5 迁移

**无迁移、无回填**。库**无存量数据**（[ADR-045](./045-db-snapshot-single-source.md)：库无存量数据、无升级路径），`Initialize.sql` 是唯一结构真相源 ⇒ **直接加列**，`NOT NULL` 可立即生效，不存在「历史 SPU 无短码」的中间窗口。

#### 8.6 访问面：短码能出现在哪里

- **对外只允许两处**：P37 写进 `scene`（`p=<短码>`）、P38 以路径变量 `{code}` 读出。
- **不得进任何 VO / DTO**（含 `SpuVO`、`WxaCodeVO`、`SpuSearchVO`）—— 一旦进报文，客户端会把它当实体标识缓存 / 回链，**别名就升格成标识**，§1.9⑧ 的禁令随之失效。
- 日志可用（非 PII、无反向解析能力），但**不得**与 `spu_id` 写成「同义」。

#### 8.7 B7b 落码对照表（实现事实，2026-10-18）

> 本节只登记**代码事实**（文件:落点 → 验收断言），口径仍以 §决策 7/8 与契约 §1.9⑦/⑧ 为准。

| # | 承载位 | 落点（文件） | 验收断言（测试名） |
| :- | :-- | :-- | :-- |
| 1 | 短码发号 | `spu` 聚合工厂 `Spu#publish`（`ShortCode.newCode()` = `IdGenerator.randomHex(10)`）；冲突有界重试 ≤3 在 `SpuManager#saveWithShortCodeRetry`，只认 `uk_short_code` 冲突，其余唯一键原样抛出 | `shouldAssignShortCode_whenPublishing` / `shouldRetryWithNewShortCode_whenUniqueKeyCollides` / `shouldFailLoud_whenShortCodeCollisionPersists` / `shouldRethrow_whenOtherUniqueKeyCollides` |
| 2 | 短码存储 | `database/Initialize.sql`：`spu.short_code VARCHAR(16) NOT NULL` + `UNIQUE KEY uk_short_code`（ADR-045 直改快照，无迁移） | `SpuShortCodeRepositoryImplH2Test`（等值查命中 / 软删仍可查 / 重复插入被唯一约束拦下） |
| 3 | 短码解析 | 端口 `domain/port/ShortCodeResolver`；实现 `infrastructure/adapter/ShortCodeResolutionAdapter`（L1 Caffeine 10k·10min / L2 Redis `item:shortcode:{code}`→业务键·7d / L3 DB；负缓存 45s；`get(key,loader)` single-flight；Redis 异常 fail-open 到 DB，DB 异常 500 且不写负缓存）+ 四类命中计数 | `shouldResolveFromDb_whenBothCachesEmpty` / `shouldFallBackToDb_whenRedisThrows` / `shouldNegativeCache_whenShortCodeUnknown` / `shouldLoadOnce_whenConcurrentMisses` / `shouldFailLoudWhenDbDown` |
| 4 | P38 端点 | `SpuController#detailByCode`（`GET /by-code/{code}`，变量名 `code` —— 承载位别名，不谎称实体）+ `SpuManager#getSpuDetailByCode`（短码→业务键→**复用** `getSpuDetail`） | `SpuControllerByCodeTest`（200/404/400）+ `SpuDetailWireShapeTest`（P2↔P38 逐字段同形） |
| 5 | `scene` / objectKey | `app/share/SpuShareManager`：`scene = "p=" + 短码`；`objectKey = wxa-code/v1/product/{sha256(scene\|page)[0:16]}.png`（`v1/` 仅为将来换算法留位 —— 项目未发布过 ⇒ **无存量对象、无需灰度/清理**） | `SpuShareManagerTest#shouldKeepSceneWithinWechatLimit` / `#shouldDeriveObjectKeyFromContent` / `#shouldBeStableForSameContent` |
| 6 | `home_config.spu_ids` | 值对象 `domain/model/home/SpuIdCsv`（写侧 `requireWire`+≤15+去重 fail-closed；读侧单项非法 fail-open 过滤）+ `HomeConfigManager` 两侧收口 | `SpuIdCsvTest`（8 例）+ `HomeConfigManagerTest`（6 例） |
| 7 | `conversation_id` | `domain/model/conversation/ConversationId`（`sha256(buyerId\|sellerId\|spuId)[0:32]`，唯一派生点 = `ChatMessageManager#sendMessage` 归一） | `ConversationIdTest`（含「32 ≤ 36」与「旧复合串超宽」反证）+ `ChatMessageManagerTest#shouldNormalizeLegacyConversationId_whenSending` |
| 8 | 退款 `reason` | `PaymentClient.MAX_REFUND_REASON_LENGTH = 80`（取微信 80 / 支付宝 256 的较严者）+ `RefundReason`（枚举文案优先 / 含单号整句回落 / 按 code point 截断）+ 唯一出口 `PaymentChannelRouter#refund` | `RefundReasonTest`（9 例，含 emoji 代理对不被劈开）+ `RefundReasonWireGuardTest`（站在渠道接缝上断言真实外发值无单号） |
| 9 | `MAX_CSV_IDS` 常量族 | `ia-common` `common/constant/CsvIdLimits`（列宽 15 / URL 4 / body 200 / 购物车 200）；消费方：`home_config` 写侧、`cart` 加购闸门（`CART_ITEM_LIMIT_EXCEEDED(3042)`，业务规则拒绝 = 200 信封）、`ShipOrderReq.skuIds` 的 `@Size` | `SpuIdCsvTest` / `CartManagerTest#shouldReject_whenCartCapacityReached` |
| 10 | P2 错误承载纠正 | `PRODUCT_NOT_FOUND(3011)` 全仓调用点 → `NO_RESOURCE_FOUND(1103)`：item（详情 / 上下架 / 编辑 / `assertVisible`）、user（收藏加/删）；枚举 `HttpStatus.OK → NOT_FOUND` 并标 `@Deprecated` | `SpuManagerTest#shouldThrow404_whenSpuMissing` 等（断言 `NO_RESOURCE_FOUND` 且 `HttpStatus = 404`） |

> **B7b 未覆盖项（诚实登记）**：① `ai_conversation.conversation_id` 是 `BINARY(16)`（UUIDv7）而 `chat_message.conversation_id` 是内容派生 `VARCHAR(36)` —— **两套口径并存是有意选择**（前者「表当前无代码接入」），本批只在两侧列注释里写死「勿混」，**没有**统一算法；② `MAX_COMPARE_SPU_IDS`（P24）与 C9 的消费方**代码尚不存在**，该值仅常量 + 单测；③ 短码解析的**跨实例**防击穿未加分布式互斥（单实例内由 Caffeine per-key 串行化保证；跨实例靠负缓存 + L2 收敛，注册为已知取舍）。

## 守卫（防复发）

| # | 守卫 | 断言 / 落点 | 现状 |
| :- | :-- | :-- | :-- |
| 1 | **不变量：对外渠道号与内部业务键不得共用 / 混用** | 领域层不变量（`PayOrder`/`RefundOrder` javadoc 已写定）+ 文档硬约束（`collaboration/09-constraints.md` §三·五「对外渠道号与内部业务键不得共用/混用」）。**评审必查项** | ✅ 已登记 |
| 2 | **发起侧传给渠道的号 ≠ 内部 `order_no` / `pay_order_no`** | 记录型渠道客户端（`RecordingPaymentClient`）断言**实际外发**的 `outTradeNo` 与 `payOrder.getChannelTradeNo()` 相等，且 ≠ 内部两键；`PayChannelTradeNoFlowTest`（6 例）覆盖 | ✅ 已实现 |
| 3 | **回调携带内部键 → 必须查不到** | 用内部 `order_no` / `pay_order_no` 作为回调 `out_trade_no` → 断言 `ORDER_NOT_FOUND`（即「两键已彻底分离」的反证）；`PayChannelTradeNoFlowTest`。退款侧同构：`RefundChannelNoFlowTest`（4 例） | ✅ 已实现 |
| 4 | **变异可捕获**（证明守卫非空转） | 已实测两轮：① 回调改回按 `pay_order_no` 查 → **8 例红**；② 退款回调改回按 `refund_no` 查 → **8 例红**；恢复后全绿。**新增渠道号相关路径必须沿用「生产工厂生成号 + 记录型客户端断言外发」的写法，禁止硬编码号再桩查询** | ✅ 变异已验证 |
| 5 | **短码不得进报文 / 不得用于业务判断**（B7a 新增） | 断言 `SpuVO` / `WxaCodeVO` / `SpuSearchVO` 的线格式键集合**不含** `short_code`；断言 P38 与 P2 对同一商品的响应**逐字段同形**（`diff` 为空） | ✅ **已实现（B7b）**：`SpuDetailWireShapeTest`（同形 + 键集不含 short_code，序列化接缝上断言）+ `ShortCodePayloadGuardTest`（扫 adapter/response·request + ia-api dto 全量源码，声明即红；含「命中面文件数为 0 即失败」自证） |
| 6 | **承载位容量预算**（B7a 新增） | `scene` 长度断言 `≤ 32`（`p=` + 短码 = 12）；`chat_message.conversation_id` 长度断言 `≤ 36`；`home_config.spu_ids` 条数断言 `≤ 15` 且 CSV 总长 `≤ 500` | ✅ **已实现（B7b）**：常量族落 `ia-common` `common/constant/CsvIdLimits`（15 / 4 / 200 / 200，三条判据不互借）；断言落 `ShortCodeTest`（scene 12 ≤ 32，并反证业务键 34 超限）、`ConversationIdTest`（32 ≤ 36，并反证旧复合串超宽）、`SpuIdCsvTest`（15 项 = 494 ≤ 500、16 项 = 527 溢出） |

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
| **R5** | **承载层规则尚未全量落码** | `scene` / objectKey / 短链仍为寄存形态（技术键） | 与 ADR-062 §后果 E1 同一笔账 | ✅ **已收敛（B7b 落码）**：`scene` = `p=<短码>`、objectKey = `wxa-code/v1/product/{sha256(scene\|page)[0:16]}.png`、`home_config.spu_ids` 写侧校验/读侧 fail-open、`conversation_id` 内容派生、退款 `reason` 消毒、`MAX_CSV_IDS` 常量族 + `short_code` 列**均已落码**；残余项见 §B7b 落码对照表 |
| **R6** | **前缀预算未强制执行** | 若将来加值前缀而不解耦承载层 | 三处承载位确定超限（§决策 7 表） | 已登记为硬约束（`09-constraints.md`）；**加前缀必须与承载解耦同批** |
| **R7** | **「一 SPU 一短码」方案不支持按渠道分发 / 召回** | 需要「按短码统计扫码来源」或「物料泄露需整体作废 / 轮换」时（§决策 8.1） | 需迁出独立映射表（`spu.short_code` → `spu_short_code`）并补灰度 | 触发器量化前**不预付复杂度**；届时按 ADR-045 直改快照（库无存量数据，迁移成本 = 一次 DDL + 回填脚本） |

## 日期

2026-10-18（文档定稿，批次 B6）；2026-10-18（**批次 B7a：§决策 7 落点裁决 + §决策 8 短码方案 + 契约 §1.9⑦/⑧ 与新增 P38**）；代码落码 2026-10-17（`256abbf3`）、**承载层与短码落码 2026-10-18（批次 B7b，见 §决策 8.7 落码对照表）**

## 参考

- **代码（实测依据，2026-10-17/18；B7a 追加项标注 🆕）**：
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
  - 🆕 `item-service`（B7a 实测，承载层现状与落点）：`app/share/SpuShareManager.java`（`:53` `OBJECT_KEY_PREFIX="wxa-code/product/"`、`:70` `SCENE_PREFIX="p="`、`:100-107` scene/objectKey 仍用技术键、`:114` 商品不存在/不在售 → `NO_RESOURCE_FOUND(1103)`、`:130` `scene=SCENE_PREFIX+技术键`）、
    `adapter/web/SpuController.java`（`:44` `@RequestMapping("/api/item/spu")`、`:105-108` P37、`:144-146` `IdGenerator.requireWire` 形态校验唯一口 —— **P38 落码即加在本类**）、
    `app/SpuManager.java`（`:91-95` 详情「不存在或 `status=3` → `PRODUCT_NOT_FOUND(3011)`」＝ P38 判据基准、`:96-106` 详情装配 `getSpuDetail` **应被 P38 复用而非另建**）、
    `domain/repository/SpuRepository#findByBusinessId`（短码查询将新增 `findByShortCode`）
  - 🆕 `ia-common`：`common/utils/IdGenerator.java`（`:91` `MIN_RANDOM_HEX_LEN=8`；`randomHex(int)` 8–64 位 ⇒ 短码取 10 位合法）
  - 🆕 表结构现状（承载位容量，B7a 实测 `Initialize.sql`）：`spu`（`uk_spu_id`；**无**短码列 ⇒ B7b 新增 `short_code VARCHAR(16) NOT NULL` + `uk_short_code`）、`home_config.spu_ids VARCHAR(500)`、`chat_message.conversation_id VARCHAR(36)`、`ai_conversation.conversation_id BINARY(16)`（**两套口径并存**，§决策 7 表下方与契约 §1.9⑦-4）
  - 🆕 **B7b 落码实测（2026-10-18）**：
    - `item-service`：`domain/model/spu/ShortCode.java`（发号/形态/容量）、`domain/model/spu/Spu#publish`（短码定稿）与 `#assignNewShortCode`（发布前换号）、`domain/port/ShortCodeResolver.java` + `domain/enums/ShortCodeHitSource.java`、`infrastructure/adapter/ShortCodeResolutionAdapter.java`（三级缓存）、`infrastructure/persistence/{SpuMapper#selectByShortCode,SpuRepositoryImpl#findByShortCode,SpuPO.shortCode}`、`app/SpuManager#{getSpuDetailByCode,saveWithShortCodeRetry}`、`adapter/web/SpuController#detailByCode`、`app/share/SpuShareManager#sceneOf/objectKeyOf`、`domain/model/home/SpuIdCsv.java` + `app/HomeConfigManager`（写/读两侧）
    - `ia-common`：`common/constant/CsvIdLimits.java`（15/4/200/200）、`common/utils/crypto/Sha256Util.java`（内容派生唯一出口）、`enums/ErrorCode`（`PRODUCT_NOT_FOUND` 状态纠正为 404 + `@Deprecated`；新增 `CART_ITEM_LIMIT_EXCEEDED(3042)`）
    - `ia-integration`：`payment/PaymentClient#MAX_REFUND_REASON_LENGTH = 80`、`payment/RefundReason.java`（消毒 + code point 截断）
    - `pay-service`：`app/PaymentChannelRouter#refund`（`reason` 唯一消毒出口）
    - `ai-service`：`domain/model/conversation/ConversationId.java` + `app/chat/ChatMessageManager#sendMessage`（唯一派生点）
    - `cart-service`：`app/CartManager#requireCartCapacity` + `CartRepository#countByUserId`
    - `trade-service`：`adapter/request/ShipOrderReq#skuIds` 的 `@Size(max = MAX_ORDER_ITEM_IDS)`
    - DB：`database/Initialize.sql`（`spu.short_code` + `uk_short_code`；`home_config.spu_ids` 与 `chat_message/ai_conversation.conversation_id` 注释钉死口径）
    - 门禁：`scripts/check-id-invariants.sh` 检查 5 新增**承载位别名例外**（仅 `/by-code/` 段 + 仅 `code` 名 + 类型仍须 `String`），并统计到自证输出

- **契约**：`contract/05-API-Specification.md` §1.9（渠道号不出现在支付/退款报文）、§4.13.14（AD44/AD45）、§8.6（`ReconciliationDetailVO` 样例）；🆕 **§1.9⑥/⑦/⑧（承载层六项 + 短码口径）、§4.4.1 **P38**、§七 7.1（小程序码落地页映射）、§十二 12.2（B7a 新增挂账行）**
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
