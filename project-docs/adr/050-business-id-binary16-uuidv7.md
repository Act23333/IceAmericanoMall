# ADR-050: 业务标识存储升级 —— BINARY(16) + UUIDv7 + byte[] 持久化边界（修订 ADR-002 双主键的存储形态）

## 背景

ADR-002 确立双主键策略：`id BIGINT AUTO_INCREMENT` 技术主键 + `xxx_id/xxx_no VARCHAR(32/36)` 业务唯一标识（UUID）。业务标识以字符串形态存储，存在三项可优化成本：

1. **空间**：`VARCHAR(32)` 实际占 33 字节（含长度位），唯一索引条目 41 字节（含行指针/变长开销）；
2. **索引局部性**：项目全部业务 UUID 由 v4 全随机生成（`UUID.randomUUID()` / `IdUtil.fastSimpleUUID()`，23 处散落生成点），二级唯一索引写入为随机点插入，页分裂与 buffer pool 局部性差；
3. **格式不统一**：同库并存 32-hex（`fastSimpleUUID`）、36-hyphen（`Invoice.invoiceNo`、`refund_no`、`transaction_no`）两种形态。

2026-10-07 用户决策（B+ 方案）：项目处于开发环境、库无存量数据（ADR-045），是存储形态改造的零成本窗口，立即执行。原提案为「BINARY(16) + `java.util.UUID` + 自定义 TypeHandler + `UUID_TO_BIN(x, true)`」，评审修正后定稿如下。

## 决策

### 1. 业务标识列存储 `BINARY(16)`（27 列）

**转换清单（依赖闭包：值复制自转换列的列同步转换）**：

| 表.列 | 原类型 | 说明 |
|---|---|---|
| `user.user_id` | VARCHAR(32) | 生成列 |
| `product.product_id` / `sku.sku_id`（**现名 `spu.spu_id` / `sku.sku_id`**，ADR-059） | VARCHAR(32) | 生成列（search-service 自有 PO 同步改造） |
| `freight_template.template_no` | CHAR(36) | 生成列（统一 32-hex） |
| `orders.order_no` | VARCHAR(32) | 生成列 |
| `pay_order.pay_order_no` / `biz_order_no` | VARCHAR(32) | 生成列 / order_no 复制 |
| `user_coupon.used_order_no` / `flash_order_log.order_no` / `after_sale.order_no` | VARCHAR(32) | order_no 复制（flash_order_log 当前无代码接入，DDL 与 orders 口径一致） |
| `settlement.settlement_no` / `withdrawal.withdrawal_no` | VARCHAR(32) | 生成列 |
| `chat_message.message_id` | VARCHAR(36) | 生成列 |
| `refund_order.refund_no` / `order_no` / `pay_order_no` | CHAR(36)/VARCHAR(64) | 生成列 / 复制列 |
| `reconciliation_detail.pay_order_no` | VARCHAR(64) | pay_order_no 复制（比对键） |
| `wallet_transaction.transaction_no` | CHAR(36) | **修订一批（2026-10-14）**：幂等键=refund_no 复制，写入方唯一实证全 UUID；转 BINARY(16)，入参严格 `toBytes` fail-fast |
| `coupon.coupon_id` | VARCHAR(32) | **修订二批（2026-10-14）**：创建端归一化——空→系统生成 UUIDv7；已提供须 32-hex/36-hyphen 且归一为 32-hex（可读编码 `CPN-001` 之类 fail-fast `PARAM_ERROR`）；查询端 `toBytesOrNull`（外部入参按不存在处理） |
| `ai_conversation.conversation_id` / `ai_message.conversation_id` | VARCHAR(36) | **修订二批（2026-10-14）**：预留表（全后端零代码引用，同 `flash_order_log` 先例），列直接收紧 BINARY(16)；接入契约见 05-API AI 行（服务端生成 32-hex，续聊回传） |
| `review.order_no`（**新增业务键列**，删除原 `order_id BIGINT` 技术键引用） | BIGINT | **修订三批（2026-10-14）**：跨域引用收敛——item 域评价引用 trade 订单由技术 id 改为业务键；契约 `ReviewCreateReq.orderNo`（32-hex，来源 `OrderVO.orderNo`，修复原 `orderId(Long)` 前端无从获得的历史缺口） |
| `member_growth_log.order_no` / `group_team.team_no` / `group_team_member.order_no` | VARCHAR(32) | 复制 / 生成 / 复制 |
| `invoice.invoice_no` / `order_no` | CHAR(36)/VARCHAR(32) | 生成列（统一 32-hex）/ 复制 |

**保留清单（含理由）**：`coupon.stack_group`（管理端输入，无格式约束）、`chat_message.conversation_id`（复合格式 `buyer_{x}_seller_{y}_product_{z}`，内嵌技术 id，不可二进制化；**`product_` 为客户端自造字面量，属 ADR-059「刻意保留」项，不随 SPU 改名变动**）、`notification.type` / `user_auth.identity_type`（枚举编码）、`task_def.code` / `task_record.task_code`（语义编码）、`refund_order.biz_ref`（复合格式 `AS:{id}`/`GB:{orderNo}`）、`wallet_transaction.biz_no`（多格式混合引用）、`channel_refund_no` / `channel_txn_id`（渠道外部值）、`embedding_id`（`es:{id}` 格式）。

> **修订注记（2026-10-14）**：`wallet_transaction.transaction_no` 原作为「Feign 幂等键契约值」保留 VARCHAR；复核确认**写入方唯一**（pay 退款链路 `transactionNo = refundNo` 复制）且实证全 UUID，遂转入转换清单（22→23 列，保留 10→9 列），原「双表示禁直连」的该示例随转换消失（比较口径恢复一致）。

> **修订注记·二批（2026-10-14）**：`coupon.coupon_id` 与 `ai_conversation/ai_message.conversation_id` 三列按「契约收紧」路线完成转换（22→23→26 列）：前者落地创建端归一化（见上表）、后者为预留表直接收紧；保留清单同步收敛为 11 项。

> **修订注记·三批（2026-10-14）**：`review` 的订单引用由技术 id（`order_id BIGINT` + 唯一键锚点）收敛为业务键 `order_no BINARY(16)`（转换口径 27 列含此新增列；原列随 Initialize.sql 直接替换，无存量数据）；ReviewAPI 契约缺口一并修复。**该收敛为范式**：`order_logistics.order_id`（logistics→trade 技术键 + 物理 FK）已按同范式列入 04-Data-Model 分片影响面清单「分表前必须收敛项」。

### 2. 生成算法收敛 UUIDv7，严禁 swap

- ia-common 新增唯一出口 `IdGenerator`（`common/utils`，纯 JDK）：
  - `nextId()` —— UUIDv7（RFC 9562：48 位毫秒时间戳 + 74 位随机），输出 **32 位小写 hex 无连字符**，与原 `fastSimpleUUID` 格式逐位兼容（对外 API 契约零改动）；
  - `toBytes(String)` / `toHexString(byte[])` —— 仅限仓储边界转换（36-hyphen 容错解析）；
  - `secureToken()` —— 32 字节（256 位熵）SecureRandom hex，专供 `refreshToken` / JWT `jti` 等凭据场景（凭据不可从时间序推断，禁止用 v7 生成）。
- v4 → v7 的原因：v7 时间戳在最高 6 字节，原始字节序趋势递增，B+ 树索引写入由随机点插入变为趋势追加。
- **严禁 `UUID_TO_BIN(x, true)` swap**：swap 只对 v1/v6 的 time-low｜time-mid｜time-high 布局有意义；v7 时间本就在高字节，swap 反而破坏时间序；v4 全随机则 swap 无效（这是替换 v4 的核心原因）。

### 3. 持久化边界用 `byte[]`，否决自定义 TypeHandler

- **PO 字段声明 `byte[]`**（MyBatis 默认 `ByteArrayTypeHandler`：插入/BaseMapper CRUD/结果映射/包装器 eq·in 全部原生工作，无需注解、无需 `autoResultMap`）；
- **否决「String PO + @TableField(typeHandler)」**：MyBatis-Plus 包装器查询参数按运行时类型解析（`StringTypeHandler`），**绕过 `@TableField(typeHandler)`** —— String 对 `BINARY(16)` 列比较永不相等且**静默查空**（不报错，全项目最难排查的一类缺陷）；
- **否决 Java 各层 `java.util.UUID`**：收益仅编译期类型安全，代价是 15 个 Feign 契约 + MapStruct + 全部 Req/VO 连锁；对外 JSON 出口本就是 String。

### 4. 类型分层契约

| 层 | 形态 |
|---|---|
| 数据库 | `BINARY(16)` 原始字节 |
| PO（infrastructure.persistence） | `byte[]` |
| 仓储 toPO/toDomain 与仓储方法出入参 | `IdGenerator.toHexString()` / `toBytes()` 转换 |
| domain 聚合 / app / adapter / Feign DTO / VO / Req / Redis / ES / MQ | **String（32 位小写 hex）**，零改动 |

### 5. 种子与运维口径

- 种子中的业务 ID 一律 32-hex 定值 + `UNHEX()`（`admin` 账号 `UNHEX('aaaaaaaa000070008000000000000001')`，原 `'admin-001'` 非 hex 字面量不可存入 BINARY 列）；
- 运维/排查 SQL 需 `HEX(col)` / `UNHEX('...')` 包裹；
- **未来新表**：业务标识列默认 `BINARY(16)`，生成一律走 `IdGenerator.nextId()`（09-constraints 增补反模式条目）。

## 后果

- ✅ 业务标识列空间减半（33→16 字节），唯一索引条目 41→24 字节；索引写入趋势递增；
- ✅ 23 处散落生成点收敛到 `IdGenerator` 单出口，格式统一 32-hex；
- ✅ 对外 API / Feign / 前端契约零改动（05-API-Specification 无需变更）；
- ✅ 库无存量数据（ADR-045），无迁移成本；
- ⚠️ 新硬约束：包装器 `eq/in/ne` 与显式 SQL 参数对 BINARY(16) 列必须传 `IdGenerator.toBytes(...)`（漏传 = 静默查空，评审必查项）；
- ⚠️ 运维 SQL 永久需要 `HEX()/UNHEX()` 包裹；
- ⚠️ 保留列与转换列并存异构格式（会话 ID 客户端可控、枚举编码、幂等键契约）——新增同类列时按保留清单理由归类。

## 日期

2026-10-07

## 参考

- [ADR-002](./002-dual-primary-key-and-money.md)（双主键策略，本 ADR 修订其存储形态，双 ID 架构不变）
- [ADR-045](./045-db-snapshot-single-source.md)（Initialize.sql 唯一结构真相源）
- `04-Data-Model.md` §一（主键决策矩阵 / 标识使用原则）
- `ia-common` `org.icedamericanomall.common.utils.IdGenerator`（实现与完整 Javadoc）
- 分库分表路线（04-Data-Model §一）：`IdType.AUTO` → `ASSIGN_ID` 雪花 + 单号基因路由，与 BINARY(16) 正交，B+ 改造不改变分表步骤
