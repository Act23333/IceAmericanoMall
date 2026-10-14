# ADR-052: search-service DB 降级直读 item 域表 —— 受控豁免登记与退出条件

> **状态注记（2026-10-14）**：**退出已执行完成**——search-service 不再直读任何业务库表，LIKE 降级检索 / 联想词 / ES 重建数据源 / publishTime 回填全部改经 item-service 内部检索端点（ia-api `ItemSearchClient`）；本 ADR 的「豁免边界」条目作为历史决策记录保留。执行清单见文末。

## 背景

search-service 的 LIKE 降级链路（`DbProductSearchGateway` + 自有 `ProductPO`/`SkuPO`/Mapper）**直读 `product`/`sku` 表**（item 域的表），属于跨服务数据所有权边界外的访问。2026-10-10 架构 CR 定位：

- 该直读不在 ADR-018 / ADR-027 / ADR-031 的**显式豁免体系**内——是无治理的口子（其他跨服务键均经白名单登记）；
- 双重风险：①item 侧 schema 变更会**静默**破坏 search 降级链路（无编译/测试保护）；②分库分表后跨库直读**必然失效**（item 域按 user/seller 分片后 search 无法直读）。

## 决策

**登记为受控豁免（带退出条件），不立即重构**。维持现状的理由：双链路（ES 主 + DB 降级）是 ADR-046 时代的既有设计，降级仅在 ES 不可用时启用；立即改为经 item-service 检索端点会把 item-service 变成 search 的后端（职责倒挂），或需要引入物化视图/CDC——均属独立工程，不在当前迭代。

**豁免边界（必须同时满足，任一破坏即须走下方退出路线）：**

1. **只读**：search 侧对 `product`/`sku` 仅 SELECT，禁止任何写操作（代码层面已满足，评审必查）；
2. **列白名单**：PO 仅映射检索/展示所需列（`ProductPO`/`SkuPO` 现状已是白名单子集，加列需评审）；
3. **降级语义**：ES 为主链路，DB 查询仅 `search.elasticsearch.enabled=false` 或 ES 故障时启用；
4. **schema 变更联动**：任何修改 `product`/`sku` 表结构的变更（Initialize.sql），必须同步检查 search 侧 PO 映射（本条写入 09-constraints 评审清单）；
5. **ID 口径**：`ProductPO.productId`/`SkuPO.skuId` 遵守 ADR-050（`BINARY(16)` ↔ `byte[]`，2026-10 已同步）。

**退出条件（满足任一即启动退出，必须早于分库分表实施）：**

- **分库分表启动前（硬红线）**：`product`/`sku` 进入 ShardingSphere 分片前，search 降级链路必须退出直读——改为经 item-service `/internal/**` 检索端点，或 item 域经 CDC/物化视图同步至 search 私有库；
- item 侧 `product`/`sku` 发生结构性重构（列重命名/拆分）且联动成本超过端点改造时；
- search-service 承担跨域读的规模成为性能瓶颈时。

## 执行完成（2026-10-14）

| 项 | 内容 |
|---|---|
| item 数据面出口 | `POST /internal/item/search/page`（LIKE 检索，SQL 语义逐条对齐原直读实现）、`GET /suggest`、`GET /export`（ES 重建分页源）、`GET /detail/{id}`（publishTime 回填）；应用层 `ItemSearchQueryManager` 做入参夹取（页长 ≤100 / 联想 ≤50） |
| ia-api 契约 | `ItemSearchClient`（16th Feign client，fail-closed 空结果降级）+ 3 个 CO；降级工厂经 `DefaultFeignConfig` 登记 |
| search 替换 | 新 `ItemServiceProductSearchGateway` / `ItemServiceProductSuggestGateway`（infrastructure/adapter）；ES 网关 reindex 改分页循环拉取（不再全量载入内存） |
| search 移除 | 删 `ProductPO`/`SkuPO`/`ProductMapper`/`SkuMapper`/`DbProductSearchGateway`/`ProductSuggestDbGateway`；移除 mybatis-plus/jsqlparser/mysql-connector 依赖、datasource 配置与 `@MapperScan`；HTTP 信封由 MP `Page` 换为自持 `SearchPageVO`（records/total/size/current/pages，核心字段不变） |
| 测试迁移 | 原 search 两份 H2 SQL 语义测试（17 例）**迁入 item 侧**（`ProductSearchMapperH2Test`/`ProductSuggestMapperH2Test`，含分页循环新覆盖）；search 侧改薄委托测试；ArchUnit 基线**零新增** |
| 新义务 | item 检索端点成为跨域契约：字段/语义变更需走 05-API 评审（原 schema 联动义务的反向形式） |

## 后果

- ✅ 口子进入治理体系：豁免边界、联动义务、退出红线全部书面化，分片影响面清单（04-Data-Model §一）同步引用；
- ⚠️ 分库分表前存在一笔明确的架构债（已排期：分片工程的第一前置项）；
- ⚠️ 依赖"评审必查"维持列白名单——CI 层暂无结构性守卫（列白名单校验待 ArchUnit 提案落地后补充）。

## 日期

2026-10-10

## 参考

- [ADR-046](./046-product-vector-recommendation.md)（search 双链路设计背景）
- [ADR-050](./050-business-id-binary16-uuidv7.md)（search 自有 PO 的 BINARY(16) 同步）
- `04-Data-Model.md` §一「分片影响面清单」
- `09-constraints.md` §三·五（schema 联动评审项）
