# ADR-038: 全面四层 DDD 收口 —— 废止轻量 COLA 双轨制（已完成）

## 背景

用户裁决：不再接受"轻量 COLA"形态（顶层 service/mapper/entity 包 + 控制器直调 service 实现类），全部业务服务必须按 java-architect 规范收敛为完整四层 DDD（adapter/app/domain/infrastructure），以 user-service、authorization-service 为标杆。本决策废止 ADR-023 中"两种形态并存"的口径。

## 决策与落地（2026-10-03 ~ 2026-10-04，主 agent 内联执行，全量测试绿）

全部 14 个业务服务已收敛为严格四层。各批次的口径与语义保持要点：

- `search-service`：domain/model/search（SearchQuery/SearchPage/ProductSearchHit/KnowledgeChunkHit）+ domain/gateway 4 端口 + infrastructure（persistence Db 网关、es Es 网关与向量索引适配器、adapter Redis/Http）；Db/Es 双实现成为同一端口的条件互补实现。23 测试。
- `logistics-service`：Package 聚合（状态单调迁移内聚）+ PackageRepository + 持久化；app/LogisticsManager 编排。33 测试。
- `cart-service`：CartItem 聚合 + CartRepository + 持久化；CartManager 持有事务边界与读时回填编排（uk_user_sku 并发合并语义保留）。16 测试。
- `pay-service`：PayOrder/RefundOrder 聚合 + Reconciliation 领域模型 + 三仓储端口/实现；WHERE status=PENDING 等资金条件更新逐字沉仓储；job→infrastructure/scheduler。72 测试。
- `item-service`：补齐 domain/repository（6 仓储）+ infrastructure 持久化与 Redis 适配器 + app/Manager（Sku 原子 SQL 原样沉仓储、Product 编排含 freight fail-closed、Review 通知触发保留）；领域归一化下沉聚合（Req 上的 normalizedXxx 移除）。51 测试（旧基线 42，覆盖只增不减）。
- `marketing-service`：PO 行为迁聚合（Coupon/UserCoupon/FlashSale/PointsLog）、4 仓储 + 3 Redis 端口（FlashStockStore/CouponGrabStore/CouponClaimDedupStore）、service 四族→app/{coupon,flash,points}Manager、Lua→infrastructure/adapter/redis、consumer/producer→infrastructure/messaging；**抢券 Redis 补偿（DB 落库失败回滚预扣）与秒杀 soldCount 乐观锁重试语义逐字保留**。49 测试。
- `trade-service`（最大）：Order/OrderItem/AfterSale/Settlement/Withdrawal 五聚合 + 五仓储；OrderServiceImpl 的**全部条件更新状态机（取消/收货/发货拆包/支付回写/超时关闭）逐字下沉 OrderRepositoryImpl**（H2 测试随迁）；manager→app/{order,aftersale,finance,dashboard}、strategy→app/order/strategy、job/consumer/producer→infrastructure/{scheduler,messaging}；Saga 补偿、退款触发时机（仅退款审核后/退货退款确认收货后）、佣金整数运算语义逐字保留；Sentinel 资源名常量上移至 OrderCreationManager。121 测试（旧基线 120）。

## 结构约定（本期起全仓统一）

```
org.icedamericanomall.<service>
├── adapter/          — web / admin / internal + request / response / assembler（MapStruct）
├── app/              — <用例族>Manager（编排/事务边界/Feign/MQ）；复杂用例族下按用例族分包
├── domain/           — model/<aggregate>/、service/（纯计算）、repository/（端口）、port/、enums/
└── infrastructure/   — persistence/（PO+Mapper+RepositoryImpl，多语句写入 @Transactional 下沉）、
                        adapter/（Redis/HTTP 防腐）、messaging/、scheduler/、config/
```

- 适配层转换器包名定为 **`adapter/assembler`**（用户裁决，替代此前的 `adapter/convert`，全仓已统一）。
- 依赖方向：adapter→app→domain；infrastructure→domain（实现端口）→允许依赖 app（scheduler/messaging 消费者调 Manager）。
- 仓储实现是多语句本地事务的持有处（pay ReconciliationRepositoryImpl 先例）；Manager 含 Feign 的方法一律不加 `@Transactional`。
- domain/model 聚合零框架依赖；PO 保留 MyBatis 注解（`@TableName`/`@Version`/`@TableField`），PO↔聚合互转内联在 RepositoryImpl。

## 后果

- 轻量 COLA 形态（顶层 service/、mapper/、entity/、convert/、job/、consumer/、producer/、strategy/ 包）在全仓已不存在；AGENTS.md 的包结构章节已同步重写。
- 测试基线：18 模块 BUILD SUCCESS，全量 911 测试（trade 121 / item 51 / marketing 49 / pay 76 / ai 52，均不低于旧基线，迁移过程零语义丢失）；ai-service 游离包收口另见 ADR-039。
- 既存缺口（未随本期处理，保持原状）：`coupon` 表的 scope_type/scope_values/stack_rule/stack_group 四列从未映射进 CouponPO/聚合——商家/平台建券时这些维度静默落 DB 默认值（scope_type=1 ALL、stack_rule=1 MUTUAL_EXCLUSIVE）；~~下单侧 stack 校验走 marketing 的 `batchGet` 内部接口返回的 PO 字段，不受影响~~。后续若启用多券叠加范围，需要先补列映射。
  - 🔧 **2026-10-15 订正（ADR-055）**：上句划线部分**推论错误**。实测 `InternalCouponController` 没有 `/batch` 端点，`CouponClient.batchGet` 调用 404 后被 `CouponClientFallback.batchGet` 吞成空列表，`PricingService.validateStackRules` 首行即 `return` —— 该叠加校验自 V4.3 起从未执行过。同时 `/use` 的适用范围参数（`productIds`/`categoryIds`）也被 Spring 静默忽略，范围限制型券可用于任意商品。四列映射 + I46 `/batch` + 范围校验已由 ADR-055 一并落地，本条缺口关闭。

## 日期

2026-10-03（立项）／2026-10-04（全部落地）

## 参考

ADR-023（adapter 结构）、ADR-025（聚合分包与用例族）、ADR-030/034（共享内核收口）
