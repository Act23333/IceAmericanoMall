# 21 — 前端重构准备方案

> 归属域: frontend | Owner: doc-engineer | 更新触发条件: 契约收敛批次推进 / 前端重构阶段推进时
>
> **日期**：2026-10-14 | **上游依据**：[20-Doc-Code-Consistency-Audit.md](../quality/20-Doc-Code-Consistency-Audit.md)（本文所有结论均可在该文查到取证）
> **定位**：本文不是实施手册（那是 [18-Frontend-Implementation.md](18-Frontend-Implementation.md)），而是**重构的准入清单**——回答「现在能不能开工、开工前必须先定什么、开工后以什么为验收基线」。

---

## 一、现状判定：不是「重构」，是「按契约重建 UI 层 + 重写 API 层」

| 判断项 | 结论 | 依据 |
| --- | --- | --- |
| 现有前端代码量 | **159 个源文件**（`apps/web` 59/5,128 行；`miniprogram` 48/1,977；`admin` 9/93；`seller` 8/85；`mobile` 1/0） | [20 §七](../quality/20-Doc-Code-Consistency-Audit.md) |
| 可复用部分 | `packages/ui`（16 文件/1,012 行）、`apps/web` 的 25 个页面骨架与设计系统落地、`packages/api` 的 15 个 `use-*.ts` hooks 骨架 | 同上 |
| 必须重写部分 | **API 层全部路径**（**9 条调用 / 7 组前缀**指向后端不存在的路径）、错误码映射、类型定义、**购物车数据源**（现为 localStorage，需切服务端） | 本文 §三 |
| `admin` / `seller` | **纯骨架（93 / 85 行）**——不存在「重构」，只有「从零实现」 | 同上 |
| 契约可信度 | 🔴 契约本身含 **13 条标注「已实现」但代码不存在的端点** + **33 条「计划中」混排在端点表内** | [20 §A1/A2](../quality/20-Doc-Code-Consistency-Audit.md) |
| 文档可信度 | 🔴 前端域文档（`15`/`17`/`18`/`16`）全部停在 **V1.0 / 2026-08-02**，而契约已 V5.18.0 | 同上 |

**核心结论**：现有前端是**按「想象中的 API」写的**。它调用的 7 组前缀里，`/api/user/notifications`、`/api/user/balance`、`/api/user/points`、`/api/auth/login`、`/api/auth/login/wechat` **5 组在契约与代码中都不存在**（纯属自造）；`/api/user/sign`、`/api/user/code`、`/api/coupon/available/filter` **3 组只存在于契约里，而契约本身是错的**（见 [20 §A1](../quality/20-Doc-Code-Consistency-Audit.md)）。因此不存在「小步重构现有 API 层」的选项；正确路径是**冻结契约 → 生成类型与客户端 → 重写 API 层 → 迁移页面**。

---

## 二、准入条件：契约必须先收敛（4 项，骨架级前置）

> 这 3 项不做完，任何前端开工都会重演一遍「照着错文档写错代码」。对应 [20 §八](../quality/20-Doc-Code-Consistency-Audit.md) 修复清单 #1–#4。

### 2.1 把「计划中」端点显式分区（33 条）——**不是移出**

契约 §四 目前混装 `✅ 已实现` 与 `⚪ 计划中`，头部只给一个未拆分的「端点总数 278」。**前端无法从文档判断某个端点是能调的还是 404。**

契约已被确认为**规格（spec）**，规格本就应包含计划项，因此**不要移出**：

**要求**：§四 的 33 条 `⚪` **保留在端点表内**，但（a）新增「状态」列或按状态分区成表；（b）头部计数拆为「已实现 N / 计划中 M」（实测基准：代码 286 个端点 / 契约表 326 行）；（c）§七 页面映射里混入的 9 条计划端点也加 `⚪` 标注。理由与决策见 [22 §五 D7](../quality/22-API-Spec-BigTech-Conformance-Review.md)。

### 2.2 更正 13 条「标注已实现但代码不存在」的端点

见 [20 §A1](../quality/20-Doc-Code-Consistency-Audit.md) 全表。前端此前若照抄，其中 5 组已在现有代码里踩中（见 §三）。

### 2.3 补录活路径 `POST /api/verification/codes`

它是契约 A10 `POST /api/auth/email/code` 与 U5 `POST /api/user/code` 两条死路径的**唯一活替身**（代码注释明写「承接」），却完全未文档化。**注册/登录/找回密码的验证码发送全靠它**——这是前端登录链路的第一块砖。

### 2.4 修正 §七 页面映射（前端直接照抄的那张表）

契约 §七「前端页面 ↔ API 映射」是前端开发的实际对照表，但逐行校验（[20 §A8](../quality/20-Doc-Code-Consistency-Audit.md)）显示：**148 个引用中 19 个端点不存在**——其中 10 个是 §4 标 ✅ 却无实现的真实缺陷（注册页发码、购物车 `grouped`、结算页券过滤、签到页 2 条、回复模板页 4 条、商家审核页 1 条），另 9 个是未加 `⚪` 标注的计划端点。**§七 未修正前，前端按页面表开发会直接踩 404。**

> 注：§七 :2348 已明文警示 `POST /api/auth/login` 会 404，但现有前端仍在调用它——警示晚于代码。重构时以修正后的 §七 为唯一对照表。

---

## 三、阻断级差异清单与路径映射表（现有前端调用 → 后端真实路径）

> 这张表**就是「适配层清单」**：它列出了现有前端与真实后端之间的全部路径落差。但**不建议据此写兼容适配层**（理由见 §七 第 2 条）——正确用法是作为阶段 1 生成客户端与阶段 3 页面迁移时的**逐条改造清单**。

以下为**实测**（`grep` 前端源码得到的路径 vs 后端 Controller 实测路径）。`前端错` = 契约正确、前端写错；`同错` = 前端与契约都错，以**代码**为准。

| # | 现有前端调用 | 后端真实路径（准） | 契约写法 | 判定 |
| --- | --- | --- | --- | --- |
| 1 | `POST /api/auth/login` | `POST /api/auth/web/login` | ✅ `/api/auth/web/login` | 前端错 |
| 2 | `POST /api/auth/login/wechat` | `POST /api/auth/web/wechat/login` | ✅ `/api/auth/web/wechat/login` | 前端错 |
| 3 | `POST /api/user/code` | **`POST /api/verification/codes`** | ❌ `/api/user/code` | 同错 |
| 4 | `POST /api/user/sign` | **`POST /api/sign`** | ❌ `/api/user/sign` | 同错 |
| 5 | `GET /api/user/sign/status` | **`GET /api/sign/status`** | ❌ `/api/user/sign/status` | 同错 |
| 6 | `GET /api/user/points/balance`<br>`GET /api/user/points/history` | **`GET /api/points/balance`**<br>**`GET /api/points/history`** | ✅ 指向 marketing-service | 前端错 |
| 7 | `GET /api/user/notifications`<br>`PUT /api/user/notifications/{id}/read`<br>`GET /api/user/notifications/unread-count`<br>`PUT /api/user/notifications/read-all` | **`GET /api/notifications`**<br>**`PUT /api/notifications/{id}/read`**<br>**`GET /api/notifications/unread-count`**<br>**`PUT /api/notifications/read-all`** | ✅ `/api/notifications*` | 前端错 |
| 8 | `GET /api/user/balance`<br>`POST /api/user/balance/recharge` | **`GET /api/wallet`**<br>**`POST /api/wallet/recharge`** | ✅ `/api/wallet` | 前端错 |
| 9 | `POST /api/coupon/available/filter` | **无此端点**（用 `GET /api/coupon/available`） | ❌ `/api/coupon/available/filter`（标 ✅ V4.3） | 同错 |

**受影响页面（现有代码已踩中）**：`app/auth/login`、`app/auth/register`、`app/user/sign-in`、`app/user/points`、`app/user/notifications`、`app/user/balance`、`app/shop/checkout`（券过滤）。

此外 `packages/api/error-codes.ts` 映射了 `1004`（**枚举中不存在**，真实流控码为 `1014 FREQUENT_ERROR`）→ 限流提示永远不会命中。

### 3.1 路径之外的阻断项（同样必须先解决）

路径改对还不够——以下是**非路径类**缺陷，第二轮流审计出并已复核（详见 [20 §C2](../quality/20-Doc-Code-Consistency-Audit.md)）：

| # | 缺陷 | 对前端的含义 |
| --- | --- | --- |
| 1 | **购物车存在双真相源**：`apps/web` 的加购/购物车/结算全走 **localStorage 的 Zustand store**（自述 `TODO: Phase 4 API integration`），而 `packages/api/hooks/use-cart.ts` 这套**写对了的服务端购物车 hook 零消费者** | 结算页把 localStorage 的 skuId 当 `cartItemIds` 发给 `POST /api/trade/order`，而 `CreateOrderReq` **无此字段**（只有 `addressId`/`userCouponId`/`remark`/`displayCurrency`），后端读的是**服务端购物车** → **下单必然命中「购物车为空」**。这是现有 Web 端最致命的业务缺陷 |
| 2 | **Web 端当前无法登录**：登录页调用已废弃的 `POST /api/auth/login` 与 `POST /api/user/code` | 认证链路整体不可用；重构的第一步就是修这两条 |
| 3 | **管理端营销功能经网关不可达**：`/api/admin/{flash,promotion,task,group-buy}/**` 无网关路由 → 落入 `/api/admin/**` 兜底 → 404（共 12 个端点） | `apps/admin` 无法管理秒杀/满减满折/任务/拼团，**必须等后端补网关路由后才能联调** |
| 4 | **12 个管理端点无鉴权**（`AdminPay`/`AdminAfterSale`/`AdminFinance`/`AdminLog` 缺 `@PreAuthorize`） | 前端不能依赖「调不动就说明没权限」；且这是必须在管理端上线前修掉的安全问题 |
| 5 | **4 个服务为 `SNAKE_CASE`**（authorization / notification / verification / wallet），其余 11 个为 camelCase | 前端必须按服务区分字段命名：这 4 个服务的**请求体与响应体字段名是 snake_case**（如 `access_token`），其余是 camelCase。**生成客户端时必须按服务覆盖命名策略**，否则一半接口字段全为 `undefined` |
| 6 | **站内信链路失效**：notification-service 收 SNAKE_CASE，而 4 个调用方（item/merchant/pay/trade）发 camelCase | `app/user/notifications` 页面将收不到站内信；这不是前端能修的，需后端统一或给 DTO 加显式 `@JsonProperty` |
| 7 | **人机验证参数口径不符**致登录死循环 1016 | 风控开启后登录页会卡在验证失败；须先定契约方向（见 [20 §九](../quality/20-Doc-Code-Consistency-Audit.md) 决策 8） |
| 8 | **`middleware.ts` 只查 Cookie 存在性**，不校验 JWT 有效性更不校验角色 | `/admin`、`/seller` 路由对**任意已登录用户**开放——管理端/商家端上线前必须补角色守卫 |
| 9 | **API base URL 在 4 处不一致**（`client.ts` / `server.ts` / `next.config.ts` / `Dockerfile`） | 重构时收敛为单一环境变量（`NEXT_PUBLIC_API_URL`），并纳入 turbo 的 `env` 声明 |

> 结论：**「前端重构」的实际范围包含「后端放行面修复」**（第 3/4/6/7 项）。建议把 [20 §八](../quality/20-Doc-Code-Consistency-Audit.md) 修复清单 #0–#4 与前端阶段 0 合并为一个「联调准入批次」。

---

## 四、必须先定的 4 个架构决策

| # | 决策 | 选项 | 影响面 | 建议 |
| --- | --- | --- | --- | --- |
| 1 | **Token 存储** | (a) 维持现状：`access_token` + **7 天 `refresh_token`** 均存 JS 可读 Cookie；(b) `refresh_token` 改 httpOnly + BFF/Route Handler 代理刷新 | 决定整个认证架构与 `middleware.ts` 路由守卫实现 | **(b)**。现状 `packages/api/token.ts` 自注「存在 XSS 暴露面」，且把 7 天有效的 refresh token 暴露给 JS 属高危。走 (b) 需 `apps/web` 增加 Route Handler 承接 `/api/auth/refresh` |
| 2 | **契约 → TS 类型** | (a) 继续手写（现状 24 个类型覆盖 286 个端点）；(b) 从 Knife4j/OpenAPI 生成 | 决定 API 层能否长期不漂移 | **(b)**。后端已上 Knife4j 4.5.0（OpenAPI 3），16 个服务可聚合一份 spec，用 orval/openapi-typescript 生成 types + 客户端。**本次 9 条死路径正是「手写」的必然结果** |
| 3 | **多端策略** | (a) `admin`/`seller` 各自 Next.js 应用（现状）；(b) 合并为单应用多布局 | 影响构建/部署与共享代码 | **(a) 保持**。现状 5 个 app 的 monorepo 骨架已成型，重构成本低于合并；但 `admin`/`seller` 需从零实现，可先复用 `packages/ui` + `packages/api` |
| 4 | **React 版本** | 实际 `react ~18.2.0`（根 `pnpm.overrides` 强制锁 18.2.0）；`frontend/15` 声明 **React 19** | 影响可用特性（Server Components `use()` 等）与依赖兼容（antd 5 对 19 支持） | **先改文档为 18.2**（`fix-doc`），React 19 升级作为独立议题单独立项 |

---

## 五、分阶段计划

> 阶段 0 拆成两半：**0a 文档收敛**（本文档范围内可完成）与 **0b 后端放行面修复**（需后端配合，但同属准入）——两者都是硬前置。
> 阶段 1–3 可与后端修复并行。

### 阶段 0a：契约收敛（见 §二）
- 输出：契约 §四 拆分「已实现 / 计划中」、13 条更正、`/api/verification/codes` 补录、头部计数改可核验三元组、§六 网关表更正、§七 页面映射更正。
- 完成判据：`node Implementation/back-end/scripts/extract-endpoints.mjs` 的 `docOnly` 中「标注已实现但缺失」= **0**；`node Implementation/back-end/scripts/check-section7.mjs` 的「不存在」中真实缺陷 = **0**。

### 阶段 0b：后端放行面修复（对应 [20 §八](../quality/20-Doc-Code-Consistency-Audit.md) #0–#4、#9–#11）
- 网关补 `/api/admin/{flash,promotion,task,group-buy}/**` 4 条路由谓词（否则管理端 12 个端点 404）。
- 4 个 admin 控制器补 `@PreAuthorize`（12 个端点当前无鉴权）。
- 跨服务线格式收敛：统一命名策略或给 `InAppMessageSendReqCO` 等加显式 `@JsonProperty`（否则站内信收不到，且前端字段命名无法统一生成）。
- 定人机验证契约方向（扁平 vs 嵌套 + snake_case），否则风控开启后登录死循环。
- 补 `GET /api/user/address/{id}` 归属校验、`PUT /api/item/product/{id}/status` 的 sellerId 校验、`/api/item/category` 写操作的管理员校验。
- 完成判据：管理端 12 个端点经网关可访问且非管理员被拒（403）；站内信可端到端收到一条。

> 阶段 0a 与 0b 完成前，阶段 1 生成的客户端**无法通过联调验证**——会分不清「前端接错」还是「后端不可达」。

### 阶段 1：生成 API 层（可并行）
- 聚合 16 个服务的 OpenAPI spec（Knife4j），生成 TS 类型 + 请求客户端。
- **删除**现有手写的 9 条死路径，`error-codes.ts` 改为从 `ErrorCode` 生成/全量对齐（现仅 9 条，实际枚举 98 条）。
- 完成判据：生成的客户端中每个 path 都能在后端 `endpoints-code.json` 中找到；`grep` 前端源码无硬编码 `/api/` 字面量。

### 阶段 2：认证与骨架（阻塞阶段 3）
- 落地决策 1（httpOnly refresh + BFF 刷新）；按 `12-Security-Model` 复核 token 生命周期（access 30min / refresh 7d 与后端签发值对齐——以代码实际签发为准）。
- 补 `apps/web` 缺失路由（现有 25 页 vs `product/16` 的目标信息架构）。
- `admin`/`seller` 从零起（先用 antd 5 + `packages/ui` 搭布局与登录，再接契约）。

### 阶段 3：按页面迁移（业务链路优先）
建议顺序（对应契约 §七 前端页面映射）：
1. 首页/市场（`GET /api/home/config`、`/api/item/product/page`、`/api/item/category/tree`）
2. 商品详情 + 评价（`/api/item/product/{id}`、`/api/item/review/product/{id}*`）
3. 搜索（`/api/search/{product,facets,hot,suggest}`）
4. 购物车 → 结算 → 下单（`/api/cart*`、`/api/coupon/available`、`/api/trade/order`）
5. 支付（`/api/pay/order/{orderNo}`、`/api/pay/order/{orderNo}/status`、`/api/pay/wechat/jsapi`）
6. 订单/物流/售后/发票（`/api/trade/order*`、`/api/after-sale*`、`/api/logistics/{orderNo}`、`/api/invoice*`）
7. 会员域（签到/积分/券/钱包/多币种展示换算 `rate_scaled`）
8. 商家端（`/api/seller/*`、`/api/seller/finance/*`、`/api/seller/knowledge/*`）
9. 管理端（`/api/admin/*`，注意路由顺序敏感）

### 阶段 4：小程序与多端
- `apps/miniprogram` 已有 1,977 行（对应后端 V5.18 已补齐的微信小程序登录、PAY7 JSAPI 支付、P37 分享码增量）；对齐同一份生成的 API 层。

---

## 六、验收基线（可机械校验）

| # | 基线 | 校验方式 |
| --- | --- | --- |
| 1 | 前端无硬编码路径 | `grep -rn "'/api/" Implementation/front-end --include=*.ts --include=*.tsx \| wc -l` → 0（全部走生成客户端） |
| 2 | 前端调用的每个路径都存在 | 对前端生成的客户端路径集合与 `extract-endpoints.mjs` 输出的代码端点清单（286 条） 取差集 → 空 |
| 3 | 错误码映射完整 | `error-codes.ts` 覆盖 `ErrorCode.java` 全部 98 个码（或明确声明只覆盖前端需分流的子集并给出判据） |
| 4 | 契约自洽 | `Implementation/back-end/scripts/extract-endpoints.mjs` 输出：`docOnly` 中 `⚪ 计划中` 与 `✅` 已分离；`✅` 类 `docOnly` = 0 |
| 5 | 契约与代码双向一致 | 同上脚本 `codeOnly` = 0（补录 3 个 `/internal/auth/*` + `/api/verification/codes` 后） |
| 6 | Token 不落 JS 可读存储 | 代码审查：`refresh_token` 无 `document.cookie` 写入路径 |
| 7 | 金额口径 | 前端一律按「分」整数处理（`/100` 仅用于展示）；多币种换算严格按契约 §1.5 定点整数公式 `round(amountCNY × rate_scaled × 10^exponent ÷ 10^8)`，禁 `BigDecimal`/浮点 |
| 8 | 内部端点不误用 | 前端代码中 `/internal/**` 出现次数 = 0 |
| 9 | 管理端端点经网关可达 | 对 `extract-endpoints.mjs` 输出的代码端点清单（286 条） 中 `/api/admin/**` 逐个走网关首命中路由，全部落到 marketing/trade/pay/merchant/wallet/auth/user **对应服务**而非兜底 user-service；即 [20 §C2-1](../quality/20-Doc-Code-Consistency-Audit.md) 的 12 个端点可访问 |
| 10 | 管理端鉴权生效 | 非管理员 token 调 `/api/admin/{pay,after-sale,finance,log}/**` 一律 403（当前为 200） |
| 11 | 字段命名按服务一致 | 生成客户端对 4 个 `SNAKE_CASE` 服务（authorization/notification/verification/wallet）产出 snake_case 字段，其余 camelCase；字段 `undefined` 数 = 0 |
| 12 | 购物车单一真相源 | 加购/结算只走服务端购物车（`/api/cart*`）；`localStorage` 不再作为购物车事实源；`CreateOrderReq` 只发 `addressId`/`userCouponId`/`remark`/`displayCurrency` |
| 13 | 路由守卫校验角色 | 非 ADMIN 访问 `/admin/**`、非 SELLER 访问 `/seller/**` 被 `middleware.ts` 拦截（当前只查 Cookie 存在性） |

---

## 七、边界与不要在重构中做的事

1. **不要把 33 个 `⚪ 计划中` 端点实现进前端**（品牌 CRUD、到货通知、账户注销/复活、`/api/chat` 已读等）——它们在契约里是计划，不是现状。
2. **不要为 9 条死路径写「兼容适配层」**。适配层会把错误固化成契约外约定；正确做法是改前端调用（阶段 1 生成客户端后自然消失）。
3. **不要信任前端域文档的 API 描述**：`15`/`17`/`18`/`16` 均停在 V1.0（2026-08-02），早于契约 V5.7–V5.18 的全部增量（拼团/发票/知识图谱/多币种/笔记/装修/任务中心/小程序支付）。前端技术选型（框架/状态/表单/样式）仍可参考，**API 与页面映射必须以 `contract/05` + 代码为准**。
4. **不要绕过网关直连服务**：契约 §六 的路由表本身有 6 处缺行与 2 处目标服务写错（[20 §A5](../quality/20-Doc-Code-Consistency-Audit.md)），以 `gateway-service/src/main/resources/application.yml` 为准；`/api/admin/**`、`/api/seller/**` 存在**顺序敏感的兜底路由**，新增前缀前必须确认归属。
5. **未验证项不得当作已同步**：[20 §十](../quality/20-Doc-Code-Consistency-Audit.md) 列出了截至本轮的**仍未取得结论**项（契约 §八 CO/VO 逐字段、`12`/`02` 逐条差集、配置键级差集、索引/约束级差集、ArchUnit 规则逐条对应）。这些不影响 §二/§五 的准入判断（准入只看 §三 的阻断项），但不得在文档或联调结论里声称已同步。

---

## 八、下一步（供选择）

| 选项 | 内容 | 前置 |
| --- | --- | --- |
| A | 执行 [20 §八](../quality/20-Doc-Code-Consistency-Audit.md) 修复清单 #1–#8（契约与镜像文档收敛） | 需先答复 [20 §九](../quality/20-Doc-Code-Consistency-Audit.md) 待决策 1（「端点总数」是规格还是快照） |
| B | 补齐本轮未验证的 8 项逐条差集 | 无 |
| C | 落地阶段 1（聚合 OpenAPI + 生成 TS 客户端） | 依赖 A 的契约收敛 |
| D | 先定 §四 的 4 个架构决策 | 无 |
