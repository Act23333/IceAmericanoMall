# ADR-064: SPU 名称字段线上键统一为 `spu_name` —— 读写两侧同批收口、I70 内网出口与「名称字段」机械门禁

## 状态

**已落码（2026-10-18，批次「甲a」契约先行 +「甲b」代码落地）**。
契约侧见 `project-docs/contract/05-API-Specification.md` §1.8④ / §4.14.5.2（I70）/ §十一 11.2 O2；
代码侧 = `item-service` / `search-service` / `ia-api` / `ai-service` 四个模块 + 前端 `apps/web` / `packages`。

本 ADR 是 [ADR-056](./056-wire-naming-snake-case.md)（线上命名 snake_case）、
[ADR-058](./058-vo-identifier-semantics.md)（VO 标识语义）、
[ADR-061](./061-order-item-business-key-snapshot-and-price-compare.md)（`product_name` → `spu_name` 的首批收口）、
[ADR-062](./062-external-identifier-and-addressing.md)（对外标识与寻址口径）的**延伸**：
前面几条把「标识」和「命名风格」定清楚了，本条补的是**同一个业务概念的线上键唯一性**，
以及它背后那个「没有任何 CI 闸能看见」的盲区。

> ⚠ **授权边界说明（登记用，同 ADR-062 体例）**：按 [ADR-053](./053-doc-ownership-by-nature-and-two-layer-standards.md) 分权，
> 决策类 ADR 归该域决策者（本条属后端/契约口径 → java-architect）。本条的**裁决内容由用户（项目方）拍定**
> （「甲」裁决 + 写侧 O2 裁决 + 「显式新增 internal 端点，立项迁入内网」），非作者自行判断。

---

## 背景

### 1. 同一个概念，两个线上键

裁决前，「某个 SPU 的名字」在报文里有**两种键名**：

| 载体 | 裁决前线上键 | 成因 |
| :-- | :-- | :-- |
| `SpuVO`（P1/P2/P38）、`RecommendSpuVO`（P27/P35）、`NoteDetailVO.spus[]`（P30）、`SpuSearchVO`（SE1） | **`name`** | 字段名就是 `name`，无 `@JsonProperty`，全局 `SNAKE_CASE` 下线上键即 `name` |
| `SkuVO` / `CartItemVO` / `FavoriteVO` / `FlashSaleVO` / `OrderItemVO` | **`spu_name`** | ADR-061 首批收口（回填旁路 VO） |

前端按其中任一键取值，都会在另一侧**静默变 `undefined`** —— Jackson 的 `SNAKE_CASE` 与
Spring Boot 默认 `FAIL_ON_UNKNOWN_PROPERTIES=false` 组合下，**未知键被忽略、缺失字段落 `null`**，
不抛错、不告警，只是页面某处空白。项目会话中多数「数据不显示」缺陷即此成因（典型：商品名、
秒杀商品名、订单项商品名三处各读一套键）。

### 2. 现有 6 道 CI 闸的真空（本条的真正动因）

仓库当时有 6 道机械闸：

| 闸 | 检查面 |
| :-- | :-- |
| `check-id-invariants.sh` 检查 1–3 | UUID 生成点 / BINARY(16) 参数化查询 / SQL 字面量 |
| 同脚本 检查 4 | 线格式**标识**字段的**类型**（`spuId` 等必须 `String`） |
| 同脚本 检查 5–6 | 外部路径变量口径 / 适配层无解析调用 |
| `check-frontend-api-paths.mjs` · `crosscheck-api-paths.mjs` | **路径**漂移 |
| `check-error-codes.mjs` | **错误码**一致 |
| `check-query-param-naming.mjs` | **查询参数**命名 |

**没有任何一道看“字段名/键名”的一致性。** 于是「后端改名、前端没跟」（或反之）在 CI 上是
**完全不可见**的：编译通过、单测通过、路径对得上、错误码对得上，线上只是「某块空白」。
这正是本批把「两端必须同一次改动」写成纪律、并新增第 7 道闸的原因。

### 3. `SpuDetailClient` 服务间调用却走公开契约

`ia-api` 的 `SpuDetailClient`（唯一调用方 `ai-service` `FeignSpuDetailAdapter`，活链路）
当时是 `@FeignClient(path = "/api/item/spu")` —— **服务间调用走公开契约**：
过网关路径语义、解 `Result` 信封、依赖公开面的错误语义。这是
[ADR-062](./062-external-identifier-and-addressing.md) §后果 **E7** 登记的待根治项，
根治需要 item 域暴露内部端点，而「新增端点 → `codeOnly` +1」按 ADR-062 §决策 4 属**显式决策**，
须单独立项。本批即该立项。

---

## 决策

### 决策 1：读出侧「某个 SPU 的名字」线上键一律 `spu_name`（7 个报文载体）

| 模块 | 文件 | 改前 | 改后（Java 字段 → 线上键） |
| :-- | :-- | :-- | :-- |
| item | `adapter/response/SpuVO.java` | `name` → `name` | `spuName` → `spu_name` |
| item | `adapter/response/RecommendSpuVO.java` | `name` | `spuName` → `spu_name` |
| item | `adapter/response/NoteDetailVO.java`（内嵌 `SpuSummaryVO`） | `name` | `spuName` → `spu_name` |
| search | `adapter/response/SpuSearchVO.java` | `name` | `spuName` → `spu_name` |
| ia-api | `dto/SpuDetailCO.java`（record 组件） | `name` | `spuName` → `spu_name` |
| ia-api | `dto/SpuSearchItemCO.java` | `name` | `spuName` → `spu_name` |
| ia-api | `dto/SpuVectorUpsertCO.java` | `name` | `spuName` → `spu_name` |

**内部链路必须同批**：`SpuDetailCO`/`SpuSearchItemCO`/`SpuVectorUpsertCO` 与各自的发送端/接收端
（`SpuVO`、item 检索出口、`search-service` 的 `SpuVectorUpsertReq`）在**同一 PR** 内改完 ——
否则 Feign 解码只静默落 `null`，不抛错。

### 决策 2：写侧一并收口（O2 裁决）—— 同一实体只许一个名字

`CreateSpuReq.name` / `UpdateSpuReq.name`（P3 发布 / P4 编辑）→ `spuName`（`spu_name`）。

**理由**：写侧只有一个键名，确实不构成「两个键名并存」的静默失效面 —— 留着它在**今天**无害。
但留着会形成**第三种口径**（读 `spu_name` / 写 `name`），下一个照 §1.8④ 强读法办事的人必然会
改错一侧（而且改错后仍然「能跑」，只是商家改名静默失效）。实测前端零调用 P3/P4，BREAKING 面仅商家端，
此刻收口代价最低。**「两者都写定」优于「悬空待裁决」** —— 悬空迟早被误读成「下一批该改」。

### 决策 3：`name` 在其他命名空间**一字不动**，映射显式化

| 保留 `name` 的位置 | 为什么不改 |
| :-- | :-- |
| 域聚合 `Spu.name`、PO `SpuPO.name`、DB 列 `spu.name` | 不是报文；改它要动表结构，收益为零 |
| ES 索引字段 `SpuDocument.name`（`index=products`） | 不是报文键；改名会孤立既有索引与 reindex 产物 |
| `search-service` 领域读模型 `SpuSearchHit.name` | 领域命名空间；与 ES 文档同源，不出报文 |
| `ai-service` `SpuDetailInfo.name` | 领域 DTO；只经 `SpuDetailTool` 渲染成文本进 LLM 上下文 |
| 别的实体自己的 `name`（`CategoryVO`/`CouponVO`/`PromotionTagVO`/`RoleVO`/`CurrencyVO`/`SearchFacetsVO.brands[]` …） | **改它们才是制造歧义**（契约 §1.8④「不改清单」14 类逐条实测） |
| `HistoryVO.productName` + `@JsonProperty("product_name")` | 全平台**唯一**登记例外（§1.9④），前端已按此键消费；**勿顺手统一** |

因为「领域名 ≠ 线上名」，跨层映射**必须显式**（MapStruct `@Mapping(source = "name", target = "spuName")`）——
不能依赖同名自动匹配的巧合。同名巧合正是本批要消灭的失效面；靠巧合成立的映射，
在下次改名时会**静默失效且不报错**（`unmappedTargetPolicy=ERROR` 能挡住「没映射」，挡不住「映射对了但语义变了」）。

### 决策 4：新增 I70 内部端点，`SpuDetailClient` 迁内网

- `item-service` 新增 `adapter/internal/InternalItemSpuController`：
  `@RequestMapping("/internal/item/spu")` + `@GetMapping("/{spuId}")`，返回**裸 `SpuDetailCO`**
  （不包 `Result`，ADR-004）。`spuId` 形态校验走 `IdGenerator.requireWire`（非法 → 400 `PARAM_ERROR(1101)`），
  不存在/已删除 → 404 `NO_RESOURCE_FOUND(1103)`。
- **装配不得另起一套**：控制器只调 `SpuManager#getSpuDetailCO`，后者直调 `getSpuDetail(String)`
  （P2/P38 同一装配源）后做纯投影。两套装配必然漂移，而契约要求 P2 / P38 / I70 三者同形。
- `ia-api` `SpuDetailClient`：`path` → `/internal/item/spu`，返回 `Result<SpuDetailCO>` → **裸 `SpuDetailCO`**；
  同步 `SpuDetailClientFallback`（**fail-closed 语义不变**）、`ai-service` `FeignSpuDetailAdapter`。
  `SpuDetailPort` / `SpuDetailTool` **零改动**（承载层搬家不该穿透领域层，这正是端口的分内事）。

### 决策 5：新增「名称字段」机械门禁（`check-id-invariants.sh` 检查 7）

| 断言 | 内容 |
| :-- | :-- |
| A. 命中面 | 线格式面（`adapter/{response,request}` + `ia-api dto`）内，**文件名或其中嵌套类型名含 `Spu`** 的类 = 「SPU 载体」 |
| B. 旧键禁令 | 载体类内不得再出现名字字段 `name` / `productName`（POJO 形态、record 组件形态都抓） |
| C. 载体清单齐全 | 10 个必检载体逐个点名（`SpuVO`/`RecommendSpuVO`/`NoteDetailVO`/`SpuSearchVO`/`SpuVectorUpsertReq`/`SpuDetailCO`/`SpuSearchItemCO`/`SpuVectorUpsertCO`/`CreateSpuReq`/`UpdateSpuReq`）—— 文件缺失或未声明 `spuName` 即失败 |
| D. 白名单陈旧即失败 | 唯一例外 `HistoryVO.productName` + `@JsonProperty("product_name")` 必须**仍然成立**；被改名/删注解即红 |
| E. 例外不得扩散 | 除 `HistoryVO` 外任何 `@JsonProperty("product_name")` 一律失败 |
| F. 命中面自证 | 载体类数为 0 即失败（防「零匹配却报 OK」的假绿，同检查 4 的自证纪律） |

**为什么「只断言有 `spu_name`」不够**：真正的坑是**两个键并存**（旧键不删 → 前端读哪套都能「有时有值」），
所以 B 是负向断言。**为什么需要 C**：只扫「已出现的字段」无法发现「某个载体整段被删/改名」，
所以清单本身也要被断言（宁可门禁点名，也不要它静默放宽）。

### 决策 6：两端同一次改动 + 往返护栏

前端（`packages/api/types` + `apps/web` + `packages/ui`）与后端在**同一批**改完；
并新增三条机械护栏：

1. `SpuDetailWireShapeTest`：P2 顶层键**含 `spu_name`、不含 `name`**（顶层断言，不是「键集含」——
   `SkuVO` 早就有 `spu_name`，只断言键集会掩盖 `SpuVO` 自身漏改）；
2. `SpuNameWireKeyTest`（item / search 各一份）：P2/P27/P30/P35 与 SE1/I47 的键为 `spu_name`，
   且旧键 `name` **不绑定**；
3. `IdWireRoundTripTest` 新增 `SpuVO ⇄ SpuDetailCO` 往返：`spuName` 解码后**必须非空** ——
   这是「两端同 PR」的护栏：跨模块、跨服务边界的一侧改名，编译期与静态扫描都可能各自「合规」，
   只有把**发送端报文喂给接收端**才会暴露。

---

## 备选方案

| 方案 | 优点 | 缺点 | 结论 |
| :-- | :-- | :-- | :-- |
| A. 读出侧统一 `spu_name`，写侧长期保留 `name`（甲a 原始建议） | BREAKING 面最小；改动量少一处 | 形成「读 `spu_name` / 写 `name`」第三种口径；下一个人照强读法改必错一侧 | ❌ 否决（O2 裁决） |
| B. 读出侧 + 写侧同批收口 | 同一实体一个名字；口径唯一 | 扩 BREAKING 到商家写接口（实测前端零调用） | ✅ **采纳** |
| C. 前端做兼容读取（`p.spu_name ?? p.name`） | 不用改后端、无 BREAKING | 把缺陷搬进前端且**永久化双键**：旧键永不删除、新缺陷永不暴露；同时让「字段级一致性」失去可断言性 | ❌ 否决（掩盖问题） |
| D. 前端 TS 类型上做「两种都给」的宽松联合 | 编译期全绿 | 同上，且让 tsc 失去捕获力（本批 tsc 变异验证能一次性列出 12 处读取点，正是因为它严格） | ❌ 否决 |
| E. I70 复用 I65（`/internal/item/search/detail/{spuId}`）承载商品详情 | 不新增端点，`codeOnly` 不变 | 把「检索卡片」与「详情装配」混成一个出口：字段面不同（I65 无 `attributes`/`images`/`status` 语义）、职责不同；且需要给 I65 加字段 → 改一个已稳定的内部契约 | ❌ 否决（显式新增更诚实：ADR-062 §决策 4「新增端点是显式决策」） |
| F. `SpuDetailClient` 继续走公开 P2（不动承载层） | 零端点变化 | 与 §1.9② / `09-constraints` §三·六「服务间调用走内网」长期相悖；ADR-062 §E7 永远挂着 | ❌ 否决（用户裁决「立项迁入内网」） |

---

## 后果

### 正面

- 「某个 SPU 的名字」在全平台**只剩一个线上键**；前端四个来源（列表/详情/搜索/推荐卡）读同一个键，
  不再有「按 A 键取值在 B 处静默 `undefined`」这一类故障面。
- **CI 第一次能看见字段级不一致**：检查 7 把本类问题的复发成本从「线上空白、事后排查」压到「提交即红」。
- 服务间调用回到内网（不包信封），公开面的 `Result` 语义不再被集群内链路复用 —— 两套语义各自干净。
- `SpuDetailCO` 与 `SpuVO` 有了跨模块往返断言，「两端同 PR」从纪律变成**可执行的检查**。

### 负面 / 代价（诚实登记）

- **BREAKING（无灰度）**：`SpuVO`/`RecommendSpuVO`/`NoteDetailVO.spus[]`/`SpuSearchVO` 的 `name` 键消失，
  `CreateSpuReq`/`UpdateSpuReq`/`SpuVectorUpsertReq` 的入参键改 `spu_name`。任何**仓外**消费者（本仓当前无）
  或**未跟改的仓内客户端**会静默失值。仓内已全量跟改并有 tsc/门禁护航。
- **交付时的现实偏差**：`extract-endpoints.mjs` 报 **289 / 0 / 43**（不是任务书写的 288 / 0 / 43）。
  原因是 I70 从「契约有、代码无」转为 `matched`：甲a 时 `332 = 288 + 44`，甲b 后 `332 = 289 + 43`。
  要求 `288 / 0 / 43`（和为 331）等于把 I70 从契约里删掉 —— 与「I70 落码」本身矛盾，
  故按「`docOnly` 44 → 43」这一条（可判定项）验收。
- **`SpuDetailCO.images` / `attributes` 仍为 `null`**：它们是 `spu` 表历史列，**不在 P2 装配的字段面内**
  （`SpuVO` 无对应字段）。迁移前经 Feign 读公开 P2 时同样取不到，本批**刻意不新查一次库**：
  那会让内部出口的数据面与 P2 分叉，正是「两套装配」的起点。AI 工具侧 `属性:` 一栏因此在
  本批前后都是 `null`（无回归），但**契约把这两字段列为 CO 的形状**，与「有值」不是同一件事。

### 风险（需后续盯）

- **R1｜门禁的命中面是「名字形态」，不是「语义」**：检查 7 判「SPU 载体」靠文件名/嵌套类名含 `Spu`。
  若未来出现一个**不含 `Spu` 字样**却承载 SPU 名的报文类（如 `GoodsCardVO`），旧键不会被拦。
  缓解：路径/命名纪律（ADR-059 词汇消歧）+ 清单 C 点名；发现此类命名时**同时**补 `Spu` 词根或加进清单。
- **R2｜写侧 `spu_name` 的商家端尚未对接**：`apps/seller` 当前无 P3/P4 调用点（前端零调用是本次
  BREAKING 面小的依据）。一旦商家端接入发布/编辑，前端必须直接用 `spu_name`
  —— 别再引入「后端叫 spu_name、前端发 name」的新一轮错位。
- **R3｜`HistoryVO.product_name` 是唯一例外，且是**故意**的**：它是「防下一个人顺手统一」的反向标记。
  检查 7 的 D 项让这个例外**必须持续成立**（被改掉即红），避免豁免悄悄失效。
- **R4｜小程序仍读 `name`（已知未修）**：`apps/miniprogram` 按用户明确指令**不在本批范围**，
  其卡片仍读 `name`/旧路径（`/api/item/product/**`、`/api/search/product`，`crosscheck-api-paths.mjs`
  报 3 处未匹配）。**该端在 P1/P2/SE1 上会显示空商品名**，属**已知未修**，不是本批引入。
- **R5｜404 经 Feign 降级成 `SYSTEM_BUSY` 的既有语义**：I70 与公开 P2 一样，商品不存在返回 404，
  Feign 默认对非 2xx 抛错 → 降级工厂 fail-closed 抛 `SYSTEM_BUSY`（"商品服务暂不可用"），
  AI 工具因此把「商品不存在」说成「服务不可用」。这是**迁移前就存在**的语义（P2 已是真 404），
  本批不改（改它要动 Fail-closed vs 404 透传的判定，属独立决策）。

---

## 参考

- 契约：`project-docs/contract/05-API-Specification.md` §1.8④（名称键规则 + BREAKING 面 + 不改清单）、
  §4.14.5.2（I70）、§十一 11.2 **O2**（写侧裁决）、§十二 12.2（挂账）。
- 前序 ADR：[056](./056-wire-naming-snake-case.md)（snake_case 三类载体）、
  [058](./058-vo-identifier-semantics.md)（VO 标识语义与 MapStruct 显式 ignore 纪律）、
  [059](./059-spu-sku-vocabulary-disambiguation.md)（SPU/SKU 词汇消歧）、
  [061](./061-order-item-business-key-snapshot-and-price-compare.md)（`product_name` → `spu_name` 首批 + ADR-057 读模型）、
  [062](./062-external-identifier-and-addressing.md)（对外标识与寻址口径；§后果 E7 即本批 I70 的立项依据）。
- 守卫落点：`Implementation/back-end/scripts/check-id-invariants.sh` 检查 7；
  `SpuDetailWireShapeTest` / `SpuNameWireKeyTest`（item、search 各一）/ `InternalItemSpuControllerTest` /
  `IdWireRoundTripTest`（`SpuVO ⇄ SpuDetailCO` 往返）。

## 日期

2026-10-18
