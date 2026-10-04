# ADR-025: 领域模型按聚合分包 + 应用层 Manager 按用例族收敛粒度

## 背景

四层 DDD 服务（authorization / user / merchant / wallet，以及并行任务负责的 verification / notification / marketing / ai）在 2026-10 边界重构后，`domain/model` 与 `app` 两处的**组织粒度不统一**：

1. **`domain/model` 平铺与分包混存**：
   - authorization：`model/` 根下平铺 6 个类型（`UserAuth` / `LoginSpec` / `EmailAddress` / `WechatSession` / `RiskAssessment` / `RefreshTokenInfo`），同时已有 `model/rbac/` 子包 —— 同一个目录下两种组织方式并存，新类型放哪没有唯一答案；
   - user：`model/` 根下平铺 `User` / `Address` / `Favorite` **三个互不相干的聚合根**（Favorite 为 T7 新增），"一个聚合一个包"的聚合边界（也是未来服务拆分边界）无法从目录上读出；
   - merchant：`model/` 根下平铺 `Merchant` / `Shop` 两个聚合根（ADR-019 拆出的 1:N），`ShopStatusEnum` 落在 `domain/enums/` 而它只被 Shop 聚合使用；
   - wallet：`domain/model` 已空（死实体 `WalletAccount` 此前已删，余额不变式由条件 UPDATE 承载）。
2. **`app` 层 Manager 粒度不一**：user 的 `UserManager` 一个类里混装了**三类用例族** —— 用户信息读取（`getByUserId` / `countUsers`，前台 `/api/user/info` 与内部端点共用）、管理后台运营（`pageUsers` / `updateStatus` / `setUserRole`）、跨服务幂等开通（`getOrCreateUser`，ADR-006 `/internal/user/getOrCreate`）。而 favorite / history / address 已按域分包、user 的 `UserInfoManager` 已是单一职责 —— 同类对象粒度不同。

本决策是**纯结构归位**：HTTP 契约（URL / JSON 字段名 / 错误码）、行为、事务语义全部不变。

## 决策

### 1）`domain/model` 按聚合分包，一个聚合一个包

- `domain/model/<聚合名小写>/` 收纳该聚合的聚合根 / 实体 / 值对象；包边界即未来的服务拆分边界。
- **单聚合平铺**：服务 `domain/model` 下只有 1 个聚合时保持平铺，不强行嵌套（防过度分包）；**0 个聚合则完全不动**（wallet）。
- 非聚合归属、被多个能力族/层共享的领域值对象（如 authorization 的 `LoginSpec` / `EmailAddress` / `WechatSession` / `RiskAssessment` / `RefreshTokenInfo`：登录策略族、风控族、会话族共用，且被 app / infrastructure 层消费）**保持平铺于 `domain/model/` 根**，不为单个类造包。
- `domain/ability/*`（策略族）、`domain/service/`（跨聚合领域服务）、`domain/repository/`、`domain/port/`、`domain/gateway/` 位置不变。

**枚举**：默认留 `domain/enums/`（服务级共享词汇，避免"同一服务的枚举去哪找"没有唯一答案）。**唯一例外**：merchant 的 `ShopStatusEnum` 下沉至 `domain/model/shop/` —— 它只被 Shop 聚合及其持久化（`ShopPO` / `ShopConverter`）引用，main 代码中零 app / adapter 引用，属聚合私有状态机。`MerchantStatusEnum`（被 `MerchantManager` 引用）、auth 的 `UserAuthStatusEnum` / `CredentialTypeEnum` / `PlatformEnum` / `RiskLevel` / `RiskTag` / `VerificationChannelEnum`、user 的 `GenderEnum` / `MemberLevelEnum` / `UserStatusEnum`（被 app / adapter 引用或跨用例族共享）一律保留在 `domain/enums/`。

### 2）`app` 层 Manager 按聚合/用例族收敛，**沿用 `XxxManager` 命名，不做 CmdExe / QryExe 化**

- 每个 Manager 只归属**一个聚合或一个用例族**；>300 行的类必须拆；已经单一的类不动。
- 新拆出的类命名 = `<用例族>Manager`（如 `UserAdminManager` / `UserProvisionManager`），**不改成 `XxxCmdExe` / `XxxQryExe`**：本代码库的 app 层从未采用 COLA 的 Exe 形态，改名会带来无收益的 churn（Controller、测试、文档同步面扩大），且**拆分收益来自"职责单一"，与类名后缀无关**。粒度单一即为达标。
- Controller 注入面随拆分调整，**URL 与报文不变**；`@Transactional(rollbackFor = Exception.class)` 随原方法迁移，不丢失、不新增。

## 影响服务清单

| 服务 | `domain/model` | `app` | 结论 |
| :--- | :--- | :--- | :--- |
| authorization-service | `UserAuth` → `model/userauth/`（与既有 `model/rbac/` 对齐，两聚合一并嵌套）；5 个共享值对象留平铺 | `AuthManager`（253 行，认证用例单一门面）与 `app/rbac/*` 四个单一 Manager 均**不动** | 只做 model 归位 |
| user-service | `User` → `model/user/`、`Address` → `model/address/`、`Favorite` → `model/favorite/`（三聚合嵌套）；测试 `UserTest` / `AddressTest` 同步随迁 | `UserManager` **拆分**为 `UserQueryManager`（读取）/ `UserAdminManager`（管理端运营）/ `UserProvisionManager`（幂等开通）；其余 Manager 已单一，不动 | model 归位 + Manager 拆分 |
| merchant-service | `Merchant` → `model/merchant/`、`Shop` → `model/shop/`、`ShopStatusEnum` → `model/shop/` | `MerchantManager`（115 行，商家生命周期单一家族）、`ShopManager`、`StoreFollowManager` 均**不动** | 只做 model 归位 |
| wallet-service | `domain/model` 为空（不变式在仓储条件 UPDATE），**不动** | `WalletManager`（55 行，余额单一用例族）**不动** | **零改动** |

## 备选方案

| 方案 | 优点 | 缺点 |
| :--- | :--- | :--- |
| 维持现状（`domain/model` 平铺与分包混存、`UserManager` 多职责） | 零改动、零风险 | "新类型/新方法放哪"无唯一解；聚合边界（未来拆分线）无法从目录读出；同层同类对象粒度不一致 |
| **按聚合分包（本决策）+ app 按用例族拆** | 目录即聚合边界；新增类型/用例有唯一落点；与 ADR-023 的"按受众人群分包"正交互补 | 一次性机械搬迁，需同步 import / 测试包 |
| `domain/model` 一律平铺（连 `rbac/` 拆回根） | 绝对一致 | 抹掉已有的聚合边界表达，与 ADR-019/018 的聚合划分相悖 |
| 全部枚举随聚合下沉 | 归属最彻底 | 枚举被 app/adapter 广泛引用，"一半在 enums、一半在聚合"更乱；churn 无收益 |
| app 层引入 CmdExe / QryExe（COLA 完整形态） | 与 COLA 模板字面一致 | 全库 app 层无此形态；Controller/测试/文档同步面大，收益仅为改名 |
| 把 infrastructure PO 升级为领域聚合后重排 | 归属最"正确" | 是**另一个量级**的重构（改持久化模型与不变式载体），超出结构收口范围，明确不做 |

## 后果

**正面**

- `domain/model/<聚合>/` 使聚合边界可直接从目录读出，`app/<用例族>Manager` 使用例边界可直接从类名读出，新代码有唯一落点；
- 拆出 `UserProvisionManager` 后，"内网幂等开通"（ADR-006 契约）与"管理后台运营"（ADR-018 委托授权域）不再共享同一个类，越权面与影响面在类型上被隔离；
- 迁移期不引入框架：`domain/model` 仍零框架依赖（无 Spring / MyBatis / Feign import），ADR-005 的领域纯度不被破坏。

**负面 / 风险**

- 机械搬迁必须同步：`import` 语句、测试包声明（ADR-023 §4 测试包与主包对齐）、跨包 javadoc `{@link}` 解析（merchant `Merchant` ↔ `Shop` 已在包间加显式 import 并补 FQN 链接）；
- 与并行重构的其他服务存在"同一文件两处改"的合并冲突风险 —— 故本次限定在授权组负责的四个服务内执行。

## 明确排除项

1. **不抽新聚合、不升级既有 `*PO` 为领域聚合**（只对**既有**类型归位）；
2. **不推翻 ADR-023**：adapter 层的 `web/` / `admin/` / `internal/` / `request/` / `response/` / `convert` 结构与 `XxxReq` 命名保持不动（本次仅因类型迁移而改其 import）；
3. **轻量 COLA 服务（item / cart / trade / pay / logistics / search）不在范围**：它们不采用 `domain/model` 四层形态，不做跨形态迁移；
4. **不引入 CmdExe / QryExe**（见决策 2）；
5. **verification / notification / marketing / ai 由并行任务收口**，不在本次改动面内。

## 日期

2026-10-03

## 参考

- `project-docs/adr/023-adapter-layer-structure-and-request-object-naming.md`（adapter 层结构基线，本次不推翻）
- `project-docs/adr/019-merchant-shop-1n-split.md`（merchant / shop 聚合拆分来源）
- `project-docs/adr/018-unified-identity-rbac-ownership.md`（`UserManager` 三类职责的历史来源）
- `project-docs/adr/006-cross-service-userid-semantics.md`（`/internal/user/getOrCreate` 幂等开通契约）
- `project-docs/adr/005-domain-purity-ports-and-eventual-consistency.md`（领域层零框架依赖）
- 涉及服务：authorization / user / merchant / wallet
