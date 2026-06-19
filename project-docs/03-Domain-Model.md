# 03 — 领域模型文档 (DDD Domain Model)

> 来源：`doc/项目的分析流程/03.领域建模.md`、`doc/项目的分析流程/00.用户分析.md`、`doc/IDEA.md`

---

## 一、限界上下文 (Bounded Contexts)

```
┌──────────────────────────────────────────────────────────┐
│                    IcedAmericanoMall                      │
│                                                          │
│  ┌──────────────┐  ┌──────────────┐  ┌───────────────┐  ┌──────────────┐  │
│  │ 用户上下文    │  │ 商品上下文    │  │ 订单上下文     │  │ AI搜索上下文  │  │
│  │ User Context │  │Product Ctx   │  │ Order Context │  │ AI Search Ctx│  │
│  │              │  │              │  │               │  │ (V2.0+)      │  │
│  │ 用户(User)   │  │ 商品(Product)│  │ 订单(Orders)  │  │              │  │
│  │ 地址(Addr)   │  │ 规格(SKU)    │  │ 订单项(Item)  │  │ 商品向量索引  │  │
│  │ 商家(Seller) │  │ 类目(Cat)    │  │ 购物车(Cart)  │  │ Embedding    │  │
│  └──────┬───────┘  └──────┬───────┘  └──────┬────────┘  └──────┬───────┘  │
│         │                 │                  │                  │          │
│  ┌──────┴───────┐  ┌──────┴───────┐  ┌──────┴────────┐  ┌──────┴───────┐  │
│  │ 认证上下文    │  │ 搜索上下文    │  │ 支付上下文     │  │ AI客服上下文  │  │
│  │ Auth Context │  │Search Ctx    │  │Payment Ctx    │  │AIService Ctx │  │
│  │              │  │              │  │               │  │ (V2.0+)      │  │
│  │ Token        │  │ 索引商品     │  │ PayOrder      │  │              │  │
│  │ 凭证         │  │ 搜索结果     │  │ 支付渠道       │  │ RAG知识库    │  │
│  └──────────────┘  └──────────────┘  └───────────────┘  │ Agent对话    │  │
│                                                          └──────────────┘  │
│                                                          ┌──────────────┐  │
│  ┌──────────────┐                                       │              │  │
│  │ 物流上下文    │                                       │ 知识图谱      │  │
│  │ Logistics Ctx│                                       │ KnowledgeCtx │  │
│  │              │                                       │ (V3.0+)      │  │
│  │ 物流单       │                                       │ 实体关系图    │  │
│  └──────────────┘                                       └──────────────┘  │
└──────────────────────────────────────────────────────────┘
```

### 上下文映射 (Context Map)

| 上游    | 下游    | 关系          | 实现                            |
| ----- | ----- | ----------- | ----------------------------- |
| 用户上下文 | 认证上下文 | 共享内核 (User) | user-service 提供 Feign 接口      |
| 用户上下文 | 订单上下文 | 客户/供应商      | trade-service 调用 user-service |
| 商品上下文 | 订单上下文 | 客户/供应商      | item-service 提供商品信息           |
| 订单上下文 | 支付上下文 | 发布/订阅       | 订单事件通过 **RabbitMQ** 触发支付（V1.1） |
| 订单上下文 | 物流上下文 | 发布/订阅       | 支付成功后通过 **RabbitMQ** 触发物流（V1.1） |
| 订单上下文 | 通知上下文 | 发布/订阅       | 状态变更通过 **WebSocket** 实时推送（V1.2） |
| 商品上下文 | AI搜索上下文 | 客户/供应商  | ai-service 调用 search-service + item-service |
| 用户上下文 | AI客服上下文 | 客户/供应商  | ai-service 调用 user-service + trade-service |
| AI搜索上下文 | AI客服上下文 | 共享内核 (Embedding) | 向量检索能力复用                      |
| AI搜索上下文 | 知识图谱     | 发布/订阅（V3.0+） | 商品实体关系抽取 → 图谱更新              |

---

## 二、核心域与支撑域

| 类型      | 上下文               | 说明         |
| ------- | ----------------- | ---------- |
| **核心域** | 订单上下文、支付上下文       | 核心竞争力，交易闭环 |
| **支撑域** | 用户上下文、商品上下文、AI搜索上下文 | 必不可少但非差异化  |
| **通用域** | 认证上下文、搜索上下文、物流上下文 | 可用通用方案实现   |
| **创新域** | AI客服上下文、知识图谱 (V2.0+) | 差异化竞争力，智能体验  |

---

## 三、实体与属性

### 3.1 用户 (User) — 用户上下文聚合根

| 属性           | 类型（业务） | 说明        |
| ------------ | ------ | --------- |
| userId       | 标识     | 用户业务唯一标识  |
| username     | 字符串    | 用户名（可选）   |
| phone        | 字符串    | 手机号（登录账号） |
| password     | 加密字符串  | 密码        |
| avatar       | URL    | 头像        |
| registerTime | 日期时间   | 注册时间      |
| status       | 枚举     | 正常 / 禁用   |
| balance      | 金额     | 账户余额      |

### 3.2 收货地址 (Address) — 用户上下文实体

| 属性                            | 类型  | 说明       |
| ----------------------------- | --- | -------- |
| addressId                     | 标识  | 地址唯一标识   |
| userId                        | 关联  | 所属用户     |
| receiver                      | 字符串 | 收货人      |
| phone                         | 字符串 | 联系电话     |
| province/city/district/street | 字符串 | 四级地址     |
| detail                        | 字符串 | 门牌号等详细地址 |
| isDefault                     | 布尔  | 默认地址标记   |
| label                         | 字符串 | 标签（家/公司） |

### 3.3 商家 (Seller) — 用户上下文实体

| 属性                                   | 类型  | 说明            |
| ------------------------------------ | --- | ------------- |
| sellerId                             | 标识  | 商家唯一标识        |
| userId                               | 关联  | 关联用户（一对一）     |
| shopName                             | 字符串 | 店铺名称          |
| shopLogo                             | URL | 店铺 logo       |
| contactPhone                         | 字符串 | 联系电话          |
| province/city/district/detailAddress | 字符串 | 发货地址          |
| status                               | 枚举  | 审核中 / 正常 / 冻结 |

### 3.4 商品 (Product/SPU) — 商品上下文聚合根

| 属性           | 类型   | 说明           |
| ------------ | ---- | ------------ |
| productId    | 标识   | 商品业务编号       |
| sellerId     | 关联   | 所属商家         |
| categoryId   | 关联   | 所属类目         |
| name         | 字符串  | 商品名称         |
| mainImage    | URL  | 主图           |
| description  | 富文本  | 图文描述         |
| brand        | 字符串  | 品牌           |
| soldCount    | 整数   | 总销量          |
| commentCount | 整数   | 总评论数         |
| isAd         | 布尔   | 广告商品标记       |
| status       | 枚举   | 上架 / 下架 / 删除 |
| publishTime  | 日期时间 | 发布时间         |

### 3.5 规格 (SKU) — 商品上下文实体

| 属性        | 类型  | 说明               |
| --------- | --- | ---------------- |
| skuId     | 标识  | SKU 业务编码         |
| productId | 关联  | 所属商品             |
| spec      | 字符串 | 规格描述（如"黑色 128G"） |
| price     | 金额  | 价格               |
| stock     | 整数  | 库存数量             |
| image     | URL | 规格专属图片           |
| soldCount | 整数  | 该规格销量            |
| status    | 枚举  | 可售 / 停售          |
| version   | 整数  | 乐观锁版本号（并发库存扣减） |

### 3.6 类目 (Category) — 商品上下文实体

| 属性         | 类型  | 说明         |
| ---------- | --- | ---------- |
| categoryId | 标识  | 类目唯一标识     |
| name       | 字符串 | 类目名称       |
| parentId   | 关联  | 父类目（多级支持）  |
| level      | 整数  | 层级 (1/2/3) |
| sortOrder  | 整数  | 排序         |

### 3.7 订单 (Orders) — 订单上下文聚合根

> 数据库表名：`orders`（避免 MySQL 保留字 `order`）

| 属性                         | 类型   | 说明                      |
| -------------------------- | ---- | ----------------------- |
| orderId                    | 标识   | 订单唯一标识（技术主键）|
| orderNo                    | 业务编号 | 订单号（对外）                 |
| userId                     | 关联   | 买家                      |
| sellerId                   | 关联   | 商家（一个订单属于一个商家）          |
| totalAmount                | 金额   | 总金额                     |
| payAmount                  | 金额   | 实付金额                    |
| discountAmount             | 金额   | 优惠金额（优惠券/满减等）        |
| status                     | 枚举   | 待付款→待发货→待收货→已完成→已取消→待评价 |
| paymentType                | 枚举   | 支付宝 / 微信 / 余额           |
| receiverName/Phone/Address | 快照   | 收货信息快照（address 最长 500 字符）|
| createTime                 | 日期时间 | 下单时间                    |
| payTime                    | 日期时间 | 支付时间                    |
| consignTime                | 日期时间 | 发货时间                    |
| endTime                    | 日期时间 | 完成时间                    |
| closeTime                  | 日期时间 | 关闭时间                    |
| version                    | 整数   | 乐观锁版本号（防并发更新丢失）       |

### 3.8 订单项 (OrderItem) — 订单上下文实体（组合关系）

| 属性          | 类型   | 说明     |
| ----------- | ---- | ------ |
| orderItemId | 标识   | 订单项标识  |
| orderId     | 关联   | 所属订单   |
| skuId       | 关联   | 对应 SKU |
| productName | 快照   | 商品名称快照 |
| skuSpec     | 快照   | 规格描述快照 |
| price       | 金额快照 | 下单时单价  |
| quantity    | 整数   | 数量     |
| subTotal    | 金额   | 小计     |
| image       | 快照   | 商品图片快照 |

### 3.9 购物车项 (Cart) — 订单上下文实体

| 属性       | 类型  | 说明     |
| -------- | --- | ------ |
| cartId   | 标识  | 购物车项标识 |
| userId   | 关联  | 用户     |
| skuId    | 关联  | SKU    |
| quantity | 整数  | 数量     |
| selected | 布尔  | 是否选中   |

### 3.10 支付订单 (PayOrder) — 支付上下文聚合根

| 属性             | 类型   | 说明               |
| -------------- | ---- | ---------------- |
| payOrderId     | 标识   | 支付单标识            |
| bizOrderNo     | 关联   | 业务订单号            |
| payOrderNo     | 业务编号 | 支付单号             |
| bizUserId      | 关联   | 支付用户             |
| payChannelCode | 枚举   | 支付渠道             |
| amount         | 金额   | 支付金额             |
| payType        | 枚举   | h5/小程序/公众号/扫码/余额 |
| status         | 枚举   | 待提交→待支付→超时取消→成功  |
| expandJson     | JSON | 渠道扩展信息           |
| qrCodeUrl      | URL  | 支付二维码            |

### 3.11 订单物流 (OrderLogistics) — 物流上下文实体

| 属性                                   | 类型  | 说明         |
| ------------------------------------ | --- | ---------- |
| orderId                              | 标识  | 订单 ID（一对一） |
| logisticsNumber                      | 字符串 | 物流单号       |
| logisticsCompany                     | 字符串 | 物流公司       |
| contact/mobile                       | 快照  | 收件人信息快照    |
| province/city/district/street/detail | 快照  | 地址快照       |

---

## 四、聚合与聚合根

| 聚合   | 聚合根            | 内部实体               | 不变量                            |
| ---- | -------------- | ------------------ | ------------------------------ |
| 用户聚合 | User           | Address (1:N)      | 一个用户可有多个地址；一个用户最多成为一个商家        |
| 商品聚合 | Product        | SKU (1:N)          | SKU 不能脱离 Product 存在于订单中；库存不能为负 |
| 订单聚合 | Orders         | OrderItem (1:N，组合) | 订单总额 = 所有订单项小计之和 - 优惠金额；订单项不能脱离订单     |
| 支付聚合 | PayOrder       | —                  | 支付金额 = 关联订单应付金额                |
| 物流聚合 | OrderLogistics | —                  | 物流必须关联已支付订单                    |

---

## 五、值对象

| 值对象      | 属性            | 所属聚合                     |
| -------- | ------------- | ------------------------ |
| Money    | 金额(分) + 币种    | Order, PayOrder, Product |
| Address  | 省+市+区+街道+详细地址 | User (作为值对象拷贝到 Order)    |
| SpecDesc | 规格描述字符串       | Product/SKU              |

---

## 六、通用语言 (Ubiquitous Language) 词汇表

| 中文术语     | 英文                       | 定义              |
| -------- | ------------------------ | --------------- |
| 用户       | User                     | 平台的注册消费者        |
| 商家       | Seller                   | 拥有店铺的用户         |
| 管理员      | Admin                    | 平台运营管理者         |
| 商品 (SPU) | Product                  | 标准产品单元，同一款商品的抽象 |
| 规格 (SKU) | SKU (Stock Keeping Unit) | 具体销售单元（如某颜色某尺寸） |
| 类目       | Category                 | 商品分类体系          |
| 订单       | Order                    | 一次购买行为生成的交易记录   |
| 订单项      | OrderItem                | 订单中的单品明细        |
| 购物车      | Cart                     | 用户暂存待购商品的容器     |
| 支付单      | PayOrder                 | 与支付渠道对应的支付记录    |
| 物流单      | Logistics                | 发货后的物流追踪记录      |
| 快照       | Snapshot                 | 下单时复制的商品/地址信息副本 |
| 待付款      | Pending Payment          | 订单已生成等待支付的初始状态  |
| 待发货      | Pending Shipment         | 支付完成后等待商家发货     |
| 待收货      | Pending Receipt          | 商家已发货等待用户确认     |
| 已完成      | Completed                | 用户确认收货，交易完成     |
| 已取消      | Cancelled                | 交易关闭（超时或用户主动取消） |

---

## 七、领域服务 (Domain Services)

以下业务规则不属于单一实体，需定义为领域服务：

| 领域服务                | 职责                 | 所在上下文 |
| ------------------- | ------------------ | ----- |
| `PasswordEncoder`   | 密码加密与验证（BCrypt）    | 用户上下文 |
| `TokenService`      | JWT 签发、验证、刷新       | 认证上下文 |
| `StockManager`      | 库存扣减、回滚（下单锁定/取消释放） | 商品上下文 |
| `AmountCalculator`  | 订单总价计算、优惠分摊、运费计算     | 订单上下文 |
| `OrderStateMachine` | 订单状态流转控制           | 订单上下文 |
| `PaymentRouter`     | 根据支付方式路由到不同渠道      | 支付上下文 |
| `EventPublisher`   | 领域事件投递（RabbitMQ, V1.1） | 全局（ia-common） |
| `NotificationService` | WebSocket 实时推送（V1.2） | 通知上下文 |
| `DistributedScheduler` | 分布式定时任务协调（XXL-Job, V1.1） | 全局 |
| `ProductVectorizer` | 商品信息 → Embedding 向量（V2.0+） | AI搜索上下文 |
| `RAGRetriever`     | 多路召回 + 重排序（V2.0+）       | AI客服上下文 |
| `AgentPlanner`     | ReAct 推理 + 工具链编排（V2.0+）  | AI搜索上下文 |
| `HandoffManager`   | 人机转接决策（V2.0+）           | AI客服上下文 |

---

## 八、领域事件

> MVP 阶段通过 Feign 同步调用；V1.1 引入 **RabbitMQ** 后全部改为异步发布/订阅。

| 事件 | 触发场景 | 消费者 | 投递方式 | 可靠性 |
|------|---------|--------|---------|--------|
| `OrderCreated` | 用户提交订单 | 支付上下文（发起支付） | RabbitMQ (V1.1) | Publisher Confirm + 持久化 |
| `OrderPaid` | 支付成功回调 | 物流上下文（准备发货）、通知服务（推送） | RabbitMQ (V1.1) | 同上 |
| `OrderShipped` | 商家发货 | 通知服务（**WebSocket** 推送物流信息, V1.2） | RabbitMQ (V1.1) | 同上 |
| `OrderCompleted` | 用户确认收货 | 积分上下文（下单积分奖励） | RabbitMQ (V1.1) | 同上 |
| `UserRegistered` | 新用户注册 | 营销上下文（新用户奖励） | RabbitMQ (V1.1) | 同上 |
| `OrderCancelled` | 订单超时/手动取消 | 商品上下文（库存回滚补偿） | Feign 同步（MVP）/ RabbitMQ (V1.1) | 补偿必须幂等 |

---

## 九、领域模型 vs 数据模型 边界

| 方面  | 领域模型            | 数据模型        |
| --- | --------------- | ----------- |
| 关注点 | 业务概念、规则、关系      | 数据存储、表结构、约束 |
| 元素  | 实体、值对象、聚合       | 表、字段、主键、外键  |
| 标识  | 业务标识（如 orderNo） | 技术主键（自增ID）  |
| 关系  | 关联、聚合、组合        | 外键、中间表      |
| 目标  | 理解业务            | 实现存储        |

**开发原则**：先建领域模型理解业务，再建数据模型实现存储。两套模型可以不同，但不能矛盾。

---

## 十、相关图表

| 图表   | 文件                     | 说明                                       |
| ---- | ---------------------- | ---------------------------------------- |
| 领域类图 | `drawio/IA_UML.drawio` | 13 个实体类 + 关联关系（User/Address/Seller/Product/Sku/Category/Orders/OrderItem/Cart/PayOrder/OrderLogistics/KnowledgeNode/AgentSession） |
