# ADR-048: 内容社区落位 item 域 —— 种草笔记与 review 同属商品 UGC，鉴定体系 Phase 2 立项

## 背景

P2 收尾两项中的「内容社区」（得物式种草笔记）需要落位决策：新建 `community-service`（第 19 个服务）还是落进现有域。同时「鉴定」作为得物式差异化能力需要明确 Phase 1 边界——真实鉴定是独立业务流程（寄送/质检/证书），远超一个标签字段。

同期交付的 F11 店铺装修无架构争议（merchant 域 1:1 扩展），其 Phase 1 取舍随本 ADR 一并登记。

## 决策（2026-10-05）

### 1. 种草笔记落位 item-service（不新建 community-service）

- **理由**：笔记本质是「围绕商品的 UGC 内容」，与既有 review（评价）同域同性质；笔记详情的关联商品卡需要商品域数据（名称/主图/最低价/在售过滤）——落位 item 同库直查零跨服务，新建服务则必须为「批量商品摘要」新增跨服务契约（或让 community 直读商品表，破坏边界）。
- **拆分边界预留**：`domain/model/content/` 独立聚合分包（ADR-025），未来若内容生态膨胀（评论/话题/关注流）可按包边界拆出 community-service——包结构即未来服务边界。
- **Phase 1 能力边界**：纯文本正文 + 图集 ≤9（不做富文本/视频）、关联商品 ≤5（发布时校验存在且在售）、点赞（uk 幂等 + 原子计数）、作者软删；**不做**笔记评论、关注流、推荐分发、草稿箱。

### 2. 鉴定体系 Phase 2 立项（Phase 1 不做假鉴定）

得物式「鉴定」是寄送-质检-证书的**独立业务流程**（逆向物流 + 质检作业 + 防伪凭证），Phase 1 若只做一个"已鉴定"静态标签等于造假承诺。PRD 明确：鉴定体系（含是否引入第三方质检合作）Phase 2 独立 PRD 立项，本期笔记不携带任何鉴定声明。

### 3. 数据模型与关键口径

- `note`（`idx_status_time` 流查询）/ `note_product`（`uk_note_product`，≤5）/ `note_like`（`uk_note_user` 点赞幂等）——按 ADR-045 口径直接入 Initialize.sql 快照。
- **点赞计数**：`like_count` 冗余列原子维护——点赞=插入 like 成功后条件 +1，取消=删除成功后 `GREATEST(0, like_count-1)`；uk 保证重复点赞空操作。
- **删除语义**：作者软删（status=2），流与详情不可见；物理删除需评审（硬约束），留待数据治理统一处理。

### 4. F11 店铺装修 Phase 1 取舍（随本 ADR 登记）

- `shop_decoration` 与 shop 1:1（merchant 域）：公告 + 轮播 ≤5 + 推荐商品位 ≤8，JSON 以字符串列存储（领域层校验条数/格式，不引入 TypeHandler）。
- **`hot_product_ids` Phase 1 不校验商品归属**（商家端选品时的跨服务校验留 Phase 2）——展示端（店铺页商品卡）按需过滤失效/非在售 id；装修数据 60s 内可见性无承诺（直读无缓存）。

## 备选方案

| 方案 | 优点 | 缺点 |
| :--- | :--- | :--- |
| 新建 community-service | 域独立、团队边界清晰 | 第 19 个服务的全套成本（网关/注册/契约），且商品摘要卡立即产生跨服务依赖 |
| 笔记落 item（采纳） | 商品卡零跨服务、与 review 同构、包边界预留拆分 | item 域持续变胖（UGX 与交易商品域混布）——拆分边界已预留 |
| Phase 1 做富文本/视频 | 体验对齐得物 | 存储与安全（XSS/视频转码）成本大增，Phase 1 纯文本+图集够验证 PMF |
| Phase 1 做「已鉴定」静态标签 | 差异化卖点立现 | 无鉴定流程的鉴定标签=虚假承诺，资信风险 |
| hotProductIds 保存时校验商品归属 | 数据严谨 | merchant→item 跨服务校验（装修低频写，收益低）；展示端过滤已兜底 |
| **落位 item + Phase 1 最小闭环（采纳）** | 增量最小、同库装配、边界预留 | item 域变胖；鉴定/富文本/评论全部 Phase 2 |

## 后果

**正面**

- 内容社区最小闭环上线（发布/流/详情/点赞/软删），商品卡同库装配零跨服务；
- 向量管道（ADR-046）未来可直接复用于笔记内容理解（笔记向量化 → 相似笔记/内容推荐）；
- 店铺装修让商家端首次具备店铺页自定义能力（与 SH1 店铺页闭环）。

**负面 / 风险**

- item 域职责加重（交易商品 + UGC）——按包边界监控，膨胀即触发拆分评估；
- 鉴定缺位期间社区内容无得物式核心差异化（PRD 已明示 Phase 2 立项）；
- `hot_product_ids` 引用的商品下架后店铺页推荐位出现空洞（展示端过滤，无自动补位）；
- 笔记点赞计数为最终一致（原子 UPDATE 无分布式锁，并发下计数精确性依赖单行原子性——MySQL 单行 UPDATE 即精确）。

## 日期

2026-10-05

## 参考

- `Implementation/back-end/merchant-service/src/main/java/org/icedamericanomall/merchant/`（shop_decoration 域 + S1c/S1d）
- `Implementation/back-end/item-service/src/main/java/org/icedamericanomall/item/domain/model/content/`（note 聚合族）+ `adapter/web/NoteController.java`（P28-P33）
- `gateway-service/application.yml`（`/api/notes/**` 路由 + GET 白名单）
- ADR-019（shop 1:1 归属）、ADR-025（聚合分包=未来拆分边界）、ADR-045（结构直接改快照）、ADR-046（向量管道——笔记向量化复用预留）
