# ADR-016: user-service DDD 四层重构与短信直发链路迁移 verification-service

## 背景

user-service 是最后一个未完成四层归位的业务服务（authorization-service 已于 985ec25 完成验证）。此前状态存在三类问题：

1. **结构半迁移**：上一轮包名归位（47b0535）只做了 import 级机械适配，遗留 `service/impl` 老式分层、`domain/entity` 内的 MyBatis DO、`mapper/`、`convert/`、`manager/` 混层，且 `UserServiceImpl` 处于不可编译的半迁移状态（R2 语法错误 + 引用不存在类型）。
2. **领域不纯**：业务规则分散在 Service 中（地址归属校验、商家状态流转、签到积分规则），Redis / 分布式锁 / Feign 等能力直接散落在业务类里，无端口化。
3. **发码链路自闭环**：`SmsServiceImpl` 本地生成验证码、本地 `sms:limit:` 限流、经 ia-integration 的 `SmsClient` 直连短信通道 —— 与 ADR-007「校验能力收口 verification-service」的方向相悖；同时 authorization-service 登录侧已走 verification-service 在线校验，两端靠 `sms:code:` Key 契约互通。

## 决策

### 一、四层归位（严格复制 authorization-service 已验证模式）

1. **分层与聚合划分**：
   - `adapter/`：REST/内部 Controller + `request/`（出参对象）+ `response/`（视图对象）+ `group/`（校验分组）+ `resolver` 能力内联。
   - `app/`：按聚合分子包的编排层（`user / address / seller / signin / sms / rbac / points / favorite / history / notification / chat / knowledge / oplog`）+ `app/port`（应用层端口）。
   - `domain/`：`model`（纯 POJO 聚合根：User / Address / Seller + 纯枚举）、`port`（DistributedLock / PasswordVerifier / SignInStore / SmsCodeStore）、`gateway`（VerificationGateway / WechatOAuthGateway 防腐层）、`repository`（UserRepository / AddressRepository / SellerRepository / RoleRepository）、`service`（UserProvisioner / SellerRegistrar / SignInPointsPolicy / SmsCodeVerifier）、`exception`（DuplicateUserException）。
   - `infrastructure/`：`persistence/<聚合>/`（DO + Mapper + 静态 Converter + RepositoryImpl）、`adapter/`（端口实现：Redisson 锁 / BCrypt / Redis Bitmap 签到 / Redis sms:code / Redis user:perms 缓存 / Redis 店铺关注 / Redis 聊天缓存 / MinIO 头像）、`client/`（VerificationGateway / WechatOAuthGateway 实现 + VerificationResultMapper）、`config/`（DomainBeanConfig / SecurityConfig / 等）、`constant/`。
2. **领域纯净**：domain 包零 spring / mybatis / redisson / feign import；业务规则内聚在实体方法（User 禁用/启用状态迁移、凭证校验、余额不变量；Address 归属断言；Seller 状态机）；领域服务为纯 POJO，由 `DomainBeanConfig` 显式装配。
3. **仓储业务键**：三聚合仓储（User/Address/Seller）按业务键操作 —— User 创建走 `uk_user_id`/`uk_phone`/`uk_wx_openid` 唯一键、不做「按 id 判断新增或更新」；Address 更新/删除/标默认以 `{userId, id}` 为 WHERE（SQL 级越权兜底）；聚合装载后的资料/状态/余额变更按 id 定位（技术主键是集群内部口径，ADR-006）。
4. **强制保留契约**（零变化）：`/api/user/**`、`/api/shop/**`、`/api/chat/**`、`/internal/user/**`（含 `/getOrCreate` 幂等）、`/internal/auth/**` 全部路由与报文；`user:perms:{userId}` / `user:info:{userId}` Redis 契约（网关实时权限依赖，30min TTL，ROLE_ 前缀）；`@RateLimit` 注解位置；`/internal/**` 裸异常语义。响应报文以「DO→VO 字段逐一对照」方式保持 JSON 等价（含 Notification 的派生 `unread` 字段）。
5. **务实取舍（记录在案）**：外围 CRUD 特性（points/favorite/notification/chat/knowledge/oplog/rbac 读模型）无跨实体不变量，按 CQRS 读路径直接使用持久化读模型（Mapper），DO 不出持久化层进入 Controller；响应对象（`adapter/response`）由应用层组装返回，适配层只做 Result 包装 —— 与 authorization-service 的 DTO→VO 全分离相比，此处以契约对象复用换取零重复壳类（domain 纯度红线已守住；后续如引入多端差异化出参再拆分）。
6. **附带修正**：微信登录的外部换取（`WechatOAuthGateway`）移出事务边界（原实现在 `@Transactional` 内发起外部调用）；唯一保留的本地事务是 `UserRegistrar`（user 行 + 默认角色，注册链路原子性，与迁移前语义一致）。
7. **测试基线只增不减**：迁移前 user-service 22 个测试（阶段 0 修复后基线），重构后 43 个（H2 集成测试迁移保绿 + 领域纯单测新增；全仓 17 模块 279 个测试 0 失败）。

### 二、短信直发链路迁移 verification-service

1. **发送迁移**：`POST /api/user/code` 的人机验证与发码改走 ia-api `VerificationClient`（`/captcha/verify` + `/code/issue`）：
   - 人机验证收口（与 authorization-service 一致，ADR-007）：极验四要素透传 verification-service，本服务不再直连 `CaptchaClient`；
   - 签发（生成 + 5 分钟有效 + 60 秒重发限流）与投递（notification-service → 第三方通道）全在远端完成，**写同一 `sms:code:{phone}` 契约**；
   - 失败结果码按 authorization-service 的 `VerificationResultMapper` 先例映射：`RATE_LIMITED → SMS_RATE_LIMITED`、`SEND_FAILED → SMS_SEND_FAILED`、依赖不可用 → `SYSTEM_BUSY`（fail-closed，绝不默认成功）。
   - 旧链路清理：本地验证码生成、`sms:limit:` 本地限流、`SmsClient` 直连引用全部删除（ia-integration 的类保留，仅去 user-service 侧引用）；保留 Controller 的分级手机号限流与 `@RateLimit`（独立键空间 `sms:minute/hour/day:`，与远端 60 秒窗口互补）。
   - scene 取值：`/api/user/code` 被注册/短信登录/找回密码共用、前端不传场景，故透传中性自描述值 `USER_CODE`（仅用于远端投递文案与埋点归因；notification-service 只做通道投递，无白名单校验）。
2. **verifyCode 本地保留（灰度后门不动）**：`SmsCodeVerifier`（domain 服务）读同一 `sms:code:` 契约，`000000` 万能码旁路原样保留 —— 该后门是用户为灰度测试环境（人机验证/短信通道未接入时的联调）刻意保留的入口。**严格比对分支（不存在→已过期、不匹配→错误）当前刻意保持关闭**（迁移前即为注释状态，灰度联调依赖宽松放行）。
3. **后门迁移路径（灰度结束后执行）**：① 恢复 `SmsCodeVerifier` 的严格比对分支并补失败分支单测；② 移除 `000000` 旁路常量；③ 评估 `verifyCode` 本地校验整链路切换为 `VerificationClient.verifyCode` 在线校验（一次性消费 + 错误计数由远端承担，authorization-service 已按此运行）；④ 为 `/api/user/code` 端点补 `scene` 入参（契约新增，走 05-API-Specification 流程）。前置条件：所有联调环境接入真实短信通道。

## 备选方案

| 方案 | 优点 | 缺点 |
|---|---|---|
| 保持 `service/impl` 老式分层，仅修语法 | 改动最小 | 与 authorization-service 已落地的四层模式持续分叉；领域纯度、端口化目标无法达成 |
| 全量 DTO/VO 双壳分离（auth 模式逐字复制） | 分层最严格 | 10+ 响应对象双壳复制，契约风险与工作量显著上升，收益低（域纯度红线不涉及 app/adapter 边界） |
| **四层归位 + 三聚合完整领域化 + 外围 CQRS 直读（采用）** | 核心域纯度与端口化达标；改动面可控 | 外围特性保留事务脚本风格（已在决策第 5 条明确为记录在案的取舍） |
| 发码继续本地直发（不迁移） | 零风险 | 与 ADR-007 校验能力收口方向相悖；短信通道/极验双份实现持续分叉 |
| **发码迁移 + 本地 verifyCode 保留（采用）** | 一处收口、两端互通（sms:code 契约不变）；灰度后门与严格校验恢复路径明确 | 灰度期存在「发码在远端、校验在本地」的过渡态（契约相同，语义成立） |

## 后果

- ✅ user-service 与 authorization-service 分层模式对齐，领域层可脱离框架独立测试（新增 30+ 纯单测/H2 测试）
- ✅ 校验能力（人机验证 + 发码）在本项目内单一实现（verification-service），短信/极验通道不再双份分叉
- ✅ 全部对外/内部契约路径与报文保持不变（`/api/user`、`/api/shop`、`/api/chat`、`/internal/*` 逐一比对；`user:perms:` 契约未变）
- ⚠️ 运行前提增加：user-service 发码依赖 verification-service + notification-service 链路可用（熔断降级 fail-closed：发码请求失败而非静默成功）
- ⚠️ user-service 新增 `redisson-spring-boot-starter` 依赖（DistributedLock 端口，镜像 authorization-service）；启动时建立 Redisson 连接（Redis 本已是运行前提）
- ⚠️ 灰度后门（000000 + 宽松校验）仍存在，恢复计划见决策二·3；在真实短信通道全面接入前不得上线生产环境
- ⚠️ 微信小程序登录端点（`/internal/auth/wechat/mini/login`）保持未接入状态（小程序登录已在 authorization-service 闭环）；路由保留，返回 null，无活跃调用方
- ⚠️ 附带修复：ia-api `AddressClient` 路径由 `/internal/address/address/{id}` 修正为 `/internal/address/{id}`（对齐服务端路由，修复下单地址快照的运行期 404）；`SmsLoginReqCO` 补回被注释掉的 `code` 字段（`/internal/auth/web/sms/login` 校验链路必需）
- ⚠️ 已知遗留（本次未动，另行立项）：`UserClient` 幂等回查等存量契约已对齐；但 `UserProfileClient` 的返回类型（`Result<List<String>>`）与 user-service `/api/user/history`、`/api/user/favorite` 实际报文（分页对象）不一致 —— ai-service `UserProfileTool` 调用会反序列化失败；`@Version` 乐观锁因缺少 `OptimisticLockerInnerInterceptor` 未实际生效（两处均为存量问题，需 05 契约与 ia-common 配置另行修复）

## 日期

2026-10-01

## 参考

- `project-docs/adr/005-domain-purity-ports-and-eventual-consistency.md`（领域纯净 + 端口化 + 无事务最终一致性）
- `project-docs/adr/006-cross-service-userid-semantics.md`（跨服务 userId 口径 —— getOrCreate 幂等端点）
- `project-docs/adr/007-verification-capability-convergence.md`（校验能力收口 verification-service）
- `project-docs/adr/008-email-code-login-and-credential-routing.md`（登录标识归 user_auth）
- `project-docs/adr/003-object-isolation-do-dto-vo.md`（DO 隔离 —— DO 不出持久化层）
- `project-docs/adr/015-package-and-contract-naming.md`（`app` / `XxxCO` / `XxxVO` 命名）
