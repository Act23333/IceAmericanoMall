# 23 — 前端角色端补完缺口矩阵（Admin / Seller：端点 · 页面 · 权限位）

> 最后更新: 2026-10-18 | 版本: V1.0
>
> 归属域: frontend | Owner: product-manager（需求域 ＋ 契约域） | 更新触发条件: admin/seller 端点、页面、权限位任一侧变化时
>
> **定位**：本文不是实施方案（那是 [18-Frontend-Implementation.md](18-Frontend-Implementation.md)），也不是准入清单（那是 [21-Frontend-Refactor-Readiness.md](21-Frontend-Refactor-Readiness.md)）。本文是**测量表**：回答「角色端要补的端点一共多少条、每条有没有页面入口、每条挂什么权限位」，并给 S0（基座）提供可直接照抄的输入。
>
> **口径**：所有数字均为本轮自测，命令与边界见 §二。凡未核实的项集中在 §八，不与已确认事实混写。

---

## 一、读数摘要（七个数字）

| # | 读数 | 值 | 证据位置 |
| :-: | :-- | :-- | :-- |
| 1 | admin + seller 端点（本文论域，方法＋路径去重） | **95** | §三 表 A 小计（55＋5＋19＋5＋8＋3） |
| 2 | 其中「路径口径」（`/api/admin/**`、`/api/trade/admin/**`、`/api/seller/**`、`/api/trade/seller/**`） | **84** | 表 A 的 A1/A2/S1/S2 |
| 3 | 其中「受众口径补充」（商家/管理受众但落在买家前缀下） | **11** | 表 A 的 X1/X2 |
| 4 | 这 95 条里**有现有前端入口**的 | **0**（全部「无入口」） | 表 A 第 5 列；`apps/admin`、`apps/seller` 各 1 个占位页、0 处 API 调用 |
| 5 | 这 95 条里**挂在权限位上**的 | **68**（`user:admin` 63 ＋ `seller:admin` 5） | 表 C |
| 6 | 这 95 条里**无任何权限位**的 | **23**（G1） | 表 C |
| 7 | 这 95 条里**只有角色、没有权限位串**的 | **4**（G3，`ROLE_SELLER` 方法体内判断） | 表 C |

**一句话结论**：角色端不是「页面没写完」，而是**三层同时缺失**——页面层 0 入口（`apps/admin`/`apps/seller` 各 1 个静态占位页）、权限层 23 条端点无权限位、命名层 13 条权限位的声明路径与实际守卫路径不一致。后端 95 条端点**全部已实现**，且其路径前缀在网关路由表中均有具体归属（静态核对，见 §八 未核实项 2），因此补完的瓶颈在前端与权限位，不在后端功能。

---

## 二、口径声明（怎么数的、边界是什么）

### 2.1 端点侧

| 项 | 做法 |
| :-- | :-- |
| 事实源 | `Implementation/back-end/**/adapter/**/*Controller.java` 的 `@RequestMapping` ＋ 方法级 `@Get/Post/Put/Patch/DeleteMapping` 静态解析 |
| 官方脚本 | `node Implementation/back-end/scripts/extract-endpoints.mjs`（本轮实跑输出：**代码 289 条**（去重）／90 个控制器／`/internal/**` 63 条／`/api/admin/**` 55 条；契约侧 334 行／去重 332 条；`codeOnly=0`、`docOnly=43`） |
| 自建脚本（补 `@PreAuthorize` 与行号） | 一次性脚本，落在仓库外 `%TEMP%/ia-measure/`，**未入库**；它与官方脚本对**同一组 289 条（方法＋路径）完全一致**（`rawMappingRows 289 / distinct 289 / internal 63`），本文所有 `类:行` 由它产出并抽样人工复核 |
| 去重键 | `METHOD + 路径`，路径把 `{任意名}` 归一为 `{}`；同路径不同方法算两条 |
| 论域 | **95 条**＝`/api/admin/**` 55 ＋ `/api/trade/admin/**` 5 ＋ `/api/seller/**` 19 ＋ `/api/trade/seller/**` 5（＝84）**＋** 受众补充 11（X1 8 ＋ X2 3）。X1/X2 的判定依据写在表 A 各组标题下 |

### 2.2 边界（明确「没数什么」）

1. **`/internal/**` 63 条全部排除**。它们是 Feign 内部契约，不经网关、不面向任何前端。
2. **买家端（web）端点不计入 95**。它们进表 B（页面 → 端点），用于核对「现有页面消费了什么」。全库 289 ＝ 论域 95（与表 B 无交集）＋ 表 B 55（现有页面消费）＋ 其余 139（`/internal/**` 63 ＋ 买家端公开/私有端点 76）。
3. **`/api/item/**` 下的受众判定按「身份来源」而非路径**：`SpuController` 写端点用 `UserContext.getUserId()` 当 `sellerId`（`SpuController.java:141,148`）、`FreightTemplateController` 用 `requireSellerId()`（`:62-67`）、`ReviewController.replyToReview(sellerId,…)`（`ReviewController.java:67` ＋ `app/ReviewManager.java:243`）⇒ 归商家受众（X1）；`CategoryController` 写端点带 `@PreAuthorize('user:admin')` ⇒ 归管理受众（X2）。
4. **不把注释里的路径当调用**。前端有 6 处 `/api/cart/{select-all,batch,...}` 出现在**块注释**里（`apps/web/app/stores/cart-model.ts:30-33`、`packages/api/hooks/use-cart.ts:18-20`），本轮按「未接线」处理，不计入表 B。
5. **前端只数 `apps/web` ＋ `packages/**`**。`apps/miniprogram`、`apps/mobile` 不在本文论域（小程序归 [18-platforms/miniprogram.md](18-platforms/miniprogram.md)）。
6. **二进制依赖目录与 `.git` 未纳入检索**（`node_modules`/`target`/`.next` 全部跳过）。

### 2.3 工作树与 HEAD 的差异（影响行号可信度）

- 测量起点 `HEAD = 96acb0c2`；**测量过程中主 agent 侧提交推进到 `51b0d904`**（`fix(pay,db,docs): …`）。本轮已在 `51b0d904` 上**重新实跑** `extract-endpoints.mjs`，结果逐项不变（289／`/internal/**` 63／`/api/admin/**` 55／契约 334 行去重 332／`codeOnly=0`／`docOnly=43`）；`Initialize.sql` 行号已按新 HEAD 校正——该提交删掉了 RBAC 段之前的 6 行注释，种子整体下移 6 行（`sys_role` 起点 960、`sys_permission` 973、`sys_role_permission` 990/993/996/999）。
- 该提交只动了 3 个后端文件（`Initialize.sql`、`InvoiceLinkCompensationLeasePolicy.java` 及其边界测试），**未触及本文引用的任何 Controller**，故表 A／表 C 的 `类:行` 在两个 HEAD 下一致。
- 本文引用的 **RBAC 种子（`Initialize.sql:959-1000`）与 `open-items.md:198` 内容逐字未变**（`git show HEAD:<file>` 在 `96acb0c2` 与 `51b0d904` 上各比对一次）。
- 测量起点的工作树**不是干净的**（当时有 32 项未提交变更，含 `Initialize.sql` 已暂存、3 个 `tailwind.config.ts` 被删）；这些不是本轮产生的，完整原始输出见交付报告。
- 本轮**未做构建、未起服务**：前端「页面能不能真跑起来」未验证（见 §八）。

---

## 三、表 A · 端点 → 页面

> **「现有前端入口」列的口径**：只有「现有代码里存在调用该端点的页面/组件」才算入口。`apps/admin`、`apps/seller` 各只有一个静态占位页（各 0 处 API 调用，见 §四 表 B-24/B-25），因此 **95 条全部为「无入口」**——这不是省事，是实测结果。
>
> 为让本表对 S0 可用，额外给两列：**后端实现（类:行）** 与 **契约 §七 规划页面**（逐行引自 [contract/05 §7.3/§7.4](../contract/05-API-Specification.md)）。后者标 ⚪ 表示**契约也没有为它规划页面**——这类端点没有「等前端补完」的期待，属于「有端点、无页面、无规划」，需要在 S0 里定去留。

**A1 · `/api/admin/**` — 平台管理端族 —— 55 条**

| # | 方法 | 路径 | 后端实现（类:行） | 现有前端入口 | 契约 §七 规划页面 |
| :-: | :-- | :-- | :-- | :-- | :-- |
| A1-1 | GET | `/api/admin/after-sale` | `AdminAfterSaleController.java:50` | **无入口** | `/admin/after-sales` ＋ `/seller/after-sales` |
| A1-2 | PUT | `/api/admin/after-sale/{id}/confirm-return` | `AdminAfterSaleController.java:79` | **无入口** | ⚪ §七 无对应行 |
| A1-3 | PUT | `/api/admin/after-sale/{id}/review` | `AdminAfterSaleController.java:64` | **无入口** | `/admin/after-sales` ＋ `/seller/after-sales` |
| A1-4 | POST | `/api/admin/coupon` | `AdminCouponController.java:47` | **无入口** | `/admin/coupons` |
| A1-5 | PUT | `/api/admin/coupon/{couponId}/disable` | `AdminCouponController.java:55` | **无入口** | `/admin/coupons` |
| A1-6 | PUT | `/api/admin/currency/{code}/rate` | `AdminCurrencyController.java:59` | **无入口** | ⚪ §七 无对应行 |
| A1-7 | PUT | `/api/admin/currency/{code}/status` | `AdminCurrencyController.java:67` | **无入口** | ⚪ §七 无对应行 |
| A1-8 | GET | `/api/admin/currency/page` | `AdminCurrencyController.java:50` | **无入口** | ⚪ §七 无对应行 |
| A1-9 | POST | `/api/admin/finance/settlement/generate` | `AdminFinanceController.java:44` | **无入口** | `/admin/finance` |
| A1-10 | GET | `/api/admin/finance/settlement/page` | `AdminFinanceController.java:52` | **无入口** | `/admin/finance` |
| A1-11 | PUT | `/api/admin/finance/withdrawal/{id}/review` | `AdminFinanceController.java:70` | **无入口** | `/admin/finance` |
| A1-12 | GET | `/api/admin/finance/withdrawal/page` | `AdminFinanceController.java:62` | **无入口** | `/admin/finance` |
| A1-13 | POST | `/api/admin/flash` | `AdminFlashController.java:53` | **无入口** | `/admin/flash` |
| A1-14 | DELETE | `/api/admin/flash/{id}` | `AdminFlashController.java:69` | **无入口** | `/admin/flash` |
| A1-15 | PUT | `/api/admin/flash/{id}` | `AdminFlashController.java:60` | **无入口** | `/admin/flash` |
| A1-16 | POST | `/api/admin/group-buy` | `AdminGroupBuyController.java:52` | **无入口** | ⚪ §七 无对应行 |
| A1-17 | DELETE | `/api/admin/group-buy/{id}` | `AdminGroupBuyController.java:68` | **无入口** | ⚪ §七 无对应行 |
| A1-18 | PUT | `/api/admin/group-buy/{id}` | `AdminGroupBuyController.java:59` | **无入口** | ⚪ §七 无对应行 |
| A1-19 | GET | `/api/admin/home` | `AdminHomeController.java:62` | **无入口** | `/admin/home` |
| A1-20 | POST | `/api/admin/home` | `AdminHomeController.java:43` | **无入口** | `/admin/home` |
| A1-21 | DELETE | `/api/admin/home/{id}` | `AdminHomeController.java:56` | **无入口** | `/admin/home` |
| A1-22 | PUT | `/api/admin/home/{id}` | `AdminHomeController.java:49` | **无入口** | `/admin/home` |
| A1-23 | PUT | `/api/admin/invoice/{invoiceNo}/issue` | `AdminInvoiceController.java:58` | **无入口** | ⚪ §七 无对应行 |
| A1-24 | PUT | `/api/admin/invoice/{invoiceNo}/red-flush` | `AdminInvoiceController.java:73` | **无入口** | ⚪ §七 无对应行 |
| A1-25 | PUT | `/api/admin/invoice/{invoiceNo}/void` | `AdminInvoiceController.java:65` | **无入口** | ⚪ §七 无对应行 |
| A1-26 | GET | `/api/admin/invoice/page` | `AdminInvoiceController.java:51` | **无入口** | ⚪ §七 无对应行 |
| A1-27 | GET | `/api/admin/kg/neighbors` | `AdminKgController.java:97` | **无入口** | ⚪ §七 无对应行 |
| A1-28 | POST | `/api/admin/kg/rebuild` | `AdminKgController.java:86` | **无入口** | ⚪ §七 无对应行 |
| A1-29 | POST | `/api/admin/kg/rule` | `AdminKgController.java:70` | **无入口** | ⚪ §七 无对应行 |
| A1-30 | PUT | `/api/admin/kg/rule/{id}` | `AdminKgController.java:78` | **无入口** | ⚪ §七 无对应行 |
| A1-31 | GET | `/api/admin/kg/rule/page` | `AdminKgController.java:59` | **无入口** | ⚪ §七 无对应行 |
| A1-32 | GET | `/api/admin/log` | `AdminLogController.java:23` | **无入口** | `/admin/logs` |
| A1-33 | GET | `/api/admin/pay/reconciliation/batches` | `AdminPayController.java:89` | **无入口** | `/admin/reconciliation` |
| A1-34 | GET | `/api/admin/pay/reconciliation/batches/{id}/details` | `AdminPayController.java:103` | **无入口** | `/admin/reconciliation` |
| A1-35 | POST | `/api/admin/pay/reconciliation/run` | `AdminPayController.java:122` | **无入口** | `/admin/reconciliation` |
| A1-36 | POST | `/api/admin/pay/refund/{refundNo}/retry` | `AdminPayController.java:78` | **无入口** | `/admin/refunds` |
| A1-37 | GET | `/api/admin/permissions` | `PermissionController.java:29` | **无入口** | ⚪ §七 无对应行 |
| A1-38 | GET | `/api/admin/permissions/tree` | `PermissionController.java:35` | **无入口** | `/admin/roles` |
| A1-39 | POST | `/api/admin/promotion` | `AdminPromotionController.java:52` | **无入口** | ⚪ §七 无对应行 |
| A1-40 | DELETE | `/api/admin/promotion/{id}` | `AdminPromotionController.java:68` | **无入口** | ⚪ §七 无对应行 |
| A1-41 | PUT | `/api/admin/promotion/{id}` | `AdminPromotionController.java:59` | **无入口** | ⚪ §七 无对应行 |
| A1-42 | GET | `/api/admin/roles` | `RoleController.java:65` | **无入口** | `/admin/roles` |
| A1-43 | POST | `/api/admin/roles` | `RoleController.java:72` | **无入口** | `/admin/roles` |
| A1-44 | DELETE | `/api/admin/roles/{id}` | `RoleController.java:89` | **无入口** | `/admin/roles` |
| A1-45 | PUT | `/api/admin/roles/{id}` | `RoleController.java:80` | **无入口** | `/admin/roles` |
| A1-46 | GET | `/api/admin/roles/{id}/permissions` | `RoleController.java:96` | **无入口** | ⚪ §七 无对应行 |
| A1-47 | POST | `/api/admin/roles/{id}/permissions` | `RoleController.java:103` | **无入口** | ⚪ §七 无对应行 |
| A1-48 | PUT | `/api/admin/seller/{id}/approve` | `AdminMerchantController.java:48` | **无入口** | `/admin/sellers` |
| A1-49 | GET | `/api/admin/seller/pending` | `AdminMerchantController.java:39` | **无入口** | `/admin/sellers` |
| A1-50 | GET | `/api/admin/task` | `AdminTaskController.java:49` | **无入口** | ⚪ §七 无对应行 |
| A1-51 | POST | `/api/admin/task` | `AdminTaskController.java:58` | **无入口** | ⚪ §七 无对应行 |
| A1-52 | PUT | `/api/admin/task/{code}` | `AdminTaskController.java:66` | **无入口** | ⚪ §七 无对应行 |
| A1-53 | PUT | `/api/admin/user/{id}/role` | `AdminUserController.java:48` | **无入口** | `/admin/users` |
| A1-54 | PUT | `/api/admin/user/{id}/status` | `AdminUserController.java:37` | **无入口** | `/admin/users` |
| A1-55 | GET | `/api/admin/user/page` | `AdminUserController.java:29` | **无入口** | `/admin/users` |

> 小计 **A1 = 55**（累计 55）。二级前缀分布：`roles` 6、`kg` 5、`finance/home/invoice/pay` 各 4、`after-sale/currency/flash/group-buy/promotion/task/user` 各 3、`coupon/permissions/seller` 各 2、`log` 1。

**A2 · `/api/trade/admin/**` — 管理端交易域族 —— 5 条**

| # | 方法 | 路径 | 后端实现（类:行） | 现有前端入口 | 契约 §七 规划页面 |
| :-: | :-- | :-- | :-- | :-- | :-- |
| A2-1 | GET | `/api/trade/admin/dashboard` | `AdminOrderController.java:39` | **无入口** | `/admin` |
| A2-2 | GET | `/api/trade/admin/orders/page` | `AdminOrderController.java:50` | **无入口** | `/admin/orders` |
| A2-3 | GET | `/api/trade/admin/stats/sales` | `AdminOrderController.java:70` | **无入口** | `/admin` |
| A2-4 | GET | `/api/trade/admin/stats/top-products` | `AdminOrderController.java:64` | **无入口** | ⚪ §七 无对应行 |
| A2-5 | GET | `/api/trade/admin/stats/trend` | `AdminOrderController.java:58` | **无入口** | `/admin` |

> 小计 **A2 = 5**（累计 60）。注意：这一族的路径**不在 `/api/admin/**` 下**，但它是管理端受众（类级 `@PreAuthorize('user:admin')`，`AdminOrderController.java:30`）——这是 G2 缺口的来源之一（见 §五）。

**S1 · `/api/seller/**` — 商家端族 —— 19 条**

| # | 方法 | 路径 | 后端实现（类:行） | 现有前端入口 | 契约 §七 规划页面 |
| :-: | :-- | :-- | :-- | :-- | :-- |
| S1-1 | POST | `/api/seller/apply` | `MerchantController.java:50` | **无入口** | `/seller/apply` |
| S1-2 | GET | `/api/seller/coupon` | `MerchantCouponController.java:43` | **无入口** | `/seller/coupons` |
| S1-3 | POST | `/api/seller/coupon` | `MerchantCouponController.java:37` | **无入口** | `/seller/coupons` |
| S1-4 | PUT | `/api/seller/coupon/{couponId}/disable` | `MerchantCouponController.java:49` | **无入口** | `/seller/coupons` |
| S1-5 | GET | `/api/seller/finance/balance` | `SellerFinanceController.java:47` | **无入口** | `/seller/finance` |
| S1-6 | GET | `/api/seller/finance/settlement/{id}` | `SellerFinanceController.java:33` | **无入口** | ⚪ §七 无对应行 |
| S1-7 | GET | `/api/seller/finance/settlement/page` | `SellerFinanceController.java:26` | **无入口** | `/seller/finance` |
| S1-8 | POST | `/api/seller/finance/withdrawal` | `SellerFinanceController.java:53` | **无入口** | `/seller/finance` |
| S1-9 | GET | `/api/seller/finance/withdrawal/page` | `SellerFinanceController.java:61` | **无入口** | ⚪ §七 无对应行 |
| S1-10 | GET | `/api/seller/knowledge` | `KnowledgeBaseController.java:47` | **无入口** | `/seller/knowledge` |
| S1-11 | POST | `/api/seller/knowledge` | `KnowledgeBaseController.java:56` | **无入口** | `/seller/knowledge` |
| S1-12 | DELETE | `/api/seller/knowledge/{id}` | `KnowledgeBaseController.java:73` | **无入口** | `/seller/knowledge` |
| S1-13 | PUT | `/api/seller/knowledge/{id}` | `KnowledgeBaseController.java:64` | **无入口** | `/seller/knowledge` |
| S1-14 | GET | `/api/seller/shop` | `MerchantController.java:112` | **无入口** | `/seller/shop`（§7.3 标 @Deprecated 别名） |
| S1-15 | PUT | `/api/seller/shop` | `MerchantController.java:123` | **无入口** | `/seller/shop`（§7.3 标 @Deprecated 别名） |
| S1-16 | GET | `/api/seller/shops` | `MerchantController.java:61` | **无入口** | `/seller/shop` |
| S1-17 | PUT | `/api/seller/shops/{shopId}` | `MerchantController.java:67` | **无入口** | `/seller/shop` |
| S1-18 | GET | `/api/seller/shops/{shopId}/decoration` | `MerchantController.java:83` | **无入口** | ⚪ §七 无对应行 |
| S1-19 | PUT | `/api/seller/shops/{shopId}/decoration` | `MerchantController.java:99` | **无入口** | ⚪ §七 无对应行 |

> 小计 **S1 = 19**（累计 79）

**S2 · `/api/trade/seller/**` — 商家端交易域族 —— 5 条**

| # | 方法 | 路径 | 后端实现（类:行） | 现有前端入口 | 契约 §七 规划页面 |
| :-: | :-- | :-- | :-- | :-- | :-- |
| S2-1 | GET | `/api/trade/seller/dashboard` | `SellerDashboardController.java:32` | **无入口** | `/seller` |
| S2-2 | GET | `/api/trade/seller/dashboard/product-stats` | `SellerDashboardController.java:38` | **无入口** | `/seller` |
| S2-3 | GET | `/api/trade/seller/order/{orderNo}` | `SellerOrderController.java:39` | **无入口** | `/seller/orders` |
| S2-4 | POST | `/api/trade/seller/order/{orderNo}/ship` | `SellerOrderController.java:47` | **无入口** | `/seller/orders` |
| S2-5 | GET | `/api/trade/seller/order/page` | `SellerOrderController.java:32` | **无入口** | `/seller/orders` |

> 小计 **S2 = 5**（累计 84）

**X1 · 商家受众 · 落在买家前缀下（`/api/item/**` 写端点）—— 8 条**

> 判定依据：身份取 `UserContext.getUserId()` 当 sellerId（`SpuController.java:141/148`）、`FreightTemplateController.requireSellerId()`（`:62-67`，注释明写「模板接口全部要求商家身份，规格标注 🏪」）、`ReviewManager.replyToReview(Long sellerId, …)`（`app/ReviewManager.java:243`）。契约 §7.3 也把它们挂在商家页面上。

| # | 方法 | 路径 | 后端实现（类:行） | 现有前端入口 | 契约 §七 规划页面 |
| :-: | :-- | :-- | :-- | :-- | :-- |
| X1-1 | POST | `/api/item/freight-template` | `FreightTemplateController.java:36` | **无入口** | `/seller/freight` |
| X1-2 | DELETE | `/api/item/freight-template/{id}` | `FreightTemplateController.java:55` | **无入口** | `/seller/freight` |
| X1-3 | PUT | `/api/item/freight-template/{id}` | `FreightTemplateController.java:42` | **无入口** | `/seller/freight` |
| X1-4 | GET | `/api/item/freight-template/list` | `FreightTemplateController.java:49` | **无入口** | `/seller/freight` |
| X1-5 | POST | `/api/item/review/{id}/reply` | `ReviewController.java:65` | **无入口** | ⚪ §七 无对应行 |
| X1-6 | POST | `/api/item/spu` | `SpuController.java:139` | **无入口** | `/seller/products` |
| X1-7 | PUT | `/api/item/spu/{spuId}` | `SpuController.java:146` | **无入口** | `/seller/products` |
| X1-8 | PUT | `/api/item/spu/{spuId}/status` | `SpuController.java:159` | **无入口** | `/seller/products` |

> 小计 **X1 = 8**（累计 92）

**X2 · 管理受众 · 落在买家前缀下（`/api/item/category` 写端点）—— 3 条**

> 判定依据：方法级 `@PreAuthorize('@ss.hasPermi('user:admin')')`（`CategoryController.java:35/41/47`）；同控制器 `GET /tree` 是公开端点，故注解只能落在方法上（`CategoryController.java:20` 有注释说明）。

| # | 方法 | 路径 | 后端实现（类:行） | 现有前端入口 | 契约 §七 规划页面 |
| :-: | :-- | :-- | :-- | :-- | :-- |
| X2-1 | POST | `/api/item/category` | `CategoryController.java:34` | **无入口** | `/admin/categories` |
| X2-2 | DELETE | `/api/item/category/{id}` | `CategoryController.java:46` | **无入口** | `/admin/categories` |
| X2-3 | PUT | `/api/item/category/{id}` | `CategoryController.java:40` | **无入口** | `/admin/categories` |

> 小计 **X2 = 3**（累计 **95**）

### 3.1 表 A 的两个副产品

1. **「有端点、无页面、且契约 §七 也没有对应行」的 31 条**（表 A 里标 ⚪ 的行：A1 25 ＋ A2 1 ＋ S1 4 ＋ X1 1 ＋ X2 0 ＝ 31）：`admin/currency` 3、`admin/kg` 5、`admin/task` 3、`admin/promotion` 3、`admin/group-buy` 3、`admin/invoice` 4、`admin/permissions` 1、`admin/roles/{id}/permissions` 2、`trade/admin/stats/top-products` 1、`seller/finance/settlement/{id}` 1、`seller/finance/withdrawal/page` 1、`seller/shops/{shopId}/decoration` 2、`admin/after-sale/{id}/confirm-return` 1、`item/review/{id}/reply` 1。这些端点**后端已实现、权限位已挂、但产品侧没有页面规划**——S0 排期时必须先裁决「补页面 / 保持无 UI / 下线」。
2. **页面对不齐导致的功能空洞**：`/seller/after-sales` 在 §7.3 被规划为调用 `GET /api/admin/after-sale` ＋ `PUT /api/admin/after-sale/{id}/review`，而这两条要求 `user:admin`，`ROLE_SELLER` **不持有该权限位**（`Initialize.sql:996`，见 §五）⇒ 商家售后页按现在这份契约实现，必然 403。这不是前端问题，是**契约与 RBAC 种子的冲突**，需要产品裁决（把商家售后挪到 `seller:admin`，或在幂等种子给 ROLE_SELLER 补 `user:admin`）。

---

## 四、表 B · 页面 → 端点

> **口径**：只统计「现有代码里真实发出的调用」。前端 API 层 `packages/api/{server.ts,hooks/*.ts}` 与页面内直连 `fetch/apiClient` 均计入；`packages/api/*.ts` 里以**注释/文档串**出现的路径不计。23 个路由页 ＋ 2 个占位应用页 ＝ 25 行。
>
> **后端实现列**：✅ ＝ 在 `extract-endpoints.mjs` 的 289 条代码端点中找到（方法＋路径匹配）。

| # | 页面（文件） | 路由 | 现有调用端点（方法＋路径） | 后端实现 |
| :-: | :-- | :-- | :-- | :-- |
| B-1 | `apps/web/app/page.tsx` | `/` | `GET /api/item/spu/page`（`packages/api/server.ts:110`） | ✅ |
| B-2 | `apps/web/app/marketplace/page.tsx` | `/marketplace` | `GET /api/item/spu/page`、`GET /api/item/category/tree`（`server.ts:110/140`）＋ 客户端无限滚动同路径（`marketplace/infinite-client.tsx:55`） | ✅ ✅ |
| B-3 | `apps/web/app/marketplace/coupons/page.tsx` | `/marketplace/coupons` | `GET /api/coupon/template`、`POST /api/coupon/claim`、`POST /api/coupon/grab`（`hooks/use-coupons.ts:27/42/52`） | ✅ ✅ ✅ |
| B-4 | `apps/web/app/marketplace/flash/page.tsx` | `/marketplace/flash` | `GET /api/flash`、`POST /api/flash/buy`（`hooks/use-flash-sale.ts:10/27`；横幅另经 `flash-sale-banner.tsx:30` 再取一次 `GET /api/flash`） | ✅ ✅ |
| B-5 | `apps/web/app/search/page.tsx` | `/search` | `GET /api/search/spu`、`GET /api/search/hot`、`GET /api/item/spu/page`、`GET /api/item/category/tree`（`page.tsx:98/113/115/140`） | ✅ ✅ ✅ ✅ |
| B-6 | `apps/web/app/spu/[spuId]/page.tsx` | `/spu/[spuId]` | `GET /api/item/spu/{spuId}`、`GET /api/item/spu/page`（推荐位）、`GET /api/shop/{sellerId}`、`GET /api/item/review/spu/{spuId}/filter`、`POST /api/item/review/{id}/like`、`POST /api/user/history`（浏览足迹）、`POST /api/cart/item`（加购，经 `stores/cart-model.ts`） | ✅ |
| B-7 | `apps/web/app/shop/cart/page.tsx` | `/shop/cart` | `GET /api/cart`、`POST /api/cart/item`、`PUT /api/cart/item`、`DELETE /api/cart/item/{skuId}`、`PATCH /api/cart/item/{skuId}/selected`、`DELETE /api/cart/clear`（`hooks/use-cart.ts:36/50/59/70/84/93`） | ✅ |
| B-8 | `apps/web/app/shop/checkout/page.tsx` | `/shop/checkout` | `GET /api/user/address/list`、`GET /api/cart`、`GET /api/coupon/available`、`POST /api/trade/order` | ✅ |
| B-9 | `apps/web/app/shop/orders/page.tsx` | `/shop/orders` | `GET /api/trade/order/page`、`POST /api/trade/order/{orderNo}/cancel`、`POST /api/trade/order/{orderNo}/confirm` | ✅ |
| B-10 | `apps/web/app/shop/orders/[orderNo]/page.tsx` | `/shop/orders/[orderNo]` | `GET /api/trade/order/{orderNo}` ＋ 取消/确认收货同上 | ✅ |
| B-11 | `apps/web/app/shop/pay/[orderNo]/page.tsx` | `/shop/pay/[orderNo]` | `GET /api/trade/order/{orderNo}`、`POST /api/pay/order/{orderNo}`、`GET /api/pay/order/{payOrderNo}/status`、`GET /api/wallet`、`POST /api/wallet/recharge` | ✅ |
| B-12 | `apps/web/app/shop/after-sale/page.tsx` | `/shop/after-sale` | `POST /api/after-sale`（`hooks/use-after-sale.ts:24`） | ✅ |
| B-13 | `apps/web/app/user/addresses/page.tsx` | `/user/addresses` | `GET /api/user/address/list`、`POST /api/user/address/add`、`PUT /api/user/address/update/{id}`、`DELETE /api/user/address/delete/{id}` | ✅（4 条均在 `AddressController.java:35/41/54/70`） |
| B-14 | `apps/web/app/user/balance/page.tsx` | `/user/balance` | `GET /api/wallet`、`POST /api/wallet/recharge` | ✅ |
| B-15 | `apps/web/app/user/coupons/page.tsx` | `/user/coupons` | `GET /api/coupon/available`、`GET /api/coupon/used`、`GET /api/coupon/template`、`POST /api/coupon/claim` | ✅ |
| B-16 | `apps/web/app/user/favorites/page.tsx` | `/user/favorites` | `GET /api/user/favorite`、`DELETE /api/user/favorite`（`page.tsx:40/49`） | ✅ |
| B-17 | `apps/web/app/user/history/page.tsx` | `/user/history` | `GET /api/user/history`、`DELETE /api/user/history`、`DELETE /api/user/history/{id}`（`page.tsx:73/79/84`） | ✅ |
| B-18 | `apps/web/app/user/notifications/page.tsx` | `/user/notifications` | `GET /api/notifications`、`GET /api/notifications/unread-count`、`PUT /api/notifications/read-all`、`PUT /api/notifications/{id}/read`（`page.tsx:89/94/99`、`use-notification-page.ts:57`） | ✅ |
| B-19 | `apps/web/app/user/points/page.tsx` | `/user/points` | `GET /api/points/balance`、`GET /api/points/history`（`page.tsx:37/45`） | ✅ |
| B-20 | `apps/web/app/user/profile/page.tsx` | `/user/profile` | `GET /api/user/info`、`POST /api/user/profile/avatar`、`GET /api/wallet`、`POST /api/wallet/recharge` | ✅ |
| B-21 | `apps/web/app/user/sign-in/page.tsx` | `/user/sign-in` | `GET /api/sign/status`、`POST /api/sign`（`page.tsx:22/26`） | ✅ |
| B-22 | `apps/web/app/auth/login/page.tsx` | `/auth/login` | `POST /api/auth/web/login`（`packages/auth/actions.ts:20`）、`POST /api/auth/web/wechat/login`（`page.tsx:70`）、`POST /api/verification/codes`（`hooks/use-verification.ts:21`） | ✅ |
| B-23 | `apps/web/app/auth/register/page.tsx` | `/auth/register` | `POST /api/auth/register`（`actions.ts:31`）、`POST /api/verification/codes` | ✅ |
| B-24 | `apps/admin/app/page.tsx` | `/`（3001 端口） | **0 条**（纯静态占位，标题「📊 平台管理后台」＋「Dashboard — 开发中」，`page.tsx:1-11`；全目录 11 个文件 / 4 个 `app/` 文件） | — |
| B-25 | `apps/seller/app/page.tsx` | `/`（3002 端口） | **0 条**（同上，标题「🏪 商家后台」＋「Dashboard — 开发中」，`page.tsx:1-11`；全目录 11 个文件 / 4 个 `app/` 文件） | — |

> 小计：**25 个页面**；被页面消费的端点（方法＋路径去重）**55 条**，**全部在后端有实现（55/55 ✅）**。
>
> **与旧结论的差异（请注意）**：[21-Frontend-Refactor-Readiness.md](21-Frontend-Refactor-Readiness.md) §三 列的「9 条死路径」在本轮**已全部消失**——`/api/auth/login`、`/api/user/code`、`/api/user/sign*`、`/api/user/points/*`、`/api/user/notifications*`、`/api/user/balance`、`/api/coupon/available/filter` 现已无一处活调用（残留在注释里）。教训是：**「文档—代码漂移」在这里是文档滞后于代码，不是代码落后于文档**。同时 `/api/user/address/*` 四条曾被判「前端错」，实测**后端确实就是这么实现的**（`AddressController.java:35/41/54/70`）——21 §三 的这张表已过时，属 doc-engineer 域的修订项（见 §八）。

---

## 五、表 C · 端点 → 权限位（S0 基座输入）

> **权限位真源**：① `@PreAuthorize('@ss.hasPermi('...')')`（`ia-common` 的 `PermissionService`，`@EnableMethodSecurity` 于 `MethodSecurityConfig.java:34` 生效）；② 方法体内手工守卫 `UserContext.hasPermission/hasRole`；③ 契约层无第三处。**RBAC 种子**只有 6 个权限位（`Initialize.sql:973-979`）：`*:*:*`、`ai:chat`、`order:read`、`product:read`、`user:admin`、`seller:admin`；`sys_role_permission` 共 16 行（`Initialize.sql:990-999`）。
>
> **缺口分类**：
> - **G1 有端点无权限位**：既无 `@PreAuthorize`、也无方法体内角色/权限判断，仅靠 `UserContext.getUserId()` 做归属过滤。
> - **G2 权限位声明路径 ≠ 实际守卫路径**：`sys_permission.path` 声明的匹配范围不覆盖该端点真实路径（按 `path` 渲染菜单/路由的客户端会漏挂）。
> - **G3 只有角色、没有权限位串**：能拦住非商家，但进不了 `sys_permission` 体系，后台的「角色—权限」勾选框看不到它。

**A1 · `/api/admin/**` — 平台管理端族 —— 55 条**

| # | 方法 | 路径 | 权限位（真源） | 判定依据 | 缺口 |
| :-: | :-- | :-- | :-- | :-- | :-- |
| A1-1 | GET | `/api/admin/after-sale` | `user:admin` | 类级 `@PreAuthorize`（:33） | — |
| A1-2 | PUT | `/api/admin/after-sale/{id}/confirm-return` | `user:admin` | 类级 `@PreAuthorize`（:33） | — |
| A1-3 | PUT | `/api/admin/after-sale/{id}/review` | `user:admin` | 类级 `@PreAuthorize`（:33） | — |
| A1-4 | POST | `/api/admin/coupon` | `user:admin` | 类级 `@PreAuthorize`（:29） | — |
| A1-5 | PUT | `/api/admin/coupon/{couponId}/disable` | `user:admin` | 类级 `@PreAuthorize`（:29） | — |
| A1-6 | PUT | `/api/admin/currency/{code}/rate` | `user:admin` | 类级 `@PreAuthorize`（:33） | — |
| A1-7 | PUT | `/api/admin/currency/{code}/status` | `user:admin` | 类级 `@PreAuthorize`（:33） | — |
| A1-8 | GET | `/api/admin/currency/page` | `user:admin` | 类级 `@PreAuthorize`（:33） | — |
| A1-9 | POST | `/api/admin/finance/settlement/generate` | `user:admin` | 类级 `@PreAuthorize`（:24） | — |
| A1-10 | GET | `/api/admin/finance/settlement/page` | `user:admin` | 类级 `@PreAuthorize`（:24） | — |
| A1-11 | PUT | `/api/admin/finance/withdrawal/{id}/review` | `user:admin` | 类级 `@PreAuthorize`（:24） | — |
| A1-12 | GET | `/api/admin/finance/withdrawal/page` | `user:admin` | 类级 `@PreAuthorize`（:24） | — |
| A1-13 | POST | `/api/admin/flash` | `user:admin` | 类级 `@PreAuthorize`（:32） | — |
| A1-14 | DELETE | `/api/admin/flash/{id}` | `user:admin` | 类级 `@PreAuthorize`（:32） | — |
| A1-15 | PUT | `/api/admin/flash/{id}` | `user:admin` | 类级 `@PreAuthorize`（:32） | — |
| A1-16 | POST | `/api/admin/group-buy` | `user:admin` | 类级 `@PreAuthorize`（:31） | — |
| A1-17 | DELETE | `/api/admin/group-buy/{id}` | `user:admin` | 类级 `@PreAuthorize`（:31） | — |
| A1-18 | PUT | `/api/admin/group-buy/{id}` | `user:admin` | 类级 `@PreAuthorize`（:31） | — |
| A1-19 | GET | `/api/admin/home` | `user:admin` | 类级 `@PreAuthorize`（:23） | — |
| A1-20 | POST | `/api/admin/home` | `user:admin` | 类级 `@PreAuthorize`（:23） | — |
| A1-21 | DELETE | `/api/admin/home/{id}` | `user:admin` | 类级 `@PreAuthorize`（:23） | — |
| A1-22 | PUT | `/api/admin/home/{id}` | `user:admin` | 类级 `@PreAuthorize`（:23） | — |
| A1-23 | PUT | `/api/admin/invoice/{invoiceNo}/issue` | `user:admin` | 类级 `@PreAuthorize`（:31） | — |
| A1-24 | PUT | `/api/admin/invoice/{invoiceNo}/red-flush` | `user:admin` | 类级 `@PreAuthorize`（:31） | — |
| A1-25 | PUT | `/api/admin/invoice/{invoiceNo}/void` | `user:admin` | 类级 `@PreAuthorize`（:31） | — |
| A1-26 | GET | `/api/admin/invoice/page` | `user:admin` | 类级 `@PreAuthorize`（:31） | — |
| A1-27 | GET | `/api/admin/kg/neighbors` | `user:admin` | 类级 `@PreAuthorize`（:39） | — |
| A1-28 | POST | `/api/admin/kg/rebuild` | `user:admin` | 类级 `@PreAuthorize`（:39） | — |
| A1-29 | POST | `/api/admin/kg/rule` | `user:admin` | 类级 `@PreAuthorize`（:39） | — |
| A1-30 | PUT | `/api/admin/kg/rule/{id}` | `user:admin` | 类级 `@PreAuthorize`（:39） | — |
| A1-31 | GET | `/api/admin/kg/rule/page` | `user:admin` | 类级 `@PreAuthorize`（:39） | — |
| A1-32 | GET | `/api/admin/log` | `user:admin` | 类级 `@PreAuthorize`（:15） | — |
| A1-33 | GET | `/api/admin/pay/reconciliation/batches` | `user:admin` | 方法体内 `assertAdmin()`（:91 调用；守卫定义 :129） | — |
| A1-34 | GET | `/api/admin/pay/reconciliation/batches/{id}/details` | `user:admin` | 方法体内 `assertAdmin()`（:106 调用；守卫定义 :129） | — |
| A1-35 | POST | `/api/admin/pay/reconciliation/run` | `user:admin` | 方法体内 `assertAdmin()`（:124 调用；守卫定义 :129） | — |
| A1-36 | POST | `/api/admin/pay/refund/{refundNo}/retry` | `user:admin` | 方法体内 `assertAdmin()`（:80 调用；守卫定义 :129） | — |
| A1-37 | GET | `/api/admin/permissions` | `user:admin` | 类级 `@PreAuthorize`（:24） | — |
| A1-38 | GET | `/api/admin/permissions/tree` | `user:admin` | 类级 `@PreAuthorize`（:24） | — |
| A1-39 | POST | `/api/admin/promotion` | `user:admin` | 类级 `@PreAuthorize`（:31） | — |
| A1-40 | DELETE | `/api/admin/promotion/{id}` | `user:admin` | 类级 `@PreAuthorize`（:31） | — |
| A1-41 | PUT | `/api/admin/promotion/{id}` | `user:admin` | 类级 `@PreAuthorize`（:31） | — |
| A1-42 | GET | `/api/admin/roles` | `user:admin` | 类级 `@PreAuthorize`（:40） | — |
| A1-43 | POST | `/api/admin/roles` | `user:admin` | 类级 `@PreAuthorize`（:40） | — |
| A1-44 | DELETE | `/api/admin/roles/{id}` | `user:admin` | 类级 `@PreAuthorize`（:40） | — |
| A1-45 | PUT | `/api/admin/roles/{id}` | `user:admin` | 类级 `@PreAuthorize`（:40） | — |
| A1-46 | GET | `/api/admin/roles/{id}/permissions` | `user:admin` | 类级 `@PreAuthorize`（:40） | — |
| A1-47 | POST | `/api/admin/roles/{id}/permissions` | `user:admin` | 类级 `@PreAuthorize`（:40） | — |
| A1-48 | PUT | `/api/admin/seller/{id}/approve` | `user:admin` | 方法体内 `assertAdmin()`（:50 调用；守卫定义 :56） | — |
| A1-49 | GET | `/api/admin/seller/pending` | `user:admin` | 方法体内 `assertAdmin()`（:43 调用；守卫定义 :56） | — |
| A1-50 | GET | `/api/admin/task` | `user:admin` | 类级 `@PreAuthorize`（:32） | — |
| A1-51 | POST | `/api/admin/task` | `user:admin` | 类级 `@PreAuthorize`（:32） | — |
| A1-52 | PUT | `/api/admin/task/{code}` | `user:admin` | 类级 `@PreAuthorize`（:32） | — |
| A1-53 | PUT | `/api/admin/user/{id}/role` | `user:admin` | 类级 `@PreAuthorize`（:19） | — |
| A1-54 | PUT | `/api/admin/user/{id}/status` | `user:admin` | 类级 `@PreAuthorize`（:19） | — |
| A1-55 | GET | `/api/admin/user/page` | `user:admin` | 类级 `@PreAuthorize`（:19） | — |

> 小计 **A1 = 55** · G1 0 ／ G2 0 ／ G3 0（累计 55）。机制分布：**49 条**走 `@PreAuthorize` 注解，**6 条**走方法体内 `assertAdmin()`（`AdminPayController` 4 条、`AdminMerchantController` 2 条——这两个服务未引入 Spring Security，注解不可用，两个控制器类注释都写明了这一点）。

**A2 · `/api/trade/admin/**` — 管理端交易域族 —— 5 条**

| # | 方法 | 路径 | 权限位（真源） | 判定依据 | 缺口 |
| :-: | :-- | :-- | :-- | :-- | :-- |
| A2-1 | GET | `/api/trade/admin/dashboard` | `user:admin` | 类级 `@PreAuthorize`（:30） | **G2** `user:admin` 声明 `path=/api/admin/**`，不覆盖 `/api/trade/admin/**` |
| A2-2 | GET | `/api/trade/admin/orders/page` | `user:admin` | 类级 `@PreAuthorize`（:30） | **G2** 同上 |
| A2-3 | GET | `/api/trade/admin/stats/sales` | `user:admin` | 类级 `@PreAuthorize`（:30） | **G2** 同上 |
| A2-4 | GET | `/api/trade/admin/stats/top-products` | `user:admin` | 类级 `@PreAuthorize`（:30） | **G2** 同上 |
| A2-5 | GET | `/api/trade/admin/stats/trend` | `user:admin` | 类级 `@PreAuthorize`（:30） | **G2** 同上 |

> 小计 **A2 = 5** · G1 0 ／ G2 5 ／ G3 0（累计 60）

**S1 · `/api/seller/**` — 商家端族 —— 19 条**

| # | 方法 | 路径 | 权限位（真源） | 判定依据 | 缺口 |
| :-: | :-- | :-- | :-- | :-- | :-- |
| S1-1 | POST | `/api/seller/apply` | 🔴 无 | 无任何守卫（`MerchantController.java:50` 只取 `UserContext.getUserId()`） | **G1** |
| S1-2 | GET | `/api/seller/coupon` | 🔴 无 | 无任何守卫（`MerchantCouponController.java:43`） | **G1** |
| S1-3 | POST | `/api/seller/coupon` | 🔴 无 | 无任何守卫（`:37`） | **G1** |
| S1-4 | PUT | `/api/seller/coupon/{couponId}/disable` | 🔴 无 | 无任何守卫（`:49`） | **G1** |
| S1-5 | GET | `/api/seller/finance/balance` | 🔴 无 | 无任何守卫（`SellerFinanceController.java:47`） | **G1** |
| S1-6 | GET | `/api/seller/finance/settlement/{id}` | 🔴 无 | 无任何守卫（`:33`，靠 `getSellerSettlement(id, userId)` 归属过滤） | **G1** |
| S1-7 | GET | `/api/seller/finance/settlement/page` | 🔴 无 | 无任何守卫（`:26`） | **G1** |
| S1-8 | POST | `/api/seller/finance/withdrawal` | 🔴 无 | 无任何守卫（`:53`） | **G1** |
| S1-9 | GET | `/api/seller/finance/withdrawal/page` | 🔴 无 | 无任何守卫（`:61`） | **G1** |
| S1-10 | GET | `/api/seller/knowledge` | 🟡 `ROLE_SELLER`（方法体内） | `KnowledgeBaseController.java:82` `if (!UserContext.hasRole("ROLE_SELLER")) throw Forbidden` | **G3** |
| S1-11 | POST | `/api/seller/knowledge` | 🟡 `ROLE_SELLER` | 同上（`:58` 调 `assertSeller()`） | **G3** |
| S1-12 | DELETE | `/api/seller/knowledge/{id}` | 🟡 `ROLE_SELLER` | 同上（`:75`） | **G3** |
| S1-13 | PUT | `/api/seller/knowledge/{id}` | 🟡 `ROLE_SELLER` | 同上（`:66`） | **G3** |
| S1-14 | GET | `/api/seller/shop` | 🔴 无 | 无任何守卫（`MerchantController.java:112`，@Deprecated 别名） | **G1** |
| S1-15 | PUT | `/api/seller/shop` | 🔴 无 | 无任何守卫（`:123`） | **G1** |
| S1-16 | GET | `/api/seller/shops` | 🔴 无 | 无任何守卫（`:61`） | **G1** |
| S1-17 | PUT | `/api/seller/shops/{shopId}` | 🔴 无 | 无任何守卫（`:67`，靠店铺归属校验） | **G1** |
| S1-18 | GET | `/api/seller/shops/{shopId}/decoration` | 🔴 无 | 无任何守卫（`:83`） | **G1** |
| S1-19 | PUT | `/api/seller/shops/{shopId}/decoration` | 🔴 无 | 无任何守卫（`:99`） | **G1** |

> 小计 **S1 = 19** · G1 15 ／ G2 0 ／ G3 4（累计 79）。**这一族是 S0 的主要工作量**：19 条里 19 条都不在权限位体系内。

**S2 · `/api/trade/seller/**` — 商家端交易域族 —— 5 条**

| # | 方法 | 路径 | 权限位（真源） | 判定依据 | 缺口 |
| :-: | :-- | :-- | :-- | :-- | :-- |
| S2-1 | GET | `/api/trade/seller/dashboard` | `seller:admin` | 类级 `@PreAuthorize`（:24） | **G2** `seller:admin` 声明 `path=/api/seller/**`，不覆盖 `/api/trade/seller/**` |
| S2-2 | GET | `/api/trade/seller/dashboard/product-stats` | `seller:admin` | 类级 `@PreAuthorize`（:24） | **G2** 同上 |
| S2-3 | GET | `/api/trade/seller/order/{orderNo}` | `seller:admin` | 类级 `@PreAuthorize`（:18） | **G2** 同上 |
| S2-4 | POST | `/api/trade/seller/order/{orderNo}/ship` | `seller:admin` | 类级 `@PreAuthorize`（:18） | **G2** 同上 |
| S2-5 | GET | `/api/trade/seller/order/page` | `seller:admin` | 类级 `@PreAuthorize`（:18） | **G2** 同上 |

> 小计 **S2 = 5** · G1 0 ／ G2 5 ／ G3 0（累计 84）。反向缺口同样成立：`/api/seller/**` 的 19 条**没有一条**挂 `seller:admin`——权限位声明指向的那一族恰恰是裸的。

**X1 · 商家受众 · 落在买家前缀下 —— 8 条**

| # | 方法 | 路径 | 权限位（真源） | 判定依据 | 缺口 |
| :-: | :-- | :-- | :-- | :-- | :-- |
| X1-1 | POST | `/api/item/freight-template` | 🔴 无 | 无守卫；`requireSellerId()` 只验「已登录」（`:62-67`） | **G1** |
| X1-2 | DELETE | `/api/item/freight-template/{id}` | 🔴 无 | 同上（`:55`） | **G1** |
| X1-3 | PUT | `/api/item/freight-template/{id}` | 🔴 无 | 同上（`:42`） | **G1** |
| X1-4 | GET | `/api/item/freight-template/list` | 🔴 无 | 同上（`:49`） | **G1** |
| X1-5 | POST | `/api/item/review/{id}/reply` | 🔴 无 | 无守卫；`replyToReview(sellerId,…)` 靠 `sellerId` 语义（`ReviewController.java:67`） | **G1** |
| X1-6 | POST | `/api/item/spu` | 🔴 无 | 无守卫；`sellerId = UserContext.getUserId()`（`SpuController.java:141`） | **G1** |
| X1-7 | PUT | `/api/item/spu/{spuId}` | 🔴 无 | 无守卫（`:148`） | **G1** |
| X1-8 | PUT | `/api/item/spu/{spuId}/status` | 🔴 无 | 无 `@PreAuthorize`；方法内 `UserContext.hasPermission("user:admin")` 只用于**放行管理员越权**（`:162`），不构成商家准入 | **G1** |

> 小计 **X1 = 8** · G1 8 ／ G2 0 ／ G3 0（累计 92）

**X2 · 管理受众 · 落在买家前缀下 —— 3 条**

| # | 方法 | 路径 | 权限位（真源） | 判定依据 | 缺口 |
| :-: | :-- | :-- | :-- | :-- | :-- |
| X2-1 | POST | `/api/item/category` | `user:admin` | 方法级 `@PreAuthorize`（:35） | **G2** `user:admin` 声明 `path=/api/admin/**`，不覆盖 `/api/item/category` |
| X2-2 | DELETE | `/api/item/category/{id}` | `user:admin` | 方法级 `@PreAuthorize`（:47） | **G2** 同上 |
| X2-3 | PUT | `/api/item/category/{id}` | `user:admin` | 方法级 `@PreAuthorize`（:41） | **G2** 同上 |

> 小计 **X2 = 3** · G1 0 ／ G2 3 ／ G3 0（累计 **95**）

### 5.1 三类缺口的汇总

| 缺口 | 条数 | 明细 | 对 S0 的含义 |
| :-- | :-: | :-- | :-- |
| **G1 有端点无权限位** | **23** | S1 的 15 条 ＋ X1 的 8 条 | 商家端「有功能、无授权语义」：任何已登录用户都能调（越权数据由 `sellerId` 归属过滤兜住，但**不是授权**）。S0 必须先补权限位，否则商家端页面一上线就是「靠归属查询碰巧安全」 |
| **G2 权限位声明路径≠实际路径** | **13** | A2 5 ＋ S2 5 ＋ X2 3 | `sys_permission.path` 是后台「角色—权限」勾选框与按路径渲染菜单的唯一依据；声明错则**勾了也不生效 / 生效的看不到**。S0 需重定 `user:admin`/`seller:admin` 的 `path` 口径（建议：要么改 `path` 为 `/**` 级通配并改名，要么按族拆位） |
| **G3 只有角色、无权限位串** | **4** | S1 的 knowledge 4 条 | 用 `ROLE_SELLER` 硬判，绕过了 `sys_permission`；后台无法把「知识库管理」单独授予某个非 SELLER 角色 |
| 干净（—） | 55 | 全部 A1 | 管理端主体已挂 `user:admin`，S0 无需改 |

### 5.2 「有权限位但无端点」

`sys_permission` 种子的 6 个权限位，按「被多少条端点引用」排：

| 权限位 | 声明 path | 挂载端点 | 判定 |
| :-- | :-- | :-: | :-- |
| `*:*:*` | `/**` | 0（不作为注解串出现，仅由 `PermissionService.hasPermi` 作为通配短路） | **有权限位、无端点引用**——语义是「超级管理员」，`PermissionService.java:25-40` 显式短路；属设计如此，不算缺陷，但**它同时使 `user:admin`/`seller:admin` 对 ROLE_ADMIN 失效**（见 5.3） |
| `user:admin` | `/api/admin/**` | 63（A1 55 ＋ A2 5 ＋ X2 3） | 在用，但声明 path 只覆盖 55 条中的一部分（G2） |
| `seller:admin` | `/api/seller/**` | 5（S2）——**且不在 `/api/seller/**` 上** | 在用，但**声明的那一族 0 引用、被守卫的那一族不在声明内**（G2 最严重的一例） |
| `ai:chat` | `/api/ai/**` | 0 | **有权限位、无端点**：`/api/ai/**` 的端点无一挂该位 |
| `order:read` | `/api/trade/order/**` | 0 | **有权限位、无端点** |
| `product:read` | `/api/item/**` | 0 | **有权限位、无端点** |

> 结论：**6 个权限位里 3 个零引用**（`ai:chat`/`order:read`/`product:read`），1 个是通配（`*:*:*`），真正承压的只有 `user:admin` 与 `seller:admin`——**一个位承包了 17 个端点族的授权**。这是 S0 最该动的结构性事实：不是「权限位太少」，而是「一个位管太宽、三个位空转」。

### 5.3 一个必须让 S0 知道的语义分叉（同名字符串，两种判定）

| 判定入口 | 实现 | 语义 |
| :-- | :-- | :-- |
| `@PreAuthorize("@ss.hasPermi('user:admin')")` | `PermissionService.hasPermi`（`ia-common/…/PermissionService.java:33-47`） | 命中 `*:*:*` **或** 角色含 `ROLE_ADMIN` **或** 权限串含 `user:admin` |
| 方法体内 `UserContext.hasPermission("user:admin")` | `UserContext.java:38-41` | **只**看权限串，不含 `ROLE_ADMIN` 短路、不含通配 |

两者在**当前种子**下等价（ROLE_ADMIN 同时持有 id=1 的 `*:*:*` 与 id=5 的 `user:admin`，`Initialize.sql:999`），但只要有一处调整（例如把 `user:admin` 从 ROLE_ADMIN 摘掉、改为只留 `*:*:*`），6 个手工守卫端点与 49 个注解端点的行为就会**分叉**。S0 定权限位方案时，应把「统一走注解」或「统一走 `PermissionService`」写成硬约束。此外，`PermissionService.hasPermi` 对 `ROLE_ADMIN` 无条件放行，意味着**A1/A2/X2 的 `user:admin` 实际只对「非 ADMIN 却有该位」的用户起筛选作用**。

### 5.4 与契约 §七 的一处直接冲突（建议优先裁决）

`/seller/after-sales` 规划调用 `GET /api/admin/after-sale` ＋ `PUT /api/admin/after-sale/{id}/review`（`05 §7.3`），二者要 `user:admin`；而 `ROLE_SELLER` 的种子权限只有 id 2/3/4/6（`Initialize.sql:996`），**不含 id 5 `user:admin`** ⇒ 商家打开售后页必 403。二选一：给 ROLE_SELLER 补 `user:admin`（粗，且同时放开全部管理端），或给商家售后单独开一个 `seller:admin` 守卫的端点（细，但要动契约）。

---

## 六、S0「基座」当初为什么被取消（取证结论）

### 6.1 结论：**没有找到取消记录**

全仓库（含 `project-docs/`、`Implementation/`、仓库根散落文件，排除 `node_modules`/`.git`）对「S0 / 基座」的**命中只有 1 处**，且该处**只陈述「曾被取消」这一事实，未给出理由、未给出时间、未给出决策人**：

> `project-docs/collaboration/open-items.md:198`
>
> `| 3 | **Web 角色端开发** | seller / admin 两个 app（现状：各 1 个"开发中"占位页）。用户定的优先级：**Web 各角色平台先补完**。S0（基座）曾被取消，需重启 |`

（该行在 `HEAD`（`96acb0c2`）与工作树中**逐字相同**，行号均为 198；已用 `git show HEAD:project-docs/collaboration/open-items.md | grep -n "S0"` 核对。）

引入该行的提交是 `2dc51b98 docs(collaboration): 新增 open-items.md 收口清单`（2026-10-18 02:05:40 +0800）。其提交信息逐条说明了「已完成 7 个主题 / 待办三层 / 已知未修 9 项 / 验证边界 / 环境事实 / 4 条判据」，**同样没有解释 S0 为何被取消**。

### 6.2 检索过的范围（命令与结果）

| # | 检索 | 命令（节选） | 结果 |
| :-: | :-- | :-- | :-- |
| 1 | 全 `project-docs` 找 `S0` | `grep -rn "S0" project-docs --include=*.md` | **仅 `open-items.md:198`** |
| 2 | 全 `project-docs` 找 `基座` | `grep -rn "基座" project-docs --include=*.md` | 同上，仅 1 处 |
| 3 | 全仓找 `基座`（不限扩展名） | `grep -rln "基座" .`（排除 `node_modules`/`.git`） | 仅 `project-docs/collaboration/open-items.md` |
| 4 | 取消类关键词 | `grep -rn "暂缓\|取消\|推后\|搁置\|deferred\|cancelled\|postponed" project-docs --include=*.md` | 命中均为**业务语义**的「取消」（订单取消、优惠券取消、点赞取消…）与 ADR-013 的「命名空间暂缓迁移」，**与 S0 无关** |
| 5 | git 历史（内容级） | `git log --oneline --all -S"S0" -- .` | 首条即 `2dc51b98`（引入 open-items 的那次，唯一真实命中）。其余命中（`96acb0c2`/`de869801`/`24d99fa3`）逐条 `git show <commit> -S"S0"` 看过，**全部是 base64 开发密钥字面量里的子串**（`'aWNlLWFtZXJpY2Fuby1kZXYtYWVzLWtleS0zMmJ5dGU='` 中的 `…tleS0z…`），与阶段无关 |
| 6 | git 历史（`基座`） | `git log --oneline --all -S"基座"` | **只有 `2dc51b98`** —— 说明「基座」这个词是那次提交才出现的 |
| 7 | 前端域阶段模型 | `grep -n "Phase\|阶段" project-docs/frontend/18-Frontend-Implementation.md`、`21 §五` | Phase 模型是 **1–6**（`18 §三`）＋ 阶段 **0a/0b/1/2/3/4**（`21 §五`），**没有叫 S0 的阶段**；`21 §五` 的「阶段 0a 契约收敛 / 0b 后端放行面修复」与「S0 基座」是否同一事物，**无任何文档把它俩关联，故不能等同** |
| 8 | ADR 目录 | `ls project-docs/adr/`（001–066 ＋ `README.md`）＋ 由 #1/#2 的全库 grep 覆盖（`adr/` 在检索范围内，0 命中） | 无任何 ADR 文件名或正文涉及 S0 / 基座 / 角色端取消 |
| 9 | 代码历史（角色端是否建过又删） | `git log --oneline --all -- Implementation/front-end/apps/{admin,seller}`；`git log --all --diff-filter=D --name-only -- …apps/admin …apps/seller` | `apps/admin`/`apps/seller` 全历史只有 4 次提交（`04c7e6d4` 建、`becb977c`/`f9a1c406` 改 React 版本、`d4093692` 加 Docker），**没有任何文件被删除** ⇒ 「S0 基座」从未以代码形态落过仓库，因此不会有「删掉即取消」的痕迹 |
| 10 | 契约/任务文档 | `project-docs/collaboration/07-Tasks.md`、`contract/05 §七/§十二`、`product/01`、`product/16` | 无 S0 相关条目；`07-Tasks` 的交付记录按批次（P1/P2/P3、批次 D/F/G/H/U）组织，**没有 S0 批次** |

### 6.3 「当时给的理由」——**无法逐条摘录，因为不存在**

要求是「逐条摘录，不要转述成你的判断」。实际情况是：

- 取消记录里**没有任何理由文本**（`open-items.md:198` 全文见上，仅 1 句「S0（基座）曾被取消，需重启」）；
- 没有 ADR、没有任务条目、没有提交信息、没有代码删除痕迹可反推理由。

因此 **§6.4 的「是否仍成立」一栏无对象可评**。为避免用推测填表，下面只列**能从证据里核实的部分**：那条记录中「事实性前提」今天是否仍成立。

### 6.4 记录中的事实性前提 vs 今天（这部分有证据）

| # | 记录中的断言（`open-items.md:198` 原文） | 今天是否成立 | 证据 |
| :-: | :-- | :-- | :-- |
| 1 | 「seller / admin 两个 app（现状：各 1 个"开发中"占位页）」 | **仍成立** | `apps/admin/app/page.tsx:1-11`（「📊 平台管理后台」「Dashboard — 开发中」）；`apps/seller/app/page.tsx:1-11`（「🏪 商家后台」「Dashboard — 开发中」）；两目录各 11 个文件、各 1 个 `page.tsx`、**0 处 API 调用**（表 B-24/B-25） |
| 2 | 「用户定的优先级：Web 各角色平台先补完」 | **无证据可评**（这是产品优先级声明，仓库内无第二处佐证；本轮任务提示词与之一致，故按现行指令执行） | 仅此一行 |
| 3 | 「S0（基座）曾被取消」 | **无证据可核**（见 §6.2：无任何旁证；且「基座/S0」一词在 git 历史中首次出现就是这一行） | — |
| 4 | 「需重启」 | **无证据可评**；本轮任务即「测量」，未获授权开码 | — |

### 6.5 给主 agent 的建议（不作事实陈述，仅标明是建议）

1. **请把「S0 到底是什么」用一句话定义下来并落文档**（例如：`packages/api` 生成式客户端 ＋ 角色路由守卫 ＋ 权限位收敛 ＋ admin/seller 布局壳）。目前仓库里「S0」没有定义、没有验收判据，任何「重启 S0」的排期都无从验收。
2. 若「S0」确实等于 `21 §五 阶段 0b＋1`（契约收敛 ＋ 生成 API 层 ＋ 认证骨架），那么**它当时的阻断条件今天大部分已消失**：`extract-endpoints.mjs` 实测 `codeOnly=0`、现有前端 55/55 端点全部有实现（表 B）。**仍成立的部分**见 §五（23 条无权限位、13 条 G2、商家售后 403 冲突）。——但**这只是「若等同」的推论，不是取证结论**。

---

## 七、集合自检（数量对不对得上）

### 7.1 三个集合

| 集合 | 定义 | 大小 | 来源 |
| :-- | :-- | :-: | :-- |
| **A** | 表 A 的端点集（admin/seller 论域） | **95** | 表 A 小计 55＋5＋19＋5＋8＋3 |
| **B** | 表 B 中被现有页面消费的端点集（方法＋路径去重） | **55** | 表 B-1…B-23 去重（B-24/B-25 为 0） |
| **无入口集** | 表 A 中「现有前端入口」为「无入口」的行 | **95** | 表 A 第 5 列全为「无入口」 |

### 7.2 三条等式

| 检查 | 期望 | 实测 | 结论 |
| :-- | :-- | :-- | :-: |
| `A = (A ∩ B) ∪ 无入口集` | `95 = 0 + 95` | `A ∩ B = ∅`（逐条比对：A 的 `/api/item/spu/{spuId}` 只有 `PUT`，B 只有 `GET`；A 的 `/api/item/review/{id}/reply` 与 B 的 `/api/item/review/{id}/like` 路径不同；A 的 `/api/item/category` 写端点 B 未调用）→ `95 = 0 + 95` | ✅ |
| `C 的端点集 = A 的端点集` | `95 = 95` | 表 C 小计 55＋5＋19＋5＋8＋3 = 95，逐行编号 A1-1…X2-3 与表 A 一一对应 | ✅ |
| 表 C 缺口分解 = 95 | `55 ＋ 23 ＋ 13 ＋ 4 = 95` | 干净 55（A1）＋ G1 23（S1 15 ＋ X1 8）＋ G2 13（A2 5 ＋ S2 5 ＋ X2 3）＋ G3 4（S1 knowledge） | ✅ |

### 7.3 与全库端点数的闭合

| 项 | 数 | 说明 |
| :-- | :-: | :-- |
| 后端代码端点（方法＋路径去重） | **289** | `extract-endpoints.mjs` 实测，含 `/internal/**` 63 |
| 其中 `/internal/**` | 63 | 排除在论域外 |
| 剩余对外端点 | 226 | 289 − 63 |
| 论域 A（admin/seller） | 95 | 与 B **无交集** |
| 表 B（页面消费，买家端） | 55 | 全部 ✅ 有实现 |
| 其余对外端点 | 76 | 226 − 95 − 55 = 76（买家端未被前端消费的公开/私有端点，与本文论域无关） |
| 闭合校验 | `95 ＋ 55 ＋ 76 ＋ 63 = 289` | ✅ |

### 7.4 与任务给定数字的核对（两个原始数字的复算）

| 给定 | 本轮实测 | 差异定位 |
| :-- | :-- | :-- |
| 「65 admin」 | **代码侧 60**（`/api/admin/**` 55 ＋ `/api/trade/admin/**` 5）；**契约侧 65**（`05 §四` 的 `/api/admin/**` 60 条 ＋ `/api/trade/admin/**` 5 条） | 65 **正是契约侧口径**。契约比代码多 5 条 `/api/admin/**`，差集是 `GET /api/admin/application`、`PUT /api/admin/application/{id}/review`、`GET /api/admin/coupon`（AD26）、`GET /api/admin/spu/page`、`PUT /api/admin/spu/{id}/status`（AD38/39）——全部是契约标 ⚪ 未实现的计划项 |
| 「30 seller」 | **代码侧 24**（`/api/seller/**` 19 ＋ `/api/trade/seller/**` 5）；**契约侧 31**（`/api/seller/**` 24 ＋ `/api/trade/seller/**` 7） | 30 与两侧都不精确吻合：若按「契约 seller 24 ＋ 代码 trade/seller 5」＝29，按「契约 24 ＋ 契约 7」＝31。**口径无法复现，建议以本文 24（代码）为准**；契约多的 5 条 `/api/seller/**` 是 `templates` 4 条（CH6–CH9，代码无实现）＋ `GET /api/seller/apply`（T26，实测仅 POST）；`/api/trade/seller/**` 的 2 条差额是 `PUT …/{orderNo}/price` 与 `PUT …/{orderNo}/remark`（T13/T14，代码无实现） |

---

## 八、未核实 / 边界 / 转派

### 8.1 未核实（本轮明确没做的事）

| # | 未核实项 | 原因 | 影响 |
| :-: | :-- | :-- | :-- |
| 1 | **前端页面能否真的跑起来** | 本轮不要求构建，未执行 `pnpm build`/`next dev` | 表 B 只证明「代码里有这条调用」，**不证明渲染成功、不证明联调通过** |
| 2 | **网关可达性未运行时验证** | 未起 Nacos/Redis/MySQL（`open-items.md` 记录 Nacos/Redis 不可达） | §3 的「网关可达」是按 `gateway-service/src/main/resources/application.yml` 的 **20 个路由块 / 48 条 path 谓词**（39 条列表式 ＋ 9 条内联式，实测计数）做**静态前缀匹配**得出的：15 条 `/api/admin/*` 二级前缀（`home/kg/after-sale/finance/invoice/coupon/roles/permissions/seller/pay/currency/flash/promotion/task/group-buy`，覆盖 A1 的 51/55）全部位于第 153 行的 `/api/admin/**` 兜底**之前**；剩余 4 条（`/api/admin/log`、`/api/admin/user/**`）落到兜底，而兜底目标 user-service 正是 `AdminLogController`/`AdminUserController` 的宿主服务；`/api/trade/{admin,seller}/**` 由 `/api/trade/**` 命中，`/api/seller/{finance,coupon}/**` 与 `/api/seller/knowledge/**` 分别排在宽前缀 `/api/seller/**` **之前**。**路由顺序的运行时行为未测** |
| 3 | **权限位在运行时的实际效果未验证** | 同上（需起 authorization-service ＋ 三个身份） | 表 C 只给「代码里声明了什么」。`ROLE_SELLER` 调 `/api/admin/after-sale` 是否真 403，是按 `PermissionService.hasPermi` 的实现**推断**，未实机验证 |
| 4 | **`X1`/`X2` 的受众归属含判断成分** | 后端没有「受众」字段，只能按身份来源归类 | 若 S0 采用更严格的「路径口径」，则论域应缩到 **84**（差额 11 条见 X1/X2）；两种口径的差已在 §二 与 §三 明示 |
| 5 | **`apps/mobile`（1 个文件 / 0 行）未纳入** | 空壳，不构成平台 | 不影响 95／55 |
| 6 | **契约侧 43 条 `docOnly`（计划中端点）未逐条判定** | 超出本文范围（属契约域后继工作） | 与本文论域的交集只有 5 条 admin ＋ 5 条 seller（见 §7.4），已单独指出 |

### 8.2 本轮发现的漂移（**转派**，不越域修改）

| # | 文档说 | 代码/实测是 | 处理 | 归属 |
| :-: | :-- | :-- | :-- | :-- |
| 1 | [21-Frontend-Refactor-Readiness.md:97](21-Frontend-Refactor-Readiness.md)「12 个管理端点无鉴权（`AdminPay`/`AdminAfterSale`/`AdminFinance`/`AdminLog` 缺 `@PreAuthorize`）」 | `AdminAfterSaleController:33`、`AdminFinanceController:24`、`AdminLogController:15` **已有类级 `@PreAuthorize`**；`AdminPayController` 4 条走方法体内 `assertAdmin()`（`:129`）。**该处已过期**（残留真问题只有 5.3 的语义分叉与 5.4 的种子冲突） | 改文档 | 【转派 doc-engineer（frontend 域 21）】 |
| 2 | [21 §二 2.1](21-Frontend-Refactor-Readiness.md) 写「实测基准：代码 286 个端点」 | 本轮实测 **289**（内部 63、admin 55） | 改文档 | 【转派 doc-engineer】 |
| 3 | [21 §三](21-Frontend-Refactor-Readiness.md) 「9 条死路径 / 前端错 7 组前缀」 | 现有活调用 **0 条死路径**（55/55 全部有实现）；`/api/user/address/*` 四条**后端就是这种实现**（`AddressController.java:35/41/54/70`），不是前端错 | 改文档（该表应标注「已收敛」） | 【转派 doc-engineer】 |
| 4 | [18-Frontend-Implementation.md:69](18-Frontend-Implementation.md) 记 admin「9 文件/93 行」、seller「8 文件/85 行」 | 旧值口径未声明、作废。**复测（2026-10-18，命令与口径见下）**：`apps/admin` 全部文件 **11** / `ts`·`tsx` **5** / `app/` 下 **4**；`apps/seller` 全部文件 **10** / `ts`·`tsx` **4** / `app/` 下 **4**。行数本轮未复测 | 改文档（18/21 已按此订正为 11 / 10） | ✅ 已由 doc-engineer 处理 |

> **文件数的口径（原写「各 11 个文件」是错的，此处订正）**：三条命令给三个数，**必须写明用的是哪条**，否则又是「文档说 X、命令给 Y」：
> ```bash
> find Implementation/front-end/apps/<admin|seller> -type f -not -path '*/node_modules/*' | wc -l   # 11 / 10
> find Implementation/front-end/apps/<admin|seller> -type f \( -name '*.ts' -o -name '*.tsx' \) -not -path '*/node_modules/*' | wc -l  # 5 / 4
> find Implementation/front-end/apps/<admin|seller>/app -type f | wc -l                              # 4 / 4
> ```
> **11 vs 10 的差不是规模差**：差的唯一文件是 `apps/admin/next-env.d.ts`（Next.js 生成物，seller 侧没有）。所以「文件数」这种口径既不稳定也没有信息量 —— 判断"空壳"应看**活调用数**（`grep -rnE 'fetch\(|axios|/api/' apps/{admin,seller}` → 仅 2 处 Dockerfile 引用）与 **`app/` 下路由页数**（各 4，其中 `page.tsx` 各 1）。
| 5 | [contract/05 §7.3](../contract/05-API-Specification.md) `/seller/after-sales` → `GET/PUT /api/admin/after-sale*` | 该两端点要求 `user:admin`，`ROLE_SELLER` 不持有该位（`Initialize.sql:996`）⇒ 商家端按此实现必 403 | **需产品裁决**（补种子位 / 新开商家端点） | 【需仲裁 → 主 agent】本文属契约域，但改结论要动 RBAC 种子（后端域），不单方面改 |
| 6 | [contract/05 §7.4](../contract/05-API-Specification.md) 管理端/商家端页面表 | 表 A 有 **31 条**端点§七无对应行（见 §3.1 第 1 条） | 契约层可补「无 UI 规划端点」小节；**或**产品侧裁决下线 | 【需仲裁 → 主 agent】（是否给 currency/kg/task/promotion/group-buy/invoice 补管理页） |
| 7 | 本文新增（`frontend/23-…`）未登记进 `00-README.md` | `00-README.md` 的「结构与索引」按 [ADR-053 §一 ④](../adr/053-doc-ownership-by-nature-and-two-layer-standards.md) 归 **doc-engineer 独有**，本角色无权改 | 请主 agent 分派登记，**现成一行**见下 | 【转派 doc-engineer】 |

> **待登记的一行（可直接粘贴到 `00-README.md` → `## frontend 前端域` 表格末尾）**：
>
> `` `| [23-Frontend-Platform-Gap-Matrix.md](./frontend/23-Frontend-Platform-Gap-Matrix.md) | 前端角色端补完缺口矩阵 — admin/seller 端点（95）· 页面入口 · 权限位三类缺口 ＋ S0 取证 | product-manager（需求/契约域测量） | 角色端端点、页面或权限位变化时 |` ``

### 8.3 本文与其他域文档的边界

- 本文**不改** `Initialize.sql`、`CLAUDE.md`/`AGENTS.md`、`open-items.md`、任何代码。
- 本文只写 `project-docs/frontend/23-Frontend-Platform-Gap-Matrix.md` 一个文件。
- 文中对 `@PreAuthorize`/RBAC 的分析是**测量与转述**，权限方案的设计决策属后端域（java-architect）与主 agent。

---

## 九、变更记录

| 版本 | 日期 | 变更内容 |
| :-- | :-- | :-- |
| V1.0 | 2026-10-18 | 首版（`96acb0c2` 起测，`51b0d904` 上复核，行号按后者）：表 A（95 条端点 → 页面，6 组小计）、表 B（25 个页面 → 55 条端点，55/55 有实现）、表 C（95 条端点 → 权限位，G1 23 / G2 13 / G3 4）、S0 取消取证（结论：**无取消记录**，唯一命中 `open-items.md:198`）、集合自检（三条等式 ＋ 289 闭合）、7 项转派/仲裁清单 |
