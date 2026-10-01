# ADR-006: 跨服务 userId 语义（业务 UUID vs 技术主键）

## 背景

user-service 的 `user` 表采用双主键（技术主键 `id` BIGINT + 业务标识 `user_id` VARCHAR(32) UUID）。authorization-service 重构中发现：注册链路 user-service 内部端点返回的 `userId` 是 UUID 业务键，而 authorization-service 签发的 JWT claim、网关透传 header、各业务表（orders/cart/cart 等）的 `user_id BIGINT` 外键全部使用技术主键。直接 `Long.parseLong(UUID)` 必然失败。

## 决策

1. **集群内部统一以技术主键 `user.id` 为跨服务标识**：JWT claim / 网关 header / 所有业务表外键均用 `BIGINT id`；UUID 业务键仅用于对外暴露与 user-service 域内。
2. **authorization-service 注册/登录后按业务键幂等回查技术主键**：经 `UserAccountGateway.getOrCreateUserId(identityType, identifier)` 调 user-service `/internal/user/getOrCreate`（幂等端点），不解析 UUID。
3. **领域仓储不接触技术主键**：`user_auth` 仓储方法全部按业务键（`userId + identityType`、`identityType + identifierHash` 等），`id` 不出持久化层。

## 备选方案

| 方案 | 优点 | 缺点 |
|---|---|---|
| 强制 user-service 内部端点改返回 Long | 调用方省一次回查 | 修改三个服务共享契约，影响面大 |
| 调用方解析 UUID → Long | 无契约变更 | 语义错误，必然运行期失败 |
| **幂等回查 getOrCreate（采用）** | 契约不变、语义正确、只读幂等 | 多一次内部调用（登录高频路径，可接受） |

## 后果

- ✅ 与既有 JWT/网关/表结构契约完全兼容
- ✅ 回查端点幂等，重试安全
- ⚠️ 新接入服务必须知晓"跨服务 user_id = 技术主键"约定（本 ADR 为权威依据）
- ⚠️ UUID 业务键与技术主键并存的双轨语义需在 04-Data-Model §一"标识使用原则"中持续维护

## 日期

2026-09-29

## 参考

- `002-dual-primary-key-and-money.md`（双主键策略）
- `project-docs/backend/04-Data-Model.md` §3.12（user_auth 表）
