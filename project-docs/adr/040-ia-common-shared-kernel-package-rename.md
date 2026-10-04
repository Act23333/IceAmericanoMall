# ADR-040: ia-common 共享内核包更名 —— domain 名不副实，拆分归位 api / security

## 背景

用户判定：`ia-common` 的 `org.icedamericanomall.common.domain` 包名不合理。该包仅含 4 个类，没有一个是 DDD 意义上的领域对象：

- `Result<T>`（统一响应壳）、`PageQuery`（分页入参）—— 属 **API/Web 契约**；
- `UserContext` / `UserInfo`（ThreadLocal 身份持有者）—— 属 **安全/会话** 关注点。

命名为 `domain` 与各服务的 `domain/` 领域层撞名，误导阅读者以为存在"共享领域模型"（共享内核的正确形态是零业务状态的契约与工具，见 ADR-030/034 收口口径）。

## 决策（2026-10-04，用户确认拆分方案）

按语义拆分归位，而非整体挪一个名字：

| 类 | 旧包 | 新包 | 归位理由 |
| :--- | :--- | :--- | :--- |
| `Result` | `common.domain` | `common.api` | 统一响应壳 = 对外 API 契约 |
| `PageQuery` | `common.domain` | `common.api` | 分页入参 = API 契约 |
| `UserContext` | `common.domain` | `common.security` | ThreadLocal 身份 = 安全关注点 |
| `UserInfo` | `common.domain` | `common.security` | 身份载体 = 安全关注点 |

`UserContext`/`UserInfo` 与既有 `security/UserContextAuthenticationFilter` 同包，安全链路内聚。

## 落地与影响面

- 全仓 82 处 `common.domain.*` FQN（import / javadoc / 内联）机械替换，零行为变化；
- `ia-common` 自身测试 `ResultTest` 随迁 `common/api`；
- 全量 18 模块 `clean install` + 测试回归 BUILD SUCCESS，测试数不变（911，0 失败）；
- 文档：`AGENTS.md` 无包路径引用需改；`05-API-Specification`/`04-Data-Model` 无涉（无契约/DB 变更）。

## 后果

- `org.icedamericanomall.common.domain` 包不复存在；后续共享内核新增类型按 `api`（对外契约）/ `security`（身份与密码学）/ `model`（值对象，如 `EncryptedIdentifier`）/ `event`（领域事件）归位，不再新增 `domain` 包。
- 各服务对 `Result`/`UserContext` 的引用路径变更属一次性迁移，无运行时影响。

## 日期

2026-10-04

## 参考

ADR-030/034（共享内核收口）、ADR-038（服务层四层 DDD 统一）
