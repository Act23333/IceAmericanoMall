# ADR-032: 操作审计双管道 —— JSON 审计日志（全服务）+ operation_log 落库（仅 user 域）

## 背景

V5.2/5.3 的「死代码清理」删除了零调用方的 `@OperationLog` + `OperationLogAspect`（见 ADR 记录与
`ia-common` 的清理说明）。清理本身正确——**没有使用方的切面就是死代码**——但它留下一个业务缺口：

1. **`operation_log` 表（user-service 所有）没有任何写入方**，而 `AdminLogController`
   （`GET /api/admin/log`）仍在读该表 → 管理端查询功能对**新产生的**操作永远查不到记录，
   只能看到历史数据；
2. **全平台管理端写操作没有任何审计留痕**：角色/权限变更、提现审核、售后审核、优惠券上下线、
   首页装修、用户禁用/改角色等敏感写操作，事后无法回答「谁在什么时候改了什么」。

大厂实践是**审计双轨**，而不是把所有审计都塞进一张业务库表：

- **结构化日志管道**：审计事件以 JSON 写入日志流（ELK / Logstash 采集），成本低、覆盖面全、
  可长期留存与全文检索，适合「全量管理操作」；
- **关键落库**：少量关键操作（尤其需要管理端页面查询、或需要随业务事务原子提交的）落业务库表。

两者不是替代关系：只落库则覆盖不全且把日志写进业务库（放大写压力、拖业务事务）；只记日志则
`AdminLogController` 的查询能力（既有产品功能）直接失效。

同时必须正面处理两个约束：

- **切面不得耦合持久化**：若 `OperationLogAspect` 注入 Mapper，共享内核 `ia-common` 就会被
  反向耦合到各服务的持久化实现；且「注解一贴就写库」会让审计写入成为业务事务的隐形参与者。
- **PII 安全**：审计日志天然会被采集、长期留存、广泛检索，**默认记录参数值等于把手机号、密文、
  令牌铺到 ELK**。默认必须是「不记值」。

## 决策

**采用审计双管道：管道一（全服务）JSON 日志；管道二（仅 user 域）显式落库。二者由不同代码路径承载，
注解只负责日志，不负责落库。**

### 管道一：`@OperationLog` + 切面 → 单行 JSON 审计日志（全服务统一，不落库）

1. **注解** `org.icedamericanomall.common.annotation.OperationLog`（`ia-common`）：
   `module()` / `action()` / `description()` / `recordParams()`，**全部有默认值**
   （`""` / `""` / `""` / `false`）。调用方一律用常量填写 `module`/`action`（语义化，
   如 `module="RBAC"`, `action="CREATE_ROLE"`），避免魔法值漂移。
2. **切面** `org.icedamericanomall.common.aspect.OperationLogAspect`：
   `@Around("@annotation(operationLog)")`，成功与失败各输出**一行 JSON**到专用 logger **`AUDIT`**：

   | 字段 | 语义 |
   | :--- | :--- |
   | `timestamp` | ISO-8601 带毫秒与偏移（ELK 可直接识别为 date） |
   | `module` / `action` | 取自注解 |
   | `method` | `类名#方法名`（如 `RoleController#createRole`） |
   | `user` | `UserContext.getUserId()`；取不到写字符串 `anonymous` |
   | `success` | 布尔 |
   | `costMs` | 切面耗时（毫秒） |
   | `exception` | **仅失败路径**，仅异常类全名，**不含 message**（防内部细节外泄） |
   | `params` | **仅 `recordParams=true`**，参数类型 + id 类字段摘要，截断至 200 字符 |

3. **零业务依赖（硬约束）**：切面不注入任何服务 Bean（无 Mapper/Redis/Feign），
   `userId` 通过静态 `UserContext` 读取，故可被全平台无条件加载。
4. **切面自身异常必须降级**：审计输出失败只记一条 WARN（走切面自身的 class logger，
   不污染 `AUDIT` 的 JSON 流），**绝不改变业务返回值或异常语义**。
5. **顺序**：`@Order(Ordered.LOWEST_PRECEDENCE)`，最内层，不参与也不改变事务切面语义。
6. **装配**：登记在 `ia-common` 的 `AutoConfiguration.imports`（与 `RateLimitAspect` 同机制），
   全平台服务自动生效，无需各服务改动。
7. **采集**：JSON 行经 root logger 落到各服务既有日志文件/控制台，由既有 ELK 管道
   （`docker/logstash/logstash.conf`，filebeat 采集 `logs/<app>/app.log` 或 TCP `json_lines`）
   采集入 ES；`logger=AUDIT` 可直接作为审计事件的过滤条件。
   **未新增独立 appender / 独立文件**（保持与既有采集链路一致，避免出现「新文件无人采集」的假管道）。

### 管道二：user 域管理操作显式写 `operation_log`（仅本域，不经 AOP）

1. `user-service` 应用层 `OperationLogManager`（`app/oplog`）提供
   `record(userId, module, action, content)`，`@Transactional(rollbackFor = Exception.class)`；
   列映射：

   | 表列 | 来源 |
   | :--- | :--- |
   | `user_id` | 入参 `userId` |
   | `username` / `ip` | 当前 `UserContext`（取不到留空） |
   | `action` | `module + ":" + action`（**表无 module 列**，模块限定词并入 action） |
   | `detail` | 入参 `content`（动作 + 目标标识摘要，**不含敏感值**） |
   | `create_time` | 列默认值 `CURRENT_TIMESTAMP` |
   | `target` | **未使用**（见下方「已知偏差」） |

2. **调用点（显式，仅本地状态变更成功后）**：`UserAdminManager.updateStatus`、`UserAdminManager.setUserRole`。
3. **事务语义**：
   - `updateStatus`（纯本地库写）加 `@Transactional(rollbackFor = Exception.class)`，
     状态变更与审计留痕**同事务生死**——不允许「改了状态却没留痕」；
   - `setUserRole`（方法内含 Feign 调用）**不得加事务**（硬约束：严禁事务内调 Feign），
     故审计自成独立事务，角色变更已完成后审计失败不回滚。
4. **不做「注解 → 落库」的桥接**：`@OperationLog` 的处理只写日志。需要落库的操作由业务代码
   **显式**调用 `record`，让「写台账」成为显式业务意图而非注解的副作用。

### 覆盖范围声明（重要，避免误读）

| 能力 | 覆盖范围 | 载体 |
| :--- | :--- | :--- |
| `AUDIT` JSON 审计日志 | **全平台所有服务**的管理端写操作（已铺设：authorization / trade / item / marketing / user 域） | ELK 日志流 |
| `operation_log` 表 + `AdminLogController` | **仅 user 域管理操作**（用户启用/禁用、角色授予） | user-service 本地表 |

即：`operation_log` 表**不再**是全平台审计台账，其管理端查询页只反映 user 域管理操作；
查询「全平台谁改了什么」请走 ELK（`logger=AUDIT`）。命名空间上二者互不冒充。

### 已知偏差（明确记录）

1. **`operation_log.target` 列未被写入方使用**：表设计的三列语义是 `action`（操作类型）/
   `target`（操作目标）/`detail`（详细信息），但本决策的写入签名 `record(userId, module, action, content)`
   只有「动作 + 摘要」，无独立「目标」入参，且 module 无对应列。当前处理是：模块限定词并入 `action`，
   目标标识并入 `detail`，`target` 留空（历史行仍有值）。
   收敛路径（需 DDL，超出本次改动范围）：迁移新增 `module` 列并明确 `target` 语义，
   或在下一次表结构评审时废弃 `target` 列。
2. **`recordParams` 的用法规则**：含 `@RequestBody` 请求体的端点一律保持默认（不记值）；
   仅「路径/查询参数且只含标识/状态」的端点开启，产出形如 `id=1, Integer` / `couponId=CPN-123`。

## 备选方案

| 方案 | 优点 | 缺点 |
| :--- | :--- | :--- |
| 维持现状（表只读、无写入方） | 零改动 | `AdminLogController` 对新操作永远查不到；全平台管理操作零审计 |
| 只做落库（恢复切面 + 切面直写 `operation_log`） | 复用既有表与查询页 | 切面须注入各服务的 Mapper → `ia-common` 反向耦合持久化；审计写入成为业务事务隐形参与者；跨服务写同一张 user 域表破坏数据归属 |
| 只做日志（删表与查询端点） | 最简、覆盖面全 | 删掉既有产品功能与历史数据查询；管理端无法自查 |
| 全量落库（各服务自己建审计表） | 查询最方便 | 十几张审计表、写放大、与业务事务耦合；日志管道仍需建设，重复投入 |
| **双管道（采纳）** | 覆盖面（日志）与可查询/可原子（落库）各得其所；切面零业务依赖；PII 默认安全 | 两处留痕口径需文档化对齐（本 ADR 即为此）；`operation_log` 覆盖范围收窄到 user 域 |
| 注解可选 `persist=true` 由切面落库 | 一处两用，表面简洁 | 一旦支持，切面必须持有 Mapper/事务上下文，等于把「共享内核耦合持久化」制度化；注解副作用不可见，违背「显式优于隐式」 |

## 后果

**正面**

- `operation_log` 重新有写入方，`AdminLogController` 对新操作恢复可用（user 域）；
- 全平台管理端写操作获得统一、可检索、可长期留存的审计留痕，且**零业务依赖、零服务改造**（仅加注解）；
- **PII 安全默认**：不显式开启就绝不记录参数值；开启也只出「类型 + id 摘要」且截断；
- 失败路径只记异常类名，兼顾可定位性与信息泄露防范；
- 审计输出异常不影响业务（降级为 WARN），审计不会成为新的故障源；
- `updateStatus` 的「状态 + 台账」同事务，杜绝本地操作的黑账。

**负面 / 风险**

- **两处口径需人工对齐**：同一 user 域操作会同时出现在 ELK 与 `operation_log`，字段命名不同
  （`module`/`action` vs `action`=`MODULE:ACTION`/`detail`），跨源关联需靠 `user` + 时间 + 动作语义；
- **注解不自证被使用**：切面本身无法校验「是否有人标注」，仍需评审时检查（本次已落实 5 处服务的使用方）；
- **`setUserRole` 审计非原子**：Feign 成功后审计写失败只留 500/WARN，无补偿（该操作本身也无法与
  中台变更同事务）；
- **本地审计失败会连带业务失败**：`updateStatus` 同事务语义意味着 `operation_log` 写入异常会让
  「禁用用户」整体失败（这是刻意的审计完整性取舍，但需运维知晓该表不可用时管理端会不可用）；
- **依赖 ELK 采集链路**：若 filebeat/logstash 未运行，管道一只写本地文件，不构成「可检索」；
  且 `AUDIT` 目前继承 root appender，若未来改为独立文件必须同步 filebeat 输入，否则退化为假管道。

## 日期

2026-10-03

## 参考

- `Implementation/back-end/ia-common/src/main/java/org/icedamericanomall/common/annotation/OperationLog.java`
- `Implementation/back-end/ia-common/src/main/java/org/icedamericanomall/common/aspect/OperationLogAspect.java`
- `Implementation/back-end/ia-common/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`（`OperationLogAspect` 登记）
- `Implementation/back-end/ia-common/src/test/java/org/icedamericanomall/common/aspect/OperationLogAspectTest.java`
- `Implementation/back-end/user-service/src/main/java/org/icedamericanomall/user/app/oplog/OperationLogManager.java`（管道二写侧）
- `Implementation/back-end/user-service/src/main/java/org/icedamericanomall/user/app/oplog/OperationAuditConstants.java`（模块/动作常量）
- `Implementation/back-end/user-service/src/main/java/org/icedamericanomall/user/app/user/UserAdminManager.java`（两个显式落库调用点）
- `Implementation/back-end/user-service/src/main/java/org/icedamericanomall/user/adapter/web/admin/AdminLogController.java`（覆盖范围声明中的查询端点）
- `Implementation/back-end/database/Initialize.sql`（`operation_log` 表结构）、`database/migrations/V1.2.1__add_operation_log.sql`
- `Implementation/back-end/docker/logstash/logstash.conf`、`ia-common/src/main/resources/logback-spring.xml`（日志采集链路）
- ADR-023（入参对象化：本次回调类端点不适用）、ADR-018（user 域管理职责边界）
