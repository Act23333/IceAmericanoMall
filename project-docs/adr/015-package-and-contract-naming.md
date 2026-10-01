# ADR-015: 应用层包名 `app` 与契约对象 `XxxCO` 命名规范

## 背景

V5.2 分层收敛后，各服务的应用层包名统一为 `application`，ia-api 契约对象统一为 `XxxDTO`。两处命名在落地中暴露三个问题：

1. **包名冗长且与 Spring 生态高频词冲突**：`org.icedamericanomall.<svc>.application` 中的 `application` 与 `application.yml`、`spring.application.name`、`application/json` 等生态概念同名，在日志、FQCN 字符串、目录跳转中不断产生「同名歧义」——`grep application` 的结果永远混着配置与包名两类语义。
2. **契约对象与应用层对象同名**：ia-api（client 模块）的跨服务传输对象叫 `XxxDTO`，各服务应用层 `app/dto` 下的内部传输对象也叫 `XxxDTO`。同一个简单名 `SkuDTO` 在「跨服务契约」与「服务内应用层」两个完全不同的语义位置出现，靠 import 才能区分，违反统一语言的可读要求。
3. **VO 归属不清**：部分 VO 被下沉到应用层，与 ADR-003 确立的对象隔离原则出现漂移。

COLA 4.0 原生目录结构中应用层即命名为 `app`（`adapter / app / domain / infrastructure`），阿里、京东等大厂规范中「客户端对象」惯用 `CO`（Client Object）。

## 决策

1. **应用层包名为 `app`**：所有服务的应用层从 `org.icedamericanomall.<svc>.application` 改为 `org.icedamericanomall.<svc>.app`（COLA 4.0 原生命名，简短、可读、与配置文件名彻底解耦）。子结构不变：`app/command`、`app/dto`、`app/query`、`app/port`、`app/executor`。**资源文件 `application.yml` / `application-*.yml` 名称不变**（那是 Spring Boot 的配置文件约定，与包名无关）。
2. **契约对象为 `XxxCO`**：ia-api 的 client 契约对象从 `XxxDTO` 改为 `XxxCO`（Client Object），与应用层 `XxxDTO`（跨层传输对象）在命名上彻底区分。包路径 `org.icedamericanomall.dto` 保持不变。命名归属：
   - `XxxCO` —— 客户端对象，ia-api `client` 契约（Feign 出入参），实现方为服务端 Adapter。
   - `XxxDTO` —— 跨层/跨服务传输对象，位于服务内 `app/dto` 等应用层包。
   - `XxxVO` —— 视图对象，见第 3 条。
3. **VO 严格归属 Adapter response**：对外视图对象统一放在 `adapter/response`（依据 ADR-003 的 DO/DTO/VO 隔离原则）。VO 是「面向展示裁剪」的产物，必须紧贴出参边界；VO 不下沉到领域层。
4. **模板与项目的关系**：`doc/`（通用模板）保持**严格完整**的命名体系——`app` / `XxxCO` / `XxxDTO` / `XxxVO` 四者齐备，用于向新项目示范完整结构；本项目（`project-docs/` 约束下的实际实现）**可裁剪**——某服务若无跨服务契约需求，则不需要 CO；若无应用层编排，则不需要 `app` 包。裁剪的是「结构的存在与否」，不是「命名规则」。

## 备选方案

| 方案 | 优点 | 缺点 |
|---|---|---|
| 维持 `application` + `XxxDTO` | 零改动 | 包名与生态高频词持续歧义；契约对象与应用层对象同名，统一语言失效 |
| 应用层改 `application`→`app`，契约对象保留 `XxxDTO` | 解决包名歧义 | 第 2 类歧义仍在 |
| 契约对象改 `XxxCO`，包名保留 `application` | 解决对象同名 | 包名歧义仍在，且 `application` 段还会被 `grep` 反复误命中 |
| **两者同时改（采用）** | 歧义彻底消除；与 COLA 4.0 原生结构一致 | 一次性机械改动面较大（跨 12 个模块） |

COLA 4.0 与主流规范支撑：

| 命名 | 规范来源 | 定位 |
|---|---|---|
| `app` | COLA 4.0 标准分层 | 应用层包 |
| `CO` (Client Object) | 阿里/京东分层规范 | 客户端契约对象 |
| `DTO` | ADR-003 | 跨层传输对象 |
| `VO` | ADR-003 | 视图对象 |

## 后果

- ✅ 包名与配置文件、`application/json`、`spring.application.name` 等生态词彻底解耦，`grep` 与目录跳转不再歧义
- ✅ 契约对象（`XxxCO`，ia-api/client）与应用层对象（`XxxDTO`，`app/dto`）在同一文件中共存时自解释，无需依赖 import 上下文
- ✅ 应用层包名更短，与 COLA 4.0 原生结构对齐，降低新成员理解成本
- ✅ **线上行为不变**：本次为纯标识符改名。Jackson 默认按字段名序列化，Java 类名不进入 HTTP 报文；Feign 按声明路径（`@FeignClient(path=...)` + `@GetMapping/PostMapping`）匹配，与类名无关；Spring 组件扫描基于包扫描，包名同步修改后扫描结果等价
- ⚠️ 破坏性变更（对开发协作而言）：任何**未同步更新**的既有分支、本地未提交改动、IDE 断点/书签、外部脚本中写死的 `application` 包路径或 `XxxDTO` 类名将编译失败——需按本 ADR 补一次同名替换
- ⚠️ `application.yml` / `application-*.yml` 必须保持文件名不变，改名会直接导致服务无法读取配置（本 ADR 明确不改）
- ⚠️ CO/DTO/VO 命名细则以 java-architect 定义文件 §四「对象命名规范」为唯一权威，本 ADR 只记录决策动因与边界

## 日期

2026-10-01

## 参考

- `project-docs/adr/001-alibaba-lightweight-ddd.md`（分层架构）
- `project-docs/adr/003-object-isolation-do-dto-vo.md`（DO/DTO/VO 三层隔离 —— VO 归属依据）
- `project-docs/adr/009-package-namespace-unification.md`（包名空间归位）
- `project-docs/adr/014-doc-template-purification.md`（`doc/` = 通用模板、`project-docs/` = 项目产物 的定位依据）
- COLA 4.0 标准工程结构（`adapter / app / domain / infrastructure`）
- java-architect 定义文件 §四 对象命名规范、§三 分层架构与包命名
