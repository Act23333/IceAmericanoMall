# 04 — 微服务架构方法 (DDD 战略设计)

## 一、目的与产出

**本阶段解决什么问题**：确定系统的宏观架构——技术栈选型、服务拆分策略、分层架构、部署方案。

**输入**：PRD（功能列表 + NFR） + 用例图

**输出**：
- Architecture Document (SAD)
- Architecture Decision Records (ADR)
- 技术栈清单（含版本）
- 服务分解图
- 分层架构规范

**产物文档类型**：Software Architecture Document (参考 arc42 / C4 Model)

---

## 二、方法论步骤

### 步骤 1：确定架构风格

根据 NFR 和功能特征选择：

| 架构风格 | 适用场景 | 不适用场景 |
|---------|---------|-----------|
| 单体 | 小团队、功能简单、低并发 | 多团队、复杂业务、需独立部署 |
| 微服务 | 多团队、独立部署、高并发 | 小项目（过度设计） |
| 事件驱动 | 异步解耦、高吞吐 | 强一致性要求 |

**选择依据**：用 QAR (Quality Attribute Requirements) 评估。

### 步骤 2：技术栈选型

决策原则：
- 优先选择团队熟悉的
- 优先选择社区活跃的（GitHub stars、更新频率）
- 优先选择有大厂背书且开源的
- 版本锁定，不追最新

每个关键技术决策写一条 **ADR**：

```markdown
# ADR-001: 选择 Alibaba 轻量化 DDD 分层架构

## 背景
需要在 Controller→Service→Mapper 传统三层和完整 DDD 之间选一个。

## 决策
采用 Alibaba 轻量化 DDD 精简版：Controller → Manager → Service → Domain → Mapper。

## 后果
- 优点：小项目不臃肿，大项目能扩容，分布式问题有 Manager 层收口
- 缺点：新成员需要理解 Manager vs Service 的分工
```

### 步骤 3：服务拆分 → DDD 限界上下文

拆分原则：
- **按业务能力拆分**：一个服务 = 一个限界上下文
- **按数据所有权拆分**：一个服务拥有自己的数据库
- **按变更频率拆分**：频繁变更的独立部署

| 限界上下文 | 微服务 | MVP | 数据所有权 |
|-----------|--------|-----|-----------|
| 认证 | authorization-service | ✅ | Redis (Token) |
| 用户 | user-service | ✅ | MySQL (user/address/seller) |
| 商品 | item-service | 🔵 | MySQL (product/sku/category) |
| 交易 | trade-service | 🔵 | MySQL (order) |
| 支付 | pay-service | 🔵 | MySQL (pay_order) |
| 物流 | logistics-service | 🔵 | MySQL (logistics) |

### 步骤 4：定义分层架构

```
Controller → Manager → Service(原子业务) → Domain → Mapper → DB
```

| 层 | 职责 | 禁止 |
|----|------|------|
| Controller | 接收请求、参数校验、调用Service、返回Result | 写业务逻辑、操作Redis/锁 |
| Manager | 流程编排、分布式锁、跨服务调用 | 操作Mapper |
| Service | 原子业务逻辑、事务管理 | 调Feign、操作Redis |
| Domain | 领域枚举、领域服务（核心规则） | 依赖Infra/DB |
| Mapper | 数据访问（MyBatis-Plus） | 返回DTO |

### 步骤 5：设计安全架构

- 认证：OAuth2 + JWT (HS256服务间/RS256对外)
- 密钥：JWKS 端点暴露公钥
- 前端：用户端 + 商家端两套前端，同一套认证后端
- 内部接口：`/internal/**` 不包装Result

### 步骤 6：规划部署架构

```
MVP: Docker单机 → V1.1: 读写分离 → V1.2: Nacos集群 → V2.0: K8s
```

---

## 三、冰美商城实践示例

### 架构选型结论

| 决策 | 选择 | 原因 |
|------|------|------|
| 架构风格 | 微服务（Spring Cloud Alibaba） | 可独立部署、独立扩展 |
| 分层 | Alibaba 轻量化 DDD 精简版 | 中小服务够用，预留扩展空间 |
| 注册中心 | Nacos | 阿里生态，同时支持注册+配置 |
| ORM | MyBatis-Plus | 比JPA灵活，比手写SQL简洁 |
| 对象映射 | MapStruct + Lombok | 编译期生成，零运行时开销 |
| API文档 | Knife4j (OpenAPI 3) | Swagger增强，国产友好 |

### 服务分解

11 个模块（2 共享 + 9 服务），按 Phase 0→1→2→3→4 逐步实现。

详见：[project-docs/02-Architecture.md](../../project-docs/02-Architecture.md)

### 分层铁律

1. 领域层不依赖基础设施/DB
2. DO 只允许在 mapper 出现
3. DTO/VO 隔离，禁止 DO 外泄
4. 分布式问题统一收口到 Manager
5. Controller 不写业务/不操作 Redis

---

## 四、常见错误与检查清单

### 容易犯的错误

1. **过早拆分微服务**：功能简单时拆分微服务 → 网络开销、调试困难
2. **共享数据库**：多个服务连同一个数据库 → 耦合，无法独立演进
3. **分层形同虚设**：Manager 层空着，所有逻辑堆在 Service
4. **技术栈求新**：追最新版本 → 不稳定、文档少、社区小
5. **忽略 ADR**：技术决策没有记录 WHY → 后来者不知道为什么这样选

### 完成后的自检清单

- [ ] 架构风格有 NFR 依据（不是"微服务就是好"）
- [ ] 每个关键技术选择有 ADR 记录
- [ ] 服务按限界上下文拆分，每个服务拥有自己的数据
- [ ] 分层规则清晰，每层职责和禁止事项明确
- [ ] 安全架构覆盖认证/授权/密钥管理
- [ ] 部署架构有演进路线（不是一上来就 K8s）
- [ ] ADR 记录了「背景→决策→后果→备选方案」

### 本阶段完成定义 (DoD)

- [ ] 架构文档通过评审
- [ ] 所有检查清单项通过
- [ ] 每条关键决策有对应 ADR 记录
- [ ] 限界上下文划分与 Phase 2 (事件风暴) 泳道一致
- [ ] 产出已同步到 project-docs/02-Architecture.md + adr/

---

## 五、与后续阶段的衔接

- **输出到 Phase 5 (领域建模)**：限界上下文 → 领域模型的作用域
- **输出到 Phase 7 (数据建模)**：服务拆分 → 每个服务独立的数据库
- **输出到 Phase 6 (接口设计)**：服务边界 → API 端点归属

**方法论对应**：
- 本阶段 → **DDD 战略设计** (限界上下文 = 微服务边界)
- 下一阶段 → DDD 战术设计 (领域建模)
