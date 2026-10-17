# 收口清单（Open Items Ledger）

> **用途**：跨会话的工作交底。不是规格文档，不需要进规格索引。
> **维护规则**：任何一批工作落地后，同步更新本文件的「已完成 / 待办 / 已知未修 / 验证边界」四段。
> **最后更新**：2026-10-18（批次 U1 派单时）

## 0. 当前实测基线

| 指标 | 值 | 唯一校验口 |
|---|---|---|
| 测试 | **2235 / 0 failures / 0 errors / 1 skipped** | `mvn -f Implementation/back-end/pom.xml clean test` |
| 端点对账 | **matched 289 / codeOnly 0 / docOnly 43** | `node Implementation/back-end/scripts/extract-endpoints.mjs` |
| 错误码 | **99** | `node Implementation/front-end/scripts/check-error-codes.mjs` |
| 表 | **62** | `sync-doc-counts.mjs --list-tables` |
| 模块 | **19** | 根 `pom.xml` |
| ADR | **001–065** | `project-docs/adr/README.md` |
| 根文档计数 | 由脚本生成 | `sync-doc-counts.mjs --check --strict-tests`（已接入 CI `build` job） |

---

## 1. 已完成（按主题，非按时间）

### 1.1 报文一致性（ADR-056 / 059 / 062）
- 全平台线格式 `snake_case`；SPU 资源段 `/api/item/product/**` → **`/api/item/spu/**`**；`review/product/` → `review/spu/`
- 路径变量一律**业务键 32-hex** + 显式命名（`{spuId}`/`{skuId}`/`{historyId}`）；删除了 `/internal/item/search/detail` 的**双形态**
- 编解码收敛为唯一出口（`IdGenerator.toWire/parseWire/requireWire`），形态非法 → **400/1101** fail-fast
- **SPU 名全平台统一 `spu_name`**（两端原子改名，7 载体 + 写侧 2 Req）；唯一例外 `HistoryVO.product_name`（`@JsonProperty` 钉名，契约 §1.8④ + §12.2 双保险登记）

### 1.2 资金链路
- `out_trade_no` 归属错位（发起写 `orders.order_no`、回调按 `pay_order_no` 查 ⇒ **真实回调必然 500、订单永不确认**）→ 渠道号独立成列 `pay_order.channel_trade_no`（20 位随机/80 bit/跨重试稳定）+ `refund_order.out_refund_no`；回调/对账**唯一寻址**渠道号
- 退款 API 的 `out_trade_no` 纠正为**原交易的对外渠道号**
- 支付单「**存在但字段不完整**」不再谎称「不存在」→ `INTERNAL_ERROR(1000)` / 500 / `BUG` / 不可重试（`PayOrder#assertRefundPayeePresent/AmountPresent`）

### 1.3 错误承载（ADR-062 / 09 §三·六）
- 四类划分成文：**资源不存在 404 / 参数非法 400 / 未认证无权 401·403 / 业务规则拒绝 200**
- P2 + **12 个 not-found 码 200 → 404**；6 处「not-found 塞进 3000」→ `1103`
- 双义码拆分：店铺「状态不允许」→ 200+`3000`；「越权」→ 403；「SKU 形态非法」→ 400+`1101`（校验前移适配层）
- 登录失败统一 **401 + `3007` + 中性文案** + **dummy BCrypt 抗时序枚举**（非安全强度，仅消时间差）

### 1.4 跨服务错误传递（09 §三·八，已落码）
- `ErrorCategory`（CLIENT/RULE/TRANSIENT/BUG，**由 HttpStatus 派生** + ≤5 条覆盖表 + 启动期自检）
- `ErrorInfo{code,msg,category,retryable,traceId}`（仅 `/internal/**`；`/api/**` 信封**零变化**，有结构性守卫）
- `UpstreamException` + `UpstreamErrorDecoder`；**下游 4xx 原样透传**，仅 5xx/超时/熔断才降级；`retryable` 只由 category 派生
- **`X-Trace-Id` 经 Feign 拦截器透传**（此前只带 `X-User-Id`，跨服务 traceId 恒 null）

### 1.5 承载层解耦（ADR-063）
`scene` → 短码（`spu.short_code` + P38 `by-code`）；objectKey → 内容哈希 `wxa-code/v1/product/{sha256}`；`home_config.spu_ids` 容量声明；`conversation_id` 内容派生；退款 `reason` 枚举文案；`CsvIdLimits` 常量族

### 1.6 类型化与文档
- `Result<Map>` **9 处 → 8 个 VO**（金额单位**钉死为分**）；`TrendDataVO`/`DailySalesVO` 两个不存在的类名订正
- 契约 V5.21~V5.23 多轮收口；ADR-062/063/064/065
- **根文档计数改为脚本生成**（`sync-doc-counts.mjs`，`--check` 已进 CI，`--write` 是唯一写入口）
- CI 新增 `consistency` job（6 道闸：契约七节/前端死路径/路径互校/错误码/查询参数命名/`codeOnly==0`）

### 1.7 建库脚本（**真库实跑通过**）
- 修复 `INSERT INTO user` 被 `42a080f7` 截断（10 列只给 3 值、缺右括号 ⇒ **MySQL 1064，整个脚本此前一次都跑不通**）
- 补齐 17 条 `DROP TABLE IF EXISTS`（62/62 双向零差集）
- 新增 **admin / seller 可登录种子**（`user_auth` USERNAME+BCrypt，`ROLE_ADMIN`/`ROLE_SELLER`，seller 另带 `seller`+`shop`）——此前**无人能登录管理端/商家端**
- 在真实 MySQL 8.0.36 上：**0 错误 / 62 表 / 连跑两遍幂等 / 种子落库 / 无旧名表残留**

### 1.8 错误承载收口（批次 R2）
- I40「缺 `amount`」不再折叠成客户错误 → `1000/500/BUG`，**且先 assert 再比较**（原 `||` 短路在挡拆箱 NPE，顺序错了会变成"500 空指针"）
- 上游订单摘要缺金额由 `1101/400` 改 `1000/500/BUG`；**刻意不用** `SYSTEM_BUSY`（那是"服务不可用"，本条是"数据不完整"，重试必然重复失败）
- 退款命令面「支付单/退款单不存在」`3009` → **`1103`**（4 处：I40/AD43/PAY6/I55，同 404、零新增码）
- 契约 §二 **删除**覆盖率计数（原文「实测 98 个 / 共 40 个 / 未收录约 58 个」当时就已全错）→ 改指 `ErrorCode` 为唯一真源；**删除而非校准**的理据写进契约

### 1.9 H2 测试的键集与承重（批次 U1）
- 14 处 H2 DDL 补回丢失的 UNIQUE 键（`user` 4 ×2 实例、`user_auth` 2、`spu.uk_spu_id` ×3、`cart.uk_user_sku`）；`user.user_id` 改 **`BINARY(16) NOT NULL`**
- **真正的 enabler**：4 个测试由裸 `openSession().getMapper()` 改用 **`SqlSessionTemplate`**（裸 session 只抛 MyBatis `PersistenceException`，Spring 不做异常翻译 ⇒ `catch (DuplicateKeyException)` 永不进入）。**没有这一步，补键等于白补**
- 14 条新用例，4 条直接读 `INFORMATION_SCHEMA.TABLE_CONSTRAINTS` 断言键集 + 列序 + 列类型；**三条变异验证**（删 `uk_phone_hash`/`uk_spu_id`/`uk_user_sku` → 各 3 例红，含 `expected:1 but was:2`，即生产"双账号/双购物车行"的等价物）
- 9 处"缺列导致键无处可挂"按口径豁免并逐条说明（7 个 spu 表无 `short_code` 列、2 个 sku 表无 `sku_id` 列）

---

## 2. 待办

### 2.1 在跑
（当前无 agent 在跑 —— 连续 6 个子 agent 失败，已停止派单，改为人工推进）

#### F 批现状（**未完成，未提交，勿当作已完成**）
- **16 / 30 个 fallback 已改**（工作区未提交、**零验证**：没跑构建、没有测试、没有 ArchUnit 守卫）
- 已改的 16 个：`Address / AfterSale / AuthContext / Balance / Cart / Coupon / Currency / FreightTemplate / GroupBuy / GroupBuyTeam / ItemSearch / Kg / KnowledgeVector / Logistics / Member / NoteVector`（+ 原有的 `SpuDetail`）
- **14 个未改**：`Notification / Order / OrderRefund / Points / Promotion / Refund / Search / Sku / SpuVector / Task / TradeInvoice / User / UserProfile / Verification`
- **16 个里 4 个 fail-open（无 throw）**：`Kg / KnowledgeVector / Member / NoteVector`；其余 12 个 fail-closed 抛 `SYSTEM_BUSY`（503/TRANSIENT/可重试）
- **U11 未做**：`PayManager` 仍 9 处 `ORDER_NOT_FOUND`

#### 续做 F 的必读要点（省去重新摸索）
1. **真实 API（我踩过坑）**：`UpstreamFailures.classify(Throwable)` 返回 **`ErrorCategory`**（不是 ErrorInfo）；
   `UpstreamException.findIn(cause)` 返回可空；fail-closed 承载是 **`ErrorCode.SYSTEM_BUSY`**（不是 `BUSINESS_EXECUTION_EXCEPTION`——后者是 200/RULE/不可重试，曾误用在 `BalanceClientFallback`，已修）
2. **统一形态**：`create()` 里取 `category` + `upstream`；每个方法内 `category == CLIENT && upstream != null` → `throw upstream`（透传）；否则 fail-open 返回降级值 + warn，或 fail-closed 抛 `SYSTEM_BUSY` + 私有 `DEGRADED_MESSAGE`
3. **14 个的决策**（已判定，实现时核实调用方）：`Notification`/`Points`/`SpuVector`/`Task`/`UserProfile` → fail-open；
   `Order`/`OrderRefund`/`Promotion`/`Refund`/`TradeInvoice`/`User` → fail-closed；
   `Search` → CLIENT 透传 + 其余降级「搜索暂时不可用」（现为无条件 SYSTEM_BUSY）；
   `Sku` → **写 fail-closed / 读可 fail-open**；
   **`Verification` → fail-closed，安全关键（fail-open = 绕过验证），理由必须写进 javadoc**
4. **验证未做**：M1（改回无条件 SYSTEM_BUSY 必须红）、M2（fail-closed 改成 fail-open 必须红）、
   ArchUnit 守卫（扫 `*Fallback` 的 `create` 必须引用 `UpstreamFailures`）+ **守卫自身的变异**（塞一个绕过的必须红）、U11、一次干净全量
5. **教训**：不要给子 agent 一个"读 17 个参照文件"的派单（上下文必爆）；**模板必须逐字取自已验证代码**

### 2.2 排队（按既定顺序）
| 序 | 批 | 内容 |
|---|---|---|
| 1 | **F** | 18 个 fallback 接 `classify`（4xx 透传 / 5xx 才降级）|
| 2 | **H** | 小程序**仅文档**（用户已裁决：本轮完全排除代码）|
| 3 | **D** | `RefundManager` 拆类（311→314 行，**改动前已超「类 ≤300 行」硬约束**）—— **先出测量报告**再决定"拆"还是"登记技术债" |
| 4 | **Web 角色端开发** | seller / admin 两个 app（现状：各 1 个"开发中"占位页，后端已有 **65 admin / 30 seller** 端点）。用户定的优先级：**Web 各角色平台先补完**。S0（基座）曾被取消，需重启 |

### 2.3 审计交回、待排期
| 项 | 内容 | 需要谁 |
|---|---|---|
| **U2** | `Initialize.sql` 两处过时注释（「未启用 `@TableLogic`、无 `deleted` 列」但 `invoice_title.deleted`+`@TableLogic` 已落地；「每表以 BIGINT AUTO_INCREMENT 为主键」但 3 张关联表是双列 PK）；并给 **4 组同名不同型列**（`spu_id`/`sku_id`/`user_id`/`coupon_id`）标注键型 | 我可直接做 |
| **U3** | **5 张死表**存废：`seller_application` / `flash_order_log` / `merchant_reply_template` / `ai_message` / `ai_conversation`（0 处 Java 引用）。⚠ `flash_order_log` 注释写"秒杀幂等+兜底"，**实际靠 Lua + `orders.uk_order_no`** | 产品决策 |
| **U4** | 60 行**只读**结构对账脚本（打印缺表/缺列/类型不符），**不进 CI、不阻塞提交** | 我可直接做 |
| **U5** | 2 处缺索引（`sys_user_role.role_id` 非前导、`refund_order(status,create_time)`）+ 7 处冗余索引 + 6 处疑似死索引 | 低优先，**不建议现在删** |
| **U6** | `invoice_title.uk_user_default` 与软删共存（软删行占默认位）；当前 `clearDefaultByUserId` 不带 `deleted=0` **恰好**没踩坑——"对的但是偶然的" | 记 ADR |
| **U7** | `database/seed-data.js` 与 `Initialize.sql` 新种子**冲突**（它也写 `USERNAME/'admin'`，口令 `Dev123456`，命中同一唯一键；且仍是 ADR-050 前的非 hex 业务 ID）→ 退役或重写 | 待裁决 |
| **U8** | `docker-compose.yml:46` 注释仍写「48 表终态」（实为 62）| 低 |
| **U9** | 通知 `type` **无枚举化真源**（4 个服务各持私有常量，10 个值）→ 建议收口为 `ia-api` 共享枚举 | 架构，需立项 |
| **U11** | **PAY2/PAY3 支付回调仍用 `3009`**（渠道号查不到支付单）—— R2 未改（属回调面），已登记契约 §12.2 ⑩；改则同为 404、调用方零感知 | 待裁决 |
| **U12** | **7 个 spu 表无 `short_code` 列、2 个 sku 表无 `sku_id` 列** ⇒ 这些 H2 表无法补唯一键（U1 豁免）。若日后参与写入用例，必须补列+补键 | 低（目前只读）|
| **U13** | `FavoriteSpuBriefQueryImplH2Test` 的 `spu_id` 约束是**匿名**（H2 自动名），键在但名与生产 `uk_spu_id` 不同 | 低 |
| **U10** | 契约 §二 覆盖率数字（98/40/58 → 实测 99/52）—— **A/B/C/E 批的 E 项就是删这些数字** | 在 R2 批内 |

---

## 3. 已知未修（**明确不算完成**）

| 项 | 后果 |
|---|---|
| **小程序 17 个文件** | 仍读 `name`、仍打 `/api/item/product/**`、`categoryId` 静默失效 ⇒ **商品名显示空白**；用户已裁决本轮排除，只登记（见 `product/02` §九）|
| **18 个 fallback 工厂** 仍是无条件 `SYSTEM_BUSY` | 只有 `SpuDetailClient` 修好了 4xx 透传 |
| `KgEdgeUpsertCO.srcId: Long` | 最后一个内部契约技术键（ADR-062 §E5）|
| 评价缺「最旧优先」枚举 | 后端无该值；前端已删非法码 `oldest`（该码会被 `fromCode()` **静默回退 newest**）|
| 通知缺多值/分组 `type` 筛选 | 前端逐值取数后合并（正确但多请求）|
| 积分 `type=4/5` | 声明在案、**无生产者** |
| `RefundManager` 超 300 行 | 改动前已超，硬约束违反 |
| `MAX_COMPARE_SPU_IDS` / C9 批量比价 | 契约已定义、代码未实现 |
| `ia-api SearchClient` 3 处 `Result<Map>` | Feign 弱类型契约，其 javadoc 自陈"强类型化需 search-service 同批发布" |

---

## 4. 验证边界（**读这份清单的人必须知道**）

| 维度 | 状态 |
|---|---|
| 后端逻辑/编译 | ✅ 2217 测试全绿（**必须 JDK 21**）|
| 契约 ⇄ 代码 | ✅ 端点对账 289/0/43 + `check-section7` |
| 建库脚本 | ✅ **真库实跑**（MySQL 8.0.36，0 错误、62 表、幂等）|
| **运行态** | ❌ **未验证**。Nacos(8848) / Redis(6379) 均**不可达**，15 个服务起不来 ⇒ **登录链路、网关路由、Feign 调用、限流全程无运行态证据** |
| **前端构建** | ❌ 未跑 `pnpm build`（环境约束）。仅 `tsc --noEmit` + 4 条静态脚本 |
| **真实第三方** | ❌ 未验（微信支付/支付宝/短信/Geocoding 均为 mock 或未实测）|
| Docker | ❌ daemon 不可用 |

> **"测试绿 ≠ 可用"** —— 本轮唯一一次发现生产级致命缺陷（`out_trade_no` 归属错位）靠的是**读发起侧实际发了什么**，不是任何断言。

### 环境事实
- **JDK**：必须 `JAVA_HOME=/c/Users/18279/.jdks/ms-21.0.10`。**JDK 25 下 JaCoCo 0.8.12 无法 instrument Mockito mock，全仓必红**（曾把它误判为"并发损坏"）。
- **MySQL**：本机 8.0.36 可用（凭据由用户提供，**不落仓库**）。`iam_smoke` 库已按 `Initialize.sql` 建好 62 表，可直接用于 DDL 核对。
- `icedamericano_mall` 现存 **11 张旧名空表**（`order`/`product` 不在新脚本 DROP 清单内，就地重建会残留这 2 张）。

---

## 5. 本轮沉淀的判据（比任务清单更重要）

1. **接缝原则**：缺陷不在"断言写错"，而在**没有断言站在「A 交给 B」那一行**。只要测试给 A 和 B 各喂一份自己编的数据，接缝就永远不被触碰。已发生 5 次（`SkuCO.productName`、`cartItemId`、`categoryId`、`out_trade_no`、`Result<Map>` 的 Map 字面键）。
2. **注释不得复写会漂的清单**（枚举值/字段名/路径/计数）。已发生 3 次事故：`notification.type` 列注释 → 前端 4 个标签页恒空；`ProductVO.name` 教义；`backend/02` "路径刻意不变"。
3. **门禁过重会逼人删断言**。所以：门禁必须便宜易修；**"删除门禁"要是一条合法且有记录的正路**；能从**类型**上强制的（`Result<VO>`）就绝不做成检查；白名单必须"陈旧即失败"。
4. **测量的可信度低于结论**。本轮我自己的核验手段出错 ≥6 次（grep 不排除 javadoc、`^CREATE TABLE` 漏缩进、MySQL 输出 CR 未剥、`$?` 被二次展开污染、正则漏转义、验收标准算术不可满足）。**凡是"我核验过"，都要能给出命令与原始输出。**
