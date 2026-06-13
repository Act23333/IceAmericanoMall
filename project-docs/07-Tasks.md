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
- **状态**: 🔵 待开始
- **依赖**: T0.1

#### T0.6 gate-service 网关

- **描述**: Spring Cloud Gateway 路由配置、Token 校验过滤器、跨域配置、限流
- **状态**: 🔵 脚手架完成，路由规则待完善
- **依赖**: T0.5, T1.1

---

### Phase 1: 用户域

#### T1.1 authorization-service

- **状态**: ✅ 核心功能已完成
- **待完善**:
  - [ ] 验证码登录 (SMS 集成)
  - [ ] Refresh Token Redis 存储
  - [ ] Token 黑名单 (退出登录)
  - [ ] 多密钥切换机制
  - [ ] 人机验证 (Geetest 集成)

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
- **待完善**:
  - [ ] 签到连续天数计算
  - [ ] 管理后台用户分页查询
  - [ ] 用户状态管理 (禁用/启用)

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

- **状态**: 🔵 脚手架就绪，待实现
- **任务**:
  - [ ] 类目 CRUD (管理后台)
  - [ ] 商品发布 (商家)
  - [ ] 商品列表 (分页 + 排序 + 筛选)
  - [ ] 商品详情 (含 SKU 列表)
  - [ ] 商品上下架 (商家 + 管理员)
  - [ ] SKU 库存管理

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

#### T2.2 search-service (可延后至 Phase 2)

- **状态**: ⚪ 脚手架就绪，ElasticSearch 待搭建
- **依赖**: T2.1

---

### Phase 3: 交易域

#### T3.1 cart-service

- **状态**: 🔵 脚手架就绪，待实现
- **依赖**: T1.1 (认证), T2.1 (SKU 查询)

**BDD 验收场景**:

```gherkin
Scenario: 添加商品到购物车
  Given 用户已登录
  When POST /api/cart with {skuId, quantity}
  Then 购物车新增该 SKU
  And 若已存在同一 SKU，则合并数量

Scenario: 查看购物车
  When GET /api/cart
  Then 返回购物车列表（含商品名/规格/单价/数量/选中状态/小计）

Scenario: 修改数量为 0 → 删除
  When PUT /api/cart/{id} with {quantity: 0}
  Then 该购物车项被删除
```

#### T3.2 trade-service

- **状态**: 🔵 脚手架就绪，待实现
- **依赖**: T1.1, T2.1, T3.1

**BDD 验收场景**:

```gherkin
Scenario: 创建订单
  Given 用户已登录且购物车有选中商品
  When POST /api/trade/order with {addressId, cartItemIds[]}
  Then 生成订单号，状态"待付款"
  And 订单金额 = 各订单项小计之和
  And 收货信息为下单时地址快照
  And 商品/价格信息为下单时快照
  And 扣减 SKU 库存
  And 清空购物车中已下单项

Scenario: 库存不足拒绝
  Given SKU 库存 3，用户购买 5
  When POST /api/trade/order
  Then 返回错误 "库存不足"

Scenario: 取消订单
  Given 订单状态为"待付款"
  When POST /api/trade/order/{orderNo}/cancel
  Then 状态 → "已取消"
  And 回滚已扣减库存

Scenario: 确认收货
  Given 订单状态为"待收货"
  When POST /api/trade/order/{orderNo}/confirm
  Then 状态 → "已完成"

Scenario: 超时取消 (30分钟)
  Given 订单创建 30 分钟后未支付
  When 定时任务扫描
  Then 状态 → "已取消"，库存回滚

Scenario: 商家发货
  Given 商家已登录，订单状态"待发货"
  When POST /api/trade/seller/order/{orderNo}/ship with {logisticsCompany, logisticsNumber}
  Then 创建物流记录，订单状态 → "待收货"
```

#### T3.3 pay-service

- **状态**: 🔵 脚手架就绪，待实现
- **依赖**: T3.2

**BDD 验收场景**:

```gherkin
Scenario: 微信支付发起
  Given 订单状态"待付款"
  When POST /api/pay/order/{orderNo}
  Then 创建 PayOrder，返回支付二维码链接

Scenario: 支付成功回调
  Given 微信支付回调通知
  When POST /api/pay/callback/wechat with 签名校验通过
  Then PayOrder 状态 → "成功"
  And 关联 Order 状态 → "待发货"

Scenario: 支付超时
  Given PayOrder 超过有效期未支付
  When 定时检查
  Then PayOrder 状态 → "超时取消"
```

#### T3.4 logistics-service

- **状态**: 🔵 脚手架就绪，待实现
- **依赖**: T3.2, T3.3

---

### Phase 4: 后台管理

#### T4.1 商家后台

- **状态**: 🔵 前端待开发，后端接口部分复用已有服务
- **依赖**: T1.1, T2.1, T3.2, T3.4

#### T4.2 管理员后台

- **状态**: 🔵 前端待开发
- **依赖**: T1.1, T1.2, T2.1, T3.2

---

## 三、未来阶段路线图

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

### V3.0 — 知识驱动

- **知识图谱引擎**：商品实体关系抽取、图谱构建、多跳推理（详见 13-AI-Technology-Selection §6 Phase 4）
- RAG + KG 混合检索：模糊语义匹配 + 精确关系推理双引擎
- 场景化搭配推荐：基于知识图谱的"主商品→配件→兼容性校验"推荐链路
- 知识图谱可视化后台：运营/商家查看和修正实体关系

---

## 四、当前进度摘要

| Phase        | 进度     | 说明                                                   |
| ------------ | ------ | ---------------------------------------------------- |
| Phase 0 基础设施 | 🟢 80% | 项目骨架、ia-common、数据库脚本 完成；Nacos/Gateway 待搭建            |
| Phase 1 用户域  | 🟢 70% | authorization-service + user-service 核心功能完成；部分边缘功能待补 |
| Phase 2 商品域  | 🔵 5%  | 脚手架就绪，类目/商品/搜索待实现                                    |
| Phase 3 交易域  | 🔵 5%  | 脚手架就绪，购物车/订单/支付/物流待实现                                |
| Phase 4 后台管理 | 🔵 0%  | 待开始                                                  |

**下一步重点**: 完成 item-service 商品模块，打通「浏览商品」→「加入购物车」→「下单支付」的完整链路。
