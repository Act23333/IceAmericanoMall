# ADR-066: HTTP 状态码与业务码的分层规范（判据 / 三看分工 / 映射派生 / 迁移路径）

## 背景

「什么时候回 200、什么时候回 HTTP 错误码」这条规则在本仓已存在，但**散落三处、无决策记录**：

| 口径载体 | 现状内容 | 性质 |
| :-- | :-- | :-- |
| `collaboration/09-constraints.md` §三·六 | 「错误承载的四类划分」（资源不存在 / 参数形态非法 / 未认证无权限 / 业务规则拒绝） | **约束**（不能做什么） |
| `collaboration/09-constraints.md` §三·八 | 跨服务错误传递铁律（透传优先 / fallback 只许降级 / 错误体五字段 / 状态码是被观测面） | **约束** |
| `contract/05-API-Specification.md` §2.1a | 「3xxx 不走 200 信封」的 15 项例外台账 | **契约**（对外承诺） |

三处**都不回答「为什么必须分层」**，因此在同一条规则上已经发生过 **5 次**独立修改（下表），每次只改一处、只留结论、不留理由：

| 批次 | 改了什么 | 依据性质 | 取证 |
| :-- | :-- | :-- | :-- |
| P2 / B7b / B8 | `3011 PRODUCT_NOT_FOUND` 由 `200` 改 `404`；SPU 详情与短码端点改抛 `1103`（真 404） | 端点级纠正 | `ErrorCode.java:132-149`（javadoc「状态码订正（2026-10-18，批次 B7b）」）、契约 §2.1a #13、`error-codes.ts:21-24` |
| 批次 ① | 12 个 not-found 业务码 `200 → 404`（`3009/3015/3016/3017/3019/3020/3032/3033/3034/3035/3036/3041`） | 码级纠正 | `ErrorCode.java:46-48`、契约 §2.1a |
| 批次 ④（`ADR-065`） | `3007` / `3008` `200 → 401`、`1017` `403 → 401`（登录失败唯一出口 + 抗枚举） | 安全决策 | `ErrorCode.java:89`、`:112`、`:35`；`ADR-065` §决策 1 |
| 批次 R2 | 退款命令面的「支付单/退款单不存在」由 `3009` 改抛 `1103`（**状态 404 → 404 未变**，只对齐码名） | 码名纠正 | 契约 §2.1a #1 与 §4.14.15b、版本行 V5.24.0 |
| **本 ADR** | `3001` / `3002` **决策为 `400`**（**未落地**，见 §决策 5） | 判据推出 | 本文 §决策 1 / §决策 5 |

**必须收口的直接动因**：把「200 承载的业务码」按当前枚举实测列出来后，**白名单里出现了 2 条与该判据正面冲突的条目**（`3001 CAPTCHA_ERROR` / `3002 CAPTCHA_EXPIRED` —— 验证码不符属「提交上来的凭证不能作为通过依据」，是**请求无法被处理**，却与「库存不足」共用 200）。若继续只留结论、不留判据，下一个改这行的人无从判断该改哪边。

本文是**收口**：给出判据、决策树、派生映射的源码事实、状态码的唯一含义、以及「为什么 23 条业务码仍在 200」的类别级理由与迁移纪律。**本文不重复** `09` §三·六/§三·八 的条款原文，也不重复 `ADR-004` / `ADR-062` / `ADR-063` / `ADR-065` 的结论（边界见 §与其他 ADR 的关系）。

> **计量口径**：本文所有计数都是 `Implementation/back-end/ia-common/.../ErrorCode.java` 与 `ErrorCategory.java` 的**实测解析结果**（脚本口径见 §参考），**不是手抄**：
> `ErrorCode` 共 **99** 项；按 `HttpStatus` 分布 `OK 23 / NOT_FOUND 21 / SERVICE_UNAVAILABLE 18 / BAD_REQUEST 14 / UNAUTHORIZED 8 / INTERNAL_SERVER_ERROR 7 / TOO_MANY_REQUESTS 4 / FORBIDDEN 2 / GATEWAY_TIMEOUT 2`；按 `ErrorCategory` 派生分布 `CLIENT 45 / TRANSIENT 27 / RULE 23 / BUG 4`。

## 决策

### 1. 核心判据：一句话 + 5 问决策树

> **判据不是「有没有出错」，而是「这次请求在协议层有没有被处理完」。**
>
> - 请求**无法被处理**（资源不存在 / 参数**或凭证**非法 / 没认证 / 无权 / 被限流 / 依赖坏了）→ **HTTP 错误码**
> - 请求**被正确处理**，只是**业务规则不同意**（余额不足 / 库存不足 / 状态不允许 / 已评价 / 限购）→ **200 + 业务码**

判据只问一句「**服务端能不能把这次请求当作一个合法请求执行到底**」。能执行到底、只是执行结论是「不行」的，属**业务规则的结论**，不是协议失败；压根执行不下去的（不知道你在说哪个资源、你说的东西不合法、你不被允许、我没力气了），属**协议失败**。

**「凭证无效」属「请求无法被处理」** —— 这是本文相对既有 `09` §三·六 的**补齐点**：§三·六 的四类划分只点名了「未认证 / 无权限」，没有点名「**提交上来的凭证本身不对**」（验证码不符 / 验证码过期 / 签名不匹配）。凭证校验不过意味着**这次请求在协议层没有被处理完**（服务端连"你是谁/你是不是本人"都没确认），因此它必须与「未认证」同侧走 HTTP 错误码，而**不能**与「余额不足」同侧走 200。由此推出：`3001 CAPTCHA_ERROR` / `3002 CAPTCHA_EXPIRED` 的 `HttpStatus` 应为 `400`（决策见 §决策 5，代码待落地）。

#### 5 问决策树（按顺序走，命中即停）

```text
Q1  请求被「识别」并「允许」到能执行业务的程度了吗？
    ├─ 没有携带身份凭证 / 凭证已失效 / 凭证不可用 ─────────→ 401
    │     （1011 未登录 / 1012 已过期 / 3007 登录失败唯一出口 / 3008 / 1017）
    ├─ 已识别身份，但无权访问该资源或需先完成人机验证 ────→ 403
    │     （1013 无权 / 1016 需人机验证挑战）
    └─ 是 ↓
Q2  请求所指的资源/实体在服务端存在吗？
    ├─ 按标识查不到（详情 / 单号 / 短码 / 配置项）─────────→ 404
    │     （1102 / 1103 / 3009 / 3011 / 3015 / 3016 / 3017 / 3019 / 3020 /
    │       3032 / 3033 / 3034 / 3035 / 3036 / 3041 / 3104 / 4013 / 4016 /
    │       4025 / 4043 / 4053 —— 共 21 条，实测见 §决策 3）
    └─ 存在 ↓
Q3  请求本身合法吗（参数形态/范围、以及**提交上来的凭证**是否有效）？
    ├─ 参数缺 / 类型错 / 形态非法 / 越界 ──────────────────→ 400（1101；网关级篡改 1015）
    ├─ 凭证不符 / 已过期（验证码、签名、金额合法性）────────→ 400
    │     （3001 / 3002 **决策为 400，未落地**；4005 / 4006 / 4011 / 4015 /
    │       4018 / 4023 / 4032 / 4033 / 4044 / 4045 / 4051 已是 400）
    └─ 合法 ↓
Q4  依赖可用吗（本服务有没有把这次调用做完）？
    ├─ 被限流 / 频率超限 ─────────────────────────────────→ 429（1014 / 3103 / 4003 / 4031）
    ├─ 上游不可用 / 超时 / 熔断 / 第三方故障 ───────────────→ 5xx
    │     （503：1001 / 3100 / 4xxx 多数；504：3101 / 4001；500 基础设施类走覆盖表）
    ├─ 本服务代码或数据缺陷（NPE / 契约违约 / 密钥配置错）──→ 500（1000 / 5000-5002）
    └─ 正常 ↓
Q5  以上全部通过 —— 请求合法、资源存在、依赖正常，只是业务规则不同意？
    └─ 是 ─────────────────────────────────────────────────→ 200 + body code
          （3000 兜底 / 3003 手机号已注册 / 3005 余额不足 / 3012 库存不足 /
            3010 状态不允许 / 3018 已评价 / 3022 已售罄 / 3023 超限购 / 3042 条目上限 …）
```

**两条使用纪律**：

1. **顺序不可调换**。若先问 Q5（"是不是业务规则不同意"），任何错误都能被解释成"规则不同意"——这正是本仓历史上把 not-found 写成 200 的成因。必须先排除"协议层能否处理完"。
2. **Q1 的 401 不得用来区分失败原因**（`ADR-065` §决策 1/2）：登录失败四类原因一律 `401 + 3007 + 中性文案`，区分原因等于把状态码本身变成账号枚举信道。

#### 与 `09` §三·六 四类划分的对应关系（不重述其条款）

| 本 ADR 决策树落点 | `09` §三·六 对应类 | 差异 |
| :-- | :-- | :-- |
| Q1 401 / 403 | 「未认证 / 无权限」 | 一致 |
| Q2 404 | 「资源不存在」 | 一致 |
| Q3 400 | 「参数/形态非法」 | **补齐**：原文该类只写「请求本身不合法（缺参、类型错、短码形态错、编码非法）」，**未覆盖"凭证不符"** |
| Q4 429 / 5xx | （§三·六 未单列，散落在 §三·八 与既有实现） | **显式化**：限流与依赖故障不从属「业务规则拒绝」 |
| Q5 200 + code | 「业务规则拒绝」 | 一致 |

### 2. 三看分工：谁消费什么（单一职责，不得越界消费）

| 消费者 | 看什么 | 用来决定 |
| :-- | :-- | :-- |
| 网关 / 缓存 / 熔断 / 重试 / 监控 / SLO | **HTTP 状态码** | 要不要重试、要不要熔断、成功率、能否缓存 |
| 前端 / 用户 | **`code`** | 显示什么文案、给什么引导动作 |
| 跨服务调用方（Feign） | **`category`** | **透传还是降级**（`09` §三·八·1/§三·八·3） |

**为什么必须三分而不是二分**：状态码是**协议层**事实（"这次调用成没成"），业务码是**语义层**事实（"不成的原因是什么"），`category` 是**处置层**事实（"谁该负责、能不能重试"）。三者消费者不同、变更频率不同、失效后果不同。把任意两个压成一个维度，就必然有一方被迫猜测。

**混用的代价（双向，均已在本仓发生过或具备发生条件）**：

| 混用方向 | 具体后果 | 本仓证据 |
| :-- | :-- | :-- |
| **用 200 承载 not-found** | ① 熔断器**永不触发**（resilience4j / Sentinel 只看非 2xx 与异常）；② 成功率指标**虚高**（失败被记成成功）；③ **404 被 CDN / ISR 缓存**（Next.js `serverFetch` 以 `revalidate` 秒数缓存响应，见 `packages/api/server.ts:63-71`）；④ 客户端重试策略**失效**（200 不触发重试）；⑤ 第三方 SDK 语义错位 | `09` §三·六「定性与纪律」1、§三·八·5；`ErrorCode.java:115-129`（3009 的整改理由） |
| **用 4xx 承载业务规则拒绝** | ① 监控**全线报警**（4xx 率是 SLO 错误预算的一部分，正常业务把预算烧光）；② 网关限流 / WAF 把**批量业务拒绝当攻击**；③ 调用方**误以为可重试**（而重试必然重复失败）；④ 前端 `!res.ok` 分支**丢掉 `data` 载荷** —— 本项目 `apiClient` 的 4xx 分支只保留 `{code,msg}`（`packages/api/client.ts:168-171`），"库存不足并告知可购数量"这类就地提示没有载荷可读 | `09` §三·六（业务规则拒绝 → 200 信封）；`client.ts:168-174` |
| **只看状态码猜语义** | 同一个 4xx 既可能是"参数错"也可能是"没权限"，调用方无法决定**透传还是降级** | `09` §三·八·3「调用方不得仅凭状态码猜测语义」 |

### 3. `HttpStatus → ErrorCategory` 映射（**源码取证，逐行**）

单一实现：`ia-common` 的 `ErrorCategory`（`ErrorCategory.java`，431 行）。派生函数入口三个：`of(ErrorCode)`、`of(HttpStatus)`、`ofStatus(int)`，以及统一判定 `of(Integer, Throwable)`。

#### 3.1 逐状态派生表（`ofStatus`，`ErrorCategory.java:243-261`）

| HTTP 状态 | `category` | `retryable` | 源码位置与判据 |
| :-- | :-- | :-- | :-- |
| `408` | **TRANSIENT** | `true` | `TRANSIENT_STATUSES`（`:103-108`）优先于 4xx 分支 —— 注释「即便它落在 4xx 区间，也不是调用方的语义错误」 |
| `429` | **TRANSIENT** | `true` | 同上（限流 → 退避重试） |
| `502` | **TRANSIENT** | `true` | 同上 |
| `503` | **TRANSIENT** | `true` | 同上 |
| `504` | **TRANSIENT** | `true` | 同上 |
| **其它所有 `5xx`**（`500/501/505/506/507/508/511`…） | **BUG** | `false` | `:247-251`：`status >= 500` → `BUG`，注释「能靠状态码区分出来的基础设施类已在 `TRANSIENT_STATUSES` 里」 |
| `400` | **CLIENT** | `false` | `:252-253`：`status >= 400` → `CLIENT` |
| `401` | **CLIENT** | `false` | 同上 |
| `403` | **CLIENT** | `false` | 同上 |
| `404` | **CLIENT** | `false` | 同上 |
| `405` | **CLIENT** | `false` | 同上（类注释 `:28` 显式列出 405） |
| `406 / 409 / 410 / 415 / 422 / 423 / 428 / 451`（及 4xx 其它） | **CLIENT** | `false` | 同上（除 408/429 已被前置分支拦走） |
| `2xx`（200–299） | **RULE** | `false` | `:255-258`：注释「这里是『200 信封承载的业务规则拒绝』（内部接口的唯一 2xx 错误形态）」 |
| `0 / 1xx / 3xx` | **TRANSIENT** | `true` | `:259-260`：不构成可判定语义 → 按未知处理，可重试，**绝不当成功** |
| `null`（无 HTTP 响应） | **TRANSIENT** | `true` | `:234`（`of(HttpStatus)`）、`:305`（`of(Integer,Throwable)`） |

> **`retryable` 是单向派生**：`retryable = (category == TRANSIENT)`，唯一实现 `isRetryable()`（`:201-204`）；`ErrorInfo.of(...)` 由 `category` 派生 `retryable` 而不接受手填（`ErrorInfo.java:76-79`、`:84-88`）。因此**不存在**「`category=CLIENT` 但 `retryable=true`」这种状态；下游报文若自相矛盾，由 `UpstreamErrorDecoder` 按 `category` 重新派生（`09` §三·八·3）。

#### 3.2 `TRANSIENT` 的两个来源

1. **状态码来源**：上表 5 个状态（`408/429/502/503/504`）+ `null` + `0/1xx/3xx`。
2. **异常类型来源**（**唯一**且**优先**）：`TRANSIENT_TYPE_NAMES` 共 **9** 个类型全名（`:158-167`）：

| 类型全名 | 何时出现 | 为何 TRANSIENT |
| :-- | :-- | :-- |
| `feign.RetryableException` | Feign 传输层失败（通常包装 `ConnectException`/`SocketTimeoutException`） | 对端没连上 / 没按时回 |
| `java.net.ConnectException` | TCP 连接被拒 / 不可达 | 实例重启、扩缩容窗口 |
| `java.net.SocketTimeoutException` | 读/连接超时 | 对端慢但可能不坏 |
| `java.io.IOException` | 网络类 IO 故障（在 Feign 路径上 IO 异常只来自传输层） | 同上 |
| `java.util.concurrent.TimeoutException` | 异步等待超时（resilience4j / CompletableFuture） | 同上 |
| `java.util.concurrent.RejectedExecutionException` | 线程池/执行器耗尽 | 过载而非缺陷 |
| `io.github.resilience4j.circuitbreaker.CallNotPermittedException` | **熔断打开** | 熔断本身即「暂时别打」 |
| `io.github.resilience4j.bulkhead.BulkheadFullException` | 隔舱（并发数）耗尽 | 过载 → 退避 |
| `com.alibaba.csp.sentinel.slots.block.BlockException` | Sentinel 流控/降级阻断（trade-service） | 限流 → 退避 |

**为什么按「类型全名」而不是 `instanceof`**：`ia-common` 是共享内核，**不声明** Feign / resilience4j / Sentinel 依赖，为分类而引入等于让每个服务被迫携带全部容错库（`:148-156`）。代价是「改名即失效」，故由 `ErrorCategoryTest`（真实 JDK 网络类）与 `UpstreamFailuresTest`（真实 `feign.RetryableException` / `CallNotPermittedException`）锁死。

#### 3.3 判定顺序（钉死）：**异常类型 → HTTP 状态码 → 500 才是 BUG**

```text
of(Integer httpStatus, Throwable cause)            [ErrorCategory.java:301-306]
  ├─ ① isTransientCause(cause) == true  → TRANSIENT   （:302-304）
  │      （连接/超时/熔断/池耗尽：这些**没有 HTTP 响应**，看状态码永远看不出来）
  ├─ ② httpStatus == null               → TRANSIENT   （:305，未知按可重试，绝不当成功）
  └─ ③ ofStatus(httpStatus)             → 派生        （:305）
        ├─ TRANSIENT_STATUSES 命中 → TRANSIENT
        ├─ >=500                  → BUG
        ├─ >=400                  → CLIENT
        ├─ 2xx                    → RULE
        └─ 其它                    → TRANSIENT

isTransientCause(Throwable)                         [ErrorCategory.java:319-336]
  沿 cause 链递归（Feign / resilience4j 装饰器会层层包装），
  每个节点沿**父类链**比对 TRANSIENT_TYPE_NAMES（:392-399，子类自动命中父类型名），
  带**防环**（身份集合 `IdentityHashMap` + `MAX_CAUSE_DEPTH = 16`，:170/:320-334）。
```

**为什么异常类型必须排在状态码之前**（`:68-74` 原文要点）：熔断打开、连接失败、读超时、线程池耗尽在 Feign 侧**根本不产生 HTTP 响应**，它们只以异常形式出现在降级路径上。若只看状态码，这些调用会被兜底成「未知」甚至「BUG」——「BUG 不可重试」直接堵死重试，把一次上游抖动升级成一次值班事故。

#### 3.4 覆盖表（`STATUS_AMBIGUOUS_OVERRIDES`）：**当前 3 条**

| # | 码 | 枚举名 | 该码 HTTP | 派生结果 | 覆盖为 | 为何不能靠状态码判定 |
| :-- | :-- | :-- | :-- | :-- | :-- | :-- |
| ① | `4007` | `THIRD_PARTY_RESPONSE_PARSE_ERROR` | `500` | `BUG` | **TRANSIENT** | 缺陷不在本服务，而是上游第三方返回了违约报文（截断/乱码/缺字段/非 JSON）；本服务防腐层只是如实上报，HTTP 层只能给 500。重试有机会拿到正常报文 |
| ② | `4008` | `THIRD_PARTY_UNKNOWN_ERROR` | `500` | `BUG` | **TRANSIENT** | 外部依赖出了未知状况，不是本服务代码缺陷；500 无法表达「责任在外部」 |
| ③ | `4999` | `THIRD_PARTY_UNDEFINED` | `500` | `BUG` | **TRANSIENT** | 语义本身即「外部依赖的未知状况」，与 ② 同口径 |

- **上限 `MAX_OVERRIDES = 5`**（`:118`，当前用 3 条，注释「留 2 格给『第三方故障』这类真实例外，越界即报错，逼着讨论派生规则本身」）。
- **启动期自检**（`:197-199` 静态初始化块 → `validateOverrides`，`:358-383`）：键必须非 `null`、**值不得与该码状态码的派生结果相同**（否则该条覆盖什么也没纠正）、条目数不得超过上限 —— 任一违反即 `IllegalStateException`，**应用启动失败**而非静默失效。
- **刻意不入表的反例**（`:426-428`）：`ENCRYPT_ERROR(5000)` / `DECRYPT_ERROR(5001)` / `HASH_ERROR(5002)` / `INTERNAL_ERROR(1000)` 都是**确定性失败**（密钥配置错或代码缺陷），重试必然重复失败，正该判 `BUG` 并告警。
- **`of(ErrorCode)` 先查表后派生**（`:216-225`）；`null` 码按 `TRANSIENT`（未知按可重试处理，与 `UpstreamErrorDecoder` 的未知降级口径一致，**绝不**当成功）。

#### 3.5 `category` 不按码段划分（实测反例）

把 99 个码按 `category` 实测分布如下（脚本口径见 §参考）——**同一码段内混有多种处置类别**，这正是"必须由状态码派生、不能按码段硬编码"的证据：

| 码段 | CLIENT | TRANSIENT | RULE | BUG | 说明 |
| :-- | :-- | :-- | :-- | :-- | :-- |
| `1xxx` | 9 | 2 | 0 | 1 | `1001 SYSTEM_BUSY(503)` 是 TRANSIENT，`1000 INTERNAL_ERROR(500)` 是 BUG |
| `3xxx` | 15 | 0 | **23** | 0 | 15 = 12 个 not-found + `3011` + `3007`/`3008` |
| `31xx`（AI） | 2 | 3 | 0 | 0 | `3102` 400=CLIENT，`3100`/`3101`/`3103` 是 TRANSIENT，`3104` 404=CLIENT |
| `4xxx`（第三方） | **19** | **22** | 0 | 0 | 同为「第三方」：`4004/4012/4021`（401）与 8 个 404 是 CLIENT，其余多数 5xx 是 TRANSIENT |
| `5xxx`（加解密） | 0 | 0 | 0 | **3** | 确定性失败，不可重试 |
| **合计** | **45** | **27** | **23** | **4** | — |

**推论（写代码时最容易踩错的一条）**：`4xxx` 段**不是**"一律可重试"。`THIRD_PARTY_AUTH_FAILED(4004/401)`、`WECHAT_OPENID_NOT_FOUND(4013/404)`、`ALIPAY_TRADE_NOT_FOUND(4025/404)` 都被派生为 **CLIENT / 不可重试**。要给"第三方故障可重试"下结论，**只能读 `category`/`retryable` 字段，不能读码段**。

#### 3.6 与 `09` §三·八 的一致性核对（**源码与文档相符，未发现矛盾**）

| `09` §三·八 的说法 | 源码事实 | 结论 |
| :-- | :-- | :-- |
| `category` 由 `HttpStatus` 派生 + 极短覆盖表（≤5 条、启动期自检） | `ofStatus` + `MAX_OVERRIDES=5` + `validateOverrides` + 静态块 | 相符 |
| 判定顺序「异常类型 → HTTP 状态码 → 500 才是 BUG」 | `of(Integer,Throwable)` 三分支（`:301-306`） | 相符 |
| `retryable = (category == TRANSIENT)` 单向派生 | `isRetryable()`（`:201-204`）+ `ErrorInfo.of` 派生 | 相符 |
| 内部错误体五字段 `code/msg/category/retryable/traceId` | `ErrorInfo` record + `@JsonProperty` 逐字钉名 + `@JsonInclude(ALWAYS)` | 相符（本次只读核对，未改代码） |
| 反例 `SpuDetailClient` 把 404 降级成 `SYSTEM_BUSY` | 已被 `I70`/`ADR-064` 迁内网改造处置 | 属历史缺陷，不在本文范围 |

### 4. 九个状态码的适用语义（禁止一事多码）

> **「一事多码」禁令的准确含义**：**同一个语义事件不得落两个不同的 HTTP 状态**（如「库存不足」既允许 200 又允许 409）。它**不**禁止同一状态下的多个业务码（`400` 有 `1015`/`1101`，`404` 有 21 条，`200` 有 23 条）—— 业务码承担的是**细粒度语义**，那正是它存在的理由。

#### `200 OK` —— 请求已被处理完（含「业务规则不同意」这一结论）
- **唯一含义**：服务端完整执行了这次请求，`Result.code` 是**执行结论**而不是**协议状态**。
- **承载（实测 23 条）**：`3000` 兜底总码 / `3003` 手机号已注册 / `3005` 余额不足 / `3010` 状态不支持 / `3012` 库存不足 / `3018` 已评价 / `3021`-`3024` 秒杀 / `3031` 售后已存在 / `3039` 退款状态 / `3040` 模板名重复 / `3042` 条目上限 / `3001`-`3002`（**待改 400**）/ 6 个 `@Deprecated` 死码（`3004/3013/3014/3030/3037/3038`）。
- **禁止用法**：❌ 承载 not-found（含"查不到"的任何形态）· ❌ 承载未认证/无权 · ❌ 承载参数非法 · ❌ 承载限流 · ❌ 承载依赖故障/超时 · ❌ 承载本服务代码缺陷。

#### `400 Bad Request` —— 请求本身不合法（含**凭证无效**）
- **唯一含义**：请求在语法/形态/范围/凭证层面不成立，**服务端连执行都没开始**。
- **承载（实测 14 条）**：`1101 PARAM_ERROR`（字段级校验 → 前端做字段高亮，`isParamError`）/ `1015 ILLEGAL_REQUEST`（请求级非法：网关判定被篡改/越界，前端**不**做字段高亮）/ `3102` 内容审核拦截 / `4005/4006/4011/4015/4018/4023/4032/4033/4044/4045/4051` 第三方参数与凭证类 / **`3001`/`3002`（决策为 400，未落地）**。
- **禁止用法**：❌ 用 400 承载"资源不存在"（应 404，否则前端会把「该商品已下架」显示成「请检查填写内容」）· ❌ 用 400 承载"业务规则拒绝"（会把服务端的账记到调用方头上，调用方无从修正）· ❌ 用 400 承载"服务端数据不一致"（`09` §三·六 子类已点名：那是 500 + `1000`）。

#### `401 Unauthorized` —— 未认证（身份凭证缺失或不可用）
- **唯一含义**：**你是谁**没被确认 —— 缺凭证、凭证失效、凭证不可用、认证尝试失败。
- **承载（实测 8 条）**：`1011` 未登录 / `1012` 已过期 / `1017`（退役，安全默认）/ `3007` 登录失败唯一出口 / `3008`（非登录域语义保留）/ `4004/4012/4021`（第三方认证失败 —— **已登记的语义拉伸**，见下）。
- **禁止用法**：❌ 用于"已认证但无权"（那是 403）· ❌ 用于业务规则拒绝 · ❌ **用 401 区分登录失败原因**（`ADR-065`：四类原因共用 `401 + 3007`）· ❌ 用 401 表达"账号不存在"（那是账号枚举信道）。
- **已登记的语义拉伸**：`4004 THIRD_PARTY_AUTH_FAILED` / `4012 WECHAT_AUTH_FAILED` / `4021 ALIPAY_AUTH_FAILED` 用 401 表达「**我们**对第三方的认证失败」。判据上它们是"请求无法被处理"（正确），但 401 的读者会以为是"**客户端**的凭证不对"。影响面已评估：前端 `isAuthError()` 按 **code**（`1011/1012`）判定而非按状态码（`error-codes.ts:333-335`），故不会误触发"跳登录"；**但按状态码分流的调用方（通用 SDK / 网关 / SRE 看板）会误判**。本文**保留**该现状（改码会破坏已发布的 401 语义与前端 99:99 对齐门禁），登记为已知代价。

#### `403 Forbidden` —— 身份已识别，但访问不被允许
- **唯一含义**：**我知道了你是谁**，但你（或当前请求所处的状态）没有被允许做这件事。
- **承载（实测 2 条）**：`1013 FORBIDDEN`（无权）/ `1016 CAPTCHA_REQUIRED`（需先完成人机验证挑战 —— **已登记的语义拉伸**：属"访问条件未满足"而非"无权"，`data` 携带 `{captcha_required, provider, captcha_id}`，前端必须渲染验证组件而不是弹提示）。
- **禁止用法**：❌ 用于未认证（401）· ❌ 用 403 承载"资源不存在"· ❌ 把"他人资源"统一掩盖成 403 —— **本项目的反枚举口径是"统一按不存在处理"**（`3019` 他人券 / `3034` 他人站内信 / `3041` 他人模板 → 404 + `*_NOT_FOUND`，见 `ErrorCode.java:236-238`、`:280-286`、`:331-338`）；唯一的例外是"店铺归属其他商家"→ **403**（`3015` javadoc `:190-196`：这是既有实现 `ShopManager#loadOwnedShop`，未用 not-found 掩盖）。

#### `404 Not Found` —— 资源不存在
- **唯一含义**：按请求给出的标识**查不到东西**（不是"你不许看"，也不是"规则不允许"）。
- **承载（实测 21 条）**：`1102` 用户 / `1103` 静态资源与通用 not-found / `3009` 订单 / `3011`（退役）/ `3015` 店铺 / `3016` SKU / `3017` 首页配置 / `3019` 优惠券 / `3020` 秒杀活动 / `3032` 售后申请 / `3033` 提现申请 / `3034` 站内信 / `3035` 物流 / `3036`（退役）/ `3041` 运费模板 / `3104` AI 会话 / `4013` 微信用户 / `4016` 微信支付订单 / `4025` 支付宝交易 / `4043` OSS 文件 / `4053` 物流公司。
- **禁止用法**：❌ 用 404 承载业务规则拒绝 · ❌ 用 404 承载"服务端数据不一致"（`09` §三·六 子类：资源存在但字段缺失 → **500 + 1000 + BUG**）· ❌ 用 404 承载"参数形态非法"（`ADR-062` §决策 7：形态非法前移到适配层 fail-fast → 400 + `1101`）· ❌ **回退到 200**（见 §决策 8 禁令 1）。
- **业务码为什么不合并成单个 `1103`**：前端仍需区分「订单不存在」「店铺不存在」「SKU 不存在」（`ErrorCode.java:120-124`）。

#### `405 Method Not Allowed` —— 方法不被该资源支持
- **唯一含义**：路径存在、动词不对。
- **承载**：**无业务码**（由 Spring MVC 框架层产生），`ErrorCategory` 判为 `CLIENT`（`ErrorCategory.java:28`）。
- **禁止用法**：❌ 不得为 405 编写业务码（它永远是框架事实，不是业务结论）。

#### `409 Conflict` —— **本项目刻意不用于业务规则拒绝**
- **唯一含义（REST 通用语义）**：请求与资源**当前状态**冲突（并发写、唯一键占用、状态机不允许）。
- **本项目实测**：`ErrorCode` 中**零个** 409；`*/src/main/java` 中**零处**直接写 `HttpStatus.CONFLICT`（唯一命中是枚举名 `REVIEW_APPEND_CONFLICT(3038)`，其状态是 `200`）。
- **刻意选择（不是遗漏）**：状态占用/状态机冲突/重复操作在本项目一律 **200 + 业务码**，理由是**前端必须就地提示**（"换个手机号""该订单已发货，不能取消"）：
  - 4xx 会进通用错误处理分支（`client.ts:168-171` 只保留 `{code,msg}`，且全局拦截器/布局层可能把 4xx 当异常页处理）；
  - 更要命的是 409 的 `category` 会被派生为 **CLIENT（`retryable=false`）**，而"并发写冲突"其实是**可以重读后重试**的 —— 用 409 反而给出了错误的处置建议；
  - 代价如实登记：**不符合 REST 语义**（外部工具、通用 SDK、API 网关的冲突语义推断会失效）。
- **将来的唯一合法用途**：若引入 409，只能是"**并发写冲突，且必须由人工/前端就地恢复**"。若需要"可自动重试"的冲突语义，应使用 **429/503**（派生 `TRANSIENT`）而不是 409。

#### `422 Unprocessable Entity` —— **本项目完全不用（永久）**
- **唯一含义（REST 通用语义）**：语法正确但语义不可处理。
- **本项目实测**：零个业务码、零处直接写入。
- **刻意选择**：与 `400` 语义重叠，本项目收敛到 `400 + 1101`，因为前端已有 `isParamError()` 字段高亮分支（`error-codes.ts:353-355`），再引入 422 需要前端新增一条等价分支而**零新增表达力**。新增端点**不得**用 422。

#### `5xx Server Error` —— 服务端没能完成这次调用
| 状态 | 唯一含义 | 派生 `category` | 承载（实测） | 禁止用法 |
| :-- | :-- | :-- | :-- | :-- |
| `500` | 本服务代码缺陷 / 服务端数据不一致；或**第三方违约报文**（覆盖表） | `BUG`（`4007/4008/4999` 例外为 `TRANSIENT`） | `1000`、`4007`、`4008`、`4999`、`5000`-`5002`（7 条） | ❌ 承载业务规则拒绝（会把业务问题记成值班告警）· ❌ 承载可重试的上游故障 |
| `502` | 网关/上游返回了坏响应 | `TRANSIENT` | 无业务码（当前未产生） | ❌ 承载参数错误 |
| `503` | 服务/依赖不可用（含 `SYSTEM_BUSY` 与第三方不可用） | `TRANSIENT` | `1001`、`3100`、`4000/4002/4010/4014/4017/4020/4022/4024/4030/4034/4035/4040/4041/4042/4050/4052`（18 条） | ❌ 承载确定性失败（会让调用方无限重试） |
| `504` | 超时 | `TRANSIENT` | `3101`、`4001`（2 条） | ❌ 与 500 混用（会让"代码缺陷"被重试、或"超时"被记成缺陷） |

> **`SYSTEM_BUSY` 的边界**：`1001` 是跨服务降级的**唯一允许改写目标**（`09` §三·八·1），且只在 5xx/超时/熔断时使用 —— **4xx + 已知 code 必须原样透传**。

### 5. 23 个「200 承载」业务码的类别级理由（不逐条写 23 段）

> 实测 **23** 条（`grep -c "HttpStatus.OK"` 口径下为 23；`3000-3042` 段共 38 条，其中 15 条已是 4xx）。
> 「生产引用」列为 `*/src/main/java` 中引用该枚举常量的**文件数**（实测）。

| 类别 | 码 | 生产引用 | 保留 200 的理由（一句话） | 若改 4xx 的代价 |
| :-- | :-- | :-- | :-- | :-- |
| **兜底 / 规则拒绝总码** | `3000 BUSINESS_EXECUTION_EXCEPTION` | 26 | 业务规则拒绝的兜底；资源存在、请求合法、规则不允许，正是 Q5 的落点 | 改 404 会把「库存不足」「状态不允许」「跨店下单」**一起**变成 404（契约 §2.1a「三点澄清」第 1 条已点名） |
| **资源占用冲突**（REST 应为 `409`） | `3003` 手机号已注册 · `3004` 用户名已占用（死码）· `3006` 用户已存在 | 2 / 0 / 3 | **刻意保留 200**：前端需**就地提示**"换个手机号/用户名"（注册表单字段级引导）；409 会被通用错误处理吞掉 | 不符合 REST 语义（外部工具推断失效）；代价已接受 |
| **状态机冲突**（REST 应为 `409`） | `3010` 订单状态不支持 · `3031` 已有售后申请 · `3039` 退款状态不支持 | 3 / 1 / 3 | 同上：状态类拒绝要给"当前状态 + 允许动作"的就地提示 | 同上 |
| **重复操作**（REST 应为 `409`） | `3018` 已评价 · `3037` 已回复（死码）· `3038` 追评冲突（死码） | 1 / 0 / 0 | 同上：幂等提示属业务结论 | 同上 |
| **业务前置条件不足** | `3005` 余额不足 · `3012` 库存不足 · `3013` 购物车为空（死码）· `3014` 跨店下单（死码）· `3021` 秒杀未开始 · `3022` 已售罄 · `3023` 超限购 · `3024` 抢购失败 · `3030` 已有审核中申请（死码）· `3040` 模板名已存在 · `3042` 购物车条目上限 | 3/5/0/0/1/2/2/3/0/1/1 | 请求合法、资源存在，只是**不满足业务条件** —— 判据的正面形态 | 与「业务规则拒绝」定义直接冲突（`09` §三·六 第 4 行） |
| **凭证无效**（**本 ADR 决策改 `400`**） | `3001 CAPTCHA_ERROR` · `3002 CAPTCHA_EXPIRED` | 6 / 4 | **不保留**：凭证不符属"请求无法被处理"（§决策 1 补齐点）→ **决策：400；落地状态：待 F 批次之后执行**（本批不改代码） | 现状态下它们与「余额不足」共用 200，使"凭证类失败"无法被监控/限流/WAF 识别 |

**死码说明（6 条，`@Deprecated` 且零生产引用）**：`3004 / 3013 / 3014 / 3030 / 3037 / 3038`（`ErrorCode.java:70`、`:162`、`:175`、`:263`、`:316`、`:327`）。它们**保留 200 不构成判据冲突**（零抛出点，不存在可观测的行为），保留原因是 99:99 前后端对齐门禁以「后端枚举 ∖ 前端表」为判据、删除即变红（`ErrorCode.java:62-65`）。**注意 `3013 CART_EMPTY`**：其"同语义实际走"是 `PARAM_ERROR(1101)/400`（空购物车 = 请求不可执行，`ErrorCode.java:154-157`）—— 这与本文判据**一致**，只是该枚举项本身处于休眠。

#### 本节结论（一句话）

> **这 23 条里，没有任何一条属于「资源不存在 / 没认证 / 无权」** —— 那三类已经**全部**是 4xx（404 共 21 条、401 共 8 条、403 共 2 条）。200 白名单只剩三种成分：① 业务规则拒绝（含兜底总码）；② 业务前置条件不足；③ 两条**待改 400 的凭证类**。因此白名单与判据是自洽的 —— 唯一的冲突项已被本 ADR 显式决策掉。

### 6. 前端判定的三种情形（可执行伪代码）

#### 6.1 先看现有实现的事实（只读取证，本批不改前端）

| 事实 | 位置 | 含义 |
| :-- | :-- | :-- |
| `ApiError.code = body.code \|\| res.status` | `packages/api/client.ts:170` | **两个维度被压进一个字段**：有 body 时是业务码，无 body 时是 HTTP 状态 |
| `!res.ok` 分支只读 `{code,msg}`，丢弃 `data` | `client.ts:168-171` | 4xx/5xx 分支拿不到载荷 |
| `body.code !== 200` → `throw new ApiError(body.code, body.msg)` | `client.ts:173-174` | 200 信封里的业务失败**也会抛**，所以"HTTP 200"≠"成功" |
| `ServerFetchError`：HTTP 层 → `code = res.status`；信封层 → `code = body.code` | `server.ts:47-55`、`:73-86` | 服务端渲染侧同一套两维口径 |
| 认证端点的 401 **不参与**刷新重放 | `client.ts:130-142` | 登录失败的 401 是"凭证不正确"，不是"掉线" |
| 既有判定函数：`isNotFound` / `isLoginFailure` / `getUserMessage` / `isAuthError` / `isRateLimited` / `isParamError` / `isThirdPartyError` | `error-codes.ts:333-441` | 全部按 **`code`** 判定，且各自把"HTTP 状态"与"业务码"混在同一个入参上 |

**核心结论**：`ApiError.code` 的**双源语义**是当前前端的结构性缺口 —— 它无法回答"这次失败到底是协议层失败还是业务规则拒绝"。因此判定必须是**两维**的：`httpStatus` 决定**动作**，`code` 决定**文案**。下面的伪代码保留这个区分（**不是**要求立刻改前端类型；`ApiError` 增加 `httpStatus` 字段属前端域改动，本批仅作为规格声明）。

#### 6.2 判定函数签名（对齐 `error-codes.ts` 既有命名）

```ts
/** 归一化后的错误 —— 两个维度分开保留，不做 `body.code || res.status` 的合并。 */
export interface NormalizedError {
  /** 业务码（body.code）。200 信封内的业务失败、或非 2xx 且带 body 时存在；传输层失败时 undefined。 */
  code?: number;
  /** HTTP 状态码。传输层失败（超时/断网/非 JSON）时 undefined。 */
  httpStatus?: number;
  /** 后端 Result.msg 或兜底文案（不清洗，交 getUserMessage 决定优先级）。 */
  message: string;
  /** 与后端 ErrorCategory 对齐的处置类别（由本函数派生，不手填）。 */
  category: 'CLIENT' | 'RULE' | 'TRANSIENT' | 'BUG' | 'UNKNOWN';
  /** category === 'TRANSIENT' 的单向派生（对齐后端 isRetryable()）。 */
  retryable: boolean;
}

/** 情形归一化：三种到达方式 → 一个结构。不抛异常，永不返回 null。 */
export function normalizeError(input: {
  ok: boolean;                                  // res.ok
  status?: number;                              // res.status（传输层失败时 undefined）
  body?: { code?: number; msg?: string } | null; // 解析后的 body（非 JSON / 无 body 时 null）
  transportError?: unknown;                     // fetch reject 的原因（AbortError / TypeError…）
}): NormalizedError;

/** 动作决策：只看 httpStatus 与 category，不看文案。 */
export type ErrorAction =
  | 'login'      // 401 且非认证端点 → 清凭证 + 跳登录
  | 'none'       // 401 且是认证端点 → 就地显示中性文案（登录失败）
  | 'forbidden'  // 403 → 无权提示（1016 时渲染人机验证组件）
  | 'not-found'  // 404 → 空态 / 404 页
  | 'retry'      // TRANSIENT → 显示重试按钮（幂等请求可自动重试一次）
  | 'backoff'    // 429 → 倒计时冷却
  | 'field'      // 400 + 1101 → 字段高亮
  | 'message'    // 业务规则拒绝（RULE）→ 只显示文案，禁止重试
  | 'alert';     // BUG(500) → 通用故障提示 + 上报

export function decideAction(e: NormalizedError, path: string): ErrorAction;

/** 文案：沿用既有 getLoginFailureMessage / getUserMessage 的优先级，不新增文案表。 */
export function messageOf(e: NormalizedError): string;
```

#### 6.3 三种情形的判定伪代码

```ts
// ─────────────────────────────────────────────────────────────
// 情形 1：HTTP 非 2xx 且 body 带 code（业务异常 —— 4xx/5xx 承载真状态码）
//   code  → 文案（"订单不存在" vs "库存不足"）
//   HTTP  → 动作（401 跳登录 / 429 退避 / 5xx 可重试提示）
// ─────────────────────────────────────────────────────────────
if (!res.ok) {
  const body = await res.json().catch(() => null);       // 非 JSON → null（落情形 3 的子分支）
  const e: NormalizedError = {
    code:        body?.code,                              // 可能 undefined（中间层剥了 body）
    httpStatus:  res.status,                              // 一定有（走到这里说明有响应）
    message:     body?.msg ?? `HTTP ${res.status}`,
    category:    deriveCategory(res.status, body?.code),  // 见 6.4
    retryable:   false,                                   // 下一行单向派生
  };
  e.retryable = e.category === 'TRANSIENT';

  switch (decideAction(e, path)) {
    case 'none':      // 401 + 认证端点（AUTH_ENDPOINTS_WITHOUT_REFRESH）→ 登录失败，中性文案
      throw new UiError(getLoginFailureMessage(e));       // 复用 error-codes.ts:417-420
    case 'login':     // 401 + 普通端点 → 令牌失效：清凭证 + 跳登录（不在此层做刷新重放）
      throw new UiError(getUserMessage(e), 'login');
    case 'backoff':   // 429
      throw new UiError(getUserMessage(e), 'backoff');
    case 'field':     // 400 + 1101
      throw new FieldError(1101, getUserMessage(e));
    case 'not-found': // 404（body.code 可能是 1103/3009/3015…，也可能缺失）
      throw new NotFoundError(getUserMessage(e));
    case 'retry':     // TRANSIENT：允许一次幂等重试；展示"重试"按钮
      throw new RetryableError(getUserMessage(e));
    case 'alert':     // BUG(500)：通用故障 + 上报，不重试
    default:
      throw new UiError(getUserMessage(e));
  }
}

// ─────────────────────────────────────────────────────────────
// 情形 2：HTTP 200 且 body.code !== 200（业务规则拒绝）
//   只用 code 取文案；**不做重试**、不改登录态、不进通用错误页。
// ─────────────────────────────────────────────────────────────
const body = await res.json();
if (body.code !== 200) {
  const e: NormalizedError = {
    code:       body.code,          // 3000/3003/3005/3012/3018/3022/3023/3042…
    httpStatus: 200,                // ← 关键：200 不代表成功，只代表"请求被处理完"
    message:    body.msg,
    category:   'RULE',             // 与后端 ErrorCategory.ofStatus(200) 一致
    retryable:  false,              // 单向派生：RULE ⇒ 不可重试
  };
  // ✅ 就地提示 + 引导动作（换支付方式 / 调整数量 / 去评价）
  // ❌ 禁止：自动重试、禁止清凭证、禁止跳登录、禁止上报 BUG 告警
  throw new BusinessRuleError(e.code, getUserMessage(e), guideFor(e.code));
}
return body.data;

// ─────────────────────────────────────────────────────────────
// 情形 3：网络层失败 / 无 body（超时、断网、非 JSON、CORS、5xx 无 body）
//   没有 code 可用 → 只能依赖 httpStatus（可能也没有）与 transport 错误类型
// ─────────────────────────────────────────────────────────────
try {
  res = await fetch(/* … */);
} catch (err) {
  // 3a：连响应都没拿到 —— 无 status、无 code
  const e: NormalizedError = {
    code: undefined, httpStatus: undefined,
    message: '', category: 'UNKNOWN', retryable: true,   // 未知按可重试（对齐后端 null ⇒ TRANSIENT）
  };
  throw new TransportError(isAbort(err) ? '请求已取消' : '网络异常，请检查连接后重试', { retryable: true });
}
// 3b：有 status 但 body 不是 JSON（中间层/网关直接返回）→ 同情形 1，但 code 缺失
//     此时 isNotFound() 依赖 httpStatus=404 的回落形态（error-codes.ts:367-374，「三态都认」）
//     5xx 无 body → category 由状态派生（502/503/504 ⇒ TRANSIENT；500 ⇒ BUG）
```

#### 6.4 `deriveCategory`（前端侧的**镜像**，必须与后端同口径）

```ts
/** 镜像 ia-common ErrorCategory#ofStatus —— 前端不重造映射，只做「无后端 category 可达」时的兜底。 */
function deriveCategory(httpStatus?: number, code?: number): NormalizedError['category'] {
  if (httpStatus === undefined) return 'UNKNOWN';        // 传输层失败 → 后端 null ⇒ TRANSIENT 口径
  if ([408, 429, 502, 503, 504].includes(httpStatus)) return 'TRANSIENT';
  if (httpStatus >= 500) return 'BUG';                   // 覆盖表条目（4007/4008/4999）前端无从得知
  if (httpStatus >= 400) return 'CLIENT';
  if (httpStatus >= 200 && httpStatus < 300) return 'RULE';
  return 'TRANSIENT';
}
```

> **为什么不直接读 `body.category`**：`category`/`retryable` 只存在于 `/internal/**` 的 `ErrorInfo`（`ErrorInfo.java:1-30` 硬边界），**对外 `/api/**` 仍是 `Result{code,msg,data}`**（`09` §三·七·1）。前端无法从对外报文拿到 `category`，只能按同一规则派生 —— 这也是"派生函数必须唯一且口径公开"的理由：**两份实现必须同口径**（后端 `ErrorCategory.ofStatus`，前端 `deriveCategory`），否则会出现"后端说可重试、前端说不可重试"。

#### 6.5 三条禁用写法（评审必查）

```ts
// ❌ 只看 res.ok —— 把 200 信封里的业务失败当成功（会拿到 data=null 直接崩）
if (res.ok) return res.json().data;
// ❌ 只看 body.code —— 把 404/401 的协议失败当"通用传输错误"（丢掉 not-found 空态）
// ❌ 把两个维度合并成一个字段后按数字大小猜 —— code=404 到底是 HTTP 404 还是业务码 404？
const code = body.code || res.status;   // 既有实现，**保留兼容**但新代码必须保留 httpStatus 维度
```

### 7. 迁移路径

#### 7.1 三条纪律

| # | 纪律 | 具体口径 |
| :-- | :-- | :-- |
| 1 | **存量冻结** | 现有 99 个码的状态**不再大面积重排**。已 4xx 的 45 条（404×21、401×8、403×2、400×14）冻结；200 的 23 条按 §决策 5 归并理由冻结，**只允许按 §7.2/§7.3 的显式路径**逐条变更 |
| 2 | **新增按判据** | 新增 `ErrorCode` **必须**先走 §决策 1 的 5 问决策树，并在枚举 javadoc 里写明 ① 命中哪一问 ② `HttpStatus` 选它的理由 ③ 是否属于既有例外类；**禁止**"照着旁边那条抄状态码" |
| 3 | **改存量走显式决策** | 任何改动旧码状态的行为，**必须在 §7.4 变更记录里留一行**（码 / 旧状态 / 新状态 / 理由 / 批次）。**否则下一个人只看到最终白名单，不知道它曾经是 200** —— 本仓已发生过 5 次这种"只留结论、不留理由"的修改（见 §背景），每次都要靠翻 git 与 javadoc 考古 |

#### 7.2 新增码的提交前自检（可直接贴进 PR 描述）

```text
[ ] 这个失败，请求在协议层被处理完了吗？（处理完 → 200 + code；没处理完 → HTTP 错误码）
[ ] 若给 4xx：Q1/Q2/Q3 哪一问命中？401 还是 403？404 还是 400？
[ ] 若给 200：它属于「业务规则拒绝」还是「业务前置条件不足」？两者都不是 → 状态选错了
[ ] 是否落在 TRANSIENT 的 5 个状态（408/429/502/503/504）之外却需要重试？→ 不得靠 200/4xx 蒙混
[ ] 是否需要进 STATUS_AMBIGUOUS_OVERRIDES？若"能由 HttpStatus 唯一确定"→ **禁止入表**
[ ] 是否同步了 ① 契约 §2 与 §2.1a ② 前端 error-codes.ts（99:99 对齐门禁）
```

#### 7.3 存量改动的批准形态

1. 先在本 ADR §7.4 追加一行（**变更记录是批准的载体**）；
2. 再改 `ErrorCode` 的 `HttpStatus`（**不得**在新代码里手写状态码绕过枚举 —— `09` §三·六 末条）；
3. 同批更新契约 §2.1a 例外台账与前端 `error-codes.ts`（门禁 `check-error-codes.mjs` 以「后端枚举 ∖ 前端表」为判据）；
4. 若涉及既有端点的对外行为变化，必须在契约 §十 变更记录留版本行（**401/404 这类变化会改变前端分支：从 `then` 进 `catch`**）。

#### 7.4 已发生的状态变更记录（从既有事实取证）

| # | 码 | 枚举名 | 旧状态 | 新状态 | 理由 | 批次 / 落地 |
| :-- | :-- | :-- | :-- | :-- | :-- | :-- |
| 1 | `3011` | `PRODUCT_NOT_FOUND` | `200` | **`404`** | 资源不存在 = 真 404；SPU 详情与短码端点改抛 `1103` | P2 / B7b / B8（**已落地**） |
| 2 | `3009` | `ORDER_NOT_FOUND` | `200` | **`404`** | 资源不存在 = 真 404 | 批次 ①（**已落地**） |
| 3 | `3015` | `SHOP_NOT_FOUND` | `200` | **`404`** | 同上（先拆语义再加状态，见其 javadoc） | 批次 ①（**已落地**） |
| 4 | `3016` | `SKU_NOT_FOUND` | `200` | **`404`** | 同上（形态非法那支已前移 400） | 批次 ①（**已落地**） |
| 5 | `3017` | `HOME_CONFIG_NOT_FOUND` | `200` | **`404`** | 同上 | 批次 ①（**已落地**） |
| 6 | `3019` | `COUPON_NOT_FOUND` | `200` | **`404`** | 同上（反枚举掩盖口径不变） | 批次 ①（**已落地**） |
| 7 | `3020` | `FLASH_SALE_NOT_FOUND` | `200` | **`404`** | 同上（`3021`-`3024` 仍是 200） | 批次 ①（**已落地**） |
| 8 | `3032` | `APPLICATION_NOT_FOUND` | `200` | **`404`** | 同上 | 批次 ①（**已落地**） |
| 9 | `3033` | `WITHDRAWAL_NOT_FOUND` | `200` | **`404`** | 同上 | 批次 ①（**已落地**） |
| 10 | `3034` | `NOTIFICATION_NOT_FOUND` | `200` | **`404`** | 同上 | 批次 ①（**已落地**） |
| 11 | `3035` | `LOGISTICS_NOT_FOUND` | `200` | **`404`** | 同上 | 批次 ①（**已落地**） |
| 12 | `3036` | `REVIEW_NOT_FOUND` | `200` | **`404`** | 同上（死码，安全默认） | 批次 ①（**已落地**） |
| 13 | `3041` | `FREIGHT_TEMPLATE_NOT_FOUND` | `200` | **`404`** | 同上（含"他人模板"反枚举） | 批次 ①（**已落地**） |
| 14 | `3007` | `PASSWORD_ERROR` | `200` | **`401`** | 登录失败唯一出口；200 与 404 的差异构成账号枚举信道 | 批次 ④（`ADR-065`，**已落地**） |
| 15 | `3008` | `USER_STATUS_ABNORMAL` | `200` | **`401`** | 同上（安全默认：即便误用也不退化成 200） | 批次 ④（`ADR-065`，**已落地**） |
| 16 | `1017` | `ACCOUNT_FROZEN` | `403` | **`401`** | 同上 + 退役（`@Deprecated`） | 批次 ④（`ADR-065`，**已落地**） |
| 17 | `3009 → 1103` | `ORDER_NOT_FOUND` → `NO_RESOURCE_FOUND`（退款命令面） | `404` | **`404`**（**状态未变，只对齐码名**） | 「支付单/退款单不存在」不该借用"订单"码名 | 批次 R2（**已落地**；PAY2/PAY3 支付回调仍为 `3009`，例外已挂账） |
| 18 | `3001` | `CAPTCHA_ERROR` | `200` | **`400`** | 凭证无效属"请求无法被处理"（§决策 1 补齐点） | **本 ADR（决策；落地状态：待 F 批次之后执行）** |
| 19 | `3002` | `CAPTCHA_EXPIRED` | `200` | **`400`** | 同上 | **本 ADR（决策；落地状态：待 F 批次之后执行）** |

> **第 17 行特意保留**：它虽然**没有改变任何状态**，但改变了「谁在抛哪个码」，属于容易被误读为"同义码重复"的历史事实 —— 记录下来才能解释"为什么 `3009` 在 pay-service 只剩回调面一个抛出点"。

### 8. 三条禁令

#### 禁令 1 —— **禁止用 200 承载 not-found**（已修，禁止回退）
- **违规形态**：任何"资源不存在"语义的业务码配 `HttpStatus.OK`（如 `PRODUCT_NOT_FOUND` 原配 200）。
- **为什么不可回退**：`09` §三·六 定性为「**纠正错误**，不是风格选择，不得以 D4 为由回退」；用 200 承载 not-found 会让熔断永不触发、成功率虚高、404 被缓存（§决策 2）。
- **检测**：`ErrorCode` 中 `.md` 名称含 `NOT_FOUND` 的枚举项状态必须是 `404`（人工评审 + 契约 §2.1a 台账核对）；契约 §2.1a 是唯一台账，新增/删除必须同批改。

#### 禁令 2 —— **禁止用 4xx 承载业务规则拒绝**
- **违规形态**：把"余额不足/库存不足/状态不允许/已评价/限购/名称重复"配成 `400/403/404/409/422`。
- **为什么**：判据（§决策 1 Q5）；后果为监控全线报警、调用方误判可重试、前端 4xx 分支丢 `data`（§决策 2）。
- **注意**：本禁令**不**禁止"资源不存在→404"（那是禁令 1 的另一面），也**不**禁止 `409` 用于**真正的并发写冲突**（§决策 4 的受限许可）。

#### 禁令 3 —— **禁止每个服务自定义状态映射**（唯一出口是 `ErrorCode` 上的 `HttpStatus`）
- **违规形态**：在 Controller / 服务内 `ResponseEntity.status(HttpStatus.X)` 手写状态；在服务内维护 `业务码 → 状态` 的第二张表；把 `ErrorCategory` 的派生规则复制一份。
- **唯一出口**：`ErrorCode#getHttpStatus()`（`ErrorCode.java:432`）+ `ia-common` 的 `GlobalExceptionHandler`（内部接口裸 `ErrorInfo`、外部 `Result` 信封）。`category` 的唯一出口是 `ErrorCategory`（`ErrorCategory.java:1-75` 类注释：「本类是全仓唯一实现，禁止各服务自建映射」）。
- **已登记的两类例外（不得扩大）**：
  1. **网关边缘过滤（3 处）**：`JwtBlacklistFilter.java:245`（401）、`AiTokenQuotaFilter.java:155`（429）、`CacheBodyGlobalFilter.java:110`（413 —— **无对应 `ErrorCode`**）。理由：请求未被路由进业务服务，业务码体系不适用（网关无 `Result` 信封）。
  2. **渠道回调协议（2 处）**：`WechatCallbackSupport.java:70`（200 成功）/ `:75`（500 触发重推）。理由：报文与状态码由渠道规定（`ADR-029`），协议优先于平台信封，**不得**改成 `Result`。
  > 除这两类，`*/src/main/java` 中**零处**手写状态码（实测 `grep -rn "setStatusCode\|HttpStatus\." ` 仅上述 5 处 + `WebUtils` 的 `is2xxSuccessful` 判定）。

#### 三条禁令的自洽性核对（本批核心质量要求）

| 核对项 | 结论 |
| :-- | :-- |
| 23 条 200 白名单里有没有"资源不存在/未认证/无权" | **没有**（§决策 5 结论：404×21 / 401×8 / 403×2 全部已在 4xx） |
| 白名单里有没有与判据冲突的条目 | **有 2 条**：`3001`/`3002`（凭证无效却在 200）→ 已被 §决策 5/§7.4 显式决策为 400，落地前以"已知冲突 + 有决策"登记，而不是沉默 |
| 三条禁令之间有没有互斥 | 无：禁令 1 约束"哪类语义不许用 200"，禁令 2 约束"哪类语义不许用 4xx"，禁令 3 约束"谁有资格决定状态" |
| 禁令 3 与现存 5 处手写状态码 | 已作为**两类受限例外**登记，禁令表述改为"业务错误的状态映射唯一出口"，避免"禁令与现状矛盾"的伪冲突 |

### 与其他 ADR 的关系（只写引用与边界，不重复其结论）

| ADR | 它已决定什么 | 本 ADR 新增什么 / 边界在哪 |
| :-- | :-- | :-- |
| [`ADR-004`](./004-internal-api-exception-passthrough.md) | **承载分工**：`/api/**` 包 `Result` 信封、`/internal/**` 裸真实状态码 + 极简 JSON（V5.3 修正为直接返回 `ResponseEntity`）；并已登记「3xxx 内部接口以 200 返回、`void` 契约丢失失败信号」的风险 | 本 ADR 定的是**"选哪个状态码"的判据**，ADR-004 定的是**"怎么承载"的形态**。两者正交：本 ADR 的 Q1-Q5 输出一个状态码，ADR-004 决定它外面包不包信封。**不重复**其 V5.3 修正内容 |
| [`ADR-062`](./062-external-identifier-and-addressing.md) | 对外标识四位置四形态、编解码唯一出口、**形态非法 → 400 + `1101` 且前移到适配层 fail-fast** | 本 ADR 只把"参数形态非法 → 400"作为 Q3 的一个输入分支引用。**不重复**其编解码细则、不重复 `3016` 的双义拆分过程 |
| [`ADR-063`](./063-external-channel-number-separation.md) | 对外渠道号与内部业务键分离、对外单号生成入口、承载层解耦 | **无交集**：本 ADR 不涉及标识生成。唯一需要说清的边界 —— 对账单里"渠道有本地无"是**数据事实**（不伪造本地单号），**不是**一次 HTTP 错误，因此不适用本文判据 |
| [`ADR-065`](./065-login-failure-disclosure-and-anti-enumeration.md) | 登录失败四类原因 → `401 + 3007 + 中性文案`；`3007/3008` 改 401、`1017` 退役；前端 `isLoginFailure`/`getLoginFailureMessage` | 本 ADR **只登记**这些状态变更（§7.4 第 14-16 行）并在 Q1 引用其结论，**不改**其任何决策。边界：`401` 的"不得区分原因"由 ADR-065 负责；本 ADR 负责"哪些失败该进 401" |
| [`ADR-053`](./053-doc-ownership-by-nature-and-two-layer-standards.md) | 文档 Owner 按**性质**分权 + 规范**双层模型**（决策归决策者、派生归 doc-engineer） | 本 ADR 与 `09` §三·六/§三·八 的关系按该双层模型处理：**ADR 存"为什么"（判据/决策树/证据），`09` 存"不能做什么"（可执行约束）**，两侧互链而不互抄 |

## 备选方案

| 方案 | 优点 | 缺点 | 结论 |
| :-- | :-- | :-- | :-- |
| **A. 判据收口 + 派生映射 + 迁移纪律**（本 ADR） | 判据可对任意新错误走完即得结论；既存例外有类别级理由；变更历史可追溯 | 需要维护一份 ADR（但只此一份，且证据可复算） | **采纳** |
| B. 全量 REST 化：业务规则拒绝也改成 409/422 | 语义最"标准"，外部工具无歧义 | ① 与"前端需就地提示 + 载荷"冲突（4xx 分支丢 `data`）；② 45→23 条码要批量改状态，前端全部 4xx 分支重写；③ 409 派生 `CLIENT`（不可重试），对"并发冲突"给出错误处置建议；④ 收益是**语义洁癖**，成本是**全链路回归** | 否决（收益/成本倒挂） |
| C. 保留 200 承载一切业务失败（连 not-found 也回 200） | 前端只有一条分支，实现最省事 | 熔断永不触发、成功率虚高、404 被缓存、调用方无法决定透传/降级（`09` §三·六 定性为"偏离 HTTP 语义"） | 否决（已被批次 ① 推翻） |
| D. 把 `ErrorCategory` 改成按码段硬编码（不用派生） | 直观、可逐条微调 | 99 行手填必然漂移；新增码会漏填；两处真相必然分叉（`ErrorCategory.java:19-20` 已论证） | 否决 |
| E. 取消覆盖表，全部按状态码派生 | 实现最简、零例外 | 第三方违约报文（`4007/4008/4999`）会被判 `BUG` 而**拦住重试**，把上游抖动升级成值班事故 | 否决（覆盖表保留，但上限 5 条 + 启动期自检） |

## 后果

**正面**

- **判据唯一且可执行**：任意一个新错误，走完 §决策 1 的 5 问即得状态码，不再依赖"看旁边怎么写"；
- **三看分工固定**：状态码给运维/网关/缓存、`code` 给用户、`category` 给跨服务调用方 —— 三方各有唯一数据源，不再互相猜测；
- **映射有源码事实**：`HttpStatus → category` 逐行有出处（含覆盖表 3 条与 5 条上限、判定顺序、`retryable` 单向派生），**`09` §三·八 与源码一致**，本批核对未发现作文档/代码矛盾；
- **变更历史可追溯**：19 行变更记录（含 3 行"状态未变但码名变"的事实），后来者不必考古 git 就能知道"它曾经是 200"；
- **自洽性已核对**：23 条 200 白名单**不含**"资源不存在/未认证/无权"；唯一的 2 条冲突（`3001/3002`）已被显式决策为 400。

**负面 / 风险（如实登记）**

1. **`3001/3002` 仍是 `200`（决策已下、代码未落）** —— 落地前，"验证码错误"与"余额不足"在协议层不可区分（监控/限流/WAF 无法识别）；且**契约 §2.1a 的 15 项例外台账会变成 17 项**，须与契约域同批改（本批契约文件由另一 agent 修改，**本 ADR 不越界**）。
2. **`3001/3002` 改 400 的连带影响**：前端 `3001/3002` 现在走"200 信封"分支（`ApiError(code=3001)`），改 400 后仍抛 `ApiError(code=3001)`（`client.ts:170` 取 `body.code` 优先），**文案不变、动作可能变**（4xx 分支不再解析 `data`）—— 需在 F 批次同批验证前端"验证码错误后自动刷新验证码"的引导动作不依赖 `data`。
3. **`401` 的语义拉伸（第三方认证失败）已登记但不改**：按状态码分流的通用调用方会把 `4004/4012/4021` 读成"客户端凭证无效"（前端 `isAuthError` 按 code 判定，不受影响）。改码会破坏已发布的 401 语义与 99:99 门禁，故本 ADR 选择**登记 + 文档化**。
4. **`403` 的语义拉伸（`1016` 人机验证）已登记**：403 在本项目有两个用途（无权 / 访问条件未满足），二者都以 `code` 区分；若将来引入"需要支付/需实名"之类挑战，建议继续用 403 + 独立 code，**不要**新造状态。
5. **`409/422` 永不用**：外部工具/通用 SDK 的"冲突/不可处理"语义推断在本项目会失效（这是 REST 一致性的**有意代价**，换取前端就地提示能力）。
6. **前端 `ApiError.code` 的双源语义未修**（`body.code || res.status`）：本 ADR 只给出规格（§6.2 保留 `httpStatus` 维度），**改类型属前端域**，需另立批次；在此之前，任何"按 code 数字大小猜语义"的新代码都是重复踩坑。
7. **禁令 3 的两类例外是长期存在的**：网关 3 处 + 渠道回调 2 处手写状态码，其中 `CacheBodyGlobalFilter` 的 `413` **没有对应 `ErrorCode`** —— 若要纳入错误码体系需新增码（会破坏 99:99 对齐），故保持现状并登记。

## 日期

2026-10-18

## 参考

**代码事实源（只读取证，本批未改任何 `Implementation/**` 文件）**

- `Implementation/back-end/ia-common/src/main/java/org/icedamericanomall/common/enums/ErrorCode.java`（99 项；状态分布 `OK 23 / NOT_FOUND 21 / SERVICE_UNAVAILABLE 18 / BAD_REQUEST 14 / UNAUTHORIZED 8 / INTERNAL_SERVER_ERROR 7 / TOO_MANY_REQUESTS 4 / FORBIDDEN 2 / GATEWAY_TIMEOUT 2`）
- `Implementation/back-end/ia-common/src/main/java/org/icedamericanomall/common/enums/ErrorCategory.java`（派生 `:243-261`、异常类型 `:158-167`、判定顺序 `:301-336`、覆盖表 `:409-430`、自检 `:358-383`、上限 `:118`）
- `Implementation/back-end/ia-common/src/main/java/org/icedamericanomall/common/api/ErrorInfo.java`（内部错误体五字段）
- `Implementation/back-end/ia-common/src/main/java/org/icedamericanomall/common/aspect/GlobalExceptionHandler.java`（内部 `ErrorInfo` / 外部 `Result`）
- `Implementation/front-end/packages/api/error-codes.ts`（`isNotFound:367-374`、`isLoginFailure:397-406`、`getLoginFailureMessage:417-420`、`getUserMessage:433-441`）
- `Implementation/front-end/packages/api/client.ts`（`ApiError:115-117`、`apiClient:156-176`、认证端点排除 `:130-142`）
- `Implementation/front-end/packages/api/server.ts`（`ServerFetchError:47-55`、`serverFetch:63-86`）

**常驻事实（本批实测，脚本口径）**

- `ErrorCode` 枚举项计数 / 状态分布 / `category` 派生分布：正则解析 `ErrorCode.java`（`^\s{4}(NAME)\((\d+), "…", HttpStatus\.X\)`）+ 按 `ErrorCategory#ofStatus` 同规则复算（覆盖表 `4007/4008/4999` 单列）
- 生产引用文件数：`grep -rl "ErrorCode.<NAME>\b" --include=*.java Implementation/back-end/*/src/main/java | wc -l`
- 手写状态码点位：`grep -rn "setStatusCode\|HttpStatus\." --include=*.java Implementation/back-end/*/src/main/java`（排除 `ErrorCode`/`ErrorCategory`/`valueOf`）→ 5 处
- 表结构 62 张：`grep -ciE "^\s*CREATE TABLE" Implementation/back-end/database/Initialize.sql`
- 端点 289 / 0 / 43：`node Implementation/back-end/scripts/extract-endpoints.mjs`（`code.distinct = 289`、`codeOnly = 0`、`docOnly = 43`）

**文档**

- `project-docs/collaboration/09-constraints.md` §三·六（错误承载的四类划分）、§三·七（响应信封）、§三·八（跨服务错误传递铁律）
- `project-docs/contract/05-API-Specification.md` §二（错误码体系）、§2.1a（15 项例外台账）
- [`ADR-004`](./004-internal-api-exception-passthrough.md) · [`ADR-029`](./029-channel-callback-protocol-over-result-envelope.md) · [`ADR-053`](./053-doc-ownership-by-nature-and-two-layer-standards.md) · [`ADR-062`](./062-external-identifier-and-addressing.md) · [`ADR-063`](./063-external-channel-number-separation.md) · [`ADR-064`](./064-spu-name-wire-key-unification.md) · [`ADR-065`](./065-login-failure-disclosure-and-anti-enumeration.md)
