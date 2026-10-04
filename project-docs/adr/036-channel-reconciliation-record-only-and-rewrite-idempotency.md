# ADR-036: 渠道对账 —— 四态差异只记录不调账、先删后写幂等与账单适配口径

## 背景

F4 渠道对账（规格：`04-Data-Model.md` §十二 12.4、`05-API-Specification.md` §4.13.14
AD44-AD46）需要在 pay-service 建立「渠道账单 ↔ 本地成功支付流水」的比对与差异留存能力。
这类功能在实现层面有若干必须先拍定的问题：

1. 差异发现后**要不要自动调账**（改本地支付单 / 反向调渠道）？
2. 同日同渠道被重复触发（人工连点 + XXL-Job 撞车）时如何保证幂等？
3. 账单**拉不到**（未生成 / 网络失败）时批次该是什么状态？
4. 契约声明 AD46 返回单个 `ReconciliationBatchVO`，但 `channel` 缺省时是「全部渠道」——怎么返回？
5. 渠道账单的列序与金额计价单位（元）如何安全地映射到本项目的「整数分」模型？

## 决策

### 1. Phase 1 只记录差异，**不自动调账**

四态明细（1-匹配 / 2-渠道有本地无 / 3-本地有渠道无 / 4-金额不一致）**全部落库**，
管理端经 AD44/AD45 查看，人工处置。

理由：自动调账是**资金写操作**，需要「谁有权限反向改本地支付单 / 向渠道发起补单」的完整权限与
对账闭环设计（含冲正、审批、留痕），远超 Phase 1；而在没有这些前提下自动改本地状态，
会把「渠道与本地不一致」升级成「本地状态被对账任务篡改且无审计」。差异可见即已达标。

落地方式：`ReconciliationManager` 的本地侧**只读**（`listPaidByDateAndChannel` 一次调用，
单测用 `verifyNoMoreInteractions(payOrderService)` 锁死「不碰任何本地支付单写操作」）。

### 2. 幂等 = `uk_date_channel` + **先删后写**

`reconciliation_batch` 上 `uk_date_channel(bill_date, channel)` 表达「同日同渠道一批次」。
重复执行 `ReconciliationServiceImpl.saveBatchResult` 时：**先删同日同渠道的旧批次与其全部明细，
再写新批次与新明细**（同一事务）。两次执行后仍只有一组批次，管理端查询无重复。

先删明细（按 `batch_id`）再删批次，避免留下孤儿明细；`uk_date_channel` 同时兜底并发重复触发
（两个并发任务里必有一个唯一键冲突失败重试）。

### 3. 账单拉取失败 → 落 **`status=失败(2)`** 的批次（而不是抛异常或假装零交易）

**接口契约**：`PaymentClient#billDownload` / `AlipayPaymentClient#billDownload`
**拉取失败抛 `BizException`；账单合法但无流水返回空列表**。
`ReconciliationManager` 捕获异常后落一条 `status=失败` 批次并继续处理其它渠道。

这样 `reconciliation_batch.status` 的 `2-失败` 有了真实语义，也让「账单未生成」与
「当日零交易」在管理端可区分（前者是失败批次，后者是完成批次且总额为 0）。
失败批次同样走先删后写，账单日重跑会被正常结果覆盖。

### 4. AD46 返回**批次列表**（契约声明为单个 VO —— 已回报待文档同步）

契约 `channel` 缺省 = 当日全部渠道，一次触发可能产出多条（WECHAT + ALIPAY）。
返回单个 VO 会丢失其中一条，故实现返回 `List<ReconciliationBatchVO>`（指定单渠道时长度 1）。
这是本项目对已定稿契约的**一处有意偏差**，已在变更交接摘要中标注，待文档同步为列表类型。

### 5. 账单解析口径收口在通道适配层：列序 + 元 → 分定点换算

- **列序**（0 基）：`0 交易时间`（用于识别数据行）、`5 微信订单号`（渠道交易号）、
  `6 商户订单号`（= 本地 `pay_order.pay_order_no`）、`24 订单金额`。
  数据行判定 = 「列数 ≥ 25 且首列形如 `yyyy-MM-` 且商户订单号非空」——
  以此跳过表头行与结尾汇总行（表头首列是“交易时间”三字，汇总行首列为空）。
- **金额单位**：渠道账单以「**元**」计价（`128.00`），本项目一律「**整数分**」。
  换算用**定点字符串运算**（拆整数位/小数位、小数右补 0 到 2 位后拼接），
  **不引入 `BigDecimal` / `double`** —— 遵守「金额只能以整数分流转」的硬约束且无浮点误差；
  脏数据按 0 处理并告警，不中断整批解析（差异会以「金额不一致」暴露）。
- 真实实现（`WechatPaymentClient`）经 SDK `BillDownloadService.getTradeBill` 取
  `download_url` 后 HTTP 拉取正文（该 URL 自带临时令牌，无需再带签名头）。
  **未对线上真实账单做端到端验证**（无商户证书/账单样本），列序与单位按微信 v3 公开文档实现——
  这是本 ADR 最大的残留风险，接入真实商户后须以一份真实账单回归一次。

### 6. Pay-service 承担批次与明细，wallet 不参与对账

对账渠道只有 `WECHAT` / `ALIPAY`：余额是本地闭环（本地账即真实账），不存在外部账单可对。
故 `ReconciliationChannelEnum` 与 `PayChannelEnum` 分开定义（前者无 `BALANCE`），
渠道码取值保持一致以便直接与 `pay_order.pay_channel_code` 比对。

### 7. 调度与入口

- 手动：AD46 `POST /api/admin/pay/reconciliation/run?billDate=&channel=`（`billDate` 必填，
  缺失/格式非法 → 400 `PARAM_ERROR`；**显式解析而非 Spring 绑定**，避免抛出不受控的绑定异常）；
- 自动：`ReconciliationJob#runDailyReconciliation`（XXL-Job，账单日 = 昨日），
  与既有 `xxl.job.enabled`（dev 默认关）口径一致。
- 审计：AD43 / AD46 为写操作，标注 `@OperationLog`（ADR-032 管道一，ELK AUDIT 日志）。

## 备选方案

| 方案 | 优点 | 缺点 |
| :--- | :--- | :--- |
| 差异自动调账（改本地状态 / 向渠道补单） | 端到端一致 | 资金写操作需权限+审批+冲正闭环；Phase 1 无此前提，风险远大于收益 |
| 全量重写（DELETE 全部再 INSERT，不按日） | 实现更简单 | 破坏「同日重跑只影响当日」的边界，历史批次被无谓改写 |
| UPSERT（`ON DUPLICATE KEY UPDATE`）替代先删后写 | 无需删除 | 明细集合会变化（差异条目增减），需逐条 diff 删除，逻辑比「先删后写」复杂且易残孤儿 |
| 拉账单失败时抛异常（不落批次） | 失败显式 | 管理端只看到 500，看不到「哪个账单日哪条渠道失败」；且多渠道路由下会中断其它渠道 |
| 拉账单失败时落「完成 + 零交易」批次 | 无需区分状态 | 把「没拉到」伪装成「真的没有交易」，掩盖问题（channelTotal=0 与 localTotal>0 貌似差异） |
| 用 `BigDecimal` 解析元金额 | 代码直观 | 违反「金额一律整数分、不用 BigDecimal」的硬约束；且引入浮点/精度分支 |
| **只记录不调账 + 先删后写 + 失败批次 + 定点换算（采纳）** | 风险边界清晰、幂等锚点单点、失败可区分、金额无浮点 | 需人工处置差异；真实账单解析未经验证（残留风险） |

## 后果

**正面**

- 对账差异对管理端**可见可筛**（AD44 批次 / AD45 按四态过滤明细），人工处置有依据；
- 幂等锚点单点（`uk_date_channel` + 先删后写），手动重跑与日终任务撞车安全；
- 失败与「零交易」可区分，账单未生成不再被静默吞掉；
- 金额换算不引入浮点/`BigDecimal`，符合「整数分」硬约束；
- 拉账单与比对在应用层（`ReconciliationManager`），落库在领域服务（本地事务）——
  通道调用不落在事务内，符合「事务内禁 Feign」硬约束。

**负面 / 风险**

- **真实渠道账单未做端到端验证**（列序/单位按公开文档实现）：接入真实商户后必须用一份真实账单回归，
  否则可能整批金额判为「不一致」。这一块已用 `WechatBillCsvParserTest`（列定位 + 元转分 + 跳表头/汇总）
  锁死解析逻辑，但**样本仍为构造样例**；
- AD46 响应类型与契约声明不同（列表 vs 单对象），需文档同步，否则管理端联调会踩到；
  **→ 已同步（05 规格 V5.6.1 落地说明）**：AD46 正文已声明 `List<ReconciliationBatchVO>`。
- 差异不自动调账 → 长期不处置的差异会持续累积（Phase 1 接受，靠管理端巡检）；
- 单表分页 `selectPage` 在批次/明细量级大时仍需索引支撑（已建 `uk_date_channel` / `idx_batch_result`），
  深分页问题按项目分页规范（>1000 页走游标）在后续需要时处理。

## 日期

2026-10-03

## 参考

- `Implementation/back-end/pay-service/src/main/java/org/icedamericanomall/pay/app/ReconciliationManager.java`（下载 → 比对 → 四态）
- `Implementation/back-end/pay-service/src/main/java/org/icedamericanomall/pay/service/impl/ReconciliationServiceImpl.java`（先删后写 + 分页）
- `Implementation/back-end/pay-service/src/main/java/org/icedamericanomall/pay/adapter/admin/AdminPayController.java`（AD43-AD46）
- `Implementation/back-end/pay-service/src/main/java/org/icedamericanomall/pay/job/ReconciliationJob.java`（XXL-Job 日终）
- `Implementation/back-end/ia-integration/src/main/java/org/icedamericanomall/integration/payment/WechatBillCsvParser.java`（列序 + 元转分）
- `Implementation/back-end/database/migrations/V5.5__refund_wallet_tx_and_reconciliation.sql`（`reconciliation_batch` / `reconciliation_detail`）
- ADR-035（同批次的退款资金流 —— 对账是退款/支付资金链路的人工兜底出口）
- ADR-004（内部端点异常透出）、ADR-032（审计双管道）
