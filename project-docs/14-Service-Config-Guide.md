# 14 — 服务配置指南

> 帮助开发者从零搭建 IceAmericanoMall 所需的全部服务组件和第三方平台配置。

---

## 一、架构全景

```
┌────────────────────────────────────────────────────────────┐
│                      第三方平台 (需注册)                      │
│  微信支付 │ 阿里云短信 │ 极验验证 │ DeepSeek API             │
└────────────────────────────────────────────────────────────┘
                            ↑
┌────────────────────────────────────────────────────────────┐
│                   自建中间件 (Docker 一键启动)                │
│  MySQL │ Redis │ Nacos │ ES │ RabbitMQ │ XXL-Job │ MinIO   │
└────────────────────────────────────────────────────────────┘
                            ↑
┌────────────────────────────────────────────────────────────┐
│              12 个微服务 (Spring Boot + Nacos)               │
│  gateway │ auth │ user │ item │ cart │ trade │ pay │ ...   │
└────────────────────────────────────────────────────────────┘
```

---

## 二、服务端口规划

| 服务 | 端口 | 说明 |
|------|------|------|
| gate-service | **8080** | 统一 API 入口 |
| authorization-service | **9000** | OAuth2 认证 + JWT 签发 |
| user-service | **8080** | 注意：与 gateway 同端口（本地分机器部署时调整） |
| item-service | **8082** | 商品/类目/SKU |
| cart-service | **8083** | 购物车 |
| trade-service | **8084** | 订单/售后/结算/入驻 |
| pay-service | **8085** | 支付 |
| search-service | **8086** | 搜索（ES 未启用时降级为 DB） |
| logistics-service | **8087** | 物流 |
| ai-service | **8089** | AI 助手 + 客服 |
| marketing-service | **8090** | 优惠券 + 秒杀 |

---

## 三、自建中间件（Docker Compose 一键启动）

### 3.1 启动命令

```bash
# 只启动基础服务（MySQL + Redis + Nacos）
cd Implementation/back-end
docker compose up -d

# 启动全部中间件（含 ES + RabbitMQ + XXL-Job + MinIO）
docker compose --profile v1.1 up -d
```

### 3.2 MySQL

| 配置项 | 值 |
|--------|-----|
| 镜像 | `mysql:9.0` |
| 端口 | **3306** |
| root 密码 | `root123` |
| 数据库 | `icedamericano_mall` |
| 初始化 SQL | `database/Initialize.sql`（自动执行） |

**手动创建数据库**（如果不使用 Docker）：

```sql
CREATE DATABASE icedamericano_mall DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci;
-- 然后执行 database/Initialize.sql
-- 再按版本顺序执行 database/migrations/V*.sql
```

### 3.3 Redis

| 配置项 | 值 |
|--------|-----|
| 镜像 | `redis:7-alpine` |
| 端口 | **6379** |
| 密码 | 无（开发环境） |

### 3.4 Nacos（注册中心 + 配置中心）

| 配置项 | 值 |
|--------|-----|
| 镜像 | `nacos/nacos-server:v2.4.0` |
| Web 控制台 | **http://localhost:8848/nacos** |
| gRPC 端口 | **9848** |
| 用户名/密码 | `nacos` / `nacos` |
| 模式 | standalone（单机） |

**验证**：访问 `http://localhost:8848/nacos`，在「服务管理→服务列表」中应能看到注册的服务。

### 3.5 ElasticSearch（V1.1 profile）

| 配置项 | 值 |
|--------|-----|
| 镜像 | `docker.elastic.co/elasticsearch/elasticsearch:7.17.25` |
| HTTP 端口 | **9200** |
| 集群端口 | **9300** |
| 安全 | 关闭（xpack.security.enabled=false） |
| 内存 | 512MB |

**启用条件**：`search.elasticsearch.enabled=true`（search-service application.yml）

### 3.6 RabbitMQ（V1.1 profile）

| 配置项 | 值 |
|--------|-----|
| 镜像 | `rabbitmq:3.13-management-alpine` |
| AMQP 端口 | **5672** |
| 管理控制台 | **http://localhost:15672** |
| 用户名/密码 | `admin` / `admin123` |

**启用条件**：`rabbitmq.enabled=true`（trade/pay/logistics application.yml）

### 3.7 XXL-Job Admin（V1.1 profile）

| 配置项 | 值 |
|--------|-----|
| 镜像 | `xuxueli/xxl-job-admin:2.4.2` |
| 控制台 | **http://localhost:8088/xxl-job-admin** |
| 用户名/密码 | `admin` / `123456` |
| 数据库 | 复用 `icedamericano_mall`（需手动执行 XXL-Job 初始化 SQL） |

**启用条件**：`xxl.job.enabled=true`

**XXL-Job 初始化 SQL**（首次部署需手动执行）：
```sql
-- 从 https://github.com/xuxueli/xxl-job/blob/master/doc/db/tables_xxl_job.sql 获取
```

### 3.8 MinIO（V1.1 profile）

| 配置项 | 值 |
|--------|-----|
| 镜像 | `minio/minio:latest` |
| API 端口 | **9000** |
| 控制台端口 | **9001** |
| 用户名/密码 | `minioadmin` / `minioadmin` |

**启用条件**：`minio.enabled=true`

---

## 四、第三方平台配置

### 4.1 微信支付（pay-service）

**注册流程**：
1. 前往 [微信支付商户平台](https://pay.weixin.qq.com/) 注册商户号
2. 在「账户中心→API安全」中设置 APIv3 密钥
3. 下载商户证书（apiclient_key.pem）

**环境变量**：

| 变量 | 说明 | 示例 |
|------|------|------|
| `WECHAT_PAY_MERCHANT_ID` | 商户号 | `1234567890` |
| `WECHAT_PAY_PRIVATE_KEY_PATH` | 商户私钥路径 | `/etc/wechat/apiclient_key.pem` |
| `WECHAT_PAY_MERCHANT_SERIAL` | 商户证书序列号 | `ABC123...` |
| `WECHAT_PAY_API_V3_KEY` | API v3 密钥 | 32 位随机字符串 |
| `WECHAT_PAY_NOTIFY_URL` | 支付回调地址 | `https://your-domain.com/api/pay/callback/wechat` |

**本地开发**：设置 `WECHAT_PAY_MERCHANT_ID=false` 使用 Mock 支付客户端，不调用真实微信接口。

### 4.2 阿里云短信（user-service）

**注册流程**：
1. 前往 [阿里云短信服务控制台](https://dysms.console.aliyun.com/) 开通服务
2. 申请短信签名（如「冰美式商城」）
3. 申请短信模板（验证码类型）
4. 获取 AccessKey ID 和 AccessKey Secret

**application.yml 配置**：

```yaml
aliyun:
  sms:
    enabled: true                         # 启用真实短信
    access-key-id: LTAI5tXXXXXXXXXXXX      # 替换为实际 AccessKey
    access-key-secret: xxxxxxxxxxxxxxxxxx  # 替换为实际 Secret
    sign-name: 冰美式商城                   # 短信签名
    template-code: SMS_123456789           # 短信模板CODE
```

**本地开发**：`aliyun.sms.enabled=false` 使用 MockSmsClient（打印日志，不发送）。

### 4.3 极验人机验证（user-service）

**注册流程**：
1. 前往 [极验官网](https://www.geetest.com/) 注册账号
2. 创建应用，获取 captcha-id 和 key

**application.yml 配置**：

```yaml
geetest:
  captcha-id: "你的captcha_id"    # 替换为实际值
  key: "你的key"                  # 替换为实际值
```

### 4.4 DeepSeek API（ai-service）

**注册流程**：
1. 前往 [DeepSeek 开放平台](https://platform.deepseek.com/) 注册账号
2. 在「API Keys」页面创建 API Key

**环境变量**：

```bash
export DEEPSEEK_API_KEY=sk-your-api-key-here
```

**启用条件**：`ai.enabled=true`

**计费**：约 ¥1/百万 tokens

---

## 五、环境变量速查表

### 生产环境必须设置的变量

| 变量 | 服务 | 默认值（开发） | 说明 |
|------|------|-------------|------|
| `ia.db.host` | user, search | `localhost` | MySQL 地址 |
| `hm.db.pw` | 所有 | `root123` | MySQL root 密码 |
| `ia.nacos.host` | 大部分 | `localhost` | Nacos 地址 |
| `ia.redis.host` | 大部分 | `localhost` | Redis 地址 |
| `DEEPSEEK_API_KEY` | ai | `sk-placeholder` | DeepSeek API Key |
| `KEYSTORE_PASSWORD` | auth | `changeit` | JWT 密钥库密码 |
| `KEY_PASSWORD` | auth | `changeit` | JWT 密钥密码 |
| `WECHAT_PAY_MERCHANT_ID` | pay | — | 微信商户号 |
| `WECHAT_PAY_PRIVATE_KEY_PATH` | pay | `/etc/wechat/apiclient_key.pem` | 商户私钥 |
| `WECHAT_PAY_MERCHANT_SERIAL` | pay | — | 商户证书序列号 |
| `WECHAT_PAY_API_V3_KEY` | pay | — | APIv3 密钥 |

### 功能开关

| 配置 | 默认 | 开启后 |
|------|------|--------|
| `aliyun.sms.enabled` | `false` | 使用真实阿里云短信 |
| `search.elasticsearch.enabled` | `false` | ES 全文检索（关闭则 DB LIKE） |
| `rabbitmq.enabled` | `false` | MQ 异步事件（关闭则 Feign 同步） |
| `xxl.job.enabled` | `false` | XXL-Job 分布式调度（关闭则 @Scheduled） |
| `sentinel.enabled` | `false` | Sentinel 限流熔断（关闭则 @RateLimit AOP） |
| `minio.enabled` | `false` | MinIO 对象存储（关闭则 MockStorageClient） |
| `ai.enabled` | `false` | Spring AI + LangChain4j Agent |
| `seata.enabled` | `false` | Seata 分布式事务（关闭则手动 Saga） |
| `websocket.enabled` | `false` | WebSocket 实时推送 |

---

## 六、搭建顺序

```
Step 1  Docker: docker compose up -d               → MySQL + Redis + Nacos 就绪
Step 2  执行 database/Initialize.sql                → 11 张基础表
Step 3  执行 database/migrations/V*.sql             → 按版本顺序执行迁移
Step 4  启动各微服务 (mvn spring-boot:run)          → 观察 Nacos 注册列表
Step 5  启动 gateway                                → http://localhost:8080
Step 6  测试: POST /api/auth/login                  → 获取 Token
Step 7  按需：docker compose --profile v1.1 up -d   → ES + MQ + XXL-Job + MinIO
Step 8  按需配置第三方平台（微信支付/阿里云短信/DeepSeek）
```

---

## 七、快速验证

```bash
# 1. 基础服务健康检查
curl http://localhost:8848/nacos/v1/console/health/readiness   # Nacos
redis-cli -h localhost ping                                     # Redis
mysql -h localhost -u root -proot123 -e "SELECT 1"              # MySQL

# 2. 认证测试（假设 authorization-service 在 9000 端口运行）
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"phone":"13800138000","password":"123456","loginType":"PASSWORD"}'

# 3. V1.1 中间件
curl http://localhost:9200                                      # ES
curl -u admin:admin123 http://localhost:15672/api/overview      # RabbitMQ
curl http://localhost:8088/xxl-job-admin                        # XXL-Job
curl http://localhost:9001                                      # MinIO Console
```
