# RBAC 权限矩阵草案（待人工评审 — 未执行）

> **状态**：<b>草案，未落码</b>。按约定「业务边界不交给 AI 拍」，本文件只给现状实测 + 拟议 + 待裁决项。
> **评审人**：项目 owner。**评审通过后**才执行拆分（顺序见 §6）。
> **本批范围**：**RBAC0 +（尽可能）统一声明式 `@PreAuthorize`**。**数据权限（data scope / 行级归属）另立项**，
> 不在本批——它需要新的模型（店铺/部门维度 + 查询层强制注入），不是加几个权限串能解决的。

---

## 1. 现状实测（不是推断）

| 项 | 实测结果 | 证据 |
|---|---|---|
| 权限位总数 | **6 个**（`*:*:*` / `ai:chat` / `order:read` / `product:read` / `user:admin` / `seller:admin`） | `Initialize.sql` 的 `sys_permission` 种子 |
| 被 `@PreAuthorize` 消费的位 | **2 个**：`user:admin`（21 处）、`seller:admin`（2 处） | 全仓 grep |
| 无消费的位 | `ai:chat` / `order:read` / `product:read` —— **零注解引用**（`PermissionService` 的 javadoc 却拿 `order:read` 当示例） | 同上 |
| `sys_permission.path` | **空设计**：只在 `PermissionVO` / PO→domain 里被读出来，**没有任何「路径→所需权限」的解析层** | 全仓 grep `getPath()` |
| 声明式覆盖率（类级） | `adapter/admin` 12/14 有类级注解；**6 个受管控制器没有类级 `@PreAuthorize`** | 门禁实测（见 §2） |
| 管理端资金面 | `AdminPayController`（退款重试 / 对账执行）与 `AdminMerchantController` 用**代码内 `assertAdmin()`**，不是注解 | 两类的 `assertAdmin()` |
| 判定路径 | **三条**：① `@ss.hasPermi`（`PermissionService`）② `UserContext.hasPermission` ③ 控制器内 `hasRole` | 见 §3 |
| method security 可用性 | `spring-boot-starter-security` 在 `ia-common` 是 **`optional`**，仅 **6/10** 服务声明 ⇒ **pay / merchant / ai / logistics 用不了 `@PreAuthorize`** | 各模块 pom |
| 前端判定 | **完全没有**（`hasPermission` 等 grep 0 命中）⇒ 后端是唯一权威，但也无入口隐藏 | 全仓 grep |

**结论**：当前是「管理员 / 非管理员」两个档位。做不了岗位分离（运营只读 / 客服只售后 / 财务只开票）。

---

## 2. 已在本次落地（不需评审，属安全增强）

| 改动 | 为什么不需要评审 |
|---|---|
| **移除 `PermissionService` 的「`roles` 含 ROLE_ADMIN 即放行」短路** | 前提已核实：`ROLE_ADMIN` 持有 `*:*:*`（`sys_role_permission (4,1)`），管理员仍经通配符全通，**行为不变**；但从此可以给管理员**收窄** |
| **`UserContext.hasPermission` 对齐通配符** | 消除第三条判定路径的隐性分叉（见 §3），今天不改变任何人的结论，只是把「万一收窄就踢管理员」的雷拆掉 |
| **新增门禁 `AdminEndpointAuthorizationGuardTest`** | 让「新增 `/api/admin`、`/api/seller` 控制器忘了鉴权」变成构建失败。豁免表当前 4 条，**全部标「待矩阵裁定」+ deadline 2026-11-15** |

> 门禁上线时就抓到两处我先前的误判：`AdminPayController` 与 `AdminMerchantController` 的**全部映射方法
> 都没有注解**（只有代码内 `assertAdmin()`），而它们的 javadoc 提到 `@PreAuthorize`，
> 朴素扫描会误判成「已有注解」。**扫描必须先剥注释**（已修）。
>
> 这两类**无法**补注解：pay / merchant 没有 `spring-boot-starter-security`，`@PreAuthorize` 编译都过不了。
> 所以它们的豁免不是「放行」，而是**可验证的替代条件** —— 门禁强制校验
> 「映射方法个数 == `assertAdmin();` 调用次数」，不成立即红。这比只写一句理由可靠。

---

## 3. 三条判定路径的收敛（这是「统一」的真正含义）

| 路径 | 认通配符 `*:*:*` | 认 ROLE_ADMIN 短路 | 可 grep 审计 | 用在 |
|---|---|---|---|---|
| `@ss.hasPermi(...)` → `PermissionService` | ✅（改造后仅此一条通路） | ❌（已移除） | ✅ | 6 个有 security 的服务 |
| `UserContext.hasPermission(...)` | ✅（本次已对齐） | ❌ | ⚠ 需 grep 方法名 | pay / merchant / ai / logistics 的 `assertAdmin()` |
| 控制器内 `hasRole("ROLE_SELLER")` | — | — | ⚠ | `KnowledgeBaseController` |

**目标态**：只剩第 1 条。前置是给 4 个无 security 依赖的服务补 `spring-boot-starter-security`
（或在 `ia-common` 里改为非 optional —— 但那会让所有服务被迫引 security，需权衡）。
**这是待裁决项 Q5。**

---

## 4. 拟议权限位（待裁决）

命名规范：`资源:动作`，全小写，冒号分隔；动作取 `read` / `write` / `admin` / `issue` / `refund`。
**不做** `资源:子资源:动作` 三级（当前端点规模不需要，且解析成本高）。

### 4.1 管理端（`/api/admin/**`）

| 控制器 | 端点 | 现用位 | 拟议位 |
|---|---|---|---|
| `AdminUserController`（用户禁用/状态） | 3 | `user:admin` | `user:write` |
| `AdminLogController`（审计查询） | 1 | `user:admin` | `user:read` |
| `RoleController` / `PermissionController`（RBAC 管理） | 8 | `user:admin` | **`rbac:admin`**（建议单列：改 RBAC 本身应比改业务数据更敏感） |
| `AdminOrderController`（订单/仪表盘） | 5 | `user:admin` | `order:read` |
| `AdminAfterSaleController`（售后审核/退款确认） | 3 | `user:admin` | **`aftersale:write`** |
| `AdminPayController`（退款重试 / 对账执行） | 4 | `assertAdmin('user:admin')` | **`pay:admin`**（资金面，建议独立） |
| `AdminFinanceController`（结算/提现审核） | 4 | `user:admin` | **`finance:admin`**（资金面） |
| `AdminInvoiceController`（开票/作废/冲红） | 4 | `user:admin` | **`invoice:issue`** |
| `AdminMerchantController`（入驻审核） | 2 | `assertAdmin('user:admin')` | **`merchant:admin`** |
| `AdminCouponController` | 2 | `user:admin` | `marketing:admin` |
| `AdminFlashController` | 3 | `user:admin` | `marketing:admin` |
| `AdminPromotionController` | 3 | `user:admin` | `marketing:admin` |
| `AdminTaskController` | 3 | `user:admin` | `marketing:admin` |
| `AdminGroupBuyController` | 3 | `user:admin` | `marketing:admin` |
| `AdminCurrencyController`（汇率维护） | 3 | `user:admin` | **`currency:admin`**（影响全站价格，建议独立） |
| `AdminHomeController`（首页装修） | 4 | `user:admin` | `home:admin` |
| `AdminKgController`（知识图谱/配对规则） | 5 | `user:admin` | `kg:admin` |

### 4.2 商家端（`/api/seller/**`）

| 控制器 | 端点 | 拟议位 | 备注 |
|---|---|---|---|
| `SellerFinanceController` | 5 | **`seller:finance`** | 只读结算/余额 + 申请提现，动作不同但暂共一位 |
| `MerchantCouponController` | 3 | **`seller:coupon`** | 商家自建券 |
| `KnowledgeBaseController` | 4 | **`seller:knowledge`** | 现为代码内 `hasRole`，应改声明式 |
| `MerchantController` | 7 | `seller:admin`（6 个） | **`POST /api/seller/apply` 保持无权限位**（入驻申请，任何登录用户可调）→ 见 Q4 |

### 4.3 角色-权限映射（拟议）

| 角色 | 位 |
|---|---|
| `ROLE_USER` | `ai:chat`、`order:read`(自有)、`product:read` |
| `ROLE_SELLER` | 以上 + `seller:admin`、`seller:finance`、`seller:coupon`、`seller:knowledge`、`product:write`(自有) |
| `ROLE_ADMIN` | `*:*:*`（**唯一持有超级位者**，由 `RbacSeedEscalationGuardTest` 守） |

> 若后续要「收窄管理员」（只留部分高位），必须**同时**改 §3 的第 2 条路径（已对齐通配符）并清缓存。

---

## 5. 待裁决（请逐条给结论，否则不执行）

- **Q1 粒度**：管理端按**域**拆（`user:` / `order:` / `pay:` …）即可，还是要域 × 读/写二维（`user:read` / `user:write`）？
  草案默认「域 + 少量动作」（见 §4.1），因为多数管理端页面读写同权。
- **Q2 资金面是否独立**：`pay:admin` / `finance:admin` / `invoice:issue` / `currency:admin` 建议独立于 `user:admin`
  （否则「客服能退款重试」这种授权无法拒绝）。**是否同意**？
- **Q3 商家端是否拆分**：`seller:admin` 拆成 finance / coupon / knowledge 三位，还是保持一位（商家自己就是老板，岗位分离诉求弱）？
- **Q4 入驻申请**：`POST /api/seller/apply` 保持「任何登录用户可调」（无权限位）——**确认**？
- **Q5 method security**：为 pay / merchant / ai / logistics 补 `spring-boot-starter-security`（统一声明式的前提），
  还是允许代码内 `assertAdmin()` 双轨？**建议补依赖**（否则「统一」只是口号，且第三条路径永远存在）。
- **Q6 RBAC 管理面是否单列 `rbac:admin`**：改角色/权限本身比改业务数据更敏感，建议单列。

---

## 6. 执行顺序（评审通过后，不跳步）

1. **门禁先行**（已完成）：`AdminEndpointAuthorizationGuardTest` 在，后续所有改动都被它兜底。
2. **种子迁移**：`Initialize.sql` 的 `sys_permission` 加新位、`sys_role_permission` 重挂；
   同步 `RbacSeedEscalationGuardTest`（它守「非 ROLE_ADMIN 不得持超级位」，新位不受影响但需跑）。
3. **注解替换**：逐控制器类级替换（门禁保证不漏）。
4. **缓存失效**（不可省）：拆位后**必须**清 `user:perms:*`，否则 TTL 30 分钟内旧权限集继续生效：
   ```bash
   redis-cli --scan --pattern 'user:perms:*' | xargs -r redis-cli DEL
   ```
   并确认 `PermissionCacheEvictor` 在角色/权限变更时已删缓存（已有）。
5. **端到端 403 测试**（不可省，**当前卡环境**）：需要 Nacos + Redis 起 authorization + gateway + 目标服务，
   用「有 A 位、无 B 位」的账号打 B 端点断言 **403**。当前 Nacos/Redis 不可达 ⇒ **本项无法执行**，
   只能留在 open-items（不得因为「单测绿」就宣称鉴权可用）。
6. **前端菜单**（S0 之后）：前端目前**完全不做权限判定**；落地时要补，且必须同时支持
   通配符与（若保留）管理员短路，否则管理员菜单会整体消失。

---

## 7. 不做什么（防止范围蔓延）

- **不做数据权限**（店铺/部门行级过滤）—— 另立项，需先定模型。
- **不引入 ABAC / 属性权限** / 不做权限继承层级。
- **不把 `sys_permission.path` 做成第二套判定** —— 除非另行立项（要么接 Filter 真用，要么在 09-constraints 写明「仅展示」）。
