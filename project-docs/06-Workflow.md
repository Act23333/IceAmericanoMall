# 06 — 开发流程文档

> 来源：`doc/项目的分析流程/00.用户分析.md`、`doc/项目的分析流程/07.创建微服务的过程需要遵循清晰的分层架构.md`、`doc/项目流程/三层架构命名规范.md`、`doc/项目难题/架构分层、工程结构、微服务规范、分布式设计选择.md`、`doc/项目的分析流程/IcedAmericanoMall的MVP.md`

---

## 一、项目阶段路线图

```
Phase 1 (MVP)              Phase 1.5 (完善)               Phase 2 (平台化)              Phase 3 (智能化)
2026 Q1-Q2                 2026 Q3-Q4                     2027+                         2028+
─────────────────────     ───────────────────────────     ────────────────────────────  ────────────────────────────────
核心交易闭环               精细化运营 + AI                平台生态                       知识驱动
用户→商品→购物车→下单→支付  优惠券/秒杀/积分/搜索/AI商品助手  商家入驻/财务结算/客服(含AI客服)  知识图谱/RAG+KG混合检索/场景化推荐
                                                                        
现在所处阶段：V2.5 安全+CI+功能收尾 + AI 生产就绪 🔵 —— 14 模块 / 133+ 端点 / 150+ 测试。下一阶段：Phase 5 前端 + V3.0 智能升级

相关图表：
- MVP 范围用例图：`drawio/IA-MALL-MVP.drawio`（标注了 Must/Should/Could 优先级）
```
```

---

## 二、方法论与开发流程的集成

### 2.1 各方法论在流程中的定位

```
需求分析         设计阶段            编码阶段          测试阶段          交付
───────▶        ───────▶           ───────▶         ───────▶         ──────▶

BDD              DDD                SDD              TDD               SPC
用户场景         领域建模            接口规格          测试驱动           质量门禁
Given-When-Then 限界上下文/聚合     请求/响应契约      单元测试→集成测试   检查清单
(PRD/API doc)   (Domain Model)     (API Spec)       (Test Cases)      (Workflow)

先写验收场景       先建领域模型        先定义接口         先写测试           质量度量
→指导开发         →理解业务           →前后端并行         →驱动实现          →持续改进
```

### 2.2 标准开发流程

```
1. BDD — 编写验收场景
   ↓
2. DDD — 识别限界上下文、实体、聚合
   ↓
3. SDD — 定义 API 规格（请求/响应格式、状态码）
   ↓
4. TDD — 编写失败测试 → 最小实现 → 重构
   ↓
5. SPC — 质量门禁检查（测试覆盖率、代码规范、PR Review）
   ↓
6. 集成 & 部署
```

---

## 三、微服务创建顺序

新微服务按 **从下往上** 顺序创建，确保依赖清晰：

```
1. 数据库表设计 & 创建 (DDL)
2. entity — 数据库实体映射 (MyBatis-Plus)
3. mapper — 数据访问接口 (继承 BaseMapper)
4. dto / vo — 数据传输对象
5. service + impl — 业务逻辑（先接口，后实现）
6. manager — 编排逻辑（如涉及多服务/分布式）
7. converter — MapStruct 转换器
8. controller — REST 接口
9. config — 服务配置
10. 编写测试 — TDD 循环
```

---

## 四、编码规范

### 4.1 通用原则

- **小类、小方法**：拒绝 500 行以上类、100 行以上方法
- **单一职责**：每个方法只做一件事，命名准确表达意图
- **避免硬编码**：常量提取到 `constants` 包
- **优雅空值处理**：使用 `Optional` 或工具方法，不散布 `if (x != null)`
- **减少嵌套**：尽早 return，避免深层 if-else

### 4.2 Controller 层铁律

```java
@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {
    private final UserService userService;

    @GetMapping("/{id}")
    public Result<UserVO> getUser(@PathVariable Long id) {
        // ✅ 参数校验、调用 Service、返回 Result
        return Result.success(userService.getUserById(id));
    }

    // ❌ 绝不在此层：
    // - 写业务逻辑
    // - 直接操作 Redis / 分布式锁
    // - 直接调用 Mapper
    // - 返回数据库 DO
}
```

### 4.3 Service 层规范

```java
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    @Override
    public UserDTO register(UserRegisterDTO dto) {
        // ✅ 业务逻辑编排
        // ✅ 调用 Mapper 做数据操作
        // ✅ 使用 Converter 做对象转换
        // ❌ 不处理 HTTP 请求参数校验（Controller 已做）
        // ❌ 不直接操作 Redis / 分布式锁（交给 Manager）
    }
}
```

### 4.4 命名规范速查

| 层 | 模式 | 正确示例 | 错误示例 |
|---|------|---------|----------|
| Mapper | `select`/`insert`/`update`/`delete` + 条件 | `selectByPhone`, `updateStatusById` | `getUser`, `findByPhone` |
| Service | 业务动词 + 领域名词 | `register`, `getUserById`, `pageUsers`, `disableUser` | `insertUser`, `queryUser` |
| Controller | HTTP+资源 / 业务动词 | `register` (POST), `getUser` (GET), `deleteAddress` (DELETE) | `doRegister`, `get` |

---

## 五、Git 工作流

### 5.1 分支策略

```
main (生产就绪)
  └── develop (集成)
        ├── feature/xxx-service (服务开发)
        ├── feature/xxx-feature (功能开发)
        └── fix/xxx-bug (Bug修复)
```

### 5.2 Commit 规范

```
<type>: <简短描述>

类型:
- feat: 新功能
- fix: Bug 修复
- refactor: 重构（无功能变化）
- test: 测试相关
- docs: 文档变更
- chore: 构建/配置变更

示例:
feat: 用户注册接口实现
fix: 修复登录时 Token 过期判断
refactor: 将限流逻辑提取到 Manager 层
```

---

## 六、质量门禁 (SPC Checkpoints)

每个功能模块在合并前必须通过以下检查：

### 6.1 开发阶段检查

| 检查点 | 说明 | 工具/方式 |
|--------|------|----------|
| BDD 场景覆盖 | 所有核心流程有 Given-When-Then 验收场景 | 人工 Review |
| API 规格先行 | 接口契约在编码前定义 | 对照 API Spec 文档 |
| 分层合规 | Controller 无业务/Redis/锁操作 | 人工 Review |
| DO 不外泄 | DTO/VO 隔离，不将 Entity 返回给前端 | IDE 搜索 `entity` import |
| 对象转换 | 使用 MapStruct Converter，不手写 set/get | 搜索 `BeanUtils.copyProperties` |
| 异常处理 | 使用 ia-common 异常体系，不吞异常、不裸奔 RuntimeException | 人工 Review |
| 日志规范 | 关键节点有 log（请求入口、第三方调用、异常），敏感信息脱敏 | 人工 Review |

### 6.2 测试阶段检查

| 检查点 | 目标 |
|--------|------|
| 单元测试 | Service/Domain 层覆盖核心业务逻辑 |
| 集成测试 | Controller 层覆盖 API 契约 |
| API 测试 | 端点实际行为与 API Spec 一致 |
| 限流测试 | 验证 `@RateLimit` 在并发下正确拦截 |

### 6.3 提交阶段检查

| 检查点 | 说明 |
|--------|------|
| 编译通过 | `mvn clean install` 无报错 |
| 测试通过 | 无失败的单元/集成测试 |
| 无未解决的问题 | TODO/FIXME 需要关联 Issue 或立即处理 |

---

## 七、CI/CD 流水线（计划 V1.1：GitHub Actions）

```
代码推送 (develop/feature)
  │
  ├── Stage 1: 编译 & 测试
  │     ├── mvn clean install (全模块)
  │     ├── 单元测试 + 集成测试 (Testcontainers)
  │     └── 代码风格检查 (Checkstyle)
  │
  ├── Stage 2: 构建镜像
  │     ├── 每个服务构建 Docker 镜像
  │     └── 推送到私有镜像仓库
  │
  └── Stage 3: 部署 (PR 合并到 main)
        ├── Docker Compose 更新容器
        └── 冒烟测试 (核心 API)
```

### 本地开发环境（Docker Compose）

```yaml
# 一键启动开发依赖
services:
  mysql:       # MySQL 9.3.0, port 3306
  redis:       # Redis 7.x, port 6379
  nacos:       # Nacos 2.x, port 8848
  # ES (可选): ElasticSearch 7.17.25, port 9200
```

---

## 八、技术决策速查

| 问题 | 决策 | 详见文档 |
|------|------|---------|
| 用什么分层架构？ | Alibaba 轻量化 DDD 精简版 | 02-Architecture §4 |
| Manager 层放什么？ | 分布式锁、限流、幂等、多服务编排 | 02-Architecture §4.2 |
| 金额怎么存？ | INT 类型，单位「分」 | 04-Data-Model §1 |
| 主键用自增还是 UUID？ | 数据库内用自增ID，对外用 UUID | 04-Data-Model §1 |
| DO 能返回给前端吗？ | 不能，必须转 DTO/VO | 04-Data-Model §4 |
| 订单地址改了怎么办？ | 下单时做快照，存到订单表 | 04-Data-Model §1 |
| 密码怎么存？ | BCrypt 加密 | 02-Architecture §5 |
| 第三方 API 怎么调？ | 独立封装在 infra 层，不散落 Service | 02-Architecture §4.2 |
| 异常怎么处理？ | ia-common 统一异常体系 + GlobalExceptionHandler | 02-Architecture §3.3 |
| Swagger 文档？ | Knife4j (OpenAPI 3) | 02-Architecture §2 |
| 分布式事务怎么办？ | MVP 手动 Saga 补偿，V1.1 引入 Seata | 09-constraints §9 |
| 定时任务多实例？ | MVP @Scheduled 单机，V1.1 迁移到 XXL-Job | 09-constraints §10 |
| 跨服务问题排查？ | V1.1 引入 SkyWalking 分布式追踪 | 09-constraints §11 |
| 消息怎么异步化？ | V1.1 引入 RabbitMQ，核心事件发布/订阅 | 03-Domain-Model §8 |
| 商品图片存哪里？ | V1.1 MinIO/阿里云 OSS 对象存储 | 02-Architecture §2.3 |
| ES 数据怎么同步？ | V1.2 Canal CDC MySQL binlog → ES | 04-Data-Model §1 |
| 分库分表用什么？ | V2.0 Apache ShardingSphere | 04-Data-Model §1 |
| CI/CD 用什么？ | GitHub Actions | 06-Workflow §7 |
| 本地开发环境？ | Docker Compose 一键启动 MySQL/Redis/Nacos | 06-Workflow §7 |

---

## 八、参考资料

分析过程中的详细推演、技术学习笔记保留在 `doc/` 目录：
- `doc/项目难题/` — 技术决策的 WHY（为什么选 BCrypt？为什么金额用分？等）
- `doc/项目流程/` — 流程说明（登录流程、命名规范等）
- `doc/项目的分析流程/` — 分析方法论教程
