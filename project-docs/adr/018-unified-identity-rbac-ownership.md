# ADR-018: 统一身份 + RBAC 归属授权域 + 用户档案域边界收敛

## 背景

边界重构（模块化 DDD 迁移）过程中发现：RBAC 五表（sys_role / sys_user_role / sys_role_permission / sys_user_permission / sys_permission）的角色/权限管理、权限缓存回填与失效，全部落在 user-service（档案域）；同时历史上存在"卖家注册 / 管理员账户"的分叉流程。由此产生三个边界问题：

1. 角色/权限的授予与回收（授权域职责）由档案域执行；
2. 下游服务（如商家知识库）与授权数据无直接关系却共处一域；
3. `user.role_type` 冗余列与五表派生并存，"商家/管理员账户"与"统一身份 + 角色"两种模型打架。

## 决策

采用大厂通行的**统一身份（Identity）+ 角色授权（RBAC）+ 档案分域**模型：

1. **统一身份**：一个自然人一个账户，登录协议/凭证存储（`user_auth`）只有一套；"买家/卖家/管理员"是账户上的角色，不是账户类型。不存在独立的"卖家注册"或"管理员注册"——注册即普通用户，卖家入驻 = 角色授予 + 商家档案创建。
2. **RBAC 归属授权域**：五表的角色/权限管理（CRUD、分配、用户角色设置）、权限缓存（`user:perms:{userId}` / `user:info:{userId}`）的写入与失效、登录成功后的缓存预热，全部归 authorization-service。
3. **档案分域**：C 端档案（昵称/头像/地址）归 user-service；商家档案（店铺名/类目）归 seller 表；未来内部运营管理员走独立企业 SSO（V2.0 演进，当前不建）。
4. **`user.role_type` 列退役**（V5.2 已代码停读写；本决策确认不回退），响应中的 roleType 兼容字段由五表角色编码派生。

### 迁移落地

- sys_* 五表数据不变（共享库，逻辑所有权转移）；auth 新增 `domain/repository/RoleRepository` + `infrastructure/persistence/rbac/*` + `app/rbac/{RoleManageManager, PermissionQueryManager, PermissionCacheEvictor, SecurityContextManager}` + `adapter/admin/{RoleController, PermissionController}`。
- 网关权限缓存契约不变：`user:perms:{userId}`（SET，含 ROLE_ 角色）仍由 auth 写入、网关读取；**登录/注册成功即预热**（消除原"授权变更后到下次 /info 之间角色为空"的窗口）。
- 新增 ia-api 稳定契约 `AuthContextClient`：`GET /internal/auth/context/{userId}`（角色+权限，回填缓存）、`GET /internal/auth/roles?userIds=`（批量派生）、`PUT /internal/auth/user/{id}/role`（用户角色设置委托）。user-service 的 `/info` 与管理后台角色派生改走该契约；`AdminUserController.setUserRole` 保留原路径、内部委托 auth。
- 默认角色兜底：`SecurityContextManager.loadContext` 读时兜底（无角色用户授予 ROLE_USER，幂等），取代原 `UserRegistrar` 建档时的授予。

## 备选方案

| 方案 | 优点 | 缺点 |
| :--- | :--- | :--- |
| 独立卖家/管理员账户体系（每角色一套用户表） | 隔离彻底 | 一个自然人多角色需多账户；凭证/MFA/风控重复建设；角色可变（入驻/回收）无法表达 |
| RBAC 留在 user-service，auth 只发 token | 迁移成本低 | 档案域持有授权数据，边界继续腐化；auth 发的 token 与权限来源分离 |
| 网关改为 Feign 实时回调 user 查权限（不做缓存） | 实时性最强 | Reactive 网关耦合阻塞调用；每请求一跳；与现有 Redis 缓存设计冲突 |
| **统一身份 + RBAC 归 auth + 档案分域（采纳）** | 与 Cognito/Auth0/京东/淘宝模式一致；档案与授权各自演进 | 需要一次跨服务迁移；user:info JSON 中的昵称字段暂为空（无消费方） |

## 后果

正面：授权数据与凭证同域（auth），变更→缓存失效→网关生效链路全部在一个服务内闭环；user-service 纯档案化；角色可自由授予/回收（商家兼管理员可行）；新增内部运营 SSO 时 C 端模型无需改动。

负面 / 风险：`user:info:{userId}` JSON 的 username 暂为空字符串（当前无消费方，昵称由前端按 userId 渲染）；授权变更后缓存靠主动失效 + TTL 收敛，撤销权限最长 30 分钟残留（迁移前既有语义，未变更）；`user.balance` / `user.role_type` 等退役列保留在表中（数据备份，勿再读写）。

## 日期

2026-10-02

## 参考

- ADR-016（user-service DDD 与短信发码迁移）、ADR-017（认证边界收敛）
- 项目边界重构系列：站内信→notification、积分→marketing、余额→wallet-service、签到→marketing、聊天/知识库→ai-service
