# 04 — 数据模型文档

> 来源：`doc/项目的分析流程/04.数据建模.md`、`doc/项目代码/数据库/Initialize.sql.md`、`doc/项目的分析流程/03.领域建模.md`、`drawio/IA_E-R.drawio`
> **2026-10-14（引用收敛批次）**：`review` 订单引用由技术 id（`order_id BIGINT`）收敛为业务键 `order_no BINARY(16)`（含 ReviewAPI 契约缺口修复：`ReviewCreateReq.orderNo` 替代前端无从获得的 `orderId`）；主键决策矩阵补「引用分层规则」脚注；分片影响面清单增补「跨服务技术键收敛项（order_logistics）」「跨服务物理 FK 清单」「user_id 口径前提（永久身份红线）」三行；现状核对快照口径统一为**全库表数 62 张**（分诊明细见 §一「现状核对快照」：真双 ID 23 + 特殊保留 1 + 仅技术主键 38 = 62）。
>
> **2026-10-15（fix-doc 表数与迁移残留批次）**：①全库表数口径统一为 **62 张**（实测 `grep -ciE '^\s*CREATE TABLE' Implementation/back-end/database/Initialize.sql` = 62），清除文中「45 张表 / 30 个迁移（V1.1.0→V5.6）」等过期表述（原 :7、:28）；②清除**全部** `database/migrations/V*.sql` 死链引用（该目录**已不存在**，ADR-045）；③补录 5 张此前缺条目的表（`shop_decoration`、`note_like`、`note_spu`、`task_def`、`task_record`）。
>
> **最后更新: 2026-10-08（ADR-050 B+ 业务标识存储升级）**：业务标识列 `VARCHAR(32)/CHAR(36)` → **`BINARY(16)`**（22 列转换 / 10 列保留，含 `refund_order`/`reconciliation_detail` 的 VARCHAR(64) 复制列闭包），生成算法 v4 随机 → **UUIDv7**（`IdGenerator` 全项目唯一出口），PO 层 `byte[]` 边界、domain/对外全链路 String 零契约改动；`admin` 种子业务 ID 改 `UNHEX` 定值；包装器/显式 SQL 参数强制 `toBytes`（外部输入 `toBytesOrNull`）。此前——2026-10-03
> 最后更新: 2026-10-03（**P0 四项落地同步（V5.5/V5.6 已建，ADR-035/036/037）**：§十二 12.1–12.4 状态由"规划"收敛为"已建"——退款资金流（refund_order/wallet_transaction，F1）、渠道对账（reconciliation_batch/detail，F4）、运费模型（freight_template + spu/orders 增量，F2）、拆单发货（order_logistics 主键与包裹改造 + order_item.package_no，F3）均已落库【**历史注记**：原文此处记为"随 `V5.5__…` / `V5.6__…` 迁移落库"——迁移机制已于 2026-10-05 废除（ADR-045），这些结构现全部直接位于 `Initialize.sql`，不存迁移文件】；金额不变式定稿 **V2：`payAmount = totalAmount − discountAmount + postFee`**；§3.5/§3.7/§3.8/§3.10/§3.11/§7.3/§7.6 规划标注同步收敛；§3.11「主键约定偏差」注记**清除**（代理主键 id 已补，ADR-037）；§12.3 纠错删除「order_id 转 UNIQUE uk_order_id」自相矛盾表述（实现为**普通外键 + uk_order_package**，未建单列唯一键）。此前——V5.6 规格先行：新增 §十二 四组 P0 规划表——refund_order/wallet_transaction/reconciliation_batch/reconciliation_detail（**V5.5 资金线**）、freight_template 及 spu/orders/order_logistics/order_item 变更（**V5.6 履约线**），§3.5/§3.7/§3.8/§3.10/§3.11/§7.3/§7.6 补规划增量标注，金额不变式变更为 `payAmount = totalAmount − discountAmount + postFee`；规划表均未建库，以"🆕 V5.6 规划"标注，实现落库后本注记同步收敛。此前：数据库整治 + 1:N 拆分同步：seller 瘦身/shop 表（V5.4，ADR-019）、spu/review 实际列修正、after_sale/order_logistics 补注、Initialize.sql 全量快照语义；遗留风险清零批次：`auth:blacklist:{jti}` 契约键注记（ADR-027）、`operation_log` 写入方恢复（ADR-032 管道二）、`merchant_knowledge_base.embedding_id` 口径 `es:{knowledgeId}`）
> **2026-10-16（SPU 词汇统一，ADR-059 落地批次，后端 `578fac27`）**：库表重命名同步 —— 表 `product`→`spu`、`product_view_log`→`spu_view_log`、`note_product`→`note_spu`；列 `product_id`→`spu_id`（`spu.spu_id` 业务键 `BINARY(16)`；`sku.spu_id` / `review.spu_id` / `favorite.spu_id` / `spu_view_log.spu_id` / `flash_sale.spu_id` / `note_spu.spu_id` / `group_buy_activity.spu_id` 为技术外键）、`product_ids`→`spu_ids`（`home_config`）、`hot_product_ids`→`hot_spu_ids`（`shop_decoration`）；索引/外键/唯一键 `uk_product_id`→`uk_spu_id`、`idx_product_id`→`idx_spu_id`、`idx_product`→`idx_spu`、`uk_user_product`→`uk_user_spu`、`uk_note_product`→`uk_note_spu`、`fk_sku_product`→`fk_sku_spu`、`fk_view_log_product`→`fk_view_log_spu`、`fk_product_seller`→`fk_spu_seller`、`fk_product_category`→`fk_spu_category`、`fk_product_freight_template`→`fk_spu_freight_template`（§3.5/§3.6、§七 7.1/7.2/7.7/7.8、§五、§十一、§十二同步；表数 62 不变）。**§11.2 原记 `idx_product_time` 有误**，实测 `spu_view_log` 为 `uk_user_spu(user_id, spu_id)` + `idx_user_time(user_id, view_time)`，本批次一并更正。**刻意保留**：展示名 `product_name`/`product_image`、ES 索引名 `products`、`conversation_id` 的 `product_{productId}` 格式（客户端自造，ADR-050）。线格式契约同步见 [05 §4](../contract/05-API-Specification.md)。
> **2026-10-16（历史注记词汇同步小批，`product`→`spu`）**：上一段（2026-10-03「最后更新」历史注记）中的表名旧词 `product/orders/…`、`product/review 实际列修正` 已按 [ADR-059](../adr/059-spu-sku-vocabulary-disambiguation.md) 词汇口径一并改为 `spu/orders/…`、`spu/review 实际列修正` —— 该段的**事实内容与结论未动**，仅统一词汇，避免历史注记成为旧表名的埋伏源。本文档头仍保留 `product` 旧词的两处为**合法保留**：①下一段（2026-10-16 SPU 词汇统一批次）的「改前→改后」映射记录；②**代码标识符/展示名的刻意保留清单**——展示名 `product_name`/`product_image`、ES 索引名 `products`、`conversation_id` 的 `product_{productId}` 格式（ADR-059「刻意保留」清单）。
> **2026-10-18（B6 批次：对外渠道号与内部业务键分离，[ADR-063](../adr/063-external-channel-number-separation.md)，代码 `256abbf3`）**：①`pay_order` 新增 `channel_trade_no VARCHAR(32)` + `uk_channel_trade_no` —— **对外渠道号**（渠道 `out_trade_no` 的唯一取值来源；20 位随机 hex / 80 bit 熵 / 无时间序 / 跨重试稳定 / 余额渠道为 `NULL`）；②`refund_order` 新增 `out_refund_no VARCHAR(64)` + `uk_out_refund_no` —— **对外退款项号**（渠道 `out_refund_no` 的唯一取值来源，与内部 `refund_no` 分离）；**同时明确 `channel_refund_no` 仍是「渠道退款单号（微信 `refund_id`，回调回填）」，不承载对外退款项号**（两列语义相反：一为「我方发出」、一为「渠道回填」，不可互相顶替）；③`reconciliation_detail` 新增 `channel_trade_no`（比对键）且 `pay_order_no` **放开 `NULL`**（「渠道有本地无」的明细没有本地支付单，不得伪造）；④同步订正 §12.1 `refund_order` 的 `after_sale_id`（ADR-049 起**可空**）与 `biz_ref`（ADR-049 新增，既有文档缺失）。两列均按 ADR-050「**外部格式列保留 VARCHAR**」口径（`channel_*` / 非业务标识），不进 `BINARY(16)` 转换面。
> **2026-10-18（B7a 批次：承载层解耦契约先行，[ADR-063](../adr/063-external-channel-number-separation.md) §决策 7/8）**：①`spu` 新增 **`short_code VARCHAR(16) NOT NULL` + `UNIQUE KEY uk_short_code`** —— SPU **短码**（10 位小写 hex / 40 bit 熵 / 无时间序 / 不复用业务键任何子串），唯一用途是写入小程序码 `scene`（`p=<短码>`）并由新增端点 **P38** `GET /api/item/spu/by-code/{code}` 换回 SPU；**不是实体标识**，不得进任何 VO，不得替代 `spu_id`。✅ **已收口（B7b，`265543d6`）**：`Initialize.sql` 已直改快照加列（`short_code VARCHAR(16) NOT NULL` + `UNIQUE KEY uk_short_code`；ADR-045：无迁移无回填，库无存量数据）。本条原记「本批只写文档、`Initialize.sql` 尚未加列」为 B7a 时点状态。②**承载位容量口径登记**（不新增列，但决定「一列能装多少业务键」）：`home_config.spu_ids VARCHAR(500)` ⇒ 按「33 字符/项」上限 **15 项**；`chat_message.conversation_id VARCHAR(36)` 与 AI 会话表（该表零代码引用，已按 U3 删除，见文档头注记）的 `conversation_id BINARY(16)` 曾是**两套并存口径**，✅ **已收口（B7b）**：`chat_message.conversation_id` 已对齐为内容派生 32-hex（`sha256(buyerId|sellerId|spuId)` 前 32 位，服务端唯一派生点 `ai` `ConversationId.derive`；复合串业务键化后为 57 字符，超限 21）。详见 [05 §1.9⑦/⑧](../contract/05-API-Specification.md)。
> 归属域: backend | Owner: doc-engineer | 更新触发条件: 新增/变更表结构时必须同步

> **⚠️ 2026-10-18（U3 裁决：5 张死表已删除；U14 已把逐列小节从正文清理）**
> 以下 5 张表在**代码中零引用**（全仓 `.java/.xml/.yml` 命中 = 0，仅本目录等设计文档提及），
> 已从 `Implementation/back-end/database/Initialize.sql` **删除**（CREATE 块 + DROP 行同时移除）：
> `seller_application`（商家入驻申请，已被 `seller` 取代；trade-service 的平行入驻流程已于
> 2026-10 第六轮退役删除——与商家域入驻重复且网关路由不可达）、`flash_order_log`（秒杀日志，
> 幂等实际靠 Redis Lua + `orders.uk_order_no` —— 该表列注释自称「幂等+兜底」属**假承诺**）、
> `merchant_reply_template`（客服快捷回复，从未实现）、`ai_message` / `ai_conversation`
> （IM 历史，实际由 `chat_message` 承载）。**上引括注即这 5 张表保留的全部设计意图**。
>
> **口径**：`Initialize.sql` 是**当前 schema 的唯一真源**；本文**已不再保留**这 5 张表的逐列/索引
> 结构（U14 清理：原 §7.2 秒杀日志表、§7.3 入驻申请表、§9.1 会话与消息两表、§10.2 回复模板表
> 均已从正文移除，§9.1 / §10.2 仅留标题位指回本节），不得据以编码。**表数不写死**：全库表数以
> `node Implementation/back-end/scripts/sync-doc-counts.mjs --list-tables` 为准；本文正文与历史变更
> 记录中出现的具体表数均为**当时时点值**（不随删表回改）。登记见 `collaboration/open-items.md` §2.3（U3 / U14）。

---

## 一、数据库设计约定

| 约定 | 说明 |
|------|------|
| **主键策略** | 见下方「主键决策矩阵」 |
| **金额** | 统一使用 `INT`，单位为「分」 |
| **金额不变式** | **V2（当前实现，[ADR-037](../adr/037-freight-model-package-split-and-refund-trigger.md)）**：`payAmount = totalAmount − discountAmount + postFee`；旧口径（V5.6 前）：`payAmount = totalAmount − discountAmount`。`totalAmount` 仍严格 = Σ `order_item.subTotal`（运费不进商品小计），运费单列入 `orders.post_fee`、下单时快照——详见 §十二 12.2 |
| **布尔** | 使用 `TINYINT`，1-是 / 0-否 |
| **状态枚举** | 使用 `TINYINT` + 注释说明枚举值 |
| **审计字段** | 所有表包含 `create_time` 和 `update_time` |
| **快照模式** | 订单相关的用户/商品/地址信息在下单时复制到订单表，保证历史可追溯 |
| **乐观锁** | `orders`：`version` INT + MyBatis-Plus `@Version`（乐观锁拦截器已在 ia-common 注册，V5.3 补齐）；`sku`：**并发控制改为单条原子 SQL**（`WHERE stock >= ?`，行数判定），`version` 仅作库存变更自增审计（[ADR-020](../adr/020-sku-stock-atomic-sql-deduction.md)）；`user`：`version` 防并发覆盖（V3.2） |
| **字符集** | `utf8mb4` + `utf8mb4_unicode_ci` |
| **引擎** | InnoDB |
| **JSON 扩展** | 使用 `JSON` 列类型（`expand_json`）预留灵活性 |

> **数据库目录与装库语义（2026-10 整治；2026-10-15 fix-doc 更正）**：脚本目录 `Implementation/back-end/database/`。**`Initialize.sql` 是唯一结构真相源（表数以 `node Implementation/back-end/scripts/sync-doc-counts.mjs --list-tables` 为准，一次装库到位）**。
>
> ⚠️ **迁移机制已废除（[ADR-045](../adr/045-db-snapshot-single-source.md)，2026-10-05）**：原 `database/migrations/` 目录（曾含约 30 个 `V*.sql` 版本化文件、V1.1.0 → V5.6）**已整体删除，该目录现已不存在**（库无存量数据，无需升级路径）。**此后任何结构变更（新表 / 新列 / 约束 / 种子）一律直接修改 `Initialize.sql`**——不得再新增迁移文件、不得再引用 `database/migrations/V*.sql`。RBAC 种子与基线一致且幂等：`ROLE_ADMIN` 持有 `*:*:*` 超管权限。

### 主键决策矩阵

**判断原点：业务是否需要看到和使用这个标识。**

```
业务需要看到？
  ├── 是 → 需要可读/可排序/有规则？
  │         ├── 是（客服报单号、支付对账） → 独立业务流水号（xxx_no）
  │         └── 否（仅需唯一引用，如分享链接） → 业务唯一标识（xxx_id，雪花/UUID）
  └── 否 → 仅技术主键 id
```

| 场景 | 主键方案 | 冰美商城示例 |
|------|---------|-------------|
| **业务需要看到 + 可读可排序** | `id` (技术PK) + `xxx_no` (独立流水号) | `orders.order_no`、`pay_order.pay_order_no` |
| **业务需要看到 + 仅需唯一引用** | `id` (技术PK) + `xxx_id` (雪花/短UUID) | `user.user_id`、`spu.spu_id`、`sku.sku_id` |
| **业务不需要看到**（子实体/关联表/内部表） | 仅 `id` (技术PK) | `order_item`、`cart`、`category`、`address`、`seller`、`order_logistics` |
| **现代轻量项目，无客服/对账场景** | 雪花ID直出，不额外维护业务编号 | （本项目不适用，保留 `order_no`） |

> **引用分层规则（2026-10-14 补注）**：①**服务内 / 同库强关联** → 技术主键 `id`（含 FK/JOIN；一服务一库与分片后仍永久正确，性能最优）；
> ②**跨服务持久化引用 / 对外契约** → 全局稳定键（业务标识 `xxx_no`/`xxx_id`）——技术主键只在「双方永远同库同分片」前提下成立，且不受契约保护；现存跨域技术键一律登记为分表前收敛项（见「分片影响面清单」）。

### 技术主键 `id` 生成策略

| 策略 | MyBatis-Plus 注解 | 适用场景 |
|------|------------------|---------|
| `AUTO_INCREMENT` | `@TableId(type = IdType.AUTO)` | 单库单表，ID 由 DB 生成，简单可靠 |
| `ASSIGN_ID`（雪花） | `@TableId(type = IdType.ASSIGN_ID)` | 分布式/分库分表，ID 由应用生成，避免 DB 自增冲突 |

冰美商城当前使用 `AUTO_INCREMENT`，分库分表前切换为 `ASSIGN_ID`。

**分库分表路线**：当前单库单表满足 MVP 需求。当单表数据量超过 500 万行时，引入 **Apache ShardingSphere** 做水平分片，同时将主键策略切换为 `ASSIGN_ID`（雪花算法），避免 DB 自增冲突。

**分片影响面清单（2026-10-10 CR 后新增，分片工程按此逐项处理）**：

| 影响面 | 现状 | 分片后变化 | 处理 |
|---|---|---|---|
| 技术主键 | `AUTO_INCREMENT` | 跨片自增冲突 | `IdType.AUTO → ASSIGN_ID`，列/字段零改动 |
| 业务标识 | `BINARY(16)` + UUIDv7，全局 UNIQUE | UNIQUE 退化为**分片内唯一** | 生成端不碰撞（v7 74 位随机）+ 单号路由（基因位/映射表，见主键决策矩阵） |
| 跨库 JOIN | 库内 JOIN 可用 | **全灭** | 组装上移应用层；逐条排查存量 JOIN |
| 跨服务读库 | search-service 直读 `spu`/`sku`（LIKE 降级） | 直读失效 | **硬红线**：分片启动前必须退出，路线见 [ADR-052](../adr/052-search-db-fallback-exemption.md) |
| 跨表唯一约束 | `uk_order_active(order_no, active_flag)`、`uk_order_package` 等 | 仅分片内生效 | 校验/幂等上移应用层或收敛到分片键内 |
| 对账/报表 | 多表 JOIN 查询 | 失效 | 收敛到报表库/ES（pay 对账链路已单表化） |
| ES/MQ 同步 | 全量重建 + 事件驱动 | 同步载荷需携带分片键 | 现有 Feign/MQ 载荷均为 String ID，天然可扩展 |
| 跨服务技术键引用（收敛项） | `order_logistics.order_id`（logistics→trade：技术 id + 物理 FK） | 一服务一库/分片后 FK 与本地 join 失效、技术 id 不受契约保护 | **分表前必须收敛**：改 `order_no BINARY(16)` 业务键——范式见已收敛的 `review`（2026-10-14，含契约缺口修复） |
| 跨服务物理 FK（共享单库期产物） | `fk_orderitem_sku`、`fk_order_user`、`fk_cart_*`、`fk_logistics_order`、`fk_seller_user`、`fk_user_auth_user`、`fk_review_like_user`、`fk_view_log_*` 等 | 一服务一库后全部不可建 | 完整性上移应用层校验（现状已主要依赖应用层）；拆库时随表迁移逐一移除 |
| 平台级 `user_id` 口径（ADR-006） | 全平台以 `user.id`（BIGINT）跨服务引用（JWT claim / 各业务表 user_id） | **可持续**——前提：已分配 `user.id` 永久不变（大厂「用户 ID 永久身份」承诺）；生成策略切雪花仅影响新增 id | 已批准口径；此前提为长期红线（禁止重排/重建已分配 user.id） |

### 数据同步策略（计划 V1.2）

当 ElasticSearch 上线后，需要保证 MySQL 与 ES 的数据一致性：

| 同步路径 | 方案 | 触发条件 |
|---------|------|---------|
| MySQL → ElasticSearch | **Canal** (binlog 订阅) | 商品上架/下架/信息变更时，近实时同步至 ES 索引 |
| MySQL → Redis | Canal → 刷新缓存 | 商品价格/库存变更时，失效 Redis 缓存 |
| 定时全量同步 | XXL-Job 日间低频 | 兜底：每天凌晨 3 点全量重建 ES 索引 |

### 标识使用原则

| 场景 | 使用 | 原因 |
|------|------|------|
| 数据库关联（外键） | 技术主键 `id` | BIGINT 索引性能最优，无业务耦合 |
| 对外暴露（API URL/响应） | 业务标识 `xxx_id` / `xxx_no` | 不暴露数据规模，语义明确 |
| 订单/支付号 | 独立业务流水号 `xxx_no` | 客服可读、对账可追溯、支持日期前缀等格式规则 |
| 内部调用（Feign） | 技术主键 `id` 或业务标识均可 | 视性能需求选择，id 更快 |

### 业务标识存储规范（ADR-050，2026-10-08 定稿）

> 完整决策、22 转 / 10 保留列清单与理由见 [ADR-050](../adr/050-business-id-binary16-uuidv7.md)。双 ID 架构（ADR-002）不变，仅存储形态升级。

| 层 | 形态 | 规则 |
|---|---|---|
| 数据库 | `BINARY(16)` 原始字节 | 业务标识列默认类型；**严禁** `UUID_TO_BIN(x, true)` swap（v7 时间已在高字节，swap 破坏时间序） |
| PO（infrastructure.persistence） | `byte[]` | MyBatis 默认 ByteArrayTypeHandler 原生支持；**禁止**自定义 TypeHandler / String PO（包装器参数绕过 typeHandler → 静默查空） |
| domain / app / adapter / Feign / VO | `String`（32 位小写 hex） | 对外契约不变；转换只发生在仓储 toPO/toDomain |

- **生成**：全项目唯一出口 `IdGenerator.nextId()`（UUIDv7，趋势递增）；凭据（refreshToken/jti）用 `IdGenerator.secureToken()`（256 位熵）；禁止散落 `UUID.randomUUID()` / `IdUtil.fastSimpleUUID()`；
- **查询**：仓储等值查询参数必须 `IdGenerator.toBytes(...)`；**外部输入**（URL path / 渠道回调单号）用 `toBytesOrNull(...)`（解析失败落空 = 未命中，不放大成 500）；
- **保留列**（不转 BINARY）：管理端输入分组（stack_group）、复合会话 ID（chat_message.conversation_id）、枚举/语义编码（notification.type/identity_type/task code）、复合/外部格式（biz_ref/biz_no/channel_*/embedding_id）；
- **运维 SQL**：需 `HEX(col)` / `UNHEX('...')` 包裹业务标识列。

### 现状核对快照（2026-10-14 全库审计时点）

> 证据快照（防「漏改 / 过度设计」误读）。分诊口径 = 该表是否含业务标识列（`BINARY(16)` 或登记保留列）。
> **数量列为 2026-10-14 审计时点值**（U3 删表后不回算，下面的枚举已剔除已删表）；**当前全库表数以 `node Implementation/back-end/scripts/sync-doc-counts.mjs --list-tables` 为准**，本文不写死。

| 类别 | 数量 | 说明与典型表 |
|---|---|---|
| **真双 ID**（技术键 + `BINARY(16)` 业务键） | 23 张（时点） | 资金/凭证/订单链/身份/资源类：user、spu、sku、freight_template、orders、pay_order、user_coupon、after_sale、settlement、withdrawal、chat_message（message_id）、refund_order、wallet_transaction、reconciliation_detail、member_growth_log、group_team、group_team_member、invoice、coupon、review（订单引用业务键，2026-10-14 收敛） |
| **特殊保留** | 1 张 | chat_message.conversation_id（复合格式，内嵌技术 id，不可二进制化） |
| **仅技术主键**（零业务标识，刻意为之） | 38 张 | 子实体/关联/内部/台账：order_item、cart、address、category、**note**、**note_like**、**note_spu**、**shop_decoration**、**task_def**、**task_record**、sys_*、operation_log、points_log、promotion、flash_sale、shop、home_config、currency、exchange_rate、reconciliation_batch 等（表级条目见 §3/§七） |

> **灰区口径**：shop / flash_sale / promotion / note / group_buy_activity 等对 C 端可见但**技术 ID 直出**属有意识的务实豁免（大厂同类：类目/活动 ID 数字直出）；分库分表切换 `ASSIGN_ID` 时自动升级为雪花直出（稀疏空间，防密集枚举）；若单表先出现爬取/规模泄露顾虑，**补业务键为纯加法演进**（不破坏既有结构，不单独上雪花工程）。

---

## 二、表清单与关系概览

```
user ───1:N─── address
  │
  └──1:1─── seller（商家身份 + 生命周期）
              │
              ├──1:N─── shop（店铺档案，V5.4 拆分，ADR-019）
              │
              └──1:N─── spu ───1:N─── sku
                           │                  │
                           ├──0:N─── freight_template（运费模板，V5.6 已建，spu.freight_template_id 可空回落商家默认）
                           │                  │
       ┌───────────────────┘                  │
       │                                      │
       ▼                                      ▼
    orders ───1:N─── order_item ───N:1─── (sku 快照)
       │
       ├──1:N─── order_logistics（V5.6 已建：1:1 → 1:N 拆单发货，按 package_no 区分包裹）
       │
       ├──1:1─── pay_order (通过 order_no 关联)
       │            │
       │            └──1:N─── refund_order（退款单，V5.5 已建；经 after_sale_id 关联售后单，1 售后单 : 1 退款单）
       │
       └──(对账，无外键) reconciliation_batch/detail（V5.5 已建，按 `channel_trade_no`（对外渠道号）比对，ADR-063）
       
cart ───N:1─── user
cart ───N:1─── sku

user ───1:N─── user_auth (authorization-service 认证凭证聚合, V5.1；V5.2 起凭证唯一所有权归 auth，ADR-017)
user ───1:1─── wallet_account (wallet-service 资金域, V5.3；user.balance 列冻结)
  │
  └──1:N─── wallet_transaction (钱包流水，V5.5 已建；幂等键 transaction_no)
user ───1:N─── notification (notification-service 触达域, V5.0；2026-10 所有权迁入)
user ───1:1─── seller (merchant-service 商家域, 2026-10 第六轮所有权迁入；V5.4 起仅身份+生命周期)
```

---

## 三、表详细定义

### 3.1 user（用户表）

> **V5.2 资料/凭证分离 + PII 加密**（[ADR-017](../adr/017-user-domain-split-and-auth-boundary.md)）：user 表为纯资料表；密码凭证/微信身份的唯一权威是 `user_auth`（§3.12），角色唯一权威是 RBAC 五表（§八）。

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | BIGINT | PK, AUTO_INCREMENT | 技术主键 |
| user_id | BINARY(16) | UNIQUE, NOT NULL | 业务唯一标识（UUIDv7 原始字节，ADR-050） |
| username | VARCHAR(50) | NULL | 昵称（V5.2 术语统一：原"用户名"语义降级为展示字段，可重复；列名保留历史兼容） |
| phone | VARCHAR(20) | NULL | ⚠️ **deprecated V5.2**：明文手机号退役（迁移器搬数后置 NULL），权威数据在 phone_cipher/phone_hash/phone_mask |
| phone_cipher | VARCHAR(512) | NULL | 手机号密文（AES-256-GCM，Base64，V5.2 新增） |
| phone_hash | CHAR(64) | UNIQUE, NULL | 手机号盲索引（HMAC-SHA256，等值查询/唯一性校验，V2.5 建列） |
| phone_mask | VARCHAR(64) | NULL | 手机号脱敏展示值（如 `138****5678`，V5.2 新增） |
| email_cipher | VARCHAR(512) | NULL | 邮箱密文（AES-256-GCM，V5.2 新增；邮箱身份登录归 user_auth.EMAIL 行） |
| email_hash | VARCHAR(128) | UNIQUE, NULL | 邮箱盲索引（HMAC-SHA256，V5.2 新增） |
| email_mask | VARCHAR(64) | NULL | 邮箱脱敏展示值（V5.2 新增） |
| password | VARCHAR(100) | NULL | ⚠️ **deprecated V5.2**：密码凭证归 `user_auth.credential`（迁移器搬数后置 NULL），观察期后删列 |
| avatar | VARCHAR(255) | NULL | 头像 URL |
| gender | TINYINT | NOT NULL, DEFAULT 0 | 性别：0-未知，1-男，2-女（V5.2 新增） |
| birthday | DATE | NULL | 生日（V5.2 新增） |
| wx_openid | VARCHAR(128) | UNIQUE, NULL | ⚠️ **deprecated V5.2**：微信身份归 `user_auth`（WECHAT 行 `identifier_plain`），观察期后删列 |
| member_level | VARCHAR(20) | NOT NULL, DEFAULT 'NORMAL' | 会员等级：NORMAL/SILVER/GOLD/PLATINUM（V5.2 新增） |
| register_time | DATETIME | NOT NULL | 注册时间（审计列，metaObjectHandler 填充；领域语义由 create_time 承担） |
| status | TINYINT | NOT NULL, DEFAULT 1 | 1-正常，0-禁用 |
| balance | INT | NOT NULL, DEFAULT 0 | ⚠️ **deprecated（2026-10 边界重构）**：余额迁至 wallet-service `wallet_account`（V5.3 存量迁移）；列保留冻结，代码停止读写 |
| role_type | TINYINT | NOT NULL, DEFAULT 0 | ⚠️ **deprecated V5.2**：角色唯一权威为 RBAC 五表（sys_user_role），兼容字段由五表派生（RoleTypeDerivation），观察期后删列 |
| version | INT | NOT NULL, DEFAULT 0 | 乐观锁版本号（V3.2 @Version） |
| last_login_time | DATETIME | NULL | ⚠️ **deprecated V5.2**：登录链路已退出 user 上下文，停止写入，观察期后删列 |
| create_time | DATETIME | NOT NULL | 创建时间 |
| update_time | DATETIME | NOT NULL | 更新时间 |

索引：`uk_user_id`, `uk_phone_hash`（V2.5）, `uk_email_hash`（V5.2 新增）, `uk_wx_openid`；`uk_phone` 已随明文手机号退役删除（V5.2）。

> **V5.2 资料/凭证分离说明**：结构现状见 `Initialize.sql` 中 `user` 表的定义（**唯一结构真相源**；迁移机制已废除，ADR-045）。**⚠️ 历史注记**：原文此处引用结构变更脚本 `database/migrations/V5.2__user_profile_split_and_encryption.sql` —— 该文件与 `database/migrations/` 目录**均已不存在**，不得再引用。本节记录的 V5.2 形态（已含 `uk_phone_hash` / `uk_email_hash` 唯一索引，明文列仅为带 deprecated 注释的保留列）现直接体现在 `Initialize.sql`。该次变更**只做结构变更**：①新增资料列 gender / birthday / member_level；②PII 三字段激活（phone_cipher/phone_mask 补齐，email 三列新增）；③唯一索引 `uk_phone` → `uk_phone_hash` + `uk_email_hash`（盲索引唯一，明文列退役）；④旧列（password / wx_openid / role_type / 明文 phone / last_login_time）注释标 **deprecated**，代码停止读写，观察期后另行 DDL 删列。**数据搬迁由 authorization-service 存量迁移器执行**（`LegacyCredentialMigrationRunner`，开关 `auth.legacy-migration.enabled` 默认 false）：①user.password → `user_auth.credential`（PHONE 行）后置 NULL；②user.phone 明文 → 三列后置 NULL；③user.wx_openid → `user_auth` WECHAT 行（identifier_plain，本轮不清理列）；幂等可重跑、逐条独立事务、失败 WARN 不中断；前置条件为 PII 密钥已装载（`CryptoKeyConfig`）。

### 3.2 address（收货地址表）

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | BIGINT | PK | 技术主键 |
| user_id | BIGINT | FK → user.id, NOT NULL | 所属用户 |
| receiver | VARCHAR(50) | NOT NULL | 收货人 |
| phone | VARCHAR(20) | NOT NULL | 联系电话 |
| province | VARCHAR(50) | NOT NULL | 省 |
| city | VARCHAR(50) | NOT NULL | 市 |
| district | VARCHAR(50) | NOT NULL | 区/县 |
| street | VARCHAR(100) | NOT NULL | 街道/镇 |
| detail | VARCHAR(200) | NOT NULL | 详细地址 |
| is_default | TINYINT | DEFAULT 0 | 1-默认 |
| label | VARCHAR(20) | NULL | 标签（家/公司） |
| longitude | DECIMAL(10,7) | NULL | 经度（可选） |
| latitude | DECIMAL(10,7) | NULL | 纬度（可选） |
| create_time | DATETIME | NOT NULL | |
| update_time | DATETIME | NOT NULL | |

外键：`fk_address_user` → user.id ON DELETE CASCADE

### 3.3 seller（商家表）与 shop（店铺表）

> **V5.4 拆分（[ADR-019](../adr/019-merchant-shop-1n-split.md)）**：`seller` 瘦身为「商家身份 + 生命周期」，7 个店铺资料列整体迁至新表 `shop`（1 商家 : N 店铺）；表所有权归 merchant-service（商家域）。领域类 `Merchant` / `Shop`（见 03-Domain-Model §3.3/§3.3.1）。

**`seller`（商家身份表，V5.4 瘦身后）**

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | BIGINT | PK | 技术主键 |
| user_id | BIGINT | FK → user.id, UNIQUE (`uk_user_id`), NOT NULL | 关联用户（1:1，一个用户至多一个商家） |
| status | TINYINT | NOT NULL, DEFAULT 0 | 0-审核中，1-正常，2-冻结 |
| create_time | DATETIME | NOT NULL | 创建时间 |
| update_time | DATETIME | NOT NULL | 更新时间 |

外键：`fk_seller_user` → user.id ON DELETE RESTRICT

**`shop`（店铺表，V5.4 新增，12 列）**

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | BIGINT | PK, AUTO_INCREMENT | 技术主键（`shopId` 口径） |
| merchant_id | BIGINT | FK → seller.id (`fk_shop_merchant`), NOT NULL | 所属商家 |
| shop_name | VARCHAR(100) | NOT NULL | 店铺名称 |
| shop_logo | VARCHAR(255) | NULL | 店铺 logo |
| contact_phone | VARCHAR(20) | NOT NULL | 联系电话 |
| province | VARCHAR(50) | NOT NULL | 发货省 |
| city | VARCHAR(50) | NOT NULL | 发货市 |
| district | VARCHAR(50) | NOT NULL | 发货区/县 |
| detail_address | VARCHAR(200) | NOT NULL | 详细地址 |
| status | TINYINT | NOT NULL, DEFAULT 0 | 0-审核中，1-正常，2-冻结；公开可见 = 1 |
| create_time | DATETIME | NOT NULL | 创建时间 |
| update_time | DATETIME | NOT NULL | 更新时间 |

索引：`uk_merchant_shop_name(merchant_id, shop_name)`（业务唯一键）、`idx_merchant_status(merchant_id, status)`

> **默认店铺口径**：遗留 `sellerId`（= 商家 `user_id`）经「该商家 `shop.id` 最小者」解析为默认店铺（应用层 `ShopManager` 唯一解析点，Phase A 兼容期）；存量迁移时每个商家生成一个店铺。下游表（spu/orders/coupon/settlement/merchant_knowledge_base）的 `seller_id` 在 Phase B 前维持 `user_id` 口径不变（`fk_spu_seller` 等外键不受影响）。

### 3.4 category（类目表）

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | BIGINT | PK | 技术主键 |
| name | VARCHAR(50) | NOT NULL | 类目名称 |
| parent_id | BIGINT | FK → category.id, NULL | 父类目（NULL=顶级） |
| level | TINYINT | NOT NULL | 层级：1/2/3 |
| sort_order | INT | DEFAULT 0 | 排序序号 |
| create_time | DATETIME | NOT NULL | |
| update_time | DATETIME | NOT NULL | |

外键：`fk_category_parent` → category.id ON DELETE SET NULL

### 3.5 spu（商品表 / SPU）

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | BIGINT | PK | 技术主键 |
| spu_id | BINARY(16) | UNIQUE, NOT NULL | 业务商品编号（UUIDv7 原始字节，ADR-050） |
| short_code | VARCHAR(16) | UNIQUE, NOT NULL（✅ **已建库（B7b）** —— B7a 契约写定、B7b 落 `Initialize.sql`） | SPU **短码**（承载层别名，8–10 位小写 `[0-9a-f]`，取值 10 位 hex = 40 bit 熵，`IdGenerator.randomHex(10)`；**不是实体标识** —— 不得用于任何业务判断、不得替代 `spu_id`、不得进任何 VO）。唯一用途：写入小程序码 `scene`（`p=<短码>`）并由 **P38** `GET /api/item/spu/by-code/{code}` 换回 SPU。生命周期：不过期、**永不复用**（软删保留占位）。细则见 [ADR-063](../adr/063-external-channel-number-separation.md) §决策 8、[05 §1.9⑧](../contract/05-API-Specification.md) |
| seller_id | BIGINT | FK → seller.id, NOT NULL | 所属商家 |
| category_id | BIGINT | FK → category.id, NOT NULL | 所属类目 |
| name | VARCHAR(200) | NOT NULL | 商品名称 |
| main_image | VARCHAR(255) | NULL | 主图 URL |
| images | VARCHAR(2000) | NULL | 多图URL列表（JSON 数组**文本**，V3.5；非 JSON 列类型） |
| video_url | VARCHAR(500) | NULL | 视频URL (V3.5) |
| attributes | JSON | NULL | 规格参数K-V (V3.5) |
| description | TEXT | NULL | 图文描述（HTML） |
| brand | VARCHAR(100) | NULL | 品牌 |
| sold_count | INT | DEFAULT 0 | 总销量 |
| view_count | INT | DEFAULT 0 | 浏览次数 (V3.5) |
| comment_count | INT | DEFAULT 0 | 总评论数 |
| is_ad | TINYINT | DEFAULT 0 | 广告商品标记 |
| service_tags | VARCHAR(500) | NULL | 售后标签（JSON 数组**文本**，V3.5；非 JSON 列类型） |
| sales_tags | JSON | NULL | 商品级销售标签 (V4.0) |
| status | TINYINT | DEFAULT 1 | 1-上架，2-下架，3-删除 |
| freight_template_id | BIGINT | FK → freight_template.id, NULL（**V5.6 已建**） | 绑定运费模板；NULL → 用商家默认模板（该商家**最早创建的启用模板**）；模板被删除时经 `ON DELETE SET NULL` 自动回落。见 §十二 12.2 |
| publish_time | DATETIME | NULL | 发布时间 |
| create_time | DATETIME | NOT NULL | |
| update_time | DATETIME | NOT NULL | |

索引：`uk_spu_id`, `idx_seller_id`, `idx_category_id`, `idx_status`, `idx_seller_status`, **`uk_short_code`（✅ B7b 已建）**

外键：`fk_spu_seller` → seller.id ON DELETE RESTRICT，`fk_spu_category` → category.id ON DELETE RESTRICT

> **✅ 已落库（B7b，`265543d6`）**：`spu.short_code` 为 **B7a 写定、B7b 落库** 的新增列（[ADR-045](045-db-snapshot-single-source.md)：**直接改 `Initialize.sql` 快照，无迁移脚本、无回填** —— 库无存量数据，`NOT NULL` 可立即生效）。
> **为何是加列而非独立映射表**：1 SPU : 1 短码且同生同灭；`NOT NULL` 强制「生成只能发生在商品创建这一处」（避免懒生成导致同一商品在不同请求拿到不同短码）；落地页热路径零 join；
> 短码是 **SPU 自身对外别名**（不可变、唯一、无独立生命周期）⇒ 属聚合内属性，不侵 `spu` 聚合职责。理由与残余风险（R7：一 SPU 一短码不支持按渠道分发/召回）见 [ADR-063](../adr/063-external-channel-number-separation.md) §决策 8。
> ⚠ **排序规则注意**：`spu` 表是 `utf8mb4_unicode_ci`（**大小写不敏感**），当前短码限定小写 hex 故无冲突；若将来改 base62，**必须**同批把 `short_code` 列改 `_bin`/`_as_cs`，否则 `AbC` 与 `abc` 会被唯一键判等（静默冲突）。

### 3.6 sku（规格表 / SKU）

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | BIGINT | PK | 技术主键 |
| sku_id | BINARY(16) | UNIQUE, NOT NULL | 业务 SKU 编码（UUIDv7 原始字节，ADR-050） |
| spu_id | BIGINT | FK → spu.id, NOT NULL | 所属商品 |
| spec | VARCHAR(200) | NOT NULL | 规格描述 |
| price | INT | NOT NULL | 价格（分） |
| original_price | INT | NULL | 原价（分）(V3.5) |
| stock | INT | NOT NULL, DEFAULT 0 | 库存数量 |
| image | VARCHAR(255) | NULL | 规格专属图片 |
| sold_count | INT | DEFAULT 0 | 该规格销量 |
| stock_type | TINYINT | DEFAULT 1 | 库存类型: 1=LIMITED限量, 2=UNLIMITED不限量, 3=PRESALE预售 (V4.0) |
| is_hot | TINYINT | DEFAULT 0 | 热销标识: 0=否, 1=是 (V4.0) |
| hot_reason | VARCHAR(50) | NULL | 热销原因: discount/new_arrival/best_seller/clearance (V4.0) |
| sales_tags | JSON | NULL | 销售标签JSON: ["限时优惠","新品"] (V4.0) |
| status | TINYINT | DEFAULT 1 | 1-可售，0-停售 |
| version | INT | NOT NULL, DEFAULT 0 | 版本列——**库存变更自增审计**；并发控制由原子 SQL（`WHERE stock >= ?`）保证，不注册 `@Version` 拦截器语义（[ADR-020](../adr/020-sku-stock-atomic-sql-deduction.md)） |
| create_time | DATETIME | NOT NULL | |
| update_time | DATETIME | NOT NULL | |

外键：`fk_sku_spu` → spu.id ON DELETE CASCADE

### 3.7 orders（订单表）

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | BIGINT | PK | 技术主键 |
| order_no | BINARY(16) | UNIQUE, NOT NULL | 订单号（UUIDv7 原始字节，ADR-050） |
| user_id | BIGINT | FK → user.id, NOT NULL | 买家 |
| seller_id | BIGINT | FK → seller.id, NOT NULL | 商家 |
| order_type | TINYINT | NOT NULL, DEFAULT 1 | 订单类型: 1=NORMAL购物车, 2=DIRECT立即购买, 3=FLASH_SALE秒杀, 4=PRESALE预售 (V4.1) |
| total_amount | INT | NOT NULL | 总金额（分）＝ Σ order_item.subTotal（不含运费） |
| pay_amount | INT | NOT NULL | 实付金额（分）。**不变式 V2（已生效，[ADR-037](../adr/037-freight-model-package-split-and-refund-trigger.md)）**：`payAmount = totalAmount − discountAmount + postFee`（旧口径无 `postFee` 项，见 §十二 12.2） |
| discount_amount | INT | NOT NULL, DEFAULT 0 | 优惠金额（分） |
| post_fee | INT | NOT NULL, DEFAULT 0（**V5.6 已建**） | 运费（分）；下单时按 §十二 12.2 计价规则计算并快照，Phase 1 同单同商家、取商品绑定模板中 fee 最高者计一次 |
| remark | VARCHAR(200) | NULL（**V5.7 已建**） | 买家留言快照（下单时随单快照，Phase 1 不提供修改端点） |
| status | TINYINT | NOT NULL | 1-待付款，2-待发货，3-待收货，4-已完成，5-已取消，6-待评价 |
| payment_type | TINYINT | NULL | 1-支付宝，2-微信，3-余额 |
| receiver_name | VARCHAR(50) | NOT NULL | 收货人快照 |
| receiver_phone | VARCHAR(20) | NOT NULL | 电话快照 |
| receiver_address | VARCHAR(500) | NOT NULL | 地址快照（完整拼接） |
| create_time | DATETIME | NOT NULL | 下单时间 |
| pay_time | DATETIME | NULL | 支付时间 |
| consign_time | DATETIME | NULL | 发货时间 |
| end_time | DATETIME | NULL | 完成时间 |
| close_time | DATETIME | NULL | 关闭时间 |
| comment_time | DATETIME | NULL | 评价时间 |
| version | INT | NOT NULL, DEFAULT 0 | 乐观锁版本号 |
| update_time | DATETIME | NOT NULL | |

索引：`uk_order_no`, `idx_user_id`, `idx_seller_id`, `idx_status`, `idx_user_status`

### 3.8 order_item（订单项表）

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | BIGINT | PK | 技术主键 |
| order_id | BIGINT | FK → orders.id, NOT NULL | 所属订单 |
| sku_id | BIGINT | FK → sku.id, NOT NULL | SKU ID |
| product_name | VARCHAR(200) | NOT NULL | 商品名快照 |
| sku_spec | VARCHAR(200) | NOT NULL | 规格快照 |
| price | INT | NOT NULL | 单价快照（分） |
| quantity | INT | NOT NULL | 数量 |
| sub_total | INT | NOT NULL | 小计（分） |
| package_no | INT | NULL（**V5.6 已建**） | 包裹号——发货时标记本明细归属包裹（对应 `order_logistics.package_no`）；NULL=尚未发货；一明细只发一次（写入后不可重复发货） |
| image | VARCHAR(255) | NULL | 图片快照 |
| create_time | DATETIME | NOT NULL | |
| update_time | DATETIME | NOT NULL | |

外键：`fk_orderitem_order` → orders.id ON DELETE CASCADE（组合关系）

### 3.9 cart（购物车表）

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | BIGINT | PK | 技术主键 |
| user_id | BIGINT | FK → user.id, NOT NULL | 用户 |
| sku_id | BIGINT | FK → sku.id, NOT NULL | SKU |
| quantity | INT | DEFAULT 1 | 数量 |
| selected | TINYINT | DEFAULT 1 | 1-选中，0-未选中 |
| create_time | DATETIME | NOT NULL | 加入时间 |
| update_time | DATETIME | NOT NULL | |

唯一约束：`uk_user_sku` (user_id, sku_id) — 同一用户同一 SKU 只有一条记录

### 3.10 pay_order（支付订单表）

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | BIGINT | PK | 技术主键 |
| biz_order_no | BINARY(16) | NOT NULL | 业务订单号（关联 orders.order_no，UUIDv7 原始字节，ADR-050） |
| pay_order_no | BINARY(16) | UNIQUE, NOT NULL | 支付单号（UUIDv7 原始字节，ADR-050；**纯内部口径，不下发任何渠道**） |
| channel_trade_no | VARCHAR(32) | UNIQUE (`uk_channel_trade_no`), NULL | **对外渠道号**（渠道 `out_trade_no` 的唯一取值来源，[ADR-063](../adr/063-external-channel-number-separation.md)）：20 位随机 hex（80 bit 熵、无时间序、`IdGenerator.randomHex`）、**跨重试稳定**（复用待支付单不重新生成）、**余额渠道为 NULL**（本地闭环不对外发起）；**发起 / 回调 / 对账一律按本列寻址**，不得等于 `biz_order_no` / `pay_order_no` |
| biz_user_id | BIGINT | FK → user.id, NULL | 用户 |
| pay_channel_code | VARCHAR(20) | NOT NULL | 支付渠道代码 |
| amount | INT | NOT NULL | 支付金额（分） |
| pay_type | TINYINT | NOT NULL | 1-h5, 2-公众号(JSAPI), 3-小程序(MINI), 4-扫码(NATIVE), 5-余额（V5.18 与 PayTypeEnum 对齐修正） |
| status | TINYINT | NOT NULL | 0-待提交, 1-待支付, 2-超时取消, 3-成功 |
| expand_json | JSON | NULL | 扩展字段 |
| result_code | VARCHAR(50) | NULL | 第三方返回码 |
| result_msg | VARCHAR(200) | NULL | 第三方返回信息 |
| pay_success_time | DATETIME | NULL | 支付成功时间 |
| pay_over_time | DATETIME | NULL | 支付超时时间 |
| qr_code_url | VARCHAR(255) | NULL | 二维码链接 |
| create_time | DATETIME | NOT NULL | |
| update_time | DATETIME | NOT NULL | |

> **幂等口径（2026-10 补注）**：支付成功处理走**条件更新**——`markSuccessIfPending(payOrderNo)` 仅在 `status=待支付` 时置成功（`UPDATE ... WHERE pay_order_no = ? AND status = ?`），条件未命中返回 false（重复回调 / 超时取消后的回调不重复处理，与 `pay_order_no` 唯一约束互为兜底）。
> **退款关联（V5.5 已建，[ADR-035](../adr/035-refund-fund-flow-and-refunded-activation.md)）**：本表同域子表 `refund_order`（退款单，`pay_order_no` 关联；三通道原路退回，`uk_after_sale` 一售后单一次退款幂等锚点）——见 §十二 12.1；同域对账两表 `reconciliation_batch` / `reconciliation_detail`（四态差异只记录不调账，§十二 12.4）。
> **渠道号唯一寻址（2026-10-18，[ADR-063](../adr/063-external-channel-number-separation.md)）**：**发起 / 回调 / 对账三条链路一律按 `channel_trade_no` 寻址**（`PayOrderRepository#getByChannelTradeNo`），**不保留「按 `biz_order_no`/`pay_order_no` 兜底查」的第二口径**——第二口径会让双键混用复发。条件更新的 `WHERE` 仍用内部键（`markSuccessIfPending(payOrderNo)`），那是**服务内并发控制**，与「对外寻址」是两件事。

### 3.11 order_logistics（订单物流表）

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | BIGINT | PK, AUTO_INCREMENT | 技术主键（V5.6 代理主键） |
| order_id | BIGINT | FK → orders.id, NOT NULL | 订单 ID（V5.6 起 **1:N 外键**，一单多包裹） |
| package_no | INT | NOT NULL, DEFAULT 1 | 包裹号（唯一约束 `uk_order_package(order_id, package_no)`，存量回填 =1） |
| logistics_number | VARCHAR(50) | NOT NULL | 物流单号 |
| logistics_company | VARCHAR(50) | NOT NULL | 物流公司 |
| contact | VARCHAR(50) | NOT NULL | 收件人快照 |
| mobile | VARCHAR(20) | NOT NULL | 电话快照 |
| province/city/district/street | VARCHAR | NOT NULL | 地址快照 |
| detail | VARCHAR(200) | NOT NULL | 详细地址快照 |
| status | INT | NOT NULL, DEFAULT 1 | 物流状态：1-待揽收(PENDING)，2-运输中(SHIPPED)，3-已签收(DELIVERED)，4-已退回(RETURNED)（V1.1.1） |
| create_time | DATETIME | NOT NULL | |
| update_time | DATETIME | NOT NULL | |

> **状态迁移约束（2026-10 补注）**：物流状态**单调迁移、不可回退**（PENDING→SHIPPED→DELIVERED↔RETURNED）；同态重放视为幂等空操作（成功返回，不重复落库）；应用层经 `LogisticsStatusEnum.canTransitTo` 校验后才更新。
> **V5.6 已建（拆单发货，[ADR-037](../adr/037-freight-model-package-split-and-refund-trigger.md)）**：原「主键复用 order_id」约定偏差已收敛——①新增代理主键 `id BIGINT AUTO_INCREMENT`，`order_id` 转为**普通外键**（**不建单列唯一键**——1:N 语义下单列唯一会禁止一单多包裹）；②新增 `package_no INT NOT NULL DEFAULT 1`，唯一约束 `uk_order_package(order_id, package_no)`，**存量数据回填 package_no=1**；③1:1 升级为 **1:N**（一订单多包裹，每包裹一条物流记录）。见 §十二 12.3。

### 3.12 user_auth（用户认证凭证表，V5.1）

> 归属 authorization-service 认证上下文聚合。每种登录方式（手机号/邮箱/用户名/微信）一条记录，通过 `user_id` 关联 user-service 的 `user` 表（聚合间只存 ID 引用）。**建表现状见 `Initialize.sql`（唯一结构真相源，ADR-045）——历史注记**：原文引用的 `database/migrations/V5.1__add_user_auth.sql` 与 `database/migrations/` 目录**均已不存在**，不得再引用。

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | BIGINT | PK, AUTO_INCREMENT | 技术主键（领域层不可见，仓储按业务键操作） |
| user_id | BIGINT | NOT NULL, FK → user.id | 关联用户技术主键（跨服务 user_id 语义，见 ADR-006） |
| identity_type | VARCHAR(32) | NOT NULL | 标识类型（保留列，枚举编码，ADR-050）：USERNAME/PHONE/EMAIL/WECHAT/WECHAT_MINI |
| identifier_plain | VARCHAR(128) | NULL | 非敏感标识明文（openid/用户名） |
| identifier_cipher | VARCHAR(512) | NULL | 敏感标识密文（AES-256-GCM，Base64 存储） |
| identifier_hash | VARCHAR(128) | NULL | 敏感标识盲索引（HMAC-SHA256，查询定位用） |
| identifier_mask | VARCHAR(64) | NULL | 敏感标识脱敏展示值（如 `138****5678`） |
| union_id | VARCHAR(64) | NULL | 微信 UnionID（跨应用打通） |
| credential | VARCHAR(255) | NULL | 登录凭证（BCrypt 密码哈希；验证码/微信登录为空） |
| status | TINYINT | NOT NULL, DEFAULT 1 | 0=已解绑, 1=有效, 2=已锁定 |
| last_login_time | DATETIME | NULL | 最后登录时间 |
| create_time | DATETIME | NOT NULL | 创建时间 |
| update_time | DATETIME | NOT NULL | 更新时间 |

索引：`uk_type_hash(identity_type, identifier_hash)`、`uk_type_plain(identity_type, identifier_plain)`、`idx_user_id`、`idx_union_id`、外键 `fk_user_auth_user`

**敏感数据三字段模型**：敏感标识（手机号/邮箱）密文 `identifier_cipher` + 盲索引 `identifier_hash` + 脱敏 `identifier_mask` 三列分离——密文可解密还原、盲索引支撑等值查询（不可逆）、脱敏值仅供展示。非敏感标识（openid/用户名）直接存 `identifier_plain`。两个唯一索引均含可为 NULL 的列，利用 MySQL 唯一索引多 NULL 特性：敏感类型只有 hash 入索引、非敏感类型只有 plain 入索引，同一 `(identity_type)` 下两类记录互不冲突，天然支撑幂等 upsert（`INSERT ... ON DUPLICATE KEY UPDATE`）。

**Repository 业务键约定**：领域仓储 `UserAuthRepository` 仅提供 `insert` / `updateByUserIdAndType` / `upsert` / `findByIdentity` / `findByIdentityHash` / `findFirstByUnionId` / `findByUserId(AndType)`，技术主键 `id` 不出持久化层；解绑通过 `status=0` 状态更新（软删除），不物理删除。

> **配套 Redis 契约键（[ADR-027](../adr/027-jwt-blacklist-writer-and-contract-key.md)）**：`auth:blacklist:{jti}` —— 登出 / 重置密码成功由 authorization-service 写入（值语义：**键存在即已吊销**；TTL = max(access token 剩余有效期, 1s)），网关 `JwtBlacklistFilter` **只读**（Redis 异常 fail-open）。该键为**网关依赖的跨服务契约键**（与 `user:info:` / `user:perms:` 同级，豁免「跨服务不得共享 Redis Key」约束，见 [09-constraints §四](../collaboration/09-constraints.md)）；非表结构，随本表所在认证上下文一并记录。

---

## 四、数据隔离规则 (DO/DTO/VO)

遵循三层对象隔离，禁止实体透传：

| 对象类型 | 使用范围 | 说明 |
|----------|---------|------|
| **DO** (Entity) | Mapper / Infra 层 | 数据库实体，仅数据访问层使用 |
| **DTO** | Controller ↔ Service; 服务间 Feign 调用 | 传输对象，接口入参/出参 |
| **VO** | Controller → 前端 | 视图对象，面向展示裁剪 |

```java
// 禁止：Controller 直接返回 DO
public UserEntity getUser(Long id) { ... }       // ❌

// 正确：Controller 返回 VO
public Result<UserVO> getUser(Long id) { ... }    // ✅

// 禁止：Service 直接操作数据库
public void createOrder(OrderDTO dto) {
    orderMapper.insert(...)  // ❌ Service 不应该直接调 Mapper
}

// 正确：Service 通过 Mapper 间接操作，且不暴露 DO
```

---

## 五、ER 图

完整 ER 图见：`drawio/IA_E-R.drawio`

核心关系要点：
- `user` ↔ `address`：一对多（CASCADE 删除）
- `user` ↔ `seller`：一对一（RESTRICT 删除）
- `seller` → `spu`：一对多
- `spu` → `sku`：一对多（CASCADE）
- `orders` → `order_item`：一对多组合（CASCADE）
- `orders` ↔ `pay_order`：通过 `order_no` 关联（非外键约束）
- `orders` ↔ `order_logistics`：一对多（V5.6 已建——按 `package_no` 区分包裹，代理主键 `id` + `uk_order_package`，见 §十二 12.3）
- `pay_order` → `refund_order`（V5.5 已建）：通过 `pay_order_no` 关联；`refund_order.after_sale_id` UNIQUE 且**可空**（一售后单一次退款，无售后场景如拼团为 NULL），幂等锚点自 ADR-049 起泛化为 `biz_ref` UNIQUE（`AS:{afterSaleId}` / `GB:{orderNo}`）；渠道侧寻址走 `out_refund_no`（ADR-063，见 §十二 12.1）
- `cart` → `user` + `sku`：多对一

---

## 七、V1.1+ 扩展表

### 7.1 V1.1 积分 & 评价 & 收藏

> 表所有权（2026-10 边界重构）：`points_log` 归**营销域 marketing-service**（积分账本；写入经 `/internal/points/add`，签到积分由 marketing 本地发放）。

**`points_log`** — 积分变动日志
| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK | |
| user_id | BIGINT | 用户ID |
| points | INT | 积分变动（正=获得，负=消耗） |
| type | TINYINT | 1=签到, 2=下单, 3=任务, 4=兑换, 5=过期 |
| source | VARCHAR(50) | 来源描述 |
| create_time | DATETIME | |

**`review`** — 商品评价
| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK | |
| user_id | BIGINT | |
| spu_id | BIGINT | |
| order_no | BINARY(16) | 关联订单号（orders.order_no，无外键），唯一约束 `uk_order_user`（ADR-050 三批收敛） |
| rating | TINYINT | 1-5 星 |
| content | TEXT | |
| create_time | DATETIME | |

**`favorite`** — 商品收藏
| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK | |
| user_id | BIGINT | 唯一约束 `uk_user_spu` |
| spu_id | BIGINT | |
| create_time | DATETIME | |

### 7.2 V1.2 秒杀 & 首页装修 & 操作日志 & 优惠券

**`flash_sale`** — 秒杀活动
| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK | |
| spu_id | BIGINT | |
| sku_id | BIGINT | |
| flash_price | INT | 秒杀价（分） |
| stock | INT | 秒杀库存 |
| sold_count | INT | 已售 |
| version | INT | 乐观锁 |
| start_time / end_time | DATETIME | |

**`home_config`** — 首页配置
| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK | |
| slot | VARCHAR(30) | 位置(banner/hot/new/sale) |
| type | TINYINT | 1=商品组, 2=广告图, 3=活动链接 |
| spu_ids | VARCHAR(500) | 商品ID CSV |
| image / link_url | VARCHAR(255) | |
| sort_order | INT | |

**`operation_log`** — 操作审计日志（user 域管理操作台账）

> **写入方恢复（2026-10，[ADR-032](../adr/032-operation-audit-dual-pipeline.md) 管道二）**：仅 **user 域管理操作**（用户启用/禁用、角色授予）经应用层 `OperationLogManager.record(userId, module, action, content)` **显式落库**（不经 AOP；`@OperationLog` 只写审计日志、不写本表）。`updateStatus` 状态变更与留痕同事务；`setUserRole` 审计独立事务（禁事务内调 Feign）。`GET /api/admin/log` 查询范围收窄为 user 域管理操作；全平台审计走 `AUDIT` JSON 日志管道（ELK 采集）——本表**不再是**全平台审计台账。

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK | |
| user_id | BIGINT | 操作者用户ID |
| username | VARCHAR(50) | 操作者用户名（当前 `UserContext`，取不到留空） |
| action | VARCHAR(50) | `MODULE:ACTION`（表无 module 列，模块限定词并入；常量 `OperationAuditConstants`） |
| target | VARCHAR(100) | **未被写入方使用**（历史行仍有值）；收敛（补 module 列或废弃 target）需 DDL，见 ADR-032 |
| detail | VARCHAR(500) | 动作 + 目标标识摘要（不含敏感值） |
| ip | VARCHAR(45) | 当前 `UserContext` 取值（取不到留空） |
| create_time | DATETIME | 列默认值 CURRENT_TIMESTAMP |

**`coupon`** — 优惠券模板（V1.1 迁移时加入，V1.2 添加 seller_id，V4.0 升级为五维模型）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK | |
| coupon_id | BINARY(16) UNIQUE | 业务标识（创建端归一：空→系统生成/提供须 hex，ADR-050 修订） |
| name | VARCHAR(100) | |
| type | TINYINT | **DEPRECATED V4.0**: 请使用 discountType。1=满减, 2=折扣（保留向后兼容） |
| value | INT | 满减金额(分) / 折扣百分比(85=8.5折) |
| min_amount | INT | 最低消费(分) |
| seller_id | BIGINT | NULL=平台券, 非NULL=店铺券 |
| total_qty / issued_qty | INT | |
| start_time / end_time | DATETIME | |
| **V4.0 五维模型** | | |
| discount_type | TINYINT | **V4.0**: 优惠计算类型: 1=FIXED满减, 2=PERCENTAGE折扣, 3=CASH_COUPON代金券 |
| coupon_category | TINYINT | **V4.0**: 券类别: 1=PLATFORM平台券, 2=SHOP店铺券, 3=FLASH_SALE秒杀券, 4=EXCLUSIVE独占券 |
| grant_type | TINYINT | **V4.0**: 获取方式: 1=FREE_CLAIM免费领, 2=PAID_PURCHASE付费购买, 3=INVITATION邀请, 4=AUTO_ISSUE自动发放 |
| stock_type | TINYINT | **V4.0**: 总量模型: 1=LIMITED限量, 2=UNLIMITED不限量 |
| grab_type | TINYINT | **V4.0**: 领取方式: 1=NORMAL普通, 2=NEED_GRAB需抢(Redis Lua), 3=PLATFORM_EXCLUSIVE平台独占 |
| price_in_cents | INT | **V4.0**: 付费券价格(分), grantType=PAID_PURCHASE时必填 |
| **V4.3 适用范围 / 叠加规则（✅ 已打通映射，2026-10-15 [ADR-055](../adr/055-coupon-scope-and-stack-rule-materialization.md)）** | | |
| scope_type | TINYINT NOT NULL DEFAULT 1 | **V4.3**：适用范围：1=ALL全场，2=CATEGORY品类，3=PRODUCT单品 |
| scope_values | JSON NULL | **V4.3**：适用范围的 ID 列表（`categoryIds` 或 `spuIds` 的 JSON 数组） |
| stack_rule | TINYINT NOT NULL DEFAULT 1 | **V4.3**：叠加规则：1=MUTUAL_EXCLUSIVE互斥，2=STACKABLE可叠加 |
| stack_group | VARCHAR(32) NULL | **V4.3**：叠加分组 ID，**同组**券可叠加使用（跨组是否可叠由 `stack_rule` 决定） |

> **✅ 四列映射已打通（2026-10-15，[ADR-055](../adr/055-coupon-scope-and-stack-rule-materialization.md)）——订正此前"四列未映射 / 计划"的描述**：`scope_type`/`scope_values`/`stack_rule`/`stack_group` 四列此前**已建但未被代码读写**（PO 无字段、写路径忽略、读路径不回吐），属"声明了却不生效"的静默忽略。ADR-055 已将四列**真正接入落库与回吐**：
>
> | 面 | 打通内容 |
> | :-- | :-- |
> | **落库** | `CouponPO` 新增四列映射；管理端/商家端创建券（AD24 / AD27）可写入适用范围与叠加规则 |
> | **回吐** | `CouponVO` 回吐 `scope_type`/`scope_values`/`stack_rule`/`stack_group`（契约 §4.10.1 示例已含四字段） |
> | **校验生效** | 核销侧 `POST /internal/coupon/use`（I22）新增可选 `spuIds`/`categoryIds`（逗号分隔）——用于 `scope_type/scope_values` 匹配；**缺省时若该券限定了品类/单品则拒绝核销**（不再静默放行） |
> | **叠加校验** | 补录 `GET /internal/coupon/batch`（契约编号 I69，源码自记 I46）供下单方一次往返校验多券叠加 |
>
> ⚠️ **订正记录**：ADR-038 曾有「下单侧不受影响」的推论，**该推论错误**（四列未映射意味着适用范围形同虚设）。ADR-055 已订正并闭环。

**`user_coupon`** — 用户优惠券
| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK | |
| user_id | BIGINT | |
| coupon_id | BIGINT | FK→coupon.id |
| status | TINYINT | 1=未使用, 2=已使用, 3=已过期 |
| used_order_no | BINARY(16) | orders.order_no 复制（UUIDv7 原始字节，ADR-050） |
| use_time | DATETIME | |

### 7.3 V2.0 售后

**`after_sale`** — 售后申请
| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK | |
| order_no | BINARY(16) | 唯一约束 `uk_order_user`（UUIDv7 原始字节，ADR-050） |
| user_id | BIGINT | |
| type | TINYINT | 1=退货退款, 2=仅退款 |
| reason | VARCHAR(500) | |
| status | TINYINT | **AfterSaleStatusEnum 六态**：1=待审核 → 2=同意 →（4=已退货）→ 5=已退款 → 6=已完成；3=拒绝（分支态） |
| refund_amount | INT | |
| logistics_number | VARCHAR(50) | 退货物流单号 |

> **V5.5/V5.6 已建（买家退款资金流，[ADR-035](../adr/035-refund-fund-flow-and-refunded-activation.md)/[ADR-037](../adr/037-freight-model-package-split-and-refund-trigger.md)）**：状态 5=已退款（REFUNDED）已激活——pay-service 退款成功后经 `PUT /internal/trade/after-sale/{id}/refunded` 条件回写（**来源 APPROVED(2) | RETURNED(4) → REFUNDED(5)**，0 行=幂等空操作）；退款处理中/失败时售后单停留 2=同意（APPROVED），失败单可重试。退款触发时机（ADR-037 纠正）：**仅退款（type=2）审核同意即触发；退货退款（type=1）在商家确认收到退货后触发**（`PUT /api/admin/after-sale/{id}/confirm-return`，APPROVED→RETURNED）。每张售后单至多一次退款（退款单 `uk_after_sale` 唯一约束兜底，天然幂等）。退款金额在申请时已校验 ≤ 订单实付（`payAmount`）。退款单见 §十二 12.1。

### 7.4 V2.1 财务结算

**`settlement`** — 结算单
| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK | |
| settlement_no | BINARY(16) UNIQUE | UUIDv7 原始字节（ADR-050） |
| seller_id | BIGINT | |
| period_start / period_end | DATE | 结算周期 |
| total_amount / commission / settlement_amount | INT | 订单总额/佣金(5%)/结算额（分） |
| status | TINYINT | 1=待结算, 2=已结算, 3=已打款 |

**`withdrawal`** — 提现申请
| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK | |
| withdrawal_no | BINARY(16) UNIQUE | UUIDv7 原始字节（ADR-050） |
| seller_id | BIGINT | |
| amount | INT | 提现金额（分） |
| status | TINYINT | 1=待审核, 2=已打款, 3=拒绝 |
| bank_account / bank_name | VARCHAR(50)/(100) | |

> **归属说明（2026-10 增量核对）**：`withdrawal` 归 **trade-service 结算域，暂不迁 wallet-service**——提现单与结算单（`settlement`）属同一聚合族的级联：提现审核通过（`PAID_OUT`）会将卖家结算单级联置为已打款（`WithdrawalServiceImpl.review` → `SettlementService.markSellerSettlementsPaidOut`）；与 C 端用户余额（`wallet_account`，wallet-service）是不同业务。此前「提现是钱包域迁移候选」的判断作废。

### 7.5 V5.0 站内信（`notification`，notification-service 所有）

> 表名不变；2026-10 边界重构后所有权归 notification-service（触达域）。公开读写 `/api/notifications/**`；内部投递 `/internal/notify/in-app`（self-send 忽略，`sender_name` 由调用方传入、不再反查档案）。

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK | |
| recipient_id | BIGINT | 接收者用户ID |
| sender_id | BIGINT NULL | 发送者用户ID（NULL=系统） |
| sender_name | VARCHAR(100) | 发送者名称（用户名或店铺名，由调用方传入） |
| sender_avatar | VARCHAR(255) | 发送者头像 |
| type | VARCHAR(32) | 通知类型（保留列，枚举编码，ADR-050）：LIKE / REPLY / APPEND / FOLLOW / ORDER / SYSTEM |
| title | VARCHAR(200) | 通知标题 |
| content | VARCHAR(500) | 通知摘要 |
| link_url | VARCHAR(500) | 跳转链接 |
| is_read | TINYINT | 0=未读, 1=已读 |
| create_time | DATETIME | |

索引: `idx_recipient_read` (`recipient_id`, `is_read`), `idx_recipient_time` (`recipient_id`, `create_time`), `idx_type` (`type`)

### 7.6 V5.3 钱包账户（`wallet_account`，wallet-service 所有）

> 资金域迁出 user 的落地表：存量自 `user.balance` 幂等迁移（唯一键冲突跳过）；`user.balance` 列保留冻结、代码不再读写。

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK AUTO_INCREMENT | |
| user_id | BIGINT UNIQUE NOT NULL | 用户ID（`uk_user_id`） |
| balance | INT NOT NULL DEFAULT 0 | 余额（分） |
| create_time / update_time | DATETIME | |

> **V5.5 已建关联（买家退款资金流，[ADR-035](../adr/035-refund-fund-flow-and-refunded-activation.md)）**：本域流水表 `wallet_transaction`（余额每笔变动留痕，幂等键 `transaction_no`；退款入账 = pay-service 余额渠道退款经 `POST /internal/balance/refund` 触发）——见 §十二 12.1。

### 7.7 V5.12 店铺装修（`shop_decoration`，merchant-service 所有）

> **🆕 2026-10-15 fix-doc 补录**：本表此前全文 0 次出现（完全未收录），现按 `Initialize.sql` 补表级条目。**F11 店铺装修（[ADR-048](../adr/048-content-notes-in-item-domain.md)）**，与 `shop` **1:1**（`uk_shop` 唯一键）；契约端点 S1c/S1d（`/api/seller/shops/{shopId}/decoration`，§4.3.1）+ 公开页 SH1 的 `decoration` 增量。

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK AUTO_INCREMENT | 技术主键 |
| shop_id | BIGINT NOT NULL | 店铺 id（→ `shop.id`，1:1，`uk_shop` 唯一键） |
| announcement | VARCHAR(200) NULL | 店铺公告（契约限 ≤200） |
| banners | VARCHAR(2000) NULL | 轮播图 **JSON 数组文本**（≤5 个 URL；非 JSON 列类型） |
| hot_spu_ids | VARCHAR(1000) NULL | 推荐商品位 **JSON 数组文本**（≤8 个 `spu.id`；**存 id 无 FK**——展示端过滤非在售，Phase 1 不校验商品归属，ADR-048） |
| create_time / update_time | DATETIME NOT NULL | 审计字段 |

> 未装修的店铺无本表行（契约面表现为各字段空值/空数组）；UPSERT 写入（改装修 = 覆盖式）。

### 7.8 V5.12 内容社区（`note` / `note_spu` / `note_like`，item-service 所有）

> **🆕 2026-10-15 fix-doc 补录**：三表此前仅被正文「`note*`」通配串一笔带过，现补表级条目。**F12 内容社区 Phase 1（[ADR-048](../adr/048-content-notes-in-item-domain.md)）**——与 `review` 同属商品 UGC，落位 item-service；契约端点 P28–P34（§4.4.xa）。

**`note`** — 种草笔记（主表）
| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK AUTO_INCREMENT | 技术主键 |
| user_id | BIGINT NOT NULL | 作者 user_id（`idx_user`） |
| title | VARCHAR(50) NOT NULL | 标题 |
| content | VARCHAR(2000) NOT NULL | 正文（Phase 1 纯文本，富文本 Phase 2） |
| cover_image | VARCHAR(255) NULL | 封面图 |
| images | VARCHAR(2000) NULL | 图集 **JSON 数组文本**（≤9 个 URL；非 JSON 列类型） |
| status | TINYINT NOT NULL DEFAULT 1 | 1=已发布，2=已删除（**软删**，流与详情不可见） |
| like_count | INT NOT NULL DEFAULT 0 | 点赞数（冗余计数，**原子维护**，取消不为负） |
| create_time / update_time | DATETIME NOT NULL | 审计字段（`idx_status_time` 支撑发布流倒序分页） |

**`note_spu`** — 笔记关联商品（≤5/篇，发布时校验存在且在售）
| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK AUTO_INCREMENT | 技术主键 |
| note_id | BIGINT NOT NULL | 笔记 id（`uk_note_spu(note_id, spu_id)`） |
| spu_id | BIGINT NOT NULL | 关联商品 id（`idx_spu`） |
| sort | INT NOT NULL DEFAULT 0 | 展示顺序 |
| create_time | DATETIME NOT NULL | |

**`note_like`** — 笔记点赞（uk 幂等）
| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK AUTO_INCREMENT | 技术主键 |
| note_id | BIGINT NOT NULL | 笔记 id（`idx_note`） |
| user_id | BIGINT NOT NULL | 点赞用户 user_id（`uk_note_user(note_id, user_id)` —— 重复点赞空操作） |
| create_time | DATETIME NOT NULL | |

### 7.9 V5.10 每日任务体系（`task_def` / `task_record`，marketing-service 所有）

> **🆕 2026-10-15 fix-doc 补录**：两表此前仅被正文「`task_*`」通配串一笔带过，现补表级条目。**F9 每日任务（[ADR-047](../adr/047-daily-task-system.md)）**——**表驱动**任务定义 + 每日幂等完成记录；契约端点 M14（`GET /api/task/daily`，§4.10.3）/ I49（`POST /internal/task/complete`，§4.14.8c）/ AD51-53（管理端 CRUD，§4.13.8c）。积分经 `PointsManager` 单出口写 `points_log`（type=3 任务奖励），与完成记录**同事务**。

**`task_def`** — 任务定义（seed：`DAILY_ORDER` 确认收货 +20 / `DAILY_REVIEW` 发表评价 +10）
| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK AUTO_INCREMENT | 技术主键 |
| code | VARCHAR(32) NOT NULL | 任务编码（`uk_code` 唯一；`[A-Z][A-Z0-9_]{0,31}`，调用方锚定，**创建后不可改**） |
| name | VARCHAR(50) NOT NULL | 任务名称 |
| type | TINYINT NOT NULL DEFAULT 1 | 1=每日重置（Phase 1 唯一值） |
| reward_points | INT NOT NULL | 完成奖励积分（**管理端调整立即生效**，影响后续发放、不追溯已发） |
| status | TINYINT NOT NULL DEFAULT 1 | 1=启用，0=停用（停用 → 触发口 I49 立即返回 `completed=false`） |
| create_time / update_time | DATETIME NOT NULL | 审计字段 |

**`task_record`** — 任务完成记录（每日幂等）
| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK AUTO_INCREMENT | 技术主键 |
| user_id | BIGINT NOT NULL | 用户 user_id（`idx_user_date`） |
| task_code | VARCHAR(32) NOT NULL | 任务编码（`uk_user_task_date(user_id, task_code, biz_date)` —— 同用户同任务同业务日唯一，**幂等锚点**） |
| reward_points | INT NOT NULL | 实发积分（快照发放时刻的 `task_def.reward_points`） |
| biz_date | DATE NOT NULL | 业务日（每日重置锚点） |
| create_time | DATETIME NOT NULL | |

---

## 八、RBAC 权限体系 (V3.0+)

### 8.1 sys_role（角色表）
| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK | |
| name | VARCHAR(64) NOT NULL | 角色名称 |
| code | VARCHAR(64) UNIQUE NOT NULL | 角色编码（ROLE_USER/ROLE_VIP/ROLE_SELLER/ROLE_ADMIN） |
| description | VARCHAR(256) | |
| status | TINYINT DEFAULT 1 | 1=启用 |
| is_system | TINYINT DEFAULT 0 | 1=系统角色(不可删除) |

### 8.2 sys_permission（权限表）
| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK | |
| name | VARCHAR(64) NOT NULL | 权限名称 |
| code | VARCHAR(128) UNIQUE NOT NULL | 权限编码（domain:action） |
| type | TINYINT DEFAULT 3 | 1=菜单, 2=按钮, 3=API |
| parent_id | BIGINT NULL | 父权限ID（菜单树） |
| path | VARCHAR(256) NULL | 资源路径 |
| sort_order | INT DEFAULT 0 | |

### 8.3 sys_user_role / sys_role_permission / sys_user_permission
用户-角色、角色-权限、用户-直接权限三张关联表（联合主键，FK 约束）。

---

## 九、AI 服务数据模型 (V2.5+)

> 状态（2026-10-15 核对）：`merchant_knowledge_base` **已建**（表结构见 `Initialize.sql`——**唯一结构真相源**；原文"V3.4 迁移"的引用已随 `database/migrations/` 目录删除而失效，ADR-045），所有权归 ai-service（客服域，2026-10 迁入）；`ai_evaluation`、`ai_token_usage` 仍为计划表（**未建**，且按 ADR-045 若落地将直接进 `Initialize.sql`，不再产生迁移文件）。

> AI 数据表为 V2.5 新增，支撑会话管理、消息持久化和知识库功能。完整设计见 [13-AI-Technology-Selection.md](./13-AI-Technology-Selection.md)。

### 9.1 会话与消息

> **本小节原有两张表的逐列结构已删除（U3 裁决，见本文档头注记）**：会话/消息现由 §10.1 `chat_message` 承载，此处不再保留字段表与索引；设计意图见文档头注记的括注。

### 9.2 知识库 (V3.4 已建)

**`merchant_knowledge_base`** — 商家 AI 知识库

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK AUTO_INCREMENT | 主键 |
| seller_id | BIGINT NOT NULL | 商家ID，FK → `seller.id` |
| title | VARCHAR(200) NOT NULL | 知识条目标题 |
| content | TEXT NOT NULL | 知识内容（FAQ/政策/产品说明） |
| category | VARCHAR(30) NOT NULL | 分类: `FAQ` / `POLICY` / `PRODUCT` / `ANNOUNCEMENT` |
| embedding_id | VARCHAR(64) | 向量索引归属标识，口径 **`es:{knowledgeId}`**（摄取成功后回填：该知识的 chunk 已入 search-service 的 `knowledge_vectors` 索引；稳定可反查、不随切片策略变化）；向量链路默认关闭（`ai.rag.enabled=false`）时保持 NULL（见 02-Architecture §3.4） |
| status | TINYINT DEFAULT 1 | 1=草稿, 2=已发布, 3=已归档 |
| create_time | DATETIME DEFAULT CURRENT_TIMESTAMP | 创建时间 |
| update_time | DATETIME ON UPDATE CURRENT_TIMESTAMP | 更新时间 |

索引: `idx_seller_category` (`seller_id`, `category`), `idx_status` (`status`)

### 9.3 评估与统计 (V3.0+)

**`ai_evaluation`** — AI 回答用户反馈

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK AUTO_INCREMENT | 主键 |
| message_id | BIGINT NOT NULL | 消息ID（原设计外键指向的会话消息表已按 U3 删除；落地时改指 §10.1 `chat_message.id`） |
| user_id | BIGINT NOT NULL | 用户ID |
| rating | TINYINT | 评分: 1-5 (5=非常满意) |
| feedback_type | VARCHAR(20) | 反馈类型: `HELPFUL` / `NOT_HELPFUL` / `HARMFUL` |
| feedback_text | VARCHAR(500) | 文字反馈 |
| create_time | DATETIME DEFAULT CURRENT_TIMESTAMP | 反馈时间 |

**`ai_token_usage`** — Token 用量统计（按日聚合）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK AUTO_INCREMENT | 主键 |
| stat_date | DATE NOT NULL | 统计日期 |
| model | VARCHAR(30) NOT NULL | 模型: `deepseek-chat` / `qwen-max` |
| provider | VARCHAR(20) NOT NULL | Provider: `DEEPSEEK` / `QWEN` |
| agent_type | VARCHAR(20) | Agent 类型: `SHOPPING` / `CUSTOMER_SERVICE` |
| prompt_tokens | BIGINT DEFAULT 0 | 输入 Token 总量 |
| completion_tokens | BIGINT DEFAULT 0 | 输出 Token 总量 |
| total_tokens | BIGINT DEFAULT 0 | Token 总量 |
| request_count | INT DEFAULT 0 | 请求次数 |
| cost_cents | INT DEFAULT 0 | 预估费用（分） |

索引: `uk_date_model_agent` UNIQUE (`stat_date`, `model`, `agent_type`)

---

## 十、买家-商家消息数据模型（V3.3；2026-10 所有权归 ai-service）

> 表所有权（2026-10 边界重构）：`chat_message` 归 AI 客服域 ai-service（人工 IM 会话；STOMP `/ws/chat` + REST `/api/chat/**` 由其提供）。

### 10.1 chat_message（聊天消息）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK AUTO_INCREMENT | |
| message_id | BINARY(16) UNIQUE NOT NULL | 消息业务ID（UUIDv7 原始字节，ADR-050） |
| conversation_id | VARCHAR(36) NOT NULL | 会话ID（**保留 VARCHAR**：复合格式，ADR-050）(buyer_{buyerId}_seller_{sellerId}_product_{productId}) |
| sender_id | BIGINT NOT NULL | 发送者用户ID |
| sender_role | VARCHAR(10) NOT NULL | BUYER / SELLER / AI |
| receiver_id | BIGINT NOT NULL | 接收者用户ID |
| content | TEXT NOT NULL | 消息内容 |
| content_type | VARCHAR(10) DEFAULT 'TEXT' | TEXT / IMAGE / PRODUCT_CARD / ORDER_CARD |
| extra | JSON NULL | 扩展数据（商品卡片 productId、订单卡片 orderNo） |
| is_read | TINYINT DEFAULT 0 | 0=未读, 1=已读 |
| is_ai_generated | TINYINT DEFAULT 0 | 0=人工, 1=AI 自动代答 |
| create_time | DATETIME NOT NULL | 发送时间 |

索引: `idx_conversation` (`conversation_id`), `idx_sender_receiver` (`sender_id`, `receiver_id`)

### 10.2 商家回复模板

> **该表的逐列结构已删除（U3 裁决，见本文档头注记）**：客服快捷回复从未实现，不再保留字段表与索引。

---

## 十一、商品详情页增强数据模型 (V3.5 已实现)

### 11.1 已落库的 spu/sku/review 字段

**`spu` 表已新增** (V3.5 migration)：`images` VARCHAR(2000)（多图，JSON 数组文本）/`video_url` VARCHAR(500)/`attributes` JSON（规格参数）/`service_tags` VARCHAR(500)（售后标签，JSON 数组文本）/`view_count` INT（浏览数）；V4.0 追加 `sales_tags` JSON（商品级销售标签）。
**`sku` 表已新增** (V3.5 migration)：`original_price` INT（原价，分；V4.0 另增 stock_type/is_hot/hot_reason VARCHAR(50)/sales_tags JSON）。
**`review` 表实际列**（V3.5 基线 + V5.0.2 增强，与早期文档"media_urls/is_anonymous/helpful_count"不符，以本节为准）：`sku_id`、`images` VARCHAR(500)（逗号分隔）、`reply`/`reply_time`（商家回复）、`append_content`/`append_media_urls`/`append_time`（追评）、`like_count`（点赞数）、`tags` VARCHAR(500)（评价标签 JSON 数组文本）；唯一约束 `uk_order_user(order_no, user_id)`（ADR-050 三批：订单引用由技术 id 收敛为业务键）。

### 11.2 已建表

**`spu_view_log`** — 商品浏览记录 (user_id/spu_id/view_time), uk_user_spu + idx_user_time
**`store_follow`** — 店铺关注：**无 MySQL 表**（实现为 Redis Set，非表；2026-10 第六轮随商家域归 merchant-service，私有 Key `shop:followers:{sellerId}` / `user:follow:shops:{userId}`）
**`sku_spec_dimension`** — SKU规格维度 (spu_id/name/sort_order) (计划)
**`sku_spec_option`** — SKU规格选项 (dimension_id/value/image) (计划)

---

## 十二、V5.5/V5.6 新增表与变更（P0 四项，已建库）

> **已建（2026-10-03 落地同步；2026-10-15 引用更正）**：本节四组表**均已实现并落库**——F1 退款资金流（ADR-035）、F4 渠道对账（ADR-036）、F2 运费模型 + F3 拆单发货（ADR-037）及退款触发纠正。**表结构现状见 `Initialize.sql`（唯一结构真相源）**；⚠️ 原文"随 `V5.5__…` / `V5.6__…` 迁移落库"的表述已失效——迁移机制于 2026-10-05 废除（[ADR-045](../adr/045-db-snapshot-single-source.md)），`database/migrations/` 目录已删除，下表文件名仅作**演进存档**，**不是现存文件、不得引用为建表依据**。正文与 §3.5/§3.7/§3.8/§3.10/§3.11 的"🆕 V5.6 规划"标注已收敛为"已建"。API 契约见 `05-API-Specification.md` §4.4.9 / §4.7.3 / §4.13.14 / §4.14.15-17。
>
> **变更批次（存档，与 05 契约版本 V5.6.0 是两套序列）**：
>
> ⚠️ **下表"文件名"列全部为历史存档**——`database/migrations/` 目录**已删除**（ADR-045，2026-10-05），这些 `.sql` 文件**在仓库中不存在**，**不得作为建表/变更依据引用**。对应结构现状一律见 `Initialize.sql`。
>
> | 存档批次 | 表/变更 | 历史文件名（**已不存在**） |
> | --- | --- | --- |
> | **V5.5 资金线** | `refund_order`、`wallet_transaction`（F1）+ `reconciliation_batch`、`reconciliation_detail`（F4） | ~~`V5.5__refund_wallet_tx_and_reconciliation.sql`~~ |
> | **V5.6 履约线** | `freight_template` 新表 + `spu.freight_template_id` / `orders.post_fee`（F2）+ `order_logistics` 主键与包裹改造、`order_item.package_no`（F3） | ~~`V5.6__freight_template_and_package_split.sql`~~ |
> | **V5.7 订单留言** | `orders.remark VARCHAR(200) NULL`（买家留言快照，T1/T6 请求增量） | ~~`V5.7__order_remark.sql`~~ |
> | **V5.8 促销与会员** | `promotion`（F5 满减/满折活动）+ `member_level_rule`、`member_growth_log`（F6 等级规则与成长值流水，含 seed） | ~~`V5.8__promotion_and_member_level.sql`~~ |
> | **V5.9–V5.18 及后续** | P2/P3 与边界重构全部增量（`shop_decoration`/`note*`/`task_*`/`group_*`/`pairing_rule`/`currency`/`exchange_rate`/`invoice*` 等） | **无迁移文件**——直接改 `Initialize.sql`（ADR-045 口径） |
>
> ※ **2026-10-05 用户裁决：迁移文件机制废除**（[ADR-045](../adr/045-db-snapshot-single-source.md)）——`database/migrations/` 目录已删除（库无存量数据，无需升级路径），`Initialize.sql` 为**唯一结构真相源**（表数以 `node Implementation/back-end/scripts/sync-doc-counts.mjs --list-tables` 为准），此后结构变更直接修改快照；上表批次与文件名仅作演进存档。
>
> 全部新表遵循 §一 约定：`INT` 分、`TINYINT` 状态枚举、审计列 `create_time`/`update_time`、`utf8mb4` + InnoDB、技术主键 `id AUTO_INCREMENT` + 业务唯一键（主键决策矩阵）。
>
> **V5.8 促销与会员（F5/F6，[ADR-043](../adr/043-pricing-center-and-promotion-engine.md)/[ADR-044](../adr/044-member-level-and-benefits.md)）**：①`promotion`——店铺级满减/满折活动（name/seller_id/type[1满减 2满折]/threshold/discount_value[满减=分；满折=万分比]/max_discount[满折封顶]/start_time/end_time/status[时间窗推导]/version）；②`member_level_rule`——等级规则（level/threshold 成长值门槛/discount_rate 万分比/points_rate 百分比/sort，seed：NORMAL 0/10000/100、SILVER 100000/9900/110、GOLD 500000/9800/120、PLATINUM 2000000/9500/150）；③`member_growth_log`——成长值入账流水（`uk_order_no` 幂等锚点，一单一次成长，只升不降）。计价口径见 §十二 12.5/12.6 与 ADR-043/044。

### 12.1 F1 买家退款资金流（V5.5 资金线迁移）

**`refund_order`** — 退款单（**归属 pay-service**；V5.5 新建，三通道原路退回，[ADR-035](../adr/035-refund-fund-flow-and-refunded-activation.md)）

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | BIGINT | PK, AUTO_INCREMENT | 技术主键 |
| refund_no | BINARY(16) | UNIQUE (`uk_refund_no`), NOT NULL | 退款单号（**内部**业务唯一键，UUIDv7 原始字节；管理端重试 AD43 与运维排查的定位键，ADR-050）——**不下发任何渠道** |
| out_refund_no | VARCHAR(64) | UNIQUE (`uk_out_refund_no`), NULL | **对外退款项号**（渠道 `out_refund_no` 的唯一取值来源，[ADR-063](../adr/063-external-channel-number-separation.md)）：20 位随机 hex（80 bit 熵、无时间序）、跨重试稳定；渠道退款回调 / 退款单查询 / 账单核对**一律按本列寻址**，**不得等于 `refund_no`** |
| order_no | BINARY(16) | NOT NULL | 关联订单号（`orders.order_no` 复制，UUIDv7 原始字节，ADR-050） |
| pay_order_no | BINARY(16) | NOT NULL | 关联支付单号（`pay_order.pay_order_no` 复制，UUIDv7 原始字节，ADR-050） |
| after_sale_id | BIGINT | NULL, UNIQUE (`uk_after_sale`) | 关联售后单（`after_sale.id`）——**一售后单一次退款**，唯一约束天然幂等：同 `afterSaleId` 重复请求返回既有退款单；**无售后场景（拼团 I55）为 NULL**（ADR-049 起可空，多 NULL 不冲突） |
| biz_ref | VARCHAR(64) | UNIQUE (`uk_biz_ref`), NOT NULL | 统一幂等业务键（ADR-049）：售后 = `AS:{afterSaleId}`、拼团 = `GB:{orderNo}` —— 幂等锚点从「仅售后」泛化为「任意退款原因」 |
| user_id | BIGINT | NOT NULL | 买家用户ID |
| channel | VARCHAR(16) | NOT NULL | 退款渠道：`WECHAT` / `ALIPAY` / `BALANCE`（与原支付渠道一致，原路退回） |
| refund_amount | INT | NOT NULL | 退款金额（分）；申请时已校验 ≤ 订单实付 `payAmount`（`PARAM_ERROR(1101)`） |
| status | TINYINT | NOT NULL, DEFAULT 0 | 0-处理中，1-成功，2-失败（失败可经管理端重试） |
| channel_refund_no | VARCHAR(64) | NULL | **渠道退款单号**（微信 `refund_id` 等，**渠道回填**）——⚠ **不承载对外退款项号**：对外退款项号是 `out_refund_no`（我方发出）。二者生命周期相反（先出后回），共用一个列会让回调写 `refund_id` 时**覆盖**对外号 → 重试复用不上同号（渠道幂等失效）、退款查询查不到（悬挂补偿卡死）；命名冲突的完整推理见 [ADR-063](../adr/063-external-channel-number-separation.md) §决策 6 |
| fail_reason | VARCHAR(255) | NULL | 失败原因（渠道返回或系统错误摘要，不含敏感细节） |
| create_time | DATETIME | NOT NULL | 创建时间 |
| update_time | DATETIME | NOT NULL | 更新时间 |

索引：`uk_refund_no(refund_no)`、`uk_out_refund_no(out_refund_no)`（渠道号寻址）、`uk_after_sale(after_sale_id)`、`uk_biz_ref(biz_ref)`、`idx_order_no(order_no)`（按订单查退款）

> **状态口径**：退款**处理中/失败**时售后单停留 `APPROVED(2)`（可重试）；仅**成功**回写售后单 `REFUNDED(5)`（trade 经 `PUT /internal/trade/after-sale/{id}/refunded`，**来源 `APPROVED(2) | RETURNED(4)` 条件迁移**，0 行=幂等空操作）。余额渠道：本地同步入账（`wallet_transaction` 幂等）；微信/支付宝渠道：异步回调确认（微信退款回调复用 ADR-033 验签+AES-GCM 解密口径与 ADR-029 裸 JSON 应答协议）。

> **三个号互不替代（2026-10-18，[ADR-063](../adr/063-external-channel-number-separation.md)）**：`refund_no`（**内部**定位：AD43 重试 / 运维排查）≠ `out_refund_no`（**对外**退款项号：渠道 `out_refund_no`、渠道侧幂等键、回调/查询/账单寻址）≠ `channel_refund_no`（**渠道回填**的 `refund_id`）。退款请求里 `out_trade_no` 取**原交易的对外渠道号** `pay_order.channel_trade_no`（**不是** `pay_order_no`），`out_refund_no` 取本行 `out_refund_no`（`RefundManager#requestChannel`）。术语三条见 `backend/03-Domain-Model.md` §六。

**`wallet_transaction`** — 钱包流水（**归属 wallet-service**；余额每笔变动留痕，V5.5 新建，wallet 首次具备流水能力）

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | BIGINT | PK, AUTO_INCREMENT | 技术主键 |
| transaction_no | BINARY(16) | UNIQUE (`uk_transaction_no`), NOT NULL | 流水号（幂等键，= refund_order.refund_no 复制，UUIDv7 原始字节，ADR-050 修订；重复请求按唯一约束/先查判定，幂等成功） |
| user_id | BIGINT | NOT NULL | 用户ID（`wallet_account.user_id` 口径） |
| type | TINYINT | NOT NULL | 1-收入，2-支出 |
| amount | INT | NOT NULL | 变动金额（分，恒正；方向由 type 表达） |
| biz_no | VARCHAR(64) | NOT NULL | 业务关联号（退款流水=退款单号 `refund_no`；支出流水=订单号等） |
| remark | VARCHAR(100) | NULL | 摘要（如"售后退款"） |
| create_time | DATETIME | NOT NULL | 创建时间 |
| update_time | DATETIME | NOT NULL | 更新时间（§一 审计列约定） |

索引：`uk_transaction_no(transaction_no)`、`idx_user_time(user_id, create_time)`（流水分页查询）

> **入账原子性**：入账与流水写入同事务（`wallet_account.balance` 增量 + `wallet_transaction` 插入）；`transaction_no` 冲突视为重复请求，顺序重放幂等返回成功（不重复加余额）；真并发撞唯一键时抛异常令本次「加余额 + 写流水」整体回滚（管理端重试即幂等收敛，资金不重复入账——有意取舍见 ADR-035「后果」）。

### 12.2 F2 运费模型（V5.6 履约线迁移）

**`freight_template`** — 运费模板（**归属 item-service** 商品域）

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | BIGINT | PK, AUTO_INCREMENT | 技术主键 |
| template_no | BINARY(16) | UNIQUE (`uk_template_no`), NOT NULL | 模板业务编号（UUIDv7 原始字节，ADR-050） |
| seller_id | BIGINT | NOT NULL | 商家ID——**口径 = 商家 `user_id`**（与 `spu.seller_id` 等下游口径一致，ADR-019 Phase A） |
| template_name | VARCHAR(50) | NOT NULL | 模板名称；同商家内唯一 `uk_seller_template(seller_id, template_name)` |
| charge_mode | TINYINT | NOT NULL, DEFAULT 1 | 计费方式：1-固定运费（**Phase 1 唯一值**，为 Phase 2 模板/重量计费预留） |
| fee | INT | NOT NULL | 固定运费（分，≥0） |
| free_threshold | INT | NOT NULL, DEFAULT 0 | 满额包邮阈值（分）；**0 = 不参与满额包邮**（>0 时订单商品总额达到该值则运费为 0） |
| status | TINYINT | NOT NULL, DEFAULT 1 | 1-启用，0-停用（停用模板不参与计价） |
| create_time | DATETIME | NOT NULL | 创建时间 |
| update_time | DATETIME | NOT NULL | 更新时间 |

索引：`uk_template_no(template_no)`、`uk_seller_template(seller_id, template_name)`、`idx_seller_status(seller_id, status)`（商家默认模板解析：该商家**最早创建的启用模板**，`WHERE seller_id=? AND status=1 ORDER BY id ASC LIMIT 1`）

**既有表增量（同迁移批次）**：

| 表 | 变更 | 说明 |
| --- | --- | --- |
| `spu` | 新增 `freight_template_id BIGINT NULL`，FK `fk_spu_freight_template` → `freight_template.id` **ON DELETE SET NULL** | NULL/模板被删 → 回落商家默认模板；见 §3.5 |
| `orders` | 新增 `post_fee INT NOT NULL DEFAULT 0` | 下单时计算并**快照**（模板后续改价不影响已建订单）；见 §3.7 |

**计价规则（Phase 1 口径，与 05 §4.4.9/§4.6.1 契约同步）**：

1. **金额不变式变更（旧→新）**：
   - 旧：`payAmount = totalAmount − discountAmount`
   - 新：`payAmount = totalAmount − discountAmount + postFee`
   - `totalAmount` 仍 = Σ `order_item.subTotal`（不含运费）；`postFee` 单列快照。（02-Architecture §六 与 03-Domain-Model §四 已同步 V2 口径。）
2. **同单同商家**：跨店下单已被禁止（`CROSS_STORE_FORBIDDEN(3014)`），一单内商品同商家，运费计一次。
3. **取最高者计一次**：取订单内各商品**绑定模板**中 `fee` 最高者计一次运费（不按商品叠加）。
4. **满额包邮**：订单商品总额（`totalAmount`）≥ 该模板 `free_threshold`（>0）时 `postFee = 0`。
5. **默认模板回落**：商品未绑定模板（NULL）或绑定模板**已停用**时，用商家默认模板（最早创建的启用模板）；商家无任何启用模板时 `postFee = 0`（Phase 1 兜底，不阻断下单）。
6. 秒杀订单（orderType=3，无店铺归属）`postFee = 0`。

### 12.3 F3 拆单发货（V5.6 履约线迁移）

**`order_logistics` 改造**（1:1 → 1:N，存量库迁移含数据回填）：

| 变更 | DDL 要点 | 说明 |
| --- | --- | --- |
| 新增代理主键 | `id BIGINT AUTO_INCREMENT`，**PRIMARY KEY (id)**；`order_id` 转为**普通外键**（`fk_logistics_order` 关系保留；**不建 `uk_order_id` 单列唯一键**——1:N 语义下单列唯一会禁止一单多包裹，原稿该表述已纠错删除） | 收敛 §3.11「主键复用 order_id」约定偏差；一单多包裹后按 `package_no` 区分包裹 |
| 新增包裹号 | `package_no INT NOT NULL DEFAULT 1`；`UNIQUE KEY uk_order_package(order_id, package_no)` | 同一订单包裹号从 1 递增；**存量数据回填 `package_no = 1`** |
| 语义变更 | `order_id` 由「1:1 主键」变为「1:N 外键」 | 每次发货（包裹）插入一条物流记录，字段口径（快照列/状态机）不变 |

**`order_item` 增量**：

| 表 | 变更 | 说明 |
| --- | --- | --- |
| `order_item` | 新增 `package_no INT NULL` | 发货时标记明细归属包裹（对应 `order_logistics.package_no`）；NULL=未发货；**一明细只发一次**（重复发货同一 sku 被拒，`ORDER_STATUS_INVALID(3010)`）；`KEY idx_order_package(order_id, package_no)` 支撑按包裹聚合明细 |

> **订单状态口径**：部分发货订单保持"待发货(2)"；**全部明细**均有 `package_no` 后才迁移"待收货(3)"（不新增订单状态，复用 §九 状态机）。空 `skuIds` = 全量发货（单包裹语义兼容）。

### 12.4 F4 渠道对账（V5.5 资金线迁移）

**`reconciliation_batch`** — 对账批次（**归属 pay-service**）

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | BIGINT | PK, AUTO_INCREMENT | 技术主键 |
| bill_date | DATE | NOT NULL | 账单日 |
| channel | VARCHAR(16) | NOT NULL | 对账渠道：`WECHAT` / `ALIPAY`（Phase 1 两渠道；余额渠道本地闭环不参与对账） |
| status | TINYINT | NOT NULL, DEFAULT 0 | 0-处理中，1-完成，2-失败 |
| channel_total | INT | NULL | 渠道账单总额（分，账单解析后回填） |
| local_total | INT | NULL | 本地支付成功总额（分，`pay_order.status=成功` 按日汇总） |
| create_time | DATETIME | NOT NULL | 创建时间 |
| update_time | DATETIME | NOT NULL | 更新时间 |

索引：`uk_date_channel(bill_date, channel)`（**同日同渠道一批次**——重复执行先删后写的幂等锚点，并兜底并发重复触发）

**`reconciliation_detail`** — 对账差异明细（**归属 pay-service**）

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | BIGINT | PK, AUTO_INCREMENT | 技术主键 |
| batch_id | BIGINT | FK → reconciliation_batch.id, NOT NULL | 所属批次 |
| pay_order_no | BINARY(16) | **NULL** | 本地支付单号（**内部定位用**；「渠道有本地无」的差异行**没有**本地支付单 → 为 NULL，**不得伪造**，UUIDv7 原始字节，ADR-050/063） |
| channel_trade_no | VARCHAR(32) | NULL | **对外渠道号**（**比对键**，= `pay_order.channel_trade_no`）：渠道账单的「商户订单号」列即本列，故四态比对按它匹配；两侧都有时回填，仅渠道有时也回填（[ADR-063](../adr/063-external-channel-number-separation.md) §决策 5） |
| channel_txn_id | VARCHAR(64) | NULL | 渠道交易号（渠道账单有则回填） |
| channel_amount | INT | NULL | 渠道账单金额（分；渠道无此单为 NULL） |
| local_amount | INT | NULL | 本地金额（分；本地无此单为 NULL） |
| result | TINYINT | NOT NULL | 比对结果：1-匹配，2-渠道有本地无，3-本地有渠道无，4-金额不一致 |
| create_time | DATETIME | NOT NULL | 创建时间 |
| update_time | DATETIME | NOT NULL | 更新时间（§一 审计列约定） |

索引：`idx_batch_result(batch_id, result)`（管理端按差异类型筛选）

> **Phase 1 口径**：差异仅**落库记录供管理端查看**（§4.13.14 AD44-AD46），**不自动调账**；日终任务由 XXL-Job 触发（dev 默认关闭），渠道账单下载经 ia-integration（Phase 1 mock 返回样例账单）。**重复执行同日对账幂等**：同 `(bill_date, channel)` 先删旧批次与明细再重写（`uk_date_channel` 兜底并发重复触发）。
> **比对键口径（2026-10-18，[ADR-063](../adr/063-external-channel-number-separation.md)）**：四态比对**一律按 `channel_trade_no`**（渠道账单的「商户订单号」列 = 我方发起时发给渠道的 `out_trade_no`）；`pay_order_no` 只是**内部定位用**的附带列，且「渠道有本地无」的行**恒为 NULL —— 不伪造本地单号**（伪造会让管理端去追一个不存在的支付单）。

### 12.5 F13 拼团（V5.14，[ADR-049](../adr/049-group-buy-full-loop.md)，schema 直改 Initialize.sql）

| 表 | 关键列 | 说明 |
| --- | --- | --- |
| `group_buy_activity` | seller_id / spu_id / sku_id / group_price(分) / required_members(≥2) / time_limit_hours / start_time / end_time / status(1/2/3 时间窗推导) / version | 拼团活动（marketing 域，管理端 AD54-56） |
| `group_team` | team_no(UK) / activity_id / leader_user_id / required_members / current_members / status(1拼团中 2已成团 3已失败) / expire_time / success_time | 团（成团状态机；参团条件更新 +1，满员条件置已成团） |
| `group_team_member` | team_id / user_id / order_no(**UK**，幂等锚点：一单一团一次) | 团成员（uk_team_user 防同团重复参团） |
| `refund_order`（改造） | `after_sale_id` 改**可空** + 新增 `biz_ref VARCHAR(64) NOT NULL` + `uk_biz_ref` | 幂等锚点泛化（修订 ADR-035 §1）：售后退款 `AS:{id}`、拼团退款 `GB:{orderNo}`；`uk_after_sale` 保留 |

> 计价口径：拼团单不进价格计算中心（`discountAmount=0`，与秒杀同口径，ADR-043）；参团两段式（下单不占团位、支付成功后 I53 入团）与结团退款联动见 ADR-049。

### 12.6 F14 发票（V5.15，[PRD §十五 已定稿](../product/01-PRD.md)，schema 直改 Initialize.sql）

| 表 | 关键列 | 说明 |
| --- | --- | --- |
| `invoice` | invoice_no(UK) / order_no / `active_flag` NULL（`uk_order_active` 多 NULL = 一单一有效票，user_auth 先例）/ seller_id（开票主体商家，D1）/ type / title 快照 / tax_no·email **PII 三字段**（O4 从严）/ amount = payAmount 快照（D3）/ status（1待开具 2已开具 3已冲红 4已作废，条件迁移）/ mock_invoice_no + pdf_key（模拟开具，`InvoiceIssuePort` 防腐）/ red_for_id / partial_refund_flag（D4/O6）/ urge_time（O10） | 发票单（买家 INV1-INV5 + 管理端 AD57-60；全额退款自动冲红联动 I56，fail-open） |
| `invoice_title` | user_id / type / title / tax_no 三字段 / is_default NULL（`uk_user_default` 多 NULL = 一用户至多一条默认）/ deleted（@TableLogic） | 常用抬头（O2 随批简版；发票单为抬头快照，本表仅服务申请体验） |

### 12.7 F15 知识图谱 Phase 1（V5.16，[PRD §十六 已定稿](../product/01-PRD.md)，schema 直改 Initialize.sql）

| 存储 | 内容 | 说明 |
| --- | --- | --- |
| `pairing_rule`（MySQL，**唯一新增表**） | src_category_id / dst_category_id（`uk_src_dst`）/ weight / status | 类目搭配规则 = PAIR_WITH 边真相源（D1 混合最小形态；平台级类目粒度，O3） |
| `kg_edges`（ES 边索引，投影） | edgeId / srcType/srcId / relType / dstType/dstId / weight / source / status | 直建边（BELONGS_TO/PARENT_OF/BRAND_OF/SOLD_BY 真相=源表列，零新增存储）+ PAIR_WITH（真相=pairing_rule）按 src 先删后写物化；查询能力 ≤2 跳 |
| 既有三向量索引（不变） | products / knowledge_vectors / notes | P35 混合检索的向量兜底面（图主召回、向量兜底） |

> 降级纪律：KG 全链路 fail-open 纯旁路（对齐 ADR-046/011）；NebulaGraph 引入触发器量化（§16.9，≥2 项才立项）。

### 12.8 F16 多币种 Phase 1（V5.17，[PRD §十七 已定稿](../product/01-PRD.md)，schema 直改 Initialize.sql）

| 表/列 | 关键列 | 说明 |
| --- | --- | --- |
| `currency`（MySQL，新增） | code(UK) / name / symbol / exponent（ISO 4217 最小单位指数）/ status | 币种（wallet 资金域；O8 种子六币种 USD/EUR/JPY/KRW/GBP/HKD；CNY 恒主币种不入表） |
| `exchange_rate`（MySQL，新增） | code(UK) / `rate_scaled` BIGINT（每 1 CNY 兑目标币主单位数 × 10⁶，定点整数严禁浮点）/ source（API/MANUAL）/ effect_time / updater_id | 展示汇率（一行一币 upsert，O6 不做历史版本化；拉取失败保上次值 + 告警） |
| `orders` 增列 | `display_currency` VARCHAR(3) NULL / `display_rate` BIGINT NULL | 下单快照锁汇（D1-B/D3-B）：NULL=CNY 订单或快照降级；**展示参考、非结算币种**——不参与金额不变式 V2，列注释强制隔离（NFR §17.7.1） |

> 金额纪律（硬验收）：全部既有金额字段（INT 分）语义零改动；换算仅前端展示（D7-A，后端契约零换算）；定点整数公式与舍入口径见 PRD D4 与 05 §1.5。
