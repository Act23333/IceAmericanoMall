# ADR-017: user 域资料/凭证分离与认证边界收敛（PII 加密 + RBAC 收尾）

## 背景

V5.1 完成"登录标识归 user_auth"的第一步（ADR-008：邮箱验证码登录、user_auth 表落地），但 `user` 表仍保留 `password` / `wx_openid` 历史列，产生了三类结构性问题：

1. **凭证脑裂（认证不可用性缺陷）**：`AuthManager.register` 只调 user-service 建 `user` 行并写 `user.password`，authorization-service **本地不建 `user_auth` 凭证行**；而 `PasswordLoginStrategy` 只查本地 `user_auth` 盲索引 + `verifyPassword` —— 注册用户**密码登录必然失败**（`user_auth.credential` 全库无写入路径，`resetPassword` 同样只改 `user.password`）。密码凭证有两个"所有权方"，两边都能写、登录只认一边。
2. **PII 明文与身份列混存**：`user.phone` 为明文 + `phone_hash` 占位列（从未写入）；`user.wx_openid` 承载微信身份；`user.email` / `gender` / `birthday` / `member_level` 等资料字段缺失（邮箱身份只能停在 user_auth 侧）。
3. **RBAC 三处收尾缺口**：授权变更（sys_user_role / sys_role_permission 写入）后 **不失效** `user:perms:{userId}` 缓存（撤销的权限最长 30 分钟仍生效）；网关对无 JWT 的公开路径**不清洗入站请求头**，外部可携带伪造 `X-User-Id` / `X-User-Roles` 直通下游（下游 `UserContextFilter` 照常重建为用户上下文）；`user.role_type` 列与 RBAC 五表并存（双写口径）。

本决策由用户拍板定稿：资料/凭证分离采用 `EncryptedIdentifier` 三字段模型，权限**不引入** `user.permissions` JSON 列（RBAC 五表 + Redis 缓存保持权威），认证边界收敛为 "`user_auth` 凭证唯一所有权归 authorization-service"。

## 决策

### 一、User 聚合与 user 表：资料/凭证分离（资料归 user，凭证归 user_auth）

1. **聚合根字段（定稿）**：`id`（技术主键）、`userId`（业务 UUID）、`nickname`、`avatar`、`gender`、`birthday`、`balance`、`phone`（`EncryptedIdentifier`）、`email`（`EncryptedIdentifier`）、`memberLevel`、`status`、`createTime` / `updateTime`。
   - **移除**：`password`、`wxOpenid`（凭证归 user_auth，ADR-008/017）；`verifyPassword` / `changePassword` 行为删除（密码校验能力不再出现在 user 域，`PasswordVerifier` 端口与适配器一并删除）。
   - **术语统一**：领域字段 `username` → `nickname`（展示语义）；持久化列名保留 `username`（历史兼容，Converter 显式映射），`UserInfoResp.username` 报文保持不变。
   - **字段表偏差说明（记录在案）**：定稿字段表中的 `private Long userId` 与 ADR-002/006 双主键（`user_id` VARCHAR(32) UUID 业务键）冲突，且 `/api/user/**` 报文按 UUID 输出 —— 按红线（契约不变 + 双主键策略）保留 `String userId`；`registerTime` / `lastLoginTime` 未列入定稿字段表：前者由 `create_time` 承担（`register_time` 列仍由 metaObjectHandler 填充以满足 NOT NULL，领域不再读取），后者随登录链路退出 user 上下文而停止写入。
   - **行为新增**：`changePhone` / `changeEmail`（格式校验 + 归一化 + 三字段整体替换）、`changeGender` / `changeBirthday` / `changeMemberLevel`（含取值校验）；`/api/user/profile` PATCH 白名单扩展 `gender` / `birthday`（`memberLevel` 不开放用户自改）。
2. **PII 三字段模型（全量激活）**：`phone_cipher`（AES-256-GCM）/ `phone_hash`（HMAC-SHA256 盲索引）/ `phone_mask`；`email_cipher` / `email_hash` / `email_mask` 新增。明文 `phone` 列退役（迁移器搬数后置 NULL）；等值查询一律走盲索引（密文不可比较）。
   - **唯一键**：`uk_phone` → `uk_phone_hash`（V2.5 已建，本迁移确保生效）、新增 `uk_email_hash`。
   - **密钥装载缺口修复**：`AesGcmUtil` / `BlindIndexUtil` 此前**只在单测里 init**，运行期无装载点（任何 `EncryptedIdentifier.fromPlain` 都会抛 `CryptoException`）。新增 ia-common `CryptoKeyConfig`（自动装配）：`ia.crypto.aes-key` / `ia.crypto.hmac-key`（Base64 32 字节，生产经 KMS/环境变量注入；缺省回退内置开发密钥并 WARN；非法密钥启动快速失败）。盲索引密钥必须跨服务一致（user-service `phone_hash` 与 auth `identifier_hash` 需对同一手机号算出同一值）。
3. **奖励语义统一（行为变化声明）**：新用户奖励从"微信新用户登录时发"改为"**所有新用户建档时发**"（注册 / 短信首登 / 邮箱首登 / 微信登录共用建档漏斗 `UserProvisioner` 新建分支 → `NewUserRewardService.award`，已存在用户不发；积分 200、类型 2 沿用）。"回归登录奖励（>30 天 100 分）"**退役**（登录链路已不在 user-service，无触发点）；`user.last_login_time` 列停止写入（deprecated）。奖励失败只 WARN 不阻断建档（历史语义）。
4. **幂等口径（记录在案）**：`/internal/user/getOrCreate` 对 PHONE / EMAIL 按盲索引短路（EMAIL 为新增能力）；WECHAT 类标识 user 表不再存定位列，端点内不存在按 openid 的二次确认 —— 幂等由调用方 `user_auth` 唯一键 + 建档锁（`user_auth:create:`）保证（ADR-008 既有机制），重复调用会新建资料行属已知取舍（见"后果·风险"）。

### 二、认证边界收敛：user_auth 凭证唯一所有权归 authorization-service

1. **注册重写**：`AuthManager.register` = ①本地校验并消费短信验证码（verification-service）→ ②`UserAccountGateway.getOrCreateUserId(PHONE, phone, nickname)`（user-service 幂等建档 + 奖励 + 昵称）→ ③本地 `UserAuthProvisioner.findByHashOrCreateWithCredential`（PHONE + 三字段 + `credential=passwordVerifier.encode(password)`）→ ④签发 token。
   - 已存在**带凭证**的行 → 拒绝（`PHONE_ALREADY_REGISTERED`，防通过注册接口覆盖既有密码 = 账号接管）；已存在**无凭证**的行（先短信登录）→ 只补 credential。
   - **用户名占用校验（USERNAME_ALREADY_TAKEN）退役**：`user` 表无 `uk_username` 约束（历史检查仅为应用级提示），昵称降级为展示字段；如需恢复唯一性须走 05 契约 + DDL 流程。
2. **重置密码重写**：`verifyAndConsume(SMS, phone, code)` → 按手机号盲索引定位 `user_auth` → `updatePassword(encode(new))`。不再调用 user-service（user 表不再持有密码）。
3. **微信网页登录收归本地**：删除 `WechatWebLoginGateway`（Feign 中转 user-service），改为 `WechatWebSessionService`（直连 `WechatOAuthThirdParty.getUserInfoByCode`，与小程序 `WechatMiniSessionService` 对称）+ `UserAuthProvisioner.findByPlainOrCreate(WECHAT, openid, unionId, nickname)`。冻结校验语义对齐：仅校验 `user_auth.status`（与密码/短信/邮箱/小程序策略一致）；user 资料级冻结的拦截点在下游业务 API 与风控处置 —— 旧实现"微信登录额外回查 `user.status`"随端点废弃退出（行为变化已在后果声明）。
4. **`/internal/auth/**` 整体废弃**：`AuthClient` + `AuthClientFallback` + user-service `InternalAuthController` + `UserAuthManager`（六方法）删除；`user-service` 保留 `sms:code:` 读路径（`SmsCodeVerifier` + `000000` 灰度后门 + ADR-016 恢复路径原样保留）。
5. **DTO 处置**：`RegisterReqCO` / `PasswordLoginReqCO` / `SmsLoginReqCO` / `WechatWebLoginReqCO` / `WechatMiniLoginReqCO` / `ResetPasswordReqCO` / `LoginRespCO` 全仓 grep 后无其他消费方，随契约一并删除；`UserAuthCO` 保留并增量 `nickname`（建档透传）。

### 三、RBAC 收尾（三类缺口）

1. **授权变更主动失效缓存**：新增 `PermissionCacheEvictor`（应用层）+ `UserSecurityCache.evict(userId)`（端口扩展，删 `user:perms:` 与 `user:info:` 两键）。调用点：`RoleManageManager.setUserRole`（用户维度）、`assignPermissions`（role_id 反查持有者，`RoleRepository.findUserIdsByRoleId` 新增）、`deleteRole`（级联删 `sys_user_role` 前先取受影响用户集合）。失效失败只 WARN（缓存可自愈）。`sys_user_permission` 当前无写入端点（仅有读路径），新增直授端点时必须调用本失效器。
2. **网关剥离伪造头**：新增 `UntrustedHeaderSanitizerFilter`（order = **-1100**，过滤器链最早）无条件移除入站 `X-User-Id` / `X-Username` / `X-User-Roles` / `X-User-Permissions` / `X-Tenant-Id` / `X-Client-Ip`（可信注入只发生在 JWT 校验后的 `UserContextHeaderFilter`）；`X-Trace-Id` 保留透传（链路追踪语义）。
3. **`role_type` 退役**：user 聚合 / DO / 仓储（`updateRoleTypeById`）/ `UserManager.updateRole` 全部移除写入与读取；`/api/user/info` 与 `/api/admin/user/page` 的 `roleType` 兼容字段改由 RBAC 五表角色编码**派生**（`RoleTypeDerivation`：ADMIN→2 / SELLER→1 / 其余→0；管理后台用批量角色查询避免 N+1）。列保留并注释 deprecated（观察期后删列）。
4. **权限模型否决项（记录理由）**：**不引入** `user.permissions` JSON 列 —— 权限是"派生数据"而非用户资料，JSON 列会与五表（尤其 `sys_user_permission` 直授）产生双写口径、且无法参与 SQL 级联与审计；保持五表 + `user:perms:{userId}` Redis SET（30 分钟 TTL + 新增主动失效）不变。

### 四、存量迁移器（authorization-service，`auth.legacy-migration.enabled` 默认关）

`LegacyCredentialMigrationRunner`（CommandLineRunner，逐条独立事务，失败 WARN 不中断，幂等可重跑）：
1. **密码迁移**：`user.password` 非空 → 解析手机号（优先明文列，其次解密密文列）→ 按盲索引找 `user_auth`（无则建 PHONE 三字段行；有则校验归属后仅补 credential）→ `user.password = NULL`；
2. **手机号加密**：`user.phone` 明文 → `phone_cipher/phone_hash/phone_mask` → `user.phone = NULL`；
3. **微信身份**：`user.wx_openid` 非空且无 WECHAT 凭证行 → 建行（`identifier_plain=openid`）；`wx_openid` 列本轮不清理（人工复核）。

旧列（`password` / `wx_openid` / `role_type` / 明文 `phone` / `last_login_time`）本轮**不删**，人工复核后走删列流程。执行前置：PII 密钥已装载（`CryptoKeyConfig`）。

## 备选方案

| 方案 | 优点 | 缺点 |
|---|---|---|
| 凭证双写（user.password + user_auth.credential 同步维护） | 兼容存量列，改动小 | 两个所有权方长期并存 = 脑裂永久化；每次改密/注册都要双写两服务 |
| 登录时按需懒迁移（首次密码登录补写 user_auth） | 无需停机迁移 | "注册即密码登录"仍不可用（注册到首登之间窗口期）；迁移时机不可控 |
| **凭证唯一所有权归 auth + 启动迁移器（采用）** | 边界彻底、脑裂根除、迁移可控可重跑 | 需一次性迁移窗口；旧列观察期 |
| `user.permissions` JSON 列承载权限 | 查询少一跳 | 双写口径 + 与五表冲突 + 不可审计；权限是派生数据不应资料化（已否决） |
| 网关仅对"无 JWT 请求"清洗头 | 改动最小 | 有 JWT 请求的头伪造仍需依赖下游覆盖行为；公开路径与认证路径规则分裂，易漏 |
| **过滤器链最早处无条件剥离（采用）** | 规则单一（入站一律不可信）；下游零改动 | 依赖 service 间不借道网关传上下文（本仓 Feign 直连 lb://，成立） |

## 后果

- ✅ **凭证脑裂修复**（有测试证据）：`RegisterThenPasswordLoginTest` 以真实 provisioner + 真实密码策略证明"注册 → 密码登录成功"；重复注册被拒（防接管）。
- ✅ user 表成为纯资料表：PII 全量三字段加密（含邮箱新能力）；`role_type` / `password` / `wx_openid` 读出写入路径清零（grep 验证）。
- ✅ RBAC 收尾：撤销权限即时生效（主动失效）、伪造上下文头在网关入口被剥离（含单测）、`roleType` 口径唯一化（五表派生）。
- ✅ 对外契约不变：`/api/auth/**`、`/api/user/**` 路径与报文保持（增量字段 gender/birthday/memberLevel/email；phone/email 返回 mask）；`/internal/user/getOrCreate` 幂等语义保持（WECHAT 见风险）；`user:perms:` 契约不变（新增失效机制）。
- ⚠️ **行为变化（需产品/前端知悉）**：
  1. 奖励统一为"新用户建档"（SMS/邮箱首登也发 200 分）；"回归登录奖励"退役；
  2. 注册的用户名占用校验退役（昵称可重复）；
  3. 微信网页登录不再因 `user.status=FROZEN` 被拒（与其他登录方式一致；冻结拦截在下游）；
  4. `/api/user/info` 的 phone/email 由明文改为掩码；registerTime 回退 create_time；
  5. 登录不再更新 `user.last_login_time`。
- ⚠️ **风险与遗留**：
  1. `getOrCreate` 对 WECHAT 的端点内幂等依赖调用方（user_auth 唯一键 + 锁）；极端重试（user 行已建、凭证行未落）会产生重复资料行与重复建档奖励 —— 需在 05 契约文档标注，后续可评估以"建档幂等键"补强；
  2. 开发缺省密钥仅为联调便利，生产必须注入 `IA_CRYPTO_AES_KEY` / `IA_CRYPTO_HMAC_KEY`（否则等于未加密）；
  3. `X-Forwarded-For` 仍由 auth 侧 `resolveClientIp` 读取（Nginx 契约），伪造面未在本次收敛范围；
  4. 旧列删除（password/wx_openid/role_type/phone/last_login_time）需在迁移复核完成后另行 DDL；
  5. `sys_user_permission` 无写入端点 —— 新增时必须接入 `PermissionCacheEvictor`。

## 日期

2026-10-01

## 参考

- `project-docs/adr/002-dual-primary-key-and-money.md`（双主键策略 —— `userId` 保留 UUID 的依据）
- `project-docs/adr/005-domain-purity-ports-and-eventual-consistency.md`（领域纯净 + 端口化）
- `project-docs/adr/006-cross-service-userid-semantics.md`（getOrCreate 幂等端点）
- `project-docs/adr/008-email-code-login-and-credential-routing.md`（登录标识归 user_auth）
- `project-docs/adr/016-user-service-ddd-and-sms-migration.md`（sms:code 契约与灰度后门恢复路径）
- `database/migrations/V5.2__user_profile_split_and_encryption.sql`（结构变更）
