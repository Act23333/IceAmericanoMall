# ADR-029: 渠道回调协议优先于平台 Result 信封（微信 v3 裸 JSON + 500 触发重推）

## 背景

平台统一响应契约是 `Result<T>`（`code` / `message` / `data` / `traceId`），由 `ia-common` 的
`GlobalExceptionHandler` 统一装配，业务接口一律返回信封（ADR-003/004 的对象与异常口径）。
但**支付渠道回调的应答对象不是我们的前端，而是渠道方**——应答体是渠道协议报文的一部分。

排查掉单时发现一个 **P1 缺陷（掉单风险）**：`/api/pay/callback/wechat`、`/api/pay/callback/alipay`
的应答被平台信封包裹（返回 `Result` 形状）。以微信支付 v3 为例，渠道按自己的协议解析应答：

- 应答不合规（不是约定的 `{"code":"SUCCESS"}` 形状）→ 渠道判定「通知处理失败」；
- 按微信重试策略，失败通知会在重试窗口内重推，**超出窗口后停止重推**；
- 结果：用户已付款成功，但本平台永远收不到（或收不到合规的）成功通知 → 支付单停留 `PENDING_PAY`
  → **订单不掉状态、库存与优惠券不确认、Saga 不推进**，即典型「掉单」。

同类问题也存在于支付宝侧：其异步通知要求应答正文**恰为** `success` 字符串。

也就是说：`Result` 信封在这两个端点上不是「更规范」，而是**破坏了渠道协议**。

## 决策

**在第三方渠道回调这一「边缘协议端点」上，渠道协议优先于平台统一信封；平台信封规则照旧适用于其余全部业务接口。**

1. **微信 v3（`POST /api/pay/callback/wechat`）**
   - 验签/解密/业务处理**成功** → HTTP **200** + 裸 JSON `{"code":"SUCCESS","message":"成功"}`；
   - 验签/解密失败或业务处理失败 → HTTP **500** + 裸 JSON `{"code":"FAIL","message":"处理失败，请重试"}`
     —— **非 200 是触发渠道重推的信号**；
   - **重复通知命中幂等分支仍返回 `SUCCESS`**：`PayManager#handleCallback` 里
     `markSuccessIfPending` 返回 `false`（支付单非待支付态）即正常返回，避免渠道把
     「已处理过的通知」当失败无限重推；
   - 失败 `message` 是固定文案，**不含任何内部细节**（异常堆栈、渠道报文只进日志）；
   - 应答体字段顺序固定（`code` → `message`），便于日志阅读与断言。
2. **支付宝（`POST /api/pay/callback/alipay`）**：返回裸字符串 `success`（协议要求正文恰为此串）。
3. **回调入参保持 `Map` / `HttpServletRequest`，不做 `*Req` 对象化**：回调必须对**原始报文与签名头**
   验签（字段随渠道版本变化），属 ADR-023「入参必须类型化」的**显式例外**，并在类 Javadoc 中留档。
4. **这些端点的异常不交由 `GlobalExceptionHandler` 重新包成信封**：控制器内 `try/catch` 后按渠道协议
   自行应答（代价是这两条路径的异常形状与平台其余端点不同，这是刻意的）。
5. `Result` 信封与 `/internal/**` 的原生异常透传（ADR-004）均**不变**：本决策的作用域严格限定为
   「第三方渠道回调应答」，不外溢到任何面向前端的接口。

## 备选方案

| 方案 | 优点 | 缺点 |
| :--- | :--- | :--- |
| 维持信封包裹（缺陷现状） | 平台响应格式绝对统一 | **渠道判定应答不合规 → 停推 → 掉单**；支付成功而订单不动，属于资损级缺陷 |
| 一律返回 200 + `FAIL` 语义体 | 渠道不会因非 200 反复重推 | 200 表示「已接收」，渠道不再重推 → 处理失败的通知永久丢失，掉单更隐蔽 |
| 立即接收、异步入 MQ 后再处理（总是回 SUCCESS） | 应答最快、渠道侧最省重推 | 需新增回调落库 + 补偿链路与对账；当前规模下复杂度不成比例；且丢失「验签失败也回 SUCCESS」的语义风险 |
| 用类型化 `*Req` 接收回调 | 契合 ADR-023 | 验签必须拿**原始报文**与签名头，反序列化会破坏字节形态；字段随渠道版本漂移，对象会成为脆弱契约 |
| **渠道协议优先 + 500 触发重推 + 幂等兜底（采纳）** | 与渠道重试策略吻合，最终一致；验签失败也能被渠道重推覆盖；幂等分支返回 SUCCESS 避免死循环重推 | 平台内出现两种应答形状，需文档化边界；500 会带来重复通知，必须依赖幂等 |

## 后果

**正面**

- 回调应答与渠道协议一致，渠道的重试策略成为**免费的可靠投递机制**：瞬时故障（DB 抖动、
  下游 Feign 超时）由渠道重推自动收敛，无需自建回调补偿；
- 幂等命中返回 `SUCCESS`，渠道不会把已处理通知当失败重推，避免「重推风暴」；
- 应答体不含内部细节，防止通过渠道回调探测系统内部状态；
- 缺陷成因（信封包裹破坏渠道协议）以类 Javadoc 形式固留在 `PayController`，防止后续「顺手统一响应格式」再次踩坑。

**负面 / 风险**

- **平台响应形状不再绝对统一**：`/api/pay/callback/**` 两个端点返回裸 JSON / 裸字符串，
  新增拦截器、网关响应过滤器或全局异常处理时**必须放行这两个路径**，否则缺陷会以另一种形式回归；
- **回调端点必须保持幂等**：500 会引来重推，重复通知是常态而非异常，任何新增的回调处理分支
  都必须走条件更新（`markSuccessIfPending` 形态）而非「先查后写」；
- **应答无法携带 `traceId`**（渠道协议字段固定），回调链路的排障只能靠日志关联
  （`out_trade_no` / 支付单号 + 时间窗）；
- 恒定 500 应答对应的 `FAIL` 文案不宜随意改动（部分渠道对文案无要求，但改文案属发布风险点）；
- **永久性业务失败也会被渠道重推**（如支付单不存在），重试窗口内产生多次 ERROR 日志；
  需要人工介入的场景（如余额已扣但支付单未迁移）仍依赖告警与对账，本决策不解决。

## 日期

2026-10-03

## 参考

- `Implementation/back-end/pay-service/src/main/java/org/icedamericanomall/pay/adapter/web/PayController.java`（协议应答 + 恒等式文案 + 入参例外的 Javadoc）
- `Implementation/back-end/pay-service/src/main/java/org/icedamericanomall/pay/app/PayManager.java`（`handleCallback`：验签 → 条件置成功 → 幂等分支）
- `Implementation/back-end/pay-service/src/main/java/org/icedamericanomall/pay/app/PaymentChannelRouter.java`（`verifyCallback` 渠道验签分派）
- `Implementation/back-end/pay-service/src/main/java/org/icedamericanomall/pay/service/impl/PayOrderServiceImpl.java`（`markSuccessIfPending` 条件更新）
- ADR-003（DO/DTO/VO 对象隔离）、ADR-004（`/internal/**` 原生异常透传 —— 与本 ADR 作用域不同）、ADR-023（入参对象化及其显式例外）
