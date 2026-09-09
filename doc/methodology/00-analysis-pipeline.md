# 00 — 分析流程总览：从需求到代码的完整管线

## 一、目的与产出

**本阶段解决什么问题**：建立全局视角，理解从「用户一句话需求」到「可执行代码」的完整转化过程。

**输入**：用户原始需求（可能是模糊的一句话，如「我想做一个电商平台」）

**输出**：一份完整的项目文档体系，足以驱动 TDD/SDD/DDD/BDD/SPC 编码

**本管线参考标准**：

- IEEE 830-1998 — Software Requirements Specification
- arc42 — Software Architecture Documentation
- OpenAPI 3.x — API Specification
- DDD Patterns (Eric Evans, Vaughn Vernon)
- UML 2.5 — Unified Modeling Language
- Microsoft STRIDE — Threat Modeling
- ISTQB — Test Strategy

---

## 二、完整管线

```
                          用户原始需求（一句话/一段话）
                               │
                               ▼
┌──────────────────────────────────────────────────────────────┐
│  Phase 1: 需求分析  ───  BDD 基础                             │
│  ─────────────────────────────────────────────────           │
│  输入: 用户原始需求                                             │
│  文档: methodology/01-requirements-elicitation.md             │
│  产出: project-docs/01-PRD.md                                 │
│        ├── 用户角色 (Actor)                                    │
│        ├── 功能列表 (MoSCoW 优先级)                             │
│        ├── 核心流程 BDD 场景 (Given-When-Then)                 │
│        └── 非功能性需求 (量化指标: TPS/延迟/可用率)              │
│  标准: IEEE 830 SRS / Modern PRD Template                     │
│  DoD : BDD 场景覆盖所有 Must 功能 + NFR 量化 + 评审通过         │
└───────────────────────────┬──────────────────────────────────┘
                            │
                            ▼
┌──────────────────────────────────────────────────────────────┐
│  Phase 2: 事件风暴  ───  需求→领域的桥梁                       │
│  ─────────────────────────────────────────────────           │
│  输入: PRD (功能列表 + BDD 场景)                               │
│  文档: methodology/02-event-storming.md                       │
│  产出: 事件风暴工作坊产物                                       │
│        ├── 领域事件列表 (橙贴纸，时间线排列)                     │
│        ├── 命令列表 (蓝贴纸)                                    │
│        ├── 聚合候选 (黄贴纸)                                    │
│        ├── 限界上下文泳道                                       │
│        ├── 读模型 (绿贴纸) + 外部系统 (粉贴纸)                   │
│        └── 热点清单 (紫贴纸 → 待澄清问题)                       │
│  标准: Event Storming (Alberto Brandolini)                    │
│  DoD : 事件覆盖全流程 + 热点有结论 + 泳道与架构一致              │
└───────────────────────────┬──────────────────────────────────┘
                            │
                            ▼
┌──────────────────────────────────────────────────────────────┐
│  Phase 3: 用例建模  ───  功能结构化                             │
│  ─────────────────────────────────────────────────           │
│  输入: PRD (角色 + 功能) + 事件风暴 (命令)                      │
│  文档: methodology/03-use-case-modeling.md                    │
│  产出: 用例图 (UML Use Case Diagram)                             │
│        ├── Actor 识别（用户/商家/管理员/第三方系统）               │
│        ├── 用例关系（包含 <<include>> / 扩展 <<extend>>）         │
│        └── 用例描述（主流程 + 备选流程 + 前置/后置条件）           │
│  标准: UML 2.5 Use Case Diagram                               │
│  DoD : 每个 MVP 功能有对应用例 + 核心用例有完整描述               │
└───────────────────────────┬──────────────────────────────────┘
                            │
                            ▼
┌──────────────────────────────────────────────────────────────┐
│  Phase 4: 架构设计  ───  DDD 战略设计                          │
│  ─────────────────────────────────────────────────           │
│  输入: PRD + 用例图 + 事件风暴 (限界上下文泳道)                  │
│  文档: methodology/04-microservice-architecture.md            │
│  产出: project-docs/02-Architecture.md + adr/                 │
│        ├── 架构风格 (微服务/单体/事件驱动)                        │
│        ├── 技术栈决策 (每条关键决策一条 ADR)                      │
│        ├── 服务分解 → DDD 限界上下文映射                          │
│        ├── 分层架构 (Controller→Manager→Service→Mapper)         │
│        ├── 安全架构 (认证/授权/密钥管理)                          │
│        └── 部署架构 (Docker→K8s 演进路线)                        │
│  标准: arc42 / C4 Model                                       │
│  DoD : 每条关键决策有 ADR + 限界上下文与事件风暴泳道一致           │
└───────────────────────────┬──────────────────────────────────┘
                            │
                            ▼
┌──────────────────────────────────────────────────────────────┐
│  Phase 5: 领域建模  ───  DDD 战术设计                          │
│  ─────────────────────────────────────────────────           │
│  输入: Architecture Document (限界上下文) + 事件风暴 (聚合候选)   │
│  文档: methodology/05-domain-modeling.md                      │
│  产出: project-docs/03-Domain-Model.md                        │
│        ├── 限界上下文 + Context Map                            │
│        ├── 实体 (Entity) + 属性                                │
│        ├── 值对象 (Value Object)                               │
│        ├── 聚合 (Aggregate) + 聚合根                            │
│        ├── 领域服务 (Domain Service)                           │
│        ├── 领域事件 (Domain Event)                              │
│        └── 通用语言词汇表 (Ubiquitous Language)                 │
│  标准: DDD Patterns (Eric Evans)                              │
│  图表: UML 类图 — 概念模型 (Domain Class Diagram)                │
│  DoD : 聚合与事件风暴一致 + 通用语言完整 + 类图与文字一致          │
└───────────────────────────┬──────────────────────────────────┘
                            │
                            ▼
┌──────────────────────────────────────────────────────────────┐
│  Phase 6: 接口设计  ───  SDD 核心                             │
│  ─────────────────────────────────────────────────           │
│  输入: Domain Model (聚合根) + PRD (功能列表) + Architecture    │
│  文档: methodology/06-api-design.md                           │
│  产出: project-docs/05-API-Specification.md                   │
│        ├── API 设计约定 (RESTful/HTTP方法/URL规范)              │
│        ├── 统一响应格式 (Result<T> + ErrorCode)                │
│        ├── 端点目录 (按服务列出所有 API)                         │
│        ├── Request/Response Schema (精确到字段类型)             │
│        ├── 认证流程 (登录/注册/Token 刷新)                      │
│        └── BDD 风格接口契约                                    │
│  标准: OpenAPI 3.x / JSON Schema                              │
│  DoD : 每个 PRD 功能有对应端点 + 核心 API 有 BDD 契约            │
└───────────────────────────┬──────────────────────────────────┘
                            │
                            ▼
┌──────────────────────────────────────────────────────────────┐
│  Phase 7: 数据建模                                            │
│  ─────────────────────────────────────────────────           │
│  输入: Domain Model (实体 + 属性 + 关系) + API Spec (VO参考)    │
│  文档: methodology/07-data-modeling.md                        │
│  产出: project-docs/04-Data-Model.md                          │
│        ├── 数据库设计约定 (主键策略/金额/快照/字符集)              │
│        ├── ER 图 → 表结构映射                                   │
│        ├── 表定义 (字段/类型/约束/索引)                           │
│        ├── 外键关系 & 级联策略                                   │
│        └── 数据隔离规则 (DO / DTO / VO)                         │
│  标准: IDEF1X / Information Engineering (IE) Notation         │
│  图表: ER 图 (Entity-Relationship Diagram)                      │
│  DoD : 表结构与领域实体一一对应 + ER图与领域模型一致               │
└───────────────────────────┬──────────────────────────────────┘
                            │
                            ▼
┌──────────────────────────────────────────────────────────────┐
│  Phase 8: 行为建模  ───  验证 API 设计                         │
│  ─────────────────────────────────────────────────           │
│  输入: API Spec (端点) + Domain Model (聚合)                   │
│  文档: methodology/08-behavior-modeling.md                    │
│  产出: 行为模型 (嵌入 project-docs/05-API-Specification.md)     │
│        ├── 时序图 (Sequence Diagram) — 验证 API 调用链          │
│        ├── 状态机 (State Machine) — 订单/支付状态流转            │
│        └── 并发场景标注 (锁/幂等/事务边界)                       │
│  标准: UML 2.5                                                │
│  图表: 时序图 + 状态机 (Sequence + State Machine Diagram)        │
│  DoD : 时序图覆盖正常+异常路径 + 状态机无死角 + 并发标注明确      │
└───────────────────────────┬──────────────────────────────────┘
                            │
                            ▼
┌──────────────────────────────────────────────────────────────┐
│  Phase 9: 安全建模  ───  威胁分析与对策                         │
│  ─────────────────────────────────────────────────           │
│  输入: Architecture + API Spec + Data Model                   │
│  文档: methodology/09-security-modeling.md                    │
│  产出: project-docs/12-Security-Model.md                      │
│        ├── 数据流图 (DFD) + 信任边界标注                        │
│        ├── STRIDE 威胁清单 (按六类逐一分析)                      │
│        ├── 安全控制措施表 (威胁 → 对策 → 实现)                   │
│        └── OWASP Top 10 适用性评估                             │
│  标准: Microsoft STRIDE + OWASP ASVS                          │
│  DoD : 所有高危威胁有对策 + 支付安全/越权防护覆盖                 │
└───────────────────────────┬──────────────────────────────────┘
                            │
                            ▼
┌──────────────────────────────────────────────────────────────┐
│  Phase 10: 测试策略  ───  TDD 执行纲领                         │
│  ─────────────────────────────────────────────────           │
│  输入: PRD (BDD场景) + API Spec + Security Model              │
│  文档: methodology/10-test-strategy.md                        │
│  产出: project-docs/11-Test-Strategy.md                       │
│        ├── 测试金字塔定义 (Unit 70% / API 20% / E2E 5%)        │
│        ├── BDD → 测试用例映射规则                               │
│        ├── 覆盖率目标 (行/分支/方法/分层)                        │
│        ├── 性能测试基线 (TPS/延迟/并发)                          │
│        └── 安全测试检查点                                       │
│  标准: ISTQB Test Strategy                                    │
│  DoD : 每个 BDD 场景有对应测试 + 覆盖率目标量化 + 性能基线可测    │
└───────────────────────────┬──────────────────────────────────┘
                            │
                            ▼
┌──────────────────────────────────────────────────────────────┐
│  Phase 11: 开发执行  ───  TDD + SPC                           │
│  ─────────────────────────────────────────────────           │
│  输入: 以上所有文档                                             │
│  产出: Working Code + Tests                                   │
│  流程:                                                        │
│        BDD 场景 → 写失败测试 → 最小实现 → 重构                  │
│        → 对照 09-constraints 查反模式                          │
│        → 对照 10-review 自检                                   │
│        → 对照 11-Test-Strategy 验证覆盖率                       │
│        → 对照 12-Security-Model 查安全漏洞                      │
│        → PR → Code Review → 合并                              │
│                                                               │
│  支撑文档:                                                     │
│        project-docs/06-Workflow.md    — 开发流程 + 编码规范     │
│        project-docs/07-Tasks.md       — 任务分解 + 验收场景     │
│        project-docs/08-agents.md      — AI 编码指令            │
│        project-docs/09-constraints.md — 技术约束 + 反模式       │
│        project-docs/10-review.md      — 代码评审标准            │
└──────────────────────────────────────────────────────────────┘
```

---

## 三、各阶段与开发方法论的精确对应

| Phase  | 方法论 | 关键活动 | 产出文档 |
| ------ | ------ | -------- | -------- |
| 1 需求分析 | **BDD** | 编写 Given-When-Then 验收场景 | PRD (含 BDD 场景) |
| 2 事件风暴 | **DDD 预备** | 发现领域事件、命令、聚合候选 | 事件清单 + 聚合候选 |
| 3 用例建模 | — | Actor/用例识别、用例描述 | 用例图 |
| 4 架构设计 | **DDD 战略** | 限界上下文划分、Context Map、ADR | Architecture Doc + ADR |
| 5 领域建模 | **DDD 战术** | 实体/值对象/聚合/领域服务 | Domain Model |
| 6 接口设计 | **SDD** | 定义精确的 API 契约 | API Specification |
| 7 数据建模 | — | ER 图→表结构映射 | Data Model |
| 8 行为建模 | — | 时序图/状态机验证 API 设计 | 行为模型 (嵌入 API Spec) |
| 9 安全建模 | — | STRIDE 威胁分析 + OWASP 评估 | Security Threat Model |
| 10 测试策略 | **TDD 预备** | 测试金字塔 + BDD→测试映射 | Test Strategy |
| 11 开发执行 | **TDD+SPC** | 测试先行→实现→Review→合并 | Working Code + Tests |

---

## 四、文档之间的依赖关系

```
PRD ◄── 事件风暴 ──→ 用例图
  │         │            │
  │         └──→ 聚合候选  │
  │              ↓        │
  │         Architecture Doc ◄── 用例图
  │              │
  │              ▼
  │         Domain Model
  │            │
  │     ┌──────┼──────┐
  │     ▼      ▼      ▼
  │  API Spec  │  Data Model
  │     │      │      │
  │     └──────┼──────┘
  │           ▼
  │      Behavior Model (验证 API)
  │           │
  │           ▼
  └──→ Security Model ◄── Architecture + API + Data
           │
           ▼
      Test Strategy
           │
           ▼
      Task Breakdown ◄── Workflow
           │
           ▼
      Working Code (TDD)
```

**变更影响链**：

- 改了 PRD 中的功能 → 需检查事件风暴的事件、API Spec 端点、Test Strategy 的 BDD 映射
- 改了 Domain Model 中的实体 → 需检查 Data Model 表结构、API Spec 的 Schema
- 改了 API Spec 中的端点 → 需检查行为建模时序图、Test Strategy 的映射
- 发现安全威胁 → 可能影响 Architecture (网关规则)、API (越权校验)、Data (脱敏)

---

## 五、需求追溯矩阵 (RTM — Requirements Traceability Matrix)

RTM 确保从需求到代码的每一条链路都可追溯。核心格式:

| ID | BDD 场景 (PRD) | 用例 | API 端点 | 测试用例 | 代码位置 | 状态 |
|----|---------------|------|---------|---------|---------|------|
| REQ-001 | 用户成功注册 | UC-REG-01 | POST /api/auth/register | shouldReturnToken_whenNewPhone | AuthController.register() | ✅ |
| REQ-002 | 已注册手机号被拒绝 | UC-REG-02 | POST /api/auth/register | shouldReturnError_whenPhoneExists | AuthService.register() | ✅ |
| REQ-003 | 成功登录获取Token | UC-LOGIN-01 | POST /api/auth/login | shouldReturnToken_whenValidCredentials | AuthController.login() | ✅ |
| REQ-004 | 浏览商品列表 | UC-ITEM-01 | GET /api/item/page | shouldReturnPagedProducts | ItemController.page() | 🔵 |
| REQ-005 | 创建订单 | UC-ORDER-01 | POST /api/trade/order | shouldCreateOrder_whenStockSufficient | TradeController.create() | 🔵 |
| REQ-006 | 库存不足拒绝订单 | UC-ORDER-02 | POST /api/trade/order | shouldReturnError_whenStockInsufficient | TradeManager.createOrder() | 🔵 |
| ... | ... | ... | ... | ... | ... | ... |

**填写规则**:

- BDD 场景来自 `01-PRD.md` 中的 Given-When-Then
- 用例来自 `03-use-case-modeling.md` 或用例图
- API 端点来自 `05-API-Specification.md`
- 测试用例来自 `11-Test-Strategy.md` 的 BDD → 测试映射
- 代码位置精确到类名.方法名()
- 状态: ✅ 已实现 | 🔵 开发中 | ⚪ 未开始

**追溯检查**:

- **前向追溯**: PRD 每个 Must 功能 → API 端点 → 测试 → 代码 (确保不遗漏)
- **后向追溯**: 每行代码 → 测试 → API → PRD (确保无多余功能)
- **安全追溯**: 每条 STRIDE 威胁 → 控制措施 → 对应代码 (来自 `12-Security-Model.md`)

---

## 六、常见错误与检查清单

### 该阶段容易犯的错误

1. **跳过事件风暴直接建模**：PRD → 领域模型没有中间桥梁 → 遗漏领域事件和聚合
2. **跳过架构直接写代码**：没有 Architecture Document 就开始建服务 → 服务拆分不合理
3. **领域模型当成数据模型**：在领域建模阶段就加主键/外键/索引 → 技术与业务耦合
4. **PRD 写得太虚**：「高可用」「易扩展」没有量化 → 无法验收
5. **API 设计后补**：代码写完了再补文档 → 文档与实现不一致
6. **不做安全建模**：等上线后被渗透测试发现问题 → 补救成本高
7. **各阶段产出不连通**：PRD 的角色和用例图的 Actor 对不上 → 需求丢失

### 完成全部管线后的自检清单

- [ ] PRD 覆盖了所有用户角色（游客/用户/商家/管理员/外部系统）
- [ ] 每条核心用户故事都有对应的 BDD 场景（至少 1 正常 + 2 异常）
- [ ] 事件风暴的事件覆盖了端到端全流程
- [ ] Architecture Document 的每个服务都有明确的限界上下文归属
- [ ] Domain Model 的每个实体都能在 Data Model 中找到对应表
- [ ] API Spec 的每个端点都能追溯到 PRD 中的功能
- [ ] 行为模型的时序图验证了 API 设计的正确性
- [ ] STRIDE 威胁清单的高危项都有对应控制措施
- [ ] Test Strategy 的 BDD → 测试映射完整
- [ ] Tasks 的每个任务都有 BDD 验收标准
- [ ] 所有 ADR 记录了关键技术决策的 WHY
- [ ] RTM 矩阵覆盖了所有 Must 功能的前向+后向追溯
- [ ] 图表（用例图/ER图/类图/时序图）与文档文字一致
