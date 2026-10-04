# ADR-033: 微信 v3 回调验签 + 解密 + 提取归通道适配层（修复 out_trade_no 断链）

## 背景

`PayController.wechatCallback` 只把 4 个 `Wechatpay-*` 签名头与原始 body 放进一个 `Map`，
`PayManager.handleCallback` 随后直接从该 Map 读 `params.get("out_trade_no")`。但微信支付 v3 的
回调报文结构是：

```
{ "id": "...", "event_type": "TRANSACTION.SUCCESS",
  "resource": { "algorithm": "AEAD_AES_256_GCM",
                "ciphertext": "base64(密文 ‖ 认证标签)",
                "nonce": "<12 字符 IV>", "associated_data": "<AAD>" } }
```

业务字段（`out_trade_no` / `transaction_id` / `trade_state` / `amount`）位于
**AES-GCM 解密后的 `resource` 明文报文**里，而不是 HTTP 参数。于是：

- `params.get("out_trade_no")` 在微信路径上**恒为 `null`**；
- 支付单必然查不到 → 抛 `ORDER_NOT_FOUND` → 控制器按 ADR-029 应答 **500 FAIL**；
- 微信按重试策略**无限重推**，而每一次重推都走同一条断链；
- 结果：用户已付款，支付单永远停在 `PENDING_PAY`，订单不推进（**存量 P0，资金级**）。

支付宝路径不受影响：其异步通知走 HTTP 表单参数，`out_trade_no` 在参数里直接可见。

同时这是 T2 审计指出的跨层魔法串契约：`"out_trade_no"` / `"result_code"` 这类
**协议字段名**（本质是第三方渠道的协议知识）被当作业务键，跨层出现在 pay-service 的应用层代码里。

## 决策

**第三方回调的「验签 + 解密 + 字段提取」是通道适配层的职责；应用层只消费强类型结果。**

1. **契约下沉到 `ia-integration`**（ADR-005 领域纯净 / 通道适配同源）：
   - `PaymentClient.parseWechatCallback(Map<String,String> headers, String rawBody)`
     成为微信回调的**唯一入口**，返回强类型 `WechatCallbackResult`
     （`outTradeNo` / `success` / `channelTradeNo` / `rawState`）；
   - 原 `PaymentClient.verifyCallback(Map)` **删除**：它把「验签」与「取业务字段」拆成两步，
     正是断链的结构成因（调用方以为验签通过就有字段）；只验签不提取的中间态不再暴露。
2. **真实实现（`WechatPaymentClient`）三步走**：
   - **验签**：复用 SDK `RSAAutoCertificateConfig.createVerifier()`
     （待签名串 = `timestamp\nnonce\nbody\n`，逐字节原文）；
   - **解密**：按 **微信 v3 官方规范** 用 JCA 实现（`WechatCallbackDecoder`）——
     密钥 = `wechat.pay.api-v3-key` 的 UTF-8 字节（32 字节），IV = `resource.nonce` 字节（12 字节），
     AAD = `resource.associated_data` 字节（可为空串），密文 = `Base64.decode(resource.ciphertext)`，
     其形态为 `密文 ‖ 认证标签` 且 **直接投喂 `Cipher.doFinal`**（JCA 期望的正是该形态）。
     **不得**按 `ia-common` 的 `AesGcmUtil` 口径（`base64(iv ‖ ct ‖ tag)`，IV 内嵌在密文里）解读，
     两者只是同为 AES-256-GCM，报文封装不同；
   - **提取**：`out_trade_no` / `trade_state` / `transaction_id`，
     `success = "SUCCESS".equals(trade_state)`。
   - 验签失败 / 报文非法 / 解密失败 / 缺 `out_trade_no` → 统一抛 `BizException(ILLEGAL_REQUEST)`，
     由控制器转 **500 FAIL** 触发渠道重推（与 ADR-029 应答口径一致）。
3. **Mock 实现口径与真实实现对齐**：Mock 环境无密文与签名，`MockWechatPaymentClient`
   把 body 当**明文 JSON** 解析，`trade_state` 优先、缺省回落 `result_code`，
   成功判定复用 `WechatCallbackDecoder.extract`；body 非 JSON → 同样 `ILLEGAL_REQUEST`。
   即 Mock/Real 在 pay-service 侧的回调分支行为一致（唯一差异在「报文形态」）。
4. **应用层（`PayManager`）**：
   - `handleWechatCallback(headers, rawBody)` —— 整包进、强类型出，不再触碰任何协议字段名；
   - **仅 `success=true` 才走 `markSuccessIfPending`**；非成功终态（`CLOSED` / `PAYERROR` / `REVOKED`）
     只 `log.warn` 记录 `trade_state` 与渠道交易号，**不改支付单状态**（保守：本平台支付单只有
     「待支付 → 成功 / 超时取消」两条迁移，引入渠道关闭态会牵出新的状态机分支与对账口径，
     超时由 `PayTimeoutJob` 收敛即可）；
   - `handleAlipayCallback(params)` —— 保留表单参数口径（`out_trade_no` / `trade_status`），
     协议键提为 `AlipayPaymentClient.PARAM_*` 常量，应用层不再出现裸魔法串。
5. **控制器只搬运**：`PayController` 收集**原始请求头 + 逐字节原文**透传，
   不再组装语义键。原文必须用字节流拷贝（`StreamUtils.copyToString`），
   **不能**用 `BufferedReader.readLine()` 拼接——后者吞掉行分隔符、改变字节序列，会让待签名串与报文不一致而验签失败（本次一并修复）。
6. **回调入参仍是非类型化的 Map / HttpServletRequest**：验签必须拿原始报文与签名头，
   属 ADR-023「入参必须类型化」的显式例外（与 ADR-029 第 3 条一致），
   类型化收敛到「通道适配层的**内部**」——`WechatCallbackResult` 是适配层对外契约，
   而非 HTTP 入参对象。

## 备选方案

| 方案 | 优点 | 缺点 |
| :--- | :--- | :--- |
| 在 `PayManager` 里就地解密并读键 | 改动面最小 | 协议知识（密钥、IV/AAD 规则、字段名）污染应用层；`ia-integration` 的 SDK 依赖被迫上浮；Mock/Real 口径易漂移 |
| 只有 Real 解密，保持 `verifyCallback` + 新增一个 `getOutTradeNo()` | 兼容旧签名 | 两步式契约正是断链成因；调用方仍可能只调验签那一步 |
| 返回弱类型 `Map<String,Object>`（解密结果原样给上层） | 零 DTO | 魔法串契约原样保留，等于没治；且无法在类型上表达 `success` 口径 |
| **强类型 `WechatCallbackResult` + 整包解析（采纳）** | 协议知识单点收口；应用层零魔法串；Mock/Real 同口径可测；解密可用固定密钥做全链路固件测试 | 新增一个 DTO + 一个解码器；`PaymentClient` 是破坏性接口变更（仅本仓两个实现，影响可控） |

## 后果

**正面**

- 存量 P0 断链修复：微信路径首次能够从解密后的报文取到 `out_trade_no`，支付回调可正常确认收款；
- 协议知识（密钥、IV/AAD、字段名、成功判定）单点收口在 `ia-integration`，pay-service 只消费强类型结果；
- 解密可独立测试：测试用固定 APIv3 密钥现场生成密文固件（`base64(ct‖tag)`）走真实解码链路，
  覆盖密钥不匹配、AAD 篡改、IV 不符、缺字段等分支，不依赖证书与线上环境；
- 非成功终态不再误推进支付单，避免「渠道已关闭 / 支付失败」被当成成功；
- 顺带修掉 `readRawBody` 的字节改写缺陷（`readLine()` 拼接吞行分隔符 → 待签名串失真）。

**负面 / 风险**

- `PaymentClient.verifyCallback(Map)` 被移除，属**破坏性接口变更**：仓内仅 `WechatPaymentClient`（Real）与其内部
  `MockWechatPaymentClient` 两个实现，且调用方只有 `PaymentChannelRouter`，已同步改造；外部若无该接口引用则无影响；
- `WechatCallbackResult.rawState` 对微信是 `trade_state`、对 Mock/支付宝是 `result_code`，
  是「渠道原始状态」的统一字段（口径已在 record Javadoc 说明），**不是**平台状态枚举，不参与状态机判定；
- 渠道关闭态（`CLOSED` 等）不改支付单状态属**刻意保守**：渠道侧关闭与本地超时关单之间可能存在
  时间差窗口，期间支付单仍显示「待支付」，需靠 `PayTimeoutJob` 收敛；若要精确对齐渠道终态，
  需引入独立的对账/关闭态分支（本次不引入）。
- 解密失败与验签失败共用 `ILLEGAL_REQUEST`，日志需靠 message 区分（渠道侧一律 500 FAIL，行为一致）。

## 日期

2026-10-03

## 参考

- `Implementation/back-end/ia-integration/src/main/java/org/icedamericanomall/integration/payment/WechatCallbackDecoder.java`（微信 v3 解密规范实现 + 明文提取）
- `Implementation/back-end/ia-integration/src/main/java/org/icedamericanomall/integration/payment/WechatCallbackResult.java`（强类型契约）
- `Implementation/back-end/ia-integration/src/main/java/org/icedamericanomall/integration/payment/WechatPaymentClient.java`（Real 验签 + 解密；Mock 明文 JSON 口径）
- `Implementation/back-end/pay-service/src/main/java/org/icedamericanomall/pay/app/PayManager.java`（`handleWechatCallback` / `handleAlipayCallback` / `confirmPaid`）
- `Implementation/back-end/pay-service/src/main/java/org/icedamericanomall/pay/adapter/web/PayController.java`（原始头 + 逐字节原文透传；`StreamUtils` 读体）
- ADR-029（渠道回调协议优先于 Result 信封 —— 本 ADR 的 500 应答口径依赖其决策）
- ADR-005（领域/适配纯净与端口化）、ADR-023（入参对象化及其显式例外）
- 微信支付 API v3 官方文档：回调通知的 resource 解密（AES-256-GCM + associated_data + nonce）
