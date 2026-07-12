# 07 — 任务分解文档

> 来源：`doc/项目的分析流程/IcedAmericanoMall的MVP.md`、`doc/IDEA.md`、`doc/项目的分析流程/01.用户需求捕获.md`

---

## 一、任务依赖图 (MVP)

```
Phase 0: 基础设施
┌─────────────────────────────────────────────┐
│ T0.1 项目骨架搭建 (父 POM + 所有服务模块)      │
│ T0.2 ia-common 公共模块 (Result/异常/工具)    │
│ T0.3 ia-api Feign 接口模块                   │
│ T0.4 数据库初始化 (Initialize.sql)           │
│ T0.5 Nacos 注册中心/配置中心搭建               │
│ T0.6 gate-service 网关搭建                   │
└──────────────┬──────────────────────────────┘
               │
Phase 1: 用户域                               
┌──────────────▼──────────────────────────────┐
│ T1.1 authorization-service: 认证授权         │ ← 阻塞所有需登录的功能
│     ├── OAuth2 Auth Server 搭建              │
│     ├── 密码登录 / 验证码登录 / 注册          │
│     ├── JWT 签发 (HS256+RS256)               │
│     ├── JWKS 公钥端点                        │
│     ├── Refresh Token                       │
│     └── 人机验证 (Geetest)                   │
│                                              │
│ T1.2 user-service: 用户管理                  │
│     ├── 用户 CRUD                            │
│     ├── 收货地址 CRUD                         │
│     ├── 签到功能 (含连续签到奖励)              │
│     └── 限流保护 (@RateLimit)                │
└──────────────┬──────────────────────────────┘
               │
Phase 2: 商品域                               
┌──────────────▼──────────────────────────────┐
│ T2.1 item-service: 商品管理                  │
│     ├── 类目管理 (后台 CRUD)                  │
│     ├── 商品 SPU 发布/编辑/上下架/列表/详情   │
│     └── SKU 管理                             │
│                                              │
│ T2.2 search-service: 搜索 (可延后)           │
│     └── ElasticSearch 商品索引 & 搜索         │
└──────────────┬──────────────────────────────┘
               │
Phase 3: 交易域                               
┌──────────────▼──────────────────────────────┐
│ T3.1 cart-service: 购物车                    │
│     ├── 添加/修改/删除购物车项               │
│     └── 选中/取消 + 合并逻辑                  │
│                                              │
│ T3.2 trade-service: 订单管理                 │
│     ├── 创建订单 (库存扣减/快照/清购物车)      │
│     ├── 订单状态流转                         │
│     ├── 取消订单 (库存回滚)                   │
│     ├── 确认收货                             │
│     └── 超时取消 (定时任务)                   │
│                                              │
│ T3.3 pay-service: 支付处理                   │
│     ├── 支付渠道抽象                         │
│     ├── 微信支付对接                         │
│     ├── 支付回调处理                         │
│     └── 支付状态查询                         │
│                                              │
│ T3.4 logistics-service: 物流                 │
│     └── 发货/物流查询                        │
└──────────────────────────────────────────────┘

Phase 4: 后台管理
┌──────────────────────────────────────────────┐
│ T4.1 商家后台                                │
│     ├── 商家登录 (独立入口/角色校验)          │
│     ├── 仪表盘                               │
│     ├── 商品管理 (复用 item-service)          │
│     ├── 订单管理 (复用 trade-service)         │
│     ├── 发货操作                             │
│     └── 店铺设置                             │
│                                              │
│ T4.2 管理员后台                              │
│     ├── 管理员登录 (独立账号)                 │
│     ├── 用户管理                             │
│     ├── 商家管理                             │
│     ├── 商品管理                             │
│     ├── 订单查看                             │
│     └── 基础数据统计                         │
└──────────────────────────────────────────────┘
```

---

## 二、详细任务分解

### Phase 0: 基础设施

#### T0.1 项目骨架搭建

- **描述**: 创建 Maven 父工程，配置 Spring Boot/Cloud/Alibaba 依赖管理，创建所有服务模块骨架
- **状态**: ✅ 已完成
- **验收**: `mvn -f Implementation/back-end/pom.xml clean install` 成功

#### T0.2 ia-common 公共模块

- **描述**: 实现统一返回 `Result<T>`、异常体系 (`ErrorCode` 枚举 + 6 种异常子类)、`GlobalExceptionHandler`、`@RateLimit` + AOP、JWT 工具、工具类
- **状态**: ✅ 已完成
- **验收**: 任一服务注入 `ia-common` 后可使用 `Result.success()` 和异常处理

#### T0.3 ia-api 接口模块

- **描述**: 定义服务间 Feign 调用接口 (UserClient, ItemClient 等)
- **状态**: ✅ 基础骨架完成，按需补充

#### T0.4 数据库初始化

- **描述**: 执行 `Initialize.sql` 创建 11 张表
- **状态**: ✅ SQL 已就绪，待部署执行
- **验收**: MySQL 中 11 张表全部创建，外键/索引正确

#### T0.5 Nacos 搭建

- **描述**: 部署 Nacos Server，配置服务发现和配置中心
- **状态**: ✅ 已完成（Docker Compose 一键启动）
- **依赖**: T0.1

#### T0.6 gate-service 网关

- **描述**: Spring Cloud Gateway 路由配置、Token 校验过滤器、跨域配置、限流
- **状态**: ✅ 已完成（13 条精确路由，JWT OAuth2，CORS，IP+手机号限流）
- **依赖**: T0.5, T1.1

---

### Phase 1: 用户域

#### T1.1 authorization-service

- **状态**: ✅ 核心功能已完成
- **已完成**:
  - [x] 密码登录 + 验证码登录（同一端点，loginType 参数区分）
  - [x] JWT 签发 (HS256 + RS256)
  - [x] JWKS 公钥端点
  - [x] Refresh Token 旋转刷新
  - [x] 退出登录（Token 黑名单）
  - [x] 人机验证（Geetest 集成）
- **V1.1 待完善**:
  - [x] 多密钥无缝切换机制—— RotationScheduler（@Scheduled cron, jwt.rotation.enabled=true）+ JwkSourceConfig 双 keystore
  - [x] 第三方登录（微信 OAuth）—— WechatLoginStrategy + `POST /api/auth/login/wechat`；user-service `loginByWechat`（code→openid→查/建）；ia-integration `WechatOAuthClient`（Real/Mock 开关，默认 Mock 虚拟 openid）

**BDD 验收场景**:

```gherkin
Scenario: 密码登录 → JWT 签发
  Given 用户已注册且状态正常
  When POST /api/auth/login with {phone, password}
  Then 返回 accessToken (JWT) + refreshToken
  And Token payload 包含 userId, roles

Scenario: 无效密码拒绝
  Given 用户已注册
  When POST /api/auth/login with {phone, wrongPassword}
  Then 返回 400, "手机号或密码错误"

Scenario: Token 刷新
  Given 持有有效的 refreshToken (Redis 中存在)
  When POST /api/auth/refresh
  Then 返回新的 accessToken

Scenario: 退出登录使 Token 失效
  Given 持有有效 accessToken
  When POST /api/auth/logout
  Then accessToken 加入 Redis 黑名单
  And 后续使用该 Token 返回 401

Scenario: 注册幂等保护
  Given requestId "uuid-123" 已完成注册
  When 使用相同 requestId 再次 POST /api/auth/register
  Then 返回已有注册结果，不重复创建用户
```

#### T1.2 user-service

- **状态**: ✅ 核心功能已完成
- **已完成**:
  - [x] 用户注册/登录（密码+验证码）
  - [x] 个人信息修改（昵称/头像，null-safe 部分更新）
  - [x] 收货地址 CRUD（含默认地址管理）
  - [x] 每日签到（Redis Bitmap，连续签到计算，幂等保护）
  - [x] 限流保护（@RateLimit 注解 + Lua 脚本）
  - [x] 商家注册申请 + 店铺管理
  - [x] 管理员用户分页/角色修改/状态管理
  - [x] Aliyun SMS SDK 集成（@ConditionalOnProperty 可切换 Mock/Real）
  - [x] 内部 Feign 接口（register/login/address/user/count）
  - [x] 签到积分递增加速策略（SignManager 编排：连续第 n 天 10/15/20 封顶，首签发放）
  - [x] 找回密码（authorization `/api/auth/reset-password` → Feign → user `/internal/user/reset-password`，短信码校验 + BCrypt 重写）
- **V1.1 待完善**:
  - [x] SM2 加密传输（高安全模式）—— Sm2Utils（hutool，@ConditionalOnProperty sm2.enabled=true, 默认关闭）

**BDD 验收场景**:

```gherkin
Scenario: 获取个人信息
  Given 持有有效 JWT
  When GET /api/user/info
  Then 返回昵称、头像、手机号（脱敏）、余额

Scenario: 新增收货地址
  Given 持有有效 JWT
  When POST /api/user/address with {receiver, phone, province...}
  Then 返回新地址 ID
  And 若 isDefault=1，其他地址取消默认

Scenario: 每日签到
  Given 今天尚未签到
  When POST /api/user/sign
  Then 返回签到成功 + 获得积分数
  And 连续签到 N 天，积分递增

Scenario: 重复签到的幂等保护
  Given 今天已签到
  When POST /api/user/sign
  Then 返回 "今日已签到"

Scenario: 接口限流
  Given 同一 IP 1 秒内请求获取验证码接口超过 3 次
  When 第 4 次 POST /api/user/sms-code
  Then 返回 429 "请求过于频繁"
```

---

### Phase 2: 商品域

#### T2.1 item-service

- **状态**: ✅ 已完成
- **已完成**:
  - [x] 类目 CRUD + 多级分类递归树
  - [x] 商品发布/编辑/上下架 (商家 + 管理员)
  - [x] 商品列表 (分页 + 销量/价格排序 + 类目/关键词筛选 + isAd 广告优先)
  - [x] 商品详情 (含 SKU 列表 + 价格/库存/规格)
  - [x] SKU 库存管理（乐观锁 @Version 扣减/恢复）
  - [x] 内部 Feign 接口（批量查 SKU / 批量扣库存 / 批量恢复库存）
  - [x] 商品评价（一单一评，POST /api/item/review + 按商品查询）
  - [x] 首页装修配置（banner/hot/new/sale slots，Admin CRUD + 公开查询）

**BDD 验收场景**:

```gherkin
Scenario: 商家发布商品
  Given 商家已登录
  When POST /api/item/product with {name, categoryId, mainImage, description, skus[]}
  Then 创建商品 (SPU) 及关联 SKU
  And 商品初始状态为"下架"

Scenario: 用户浏览商品列表
  Given 无需登录
  When GET /api/item/product/page?page=1&size=20&sort=soldCount,desc&categoryId=1
  Then 返回分页商品列表（仅上架商品）
  And 按销量降序排列

Scenario: 查看商品详情
  When GET /api/item/product/{id}
  Then 返回商品基本信息 + SKU 列表（含价格/库存/规格）
  And 只返回可售状态 SKU
```

#### T2.2 search-service

- **状态**: ✅ 已完成（V1.1 ES+DB 双模式）
- **已完成**:
  - [x] ES 全文检索（ElasticsearchOperations）
  - [x] DB LIKE 降级方案（@ConditionalOnProperty 自动切换）
  - [x] GET /api/search/product（关键词+类目筛选+分页）
  - [x] GET /api/search/hot（热门关键词）
  - [x] POST /internal/search/reindex（索引重建）
- **依赖**: T2.1

---

### Phase 3: 交易域

#### T3.1 cart-service

- **状态**: ✅ 已完成
- **已完成**:
  - [x] 添加商品（SKU 去重合并，默认选中）
  - [x] 数量修改（≤0 自动删除）
  - [x] 选中/取消切换
  - [x] 清空购物车 + 获取选中项
  - [x] 总价/选中金额计算
  - [x] 内部接口（获取选中项 / 清空购物车）

#### T3.2 trade-service

- **状态**: ✅ 已完成
- **已完成**:
  - [x] OrderManager Saga 编排（cart→SKU→address→stock→order）
  - [x] 创建订单（库存扣减 + 地址/商品快照 + 清购物车）
  - [x] 下单优惠券抵扣（OrderManager→CouponClient，payAmount=total-discount；失败/取消/超时按订单号回滚券）
  - [x] 取消订单（库存回滚 + 优惠券回滚）
  - [x] 确认收货（→已完成 + 积分奖励）
  - [x] 商家发货（→Feign 创建物流记录）
  - [x] 超时取消（@Scheduled + @XxlJob 双模）
  - [x] 商家仪表盘 + 管理员仪表盘/订单管理
  - [x] 售后（退货/退款申请 + 管理员审核）
  - [x] 商家入驻（申请 + 管理员审核）
  - [x] 财务结算（结算单生成 + 提现申请/审核）

#### T3.3 pay-service

- **状态**: ✅ 已完成
- **已完成**:
  - [x] 微信支付 API v3（Native 扫码）
  - [x] 支付回调（签名验证 + 幂等处理）
  - [x] 支付超时（@Scheduled + @XxlJob 双模）
  - [x] 支付状态查询
  - [x] 支付单幂等校验（数据库唯一约束 + 状态机）

#### T3.4 logistics-service

- **状态**: ✅ 已完成
- **已完成**:
  - [x] 物流记录创建（trade-service → Feign 调用）
  - [x] 物流状态追踪（PENDING→SHIPPED→DELIVERED→RETURNED）
  - [x] GET /api/logistics/{orderId}
  - [x] PUT /internal/logistics/{orderId}/status（内部状态更新）

---

### Phase 4: 后台管理

#### T4.1 商家后台

- **状态**: ✅ 后端完成
- **已完成**:
  - [x] 商家仪表盘（今日订单/待发货/月收入）
  - [x] 商品管理（发布/编辑/上下架/列表）
  - [x] 订单管理（列表+详情+发货）
  - [x] 店铺设置（名称/Logo/联系电话/地址）
  - [x] 店铺优惠券创建和管理
  - [x] 财务中心（结算单 + 提现）
  - [x] 入住申请（提交 + 审核状态查询）

#### T4.2 管理员后台

- **状态**: ✅ 后端完成
- **已完成**:
  - [x] 用户管理（分页+禁用/启用+角色修改）
  - [x] 商家管理（待审核列表 + 审核通过）
  - [x] 商品管理（查看所有 + 上下架）
  - [x] 订单管理（查看所有 + 状态筛选）
  - [x] 基础数据统计（用户数+订单数+收入 + 30天趋势）
  - [x] 优惠券管理（创建+下线）
  - [x] 首页装修（banner/hot/new 配置 CRUD）
  - [x] 提现审核
  - [x] 操作日志查询

---

## 三、V1.1 详细任务 — 基础设施 + 业务扩展

### T1.1-V1.1 authorization-service 补充

- [x] SMS SDK 集成（Aliyun dysmsapi20170525 3.1.0，@ConditionalOnProperty 可切换 Mock）

### T1.2-V1.1 user-service 补充

- [x] 签到积分递增加速策略（Day1=10pts, Day2=15pts, Day3+=20pts）
- [x] Aliyun SMS SDK 真实发送（@ConditionalOnProperty 可切换）
- [x] 积分系统（points_log 表 + PointsService + 签到/下单积分奖励）
- [x] 商品收藏（favorite 表 + FavoriteController CRUD）
- [x] 浏览历史（Redis List + HistoryService + HistoryController）

### T2.1-V1.1 item-service 补充

- [x] 多级分类递归树（一次查询全量 → 按 parentId 分组 → 递归填充 children）
- [x] 商品评价（review 表 + ReviewController，一单一评）
- [x] 广告商品优先排序（ProductServiceImpl: isAd DESC）

### T2.2-V1.1 search-service 上线

- [x] ES 全文检索（ElasticsearchOperations + CriteriaQuery）
- [x] DB LIKE 降级（DbSearchServiceImpl，@Primary，matchIfMissing=true）
- [x] 索引重建接口（POST /internal/search/reindex）
- [x] Gateway 路由 + Security 公开路径

### T5-V1.1 基础设施集成

- [x] T5.1 Docker Compose 增强（+ES/RabbitMQ/XXL-Job/MinIO profile）
- [x] T5.2 XXL-Job 集成（XxlJobConfig + @XxlJob + @Scheduled 双模）
- [x] T5.6 RabbitMQ 集成（DomainEvent 4 类 + RabbitMqConfig + EventPublisher + 消费者）
- [x] T5.7 Sentinel 集成（Gateway + Trade 限流熔断规则）
- [x] T5.8 MinIO 集成（StorageClient + MinioStorageClient + MockStorageClient）
- [x] T5.9 GitHub Actions CI/CD（ci.yml: build → test → package）

---

## 四、V1.2 详细任务 — 运营工具 + AI助手

### T1.2-V1.2 WebSocket 实时推送

- [x] WebSocketConfig + WebSocketNotifyController
- [x] OrderStatusChangedEvent 领域事件
- [x] @ConditionalOnProperty(websocket.enabled=true)

### T2.1-V1.2 首页装修

- [x] home_config 表（slot/banner/hot/new/sale）
- [x] HomeConfigService + HomeController（公开 GET /api/home/config）
- [x] AdminHomeController（管理后台 CRUD）

### T3.2-V1.2 秒杀活动

- [x] flash_sale 表（乐观锁 @Version）
- [x] FlashSaleService（秒杀列表 + 抢购 stock 扣减）
- [x] FlashSaleController（GET /api/flash + POST /api/flash/buy）

### T3.2-V1.2 数据统计增强

- [x] AdminController: GET /api/trade/admin/stats/trend（30天订单趋势）

### T3.2-V1.2 操作日志

- [x] operation_log 表 + @OperationLog 注解 + OperationLogAspect（AOP 自动记录）
- [x] AdminLogController: GET /api/admin/log

### T6-V1.2 AI商品助手

- [x] ai-service 模块创建（13th module，Spring AI 1.0.0-M5 + LangChain4j 1.0.0-beta1）
- [x] ShoppingAssistant（ReAct Agent: LLM→搜索→汇总）
- [x] CustomerServiceAssistant（FAQ + 订单查询 + 转人工）
- [x] @Tool 注解封装（SearchTool → search-service，OrderLookupTool → trade-service）
- [x] MessageWindowChatMemory（10-20 轮多轮对话）
- [x] DeepSeek V3 API 对接（OpenAI 兼容协议，api.deepseek.com）
- [x] @ConditionalOnProperty(ai.enabled=true) 可插拔，默认关闭

---

## 五、V2.0 详细任务 — 平台化

### T3.2-V2.0 商家入驻流程

- [x] seller_application 表 + SellerApplicationEntity/Mapper
- [x] SellerApplicationController（POST /api/seller/apply + GET 查询）
- [x] AdminApplicationController（审核列表 + 通过/拒绝）

### T3.2-V2.0 售后系统

- [x] after_sale 表（退货退款/仅退款，状态流转）
- [x] AfterSaleController（POST /api/after-sale + GET 列表）
- [x] AdminAfterSaleController（审核 + 退款处理）

### T6-V2.0 AI智能客服

- [x] CustomerServiceAgent（FAQ 知识库上下文 + 订单号正则提取）
- [x] CustomerServiceController（POST /api/ai/cs/chat）
- [x] 低置信度转人工模板

### T5-V2.0 Seata 分布式事务

- [x] spring-cloud-starter-alibaba-seata 依赖
- [x] SeataConfig: @ConditionalOnProperty(seata.enabled=true)
- [x] 降级方案：手动 Saga 补偿保留

---

## 六、V2.1-V2.2 详细任务 — 完善与治理

### T3.2-V2.1 商家财务结算

- [x] settlement 表 + SettlementService（平台抽成 5%）
- [x] withdrawal 表 + 提现申请/审核
- [x] SellerFinanceController（GET balance + settlement/{id} + POST withdrawal）
- [x] AdminFinanceController（POST generate + PUT review）

### T0-V2.1 多语言 i18n

- [x] I18nConfig（ResourceBundleMessageSource + AcceptHeaderLocaleResolver）
- [x] messages_zh_CN.properties + messages_en_US.properties
- [x] I18nUtils（编程式获取多语言消息）

### T0-V2.2 架构治理

- [x] Gateway 路由修复：8 条死链路 → admin/seller 按服务拆分
- [x] Controller→Service 抽取：HistoryService / HomeConfigService / FlashSaleService
- [x] UserServiceImpl 拆分：447行 → AuthService(95) + SmsService(70) + UserService(38)
- [x] marketing-service 模块拆分：优惠券+秒杀从 trade-service 独立
- [x] 02-Architecture.md §9-10：架构合规审计 + 阿里标准对照
- [x] SPI 修正：ia-common AutoConfiguration.imports 补全 I18nConfig/XxlJobConfig/RabbitMqConfig
- [x] 生产级 Docker Compose：资源限制/健康检查/日志轮转/持久化/网络隔离

---

## 七、未来阶段路线图

### V1.1 — 用户体验优化

- 接入小程序端（复用现有 API）
- 商品搜索 (ElasticSearch 上线)
- 优惠券（平台发放）
- 用户积分系统（签到、下单得积分）

### V1.2 — 运营工具 + AI商品助手

- 首页装修后台配置
- 秒杀活动
- 商家营销工具（店铺优惠券）
- 数据统计增强
- **AI商品助手**：自然语言搜索、多条件对比、多轮推荐对话（详见 13-AI-Technology-Selection §6）

### V2.0 — 平台化 + AI客服

- 商家入驻流程（在线申请、审核）
- 商家财务结算
- **AI智能客服系统**：FAQ问答、订单查询、人机转接、商家知识库管理（详见 13-AI-Technology-Selection §6）
- 多语言、多币种

### V2.1 — 商家财务 + 国际化

- **商家财务结算**：结算单生成（平台抽成5%）、提现申请/审核、可提现余额查询
- **多语言支持 (i18n)**：Spring MessageSource + Accept-Language 自动切换，zh_CN/en_US 双语言

### V2.2 — 架构治理

- **Gateway 路由修复**：admin/seller 路由按服务拆分，精确路径优先匹配，消除 8 条死链路
- **Controller→Service 抽取**：HistoryService / HomeConfigService / FlashSaleService，Controller 不再直接操作 Mapper/Redis
- **UserServiceImpl 拆分**：447 行拆为 AuthService + SmsService + UserService（SRP 单一职责）
- **`02-Architecture.md §9`**：架构合规审计报告，综合评分 9.2/10

### V2.3 — 支付补齐 + V1.1 遗留缺口回填

- **DDD 分层治理**（Tier 1）：Entity→VO、Service 直连 Feign 上移 Manager（OrderManager/PayManager/AdminManager/SignManager）、Controller 去 Mapper/JdbcTemplate、魔法值→枚举
- **真实 Bug 修复**：AddressClient Feign 路径（下单打通）、秒杀超卖原子化、IdType 对齐
- **V1.1 遗留缺口**：下单优惠券抵扣（CouponClient + Saga 回滚）、找回密码（短信码重置）、签到积分递增（10/15/20）、微信 OAuth 登录（Mock）
- **支付补齐**：支付渠道选择（`?channel=WECHAT|ALIPAY|BALANCE`）、支付宝(Mock)、余额支付 + 简易充值（原子扣减，余额不足拒付）
- 测试新增 OrderManager/PayManager/SignManager/Balance/Alipay 等单测与 H2 集成测试

### V3.0+ — 延期 backlog（需求评审移出当前范围）

> 以下功能经需求评审明确**移出 V2.x，延期至 V3.0+ 大版本**，当前不实现：
> **RBAC 资源级权限管理、积分商城、任务中心、店铺装修、多币种**（PRD 曾标 🔵，但无对应任务，评估工作量大且非当前交易闭环所需）。

### V3.0 — 知识驱动

- **知识图谱引擎**：商品实体关系抽取、图谱构建、多跳推理（详见 13-AI-Technology-Selection §6 Phase 4）
- RAG + KG 混合检索：模糊语义匹配 + 精确关系推理双引擎
- 场景化搭配推荐：基于知识图谱的"主商品→配件→兼容性校验"推荐链路
- 知识图谱可视化后台：运营/商家查看和修正实体关系
- **（并入）延期 backlog**：RBAC、积分商城、任务中心、店铺装修、多币种

---

## 八、当前进度摘要

### 版本完成状态

| 版本          | 完成度     | 模块  | 端点  | 测试  | 关键产出                             |
| ----------- | ------- | --- | --- | --- | -------------------------------- |
| V1.0 MVP    | 🟢 100% | 13 | 62  | 77  | 核心交易闭环 |
| V1.1 基础+业务  | 🟢 100% | 13 | 78  | 82  | XXL-Job, RabbitMQ, ES搜索, 积分, 优惠券 |
| V1.2 运营+AI  | 🟢 100% | 14 | 97  | 82  | 秒杀, 首页装修, WebSocket, ai-service |
| V2.0 平台化    | 🟢 100% | 14 | 106 | 82  | 商家入驻, 售后, AI客服, Seata |
| V2.1 财务+国际化 | 🟢 100% | 14 | 115 | 82  | 财务结算, 多语言 |
| V2.2 架构治理   | 🟢 100% | 14 | 115+ | 89  | 路由修复, 模块拆分, 合规 9.4/10 |
| V2.3 支付补齐   | 🟢 100% | 14 | 125+ | 130+ | DDD分层, 优惠券抵扣/找回密码/签到积分/微信OAuth, 支付宝(Mock)/余额支付/充值 |
| V2.4 观测+测试   | 🟢 100% | 14 | 127+ | 140+ | JaCoCo 覆盖率, 补 H2 集成测试(search/cart), Actuator/Prometheus 指标, TraceId 日志, 观测栈(SkyWalking/Grafana/ELK)配置就绪 |
| V2.5 安全+CI+功能 | 🟢 100% | 14 | 133+ | 150+ | 安全加固(SM2/JWT轮换/keystore-git/PII/空闲超时/RabbitMQ TLS/MinIO presigned)、CI/CD Stage2/3+Checkstyle、登录奖励/管理员统计/商家分析 |
| Phase 5 前端  | 🔵 进行中   | —   | —   | —   | React 19 + Next.js 15 + Tailwind + Shadcn/ui（storefront）/ Ant Design 5（admin·seller），详见 15-Front-End-Technology-Selection |
| V3.0 知识驱动   | ⚪ 0%    | —   | —   | —   | 知识图谱 + RAG+KG（并入延期 backlog：RBAC/积分商城/任务中心/店铺装修/多币种） |

**已实现的核心链路**: 注册/登录 → 浏览商品 → 加入购物车 → 下单（库存扣减+地址快照+商品快照）→ 微信支付 → 商家发货 → 确认收货 → 售后 → 财务结算。

---

## 九、V1.1 基础设施补全任务（全部已完成 ✅）

> 这些任务是 MVP 代码完成后、投入生产前的关键基础设施。每一项都有明确的「为什么需要」和「不做的风险」。

### T5.1 Docker Compose 本地开发环境

- **描述**: 编写 `docker-compose.yml`，一键启动 MySQL + Redis + Nacos
- **为什么需要**: 当前每个开发者手动搭建这三个依赖，耗时且容易版本不一致
- **优先级**: 🔴 P0
- **依赖**: T0.1
- **验收**: `docker compose up -d` 后所有服务可正常启动

### T5.2 XXL-Job 分布式任务调度

- **描述**: 部署 XXL-Job 调度中心，迁移 `PayTimeoutJob`、`OrderTimeoutJob` 从 `@Scheduled` 到 XXL-Job 执行器
- **为什么需要**: `@Scheduled` 在多实例部署时会重复执行，导致重复退款/重复取消订单
- **优先级**: 🔴 P0（多实例部署前必须完成）
- **依赖**: T0.5 (Nacos)
- **验收**: 3 个实例部署，同一订单只被一个实例处理

### T5.3 SkyWalking 分布式链路追踪

- **描述**: 部署 SkyWalking OAP + UI，所有服务接入 Java Agent
- **为什么需要**: 11 个微服务无追踪，排查一次跨服务异常需要 grep 所有服务日志，效率极低
- **优先级**: 🟡 P1
- **依赖**: T0.5
- **状态**: 🟢 配置就绪（V2.4）—— docker-compose `observability` profile 含 OAP(存储接 ES)+UI；日志 TraceId 已接入（TraceIdFilter + logback %tid 占位）；Agent 以 `-javaagent` 附加（见 14-Service-Config-Guide）
- **验收**: SkyWalking UI 能看到完整调用链拓扑和 Trace 详情（需运行栈 + 附加 Agent）

### T5.4 Prometheus + Grafana 监控

- **描述**: 部署 Prometheus 采集 JVM/接口指标，Grafana 仪表盘展示
- **为什么需要**: 不知道服务 QPS、RT、错误率，故障发现靠用户投诉
- **优先级**: 🟡 P1
- **依赖**: T0.5
- **状态**: 🟢 配置就绪（V2.4）—— 各服务已加 actuator + micrometer-prometheus 暴露 `/actuator/prometheus`；docker-compose 含 Prometheus(`docker/prometheus/prometheus.yml`)+Grafana(datasource 自动配置)
- **验收**: Grafana 仪表盘显示各服务 QPS、P99 延迟、错误率（需运行栈）

### T5.5 ELK 日志平台

- **描述**: 部署 ElasticSearch + Logstash + Kibana，统一日志采集和查询
- **为什么需要**: 查日志需要登录多台机器，无法按 TraceId 串联
- **优先级**: 🟡 P1
- **依赖**: T5.3 (TraceId 需要 SkyWalking)
- **状态**: 🟢 配置就绪（V2.4）—— docker-compose 含 Logstash(`docker/logstash/logstash.conf` grok 解析 traceId)+Kibana(接现有 ES)；应用侧日志已带 `[tid:...]`，接入 Filebeat/TCP 即可入库
- **验收**: Kibana 中可按 traceId 查看一次请求的完整日志（需运行栈 + 日志转发）

### T5.6 RabbitMQ 消息队列

- **描述**: 部署 RabbitMQ，将领域事件（订单创建/支付成功/发货）从 Feign 同步改为消息异步
- **为什么需要**: 订单创建流程当前串行 Feign 调用 3 个服务（Sku/Cart/Address），同步耦合严重；支付成功后的物流/通知逻辑硬编码在 pay-service
- **优先级**: 🟡 P1
- **依赖**: T0.5
- **验收**: 订单创建事件发布后，pay-service 异步消费并创建支付单

### T5.7 Sentinel 限流熔断

- **描述**: 配置 Sentinel Dashboard，对网关和核心接口配置限流规则和熔断降级策略
- **为什么需要**: `@RateLimit` 只实现了单接口限流，缺少熔断降级（如支付服务挂了，订单服务应有 fallback）
- **优先级**: 🟡 P1
- **依赖**: T0.5
- **验收**: 模拟 item-service 宕机，trade-service 触发熔断，返回降级响应而非 500

### T5.8 MinIO 对象存储

- **描述**: 部署 MinIO，商品图片/用户头像上传功能
- **为什么需要**: 当前商品/用户无图片上传能力，只能使用外部 URL
- **优先级**: 🟢 P2
- **依赖**: T2.1 (item-service)
- **验收**: 商品发布时可上传主图，返回可访问的图片 URL

### T5.9 GitHub Actions CI/CD

- **描述**: 编写 GitHub Actions workflow，实现 push 自动编译+测试+镜像构建
- **为什么需要**: 当前全靠手动 `mvn install`，容易漏测
- **优先级**: 🟡 P1
- **依赖**: T5.1 (Docker Compose 提供测试依赖)
- **验收**: PR 自动触发 CI，失败时阻止合并
