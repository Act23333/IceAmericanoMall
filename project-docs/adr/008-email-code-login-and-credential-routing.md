# ADR-008: 邮箱验证码登录 —— 复用 `/api/auth/web/login` 并按 credentialType 路由

## 背景

需要新增"邮箱 + 邮箱验证码"登录（含发码端点）。现状有两个约束：

1. **`WebLoginReq` 的 `credentialType` 被服务端忽略**：适配层硬编码 `CredentialTypeEnum.PASSWORD`（`project-docs/contract/05-API-Specification.md` §4.1 A1 原文："credentialType?(仅语义，服务端固定 PASSWORD)"），导致 `SmsLoginStrategy`（PHONE + SMS_CODE）**没有任何 HTTP 入口** —— 领域策略、工厂、测试齐备却永远不可达，属既有缺陷。
2. **人机验证参数未接线（R3）**：`BaseLoginReq.captcha` 字段从未被读取，`LoginCommand.captchaProvider/captchaPayload` 恒为 null，风控抛出的 `CAPTCHA_REQUIRED` 无法闭环。

端点方案需要在"复用 `/web/login`"与"新增 `/api/auth/email/login`"之间取舍。

## 决策

1. **复用 `/api/auth/web/login`**，新增可选字段 `credentialType`（缺省 PASSWORD），服务端按"身份类型 + 凭证类型"分发领域策略：PASSWORD / SMS_CODE / EMAIL_CODE（白名单，其余取值拒绝；微信类走专属端点）。**同时修复 SMS_CODE 不可达的既有缺陷**。
2. **新增公开发码端点 `POST /api/auth/email/code`**（邮箱 + 可选场景）→ `VerificationClient.issueCode(EMAIL, ...)`；重发限流（60 秒 / 错误 5 次作废）由 verification-service 内建，auth 不重复实现；响应只回脱敏邮箱与有效期，不回验证码明文。
3. **人机验证参数增量映射**：请求体新增可选 `captchaProvider / lotNumber / captchaOutput / passToken / genTime`（与极验 v4 二次校验四要素一一对应，字段风格与 user-service 的 `SmsCodeSendReq` 一致），映射为强类型值对象 `CaptchaVerifyParam` 进入 `LoginCommand`；`captchaId` 由服务端下发（随 `CAPTCHA_REQUIRED` 的 payload），**不接受**前端传入。
4. **未配置人机验证时跳过并 WARN**（`captcha.enabled` 默认 false）：不得因"要求验证但无可用验证组件"拦死全部登录；生产必须显式 `CAPTCHA_ENABLED=true`。
5. **领域建模**：新增 `EmailLoginStrategy`（EMAIL + EMAIL_CODE）与值对象 `EmailAddress`（创建即校验 + `trim/lowercase` 归一化 + 盲索引），保证盲索引、Redis 验证码 Key、user-service 建号标识三方对齐；`VerificationCodeService` 端口统一承载短信/邮箱两条通道的"校验并消费"。邮箱注册不在本次范围（PRD 注册仅手机号）。

## 备选方案

| 方案 | 优点 | 缺点 |
|---|---|---|
| **复用 `/web/login` + credentialType 路由（采用）** | 少一个端点；与既有"身份+凭证"矩阵设计一致；顺带修复 SMS_CODE 不可达 | 需在白名单约束下放开凭证类型（已用 `WEB_LOGIN_CREDENTIALS` 限定） |
| 新增 `/api/auth/email/login` | 端点语义直白、参数更少 | 端点数量随登录方式线性膨胀；SMS_CODE 不可达缺陷仍在；前端需要维护两套登录调用 |
| 前端传 `identityType` 推断凭证类型 | 前端更省字段 | 隐式推导易产生歧义，且无法表达"邮箱+密码"与"邮箱+验证码"的区分 |
| 保留 `captcha` 不透明字符串 + JSON 编解码 | 改动最小 | 需在适配层做一次 JSON 编解码，字段错误到运行期才暴露 |

## 后果

- ✅ PASSWORD / SMS_CODE / EMAIL_CODE 三种登录方式共用同一端点，前端只需切换 `credentialType`
- ✅ 既有调用方零影响：`credentialType` 缺省 PASSWORD，新增字段全部可选（增量契约）
- ✅ R3 闭环成立：风控 `CAPTCHA_REQUIRED`（1016，payload 含 `captchaRequired/provider/captchaId`）→ 前端渲染验证组件 → 重试时携带四要素 → 适配层映射 → 应用层校验通过 → **风控处置不再重复要求验证**（`RiskAssessment.captchaVerified` 由应用层置入，`CaptchaHandler` 命中即放行）
  - 闭环修复说明：原实现中 `CaptchaHandler` 只看风险等级/标签，不感知"步骤 1 是否已校验通过"，
    因此带着合法验证凭证重试仍会被要求验证 —— 该循环无法收敛，属本次一并修复的缺陷（`RiskAssessment` 新增不可变标记 + 早退分支）
- ⚠️ 人机验证开关默认关闭：dev 便利性优先，**生产必须显式开启**（否则风控要求人机验证的处置退化为放行 + WARN）
- ⚠️ 网关 `SecurityConfig` 的公开路径白名单原为 V5.1 前的历史路径，本次增量补齐 `/api/auth/web/login` 等真实端点与 `/api/auth/email/code`；前端仍在使用历史路径 `/api/auth/login`（`packages/auth/actions.ts`），需前端侧同步
- ⚠️ 邮箱登录为新增能力，PRD 尚未收录，需在 `project-docs/product/01-PRD.md` 与 `03-Domain-Model.md` 词汇表补条目

## 日期

2026-09-29

## 参考

- `project-docs/contract/05-API-Specification.md` §4.1（A1 登录端点、新增 A10 邮箱发码）
- `007-verification-capability-convergence.md`（校验能力收口与降级策略）
