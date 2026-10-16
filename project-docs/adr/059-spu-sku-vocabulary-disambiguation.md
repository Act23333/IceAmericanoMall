# ADR-059: SPU/SKU 词汇消歧 —— `product` 实为 SPU，线格式统一 `spu_id` / `sku_id`

## 状态

已决策；**线格式（契约 + VO/DTO）先行**，库表/领域层重命名为后续独立批次

## 背景

2026-10-15 审计把 `productId` 报为「同一实体同名异型异义」，**该定性有误**。真实情况是**两个不同实体共用一个名字**：

| 表 | 列 | 实际语义 |
| --- | --- | --- |
| `product` | `id BIGINT` | 技术主键 |
| `product` | `product_id BINARY(16)` | **SPU 业务唯一标识**（ADR-050） |
| `sku` | `id BIGINT` | SKU 技术主键 |
| `sku` | `product_id BIGINT` | **SKU → SPU 的技术外键**（指向 `product.id`） |

于是线格式里同时存在：

- `ProductVO.productId` = `String`（SPU **业务键**，32-hex）
- `SkuVO.productId` / `FavoriteVO.productId` / `HistoryVO.productId` / `FlashSaleVO.productId` 等 11 处 = `Long`（**SPU 技术键**）

它们**不是同一个字段的两种形态**，而是「同一概念（SPU）的两种标识」+「命名未区分 SPU 与 SKU」。审计据此给出的"收敛为业务键 / 技术外键改名"二选一**问法本身是错的**。

## 决策

### 1. 采用行业标准词汇 SPU / SKU（阿里、京东、Shopify `product`+`variant` 同构）

本项目中 **`product` 就是 SPU**（标准产品单元），`sku` 是 SKU。**词汇层面把 SPU 显式化**，不再用 `product` 兼职表达 SPU。

### 2. 线格式：一律业务标识，且名称显式区分

| 线格式字段 | 含义 | 形态 |
| --- | --- | --- |
| `spu_id` | **SPU 业务标识** | `String`，32-hex（ADR-050） |
| `sku_id` | **SKU 业务标识** | `String`，32-hex |

- 技术键（`spu.id` / `sku.id`）与**技术外键**（`sku.product_id`）**只保留在 PO 与领域层**，不得出现在任何 VO/DTO —— 与 ADR-058 §1 同口径。
- 线格式中**不再出现 `product_id`**（它是歧义源）；出现即为违规。

### 3. 内部（库表/领域）重命名：独立批次

`product` → `spu`、`product.product_id` → `spu.spu_id`、`sku.product_id` → `sku.spu_id`。**属高影响重构**（62 表、19 模块、全部 PO/Mapper/领域/仓储/索引/前端类型），
与线格式解耦，**单独成批、单独提交**，避免把 API 变更与结构重构混在一次提交里（这样任一方向出问题都可独立回退）。

> 本项目已废除迁移机制（ADR-045，库无存量数据），**结构性重命名成本是零**——这是执行该重命名最经济的窗口。

### 4. 排序理由：线格式先行

对外报文是**契约**（`contract/05` 为唯一真相源），先定对外承诺，再改内部实现。若先改库表，会在"线格式未定"期间产生两种词汇并存的混乱。

## 后果

**正面**：消除 SPU/SKU 歧义；线格式与行业词汇对齐；跨接口 join 在类型层可成立（SPU 业务键全链路同型）。
**负面 / 成本**：线格式字段改名是 **BREAKING CHANGE**（`product_id` → `spu_id`），需契约升版 + 前端同步；库表重命名触及面广，需独立批次与独立验证。

## 守卫（防复发）

| 守卫 | 断言 |
| --- | --- |
| 线格式禁用歧义名 | 扫描 `adapter/response/**` 与 `ia-api/**/dto/**`：出现 `productId`/`product_id` 即失败 |
| MapStruct 丢字段 | 构建期 `Unmapped target properties` 对选定 mapper **升级为失败**（见本轮 F-1 教训：该警告一直存在却无人读） |
| 业务标识同型同义 | 同名字段在所有 DTO 中类型与语义一致（ADR-058 守卫的扩展） |

## 参考

- ADR-002（双主键）、ADR-050（业务标识 `BINARY(16)`+UUIDv7）、**ADR-058**（VO 标识字段语义唯一性）、ADR-057（展示字段读模型）、ADR-045（库无存量数据）
- `database/Initialize.sql:249`（`product.product_id BINARY(16)`）vs `:288`（`sku.product_id BIGINT`）
- 审计报告 F-2（定性已由本 ADR 更正）
