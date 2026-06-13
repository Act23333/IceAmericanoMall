# 09 — 安全威胁建模方法 (Security Threat Modeling)

## 一、目的与产出

**本阶段解决什么问题**：在编码前识别系统的安全威胁面，针对认证、授权、数据保护、支付安全等关键领域建立防护策略，防止安全漏洞进入生产环境。

**输入**：Architecture Document + API Specification + Data Model

**输出**：

- 数据流图 (DFD) — 标注信任边界
- STRIDE 威胁清单（按威胁类型分类）
- 安全控制措施表（威胁 → 对策）
- OWASP Top 10 适用性评估

**产物文档类型**：Security Threat Model (参考 Microsoft STRIDE 方法论 + OWASP ASVS)

---

## 二、方法论步骤

### 步骤 1：绘制数据流图 (DFD) 并标注信任边界

以 Level 0（系统级）和 Level 1（服务级）两个粒度绘制数据流：

```
[浏览器] ──HTTPS──→ [API Gateway] ──内网──→ [微服务] ──→ [MySQL]
   │                    │                        │
   └── 信任边界 ────────┴────── 信任边界 ──────────┘
```

**信任边界**：数据从一个安全域进入另一个安全域的位置。每个信任边界就是威胁分析的焦点。

### 步骤 2：STRIDE 威胁建模

对 DFD 中每个穿越信任边界的数据流，按 STRIDE 六类威胁逐一分析：

| 威胁类型 | 含义 | 电商示例 |
|---------|------|---------|
| **S**poofing（仿冒） | 冒充身份 | 用他人 Token 下单 |
| **T**ampering（篡改） | 修改数据 | 修改订单金额后提交 |
| **R**epudiation（抵赖） | 否认操作 | 用户否认曾下单 |
| **I**nfo Disclosure（信息泄露） | 数据泄露 | 订单列表越权查看 |
| **D**oS（拒绝服务） | 服务不可用 | 恶意刷接口耗尽资源 |
| **E**levation（权限提升） | 越权操作 | 普通用户调用管理员接口 |

### 步骤 3：匹配安全控制措施

每项威胁至少对应一个控制措施：

| 威胁 | 控制措施 | 实现方式 |
|------|---------|---------|
| 仿冒 | JWT 签名验证 + 过期检查 | Spring Security + JJWT RS256 |
| 篡改 | 请求签名 / HTTPS / 幂等令牌 | 幂等键 + TLS 1.3 |
| 抵赖 | 审计日志（谁在何时做了什么） | AOP + 操作日志表 |
| 信息泄露 | 横向越权检查 + 数据脱敏 | Manager 层校验资源归属 |
| DoS | 限流 + 熔断 + 降级 | @RateLimit + Sentinel |
| 权限提升 | RBAC 鉴权 + /internal/** Gateway 拦截 | Spring Security + Gateway 路由规则 |

### 步骤 4：OWASP Top 10 适用性评估

逐项评估本项目风险：

| OWASP Top 10 | 本项目风险 | 应对 |
|-------------|-----------|------|
| Broken Access Control | **高** — 订单/地址等资源越权 | Manager 层统一做归属校验 |
| Cryptographic Failures | **中** — 密码、Token 密钥管理 | BCrypt + RS256 JWKS |
| Injection | **高** — 搜索、排序字段拼接 | MyBatis 参数化查询 + 输入白名单 |
| Insecure Design | **中** — 未做威胁建模 | 本文档即为应对 |
| Security Misconfiguration | **中** — CORS、错误堆栈泄露 | 生产关闭 stacktrace |
| Vulnerable Components | **低** — 版本锁定 | Dependabot 定期扫描 |
| Identification Failures | **高** — 弱密码、爆破攻击 | 密码强度 + 限流 + 验证码 |
| Software Integrity Failures | **低** | CI/CD 签名校验 |
| Logging & Monitoring Failures | **中** | 操作审计 + 异常告警 |
| SSRF | **低** — 无外部 URL 抓取需求 | Gateway 出站白名单 |

---

## 三、冰美商城实践示例

### 信任边界图（下单支付场景）

```
用户手机/PC ──(信任边界1)──→ API Gateway ──(内网)──→ trade-service
                                 │                      │
                          /internal/** 禁止外网       ├──→ user-service (获取地址)
                                                      ├──→ cart-service (获取选中商品)
                                                      ├──→ item-service (锁库存)
                                                      └──→ pay-service ──(信任边界2)──→ 微信支付/支付宝
```

**信任边界 1**：所有外部请求进入系统 → 认证 + 限流 + 参数校验
**信任边界 2**：内部服务调用外部支付 → 回调签名验证 + 金额二次核对

### 关键威胁与对策（节选）

| # | 威胁 | STRIDE | 利用场景 | 对策 |
|---|------|--------|---------|------|
| 1 | 横向越权查看他人订单 | Information Disclosure | 修改请求中的 userId 参数 | Manager 层校验 `order.userId == currentUser.id` |
| 2 | 重复提交订单 | Tampering | 使用相同 requestId 多次提交 | 幂等令牌 + Redis 去重 |
| 3 | 支付回调伪造 | Spoofing | 攻击者模拟支付成功回调 | 回调签名验证 + IP 白名单 |
| 4 | 短信接口被刷 | DoS | 恶意调用发送验证码接口 | @RateLimit(limit=1, period=60) |
| 5 | 日志泄露用户手机号 | Info Disclosure | 日志中明文记录手机号 | 日志脱敏：`138****5678` |
| 6 | 管理员接口被普通用户调用 | Elevation | 直接请求 /admin/** 路径 | Gateway 路由级权限拦截 |

---

## 四、常见错误与检查清单

### 容易犯的错误

1. **只考虑外部攻击不考虑内部威胁**：内部服务间调用也需要认证（Feign 拦截器传递 Trace Token）
2. **回调接口不做签名验证**：信任 HTTP Header 中的参数 → 必须验证签名
3. **错误信息暴露内部细节**：生产环境返回 `Stacktrace` → 配置 `server.error.include-stacktrace=never`
4. **日志中明文记录敏感字段**：密码、手机号、身份证号直接打印 → 日志脱敏
5. **不做越权检查**：只在 Controller 校验登录态，不校验资源归属

### 完成后的自检清单

- [ ] DFD 标注了所有信任边界
- [ ] 每条跨边界数据流做了 STRIDE 分析
- [ ] 高危威胁（资金/认证/数据泄露）有明确对策
- [ ] OWASP Top 10 逐项评估完成
- [ ] 支付回调有签名验证
- [ ] Gateway 禁止 `/internal/**` 外网访问
- [ ] 日志脱敏规则已定义
- [ ] API 限流策略与安全威胁匹配

### 本阶段完成定义 (DoD)

- [ ] STRIDE 威胁清单通过安全评审
- [ ] 所有高危威胁的对策已在 Architecture/API Spec 中体现
- [ ] OWASP 评估记录可追溯
- [ ] 安全控制措施在 Security-Model.md (project-docs) 中记录

---

## 五、与后续阶段的衔接

- **输出到 Phase 10 (测试策略)**：威胁 → 安全测试用例（越权测试、注入测试、回调签名测试）
- **输出到 Phase 11 (开发执行)**：安全控制措施 → constraints.md 中的安全检查清单
- **输出到 CI/CD**：OWASP 依赖扫描 + SAST 集成
