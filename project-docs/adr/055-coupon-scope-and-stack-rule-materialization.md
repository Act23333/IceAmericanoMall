# ADR-055: 优惠券适用范围与叠加规则落地 —— scope/stack 四列映射打通 + I46 `/batch` 补齐

## 背景

`coupon` 表的四列 —— `scope_type`（1=ALL/2=CATEGORY/3=PRODUCT —— 此处 `PRODUCT` 是**券适用范围种类**的枚举名（后端 `ScopeTypeEnum` 常量，**未随 ADR-059 改名**），与 SPU 实体词汇无关；`Initialize.sql` 列注释现写作「3=PRODUCT单品」，而 `scope_values` 装的是 **spu id**）、`scope_values`（JSON：categoryIds 或 spuIds —— **列注释 2026-10-16 随 ADR-059 由 `productIds` 同步为 `spuIds`**）、`stack_rule`（1=MUTUAL_EXCLUSIVE/2=STACKABLE）、`stack_group`（VARCHAR(32)）—— 自 V4.3 起就存在于 `Initialize.sql`，`CouponCreateReq`/`CouponVO` 也已有对应字段，但**从未映射进 `CouponPO` / `Coupon` 聚合**，由此产生三个真实缺陷（2026-10-15 逐条实测）：

1. **建券维度静默丢失**：管理端/商家端建券时 `scopeType`/`scopeValues`/`stackRule`/`stackGroup` 被静默丢弃，落 DB 默认值（`scope_type=1`、`stack_rule=1`）——用户以为自己配了「限定品类可叠加券」，实际拿到的是「全场互斥券」。
2. **叠加校验从未执行**：`ADR-038` 的「后果」段曾推论「下单侧 stack 校验走 marketing 的 `batchGet` 内部接口返回的 PO 字段，不受影响」——**该推论错误**。`InternalCouponController` 没有 `/batch` 端点，`CouponClient.batchGet` 调用 404 后被 `CouponClientFallback.batchGet` 吞成空列表，`PricingService.validateStackRules` 首行 `if (infos == null || infos.isEmpty()) return;` 即返回。即「互斥券不可叠」「不同叠加组不可同用」两条规则**自 V4.3 起在线上从未生效**。
3. **适用范围校验同样缺失**：`ia-api` 的 `CouponClient.useCoupon` 声明了 8 个参数（含 `productIds`/`categoryIds`，**现已改名 `spuIds`/`categoryIds`**，ADR-059），而 `InternalCouponController.use` 只接 6 个 —— Spring 直接忽略多出的 2 个 → 范围限制型券可用于任意商品（`PricingService` 侧一直在认真组装这两个参数，只是对端不接）。

三条都属「契约已声明、实现缺位」，静默降级而非报错，因此长期未被发现。

## 决策

**一次性补齐四个落地环节，把「声明即事实」重新建立起来**：

1. **列映射打通**：`Coupon` 聚合与 `CouponPO` 各增 `scopeType`(Integer)/`scopeValues`(String)/`stackRule`(Integer)/`stackGroup`(String)，`CouponRepositoryImpl.toDomain/toPO` 补映射。`scope_values` 虽是 MySQL `JSON` 列，**仍以 `String` 映射、不引入 TypeHandler**（先例：merchant `ShopDecorationRepositoryImpl`、item `NoteRepositoryImpl`）——MyBatis 默认按字符串读写，JSON 形态解释归领域层。
2. **建券路径自动生效**：`MarketingConverter.toDomain(CouponCreateReq)` 由 MapStruct 按同名同型自动映射四字段（已查生成的 `MarketingConverterImpl` 第 67–70 行确认），`CouponAdminManager.create/createShopCoupon` 无需额外代码即带上四字段；`toVO` 侧第 100–103 行同理（读路径回吐）。**不新增显式 `@Mapping`**：同名同型时显式标注只增加维护面。
3. **I46 `/batch` 补齐**：`CouponUsageManager.batchStackInfo(List<Long> userCouponIds)` + `InternalCouponController` `@GetMapping("/batch")`。
   - **入参口径 = `user_coupon.id`**：与 `/use` 的 `userCouponId` 同口径（`PricingService.applyCoupons` 传的确实是 `user_coupon.id`）。返回的 `CouponStackInfoCO.id` 亦填持券 ID，便于调用方按原入参对应；`couponId` 填券模板业务券号。`CouponStackInfoCO.id` 原注释「coupon.id」是错的，已订正。
   - **fail-open**：入参为空/券模板已删时**跳过**而非抛异常 —— 该返回值被调用方解释为「叠加约束事实」，缺项只意味着该券不参与约束判定，不能让查询失败阻断下单（ADR-011 降级三分法）。
   - 为避免 N+1，`UserCouponRepository` 增 `findByIds(List<Long>)`（`MyBatis-Plus BaseMapper.selectByIds`），持券一次查出、模板逐个 `findById`。
4. **适用范围校验**：新增纯领域服务 `CouponScope`（零框架依赖），`CouponUsageManager.useCoupon` 在既有品类/店铺校验之后调用：
   - `scopeType=1 ALL` / `null` / `scopeValues` 为空 → 放行（存量数据向后兼容 + 防御 null）；
   - `2 CATEGORY` → 订单品类与 `scopeValues` 需有交集；`3 PRODUCT`（券适用范围种类，**非 SPU 实体词汇**；匹配的是订单的 **spu id** 串）→ 订单 spu 与 `scopeValues` 需有交集；
   - 无交集 → `BizException(BUSINESS_EXECUTION_EXCEPTION, "该优惠券不适用于当前订单商品")`；
   - **订单侧未传 `productIds`/`categoryIds`（现名 `spuIds`/`categoryIds`，ADR-059）时，若券有限定范围则拒绝**（`intersects` 对空集恒返回 false）——「漏传」不能被解释为「不限」，否则下游任何一次少传参数都会把范围券变成全场券，正是本次要修的缺陷本身。
   - `scopeValues` 解析**手写不做 JSON 库依赖**（域层零框架 + 只需「数字 ID 集合」一种最简形态）：容忍 `["1","2"]`（字符串元素）与 `[1,2]`（数字元素），另容忍外层多一层引号的「数组被当作 JSON 标量存储」形态。

## 备选方案

| 方案 | 优点 | 缺点 |
| :--- | :--- | :--- |
| **A. 本期按上述四步全量落地**（选定） | 契约声明与实现一次对齐；叠加/范围两条规则真正生效；列映射缺口按 ADR-038 的「后续需先补列映射」指引关闭 | 一次性改动横跨 domain/PO/仓储/app/adapter 五处；`/use` 增加参数属对外契约变更（集群内 Feign，口径本就已声明） |
| B. 只补列映射，暂不补 `/batch` 与范围校验 | 改动面最小 | 「建券配得上、下单不校验」——用户可配出永不生效的规则，比静默丢弃更难排查 |
| C. `scope_values` 引入 `JacksonTypeHandler` 映射为 `List<Long>` | 类型更强 | 与仓储侧既有先例（JSON 列用 String 承载）不一致；聚合被迫携带「JSON 数组」这一形态约定；且建券请求体字段本就是 `String`，多一层转换无收益 |
| D. `/batch` 入参改为 `coupon_id` 列表 | 语义上更贴「查券模板」 | 调用方 `PricingService.applyCoupons` 手里只有 `user_coupon.id`（它要在下一步把这些 id 交给 `/use` 核销），改口径需调用方先反查 `user_coupon`，等于把一次查询拆成两次 |

## 后果

**正面**

- ADR-038 记录的两项遗留缺口（四列未映射、「下单侧不受影响」的错误推论）均由本期关闭，ADR-038 对应段落已加订正标注。
- 「互斥券不可叠」「不同叠加组不可同用」两条规则真正开始执行：多券同时使用时，`PricingService.validateStackRules` 会拿到真实的 `stackRule`/`stackGroup` 并抛 `BizException`。
- 范围限制型券不再可用于任意商品；建券配置的适用范围与下单核销口径一致。
- 测试：marketing-service 190 → **229** 测试全绿（新增 `/batch` 端点接线、范围校验 8 例、`batchStackInfo` 4 例、`CouponScope` 11 例、`CouponRepositoryImpl` H2 往返 5 例）；trade-service **343** 测试保持全绿（`PricingServiceTest` 的 `CouponClient` mock 签名已含 8 参数，无需改动）。

**负面 / 风险**

- **行为收紧**：此前「范围券 = 全场券」「叠加规则不校验」的宽松行为被纠正。存量多券订单若依赖旧宽松语义，将在下单时被拒 —— 因四列从未映射，存量数据 `scope_type=1`/`stack_rule=1`（互斥），故**存量券默认互斥**：多券同时使用会被新生效的叠加校验拦截。这是把 ADR-043 的设计意图真正落地，但属可感知的行为变化，需在 PRD/契约侧确认「多券同时使用」当前是否为线上路径。
- `scopeValues` 手写解析不做完整 JSON 校验：形如 `[1,2` 的残缺输入会被尽力解析而非报错。取向是「解析失败即空集合 → 视为不限」，与存量默认值行为一致，不引入新的下单阻断点。
- `InternalCouponController.use` 的 `productIds`/`categoryIds`（**现名 `spuIds`/`categoryIds`**，ADR-059）为 `required = false`：调用方漏传时不会 400，而是转由应用层按「有限定范围则拒绝」处理。这是刻意的 —— 让「漏传」与「不适用」在业务层有统一出口，而不是把契约完整性交给参数绑定。
- **缺口已关闭**（原登记为「已知未修」，2026-10-15 补齐）：`GET /internal/coupon/batch` 缺少 `ids` 参数时，原本抛 `MissingServletRequestParameterException` 并**兜底成 HTTP 500**（`{"code":5000,...}`）而非 400 —— 根因是 `ia-common` `GlobalExceptionHandler` 缺该异常的处理器。修复落在**共享内核**（故对全部 15 个服务一并生效），并顺带补齐同类两个出口：
  - `MissingServletRequestParameterException`（必填 `@RequestParam` 未传）→ **500 改为 400 `PARAM_ERROR(1101)`**
  - `MethodArgumentTypeMismatchException`（参数类型不符，如 `?page=abc`）→ **500 改为 400 `PARAM_ERROR(1101)`**
  - `HttpMessageNotReadableException`（请求体 JSON 语法/类型错误）→ **500 改为 400 `PARAM_ERROR(1101)`**
  - 依据：契约 §1.3「全平台方法参数校验失败统一映射为 400 `PARAM_ERROR`」——修前行为与该条冲突。
  - **实现证据（file:line）**：`Implementation/back-end/ia-common/src/main/java/org/icedamericanomall/common/aspect/GlobalExceptionHandler.java:186`（缺参处理器）、`:199`（类型不符）、`:213`（请求体不可读）。三处均走既有 `processResponse` 通道，故 `/internal/**` 仍保持 ADR-004 的「真实状态码 + 极简 `{"code","msg"}`、不包 `Result`」语义。
  - **测试证据（file:line）**：`Implementation/back-end/ia-common/src/test/java/org/icedamericanomall/common/aspect/GlobalExceptionHandlerTest.java:155`（`shouldReturn400_whenMissingServletRequestParameter`）、`:168`（`shouldReturn400_whenMethodArgumentTypeMismatch`）、`:179`（`shouldReturn400_whenHttpMessageNotReadable`）；该测试类 **6 → 9** 例，`ia-common` 模块 **76 → 79** 例。
  - **全量验证证据**：`mvn -f Implementation/back-end/pom.xml clean test` → **1775 tests / 0 failures / 0 errors / 1 skipped**（该项为既有 `@Disabled`），20 个反应堆模块全部 SUCCESS。

## 日期

2026-10-15

## 参考

- ADR-004（内部接口异常透传）、ADR-011（Feign fallback 降级三分法）、ADR-038（全面四层 DDD 收口，其遗留缺口段落本期订正）、ADR-043（价格计算中心与促销引擎 —— 叠加口径的原始设计）、ADR-050（业务标识 BINARY(16)：`coupon.coupon_id` ↔ hex 转换）
- `project-docs/backend/04-Data-Model.md`（`coupon` 四列定义）、`project-docs/contract/05-API-Specification.md`（I46 端点）
