# 12 — 安全威胁模型

> 来源：`doc/methodology/09-security-modeling.md`（方法论模板）
> 关联：`02-Architecture.md`（安全架构）、`09-constraints.md`（安全约束）

---

## 一、数据流与信任边界

### Level 0 — 系统级

```
[用户浏览器/App] ──HTTPS──→ [IceAmericanoMall] ──→ [微信支付/支付宝]
        │                         │                       │
   信任边界 A                 内部网络               信任边界 B
```

### Level 1 — 服务级（下单支付链路）

```
手机/PC ──(边界A)──→ API Gateway ──内网──→ trade-service
                         │                      │
                  禁止外网访问               ├──→ user-service (获取地址)
                  /internal/**              ├──→ cart-service (获取选中商品)
                                            ├──→ item-service (锁库存)
                                            └──→ pay-service ──(边界B)──→ 微信支付/支付宝
```

**边界 A**：公网 → 系统入口
**边界 B**：内网 → 外部支付网关

---

## 二、STRIDE 威胁清单

### S — Spoofing（仿冒）

| # | 威胁 | 严重度 | 对策 |
|---|------|--------|------|
| S1 | 使用伪造 JWT 访问接口 | 🔴 高 | RS256 签名验证，公钥通过 JWKS 暴露，Gateway 统一验签 |
| S2 | 使用他人 Token 操作 | 🔴 高 | Token 中包含 userId，后端校验 Token 主体与资源归属一致 |
| S3 | 支付回调方伪造 | 🔴 高 | 回调签名验证 + 回调 IP 白名单 |

### T — Tampering（篡改）

| # | 威胁 | 严重度 | 对策 |
|---|------|--------|------|
| T1 | 修改请求中订单金额 | 🔴 高 | 后端从 DB 查询金额，不信任前端传入 |
| T2 | 修改请求中商品价格 | 🔴 高 | 价格以 item-service DB 为准，订单创建时做快照 |
| T3 | 重复提交订单 | 🔴 高 | requestId 幂等令牌 + Redis 去重（1h TTL） |
| T4 | 篡改支付回调参数 | 🔴 高 | HMAC-SHA256 签名校验 |

### R — Repudiation（抵赖）

| # | 威胁 | 严重度 | 对策 |
|---|------|--------|------|
| R1 | 用户否认曾下单 | 🟡 中 | 审计日志记录（userId + IP + UserAgent + 时间戳） |
| R2 | 商家否认曾发货 | 🟡 中 | 发货操作记入不可变日志表 |

### I — Information Disclosure（信息泄露）

| # | 威胁 | 严重度 | 对策 |
|---|------|--------|------|
| I1 | 横向越权查看他人订单 | 🔴 高 | Manager 层统一校验 `order.userId == currentUser.id` |
| I2 | 横向越权操作他人地址 | 🔴 高 | 同 I1，地址归属校验 |
| I3 | 日志泄露手机号/密码 | 🟡 中 | 日志脱敏：手机 `138****5678`，密码不记录 |
| I4 | 异常堆栈泄露内部结构 | 🟡 中 | 生产环境 `server.error.include-stacktrace=never` |
| I5 | 枚举用户是否存在 | 🟡 中 | 注册/登录返回统一模糊信息：「手机号或密码错误」 |
| I6 | DO 直接返回前端泄露密码哈希 | 🔴 高 | DO/DTO/VO 三层隔离，Controller 只返回 VO |

### D — Denial of Service（拒绝服务）

| # | 威胁 | 严重度 | 对策 |
|---|------|--------|------|
| D1 | 短信接口被恶意调用 | 🔴 高 | `@RateLimit(limit=1, period=60)` — 每 60s 仅1次 |
| D2 | 登录接口被爆破 | 🔴 高 | `@RateLimit(limit=5, period=60)` — 失败 5 次锁定 |
| D3 | 商品查询被刷 | 🟡 中 | `@RateLimit(limit=100, period=60)` + 缓存 |
| D4 | 大流量打垮 Gateway | 🟡 中 | Sentinel 限流 + 熔断降级 |

### E — Elevation of Privilege（权限提升）

| # | 威胁 | 严重度 | 对策 |
|---|------|--------|------|
| E1 | 普通用户调用管理员接口 | 🔴 高 | Gateway 拦截 `/admin/**`，校验角色为 ADMIN |
| E2 | 商家操作其他商家的商品 | 🔴 高 | Manager 层校验 `product.sellerId == currentUser.sellerId` |
| E3 | 通过 `/internal/**` 绕过认证 | 🔴 高 | Gateway 禁止 `/internal/**` 被外网访问 + 内网 Feign 拦截器 |

---

## 三、OWASP Top 10 适用性评估

| OWASP Top 10 (2021) | 本项目风险 | 应对措施 |
|---------------------|-----------|---------|
| A01: Broken Access Control | **高** | Manager 层资源归属校验 + Gateway 角色拦截 |
| A02: Cryptographic Failures | **中** | BCrypt + RS256 JWT + TLS 1.3 + JWT 密钥轮换（§五）+ PII 静态加密（§六） |
| A03: Injection | **高** | MyBatis-Plus 参数化查询 + 排序字段白名单校验 |
| A04: Insecure Design | **中** | 本文档（威胁建模）+ ADR 记录关键决策 |
| A05: Security Misconfiguration | **中** | 生产配置关闭 stacktrace + CORS 白名单 |
| A06: Vulnerable Components | **低** | Maven 版本锁定 + Dependabot 定期扫描 |
| A07: Identification Failures | **高** | 密码强度策略 + @RateLimit 防爆破 + 验证码 |
| A08: Software Integrity Failures | **低** | CI/CD 构建签名 |
| A09: Logging & Monitoring Failures | **中** | 操作审计表 + 异常告警（Prometheus Alert） |
| A10: Server-Side Request Forgery | **低** | 无外部 URL 抓取需求，Gateway 出站白名单 |

---

## 四、安全控制总览

| 控制层 | 机制 | 覆盖威胁 |
|--------|------|---------|
| 传输层 | HTTPS (TLS 1.3) | T1-T4, I1-I6 |
| 认证层 | JWT RS256 + JWKS | S1, S2 |
| 鉴权层 | RBAC + 资源归属校验 | E1, E2, I1, I2 |
| 输入层 | 参数校验 + 白名单 | A03-Injection |
| 限流层 | @RateLimit (Redis Lua) | D1-D3 |
| 数据层 | DO/DTO/VO 隔离 | I6 |
| 审计层 | 操作日志 + 不可变日志表 | R1, R2 |
| 运维层 | Gateway 路由控制 + 内部接口隔离 | E3 |
| 密钥层 | JWT 密钥轮换 + Keystore 密码管理 | S1, S2 |
| 加密层 | AES-256 PII 加密 + HMAC 辅助列 | A02, 个保法合规 |

---

## 五、密钥管理策略

### 5.1 JWT 签名密钥生命周期

| 阶段 | 周期 | 操作 |
|------|------|------|
| **生成** | 系统初始化 / 密钥泄露后 | `keytool -genkeypair -alias jwt-rsa -keyalg RSA -keysize 2048 -keystore authorization-server.p12` |
| **正常轮换** | 每 90 天 | 生成新密钥对 → 新旧密钥共存 24h（JWKS 同时暴露两把公钥）→ 旧密钥仅用于验签，新密钥用于签发 |
| **紧急吊销** | 发现泄露后立即 | 从 JWKS 移除泄露公钥 → 所有已签发 Token 立即失效 → 强制全量用户重登录 |
| **备份** | 每次轮换后 | Keystore 加密备份至安全存储（非代码仓库），恢复密钥需 2 人授权 |

### 5.2 密钥存储

```yaml
# authorization-service/src/main/resources/application.yml
jwt:
  keystore:
    path: classpath:keystore/authorization-server.p12  # 生产环境从外部挂载
    password: ${KEYSTORE_PASSWORD}   # 环境变量，永不写入配置文件
    alias: jwt-rsa
    key-password: ${KEY_PASSWORD}
```

**硬约束：**
- `.p12` 和 `.jks` 文件 **不得** 提交到 Git 仓库（已加入 `.gitignore`）
- 密码通过 K8s Secret / Vault 注入，**不得** 出现在 `application.yml` 中
- 开发/测试环境使用独立密钥对，**严禁** 共用生产密钥

### 5.3 Refresh Token 安全

| 措施 | 说明 |
|------|------|
| 存储 | Redis，Key = `refresh_token:{userId}:{deviceId}` |
| 有效期 | 7 天，每次使用后不续期（Refresh Token Rotation） |
| 复用检测 | 同一 Refresh Token 被使用 2 次 → 判定泄露 → 吊销该用户所有 Token |
| 登出清理 | 用户主动登出 → 立即删除 Redis 中的 Refresh Token |

---

## 六、数据静态加密

### 6.1 PII 敏感字段加密

以下字段在 MySQL 中 **不得明文存储**：

| 表 | 字段 | 加密方式 | 说明 |
|----|------|---------|------|
| user | phone | AES-256-CBC + 应用层加解密 | 手机号是登录凭证，泄露风险极高 |
| address | phone | AES-256-CBC | 收货电话 |
| address | receiver | AES-256-CBC | 收货人姓名 |
| address | detail | AES-256-CBC | 详细地址 |
| seller | contact_phone | AES-256-CBC | 商家联系电话 |

### 6.2 加密实现

```
加密: 明文 → AES-256-CBC(明文, DEK) → 密文存入 MySQL
解密: MySQL 读取 → AES-256-CBC(密文, DEK) → 明文

DEK (Data Encryption Key): 从 Key Management Service 获取
                             开发环境: 环境变量
                             生产环境: Vault / K8s Secret
```

### 6.3 搜索方案

加密后无法用 `WHERE phone = '138...'` 查询，使用 **HMAC 哈希辅助列**：

```sql
-- user 表额外维护一个 phone_hash 列用于登录查找
`phone_hash` VARCHAR(64) NOT NULL COMMENT 'HMAC-SHA256(phone, secret) — 用于唯一性校验和登录查找'
UNIQUE KEY `uk_phone_hash` (`phone_hash`)
```

查询流程：`用户输入手机号 → HMAC(手机号) → WHERE phone_hash = hash → 查到记录 → AES解密phone字段`

---

## 七、隐私合规（等保 2.0 + 个保法）

| 要求 | 落实措施 |
|------|---------|
| 数据分类分级 | PII（手机号/地址）→ 敏感级，加密存储；交易数据 → 机密级，访问控制 |
| 数据最小化 | 日志脱敏，不记录明文手机号/密码/Token |
| 用户数据可删除 | user 表支持软删除（`deleted` 字段），满足「被遗忘权」 |
| 访问审计 | 所有 `/admin/**` 操作 + PII 访问记录审计日志 |
| 导出限制 | 管理后台导出用户数据需二次验证 + 操作理由 |
