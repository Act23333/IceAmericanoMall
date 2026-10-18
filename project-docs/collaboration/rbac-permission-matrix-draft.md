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

## 5. 裁决结果（2026-10-18，已定，不再讨论）

| Q | 裁决 | 对实现的影响 |
|---|---|---|
| Q1 | **域 + `{read, write/admin}`，不做全二维**；逐域明确有无 admin 档 | §6 权限位表：每个域只有 **两个** 位（读 + 写/admin），不出现 `域:子资源:动作` |
| Q2 | **资金面独立**（无商量）；`invoice:issue` / `invoice:void` 再拆 | `pay:admin` / `finance:admin` / `currency:admin` 独立；发票拆发行与作废两位 |
| Q3 | **商家端拆三位，默认角色给全集，存量不失权** | `seller:finance` / `seller:coupon` / `seller:knowledge`；`ROLE_SELLER` 迁移时一次性给全 |
| Q4 | **入驻申请无权限位**同意；**补限流**进 open-items | `POST /api/seller/apply` 永久豁免门禁；限流登记（见 open-items §1.17） |
| Q5 | **补依赖，`assertAdmin()` 降级或删，不做双轨** | ✅ **已执行**：4 个 pom + 两个控制器转声明式 + 门禁转正（`e361a8d5` 之后） |
| Q6 | **`rbac:admin` 单列、只给超管、通配符显式排除** | ✅ **已实现** `PermissionBits.WILDCARD_EXCLUDED = {rbac:admin}`，两条判定路径共用 |

> Q5 的执行前提已实测：`MethodSecurityConfig` 是 `@ConditionalOnClass(EnableWebSecurity)` + `@EnableMethodSecurity`
> 且已进 `AutoConfiguration.imports` ⇒ 补依赖后注解**真的生效**（否则就是安全剧场）。
> 四个服务亦无自建 `SecurityFilterChain`，不会触发该配置退避。

---

## 6. 页一 · 角色 → 权限映射（裁决后，待审）

### 6.1 权限位全表（Q1：每域两档）

**计数口径（别再用"25 个"那种说法）**：本表共 **27 行** = **存量保留 4**（`ai:chat`、`product:read`、
`order:read`、`seller:admin`）+ **迁移新增 21**（`user:admin` 1→14、`seller:admin` 1→4、
`rbac:admin` 1、`aftersale:read` 1、`product:admin` 1、`dashboard:read` 1、`audit:read` 1）。`user:admin` 本身不在表内活位之列（迁移后 `status=0` 停用）。

| 权限位 | 含义 | 有 admin 档？ | `*:*:*` 是否覆盖 |
|---|---|---|---|
| `ai:chat` | AI 对话/客服 | 否（单一能力位） | ✅ |
| `product:read` | 商品浏览 | 否 | ✅ |
| `product:write` | 商家维护自有商品 | 否（写即商家档） | ✅ |
| `product:admin` | **平台级商品配置**（类目/品牌/属性）+ 管理员的商品干预 | **是**（Q8） | ✅ |
| `order:read` | 订单查询（含自有） | 否 | ✅ |
| `order:admin` | 订单管理（发货/改状态等后台动作，**不按方法分**） | **是** | ✅ |
| `aftersale:read` | 售后查询 | 否 | ✅ |
| `aftersale:write` | 售后状态操作（**当前无消费者**，见下） | **是** | ✅ |
| `pay:admin` | 退款重试 / 手动对账（资金面，Q2） | **是** | ✅ |
| `finance:admin` | 结算 / 提现审核（资金面，Q2） | **是** | ✅ |
| `invoice:issue` | 开具 / 重开 | **是** | ✅ |
| `invoice:void` | 作废 / 冲红（Q2 再拆） | **是** | ✅ |
| `currency:admin` | 汇率维护 | **是** | ✅ |
| `marketing:read` | 营销活动查询 | 否 | ✅ |
| `marketing:admin` | 券/秒杀/满减/任务/拼团管理 | **是** | ✅ |
| `merchant:admin` | 商家入驻审核 | **是** | ✅ |
| `home:admin` | 首页装修 | **是** | ✅ |
| `kg:admin` | 知识图谱 / 配对规则治理 | **是** | ✅ |
| `user:read` | 用户资料查询（**不含审计日志**） | 否 | ✅ |
| `audit:read` | 审计日志查询（**不挂 user:read**） | 否 | ✅ |
| `dashboard:read` | 平台看板/统计数据（看数据 ≠ 改订单） | 否 | ✅ |
| `user:write` | 用户状态/角色变更 | **是** | ✅ |
| `rbac:admin` | **改角色/权限本身**（Q6） | **是** | ❌ **显式排除**（必须单独授予） |
| `seller:admin` | 店铺资料维护（商家自有） | 否 | ✅ |
| `seller:finance` | 商家结算 / 余额 / 提现申请 | 否 | ✅ |
| `seller:coupon` | 商家券 | 否 | ✅ |
| `seller:knowledge` | 商家知识库 | 否 | ✅ |

**「有 admin 档」= 该域的写操作会改动他人数据/资金/平台配置**，故单独一位；纯读域只给 `read`。
**不做 `域:子资源:动作` 三级**（Q1）——端点规模不需要，且 SpEL 里的串越长越容易写错。

### 6.2 角色矩阵（Q3：默认给全集，存量不失权）

| 角色 | 持有的位 | 说明 |
|---|---|---|
| `ROLE_USER` | `ai:chat`、`product:read`、`order:read` | 全是"自有数据"语义；公开端点本就无 `@PreAuthorize` |
| `ROLE_VIP` | 同 `ROLE_USER` | 会员权益走等级规则（ADR-044），不走权限位 |
| `ROLE_SELLER` | `ROLE_USER` 全集 + `product:write`、`seller:admin`、`seller:finance`、`seller:coupon`、`seller:knowledge` | 迁移时**一次性给全**（Q3「存量不失权」）。<br>**刻意不给 `order:write`**：该位守的是 `/api/admin/order/**`（跨商家后台动作），商家发货走 `/api/seller/**` 由 `seller:admin` 覆盖 —— 把 admin 档位给商家就是"多给"，与 Q3「不失权」不是一回事（不失权指**原有能力**不减，不代表**新增**跨商家能力） |
| `ROLE_ADMIN` | `*:*:*` + **`rbac:admin`（显式）** | 其余位经通配符获得；`rbac:admin` 通配符**不顶**（Q6） |

**商家侧售后：当前没有（实测，2026-10-18）**。全部售后端点只有三个：买家端 `/api/after-sale`、
管理端 `/api/admin/after-sale`、内部 `I42`（`/internal/trade/after-sale`）；`trade-service` 的
`/api/seller/**` 只有 finance。且 `after_sale` 表只有 `order_no` + `user_id`，**没有 seller/shop 维度**。

> **结论写死**：**商家侧暂无售后功能**。将来要引入，**前置是 `after_sale` 补 `seller_id`（或经 order 关联取
> seller）**，然后才谈 `seller:aftersale` 位 —— **权限位不能绕过这个前置**：没有 seller 维度的表，
> 给商家任何售后位都等于让他读/审批别家的售后（这正是 S0-pre 种子守卫拦的那件事）。

**`aftersale:read` 给谁（审阅问题，此处给结论）**：**只由 `ROLE_ADMIN` 经通配符获得**，不给商家。
理由是种子守卫 S0-pre 记录的那个事实：`after_sale` 表**没有 seller 维度**，管理端列表只按状态过滤 ——
一旦给商家这个位，他读到的就是**别家的售后**。商家看自己的售后走 `/api/seller/**`（由 `seller:admin` 覆盖），
不读管理端列表。**这也是"管理端 read 档不能给商家"的具体一例**，与 §7.6 第 2 条判据同源。

**为什么管理员要显式列 `rbac:admin`**：它是唯一「能改变授权本身」的位。若通配符能顶，任何拿到超级位的账号
都能改角色、给自身上位 —— 提权路径没有被单独看守。`PermissionBits` 的排除表刻意用**精确位**而非
`rbac:` 前缀：按前缀自动扩会让「新增一个 rbac 子位」悄悄改变已有账号的授权范围。

---

## 7. 页二 · 迁移映射（现 6 位 → 新位，待审后执行）

### 7.1 位级映射

| 旧位 | 新位 | 备注 |
|---|---|---|
| `user:admin` | `user:read`、`user:write`、`order:write`、`aftersale:write`、`pay:admin`、`finance:admin`、`invoice:issue`、`invoice:void`、`merchant:admin`、`marketing:read`、`marketing:admin`、`currency:admin`、`home:admin`、`kg:admin` | 1 → 14；`user:admin` 是当前**唯一**的管理端开关 |
| `seller:admin` | `seller:admin`、`seller:finance`、`seller:coupon`、`seller:knowledge` | 1 → 4（Q3）。**逐一对上 §6.2**：四个位都在 `ROLE_SELLER` 那一行里，且 §6.1 中四者的「有 admin 档？」均为**否**（商家只动自有数据）。对账口径见 §7.6 |
| `ai:chat` / `order:read` / `product:read` | **保留原名** | 首次真正被 `@PreAuthorize` 消费 |
| （新增） | `rbac:admin` | 只挂 `ROLE_ADMIN`，且**不**由通配符覆盖 |

**买家侧 `read` 档与零消费白名单的关系（审阅问题，必须给结局）**：
`ai:chat` / `order:read` / `product:read` 目前在 `UNUSED_WHITELIST` 里（零消费）。它们是"能力声明"，
但**对买家侧不构成防护** —— 所有登录用户都持这些位，给买家端点挂它们等于没挂。
所以拆位时必须**二选一并在白名单里写死结局**，不许长期挂着：
1. **接线到管理/商家侧**（那里位才有区分度），白名单条目标注"已接线"后删除；
2. 或承认它们是**前端入口显隐**用途 —— 那就不该放在 `sys_permission`（判定表）里，应移到菜单/前端配置表。
**现状提醒**：前端目前**完全不做权限判定**（grep 0 命中），所以第 2 条的"消费者"现在不存在 ——
即这三个位在生产上**没有任何消费者**。白名单不是免责单，它要求一个截止时间。

**停用位的角色关联保留（写死）**：停用只改 `sys_permission.status`，
**`sys_role_permission` 关联行一律保留**。理由：① 重新启用即恢复原状，不需要重建授权；
② 删了就无法回答"这个位曾经给了谁"；③ 装配查询已加 `p.status = 1`，所以**停用对判定即时生效**，
不需要动关联行。**注意缓存**：`user:perms:` 仍有 TTL 30 分钟，停用后必须 `DEL` 才立即生效。

**停用机制只选一条（已定）**：`sys_permission.status = 0` **停用、不删行**。
不选"物理删除"的理由：删了就无法回滚比对，也无法在出问题时一眼看出"这个位曾经存在、被谁用过"。
代价是表里会有历史行 —— 由 `status` 区分，可接受。

**扩权必须人工审**：迁移只允许"位变多、端点可达集不变或变大"。任何**收紧**（某角色失去某个位/某个端点
从可达变不可达）都要在评审里逐个列出并说明是有意为之；**不允许**静默收紧（那是最难查的线上故障）。
投影比对（§7.3-1）会给出不对称结果，评审就按它逐条签。

**端到端用例不得依赖种子**：用例里的账号与权限必须在测试内**显式构造**（直接写 `user:perms:` 缓存或
用测试专用账号 + 明确授予），<b>不得</b>假设"种子里的 ROLE_ADMIN/ROLE_SELLER 恰好是我要的形状" ——
否则种子一改，测试就从"验证鉴权"退化成"验证种子"。同理，断言要打在<b>HTTP 状态码</b>上，不是错误文案。

**旧位不删除、只停用**（`sys_permission.status = 0`）：便于回滚比对；注解改完后若有残留引用，
`AdminEndpointAuthorizationGuardTest` 不会报（它只看"有没有注解"），所以**必须另加一条门禁/脚本**
扫描「注解里出现的位是否都在 `sys_permission` 且 `status=1`」——列为执行批的必做项。

### 7.2 种子 SQL 草案（执行时才落盘，此处只审结构）

```sql
-- ① 新增位（id 从 7 起，保持既有 1–6 不动）。共 21 行 = 14(user:admin) + 4(seller:admin) + rbac:admin + aftersale:read + product:admin
INSERT INTO sys_permission (id, name, code, type, path, sort_order, status) VALUES
  (7,  '用户查询',   'user:read',      3, '/api/admin/user/**',       10, 1),
  (8,  '用户变更',   'user:write',     3, '/api/admin/user/**',       11, 1),
  (9,  '订单管理',   'order:admin',    3, '/api/admin/order/**',      12, 1),
  (10, '售后处理',   'aftersale:write',3, '/api/admin/after-sale/**', 13, 1),
  (11, '支付运维',   'pay:admin',      3, '/api/admin/pay/**',        14, 1),
  (12, '财务结算',   'finance:admin',  3, '/api/admin/finance/**',    15, 1),
  (13, '发票开具',   'invoice:issue',  3, '/api/admin/invoice/**',    16, 1),
  (14, '发票作废',   'invoice:void',   3, '/api/admin/invoice/**',    17, 1),
  (15, '汇率维护',   'currency:admin', 3, '/api/admin/currency/**',   18, 1),
  (16, '营销查询',   'marketing:read', 3, '/api/admin/**',            19, 1),
  (17, '营销管理',   'marketing:admin',3, '/api/admin/**',            20, 1),
  (18, '商家审核',   'merchant:admin', 3, '/api/admin/seller/**',     21, 1),
  (19, '首页装修',   'home:admin',     3, '/api/admin/home/**',       22, 1),
  (20, '知识图谱',   'kg:admin',       3, '/api/admin/kg/**',         23, 1),
  (21, 'RBAC 管理',  'rbac:admin',     3, '/api/admin/roles/**',      24, 1),
  (22, '商品维护',   'product:write',  3, '/api/seller/**',           25, 1),
  (23, '商家结算',   'seller:finance', 3, '/api/seller/finance/**',   26, 1),
  (24, '商家券',     'seller:coupon',  3, '/api/seller/coupon/**',    27, 1),
  (25, '商家知识库', 'seller:knowledge',3,'/api/seller/knowledge/**', 28, 1),
  (26, '售后查询',   'aftersale:read', 3, '/api/admin/after-sale/**', 29, 1),
  (27, '平台商品配置','product:admin', 3, '/api/item/category/**',    30, 1),
  (28, '看板数据',   'dashboard:read',3, '/api/admin/dashboard/**',   31, 1),
  (29, '审计日志查询','audit:read',   3, '/api/admin/log/**',         32, 1);

-- ② ROLE_ADMIN：显式加 rbac:admin（通配符不覆盖它）
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT 4, id FROM sys_permission WHERE code = 'rbac:admin';

-- ③ ROLE_SELLER：一次性给全（Q3 存量不失权）
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT 3, id FROM sys_permission WHERE code IN
  ('product:write','seller:admin','seller:finance','seller:coupon','seller:knowledge','order:write');

-- ④ 旧位停用（不删）
UPDATE sys_permission SET status = 0 WHERE code IN ('user:admin');
```

### 7.3 迁移后的「不失权」验证（必须做，不能靠肉眼）

0. **「位 → 端点」映射从哪来（定死，不手工维护）**：**由门禁/脚本在注解替换后生成** ——
   扫 `@PreAuthorize` 拿位、扫类级 `@RequestMapping` + 方法级 `@XxxMapping` 拿端点，产出
   `controller → 方法 → HTTP 路径 → 权限位` 的 TSV，作为投影比对的输入。
   **理由**：手工维护的表**必漂**，而它漂了没人会发现（比对结果看起来仍然"通过"）——
   这正是本仓反复吃过的亏（注释里复写会漂的清单/计数）。
   实现上可复用两个既有门禁的解析函数（`AdminEndpointAuthorizationGuardTest` 的类级路径解析、
   `PermissionBitReferenceGuardTest` 的注解解析），不另写一套。
1. **投影比对**：建立「端点 → 所需位」映射，对每个角色计算「旧位集合下可达端点集」与「新位集合下可达端点集」，
   断言 **旧 ⊆ 新**（只许放宽不许收紧；任何收紧都必须单独列出并确认是有意的）。
2. **缓存失效**（不可省）：
   ```bash
   # 必须打印清除条数：不打印就无法区分「清了 0 条」和「命令没生效」
   redis-cli --scan --pattern 'user:perms:*' | tee /tmp/perms.keys | xargs -r redis-cli DEL
   echo "cleared=$(wc -l < /tmp/perms.keys)"
   ```
   否则 TTL 30 分钟内旧权限集继续生效，测出来的 403 是假的。
   **「清了 0 条」也要当结论看**：可能是缓存键前缀写错（应与 `UserSecurityCache` 里的
   `user:perms:` 完全一致），不是"本来就没有"。
3. **端到端 403 用例清单**（每条：账号 → 端点 → 期望）：
   - 有 `user:read`、无 `user:write` → `PUT /api/admin/user/{id}/status` = **403**
   - 有 `aftersale:write`、无 `pay:admin` → `POST /api/admin/pay/refund/{no}/retry` = **403**（资金面隔离的核心断言）
   - 有 `*:*:*`、**无** `rbac:admin` → `PUT /api/admin/roles/{id}/permissions` = **403**（Q6 的核心断言）
   - 商家 A 调 `GET /api/seller/finance/balance` 只看得到自己的余额（数据面，非本批）
4. **回滚**：旧位只停用不删 + 注解改动可 `git revert`；缓存失效命令对回滚同样适用。

**端到端 403 的覆盖边界（写死，别把它当"鉴权全验过"）**：

| 层次 | 是否覆盖 | 说明 |
|---|---|---|
| **控制器链路（mock）** | ✅ 覆盖 | 用 `MockMvc` / `standaloneSetup` 装配真实 `@PreAuthorize` 拦截器，注入不同权限集的 `UserContext`，断言 **403 vs 2xx**。它验的是"注解 + 判定函数 + 异常映射"这条链 |
| **DB 层（权限装配 SQL）** | ❌ **未覆盖** | `selectDirectPermCodesByUserId` / `selectRolePermCodesByUserId` 的 `status=1` 过滤、以及角色-权限 join 是否正确，mock 层完全碰不到。这一层现在只由 `PermissionStatusFilterGuardTest`（源码级）兜住"过滤没被删掉"，**不证明 SQL 语义在真实库上正确** |
| **种子正确性** | ❌ 未由 e2e 覆盖 | 靠 **投影比对（§7.3-1）+ 人工审**。即"谁的位集合能到哪些端点"是算出来的、审出来的，不是跑出来的 |
| **网关 → 下游的身份透传** | ❌ 未覆盖 | 需要 Nacos + gateway + 目标服务同时起（当前不可达）。`UserContext` 在真实链路上是否正确重建，至今**没有自动化验证** |

**所以 403 用例通过后的正确说法是**：「控制器链路的鉴权验证通过」，**不是**「鉴权验证通过」。

### 7.4 宣称边界（写死，避免误判「已完成」）

- **端到端 403 用例仍卡 Nacos/Redis，未执行**。在它跑通之前，只能说「单测 + 门禁通过」，
  **不得声称「RBAC 已符合大厂标准」**。
- `sys_permission.path` 仍是**装饰**，本批不接路径解析层（不要顺手做成第二套判定）。
- **数据权限（行级）不在本批**，另立项。

---

### 7.6 归属对账（每次改动本映射表都要重跑，别靠眼看）

**做法**：从 §6.1 表抽出全部位 → 判「有 admin 档」；从 §6.2 抽出每个角色持有的位；
然后机械检查三条，任一不满足就是错（`order:write` 那次就是这条抓出来的）：

1. **1→4 / 1→14 的目标位必须都出现在对应角色的行里**（迁移后的位不能成为"孤儿位"）；
2. **`admin 档 = 是` 的位不得出现在 `ROLE_USER` / `ROLE_VIP` / `ROLE_SELLER` 行里**
   （唯一例外：`ROLE_ADMIN` 经通配符获得，无需逐位列出）——
   这条就是 `order:write` 的错误形态：它是 admin 档，却被写进商家的行；
3. **每个角色持有的位都必须在 §6.1 表里存在**（角色行不能引用不存在的位）。

> 迁移执行时把这三条写成脚本/门禁跑一遍（而不是审阅时肉眼扫），因为 §6 与 §7 会分别改。

### 7.4b Q7 **已裁决（2026-10-18）**：维持单一 ROLE_SELLER，员工角色另立项

Q3 裁决的是**权限位**拆三位（`seller:finance` / `seller:coupon` / `seller:knowledge`），
但**角色**仍只有一个 `ROLE_SELLER` —— 等于"拆了位、没拆角色"，实际效果是**每个商家账号都拿三个位全集**
（正是 Q3「默认角色给全集，存量不失权」的要求）。

若将来要支持**店铺员工**（运营管商品券、财务管结算、老板管一切），需要的是**角色拆分**，不是继续拆位：

| 拟议角色 | 位 |
|---|---|
| `ROLE_SELLER_OWNER` | `seller:*` 全集 + `product:write` |
| `ROLE_SELLER_OPS` | `seller:coupon`、`product:write` |
| `ROLE_SELLER_FINANCE` | `seller:finance` |

**这属于数据模型增量（新角色 + 授予流程 + 商家侧成员管理），不在本批**。
请裁决：**本批维持单一 `ROLE_SELLER`（推荐，符合 Q3「存量不失权」），员工角色另立项**？
还是本批就拆角色（会引入商家子账号体系，工作量远大于拆位）？

### 7.4c Q8 **已裁决（2026-10-18）**：类目挂 `product:admin`；门禁扩前缀分两步

实测发现 `item-service/adapter/web/CategoryController`（`/api/item/category`）的
`POST` / `PUT /{id}` / `DELETE /{id}` 三处**方法级**挂着 `user:admin`
（类级会连带封掉公开的 `GET /tree`，所以只能方法级 —— 这个注释是对的）。
**裁决**：

1. **挂 `product:admin`**，定义为「**平台级商品配置（类目 / 品牌 / 属性）**」，**不新造 `category:admin`**。
   与 `product:write` 的区别写进位表：write 是商家维护**自有**商品，admin 是平台维护**全局**配置。
   已落地：种子新增 id 27；`CategoryController` 三个写方法由 `user:admin` 改挂 `product:admin`。
2. **扩前缀分两步**：
   - **本批**：门禁新增「平台级配置前缀」组（`/api/item/category`、`/brand`、`/attr`），
     允许**方法级覆盖**（类级注解会封掉公开的 `GET /tree`）——规则：`@PreAuthorize` 数 ≥ 写方法数；
     既存缺口进白名单并带截止日期；**新增写端点缺注解 = 硬失败**。
   - **另立项**：逐条补既存缺口（把受管面扩到"所有含写操作的非公开控制器"会一次扫出更多，
     需要先盘点再逐条补，不塞进拆位批）。
   - 已知近似：方法级覆盖是**计数**不是逐方法绑定（可能把注解挂读方法上凑数），
     更严需源码解析器 —— 已在门禁注释里写明，不假装守住了。

### 7.4e 裁决与批前确认（2026-10-18，已定）

| 项 | 裁决 | 依据 |
|---|---|---|
| Q10 | 仪表盘独立 `dashboard:read` | 看数据 ≠ 能改订单；与 `order:admin` 分开 |
| Q11 | RBAC 控制器（`RoleController` / `PermissionController`）**整体挂 `rbac:admin` 含读** | 读角色/权限配置本身即为敏感信息（谁能做什么） |
| 批 3 | 管理端订单统一 **`order:admin`，不按方法分**；原 `order:write` **改名**为 `order:admin`（非新增） | 若按方法分，`order:read` 会**与买家侧同名但语义不同**；改名消除歧义，买家侧 `order:read` 保持不变 |
| 批 8 | `AdminLogController` 挂 **`audit:read`**，**不挂 `user:read`** | 读审计 ≠ 读用户资料 |

**批 4 确认（推翻了原计划，必须记住）**：
`AdminAfterSaleController#review`（type=2 仅退款）与 `#confirm-return`（type=1 退货退款）
**都同步**调 {@code refundClient.refund(...)}（Feign，fail-closed 不吞异常）——
即"审核售后/确认退货"**就是资金动作**，不是状态动作。
⇒ **两个写方法都挂 `pay:admin`**，而**不是** `aftersale:write`。
若按控制器机械挂 `aftersale:write`，后果正是 Q2 要防的**"客服能退款"越权**。
⇒ `aftersale:write` 当前**无消费者**（保留给将来"不涉退款的售后状态操作"；若无则按 §7.1 停用）。

**批 3 两个实测发现（都改变了做法）**：

1. **类级 + 方法级在 Spring 里是 AND 不是覆盖** ⇒ "类级 `order:admin` 兜底 + `dashboard` 方法级例外"
   **不可行**：那样 dashboard 会同时要求 `order:admin` **和** `dashboard:read`。
   故 `AdminOrderController` 改为**全部方法级**：4 个订单域方法 `order:admin`、`GET /dashboard` `dashboard:read`。
2. **它的路径是 `/api/trade/admin`，不在门禁受管前缀里** ⇒ 该控制器**从未被鉴权门禁覆盖**。
   已补前缀并新增「允许方法级」规则（要求注解数 ≥ 全部映射方法数，管理端无公开端点可豁免）。
   **教训**：受管前缀不能按"看起来像管理端"猜，要按实际 `RequestMapping` 扫。

**批 3 确认（已装库必须迁移）**：种子 **8304f976 里落的位是 `order:write`**（不是 `order:admin`）。
本次改名只动了 `Initialize.sql`，而那是**全新安装**脚本 ⇒ **已装库仍是 `order:write`**。
若不迁移，代码注解找 `order:admin` 而库里没有该位 ⇒ **该位不存在 ⇒ 永久 403**。
⇒ 已提供 `scripts/migrate-rbac-order-admin.sql`（幂等 UPDATE + 复核 SELECT），执行完跑 `verify-rbac-seed.sh`。

**批 4 方法清单（实测，必须按方法挂，不能类级）**：`AdminAfterSaleController` 有三个方法 ——
`GET`（分页查询）、`PUT /{id}/review`、`PUT /{id}/confirm-return`。
⇒ 查询挂 **`aftersale:read`**；两个 PUT 都**同步触发退款** ⇒ 挂 **`pay:admin`**。
若整类挂 `pay:admin`，"只想看售后列表的客服"会被迫拿到退款权；若整类挂 `aftersale:read`，则查询也写不了。
**类级在这里是错的两种走法都不通**，必须按方法。

**批 8 切分（已定，含一条提权路径）**：
- `GET /user/page` → `user:read`；
- `PUT /user/{id}/status` → `user:write`；
- `PUT /user/{id}/role` → **`rbac:admin`**（实测：`roleType` 来自**请求参数**，直传
  `authContextClient.setUserRole(id, roleType)`，**可以传 ADMIN** ⇒ 能改角色 = **能造超管**）。
  ⇒ 若把"改角色"归给 `user:write`，任何拿到用户写权限的运营都能给自己/别人造一个管理员 ——
  这正是 Q6 把 RBAC 单列的理由。

**批 2 确认**：`MerchantManager#approve` 调 `authContextClient.setUserRole(userId, ROLE_TYPE_SELLER)`——
**固定常量，不是任意角色** ⇒ `merchant:admin` 足够，不需要 `rbac:admin`。
但它是"写授权域数据"的侧效应，目前只有 `log.info`，**建议补一条操作审计**（登记，不阻塞）。

### 7.4d Q9 **已裁决（2026-10-18）**：冲红挂 `invoice:void`

`AdminInvoiceController` 有三个写方法，Q2 已裁决拆出 `invoice:issue` / `invoice:void`，
但 `PUT /{invoiceNo}/red-flush`（冲红：作废原票 + 重开）**同时具备两者语义**。
**裁决**：挂 **`invoice:void`**。方法注释必须写明「**冲红 = 作废 + 重开**」的**原子语义** ——
一次请求同时完成作废与重开，不是两个可分别授权的小动作。
SoD（职责分离：一个人不能既开票又作废）**另立项**：它需要"同一实体两个动作互斥"的授权模型，
不是权限位能表达的（现在 `*:*:*` 一律通吃），且本批门禁只认 `hasPermi` 单参，双位要求会先卡住门禁。

### 7.5 Q5 过渡态登记（拆位时必须改掉，不然就是"拆了位但没拆到这两处"）

| 控制器 | 当前（过渡态） | 拆位后必须改为 |
|---|---|---|
| `AdminPayController` | `@ss.hasPermi('user:admin')` | `@ss.hasPermi('pay:admin')` |
| `AdminMerchantController` | `@ss.hasPermi('user:admin')` | `@ss.hasPermi('merchant:admin')` |

两处代码里已留 `TODO(拆位, open-items §1.17)` 注释，改注解时顺手删掉。
**为什么单独登记**：这两个控制器是 Q5 才从"代码内 `assertAdmin()`"转成声明式的，
它们的位是**沿用**旧开关，不是按域裁定的结果 —— 不登记就会在拆位时被当成"已经改好了"。

---

## 8. 执行顺序（评审通过后，不跳步）

0. ✅ **"位必须存在"门禁先行**（`PermissionBitReferenceGuardTest`）：引用必须存在 = 硬失败；
   定义未引用 = WARN + 白名单；解析边界只认 `hasPermi` 单参，复杂表达式直接失败。
   顺序口径写死为 **先种子 → 再注解 → 后删旧位**（理由见该门禁类注释）。
1. ✅ 门禁先行（`AdminEndpointAuthorizationGuardTest` 在）
2. ✅ Q5：4 个服务补 `spring-boot-starter-security` + 两个控制器转声明式 + 门禁转正
3. ✅ Q6：`PermissionBits` 单点实现 + `rbac:admin` 通配符排除 + 两条路径共用
4. ✅ **种子迁移（2026-10-18 已执行）**：`sys_permission` 新增 20 位（id 7–26，`status=1`）；
   `ROLE_ADMIN` 经 `INSERT ... SELECT ... WHERE code = 'rbac:admin'` **显式**持有该位；
   `ROLE_SELLER` 一次性拿全 `product:write` + 三个 `seller:*`。
   **`user:admin` 刻意保持 `status=1`** —— 它就是「后删旧位」那一步，必须等注解替换完再停用，
   否则中间态全线 403（执行顺序的意义就在这里）。
   **进度计**：此刻 `PermissionBitReferenceGuardTest` 会打印 20 条「零消费」WARN，
   它们逐条对应待接线的位 —— 第 5 步做完，WARN 归零；这就是迁移进度表，不需要另做一份。
5. ⬜ **逐控制器替换注解** —— **分两批，不许一起机械替换**：
   - **5a 管理端**：位与域一一对应（§6.1），多数可类级批量替换。
     **但已 grep 出唯一跨域控制器 —— `AdminInvoiceController`**：`issue` / `void` / `red-flush`
     三个方法对应**不同的位**（`invoice:issue` / `invoice:void` / 冲红归属待定，见 Q9），
     **必须按方法替换**。其余控制器域一致，可类级替换。
   - **批次顺序（已调整）**：**单域控制器先做**（用它们验证流程：类级替换 → full → preflight 递减 →
     grep 本批控制器代码内判权），**`AdminInvoiceController` 单独一批放最后**（按方法替换、风险最高）。
   - **批次日志**：
     | 批 | 控制器 | 位 | preflight 引用数 |
     |---|---|---|---|
     | 1 ✅ | `AdminCurrencyController` / `AdminHomeController` / `AdminKgController` | `currency:admin` / `home:admin` / `kg:admin` | 18 → 15 |
     | 2 ✅ | `AdminMerchantController` | `merchant:admin` | 15 → 14 |
     | 3 ✅ | `AdminOrderController`（**全方法级**：4 个订单域 + `GET /dashboard`） | `order:admin` ×4 / `dashboard:read` ×1 | 14 → 13 |
     | 4 ⬜ | `AdminAfterSaleController`（**按方法**：GET list / PUT review / PUT confirm-return） | `aftersale:read` / `pay:admin` / `pay:admin` | — |
     | 8 ⬜ | `AdminLogController` / `AdminUserController` / `Role`+`Permission` | `audit:read` 类级 / **按方法** `user:read`+`user:write`+**`rbac:admin`** / `rbac:admin` | — |
     | 末 ⬜ | `AdminInvoiceController`（按方法：issue / void / red-flush） | `invoice:issue` / `invoice:void` / `invoice:void`（Q9） | — |
   - **每批替换后必须跑 `verify-gates.sh full`**，不能只看零消费 WARN 的下降：
     WARN 只反映"有没有被注解引用"，**不反映测试是否还在 mock 旧位**（mock 里写死
     `"user:admin"` 会导致"注解换了、测试还绿、线上却换位"的假通过）。WARN 是进度计，不是验收。
   - **5b 商家端（逐端点人工确认，不按控制器机械替换）**：`/api/seller/**` 的位不是"把开关换名"，
     而是**逐个端点判定**：这个端点是"商家对自己数据"还是"商家对别家/平台数据"？归哪个位？
     own-data 由谁保证（现在靠 `UserContext.getUserId()` 过滤，不是靠权限位）。
     **确认结果逐行记在本文件 §4.2**，由人工签字后我再动手。
     原因：seller 侧的位是**新语义**（Q3 才拆出来），机械替换等于让 AI 决定业务边界。
     待确认端点清单以门禁 `EXEMPT` 的四条为准（`SellerFinanceController` /
     `MerchantCouponController` / `KnowledgeBaseController` / `MerchantController`），
     其中 `POST /api/seller/apply` 按 Q4 **永久豁免**（无权限位，只补限流）。
6. ⬜ **缓存失效**（§7.3-2）
7. ⬜ **投影比对**（§7.3-1）
8. ⬜ **端到端 403**（§7.3-3，**卡环境**）
9. ⬜ 前端入口隐藏（S0 之后；须支持通配符与 `rbac:admin` 排除，否则管理员菜单会消失/越权可见）

---

## 9. 不做什么（防止范围蔓延）

- **不做数据权限**（店铺/部门行级过滤）—— 另立项。
- **不引入 ABAC / 权限继承层级**。
- **不把 `sys_permission.path` 做成第二套判定**。
- **不保留「代码内 assertAdmin」**（Q5：不做双轨）。
