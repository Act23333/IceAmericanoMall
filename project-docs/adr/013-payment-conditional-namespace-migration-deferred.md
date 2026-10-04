# ADR-013: 支付渠道条件装配命名空间暂不迁移（记录风险与迁移路径）

## 背景

V5.2 对 ia-integration 五组第三方客户端（sms / email / captcha / payment / storage）做条件装配一致性审计，发现统一的模板应为：

```
Mock 侧：@ConditionalOnProperty(name = "<ns>.enabled", havingValue = "false", matchIfMissing = true)
Real 侧：@ConditionalOnClass(<SDK 类>) + @ConditionalOnProperty(name = "<ns>.enabled", havingValue = "true")
```

四组已符合或本轮已补齐（`alipay.enabled` 为本轮新增开关；`minio` / `aliyun.sms` / `notification.email` 本已符合）。**唯独微信支付组不符合**：

```java
// Real
@ConditionalOnClass(NativePayService.class)
@ConditionalOnProperty(name = "wechat.pay.merchant-id")          // ← 存在性判定，非布尔开关
// Mock（内部类）
@ConditionalOnProperty(name = "wechat.pay.merchant-id", havingValue = "false", matchIfMissing = true)
```

而 `pay-service/application.yml` 中该键**恒存在**（`merchant-id: ${WECHAT_PAY_MERCHANT_ID:}`，环境变量缺省为空串）。`@ConditionalOnProperty` 的存在性判定不区分「值为空串」与「值已配置」，因此该组的实际激活状态对空值敏感、可读性差。

## 决策

**本轮不改动支付组条件，仅记录风险与迁移路径。**

理由：支付是资金链路，任何条件语义变更都可能把「真实支付」静默降级为「Mock 支付」。由于 `merchant-id` 键在 pay-service 恒存在，若简单地按统一模板改成 `wechat.pay.enabled`（默认缺省），现网形态会从「Real 生效」翻转为「Mock 生效」——即**线上收款变成假支付**。这种破坏性变更不能在共享模块的清理轮次里顺手做，必须与 pay-service 的配置迁移同批发布。

迁移路径（待独立任务执行）：

1. `pay-service` 的 `wechat.pay` 配置块显式增加 `enabled: ${WECHAT_PAY_ENABLED:false}`，并在生产环境变量中置为 `true`；
2. `WechatPaymentClient` 条件改为 `@ConditionalOnClass(NativePayService.class)` + `@ConditionalOnProperty(name = "wechat.pay.enabled", havingValue = "true")`，同时保留 `@ConditionalOnProperty(name = "wechat.pay.merchant-id")` 作为**低配即启动失败**的保险（`enabled=true` 但未配商户号时不给 Mock 兜底，直接报 Bean 缺失）；
3. `MockWechatPaymentClient` 条件改为 `havingValue = "false", matchIfMissing = true`，与其余四组对齐；
4. 发布前以「Mock 环境 + Real 配置」与「Real 环境 + Real 配置」两种组合跑一遍启动与下单验证。

## 备选方案

| 方案 | 优点 | 缺点 |
| :--- | :--- | :--- |
| 本轮直接按统一模板改 | 五组完全一致 | 极可能把现网真实支付静默降级为 Mock；需与 pay-service 同批发布，超出共享模块清理范围 |
| 保持现状不动、不记录 | 零风险 | 认知债留存，下一个人会「顺手统一」从而触发上述事故 |
| **不动 + ADR 记录 + 显式迁移路径（采用）** | 规避资金风险，同时把知识固化下来 | 五组存在一处已知不一致，需靠 ADR 与 Javadoc 双重提示 |

## 后果

- ✅ 本轮零资金风险；`WechatPaymentClient` 的 Javadoc 已标注「条件装配与其余四组不一致，迁移见 ADR-013」
- ⚠️ 五组条件装配仍有一处不一致，属**已知并已记录**的技术债
- ⚠️ pay-service 在「`merchant-id` 键存在但为空 + 本机无 `/etc/wechat/apiclient_key.pem`」时，`@PostConstruct` 构建 SDK 会失败 —— 这是既有行为，本轮未触碰；建议纳入上面的迁移任务一并处理

## 日期

2026-09-29

## 修订

### V5.3（2026-10-03）：空串缺陷已修复（不采用原「enabled 默认 false」迁移路径）

审计进一步确认：本 ADR 记录的风险低估了 —— 「`merchant-id` 键恒存在但值为空串」不只是
「对空值敏感」，而是**干净环境 pay-service 启动即崩**：真实实现被装配（空串算「存在」），
其 `@PostConstruct` 用空 merchantId + 不存在的私钥路径构建 SDK 抛异常；Mock 又因
`havingValue = "false"` 不匹配空串而不装配。即两条分支同时不成立。

**落地方式（改动了原迁移路径的第 2、3 步）**：不引入 `enabled` 默认 `false`（那会把已配置
环境变量的现网从 Real 翻转成 Mock，正是本 ADR 要避免的资金事故），改为**读值判空 + 可选 kill-switch**：

```java
// WechatPayConditions（ia-integration）
static boolean isRealPayEnabled(Environment env) {
    String merchantId = env.getProperty("wechat.pay.merchant-id");
    if (merchantId == null || merchantId.isBlank()) return false;   // 空串/空白 → Mock
    return !"false".equalsIgnoreCase(env.getProperty("wechat.pay.enabled")); // 显式关闭开关
}
```

- `merchant-id` 非空且未显式关闭 → Real（与迁移前行为一致，不翻转）；
- `merchant-id` 空/缺失/全空白 → Mock（修复启动崩溃）；
- `wechat.pay.enabled=false` → 强制 Mock（显式 kill-switch，供演练/故障处置）；
- 两个条件严格互补，保证任何配置下恰好一个 `PaymentClient`。

**与 ADR-013 原计划的差异**：第 1 步（yml 增加 `enabled`）仍需主控在 pay-service 落地
（建议 `enabled: ${WECHAT_PAY_ENABLED:}`，空 = 自动），但**不再是激活真实支付的必要前提**；
第 2 步「`enabled=true` 但未配商户号时直接 Bean 缺失」未采用 —— 缺商户号属于配置缺失，
静默落 Mock 比启动失败更符合现有 pay-service 的部署约定（且启动失败会拖垮整个支付服务）。
验证方式仍遵循第 4 步：Mock 环境 + Real 配置、Real 环境 + Real 配置两种组合各跑一遍启动与下单。

**回归测试**：`ia-integration` 的 `WechatPaymentClientWiringTest` 覆盖
「缺失 / 空串 / 空串+enabled=true → 仅 Mock」「merchant-id 非空 → Real 条件成立」
「enabled=false → 强制 Mock」「全空白 → Mock」。

## 参考

- `Implementation/back-end/ia-integration/src/main/java/org/icedamericanomall/integration/payment/WechatPaymentClient.java`
- `Implementation/back-end/pay-service/src/main/resources/application.yml`（`wechat.pay` 配置块）
- `ADR-009`（Bean 注册与条件装配的既有约定）
