# ADR-046: 商品向量推荐 —— 复用向量管道的单向量相似检索与 item 侧新鲜组装

## 背景

P2 首选功能「商品推荐」的性价比判断：T14 已打通 `chunk → embed → ES 索引 → kNN 检索` 全链路（知识库 `knowledge_vectors`），且商品侧 `ProductDocument` 早就带有 `embedding` 字段、I28 `index=products` 就是现成的商品 kNN 检索——**管道齐全，唯独商品向量从未被摄取**（商品进 ES 只靠 reindex 手动全量重建，日常创建/编辑不触发），推荐无数据可查。本 ADR 以最小增量补齐摄取与 C 端出口。

## 决策（2026-10-05）

### 1. Phase 1 只做「相似商品」（向量 kNN），「看了又看」留 Phase 2

向量相似（内容理解）无需行为数据积累即可上线；共现统计（浏览日志聚合）需要数据量与离线计算，Phase 2。

### 2. 单向量不分块（与知识库路线刻意不同）

商品文本（`name + description`，可选拼接 brand）是短文本，单条 embedding 语义已足够；知识库的 `DocumentChunker` 多块路线服务于长文档召回，不引入。I47 幂等覆盖写：同 productId 重写**整个** `ProductDocument`（含 name/description/brand/categoryId/mainImage/price/soldCount + embedding）——顺带补齐「日常增量进 ES」的缺口（此前只有 reindex 全量兜底），与 reindex 共存（reindex 为全量重建兜底，I47 为增量主路径）。

### 3. 查询 I48 只出 `productId + score`，C 端组装归 item

ES 文档可能滞后于 MySQL（编辑与摄取之间有窗口），**展示真相源是 MySQL**：P27（item C 端）拿到命中 id 后批量查本地库组装新鲜商品卡，并**恒过滤在售、排除自身、按相似度降序**。search-service 不直接面对 C 端推荐。

### 4. 降级定类：全链路 fail-open（推荐是纯旁路）

| 环节 | 未启用/失败行为 | 理由 |
| --- | --- | --- |
| I47 摄取 | ES/Embedding 未启用 → 返回 `0`；Feign 降级 → 哨兵 `-1` | 推荐摄取是旁路（对齐知识库 I37 哨兵口径），商品主流程零感知 |
| I48 检索 | ES 关闭/自身无向量 → 空列表；Feign 降级 → 空列表 | 推荐缺失 = 体验损失（非资损），商品页不可被推荐拖垮——与 I43/I44 fail-closed 刻意不同（定价是资金事实，推荐不是） |
| P27 组装 | 命中商品已下架/删除 → 剔除；最终不足 size → 按实际数量返回 | 宁缺毋滥，不出坏卡 |

### 5. 冷启动口径

未摄取的商品（历史存量/ES 重建前）无向量 → I48 对其返回空列表，P27 相应为空——**推荐位可空**是 Phase 1 接受的产品形态；批量补向量走 reindex 全量重建（agent 对齐 reindex 既有 embedding 生成方式）。

## 备选方案

| 方案 | 优点 | 缺点 |
| :--- | :--- | :--- |
| 复用知识库 `knowledge_vectors` 索引装商品 chunk | 零新索引 | 商品与知识 chunk 混检，域污染；商品短文本切块无意义 |
| 「看了又看」共现统计先行 | 行为信号强 | 需浏览数据积累 + 离线聚合 job，冷启动为空，Phase 1 不值 |
| search 直接返回完整商品卡（读共享库） | 少一次组装 | 越界消费 item 域数据 + ES 滞后直接暴露给 C 端 |
| I47/I48 fail-closed | 向量数据强一致 | 商品保存/详情被推荐旁路拖垮，本末倒置 |
| **单向量 + 幂等覆盖写 + item 组装 + 全链路 fail-open（采纳）** | 增量最小、主流程零风险、展示新鲜 | 单向量语义粒度粗于多 chunk；冷启动推荐位可空 |

## 后果

**正面**

- 向量管道完成从「知识库专用」到「通用底座」的第一步复用（T14 投入的杠杆兑现）；
- 商品详情页获得相似推荐能力，零 MySQL schema 变更（向量在 ES）；
- 商品日常增量进 ES 的缺口顺带补齐（reindex 从「唯一手段」降级为「全量兜底」）。

**负面 / 风险**

- **冷启动推荐位可空**（存量商品需 reindex 或编辑触发摄取才有向量）；
- 单向量对长描述的语义压缩有损（Phase 1 接受，多向量/多路召回 Phase 2）；
- ES 文档与 MySQL 之间存在摄取窗口（编辑后瞬短不一致，仅影响推荐文案新鲜度，不影响交易）；
- Embedding 调用为同步 HTTP（摄取在事务外旁路，量级为单商品一次，可接受）。

## 日期

2026-10-05

## 参考

- `Implementation/back-end/search-service/src/main/java/org/icedamericanomall/search/`（`ProductVectorManager`、I47/I48、`ProductSearchGateway` 向量写入扩展）
- `Implementation/back-end/item-service/src/main/java/org/icedamericanomall/item/`（P27 + 摄取钩子）
- `Implementation/back-end/ia-api/`（`ProductVectorClient` fail-open / `ProductVectorUpsertCO` / `ProductVectorHitCO`）
- ADR-038（四层）、ADR-011（降级三分法——旁路 fail-open 定类）、ADR-042（旁路 fail-open 先例）、T14 知识库向量链路（05 §4.14.10）
