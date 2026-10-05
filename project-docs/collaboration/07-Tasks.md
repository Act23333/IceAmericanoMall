# 07 — 任务分解文档

> 来源：`doc/项目的分析流程/IcedAmericanoMall的MVP.md`、`doc/IDEA.md`、`doc/项目的分析流程/01.用户需求捕获.md`
> 最后更新: 2026-10-03（§八 追加遗留风险清零批次交付记录：WS 握手鉴权/黑名单写入/Sentinel 修复/渠道回调协议/RAG 接线/审计双管道/种子脚本重写/契约收口，ADR-026~029 + ADR-032；§七 V4.0 DDD 架构升级块补状态标注；§八 追加工具类收口批次交付记录：账号掩码归并/网关 IP 解析器更名消歧/MetaObjectHandler 8→1/SecurityHeaders 常量化/PageDTO 删除，ADR-034 + ADR-030）
> 归属域: collaboration | Owner: doc-engineer | 更新触发条件: 任务状态变化时

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
  Then 返回昵称、头像、手机号（脱敏）（余额经 `GET /api/wallet` 单独获取）

Scenario: 新增收货地址
  Given 持有有效 JWT
  When POST /api/user/address with {receiver, phone, province...}
  Then 返回新地址 ID
  And 若 isDefault=1，其他地址取消默认

Scenario: 每日签到
  Given 今天尚未签到
  When POST /api/sign
  Then 返回签到成功 + 获得积分数
  And 连续签到 N 天，积分递增

Scenario: 重复签到的幂等保护
  Given 今天已签到
  When POST /api/sign
  Then 返回 "今日已签到"

Scenario: 接口限流
  Given 同一 IP 1 秒内请求获取验证码接口超过 3 次
  When 第 4 次 POST /api/verification/codes
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
- [x] T5.6 RabbitMQ 集成（DomainEvent 4 类 + RabbitMqConfig + EventPublisher + 消费者）→ **V4.0 起 RocketMQ 全面替代，RabbitMqConfig 已移除**（现役事件类：`OrderCreatedEvent` / `OrderShippedEvent`，见 03-Domain-Model §八）
- [x] T5.7 Sentinel 集成（Gateway + Trade 限流熔断规则）
- [x] T5.8 MinIO 集成（StorageClient + MinioStorageClient + MockStorageClient）
- [x] T5.9 GitHub Actions CI/CD（ci.yml: build → test → package）

---

## 四、V1.2 详细任务 — 运营工具 + AI助手

### T1.2-V1.2 WebSocket 实时推送 🗑️ 已删除（2026-10）

> gateway-service 的 `WebSocketConfig` + `WebSocketNotifyController` 与 `OrderStatusChangedEvent` 领域事件已删除（能力未采用）。实时通道现状：IM 由 ai-service STOMP `/ws/chat` 承载；站内信由 notification-service 落库触达。

- [x] ~~WebSocketConfig + WebSocketNotifyController~~ → 已删除（gateway-service）
- [x] ~~OrderStatusChangedEvent 领域事件~~ → 已删除（全仓无该类）
- [x] ~~@ConditionalOnProperty(websocket.enabled=true)~~ → 开关已移除

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

### T3.2-V1.2 操作日志 📌 AOP 写入侧已删除（2026-10）

> `@OperationLog` 注解 + `OperationLogAspect` 已删除（**能力未采用**：`operation_log` 表已无写入方）。表保留（deprecated 历史数据），`GET /api/admin/log` 查询端点保留可读历史。

- [x] operation_log 表（保留，无新增写入）
- [x] ~~@OperationLog 注解 + OperationLogAspect（AOP 自动记录）~~ → 已删除
- [x] AdminLogController: GET /api/admin/log（保留，读历史）

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

### T3.2-V2.0 商家入驻流程 🗑️ 已退役（2026-10 第六轮边界重构）

> trade-service 平行入驻流程整体退役删除（SellerApplicationController / AdminApplicationController / ApplicationService / SellerApplicationEntity / Mapper），原因：与商家域入驻流程平行重复，且网关 `/api/seller/**`、`/api/admin/**` 均路由不到 trade（早已不可达）。`seller_application` 表保留在库作数据备份；现行入驻/审核归 merchant-service（`POST /api/seller/apply`、`GET /api/admin/seller/pending`、`PUT /api/admin/seller/{id}/approve`，审批自动授予 ROLE_SELLER）。

- [x] seller_application 表 + SellerApplicationEntity/Mapper（已退役，表保留）
- [x] SellerApplicationController（POST /api/seller/apply + GET 查询）（已退役）
- [x] AdminApplicationController（审核列表 + 通过/拒绝）（已退役）

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

### V2.5 — AI 生产就绪 🔵 当前阶段

> 目标：修复 V2.0 AI MVP 的 7 个已知缺陷，将 AI 功能从"实验演示"提升到"可安全上线"。详见 [13-AI-Technology-Selection.md](../backend/13-AI-Technology-Selection.md) §11.1。

**优先级 P0（阻塞上线）：**
- [ ] **会话隔离** — Redis `ChatMemoryStore` 实现 Per-User 隔离，修复全局 `MessageWindowChatMemory` 跨用户泄漏

**优先级 P1（核心能力补齐）：**
- [ ] **Feign 迁移** — `SearchTool` / `OrderLookupTool` 替换 `new RestTemplate()` 为 ia-api FeignClient，走 Nacos 负载均衡 + `X-User-Id` 传播
- [ ] **SSE 流式响应** — WebFlux + LangChain4j `TokenStream` 实现打字机效果，新增 `POST /api/ai/chat/stream` 和 `POST /api/ai/cs/chat/stream`
- [ ] **ES 向量索引** — ES 8.x `dense_vector` 映射 + kNN 检索 + 商品 Embedding 写入，建立 RAG 基础管线

**优先级 P2（质量保障）：**
- [ ] **Langfuse 可观测性** — OpenTelemetry + Langfuse 双轨埋点：Token 消耗、Tool 调用链、延迟分解
- [ ] **基础测试** — `SearchTool` / `OrderLookupTool` 单元测试 + Agent 集成测试（录制回放 LLM 响应）
- [ ] **内容安全** — 输入敏感词过滤 + System Prompt 加固 + 输出 PII 脱敏

### V3.0+ — 延期 backlog（需求评审移出当前范围）

> 以下功能经需求评审明确**移出 V2.x，延期至 V3.0+ 大版本**，当前不实现：
> **RBAC 资源级权限管理、积分商城、任务中心、店铺装修、多币种**（PRD 曾标 🔵，但无对应任务，评估工作量大且非当前交易闭环所需）。

### V3.4 — 店铺 AI ✅

> **大厂对标**: 阿里"店小蜜"、京东"商家智能客服" — AI 嵌入消息系统，仅检索本店知识库。
> 独立于 V3.3 消息基础设施，复用现有 `ai-service` RAG 管线 + `CustomerServiceAssistant`。

- **商家知识库管理**: FAQ/政策/产品手册上传 → `merchant_knowledge_base` CRUD + ES 索引（V2.5 延期，V3.4 补上）
- **商家 AI 自动代答**: 复用 `CustomerServiceAssistant` + `RAGRetriever`，限定 `seller_id` 过滤
- **商家对话模板**: `merchant_reply_template` CRUD — 欢迎语/离线回复/FAQ/快捷话术
- **知识库隔离**: RAG 检索时 WHERE `seller_id = :currentSeller`
- **人机转接通知**: AI 置信度 < 0.7 → WebSocket 通知商家 + 对话摘要

### V4.x — 组件收敛 ⚪

> **大厂对标**: 京东/阿里技术栈收敛运动。先加组件学习原理(V4.0)，再减组件理解为何可以不用(V4.x)。
> 详见 [02-Architecture.md §2.13](../backend/02-Architecture.md) 组件演进路线。

- **PostgreSQL pgvector 替代 ES 向量** (V4.1): PG 内置 pgvector HNSW → 去 ES 向量检索组件
- **PG tsvector 替代 ES 全文搜索** (V4.2): zhparser 中文分词 → 去 ES 全文搜索组件
- **KRaft 替代 Zookeeper** (V4.1): Kafka 3.7+ 内置 KRaft 共识 → 去 Zk 组件
- **MinIO 数据湖替代 Hadoop** (V4.2): Spark + MinIO → 去 Hadoop/HDFS 组件
- **事件驱动替代 Flink** (V4.2): Spring + RabbitMQ → 去 Flink 组件（保留为可选）
- **事务表补偿替代 Seata** (V4.2): Saga + MQ + 本地事务表 → 去 Seata 组件
- **PG 替代 MySQL** (V4.1): PG 完全兼容 MySQL 语法(MyBatis-Plus PG 方言) → 去 MySQL 组件

### V4.0 — DDD 架构升级 ✅ 已交付（2026-10 边界重构与 ADR-023/025 收口，部分口径被取代）

> **现状**: 仅 trade-service(3)/pay-service(2) 有 Manager 层。其余 7 模块采用三层(Controller→Service→Mapper)。 —— ✅ 已取代（现状已翻转为两种形态并存：四层 DDD 服务 `app/*Manager` 全量落地，trade 编排层为 `manager/`，轻量 COLA 服务保持 `app/`+经典目录）
> **目标**: 全模块统一四层 DDD (Controller→Manager→Service→Mapper)，符合 CLAUDE.md 规范。 —— ✅ 已取代（「全模块统一四层」经 ADR-025 修正为「两种形态并存定稿，轻量 COLA 服务不做跨形态迁移」）

- **user-service**: 31处Controller→Mapper/Redis → Manager层补齐 + UserManager/SignManager 拆分 —— ✅ 已交付（ADR-016/017 四层 DDD；UserManager 经 ADR-025 拆为 UserQuery/UserAdmin/UserProvisionManager；签到域于 2026-10 ③ 轮迁 marketing-service）
- **item-service**: ProductDetailController→Mapper → ProductService 封装 —— ✅ 已交付（轻量 COLA 收口：控制器归位 `adapter/web/ProductController` 等，`app/ReviewManager` 编排层落地）
- **ai-service**: 8处Controller→Redis/Mapper → AIManager 编排层 —— ✅ 已交付（`app/AiManager`；聊天/知识库 Manager 随 2026-10 ④ 轮并入客服域）
- **cart/auth/logistics/search/marketing**: 各模块 Manager 层新建 —— ✅ 已交付（cart `CartManager` / auth `AuthManager`+`app/rbac/*` / logistics `LogisticsManager` / marketing `MarketingManager`+`SignManager`）；其中 search-service —— ✅ 已取代（ADR-025 明确维持轻量形态，不建编排层）
- **Service→Feign**: 7模块 Feign调用上移到 Manager 层 —— ✅ 已交付（Feign 调用已收敛至编排层，并固化进 `09-constraints.md` 硬约束）

> **本计划已于 2026-10 边界重构与 ADR-023/025 落地取代，现状见 [02-Architecture §4](../backend/02-Architecture.md)。**

### V4.0 — 大数据全链路学习 🔵

> **大厂对标**: 京东/阿里完整大数据架构。学习目的——先加组件理解大厂全链路，后续 V4.x 再收敛精简。

- **PostgreSQL 16 + pgvector**: 第二数据库，pgvector HNSW 向量搜索、JSONB 文档、tsvector 全文
- **PgBouncer**: PG 连接池（PG fork 进程 5-10MB/连接 → 50连接池）
- **Caffeine**: JVM 进程内缓存（多级缓存第一层：Caffeine→Redis→DB）
- **Prometheus AlertManager**: 告警规则（订单异常/RT飙升/AI Token超量 → 通知）
- **Apache Kafka**: 事件流管道（用户行为埋点/订单事件溯源/实时数据流）
- **Apache Zookeeper**: Kafka 集群协调（Kafka 3.7+ 可选 KRaft 模式去 Zk）
- **Apache Flink**: 流计算（实时大屏秒级GMV/用户行为聚合/异常检测/CD数据同步）
- **Apache Hadoop (HDFS)**: 分布式存储（用户行为/订单日志/搜索日志离线存储）
- **Apache Spark**: 批量计算（离线报表/用户画像/推荐模型训练）
- **Apache Hive**: 数据仓库 SQL-on-Hadoop（离线数据查询）
- **docker-compose.yml**: 新增 v4.0 profile（11 个服务：PG/PgBouncer/Kafka/Zk/Flink×2/Hadoop×2/Spark×2/AlertManager）

### V3.7 — 秒杀漏斗模型+订单四层保障 ✅

> **大厂对标**: JD 秒杀漏斗（网关限流→Redis分片→MQ削峰→乐观锁）+ 淘宝订单四层保障（RocketMQ延迟→事务表→定时兜底→人工后台）。

- **漏斗第一层**: 网关 @RateLimit + Sentinel 热点限流（拦截95%无效请求）
- **漏斗第二层**: Redis 库存热点分片（拆10片，Lua随机选片，消除单key热点）
- **漏斗第三层**: RabbitMQ 削峰填谷（抢到库存→发MQ→消费者异步建单）
- **漏斗第四层**: @Version 乐观锁 + sold_count<stock 条件（数据库最终防线）
- **订单第一层**: RabbitMQ TTL 延迟消息（30min主链路）
- **订单第二层**: flash_order_log 本地事务表（幂等保障，retryCount max 3）
- **订单第三层**: XXL-Job 兜底扫描（每天凌晨扫近3天异常订单）
- **订单第四层**: 人工运营后台（手动处理异常订单）
- **中间件**: docker-compose 新增 flash TTL 队列+DLX声明

### V3.6 — 秒杀+订单超时大厂标准改造 🔵

> **大厂对标**: 京东秒杀 Redis Lua 预扣 + 阿里 RocketMQ 延迟消息取消订单。
> 消除 @Scheduled 60s 轮询 CPU 浪费 + 串行 Feign 调用 O(N) 复杂度。

- **秒杀 Redis Lua 预扣**: 原子 DECR + 用户限购 + 库存预热, P99<10ms
- **秒杀异步持久化**: RabbitMQ → 消费者写 MySQL, 削峰填谷
- **订单 TTL 死信队列**: 创建订单时发 RabbitMQ 消息(TTL=30min) → 自动过期 → 死信消费者取消
- **订单取消幂等**: closeTimeoutOrder 增加状态检查, 防止重复回库存
- **批量回库存**: 取消时一次性调 SkuClient.restoreStock, 1 次 Feign 代替 N 次
- **删除 @Scheduled 轮询**: OrderTimeoutJob 移除 60s 定时扫描, 改为纯事件驱动

### V3.5 — 商品详情页京东标准改造 🔵

> **大厂对标**: 京东详情页（多图+规格参数+SKU维度选择+店铺卡片+到手价+立即购买+评论晒图+关注店铺）。

- **商品卡片增强**: ProductSearchVO 增加 shopName/shopLogo/ratingSold/viewCount 字段
- **详情页数据聚合**: `GET /api/item/product/{id}/detail` 一次返回(商品+店铺+SKU+参数+评论摘要+优惠券)
- **多图+视频**: product 表增加 images(JSON) + video_url
- **规格参数表**: product 表 attributes(JSON) → 京东标准 K-V 参数表
- **结构化SKU**: `sku_spec_dimension`/`sku_spec_option` 表 + spec_json 替换 spec 字符串
- **原价/到手价**: sku 增加 `original_price`，到手价=price-original_price
- **立即购买**: `POST /api/trade/order/direct { skuId, quantity, addressId }`
- **关注商家**: Redis Set 关注关系（`shop:followers:{sellerId}` / `user:follow:shops:{userId}`，2026-10 第六轮归 merchant-service；无 MySQL 表）+ `/api/shop/follow/{sellerId}`
- **评论增强**: media_urls(JSON)替代 images 字符串，增加 helpful_count，筛选端点
- **评论摘要**: 均分+星级分布+总评数，`GET /api/item/review/product/{id}/summary`
- **购物车分组**: `GET /api/cart/grouped` 按 seller 分组
- **店铺公开页**: `GET /api/shop/{sellerId}` + `GET /api/shop/{sellerId}/products`

### V3.3 — 买家-商家消息系统 ✅

> **大厂对标**: 淘宝旺旺（独立消息系统）、京东咚咚。**消息系统是基础设施，AI 是嵌入式辅助能力。**
> V3.3 先建消息系统（WebSocket + 消息表 + 离线推送），V3.4 再嵌入店铺 AI。

- [ ] WebSocket STOMP 端点（gateway-service 统一入口）
- [ ] `chat_message` 表持久化 + Redis 最近 100 条热缓存
- [ ] 买家发起对话（商品详情页/订单列表页）
- [ ] 消息已读/未读 + 实时未读计数
- [ ] RabbitMQ 离线消息推送
- [ ] 对话列表 API（分页查询历史）

### V3.2 — 现代化 + DDD 重构 ✅

- **RestTemplate → RestClient**: ia-common 全局 RestClient Bean (Spring Boot 3.2+标准), EmbeddingService/RAGRetriever/RerankerService 全部替换
- **DTO → Java Record**: 10 个 DTO 转为不可变 Record (ai-service 5个 + ia-api 5个)
- **UserProfileService DDD 分层**: Controller→DomainService→Repository, PATCH 语义 + updateMask 白名单
- **@Version 乐观锁**: user 表 version 列 + MyBatis-Plus 防并发覆盖
- **MinIO 头像管理**: AvatarController 上传(Thumbnailator 3尺寸) + 下载(?width=200)

### V3.1 — RBAC 授权体系 ✅

- **@PreAuthorize 方法级授权**：`@PreAuthorize("@ss.hasPermi('user:admin')")` + PermissionService（大厂标准）
- **UserContext→SecurityContext 桥接**：UserContextAuthenticationFilter 自动注入 GrantedAuthority
- **管理员角色/权限 CRUD**：RoleController + PermissionController REST API
- **Admin端点权限保护**：4 个 admin controller 已加 `@PreAuthorize`
- **待完成**：Seller 端点角色细化、数据级权限（行级）、前端权限 UI 条件渲染

### V3.0 — 智能升级 🔵 当前阶段

- **AI 完整 RAG 管线**：BGE-reranker 部署 + 多路召回（向量+关键词+结构化）+ RRF融合 + Query Rewrite + 上下文组装
- **多模型路由**：DeepSeek (80%) + Qwen (20%) 混合路由 + 故障转移 + A/B 测试框架
- **主-子Agent 编排**：Master Agent(Qwen-Max) → Search/Compare/Recommend/Order/FAQ 专业 Subagent(DeepSeek) → Synthesizer 汇总
- **AI Gateway 增强**：Spring Cloud Gateway GlobalFilter — Token 配额、语义缓存、成本归因
- **离线评测体系**：标注数据集 + LLM-as-Judge + Langfuse 评估报告
- **商家知识库管理**：FAQ/产品手册上传 → 自动切分 → ES 索引
- **人工转接系统**：置信度评分(<0.7触发) + 对话摘要 + 工单创建
- **（并入）延期 backlog**：RBAC、积分商城、任务中心、店铺装修、多币种

### V3.x — 高级 AI（知识图谱 + 多模态）⚪

> **知识图谱已从 V3.0 移出**。触发条件（SKU>10万 / 关系查询>20%流量 / RAG 关系召回率<80%）均未满足。
> 详见 [13-AI-Technology-Selection.md §10.1](../backend/13-AI-Technology-Selection.md) 触发条件量化分析。

- **知识图谱引擎**：NebulaGraph 部署 + 实体/关系抽取 + RAG + KG 混合检索（触发条件达标后启动）
- **多模态搜索**：VLM 微调 (Qwen2-VL 7B) + 以图搜商品
- **MCP 协议集成**：Spring AI Alibaba MCP Gateway → 零代码暴露已有服务为 MCP Tool
- **自建 Embedding**：bge-large-zh GPU 服务替代 API（日调用 > 10万次触发）
- **领域模型微调**：电商对话数据 Fine-tune DeepSeek/Qwen

---

## 八、当前进度摘要

> **2026-10-03 交付记录（结构规范化 + 数据库整治 + 商家/店铺 1:N 拆分）**：①**结构规范化（T8）**——全部 15 个业务服务 + 3 个共享模块（共 18 Maven 模块）的 adapter 层按受众统一（`web`/`admin`/`internal` + `request`/`response`/`convert`，ADR-023），轻量 COLA 服务的 Web 契约类迁出 `domain/`（domain 包删除、实体顶层 `entity/`），并清理死代码（FieldPermission/OperationLog 两套 AOP、3 个零调用 Lua 脚本、gateway 废弃配置类）；②**数据库整治（T0）**——`Initialize.sql` 重建为全量最新快照（40 表）、28 个迁移重命名去重（12 个三段式版本号）、RBAC 种子幂等；③**商家/店铺 1:N 拆分（ADR-019）**——`seller` 瘦身为身份+生命周期、新增 `shop` 表、默认店铺兼容解析、网关 `/api/shops/**` 补挂。附带 P0/P1 修复：SKU 原子扣减（ADR-020）、购物车读时回填（ADR-021）、Feign 熔断扩至 8 服务（ADR-022）、分布式锁归并 ia-common（ADR-024）、网关限流/黑名单/白名单方法级收紧、`/internal/**` 异常返回真实 HTTP 状态码（ADR-004 修订）。全仓 590 项测试 0 失败。

> **2026-10-03 交付记录（方案 2 结构收口，[ADR-025](../adr/025-domain-aggregate-packaging-and-app-usecase-granularity.md)）**：四层 DDD 服务 `domain/model` 按聚合分包（authorization `userauth`+`rbac`、user `user`/`address`/`favorite`、merchant `merchant`/`shop`、notification `inapp`+`channel`；单聚合平铺、跨族共享值对象留根、聚合私有枚举下沉——例外条款见 ADR-025）+ `app` 层 Manager 按用例族收敛（user `UserManager` → `UserQueryManager`/`UserAdminManager`/`UserProvisionManager`；notification `NotificationManager` → `ChannelDispatchManager`/`InAppMessageManager`；marketing `CouponService` → `CouponClaimService`/`CouponUsageService`）。HTTP 契约零变化，590 项测试保持 0 失败。

> **2026-10-03 交付记录（遗留风险清零批次，[ADR-026](../adr/026-ws-handshake-query-token-edge-swap.md)~[ADR-029](../adr/029-channel-callback-protocol-over-result-envelope.md) + [ADR-032](../adr/032-operation-audit-dual-pipeline.md)）**：①**WS 握手鉴权（ADR-026）**——网关新增 `WsHandshakeTokenFilter`（WebFilter，order -900）：握手 query `token` 换写 `Authorization: Bearer` 并剔出参数（不落下游），`/ws/chat/**` 维持 `authenticated()`，实时客服通道恢复可用；②**jti 黑名单写侧（ADR-027）**——authorization-service 登出/重置密码写 `auth:blacklist:{jti}`（TTL=max(剩余有效期,1s)，fail-open；契约键 auth 写/网关只读），网关 `JwtBlacklistFilter` 读侧从死代码变为有效防线；③**trade Sentinel 接线修复（ADR-028）**——激活键对齐 `spring.cloud.sentinel.enabled`（此前写错导致规则静默不注册），`createOrder` flow QPS 100 + 异常比例 0.5 规则生效，blockHandler 抛 `FREQUENT_ERROR`(429)，Dashboard 保持默认关；④**渠道回调协议（ADR-029）**——微信 v3 裸 JSON（成功 200 + `SUCCESS`；失败 500 + `FAIL` 触发重推；幂等命中仍 SUCCESS）、支付宝裸串 `success`，契约面由 05 文档同步；⑤**RAG 接线（T14）**——`ai.rag.enabled` 渐进启用：知识库 CRUD → Feign 向量摄取（search-service 分块 + Embedding → 索引 `knowledge_vectors`）→ 客服应答 `RAGRetriever` topK 注入，全链路 fail-open（默认关闭=现状）；⑥**审计双管道（ADR-032）**——`@OperationLog` + 切面恢复（JSON→`AUDIT` logger，`AutoConfiguration.imports` 全服务装配；12 个管理端写端点已铺设注解）+ user 域 `operation_log` 显式落库恢复（`AdminLogController` 查询范围收窄为 user 域）；⑦**开发种子脚本重写（T12）**——`database/seed-data.js` 适配 V5.2/V5.4 schema（PII 三字段 + user_auth 凭证 + seller/shop），种子账号密码统一 `Dev123456`（替换不可登录占位；**未实跑**），`fix-comments.js` 已删除；⑧**契约收口**——`WithdrawalVO` 提现账号商家端脱敏/管理端明文（落 12-Security-Model §6.4）、`userCouponIds` 删除（多券待 PRD）、`AddressResp.updateTime` 删除、售后 type 1/2 校验。遗留验证项（按 ADR 记录）：登出后旧 access token 端到端 401 验证、RAG 全链路联调、seed 脚本实跑。

> **2026-10-03 交付记录（工具类收口批次，[ADR-034](../adr/034-cross-module-utility-consolidation-and-boundaries.md) + [ADR-030](../adr/030-metaobjecthandler-consolidation-autoconfig.md)）**：①**账号掩码归并（ADR-034）**——trade 本地 `SensitiveMaskUtil` 删除，账号掩码规则上提 ia-common `DesensitizeUtil.maskAccount`（≤4 全遮 / >4 保留末 4 位，规则由 ia-common 单测锁定；trade MapStruct 表达式改指本类），ai-service `ContentSafetyFilter` 手机号掩码改调 `DesensitizeUtil.maskPhone`（maskPhone/maskEmail 签名零改动）；②**网关 IP 解析器更名消歧（ADR-034）**——`ClientIpUtil` → `EdgePeerIpResolver`（边缘 TCP 对端口径，绝不信任 XFF 等代理头；与 ia-common `IpUtil`「下游信任边缘注入头」两处 Javadoc 双向互链、明示不可合并理由；`IpUtil` 代理头回落分支复核为**非死代码**——dev 直连/解析失败兜底仍命中，已注释保留）；③**MetaObjectHandler 8→1 收口（ADR-030）**——ia-common 新增 `AuditFieldMetaObjectHandler`（`MyMetaObjectHandlerAutoConfiguration` 自动装配：`@ConditionalOnClass` 守卫 + `@ConditionalOnMissingBean` 退避，注册 `AutoConfiguration.imports`），cart/item/logistics/merchant/pay/search/trade 七份副本删除；user-service 因额外填充 `registerTime` 保留自有实现（退避共存）；authorization/marketing/notification/wallet/ai 五服务首次获得应用侧真实填充（口径统一，非 HTTP 契约变化）；④**头名常量化（ADR-034）**——ia-common 新增 `SecurityHeaders`（14 个可信上下文头/代理头名称，含可信/可伪造/追踪三类信任边界注释），替换 ia-common/ia-api/gateway/ai-service 8 个文件的字面量（值逐字不变，HTTP 契约零变化）；⑤**`PageDTO` 删除**——ia-common 零调用类清理（与 notification `PageResult` 的层级差异登记见 ADR-034）。相关模块测试全绿（clean run 记录见 ADR-034/030）；HTTP 契约零变化。

> **2026-10-03 交付记录（多粒度令牌吊销，[ADR-031](../adr/031-multi-granularity-token-revocation.md)，修订 [ADR-027](../adr/027-jwt-blacklist-writer-and-contract-key.md)）**：①**tokenVer 用户级版本（T19）**——access token 新增 `ver` claim（登录与 refresh 换发走同一签发链路，均写入当前 `auth:tokenver:{userId}` 版本；键缺失 / 无 claim 均按 0，存量令牌零迁移）；②**网关合并校验**——`JwtBlacklistFilter`（order -700，类名保留）一次 MGET 合并 `auth:blacklist:{jti}` + `auth:tokenver:{userId}`：黑名单命中或版本不一致 → 401，版本键缺失跳过比对，fail-open 与「每请求一次 Redis 往返」口径不变；③**触发点重排**——登出=jti 黑名单（**不变**）；改密/重置密码=版本 INCR + 全端拆 refresh 会话（替换「拉黑当前请求令牌」）；管理端禁用=user-service `UserAdminManager` 经 `AuthContextClient.revokeUserTokens` → `POST /internal/auth/token-version/revoke`（版本 INCR + 全端拆会话；调用在本地事务之后、降级 fail-open）；④**契约键**——`auth:tokenver:{userId}` 与 `user:info:` / `user:perms:` / `auth:blacklist:` 同级豁免（auth 写 / 网关只读，**不设 TTL**——过期会复活已吊销令牌），豁免清单落 09-constraints §四。**ADR-027 两项残余风险（改密后其他设备存活、管理端禁用不拆会话）关闭**；相关模块测试全绿。遗留验证项（按 ADR-031 记录）：改密/禁用后旧 access token 端到端 401 未实机验证。

> **2026-10-03 交付记录（P0 四项资金/履约批次，[ADR-035](../adr/035-refund-fund-flow-and-refunded-activation.md)/[ADR-036](../adr/036-channel-reconciliation-record-only-and-rewrite-idempotency.md)/[ADR-037](../adr/037-freight-model-package-split-and-refund-trigger.md)）**：①**退款资金流（F1，ADR-035）**——售后审核同意触发三通道原路退回（BALANCE 退钱包+流水 / WECHAT v3 退款+异步回调 / ALIPAY mock 同步），`refund_order`（V5.5）`uk_after_sale` 幂等锚点、`refund_no` 贯穿渠道幂等键与钱包流水号，退款成功经 I42 条件回写激活 `REFUNDED(5)` 死状态；wallet-service 首次具备流水能力（`wallet_transaction`，幂等键 `transaction_no`，入账与流水同事务）；②**渠道对账（F4，ADR-036）**——日终账单比对四态差异（匹配/渠道多/本地多/金额不一致）只落库记录不调账，`uk_date_channel` + 先删后写幂等，日终 XXL-Job dev 默认关；③**运费模型（F2，ADR-037）**——`freight_template` + `product.freight_template_id` + `orders.post_fee`（V5.6），**金额不变式升级 V2：`payAmount = totalAmount − discountAmount + postFee`**（`totalAmount` 仍 = Σ subTotal，运费不进商品小计），Phase 1 同单取最高费率模板计一次 + 满额包邮阈值，秒杀单免邮，I43 模板查询 fail-closed（不降级为免邮）；④**拆单发货（F3，ADR-037）**——`order_logistics` 改代理主键 `id` + `package_no`（`uk_order_package`，`order_id` 转 1:N 外键，T0「主键约定偏差」消除），`order_item.package_no` 发货标记，部分发货停留待发货、全部发货转待收货（不新增订单状态）；⑤**退款触发纠正（ADR-037）**——仅退款（type=2）审核同意即触发，退货退款（type=1）在商家确认收到退货（`confirm-return`，APPROVED→RETURNED）后触发，I42 回写来源扩展为 APPROVED|RETURNED → REFUNDED。数据库 45 表 / 30 迁移（V1.1.0→V5.6）；测试全量通过（最终计数以主控构建校准为准）。遗留验证项（按 ADR-036 记录）：真实渠道账单解析未做端到端验证。

> **2026-10-04 交付记录（P0 资金线风险收敛，[ADR-041](../adr/041-refund-resume-aftersale-guard-freight-cache.md)）**：①**退款悬挂补偿**——`RefundResumeManager` + `RefundResumeJob`（XXL-Job `resumeStuckRefunds`，悬挂>10min、单轮≤100）：微信 v3 退款查询（`PaymentClient.queryRefund`，状态归一化 SUCCESS/FAILED/PROCESSING，CLOSED·ABNORMAL→FAILED）三态收敛——SUCCESS 走共享条件结算+I42 回写、FAILED 置失败交 AD43 人工重试（不自动出款）、PROCESSING 跳过；BALANCE/ALIPAY 幂等重放（wallet `transaction_no` 重放 / Mock 同步）；与 PAY6 回调互为冗余，**关闭 ADR-035 风险 3（PROCESSING 悬挂无兜底）**；②**售后审核守卫收紧**——`review` 禁改来源由 `REFUNDED` 扩至 `RETURNED`（`REFUND_STATUS_INVALID(3039)`），RETURNED 唯一合法去向是 I42 回写资金终态，**关闭 ADR-037 风险 6**；③**I43 计价缓存**——trade `FreightPostageResolver` 加 Caffeine 60s TTL 本地缓存（key=sellerId、max 10k，空 templateIds 全量查询，loader 异常不缓存），高频下单读热点消除且 fail-closed 保持，**关闭 ADR-037 风险 4**；④ADR-036 风险 2（AD46 类型）与 ADR-037 风险 7（契约偏差三处）确认已在 05 规格 V5.6.1 收敛，仅补 ADR 标注。测试全量 930（+19）0 失败。遗留：真实微信退款查询/真实账单解析待联调环境回归（同 ADR-036 遗留口径）。

> **2026-10-04 交付记录（P1 第一批：消息触达 + 订单留言 + 秒杀管理端，[ADR-042](../adr/042-buyer-notification-wiring.md) + 05 规格 V5.7.0 契约先行）**：①**买家消息触达接线（ADR-042）**——四业务事件 → 站内信（收件人=买家，I32 落库）：支付成功（pay `PayManager` 回调/余额两路径）、包裹发货（trade `OrderLifecycleManager`）、退款成功（pay `RefundManager`，I42 回写与买家触达一并迁入新协作器 `PayEventNotifier`，类 300→286 行）、售后审核结果（trade `AfterSaleManager`，协作器 `TradeEventNotifier`）；全部**事务外 fail-open + log.warn**（复用 ReviewManager/StoreFollowManager 先例），SMS/email 留 Phase 2，MQ 事件化留 roadmap；口径说明：审核触达在落库后、退款触发前发出（触达的是「审核结论」本身，退款失败属资金链路由 AD43 重试收敛，不回滚触达）。②**订单买家留言（remark 重新立项）**——V5.4 删除的死字段按真实需求回归：T1/T6 请求增量 `remark?`（≤200，`Order.REMARK_MAX_LENGTH` 常量）、`orders.remark` 快照（`V5.7__order_remark.sql` + Initialize.sql 同步）、OrderVO 回显；秒杀链路无 remark 渠道落 null（与契约一致）。③**秒杀管理端 AD40-42**——`AdminFlashController`（`@OperationLog` 审计）+ `FlashSaleAdminManager`：创建（时间窗推导状态 1/2/3，已过期窗口拒绝）、编辑（null-safe 局部更新、已结束禁编）、删除（进行中禁删）；错误口径 1101/1103；库存继续惰性预热兜底（显式预热会重置已扣库存，故不接）。测试全量通过（计数以主控构建校准）。

> **2026-10-05 交付记录（P1 第二批：促销引擎 + 价格计算中心 / 会员等级与权益，[ADR-043](../adr/043-pricing-center-and-promotion-engine.md)/[ADR-044](../adr/044-member-level-and-benefits.md) + 05 规格 V5.8.0 契约先行）**：①**F5 促销引擎（marketing）**——`promotion` 表（店铺级满减/满折，时间窗推导状态 1/2/3，version 乐观锁）+ `AdminPromotionController` AD48-50（复用秒杀先例：null-safe 局部更新、已结束禁编、进行中禁删、`@OperationLog` 审计、已过期窗口拒绝创建）+ `InternalPromotionController` I44（按 sellerId 查进行中活动，空集合=合法业务态）；②**价格计算中心（trade）**——`domain/service/PricingCalculator` 纯函数（同类型促销取减免最大者、满折万分比+封顶、会员折扣万分比、三源合计守卫 `discountAmount ≤ totalAmount`）+ `app/order/pricing/PricingService`（I44 fail-closed → I45 fail-open → 券收编：`applyCoupons` 自策略层迁入且券核销 Feign 语义/时点原样保留，I44 失败时券未核销免补偿）；秒杀单维持不进计价中心；③**F6 会员等级（user）**——`member_level_rule`（seed 四级：NORMAL 不折/SILVER 99 折 1.1 倍/GOLD 98 折 1.2 倍/PLATINUM 95 折 1.5 倍）+ `member_growth_log`（`uk_order_no` 幂等锚点，并发 DuplicateKey 回落）+ `MemberLevelPolicy` 纯函数重算（只升不降）+ I45 权益查询（规则缺行枚举兜底）；④**交易接线**——下单链路 NORMAL/DIRECT 策略改调定价中心；确认收货路径积分乘会员倍率（`payAmount/divisor × pointsRate/100`）并经 I46 入账成长值（双 fail-open）。V5.8 迁移 `promotion`/`member_level_rule`/`member_growth_log`（Initialize.sql 同步，48 表）。测试全量通过（计数以主控构建校准）。遗留：C 端促销标签/N 元 N 件/SKU 级会员价/多券分摊（O1）roadmap 挂账。

> **2026-10-05 治理变更（数据库结构演进口径，[ADR-045](../adr/045-db-snapshot-single-source.md)）**：用户裁决库无存量数据——`database/migrations/` 目录（V1.1.0→V5.8 共 32 个迁移文件）删除，**`Initialize.sql` 为唯一结构真相源**，此后结构变更直接修改快照、不再产生迁移文件；AGENTS/CLAUDE/02-Architecture/04-Data-Model/14-Service-Config-Guide/docker-compose 注释同步清理；历史 ADR 中的迁移文件路径为存档记录不回写。

> **2026-10-05 交付记录（P2 第一批：F7 商品向量推荐，[ADR-046](../adr/046-product-vector-recommendation.md) + 05 规格 V5.9.0 契约先行）**：①**search-service**——`ProductVectorManager`（条件装配，单向量不分块：name+description 拼接 → EmbeddingGateway → 幂等覆盖写 products 索引）+ I47 `POST /internal/search/vector/product/upsert`（未启用返回 0 / Feign 降级哨兵 -1）+ I48 `GET /internal/search/vector/product/similar`（自身向量 kNN 排除自身，id+score）；`ProductSearchGateway` 扩 `upsertVectorDoc`/`findVectorById`，`products` 索引 mapping 升级 knn+dense_vector（`ProductVectorMapping`）；**reindex 修复**——重建改走 ProductVectorMapping 并逐商品补向量（单商品 embed 失败降级为无向量文档不中断整批），消除「reindex 冲掉 kNN mapping」的隐患；②**item-service**——P27 `GET /api/item/product/{id}/similar`（命中 id 批量查本地库组装新鲜在售商品卡，恒过滤在售/剔除自身/按相似度降序）+ 摄取钩子（`ProductRecommendManager` 协作器，afterCommit 注册——事务回滚不摄取、哨兵/异常仅 log 主流程零感知，对齐触达接线先例）；③ia-api `ProductVectorClient`（fail-open）/`ProductVectorUpsertCO`/`ProductVectorHitCO`；MySQL 零 schema 变更（向量在 ES）。降级定类：推荐纯旁路全链路 fail-open（与 I43/I44 fail-closed 刻意不同）。测试全量通过（计数以主控构建校准）。遗留：冷启动推荐位可空（存量商品靠 reindex 或编辑触发摄取）、「看了又看」共现统计/多路召回 Phase 2。

> **2026-10-05 交付记录（P2 第二批：F8 搜索 facet / F9 每日任务体系，[ADR-047](../adr/047-daily-task-system.md) + 05 规格 V5.10.0 契约先行）**：①**F8 搜索 facet 与过滤补齐**——SE1 声明未实现的 `brand/minPrice/maxPrice/sort` 过滤补齐（ES 链 filter+sort、`ProductDocument` 新增 `publishTime`（epoch millis）+ mapping/reindex/I47 回填同口径、DB 链 HAVING 子查询同口径过滤；`EsProductSearchGateway` 296→270 行，查询装配下沉 `ProductSearchRequestBuilder`、文档转换下沉 `ProductEsDocuments`）；新增 SE6 `GET /api/search/facets`（品牌 terms size20 + 固定五档价格聚合，独立端点零破坏，聚合异常降级空对象，DB 链恒空对象）；②**F9 每日任务体系**——`task_def`（表驱动，seed DAILY_ORDER +20 / DAILY_REVIEW +10，按 ADR-045 口径直接入 Initialize.sql 快照）+ `task_record`（`uk_user_task_date` 每日幂等锚点）+ `TaskManager`（完成与 `PointsManager.addPoints` type=3 同一本地事务，DuplicateKey 幂等回落不外抛）+ M14 任务中心 + I49 触发口；接线：trade 确认收货 → DAILY_ORDER（事务外旁路）、item 评价落库 → DAILY_REVIEW（afterCommit，Feign 严格出事务）；ia-api `TaskClient` fail-open。测试全量通过（计数以主控构建校准）。遗留：管理端任务 CRUD/新手与周期任务类型/动态价格分档/搜索联想（SE5）roadmap 挂账。

> **2026-10-05 交付记录（P2 第三批：F10 活动中心与促销可见性，05 规格 V5.11.0 契约先行）**：①**M12/M13 活动中心**——`ActivityController` + `ActivityManager`：进行中促销（时间窗推导，与下单计价同源 `promotion` 表——所见即所得）按契约类型过滤（1→FULL_REDUCE/2→LIMIT_DISCOUNT，`PromotionTypeEnum.contractName` 单一出处），NEW_USER 暂空列表（数据源 Phase 2）；M13 按 id 详情（1103）；②**M7 我的券三态计数**（available/used/expired，`UserCouponRepository` 补计数端口）；③**P2 商品详情促销标签**——`ProductVO` 增量 `promotions`（`PromotionTagVO`），item 复用 I44 `PromotionClient`、调用方 try/catch fail-open 包装（fallback 仍 fail-closed 语义不变）+ 60s Caffeine 缓存（ADR-041 先例，marketing 抖动时标签空 60s 不打压）；④**I44 契约增量**——`PromotionCO` 补 `endTime`（标签倒计时，marketing/item 映射同步）；⑤**网关补路由**——`/api/task/**`（上批 M14 遗漏）与 `/api/activity/**` 进 marketing 路由块，`GET /api/activity/**` 入匿名白名单（M12/M13 🌐）。测试全量通过（计数以主控构建校准）。遗留：新人礼活动数据源/商品列表促销角标 Phase 2。

> **2026-10-05 交付记录（P2 第四批：F11 店铺装修 / F12 内容社区 Phase 1，[ADR-048](../adr/048-content-notes-in-item-domain.md) + 05 规格 V5.12.0 契约先行）**：①**F11 店铺装修（merchant）**——`shop_decoration`（与 shop 1:1，公告/轮播 ≤5/推荐商品位 ≤8，JSON 字符串列领域层校验）+ S1c/S1d（归属校验同 S1b，UPSERT 走 `uk_shop` 的 `ON DUPLICATE KEY UPDATE`）+ SH1 ShopVO 增量 `decoration`；`hotProductIds` Phase 1 不校验商品归属（展示端过滤，ADR-048 §4）；②**F12 种草笔记（item，content 聚合分包——未来 community-service 拆分边界）**——P28 发布（关联商品 ≤5 逐个校验在售）/P29 流/P30 详情（liked 两态 + 在售商品卡同库装配）/P31·P32 点赞取消（`uk_note_user` 幂等 + `like_count` 单条 UPDATE 原子计数）/P33 作者软删；③**网关**——`/api/notes/**` 进 item 路由块 + GET 白名单（流/详情 🌐，写端点落 authenticated）。数据模型 4 张表按 ADR-045 口径直接入 Initialize.sql 快照（50→54 表）。鉴定体系 Phase 2 独立 PRD 立项（Phase 1 不携带任何鉴定声明）。测试全量通过（计数以主控构建校准）。遗留：富文本/视频/笔记评论/关注流/笔记向量化推荐 Phase 2。

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
| V2.5 AI 生产就绪 | 🟢 100% | 14 | 137+ | 160+ | AI 会话隔离(Redis ChatMemory)、SSE流式(2端点)、ES向量索引+kNN、Feign迁移(SearchClient/OrderClient)、Conversation API、RAG Pipeline(EmbeddingService+RAGRetriever+RAGSearchTool)、内容安全(3层防御)、单元测试(10用例)、@RateLimit(4端点) |
| V3.0 智能升级   | 🟡 85% | 14 | —   | —   | 完整RAG管线✅、多模型路由✅(ModelRouter已接线)、语义缓存✅、离线评测✅(LLMJudge+35条数据集)、人工转接✅(HandoffService)、Conversation API✅(Redis持久化)、AI Gateway✅(Token配额); 待完成: 主-子Agent编排、离线评测自动化、商家知识库CRUD完整 |
| V3.1 RBAC授权  | 🟢 100% | 14 | 152+ | 160+ | @PreAuthorize方法级授权+PermissionService(@Service("ss"))、角色/权限管理CRUD、Admin端点保护(user:admin)、UserContext→SecurityContext桥接 |
| V3.2 现代化+DDD | 🟢 100% | 14 | 152+ | 160+ | RestTemplate→RestClient(Spring Boot 3.2+)、DTO→Java Record(10个)、UserProfileService DDD分层+@Version乐观锁、MinIO头像上传/下载/缩略图 |
| V3.3 买家-商家消息 | 🟢 100% | 14 | 160+ | 160+ | ChatWebSocketConfig(STOMP)+ChatMessageController+ChatMessageService(DDD分层)+chat_message迁移+Redis热缓存; 待完成: RabbitMQ离线推送 |
| V3.4 店铺AI        | 🟢 100% | 14 | 165+ | 160+ | KnowledgeBaseController(CRUD)+merchant_knowledge_base迁移+DocumentChunker+RAGRetriever.retrieveForSeller()+MerchantKnowledgeEntity/Mapper; 待完成: AI自动代答完整测试 |
| V3.5 详情页改造    | 🟢 100% | 14 | 172+ | 160+ | 商品详情页京东标准改造：店铺卡片/多图轮播/规格参数/结构化SKU/原价到手价/立即购买/评论增强(图视频)/关注商家/店铺公开页/购物车分组 |
| V3.6 秒杀+订单优化 | 🟢 100% | 14 | 172+ | 160+ | 秒杀Redis Lua预扣(消除TOCTOU)+用户限购+异步订单；订单超时RabbitMQ TTL死信队列(消除60s轮询CPU浪费)+幂等保护+批量回库存 |
| V3.7 秒杀漏斗模型  | 🟢 100% | 14 | 175+ | 160+ | 漏斗模型(网关限流→Redis热点分片→RocketMQ削峰→Consumer异步DB)+订单四层保障(RocketMQ延迟消息主链路→事务表幂等→XXL-Job兜底→人工运营后台) |
| V4.0 RocketMQ迁移  | 🟢 100% | 14 | 175+ | 160+ | RocketMQ 5.3 NameServer+Broker(docker-compose)、OrderTimeoutPublisher策略模式、FlashSaleOrderPublisher+RocketFlashSaleRocketConsumer、延迟消息level=16(30min)、替代RabbitMQ TTL+DLX |
| V4.0 DDD架构升级  | 🔵 计划中 | 14 | 175+ | 160+ | 7模块Manager层补齐(user/item/cart/auth/logistics/search/ai/marketing)、Controller→Mapper/Redis全下沉、Service→Feign全上移、Entity不泄漏到Controller |
| V4.0 大数据学习    | 🔵 计划中 | 14 | 175+ | 160+ | +PG(pgvector)+PgBouncer+Caffeine+AlertManager+Kafka+Zk+Flink+Hadoop+Spark+Hive — 13→22组件, 学习大厂全链路 |
| V4.x 组件收敛      | ⚪ 远期    | — | — | — | PG替代MySQL+ES向量, KRaft去Zk, MinIO+Spark去Hadoop, 事件驱动去Flink/XXL-Job, 事务表去Seata — 22→10组件 |
| V3.x 高级AI     | ⚪ 0%    | —   | —   | —   | 知识图谱(NebulaGraph)、多模态(VLM)、MCP协议、自建Embedding、领域模型微调（均有触发条件） |

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
