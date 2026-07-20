# 14 — 服务配置指南

> 从零搭建 IceAmericanoMall 所需的全部组件：Docker 容器编排、中间件配置、第三方平台注册、微服务环境变量。

---

## 一、架构全景

```
┌──────────────────────────────────────────────────────────────┐
│                    第三方平台 (需注册获取密钥)                    │
│  微信支付 │ 阿里云短信 │ 极验验证 │ DeepSeek API               │
└──────────────────────────────────────────────────────────────┘
                           ↑ HTTPS (Nginx 反向代理)
┌──────────────────────────────────────────────────────────────┐
│                  自建中间件 (Docker 一键编排)                    │
│  Nginx │ MySQL │ Redis │ Nacos │ ES │ RabbitMQ │ XXL-Job │ MinIO │
└──────────────────────────────────────────────────────────────┘
                           ↑ 内部网络 (172.20.0.0/16)
┌──────────────────────────────────────────────────────────────┐
│             12 个微服务 (Spring Boot + Nacos 注册发现)          │
│  gateway │ auth │ user │ item │ cart │ trade │ pay │ ...     │
└──────────────────────────────────────────────────────────────┘
```

**设计原则：**

- 容器间通过 `backend` 网络通信，宿主机仅暴露必要端口
- 全部中间件启用健康检查，`depends_on` 保证启动顺序
- 数据持久化：MySQL/Redis/ES/RabbitMQ/MinIO 均挂载独立 volume
- 资源限制：每个容器设置 CPU/内存上限，防止单点资源耗尽
- 日志轮转：json-file driver，单文件 50MB，保留 3 个

---

## 二、快速开始

```bash
# 1. 复制环境变量模板
cd Implementation/back-end
cp .env.example .env

# 2. 编辑 .env，修改所有 changeit_* 密码

# 3. 启动基础设施（MySQL + Redis + Nacos）
docker compose up -d

# 4. 等待健康检查通过
docker compose ps    # STATUS 全部显示 healthy

# 5. 启动全部中间件（ES + RabbitMQ + XXL-Job + MinIO + Nginx）
docker compose --profile v1.1 --profile v1.2 up -d

# 6. 初始化中间件数据库表（Nacos + XXL-Job）
docker compose exec mysql mysql -u root -p${MYSQL_ROOT_PASSWORD} icedamericano_mall < database/middleware/nacos-mysql.sql
docker compose exec mysql mysql -u root -p${MYSQL_ROOT_PASSWORD} icedamericano_mall < database/middleware/xxl-job-mysql.sql

# 7. 启动微服务（在 IDE 或命令行分别启动）
```

---

## 三、服务端口规划

| 组件        | 容器名                   | 端口             | 网络      | 说明           |
| --------- | --------------------- | -------------- | ------- | ------------ |
| Nginx     | ia-nginx              | **80**         | 宿主机     | 反向代理 + 限流    |
| Gateway   | gate-service          | **8080**       | backend | API 统一入口     |
| Auth      | authorization-service | **9000**       | backend | OAuth2 + JWT |
| User      | user-service          | **8080**       | backend | 同端口，分实例部署    |
| Item      | item-service          | **8082**       | backend | 商品/类目/SKU    |
| Cart      | cart-service          | **8083**       | backend | 购物车          |
| Trade     | trade-service         | **8084**       | backend | 订单/售后/结算     |
| Pay       | pay-service           | **8085**       | backend | 支付回调         |
| Search    | search-service        | **8086**       | backend | ES 搜索        |
| Logistics | logistics-service     | **8087**       | backend | 物流           |
| AI        | ai-service            | **8089**       | backend | AI 助手        |
| Marketing | marketing-service     | **8090**       | backend | 优惠券+秒杀       |
| MySQL     | ia-mysql              | **3306**       | 宿主机     | 数据库          |
| Redis     | ia-redis              | **6379**       | 宿主机     | 缓存+限流        |
| Nacos     | ia-nacos              | **8848/9848**  | 宿主机     | 注册+配置中心      |
| ES        | ia-elasticsearch      | **9200/9300**  | 宿主机     | 搜索引擎         |
| RabbitMQ  | ia-rabbitmq           | **5672/15672** | 宿主机     | 消息队列/管理      |
| XXL-Job   | ia-xxl-job            | **8088**       | 宿主机     | 调度中心         |
| MinIO     | ia-minio              | **9000/9001**  | 宿主机     | 对象存储/控制台     |

---

## 四、自建中间件 — 详细配置

### 4.1 MySQL 9.0

**为什么需要：** 全部业务数据存储（用户/商品/订单/支付等 11+ 张表）

**Docker 配置要点：**

| 配置项    | 说明                                                        |
| ------ | --------------------------------------------------------- |
| 资源限制   | CPU 2 核 / 内存 1G（生产建议 4C8G）                                |
| 持久化    | `mysql-data` volume → `/var/lib/mysql`                    |
| 配置文件   | `docker/mysql/conf.d/custom.cnf` 挂载到 `/etc/mysql/conf.d/` |
| Binlog | ROW 格式，server-id=1，为主从复制和 Canal CDC 做准备                   |
| 健康检查   | `mysqladmin ping`，5 次重试，30s 启动等待                          |
| 字符集    | utf8mb4 + utf8mb4_unicode_ci                              |

**my.cnf 关键参数：**

```ini
innodb_buffer_pool_size=512M            # 生产建议为物理内存 50%-70%
innodb_flush_log_at_trx_commit=2        # 性能优先（1=最安全，2=性能好）
max_connections=500                     # 预留给 12 个微服务的连接池
binlog_format=ROW                       # 为 Canal CDC 主从同步准备
slow_query_log=1                         # 慢查询监控
```

**初始化：**

```bash
# 自动执行 (Docker 首次启动)
# database/Initialize.sql → /docker-entrypoint-initdb.d/01-init.sql
# 手动执行迁移:
docker compose exec mysql mysql -u root -p icedamericano_mall < database/migrations/V1.1__add_points.sql
# ... 按版本号顺序执行
```

**分布式扩展：**

- **读写分离**：配置 MySQL Router 或 ShardingSphere 读写分离
- **主从复制**：添加 MySQL 从库容器，配置 `replica_host` + `CHANGE MASTER TO`
- **分库分表**：V2.0 计划引入 ShardingSphere（触发条件：单表 > 500 万行）
- **CDC 同步**：`binlog_format=ROW` 已就绪，接入 Canal 即可实时同步到 ES/Redis

**需要提供的配置信息：**

| 配置键                   | 示例值                                      | 来源        |
| --------------------- | ---------------------------------------- | --------- |
| `MYSQL_ROOT_PASSWORD` | `changeit_root_2024`                     | `.env` 文件 |
| `MYSQL_DATABASE`      | `icedamericano_mall`                     | `.env` 文件 |
| 连接地址                  | `mysql:3306`（容器内）/ `localhost:3306`（宿主机） | Docker 网络 |

---

### 4.2 Redis 7

**为什么需要：** Token 缓存、验证码存储、签到 Bitmap、限流计数器（Lua 脚本）、分布式锁（Redisson）、浏览历史

**Docker 配置要点：**

| 配置项  | 说明                                                            |
| ---- | ------------------------------------------------------------- |
| 资源限制 | CPU 1 核 / 内存 512MB                                            |
| 持久化  | `redis-data` volume → `/data`，RDB(changes) + AOF(everysec) 双写 |
| 配置文件 | `docker/redis/redis.conf` 挂载                                  |
| 淘汰策略 | `allkeys-lru`（内存满时淘汰最少使用的 key）                                |
| 连接   | maxclients 10000，tcp-keepalive 300s                           |

**Redis 持久化策略：**

```
RDB (快照):    900秒内 ≥1次变更 / 300秒≥10次 / 60秒≥10000次
AOF (增量):    每秒 fsync 一次 (everysec)，rewrite 触发: 增长 100% 且 ≥64MB
```

**分布式扩展：**

- **哨兵模式 (Sentinel)**：添加 3 个 Sentinel 容器，实现自动故障转移
- **集群模式 (Cluster)**：3 主 3 从，支持水平扩展
- **读写分离**：从节点 `replica-read-only yes` 用于读操作

**需要提供的配置信息：**

| 键                | 默认值                                      | 说明   |
| ---------------- | ---------------------------------------- | ---- |
| `REDIS_PASSWORD` | 无                                        | 生产必设 |
| 连接地址             | `redis:6379`（容器内）/ `localhost:6379`（宿主机） | —    |

---

### 4.3 Nacos 2.4（注册中心 + 配置中心）

**为什么需要：** 12 个微服务的服务发现、健康检查、配置热更新

**Docker 配置要点：**

| 配置项  | 说明                                                 |
| ---- | -------------------------------------------------- |
| 资源限制 | CPU 1 核 / 内存 1G                                    |
| 鉴权   | `NACOS_AUTH_ENABLE=true`，token + identity key 双重验证 |
| 持久化  | MySQL 独立库 `nacos_config`（12 张表，执行 `database/middleware/nacos-mysql.sql`） |
| 配置文件 | `docker/nacos/conf/application.properties`           |
| 健康检查 | `curl /nacos/v1/console/health/readiness`，10 次重试   |

**首次启动前必须初始化表结构：**

```bash
docker compose exec mysql mysql -u root -p${MYSQL_ROOT_PASSWORD} < database/middleware/nacos-mysql.sql
```

**访问地址：**

- 控制台: `http://localhost:8848/nacos`
- 默认用户名/密码: `nacos` / `nacos`
- 服务列表: `http://localhost:8848/nacos/#/serviceManagement`

**分布式扩展：**

- **集群模式**：部署 3 个 Nacos 节点，配置 `nacos/conf/cluster.conf`
- **Nginx 负载均衡**：`upstream nacos_cluster { server nacos1:8848; server nacos2:8848; server nacos3:8848; }`

**需要提供的配置信息：**

| 键                  | 默认值                           | 说明               |
| ------------------ | ----------------------------- | ---------------- |
| `NACOS_AUTH_TOKEN` | 自动生成                          | JWT 签名密钥，≥ 32 字符 |
| 控制台地址              | `http://localhost:8848/nacos` | 开发环境             |

---

### 4.4 ElasticSearch 7.17（V1.1 profile）

**为什么需要：** 商品全文检索（IK 分词）、向量检索（V1.2）、日志聚合（ELK）

**Docker 配置要点：**

| 配置项  | 说明                                                           |
| ---- | ------------------------------------------------------------ |
| 资源限制 | CPU 2 核 / 内存 2G（生产建议 4C8G+）                                  |
| 持久化  | `es-data` volume → `/usr/share/elasticsearch/data`           |
| 配置文件 | `docker/es/elasticsearch.yml`                                |
| 内存锁定 | `bootstrap.memory_lock=true`（禁用 swap，提高性能）                   |
| 快照备份 | `es-snapshots` volume → `/usr/share/elasticsearch/snapshots` |
| 健康检查 | `curl /_cluster/health` 返回 green 或 yellow                    |

**分布式扩展：**

- 改为 3 节点集群：`discovery.type` 改为 `discovery.seed_hosts` 配置
- 索引分片策略：每个索引 3 primary + 1 replica

**需要提供的配置信息：**

| 键              | 默认值              | 说明                 |
| -------------- | ---------------- | ------------------ |
| `ES_JAVA_OPTS` | `-Xms1g -Xmx1g`  | JVM 堆内存，≤ 物理内存 50% |
| HTTP 地址        | `localhost:9200` | 开发环境               |

---

### 4.5 RabbitMQ 3.13（V1.1 profile）

**为什么需要：** 领域事件异步解耦（订单创建→支付、支付成功→物流、发货→通知）

**Docker 配置要点：**

| 配置项  | 说明                                           |
| ---- | -------------------------------------------- |
| 资源限制 | CPU 1 核 / 内存 512MB                           |
| 持久化  | `rabbitmq-data` volume → `/var/lib/rabbitmq` |
| 内存阈值 | `VM_MEMORY_HIGH_WATERMARK=0.6`（使用 60% 内存时限流） |
| 磁盘阈值 | `DISK_FREE_LIMIT=2GB`（剩余 < 2GB 时拒绝写入）        |

**需要提供的配置信息：**

| 键                       | 默认值                      | 说明   |
| ----------------------- | ------------------------ | ---- |
| `RABBITMQ_DEFAULT_USER` | `admin`                  | 用户名  |
| `RABBITMQ_DEFAULT_PASS` | `admin123`               | 密码   |
| 管理控制台                   | `http://localhost:15672` | 开发环境 |

---

### 4.6 XXL-Job Admin 2.4.2（V1.1 profile）

**为什么需要：** 分布式任务调度，替换 `@Scheduled` 避免多实例重复执行

**Docker 配置要点：**

| 配置项  | 说明                                                 |
| ---- | -------------------------------------------------- |
| 资源限制 | CPU 0.5 核 / 内存 512MB                               |
| 持久化  | MySQL 存储任务数据（7 张表，执行 `database/middleware/xxl-job-mysql.sql`） |
| 控制台  | `http://localhost:8088/xxl-job-admin`                |

**首次启动前必须初始化表结构：**

```bash
docker compose exec mysql mysql -u root -p${MYSQL_ROOT_PASSWORD} icedamericano_mall < database/middleware/xxl-job-mysql.sql
```

**执行器注册（微服务侧）：**
配置 `xxl.job.enabled=true` + `xxl.job.executor.appname=trade-service` 后，执行器会自动注册到调度中心。在 XXL-Job 控制台中添加任务（JobHandler 对应 `@XxlJob("cancelTimeoutOrders")` 中的名称）。

**需要提供的配置信息：**

| 键                      | 默认值                                   | 说明                   |
| ---------------------- | ------------------------------------- | -------------------- |
| 控制台                    | `http://localhost:8088/xxl-job-admin` | 用户名 `admin` / 密码 `123456` |
| `XXL_JOB_ACCESS_TOKEN` | `iamall_token`                        | 执行器通信 token，需与微服务侧一致  |

---

### 4.7 MinIO（V1.1 profile）

**为什么需要：** 商品图片、用户头像、商家 Logo 对象存储

**需要提供的配置信息：**

| 键                     | 默认值                     | 说明        |
| --------------------- | ----------------------- | --------- |
| `MINIO_ROOT_USER`     | `minioadmin`            | 用户名       |
| `MINIO_ROOT_PASSWORD` | `minioadmin`            | 密码        |
| 控制台                   | `http://localhost:9001` | 管理 Bucket |

---

### 4.8 Nginx（V1.2 profile）

**为什么需要：** 反向代理 + 限流 + TLS 终结

**配置示例**：`docker/nginx/conf.d/gateway.conf`

```nginx
upstream gateway_cluster {
    least_conn;
    server gate-service-1:8080;
    keepalive 32;
}
limit_req_zone $binary_remote_addr zone=api_limit:10m rate=100r/s;
```

---

## 五、第三方平台配置

### 5.1 微信支付（pay-service）

**注册步骤：**

1. [微信支付商户平台](https://pay.weixin.qq.com/) → 注册企业/个体工商户
2. 「账户中心 → API安全」→ 设置 APIv3 密钥（32 位随机字符串）
3. 下载商户证书 → 上传到服务器 `/etc/wechat/apiclient_key.pem`
4. 获取商户号、证书序列号

**环境变量（.env）：**

| 变量                           | 说明       | 从哪里获取                                                        |
| ---------------------------- | -------- | ------------------------------------------------------------ |
| `WECHAT_PAY_MERCHANT_ID`     | 商户号      | 商户平台首页                                                       |
| `WECHAT_PAY_MERCHANT_SERIAL` | 证书序列号    | API安全 → 证书管理                                                 |
| `WECHAT_PAY_API_V3_KEY`      | APIv3 密钥 | 自行设置（32位）                                                    |
| `WECHAT_PAY_NOTIFY_URL`      | 回调地址     | 你的域名，如 `https://api.your-domain.com/api/pay/callback/wechat` |

**本地开发：** `wechat.pay.merchant-id=false` → 自动使用 Mock 支付客户端

---

### 5.2 阿里云短信（user-service）

**注册步骤：**

1. [阿里云短信控制台](https://dysms.console.aliyun.com/) → 开通服务
2. 「国内消息 → 签名管理」→ 申请短信签名（显示在短信开头，如「冰美式商城」）
3. 「国内消息 → 模板管理」→ 申请验证码模板（包含 `${code}` 变量）
4. RAM 访问控制 → 创建 AccessKey（AccessKey ID + Secret）

**需要填写的配置：**

| 配置项                        | 从哪里获取   | 示例值                      |
| -------------------------- | ------- | ------------------------ |
| `ALIYUN_ACCESS_KEY_ID`     | RAM 控制台 | `LTAI5tXXXXXXXXXXXX`     |
| `ALIYUN_ACCESS_KEY_SECRET` | RAM 控制台 | `xxxxxxxxxxxxxxxxxxxxxx` |
| `ALIYUN_SMS_SIGN_NAME`     | 签名审核通过  | `冰美式商城`                  |
| `ALIYUN_SMS_TEMPLATE_CODE` | 模板审核通过  | `SMS_123456789`          |

**本地开发：** `aliyun.sms.enabled=false` → 使用 MockSmsClient（仅打印日志）

---

### 5.3 极验人机验证（user-service）

**注册步骤：**

1. [极验官网](https://www.geetest.com/) → 注册 → 创建应用
2. 获取 `captcha-id` 和 `key`

**配置：** `geetest.captcha-id` / `geetest.key`

---

### 5.4 AI 服务配置（ai-service）

> 完整选型与架构见 [13-AI-Technology-Selection.md](./13-AI-Technology-Selection.md)

#### 5.4.1 DeepSeek API（主模型）

**注册步骤：**

1. [DeepSeek 开放平台](https://platform.deepseek.com/) → 注册 → API Keys
2. 设置 `DEEPSEEK_API_KEY=sk-xxx`（.env 文件）

**计费：** 约 ¥1/百万 tokens（deepseek-chat 模型）

**本地开发：** `ai.enabled=false` → AI 服务返回「未启用」提示

#### 5.4.2 通义千问（备用模型，V3.0）

| 配置项 | 说明 |
|--------|------|
| `QWEN_API_KEY` | 阿里云 DashScope API Key |
| `QWEN_BASE_URL` | `https://dashscope.aliyuncs.com/compatible-mode/v1` |
| `QWEN_MODEL` | `qwen-max`（生产）/ `qwen-turbo`（低成本场景） |

**使用场景**：中文复杂电商 query 的 fallback（约占 20% 流量），详见混合路由策略。

#### 5.4.3 Langfuse 可观测性（V2.5）

| 配置项 | 说明 |
|--------|------|
| `LANGFUSE_PUBLIC_KEY` | Langfuse 项目公钥 |
| `LANGFUSE_SECRET_KEY` | Langfuse 项目密钥 |
| `LANGFUSE_HOST` | 自部署地址（默认 `http://localhost:3000`） |
| `LANGFUSE_ENABLED` | 是否启用（默认 `true`，`ai.enabled=false` 时自动关闭） |

**部署**：Docker Compose 一键启动 Langfuse（`docker compose --profile observability up langfuse`）

#### 5.4.4 RAG 组件配置（V2.5）

| 组件 | 配置方式 | 说明 |
|------|---------|------|
| ES 向量索引 | `spring.elasticsearch.*` | 复用已有 ES，`dense_vector` 字段 dims=1536 |
| BGE-Reranker | Docker 独立服务 | `bge-reranker-v2-base`, FastAPI + sentence-transformers, ~200MB 显存 |
| Embedding (API) | `spring.ai.openai.embedding.*` | DeepSeek Embedding API，模型 `text-embedding-3-small`，1536维 |

**V3.0 迁移到 Milvus**：配置 `spring.ai.vectorstore.milvus.*`（host/port/collection），Milvus 容器通过 `docker compose --profile ai up milvus` 启动。

---

## 六、密钥安全模型

### 6.1 设计目标：.env 作为统一入口

```
                     ┌─────────────┐
                     │   .env 文件  │  (不在 Git，.gitignore 保护)
                     └──────┬──────┘
                            │ docker compose 自动注入
          ┌─────────────────┼─────────────────┐
          ▼                 ▼                  ▼
   ┌──────────────┐ ┌──────────────┐ ┌──────────────┐
   │ Docker 容器   │ │ Docker 容器   │ │ Docker 容器   │
   │ MySQL/Redis  │ │ Nacos/ES/MQ  │ │ Spring Boot  │
   │ (密码)       │ │ (密码)       │ │ (API密钥等)  │
   └──────────────┘ └──────────────┘ └──────────────┘
```

**容器化后**：所有变量（容器密码 + API 密钥）都在 `.env` 中，docker compose 统一注入到所有容器。
Spring Boot 的 `application.yml` 通过 `${WECHAT_PAY_MERCHANT_ID}` 等占位符读取。

### 6.2 当前过渡期：非容器化服务需额外步骤

如果你的 Spring Boot 服务还没进 docker-compose（还在 IDE 或 `mvn spring-boot:run`），需要把 `.env` 中的 API 密钥单独 export：

```bash
# 快速方案：source .env 到当前 shell
set -a && source .env && set +a
mvn spring-boot:run

# 或 IDE 中配置 Environment Variables
# IntelliJ: Run → Edit Configurations → EnvFile → 选择 .env 文件
```

### 6.3 生产环境安全

| 方案 | 适用场景 | 说明 |
|------|---------|------|
| K8s Secret | K8s 部署 | `kubectl create secret generic icedmall-secrets --from-literal=api-key=xxx` |
| Vault | 企业级 | 动态密钥轮换，审计日志，RBAC 权限 |
| Docker Secret | Docker Swarm | `docker secret create wechat_pay_key apiclient_key.pem` |
| 环境变量 | 单机/小团队 | 最简单，配合 systemd EnvironmentFile 使用 |

---

## 七、环境变量速查表

### 7.1 生产环境必设

| 变量                       | 层级 | 服务  | 默认值    |
| -------------------------- | ---- | ----- | --------- |
| `MYSQL_ROOT_PASSWORD`      | 容器 | MySQL | `root123` |
| `REDIS_PASSWORD`           | 容器 | Redis | 无        |
| `NACOS_AUTH_TOKEN`         | 容器 | Nacos | 自动      |
| `DEEPSEEK_API_KEY`         | 系统 | ai    | —         |
| `WECHAT_PAY_MERCHANT_ID`   | 系统 | pay   | —         |
| `WECHAT_PAY_API_V3_KEY`    | 系统 | pay   | —         |
| `KEYSTORE_PASSWORD`        | 系统 | auth  | `changeit` |

### 7.2 功能开关（全部默认关闭）

| 开关                                  | 开启后的行为                             |
| ----------------------------------- | ---------------------------------- |
| `aliyun.sms.enabled=true`           | 真实短信发送                             |
| `search.elasticsearch.enabled=true` | ES 全文检索（关闭 → DB LIKE）              |
| `rabbitmq.enabled=true`             | MQ 异步事件（关闭 → Feign 同步）             |
| `xxl.job.enabled=true`              | XXL-Job 调度（关闭 → @Scheduled）        |
| `sentinel.enabled=true`             | Sentinel 限流熔断（关闭 → @RateLimit AOP） |
| `minio.enabled=true`                | MinIO 真实存储（关闭 → Mock 占位 URL）       |
| `ai.enabled=true`                   | Spring AI Agent（关闭 → 降级提示）         |
| `seata.enabled=true`                | Seata 分布式事务（关闭 → 手动 Saga）          |
| `websocket.enabled=true`            | WebSocket 实时推送                     |

---

## 八、搭建顺序

```
Step 1  cp .env.example .env && 编辑密码
Step 2  docker compose up -d                      → MySQL + Redis + Nacos
Step 3  docker compose ps (确认全部 healthy)
Step 4  docker compose exec mysql mysql ... < database/Initialize.sql           → 11 张业务表
Step 5  docker compose exec mysql mysql ... < database/middleware/nacos-mysql.sql  → Nacos 12 张表
Step 6  执行 database/migrations/V*.sql             → 按版本顺序迁移
Step 7  docker compose --profile v1.1 up -d        → ES + MQ + XXL-Job + MinIO
Step 8  docker compose exec mysql mysql ... < database/middleware/xxl-job-mysql.sql → XXL-Job 7 张表
Step 9  mvn -f Implementation/back-end/pom.xml clean install -DskipTests
Step 10 按顺序启动微服务 (gateway/auth/user/item/cart/trade/pay/...)
Step 11 访问 http://localhost:8848/nacos           → 确认 12 个服务已注册
Step 12 配置第三方平台密钥 (.env) + 开启功能开关 (application.yml)
Step 13 测试: POST /api/auth/login → 获取 Token → 调用各接口
```

---

## 九、验证命令

```bash
# 基础服务
curl http://localhost:8848/nacos/v1/console/health/readiness         # Nacos
docker compose exec redis redis-cli -a $REDIS_PASSWORD ping           # Redis
docker compose exec mysql mysqladmin ping -u root -p$MYSQL_ROOT_PASSWORD  # MySQL

# 认证测试
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"phone":"13800138000","password":"123456","loginType":"PASSWORD"}'

# V1.1 中间件
curl http://localhost:9200/_cluster/health                            # ES
curl -u admin:admin123 http://localhost:15672/api/overview            # RabbitMQ
curl http://localhost:8088/xxl-job-admin                              # XXL-Job
curl http://localhost:9000/minio/health/live                          # MinIO

# 微服务健康 (需要 actuator)
curl http://localhost:8084/actuator/health                            # Trade Service
```

---

## 可观测性栈（V2.4，观测 + 指标 + 日志）

三件套以 docker-compose `observability` profile 交付（需 ES，建议同时启用 `v1.1`）：

```bash
# 启动观测栈（含 SkyWalking OAP/UI、Prometheus、Grafana、Logstash、Kibana）
docker compose --profile v1.1 --profile observability up -d
```

### 指标（Prometheus + Grafana）
- 各服务已内置 Actuator + Micrometer，暴露 `GET /actuator/prometheus`。
- Prometheus 抓取配置：`docker/prometheus/prometheus.yml`（宿主机运行的服务用 `host.docker.internal:<port>`，端口按各服务 `server.port` 调整）。
- Grafana：`http://localhost:3000`（admin/admin），数据源已自动指向 Prometheus；JVM 面板建议导入 Grafana Dashboard ID `4701`。
- 验证：`curl http://localhost:8080/actuator/prometheus`（gate）应返回指标；`curl http://localhost:8080/actuator/health` 健康检查。

### 链路追踪（SkyWalking）
- OAP 存储接现有 ES；UI：`http://localhost:8090`。
- 服务接入 Agent（非侵入，下载 `skywalking-agent` 后）：
  ```bash
  export JAVA_TOOL_OPTIONS="-javaagent:/path/skywalking-agent/skywalking-agent.jar \
    -Dskywalking.agent.service_name=user-service \
    -Dskywalking.collector.backend_service=127.0.0.1:11800"
  mvn -f Implementation/back-end/user-service -DskipTests spring-boot:run
  ```
- 应用日志已带 `[tid:...]`（`ia-common/logback-spring.xml` + `TraceIdFilter` 写 MDC），便于跨服务串联。

### 日志（ELK）
- 各服务统一日志格式（含 traceId）见 `ia-common/src/main/resources/logback-spring.xml`，落 `logs/<app>/app.log`。
- Logstash 管道 `docker/logstash/logstash.conf`（grok 解析 traceId → ES 索引 `ia-logs-*`）；Kibana：`http://localhost:5601`。
- 日志转发二选一：Filebeat 采集日志文件 → `beats:5044`；或应用侧加 TCP JSON appender → `tcp:5000`。

> 说明：观测栈为基础设施，构建（`mvn`）不校验其运行；上述为可运行的 compose + 配置交付，按需 `up` 即用。
