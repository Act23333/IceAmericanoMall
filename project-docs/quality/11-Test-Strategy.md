# 11 — 测试策略文档

> 最后更新: 2026-10-14（新增 §九 多平台测试：设备矩阵、原生测试要求、包体回归；后端五层金字塔与 AI 测试策略未变）
> 来源：`doc/methodology/10-test-strategy.md`（方法论模板）
> 关联：`contract/05-API-Specification.md`（BDD 契约）、`10-review.md`（测试检查点）、`19-Test-Flow-Guide.md`（各平台执行流程实操）
> 归属域: quality | Owner: doc-engineer | 更新触发条件: 测试策略变更时（含新增平台/设备形态）

---

## 〇、当前实施状态 vs 目标策略

> ⚠️ 以下测试金字塔为 **V1.1 目标策略**。当前 MVP 阶段的实际覆盖与目标差距较大。

| 测试层级 | 目标占比 | 当前实际 | 状态 |
|---------|---------|---------|------|
| Unit (Entity/Enum/DTO) | 70% | **多模块覆盖** | ✅ 部分覆盖 |
| Unit (Mockito 业务逻辑) | 70% | **多 Manager/Service** (PayManager/OrderManager/SignManager 等) | 🟡 起步→提升 |
| API / Integration | 20% | **~30 H2 集成测试**（OrderManager/AuthService/Balance/FlashSale/DbSearch/Cart 等，MyBatis-Plus+H2 无 Spring 上下文） | 🟡 起步（替代重量级 @SpringBootTest） |
| Contract (Pact) | — | **0**（以内部端点 H2 集成 + Feign fallback 单测替代） | 🟡 后续 |
| E2E | 5% | **0 tests** | ❌ 未开始 |
| Performance (JMeter) | — | **0 tests** | ❌ 未开始 |
| JaCoCo 覆盖率 | 70%(目标) | **V2.4 已接入**：父 POM `jacoco-maven-plugin`（report 随 test 生成；排除 POJO/config/生成代码）；`check` 门禁 30% **advisory(不阻断)**，随测试补齐逐步抬高至 70% | 🟡 已接入(advisory) |

**当前覆盖（2026-10-10 实测）**: 18 个 Maven 模块全部有测试（合计 1636，模块计数明细见 `AGENTS.md`；`database` 仅 SQL 无测试）  
**零测试模块**: 无

**已知限制**: MyBatis-Plus `ServiceImpl` 的 `lambdaQuery()`/`lambdaUpdate()` 链无法被 Mockito mock（`currentModelClass()` 从 Mapper 接口读取泛型类型信息，在 Mockito 代理中丢失）。这些方法需要 `@SpringBootTest` + H2 或 Testcontainers 集成测试。

### 〇.1 治理与补齐路线（2026-10-10 CR 决议）

| 事项 | 状态 | 说明 |
|---|---|---|
| CI 不变量门禁 | ✅ 已落地 | `Implementation/back-end/scripts/check-id-invariants.sh`（UUID 生成唯一出口 / 包装器 byte[] 参数 / SQL 字面量禁令），`ci.yml` 编译前强制；`architecture-tests` 模块（ArchUnit）随全量 test 强制 |
| H2 持久化回归 | ✅ 已扩 | BINARY(16) 往返 / `toBytesOrNull` 未命中语义 / 订单-发票-建账链路 / **商品检索与联想 SQL（ADR-052 自 search 迁入 item）**共 9 个 H2 测试类 |
| 集成测试方案 | 🔵 方案修正，未实施 | **本拓扑（Nacos/MySQL/Redis/RocketMQ 等由 Docker 部署于独立 Linux 虚拟机）下不引入 Testcontainers**——改用「外部测试环境 profile 集成测试」：`@SpringBootTest` + 专用 test profile 指向 VM 内测试库/独立 Redis DB index/测试 Nacos namespace（隔离纪律：专用 schema + 运行前清场）；CI 内置 services 仅作可选补充。首批目标：① `BINARY(16)` 驱动级往返（真 MySQL）② Redis 验证码场景隔离端到端 ③ auth↔verification `scene` 契约。触发条件：功能开发完成后 |
| ArchUnit 架构守卫 | ✅ 已落地（2026-10-14） | `architecture-tests` 模块（第 19 个 Maven 模块）：① domain 框架无关 ② PO 不出 persistence ③ adapter 不触 persistence ④ UUID 生成白名单。存量债务经 **FreezingArchRule 冻结 25 条**（22 处域端口 IPage 返回 + 3 处 trade 域枚举 @EnumValue；偿还方向：域层 `PageResult` 抽象 / PO Integer 映射——item 本次新代码已按 `PageResult` 形态落地）；`allowStoreCreation=false` 为治理红线（store 缺失必须失败而非静默重建）。原 CR 发现的 user oplog PO 越界（26 处）已**真实整改**（补 `OperationLogRepository` 端口与领域模型），未入基线 |
| 压测基线 | ❌ 未开始 | 场景与 SLO 见 §五；执行前置=完整 compose 编排（MySQL/Redis/Nacos/RocketMQ/ES） |

---

## 一、测试金字塔（V1.1 目标）

```
       /\
      /E2E\          5%   — 核心全链路（下单→支付→发货→收货）
     /------\
    /  API   \        20%  — 接口契约测试（每端点 1 正常 + N 异常）
   /----------\
  /   Unit     \      70%  — 单元测试（Service/Domain/Util/Converter）
 /--------------\
     Security         不按占比 — 越权/注入/签名伪造
```

| 层级 | 占比 | 范围 | 框架 | 运行频率 |
|------|------|------|------|---------|
| Unit | 70% | Service、Domain、Util、Converter | JUnit 5 + Mockito | 每次 push |
| API / Integration | 20% | Controller → Service → Mapper → DB | SpringBootTest + Testcontainers | 每次 PR |
| Contract | — | Feign 接口契约 | Pact | 每次 PR |
| E2E | 5% | 全链路，外部系统 Mock | Postman / Newman | 每次发布前 |
| Security | — | 越权、注入、签名伪造 | 手动 + OWASP ZAP | 每次大版本 |

---

## 二、BDD → 测试用例映射规则

PRD 中每个 BDD Scenario 映射到至少一个集成测试：

| BDD 场景 | 测试类型 | 最少测试数 |
|----------|---------|-----------|
| 正常场景（Happy Path） | API 集成测试 | 1 |
| 备选场景（Alternative） | API 集成测试 | 每个备选 1 个 |
| 异常场景（Error） | API 集成测试 + 单元测试 | 每个异常 1 个 |

**命名约定**：`should_[预期行为]_when_[条件]`

### 示例：注册功能

```java
// BDD 正常场景 → API 测试
@Test
@DisplayName("注册 — 新手机号+有效验证码 → 返回Token")
void shouldReturnToken_whenNewPhoneAndValidCode() { ... }

// BDD 备选场景 → API 测试
@Test
@DisplayName("注册 — 已注册手机号 → 返回错误")
void shouldReturnError_whenPhoneAlreadyRegistered() { ... }

// BDD 备选场景 → API 测试
@Test
@DisplayName("注册 — 验证码错误 → 提示错误，允许重试")
void shouldReturnError_whenWrongSmsCode() { ... }
```

---

## 三、契约测试 (Contract Testing)

微服务间 Feign 接口通过 **Pact** 做 Consumer-Driven Contract Testing：

### 3.1 原则

- **消费者定义契约**：调用方（如 trade-service）定义期望的 Feign 接口行为
- **提供者验证契约**：被调用方（如 user-service）验证自己能满足所有消费者的期望
- **契约即文档**：`.pact` 文件可导出为可读的 API 规范

### 3.2 关键接口契约清单

| 消费者 | 提供者 | Feign 接口 | 关键场景 |
|--------|--------|-----------|---------|
| trade-service | user-service | `GET /internal/user/{id}` | 返回用户基本信息 |
| trade-service | user-service | `GET /internal/user/address/{id}` | 返回收货地址 |
| trade-service | item-service | `GET /internal/sku/{id}` | 返回 SKU 库存/价格 |
| trade-service | item-service | `PUT /internal/sku/{id}/stock` | 锁库存 |
| pay-service | trade-service | `PUT /internal/order/{orderNo}/status` | 更新订单支付状态 |
| gate-service | authorization-service | `POST /api/auth/*` | JWT 签发/验证 |

### 3.3 CI 集成

```
PR 创建 → 消费者 Pact 生成 → 提供者 Pact 验证 → 契约不匹配 → CI 失败
```

契约变更必须双向 Review：消费者提 PR 时附带 `.pact` 变更，提供者确认兼容性。

---

## 四、覆盖率目标

| 维度 | 最低 | 目标 | 豁免 |
|------|------|------|------|
| 行覆盖率（全项目） | 70% | 80% | Lombok 生成、POJO、Config、Constants |
| 分支覆盖率 | 60% | 70% | — |
| Service 层 | 90% | 95% | — |
| Manager 层 | 85% | 90% | — |
| Controller 层 | 80% | 90% | — |
| Domain/Util 层 | 95% | 95%+ | — |
| Mapper 层 | 不要求 | — | MyBatis-Plus 自动生成 |

**执行**：JaCoCo 插件，CI 中覆盖率不达标则构建失败。

---

## 五、性能测试基线

| 场景 | 并发 | TPS 目标 | P99 延迟 | 失败率 |
|------|------|---------|---------|--------|
| 商品列表查询 | 500 | ≥ 5000 | < 200ms | < 0.1% |
| 商品详情 | 500 | ≥ 3000 | < 150ms | < 0.1% |
| 登录 | 200 | ≥ 1000 | < 300ms | < 0.1% |
| 创建订单 | 200 | ≥ 500 | < 500ms | < 0.1% |
| 支付回调 | 100 | ≥ 800 | < 200ms | < 0.01% |

**压测工具**：JMeter（既有计划）或 k6（脚本化、可入 CI 冒烟），二选一落地；每个 Sprint 至少跑一次基准测试。

**执行前置与新增场景（2026-10-10 CR 补）**：需完整依赖编排（docker-compose 拉起 MySQL/Redis/Nacos/RocketMQ/ES）；核心链路场景须覆盖本轮加固路径——注册（含**前置快拒绝**分支）、发码（含**日配额拒绝**分支）、验证码登录（场景隔离）、下单 Saga（库存原子扣减 + 状态条件更新竞态）。

---

## 六、安全测试清单

| 测试项 | 方法 | 优先级 |
|--------|------|--------|
| 横向越权 — 订单 | 替换 JWT 中的 userId 后请求他人订单 | 🔴 必测 |
| 横向越权 — 地址 | 修改 addressId 参数访问他人地址 | 🔴 必测 |
| SQL 注入 | 搜索/排序字段注入 payload | 🔴 必测 |
| 支付回调签名伪造 | 篡改金额/订单号重放回调 | 🔴 必测 |
| 短信接口爆破 | 60s 内高频请求 `/api/auth/send-sms` | 🟡 应测 |
| 日志敏感信息泄露 | grep 日志中的手机号/密码/Token | 🟡 应测 |
| 管理员接口越权 | 普通用户 Token 请求 `/admin/**` | 🔴 必测 |

---

## 七、测试数据管理

- **单元测试**：纯 Mock，不需数据库
- **API 集成测试**：Testcontainers 启动临时 MySQL/Redis，测试结束自动销毁
- **E2E 测试**：独立测试环境，预置标准测试数据集
- **隔离原则**：测试之间不共享可变状态

---

## 八、测试在 CI/CD 中的位置

```
Push → 单元测试 (3min) → 编译 → 契约测试 (2min) → API集成测试 (8min) → PR 创建
                                                                    │
PR Merge ← Code Review ← 覆盖率检查 ← E2E测试 (15min) ←──────────┘
```

覆盖率不达标 → CI 直接失败，禁止合并。

---

## 九、多平台测试策略（前端五平台）

> **适用范围**：本文 §一–§八 描述的是**后端**五层金字塔（基础设施→单元→API 冒烟→E2E→性能）。
> 前端多平台化后，同一套质量目标需要**额外的设备维度与原生维度**，本节定义它们。
>
> **平台轴**（与 `frontend/18-platforms/` 一致）：Web / 桌面端 / Android / iOS / 小程序。
>
> | 平台 | 测试实施状态 | 承载 |
> | :-- | :-- | :-- |
> | Web（`apps/web` / `admin` / `seller`） | 🟡 骨架可测，E2E 与性能门禁未接入 | Vitest + Playwright + Lighthouse CI |
> | 小程序（`apps/miniprogram`，Taro 4） | 🔴 未开始 | 开发者工具 + 真机预览 |
> | Android / iOS | 🔴 未开始（无独立工程，候选承载 `apps/mobile` RN 占位） | 未定 |
> | 桌面端 | ⚪ 未排期 | 未定（取决于所选生态） |
>
> 各平台的**执行步骤**（命令、分发渠道、审核等待）见 [19-Test-Flow-Guide.md](./19-Test-Flow-Guide.md) §十一；
> 本文只定**测什么、测到什么程度、在哪些设备上测**。

### 9.1 设备矩阵（Device Matrix）

**为什么要矩阵**：单一设备上的"通过"不能证明多平台可用。同一个前端在不同 OS 版本、不同屏幕尺寸、不同渲染引擎上的行为差异，是**发布后才知道**的典型事故来源。

#### 9.1.1 分层策略（三档原则）

每个平台都按三档维护最小必要设备集，**不为每档都买真机**：

| 档位 | 含义 | 覆盖目的 | 渠道 |
| :-- | :-- | :-- | :-- |
| **最低支持档** | 该平台声明支持的最低 OS 版本 / 最小屏幕 | 验证降级路径与布局不崩 | 真机优先（老机器） |
| **主流档** | 当前市场占有率最高的 OS/屏幕组合 | 覆盖绝大多数用户 | 真机 + 模拟器 |
| **上限档** | 最新 OS / 最大屏 / 高刷 / 折叠形态 | 验证新特性、新 API 与高级动效 | 模拟器/模拟器 + 真机抽查 |

#### 9.1.2 矩阵表（骨架 —— 数值待 `15` 的平台矩阵定稿后填充）

> ⚠️ **本表的 OS 版本、机型、屏幕尺寸留空是有意的**：它们的权威来源是
> `frontend/15-Front-End-Technology-Selection.md` 的**平台矩阵**（`最低支持版本` 列，归 frontend-engineer）。
> 本表**不复制、不自造**这些数值 —— 改一处必须改一处。

| 平台 | 档位 | OS / 运行时 | 机型 / 设备形态 | 屏幕 | 覆盖用途 |
| :-- | :-- | :-- | :-- | :-- | :-- |
| **Web** | 最低支持 | 见 `15` 平台矩阵（浏览器最低版本） | —（桌面浏览器） | 1280×720 | 布局降级、老浏览器特性探测 |
| | 主流 | 见 `15` 平台矩阵 | — | 1920×1080 / 1440×900 | 主路径功能与视觉 |
| | 移动视口 | 同上 | Playwright 设备模拟（如 iPhone 14 / Pixel） | 375×667 起 | 响应式与 Touch 交互 |
| | 上限 | 最新 Chrome / Safari / Edge | 高刷显示器 / 折叠视口 | 2560×1440 / 折叠比例 | 高级动效、新 CSS 特性 |
| **小程序** | 最低支持 | 见 `15` 平台矩阵（宿主最低版本，如微信基础库） | 低端 Android 机 | 小屏 | 分包加载、降级渲染 |
| | 主流 | 同上 | iOS + Android 各 1 台主流机 | 标准 | 主路径 |
| | 宿主分支 | 微信 / 支付宝（如两者都做） | 各宿主 1 台 | 标准 | **宿主 API 差异**（登录、支付、分享） |
| **Android** | 最低支持 | 见 `15` 平台矩阵（minSdk） | 低端真机 | 小屏 | 冷启动、内存、降级 |
| | 主流 | 同上 | 主流品牌中端机 | 标准 | 主路径 |
| | 上限 | 最新 Android | 高刷 / 折叠 | 大屏/折叠 | 新 API、折叠布局 |
| **iOS** | 最低支持 | 见 `15` 平台矩阵（最低 iOS 版本） | 老机型真机 | 小屏 | 降级路径 |
| | 主流 | 同上 | 主流 iPhone | 标准 | 主路径 |
| | 上限 | 最新 iOS | 大屏 / ProMotion 高刷 / Dynamic Island | 大屏 | 新 API、系统集成 |
| **桌面端** | 最低支持 | 见 `15` 平台矩阵（最低 OS） | 低配机 | 1280×720 | 安装包兼容性 |
| | 主流 | 同上 | 主流 Win / macOS | 1920×1080 | 主路径 |
| | 上限 | 最新 OS | 高 DPI / 多显示器 | 4K / 缩放比 | 高 DPI 渲染、多窗口 |

#### 9.1.3 矩阵使用规则

| 规则 | 要求 |
| :-- | :-- |
| **每个发布候选必须在矩阵上跑一遍** | 不允许"只在我这台机器上过了" |
| **档位不可静默省略** | 某档位缺失（如没有低端机）必须在发布记录里写明并给出替代证据 |
| **矩阵变更需同步三处** | ① 本表 ② `15` 平台矩阵的最低支持版本 ③ 各 `18-platforms/<平台>.md` 的验收清单 |
| **小程序额外有宿主维度** | 微信与支付宝是**两个测试目标**，不是"同一份代码"；宿主 API 差异必须逐宿主验 |
| **Web 的移动视口不是"移动端"** | 它只验证响应式与 Touch；**RN/原生 App 的验收必须在真机矩阵上做** |

### 9.2 原生测试要求（真机 vs 模拟器）

> **核心原则：模拟器/模拟器能证明「逻辑对」，不能证明「能用」。**
> 性能、传感器、权限、推送、支付、后台执行这六类问题**只在真机上出现**。

#### 9.2.1 强制真机的场景（模拟器结论无效）

| # | 场景 | 为什么模拟器测得不算 |
| :-- | :-- | :-- |
| 1 | **冷启动时间** | 模拟器无真实磁盘/内存压力，数字失真 |
| 2 | **包体大小** | 模拟器不反映真实分发产物（见 §9.3） |
| 3 | **推送通知** | APNs / FCM / 厂商推送通道需真实设备令牌 |
| 4 | **授权与权限** | 相机、相册、定位、通知授权的真实弹窗与拒绝路径 |
| 5 | **支付链路** | 微信/支付宝/Apple Pay/IAP 的沙箱与真实环境绑定设备与账号 |
| 6 | **后台执行与生命周期** | 后台挂起、被杀进程、唤醒策略，模拟器行为与真机不同 |
| 7 | **手势与导航范式** | iOS 边缘返回、Android 系统返回手势/预测式返回 |
| 8 | **高刷与掉帧** | ProMotion / 高刷屏的动画帧率与能耗 |
| 9 | **弱网与网络切换** | 真实弱网、Wi-Fi↔蜂窝切换、飞行模式恢复 |
| 10 | **小程序宿主限制** | 真机预览的分包加载、域名白名单、宿主壳内渲染差异 |

#### 9.2.2 模拟器/模拟器可用于

- 单元测试、组件测试、逻辑层测试（快速、可并行）
- 布局与多分辨率快照（UI 快照/视觉回归）
- 首次可用性验证与开发内循环
- **上限档**的新 OS 特性早期验证

#### 9.2.3 自动化与人工的边界

| 测试类型 | 手段 | 频率 | 门禁 |
| :-- | :-- | :-- | :-- |
| 逻辑层单测（各平台） | 各生态单测框架 | 每次 push | 🔴 阻断 |
| 组件/UI 快照 | 各生态快照或视觉回归工具 | 每次 PR | 🟠 告警 |
| 关键路径 E2E（Web） | Playwright | 每次 PR | 🔴 阻断 |
| 关键路径 E2E（原生/小程序） | 各生态 E2E 或自动化脚本 | 每次发布候选 | 🟠 需人工确认 |
| **真机人工验收** | 人 + 真机矩阵 | **每个发布候选** | 🔴 阻断发布 |
| 性能与包体回归 | 见 §9.3 | 每个发布候选 | 🔴 阻断（超阈值时） |

> **原生平台的覆盖率口径**：不要求原生 UI 代码达到 §四 的行覆盖率数字（原生 UI 的测试性价比低且框架支持差）。
> 但**必须**把可测逻辑下沉到可单测的层（状态、计算、校验、格式化），并对其设覆盖率要求 ——
> 具体阈值在各 `18-platforms/<平台>.md` 的 §七 平台 DoD 中登记，不在此处凭空设定。

### 9.3 包体回归（Bundle / 安装包体积基线）

> **为什么必须单列一项**：包体是**唯一只会单向增长**的质量指标 —— 没有基线就一定会膨胀，
> 而膨胀的直接后果是启动变慢、下载转化下降、小程序超限无法提交。

| 平台 | 度量对象 | 记录方式 | 门禁 |
| :-- | :-- | :-- | :-- |
| Web | 首屏 JS（gzip/brotli 后）+ 首屏 CSS | 构建产物分析（`@next/bundle-analyzer`） | 超基线 **+5%** 需在 PR 说明理由 |
| 小程序 | **主包 / 分包 / 总包**（Taro 构建产物） | `dist` 体积统计 | 主包不得超过宿主上限（上限值查 `15` 平台矩阵）；超限阻断提交 |
| Android | APK / AAB 体积（按 ABI 拆分） | 构建产物统计 | 超基线 **+5%** 需说明；发布前记录趋势 |
| iOS | `.ipa` 体积（按设备类型） | 归档产物统计 | 同上 |
| 桌面端 | 安装包体积 | 构建产物统计 | 同上 |

**执行要求**

1. **建立基线**：平台首次达到发布候选状态时，记录一次基线值（含构建模式、压缩设置、目标 ABI/架构）。
2. **每个发布候选对比**：超阈值 → 先查原因（新增依赖？未做 tree-shaking？资源未压缩？）→ 再决定接受或优化。
3. **阈值收紧优先于功能新增**：包体治理是持续项，不允许"先发出去再优化"。
4. **小程序是硬上限**：超限不是"告警"而是**无法提交**，必须在上限前留出余量。
5. 基线与实测值登记位置：各 `18-platforms/<平台>.md` 的平台 DoD 或平台细则正文（**不在本文重复登记具体数字** —— 数字属平台事实，随构建变化）。

### 9.4 多平台测试的 CI 位置

```
                     ┌──────────────────────────────┐
Push ───────────────►│ lint + typecheck + 单测(各平台) │  必须全绿
                     └──────────────┬───────────────┘
                                    ▼
                     ┌──────────────────────────────┐
PR ─────────────────►│ token 一致性 + 组件快照 + Web E2E │  灰色项: 告警
                     └──────────────┬───────────────┘
                                    ▼
发布候选 ────────────►│ 真机矩阵人工验收 + 性能/包体回归  │  🔴 阻断发布
                     └──────────────┬───────────────┘
                                    ▼
发布 ────────────────►│ 灰度观察(各平台手段见 19 §十一)   │
                     └──────────────────────────────┘
```

> **真机验收无法自动化，因此必须显式排期与留痕**：验收人、设备档位、结论三者缺一不可。
> 「没人验过」与「验过通过」在发布记录里必须是可区分的两种状态。

---

## 六、AI 服务测试策略 (V2.5+)

> AI 服务（ai-service）的测试与传统微服务有本质差异：LLM 输出具有不确定性，Tool 调用链动态变化，传统"给定输入→断言输出"模式不适用。

### 6.1 测试分层

| 层级 | 测试对象 | 策略 | 工具 |
|------|---------|------|------|
| **Unit** | Tool 方法（SearchTool, OrderLookupTool） | Mock LLM + 断言 Tool 输入/输出格式化逻辑 | JUnit 5 + Mockito |
| **Unit** | 会话管理（RedisChatMemoryStore） | Testcontainers Redis → 写入/读取/过期验证 | Testcontainers |
| **Integration** | Agent 行为（ReAct 循环） | 录制 LLM 响应 → 回放验证 Tool 选择正确性 | 自定义 Record/Replay 或 Spring AI Test |
| **Integration** | RAG Pipeline（V2.5 ES向量检索） | Testcontainers ES 8.x → 索引测试文档 → 验证召回 | Testcontainers |
| **Evaluation** | 答案质量（Faithfulness/Relevance） | 标注数据集 + LLM-as-Judge 自动评分 | Langfuse Evaluation |
| **E2E** | 用户完整对话流程 | 真人验收 / E2E 录制回放（非 CI，周期性执行） | 人工 + 自动化报告 |

### 6.2 LLM 调用的录制/回放模式

```
录制模式（生成 goldens）:
  AI请求 → 真实 LLM → 保存 {input → output} 到 test/resources/llm-goldens/

回放模式（CI 中使用）:
  AI请求 → 拦截器匹配 → 返回录制响应（不调用真实 LLM）
```

**关键规则**：
- Golden 文件签入 Git（小体积 JSON，不超过 100 条）
- CI 必须使用回放模式（`ai.test.record=false`）
- 新增 Tool 或 Prompt 变更 → 手动重新录制 goldens
- 回放模式下，断言聚焦于：Tool 是否被正确选择、返回格式是否正确（不评测 LLM 生成质量）

### 6.3 RAG 评估指标

| 指标 | 计算方式 | CI 准入阈值 |
|------|---------|------------|
| Context Recall | 检索文档是否覆盖答案所需信息 | > 80% |
| Context Precision | 检索文档中相关文档的排位 | MRR > 0.85 |
| Faithfulness | LLM-as-Judge: 回答是否被检索文档支持 | > 90% |
| Answer Relevance | LLM-as-Judge: 回答是否切题 | > 85% |

### 6.4 特殊约束

- LLM 测试 API Key 独立于生产，设置月度预算上限（$5/月）
- 不测试 LLM 的"生成质量"（那是 Prompt Engineering 的工作，不是测试的工作）
- 只测试"确定性行为"：Tool 选择、格式化输出、会话隔离、错误降级
