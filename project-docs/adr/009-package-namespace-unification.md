# ADR-009: 全模块包名归位（服务段拆分 + ia-common 统一 + scan/imports 二选一）

## 背景

V5.1 迁移期间出现三类包名问题：① 多个服务的 Java 代码直接放在共享根包 `org.icedamericanomall` 下（未按服务分段），与 ia-api / ia-integration 的共享包同处一个扫描空间，曾引发 `org.icedAmericanomall.auth.config`（ia-api）与 authorization-service 基础包的拆分包冲突，部分模块还残留了误插入的 `auth/` 占位段（内装的是 ItemManager/PayManager 等非认证类）；② ia-common 历史包名为 `org.noLazy.common`，与全仓库 `org.icedamericanomall` 不一致；③ ia-integration 的 `AutoConfiguration.imports` 存在大写 A 的错误 FQCN 与指向已删除类的死条目（Windows 开发态大小写不敏感可用，jar/Docker 形态启动失败）。

## 决策（V5.2，纯机械改名，零业务内容改动）

1. **服务段拆分**：所有服务实现归位到 `org.icedamericanomall.<service>`；误插入的 `auth/` 占位段替换为真实服务段（不是嵌套保留）。
2. **ia-common 统一**：`org.noLazy.common` → `org.icedamericanomall.common`，全仓 194 个文件 import 同步。
3. **ia-api 保持根包子包**（`client/config/dto/fallback`），不迁移到 `.api.*`（用户决策，避免最大规模改名）。
4. **Bean 注册二选一**：服务启动类显式 `scanBasePackages = {本服务段, org.icedamericanomall.fallback, 用到的 integration 子包}`；ia-integration 客户端要么走 imports 文件、要么走扫描，同组不双注册。实测（Spring Boot 3.5.4）：扫描与 imports 同类共存不报错（scan 胜出、imports 注册名不同），二选一是架构整洁性要求而非启动安全要求。
5. `artifactId` / 父 pom `<modules>` 不变（本次只改 Java 包名，不改 Maven 坐标）；`groupId` 仍为 `org.nolazy`（坐标与包名对齐属独立决策，未做）。

## 备选方案

| 方案 | 优点 | 缺点 |
|---|---|---|
| 维持现状（根包混居） | 零迁移成本 | 拆分包冲突重现、扫描面失控、语义混乱 |
| 各服务独立顶级包（org.user.xxx） | 隔离彻底 | 与 Maven groupId/生态惯例不符 |
| **服务段 + 共享段统一（采用）** | 冲突根除、扫描面可控、命名自解释 | 一次性大规模机械改名（本轮已付） |

## 后果

- ✅ `org.noLazy` / 大写 `org.icedAmericanoMall` 在 Java 源码与资源文件中清零
- ✅ ia-integration imports 13 条逐条核对真实类存在；ia-common imports 14 条同步改写
- ⚠️ 服务扫描面收窄是行为变化：漏配 `scanBasePackages` 会丢 fallback/第三方 Bean（已按服务逐一配置，见各启动类 Javadoc）
- ⚠️ trade-service 测试基线失败原因因此前移（暴露出与包名无关的既有构造器 arity 缺陷），失败集合不变
- ⚠️ 未做实机启动验证（中间件未起）；建议合并前 smoke 启动 user / pay / authorization 三服务

## 日期

2026-09-29

## 参考

- `project-docs/collaboration/09-constraints.md` §2.3（包名与 Bean 注册约束）
- `ADR-005`（领域端口化，scanBasePackages 先例 authorization-service）
