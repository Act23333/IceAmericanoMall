# 收口清单（Open Items Ledger）

> **用途**：跨会话的工作交底。不是规格文档，不需要进规格索引。
> **维护规则**：任何一批工作落地后，同步更新本文件的「已完成 / 待办 / 已知未修 / 验证边界」四段。
> **最后更新**：2026-10-19（批次 K：第一步 + 触发入口（§1.26）+ **第二步条件预占（§1.28）** 均已落码并跑通 0→4；**第三步已按 §1.29 落码并跑绿（§1.30）**；仍不可上线 —— 端点/调度注册、分配核销、真实打款渠道三缺，见 §3）

## 0. 要数字？跑命令 —— 本文件不是 SSOT

> **本文件只承载「决策 / 意图 / 约束 / 为什么」，不承载状态快照。**
> 任何「当前测试数 / 表数 / 端点对账结果 / 某批完成了几分之几」都以命令输出为准。
> 理由不是洁癖：曾因本文件写着「改动已过大半、零验证」，而真实代码库在同一上午已改完并提交，
> 下一会话照着过期清单动手就会重复劳动甚至覆盖别人的工作。**复述过的数字必然漂，漂了的数字比没有更危险。**
>
> 本文件由 `check-ledger-facts.sh` 强制执行（见 §0.1）：出现「N 测试 / N 张表 / matched N / N 个模块」
> 这类状态断言即失败；确属历史证据的行必须显式标注 `[历史]`。

```bash
# 唯一校验口（第一条就是提交前钩子跑的东西）
bash Implementation/back-end/scripts/verify-gates.sh fast    # 秒级：交接单/计数/ID 不变量/ia-api 单测
bash Implementation/back-end/scripts/verify-gates.sh full    # 全量，与 CI 等价
mvn -f Implementation/back-end/pom.xml clean test             # 仅后端全量单测
node Implementation/back-end/scripts/sync-doc-counts.mjs --check --strict-tests   # 计数事实
node Implementation/back-end/scripts/sync-doc-counts.mjs --list-tables            # 逐表名清单
node Implementation/back-end/scripts/extract-endpoints.mjs                        # matched/codeOnly/docOnly
node Implementation/front-end/scripts/check-error-codes.mjs                       # 错误码表对齐
git log --oneline -10                                        # 谁改了什么（永远比交接单可信）
```

### 0.1 本地门禁（本地 hook 与 CI **共用同一份定义**）

- **事实纠正**：CI 并不是没跑测试 —— `build` job 一直有 `mvn test`。真正的洞是**红基线从未提交**，
  CI 只在 push 后运行，所以对「未提交的红工作区」完全不可见。
- 唯一门禁定义：`Implementation/back-end/scripts/verify-gates.sh`，五个模式：
  | 模式 | 用途 |
  |---|---|
  | `fast` | 提交前：交接单禁复述事实 / 根文档计数 / ID 不变量 / ia-api 单测 |
  | `full` | 推送前 + **CI**：fast + 全量 `mvn test`（含 architecture-tests 全部守卫） |
  | `self-test` | 门禁自身的变异测试：喂合成违规样本，**断言门禁必须红** |
  | `matrix` | 反 flaky：同一门禁在「单类 / 整模块」两个入口结论必须一致 |
  | `install-hooks` | 一次性安装 `core.hooksPath=.githooks`（幂等） |
- **CI 与 hook 不会漂**：`.github/workflows/ci.yml` 的 `Gates (shared definition with local hooks)`
  一步就是 `MVN_ARGS="" ... verify-gates.sh full` —— 同一份脚本，CI 只多传一个在线模式变量。
  另有一步校验 `.githooks/*` 存在且在索引里是 `100755`（钩子被删或丢可执行位即红）。
- 安装：`bash Implementation/back-end/scripts/verify-gates.sh install-hooks`（本仓库已安装并实测）。

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
- `[历史]` 在真实 MySQL 8.0.36 上：0 错误 / 连跑两遍幂等 / 种子落库 / 无旧名表残留（表数见 `--list-tables`）

### 1.8 错误承载收口（批次 R2）
- I40「缺 `amount`」不再折叠成客户错误 → `1000/500/BUG`，**且先 assert 再比较**（原 `||` 短路在挡拆箱 NPE，顺序错了会变成"500 空指针"）
- 上游订单摘要缺金额由 `1101/400` 改 `1000/500/BUG`；**刻意不用** `SYSTEM_BUSY`（那是"服务不可用"，本条是"数据不完整"，重试必然重复失败）
- 退款命令面「支付单/退款单不存在」`3009` → **`1103`**（4 处：I40/AD43/PAY6/I55，同 404、零新增码）
- 契约 §二 **删除**覆盖率计数（原文「实测 98 个 / 共 40 个 / 未收录约 58 个」当时就已全错）→ 改指 `ErrorCode` 为唯一真源；**删除而非校准**的理据写进契约

### 1.9 H2 测试的键集与承重（批次 U1）
- 14 处 H2 DDL 补回丢失的 UNIQUE 键（`user` 4 ×2 实例、`user_auth` 2、`spu.uk_spu_id` ×3、`cart.uk_user_sku`）；`user.user_id` 改 **`BINARY(16) NOT NULL`**
- **真正的 enabler**：`[历史]` 该批把若干 H2 用例由裸 `openSession().getMapper()` 改用 **`SqlSessionTemplate`**（裸 session 只抛 MyBatis `PersistenceException`，Spring 不做异常翻译 ⇒ `catch (DuplicateKeyException)` 永不进入）。**没有这一步，补键等于白补**
- 14 条新用例，4 条直接读 `INFORMATION_SCHEMA.TABLE_CONSTRAINTS` 断言键集 + 列序 + 列类型；**三条变异验证**（删 `uk_phone_hash`/`uk_spu_id`/`uk_user_sku` → 各 3 例红，含 `expected:1 but was:2`，即生产"双账号/双购物车行"的等价物）
- 9 处"缺列导致键无处可挂"按口径豁免并逐条说明（7 个 spu 表无 `short_code` 列、2 个 sku 表无 `sku_id` 列）

### 1.10 F 批收口：让「4xx 透传」被机械强制（批次 F·收口）
- **两只门禁（本批核心，回答「下一个工厂凭什么不回退」）**
  - **结构层** `architecture-tests/.../FallbackCategoryGuardTest`：规则「`org.icedamericanomall.fallback..` 下类名含 `Fallback` 的类，**只要依赖 `BizException`（= 会抛错），就必须依赖 `UpstreamFailures`**」。豁免表**当前为空**且实现为「陈旧即失败」（登记项必须仍真实存在且仍在违规）。
    - **实测坑（必记）**：本包的 `throw` 全写在 `create()` 返回的**匿名内部类**里，字节码落在 `XxxFallback$1.class`；只查外层类的直接依赖会得到**零命中 ⇒ 恒真空规则**（`javap -c SkuClientFallback.class | grep -c BizException` = **0**）。故判定按「外层类 + 其 `类名$*` 嵌套类」合并，并加两条**反空断言**（选中的工厂非空；触发前置条件的工厂非空）。
  - **语义层** `ia-api/.../FallbackDegradeContractTest`：扫描 `target/classes` **自动发现**全部工厂（不维护类名清单），逐个方法验证两件事 —— ① `CLIENT` cause 下**不得**出现 `BizException`（4xx 必须透传 `UpstreamException`）；② `TRANSIENT` cause 下按**人工处置表**执行（`THROWS` 必须抛 `SYSTEM_BUSY` / `RETURNS` 必须不抛）。处置表**刻意不用「有没有 import BizException」自动推导**——那会让 M2 变异自动变绿。新工厂未登记即红、陈旧条目即红。
- **M1 / M2 变异（本批实测，非推断）**：把 `OrderClientFallback` 改回无条件 `SYSTEM_BUSY` → **语义面 + 结构面同时红**；把 `RefundClientFallback` 的 `throw` 改成 `return null`（fail-closed→fail-open）→ **语义面红**（`shouldFollowDispositionTable_whenDownstreamUnavailable`）。两次均按 md5 复原并复跑全绿。
- **`TradeInvoiceClientFallback` 静默 → 抛出**：`void` 的静默返回让调用方无法区分「联动成功/丢失」，会打出假的「已联动冲红」日志。改为 `classify` + 4xx 透传 + 其余抛 `SYSTEM_BUSY`；调用点 `PayEventNotifier#triggerInvoiceLinkage` 本就 `try/catch`（**退款优先不变**），`catch` 内落补偿记录。
- **补偿承载**：新增 `invoice_link_compensation`（表数见 `sync-doc-counts.mjs --list-tables`；`order_no` BINARY(16) / `scene_ref`=biz_ref / `kind` 1=VOID(I56) 2=PARTIAL(I60) / `status` 0=PENDING 1=DONE 2=FAILED_PERMANENT / `retry_count` / `next_retry_at` / `last_error`；`uk_order_scene_kind` + `idx_status_next_retry`）。pay-service 按四层落：`domain/model/invoicelink` 聚合 + `domain/enums` + `domain/repository` 端口 + `infrastructure/persistence`（PO/Mapper/Impl，`INSERT` 捕 `DuplicateKeyException` 归一为幂等命中）。写入再包一层 `try/catch`（补偿表挂了也绝不外溢到退款）。
- **U11**：`PayManager#confirmPaid` 的「支付单不存在」`ORDER_NOT_FOUND(3009)` → `NO_RESOURCE_FOUND(1103)`（同 404 / CLIENT，零感知）；另两处「订单不存在」保留 3009。
- **`package-info` 重写**：**删掉整张「逐 Client 语义速查表」**（正是「注释复写会漂的清单」这类事故源），改为指向两只门禁；修正「仅 authorization-service 开启熔断」这个错事实（实为 authorization/user/merchant/verification/marketing/ai/pay/logistics + search(ADR-052)，trade 走 Sentinel）。
- **教训（补进 §5）**：**「量到的」和「读到的」会互相打架** —— `[历史]` 本轮开工时按交接单读到「16/30 已改、14 个未改」，实际并行会话已在 10 分钟内改完并提交，凭旧清单动手就会重复劳动/覆盖别人的工作。开工第一件事应是 `git log --oneline -5` + `git status`，而不是相信交接单。**根因已用机制修掉，见 §0.1**。

### 1.11 补偿闭环 + 约束棘轮 + 流程修复（批次 G，2026-10-18）
- **补偿消费者上线**（把僵尸数据变成可收敛）：`InvoiceLinkCompensationRetryManager`（编排，含 Feign）+ `infrastructure/scheduler/InvoiceLinkCompensationRetryJob`（裸触发）。沿用同包既有 Job 形态（`RefundResumeJob`/`ReconciliationJob`/`PayTimeoutJob` **都没有分布式锁**，本 Job 亦不加，残余风险写进类注释）。重试策略：`2^n` 分钟退避、封顶、最多 5 次；**下游 4xx（`CLIENT`）直接 FAILED_PERMANENT**（重试必然重复失败）；状态迁移**全部条件更新**（`WHERE id=? AND status=PENDING`，不许读-改-写）；单条失败不中断整批。`order_no` 的 BINARY(16)→32-hex 走 `IdGenerator.toWire`（ADR-062 唯一出口）。
- **类长度棘轮门禁**（把「类 ≤ 300 行」从口号变成机械约束）：`ClassSizeRatchetTest` + `class-size-baseline.tsv`。四条规则——新增超限即红 / 不许增长 / **陈旧即红**（还清必须从基线删）/ **到期即红**。判定抽成纯函数 `evaluate(...)`，用合成数据单测四条规则各自会红；并做过 5 次**变异验证**（把规则分支改成恒假 → 对应单测转红）+ 4 次端到端变异（改基线本身）。
  - `PayManager`（344 行）与 `RefundManager`（334 行）登记为**限时豁免，deadline `2026-11-15`** ——「待 D 批」这种口头挂账不再成立：到期未拆则构建红。
- **门禁自证成为常规**（回答「门禁自己会不会空转」）：
  - `bash scripts/verify-gates.sh self-test`：喂合成违规样本，断言门禁**必须红**（含 `[历史]` 豁免必须豁免）。
  - Java 侧合成违规体进 `mvn test`：`FallbackDegradeContractTest` 的两个自证方法（`shouldFlagSyntheticClientThatRewritesClientErrorToSystemBusy` / `...ThatSwallowsUnavailableFailure`，合成违规体 `RewriteToBusyFactory` / `SilentFailOpenFactory`）、`FallbackCategoryGuardTest` 的 fixture 变异、`ClassSizeRatchetTest` 的四条规则单测。
  - **原则**：只靠「真实仓库跑一遍全绿」证明不了门禁有效 —— 那可能是一条恒为空转的规则。
- **流程修复（根因，不是记性）**：
  1. **交接单不再是 SSOT**：`check-ledger-facts.sh` 禁止本文件复述可测量事实（N 测试 / N 张表 / matched N / N 个模块 / 分数进度），历史证据须标 `[历史]`；已进 CI，且 `verify-gates.sh fast` 首步就跑它。自证时还发现一个漏网形态（`0 failures` 不带「测试」二字）并补上。
  2. **红基线不再无人拦**：`scripts/verify-gates.sh` 是**唯一门禁定义**（`fast` / `full` / `self-test`），`​.githooks/pre-commit` 跑 fast、`pre-push` 跑 full（安装：`git config core.hooksPath .githooks`）。
  3. **事实纠正**：CI 一直有 `mvn test`；漏过去的不是 CI，而是**从未提交的红工作区** —— CI 只在 push 后运行。

### 1.12 让门禁自己可信（批次 H，2026-10-18）
上一批留下的残余全部是「门禁可能不可信」这一类，逐条堵掉：
- **补偿消费者从「假设」变「机制」**：`invoice_link_compensation` 增加 `lease_until`（**认领租约**）。
  重试前先 `UPDATE ... WHERE status=PENDING AND (lease_until IS NULL OR lease_until < now())`，
  **受影响行数=1 才处理**；三条状态迁移（DONE / 退避 / FAILED_PERMANENT）释放租约。
  - **刻意不新增 `PROCESSING` 状态**：新状态会制造一个必须额外回收的新僵尸态；租约是
    **会自愈的时间盒**（到期自动回到可认领集合），互斥与回收两件事一并解决，且不需要回收任务。
  - 残余风险已写进类注释：租约 ≠ 分布式锁（单次 Feign 超过租约时长仍可能并发一次，由下游幂等兜住）；
    到期时间由应用时钟算（多实例秒级偏差 ≪ 租约时长）。
  - `kind` 未判定时**不静默**：`[refund-invoice][compensation][skip] ... 需人工核查` 为 WARN 且明确说「未能判定补偿类型」。
- **交接单门禁的反向自证扩到表述变体**：`check-ledger-facts.sh --self-test` 内置一组**应拦样本**
  （覆盖：数量+量词、**模糊措辞**「约/大约/近/约计/~」、Markdown 强调、`matched N`、`codeOnly N`、分数进度）
  与一组**应放行样本**（设计口径如「最多 N 次」、标识符如 `H2 测试`/`ADR-056`、以及 `[历史]` 行）。
  模糊措辞**一律算违规** —— 「约 N 个测试」与「N 个测试」一样会漂。
  - 强化后立刻抓出旧判据漏掉的**真漂移**：交接单里原本还留着若干过时表数表述（`[历史]`）。
  - 自证还暴露判据自己的假阳性：标识符末尾的数字（如 `H2` 这种带数字的库名）会被误当成「数量 + 事实名词」
    ⇒ 判据改用 PCRE 负向后行断言排除标识符内数字；无 `grep -P` 时降级并**自报降级**（不静默）。
- **反 flaky 升格为常规模式**：`verify-gates.sh matrix` 对四个门禁类比对「单类 vs 整模块」结论，
  **不一致即红**。判据：单入口全绿不足以证明可信（上一批那个运行期 `javac` 的变异样本就是这么漏的）。
- **类长度棘轮语义写死**：新增边界用例 —— 未超限类 **250→290 放行**、**250→310 判红**。
  即棘轮只约束「已超限类」与「越过 300」，**不是「所有类不许变长」**；后者会逼人删门禁。
- **固定入口清单（怀疑门禁空转时按此顺序跑，四个结论必须一致）**：
  ① 单类 `-Dtest=X` ② 整模块 `-pl <module> test` ③ 关联模块 `-pl ia-api,architecture-tests test`
  ④ 全量 `mvn clean test`。第 ③ 入口正是本批发现 flaky 的地方。

### 1.13 门禁可信度收尾 + 两个产品裁决（批次 I，2026-10-18）
- **租约的适用边界写清（真实缺陷）**：首版租约 120s 的依据是「批 100 条 × 单笔 ~200ms ≈ 20s」——
  **只看平均耗时，漏了降级态**。Feign 的 TimeLimiter 上限是 5s，下游全超时时单批最坏
  = 批大小 × 5s ⇒ 租约在批处理没跑完时就过期，另一个执行者会把**仍在处理**的行认领走，
  认领机制在最需要它的场景下失效。
  - 现取值 `CLAIM_LEASE_SECONDS = BATCH_LIMIT × FEIGN_CALL_TIMEOUT_SECONDS × 1.2`，
    并新增**接缝测试** `InvoiceLinkCompensationLeaseBoundaryTest`：**读 `application.yml` 的
    `timeout-duration`** 交叉断言「租约 > 批大小 × 超时上限」「常量与 yml 未漂移」「租约 > 调度间隔」。
    改 Feign 超时而不改租约 ⇒ 测试红。
  - **时钟口径**：写入与比较**都用应用时钟**（`LocalDateTime.now()` 在 Java 侧算好传参，不用
    `DATE_ADD(... INTERVAL ...)`，因 MySQL/H2 方言不一致）。同节点绝对自洽；跨节点依赖 NTP，
    严重漂移（分钟级）时会出现一次并发重调，由下游幂等兜底。**租约 ≠ 分布式锁**，不承诺恰好一次。
- **门禁降级改为判红**：`check-ledger-facts.sh` 原先「无 `grep -P` 就退回 `-E` 并打印警告」——
  **门禁降级 = 门禁变弱**，而变弱的门禁比没有更危险（会给人「已检查」的错觉）。现在直接退出 1
  并说明原因；`FORCE_ERE=1` 可复现该分支（供自证）。
- **17 个超限类补统一 deadline**：「不许增长」是**冻结**，不是约束 —— 冻结久了就变遗产。
  基线现在**每条都带 deadline**（17 个统一 `2026-12-31`；pay 的两个走 D 批轨道，取 `2026-11-15`）。
  到期未拆则构建红；改期只能在**显式提交**里做并写明理由。
- **U3 裁决：删除 5 张死表**（`seller_application` / `flash_order_log` / `merchant_reply_template` /
  `ai_message` / `ai_conversation`）——代码引用实测为 0，其余提及全在设计文档。`flash_order_log`
  的列注释自称「秒杀幂等+兜底」是**假承诺**（实际靠 Lua + `orders.uk_order_no`）。
  `04-Data-Model` 头部已加裁决注记（那 5 处降级为历史设计记录），逐节删除登记为 U14；
  `ConversationId` 里「两套口径并存」的 javadoc 同步改写（隐患随表消失）。
- **U7 裁决：退役 `database/seed-data.js`** —— 它和 `Initialize.sql` 抢同一唯一键（`USERNAME/'admin'`），
  业务 ID 还是 ADR-050 之前的非 hex 形态。种子真源统一为 `Initialize.sql`。

### 1.14 Web 角色端：S0-pre 完成 + S0-0 范围（2026-10-18）
- **顺序（用户定）**：`S0-pre` → `S0-0` → 新开商家售后端点 → `S0-1..4`。**S0 是「首次落地」**，不要再用「重启」（查无取消记录）。
- **S0-pre 完成**：
  - **写侧留痕本来就有**（`AdminAfterSaleController:62/77` 两个写操作都带 `@OperationLog`，类注释引 ADR-032 管道一）⇒ 本步无需新增代码。
  - 新增 `RbacSeedEscalationGuardTest`：判据「**非 ROLE_ADMIN 角色不得持有 `*:*:*` 或 `user:admin`**」，判定抽成纯函数；带**两条合成变异**（非管理员持超级位 / 商家持 `user:admin`）+ 一条反向对照；并**在真实 `Initialize.sql` 里注入 `(3,5)` 实测变红**后复原。
  - **自查教训（值得记）**：首版解析器全文件扫 `(\d+, '..', '..')`，把 `sys_role` 的 `(1,'ROLE_USER',…)` 当成权限位 ⇒ **真实扫描红、变异用例绿**。这正是「变异绿不构成证据」的镜像：**扫描面过宽**也会让门禁骗人。已改为只解析 `sys_permission` 的 INSERT 语句。
- **两个细节已确认（供 S0 叙事定性）**：
  - (a) **ROLE_SELLER 访问 admin 端点 = 403**。`PermissionService.hasPermi` 顺序：`user/permissions 为 null → false`（fail-closed）→ `*:*:*` → **`roles 含 ROLE_ADMIN` 短路** → 否则查集合；商家不命中任何一条。
  - (b) **语义分叉实锤（G2 根因）**：后端 `hasPermi` 有 `ROLE_ADMIN` 短路 + `*:*:*` 通配；前端 `UserContext.hasPermission` **两者都没有**。⇒ 同一个权限串两端结论不同：`ROLE_ADMIN` 在**前端会被判无权限**（菜单消失）、后端放行。这就是「**后端 `@PreAuthorize` 是唯一权威，前端判定只用于隐藏入口**」必须写成硬约束的原因。
- **S0-0 四件事（已定）**：① 23 条「有端点无权限位」的鉴权口径；② 13 条「声明路径 ≠ 实际路径」的收敛；③ 表 C 最终形态（可执行常量表）；④ **契约 ✅ 对账门禁**——`✅` 必须有前端入口、`🔌` 必须登记，否则红（不阻塞 S0-pre）。
- **三条裁决已落**：契约 ✅ 对账进 S0-0④；网关营销路由标 🟡 并登记 **U16**；`apps/web` 的旧文件/行数改为**指向命令**（21/18 两处已改，不再写死）。

### 1.15 跨服务 4xx 透传的「最后一米」（批次 J，2026-10-18）

F 批把「工厂必须原样透传」变成了机械强制（§1.10 两只门禁），但只覆盖了链条的**上半段**：谁抛出。

- **缺陷（实测，不是推断）**：`ia-common` 的 `GlobalExceptionHandler` 对 `UpstreamException` **零引用**
  （全文件 grep = 0）；而 `UpstreamException extends RuntimeException`（历史注释明写「不是 `CommonException`」，
  理由是不绑定本域 `ErrorCode`）。两者相交的后果是：工厂**正确地** `throw upstream` 之后，
  它必然落 `@ExceptionHandler(Exception.class)` 兜底 → `INTERNAL_ERROR` / **HTTP 500**。
  **透传的全部投资在最后一米被碾平**，且没有任何红。
- **根因是模块方向**：`ia-common` 是 `ia-api` 的上游依赖，反向 `import UpstreamException` 会成环
  （`ErrorCategory` 的 javadoc 早已把该禁令写死）。故把「透传所需的**形状**」下沉：
  - `ia-common` 新增 `common/exception/CarriedErrorException`（持 `ErrorInfo` + `upstreamStatus`，
    **不含本域 `ErrorCode`**，且 **`httpStatus()` 是状态码还原的唯一入口**）；
  - `ia-api` 的 `UpstreamException` 改为继承它。**构造器与全部 getter 签名不变** ——
    降级工厂与调用方一个字都不用改（这是能低成本修掉的前提）。
- **响应口径**（`ExceptionResponseSupport#carriedResponse`）：
  - 内部 `/internal/**`：body 就是下游那份 `ErrorInfo` **原样** —— 业务码、文案、`category`、
    `retryable`、`traceId` 一个字段都不重算。多跳链（A→B→C）不会在中途把 C 的业务事实改写成 B 的。
  - 对外 `/api/**`：仍是 `Result`，键集合恒为 `code/msg/data`（**前端契约零变化**），
    **变的只有 HTTP 状态码** —— 从恒 500 变为真实值。这一条由既有那条「`Result` 字段集 + 字面量」守卫继续钉死。
  - 状态码还原：**状态码优先，类别只在拿不到状态码时兜底** —— 上游给过真实状态码（`100..599`）就原样透传（`409`/`422`/`429`/非标准 `499` 都照发）；只有连接层失败 / 超时 / 熔断（`upstreamStatus = 0`）才按类别还原：`CLIENT` 400 / `RULE` 200 / `TRANSIENT` 503 / `BUG` 500。
  - 日志级别**不是**一律 ERROR：`CLIENT` / `RULE` 走 WARN（客户传错参数不该惊动值班），
    `TRANSIENT` / `BUG` 才 ERROR 带堆栈。
  - 兜底分支先沿 `cause` 链找载体（装饰器/容器再包一层的形态），**找不到时行为与改动前逐字一致**。
- **棘轮把「顺手塞实现」逼成了「按职责拆分」（本轮最值得记的一条）**：
  首版把整段实现写进 `GlobalExceptionHandler` → 变成 404 行，`ClassSizeRatchetTest` 判红（`不许增长`）；
  抽出 `ExceptionResponseSupport` 后降到 293 行，**同一条门禁又判红**（`BASELINE_REDEEMED`：
  实测 ≤ 300 即债务还清，必须从基线删除，`基线只减不增`）。删基线条目后才绿。
  **两条规则各自都咬到了**，而且它们联合起来做的事是：不允许「为了方便往映射表里堆实现」。
  代价是本类的到期拆类任务少了一个；基线条目已删，不要再加回去。
- **变异验证（M3/M4/M5 三次，都按 md5 复原）** —— 其中 M3 的第一版结论**被自己推翻**，留档：
  1. **M3**：摘掉 `@ExceptionHandler(CarriedErrorException.class)` → **只有声明层反射守卫红**，
     其余直调用例全绿。**我起初据此写下「那颗反射钉子不可省」，随后补了框架层用例复跑 M3，
     结果框架层用例也全绿** —— 原因是兜底分支的 `cause` 链回溯本来就能接住同一条异常。
     故修正为：**这条显式分支对行为是冗余的**，声明层守卫守的是「显眼」，不是「可用」。
     （教训与 §5 判据 4 同源：**「我对变异的预期」不是证据，跑到才算**。）
  2. **M4**：把 `UpstreamException` 改回 `extends RuntimeException`，**并补一个状态码 shim 让它仍可编译**
     → **只有继承断言红**，round-trip 用例全绿。这条变异证明的是：
     `shouldExtendCarriedErrorException` 是这条回归的**唯一**防线
     （ia-common 侧用的是合成子类，看不见真实类的继承关系）。
  3. **M5（唯一能杀行为的变异）**：**同时**摘注解 + 停掉兜底回溯 → 框架层五条透传用例
     **全红**（响应变成 500 + `code=1000`），反向对照（无关异常仍 500）保持绿。
     **结论：两条路径是刻意的双保险，谁把它们当重复代码删掉，M5 就会红。**
     代码里两处都写了「不要清理」的告示（`handleException` 的注释 + 各自用例的类注释）。
- **框架层证据（本轮补，回答「直调 handler 算不算验证过」）**：
  `CarriedErrorPassthroughMvcTest` 用 `MockMvc` + **真实 `ExceptionHandlerExceptionResolver`** 打
  `standaloneSetup`（只装探针 Controller + 真实 handler，**不需要 Nacos/Redis/MySQL**），
  覆盖：内部 404 + `ErrorInfo` 原样 / 对外 404 + `Result` 三键 / `TRANSIENT` → 503 /
  装饰器包装仍透传 / 反向对照仍 500。**这一层不可省**：直调用例根本不经过类型匹配，
  「Spring 会不会选中它」它们一概看不见。
- **接缝修法**：继承关系这件事**没有任何编译器或跨模块检查能保证**（改回去编译照样过、
  ia-common 的测试照样绿），故在 `ia-api` 侧立 `UpstreamExceptionPassthroughWiringTest`
  —— 它同时看得见两个类，正好站在「A 交给 B」那一行。
- **双保险等价性（提交前补的，最容易漏的一条）**：两条冗余路径自己都「能用」不等于「等价」——
  若各自跑偏，同一个故障会按**包装层数**给出两种报文，比只有一条路更糟。故新增
  `shouldProduceIdenticalResponse_throughEitherRedundantPath`：显式分支 / 兜底直接收到载体 /
  装饰器包装 三条路，status 与 body 必须逐一相同，且带**反空前提**（三条都得真的是 404+下游码，
  否则「三个 500 彼此相等」也会通过）。**M6 实测**：让兜底路径故意返回 500+1000 →
  该用例红（报错文案直接指出「报文形态取决于包装层数，而不是下游说了什么」）。
  ⚠ 比较必须**分开比 status 与 body**：`ResponseEntity` 继承的 `equals` 只比 headers+body，
  整体 `isEqualTo` 会漏掉「状态码不同、body 相同」这一形态。
- **契约口径（提交前补，否则明知不全还提交）**：`05-API-Specification` §二 增加
  「状态码的两个来源」两条规则 —— **本域映射**（状态码 = `ErrorCode.httpStatus`）与
  **下游透传**（状态码 = 下游真实状态码，业务码/文案原样，`/internal/**` body 原样、
  `/api/**` 只换状态码不换信封）。**边界写进契约**：`/api/**` 因此可能出现**不在 §2.1a 清单里的 4xx**
  （如下游 409/422），前端只能走「通用错误 + 直显 msg」，不得据此反推本域码。
  原文只写「内部状态码 = `ErrorCode` 对应状态」，在透传情形下会与实现矛盾 —— 补这两条是**让那句话不再说谎**。
- **批次 J 后补修：两处映射「写反了」（同一根因，两个症状）**。自查方法很简单 ——
  问「这个状态码是从哪来的」。答案：`httpStatus()` 我只信了 `category`，
  而 `category` 恰恰是**可以被下游报文覆盖**的派生标签
  （`UpstreamErrorDecoder#toErrorInfo`：报文里有 `category` 就采信它，缺省才按状态码派生）。
  于是下游报文自相矛盾时，我把唯一的**事实**（HTTP 状态码）丢掉了：
  - `category=CLIENT` + 状态码 **500** → 回过 **400**：把服务端的账记到调用方头上，
    调用方会去改一个本来没错的请求（`09-constraints` §三·六 明文「谎报成 400 更糟」）；
  - `category=RULE` + **非 2xx** → 回过 **200**：用 200 承载失败，熔断器**永不触发**、
    成功率指标虚高（§三·八·5「状态码是被观测面」）；且 `ErrorDecoder` 只在非 2xx 时被调用，
    所以「RULE 带非 2xx」正是这条矛盾路径才可能出现的形态。
  **修法（一条规则同时杀掉两个症状）**：`httpStatus()` 改为 **状态码优先**（`100..599` 原样透传），
  类别只在**拿不到状态码**时兜底。判据一句话：**事实（状态码）优先于解释（category）**。
  顺带把返回类型从 `HttpStatus` 改成 **`int`**：让 `499` 这类合法但非标准的状态码也能透传，
  不被 `HttpStatus.resolve` 解析成 `null` 再回落成 400（那等于又开了一条谎报通道）。
  **M7 实测**：把映射退回「类别优先」→ `GlobalExceptionHandlerTest` 8 例红，
  包括两条带指名信息的缺陷回归（`CLIENT`+500 不得回 400 / `RULE`+非 2xx 不得回 200）。
- **`int` 返回类型的两点复核（改 `int` 之后必须验的，已验）**：
  1. **`0` 不会漏进 `ResponseEntity`**：`0` 在 `httpStatus()` 内部就被还原
     （`hasObservedStatus()` 为假 → `derivedStatus()`，四个分支只产出 200/400/500/503），
     调用方拿到的**已经是可用状态码**；`ResponseEntity.status(0)` 会抛
     `IllegalArgumentException`，所以这条不能靠「调用方记得判 0」。已加不变量用例
     `shouldNeverEmitZeroStatus_whenObservedStatusMissing`：遍历四个类别 × `upstreamStatus=0`，
     断言落点均在 `100..599`。
  2. **没有二次 resolve**：`httpStatus()` 的生产消费点**只有一处**
     （`ExceptionResponseSupport#carriedResponse`，`int status = e.httpStatus()` → `status(int)` 直接建响应）；
     全仓 `HttpStatus.resolve` **零命中**（这条 grep 值得保留：它是「又有人把数值转回枚举」的信号）。
- **已知边界（登记，未做）**：
  - 若上游失败被**替换**（而非嵌套在 `cause` 链上）成别的异常，仍会 500；当前链路不产生这种形态。
  - 下游 `401/403` 属 `CLIENT`，会**原样透传**成 401/403。集群内 `/internal/**` 的鉴权由网关注入头承担、
    正常不会返回 401；若出现，那属于服务间凭证配置故障（可重试），应在**解码侧**归类，
    **不要**在响应层按状态码特判 —— 那会重新打开「两份状态码判断」的漂移口子。
  - 下游报文 `category` 与状态码矛盾时，我们的**响应 body 仍原样透传那份 `category`**，
    于是「发出的状态码」与「body 里的 category」可能不同源。这是刻意的（透传不改写下游事实），
    但调用方若**只看 category** 就会与状态码不一致 —— 已由契约 §二 两条规则写明「以状态码为准」。

### 1.16 U17 第一步：void 降级方法的兜底（2026-10-18）

**做完了什么**（两处改 throw + 两处 catch 体审计 + 一条新门禁）：

- **改 throw（复用现成载体，零新异常）**：
  - `AfterSaleClientFallback#markRefunded`：4xx → `throw upstream`；其余 → `BizException(SYSTEM_BUSY)`。
    删掉那段过时的方法注释（它说「一律不抛…抛错会把成功的退款判成失败」），改为指向
    `PayEventNotifier#writeBackAfterSaleRefunded:106` 的锚点。实测该调用点**有** `try/catch(Exception)`，
    所以类注释一直是对的、方法注释是过时的。
  - `KgClientFallback#deleteEdges`：同上。
- **处置表两行**（与代码同 commit）：`AfterSaleClientFallback` 工厂级 `RETURNS → THROWS`；
  `KgClientFallback` **不动工厂级**（同工厂 `edgeUpsert` 走哨兵 `-1`、`neighbors` 返回空列表，
  按 PRD §16.8.1 必须继续 fail-open），改为在 `METHOD_DISPOSITIONS` 新增
  `"KgClientFallback#deleteEdges" → THROWS`。**一个方法一个结论，别把工厂级当整类。**
- **两个 catch 体的收敛路径（各一句话，按要求写死；写不出的按「暂无消费者」）**：
  - `PayEventNotifier#writeBackAfterSaleRefunded`：**暂无自动消费者** —— 人工核对入口 =
    管理端售后列表按状态 `APPROVED(2)`（`GET /api/admin/after-sale?status=2`，
    `AdminAfterSaleController#pageByStatus`）逐单与 `refund_order` 对照后手工回写；
    **告警级别 = ERROR**（不是 WARN）：这是「钱已退、售后单未回写」的数据不一致，
    且无任何自动收敛手段，WARN 会被同期告警淹掉。原日志里的「待对账/重试收敛」是**假承诺**，已改写。
  - `KgEdgeIngestManager#deleteEdgesSafely`：**暂无可靠消费者**（原写「AD64 全量重建兜底」是**错的**，
    已订正）。实测 `KgAdminManager#rebuild` **不是全量重建、也不做全局清空** ——
    它只 `spuRepository.pageAll(...)` 扫描**仍存在**的 SPU，在售 upsert、下架清边
    （`writeSpuEdges` 的分支）。因此它只能收敛「仍存在但已下架」这一种情形；
    **SPU 行已删除时重建扫不到它，残留边永不清**。日志已带 `srcType`/`srcId` 可定位键。
    —— 这条正是「写不出可靠收敛路径时按『暂无消费者』登记」的实例：**别为了让登记好看而编一个兜底**。
- **⚠ M8 挖出的真洞（本条最值得记）**：处置表的 `THROWS` **只约束「不可用」那一支**。
  CLIENT（4xx）分支的判据是「有没有抛异常」—— 对有返回值的方法，静默返回还能被调用方看见；
  对 `void` 方法，**静默返回与「真的执行成功」完全同形**。实测：把
  `AfterSale#markRefunded` 的 `throw upstream` 换回 `return`（表仍为 `THROWS`）→ **全部用例照旧绿**。
  即「改 throw 的回归可以无声发生」。
  - 补的规则：`VOID_CLIENT_SILENT_ALLOWED`（**当前为空**）+ 「扫描到的 `void`+4xx 静默」集合与登记表
    **双向比对**（新增未登记、陈旧登记 都红）。**M8b 实测**：同一变异 → 2 例红
    （void 登记表 + CLIENT 扫描），正是这条规则与既有语义门禁各咬一口。
  - **它同时纠正了我自己的一个误判**：原以为 `AuthContextClientFallback#revokeUserTokens` 会进白名单，
    扫描发现它在 **4xx 分支其实是抛的**（只有不可用分支静默）⇒ 白名单应为空。
    那张表管「4xx 底下有没有真的抛」，处置表管「不可用时应抛还是应返回」，**职责不同，别混**。
  - **顺带踩了个坑（值得记）**：反空断言原本写成「违规集合非空」，于是**代码正确时反而变红**
    （正确状态就是空集）。反空断言必须落在**扫描面**上（「扫到了几个 void 方法」），
    不能落在「违规集合」上。

**U17 第二步（已完成，2026-10-18）—— 方案 A：五步一个 commit，不留空窗**

- **① `record` 直接改签名（不加重载）**：`record(userId, module, action, target, content)`。
  理由：只两处生产调用 + 两处测试，编译器能覆盖全部；**保留旧重载会留下一条 `target` 恒 NULL 的路径**，
  而补偿对账正建在 `target` 上 —— 那是「新增可选参数」最典型的漏填形态。
- **② 调用点防护**：`UserAdminManager#updateStatus` 在事务提交后调吊销，异常一律接住（不外溢成 5xx），
  按类别分流（见下）。终结记录写失败也吞掉（只打 ERROR）：DB 抖动不得让一次已生效的禁用失败。
- **③ 对账消费者**：`TokenRevokeReconcileManager` + `TokenRevokeReconcileJob`（`@Scheduled`，
  默认 15 分钟）。**用 `@Scheduled` 而非 `@XxlJob`**：`xxl-job-core` 在 `ia-common` 里是 `optional`，
  user-service 没引、也没有执行器配置 —— 为一个补偿任务新引调度框架不值得。
- **④ fallback 改抛**：`AuthContextClientFallback#revokeUserTokens`（4xx 透传 / 其余 `SYSTEM_BUSY`）。
- **⑤ 处置表 + 测试**：`METHOD_DISPOSITIONS` 新增该方法的 `THROWS`（工厂级仍 `RETURNS`，
  `context` / `rolesByUserIds` 必须继续 fail-open —— 判据 8）。

**四条锁定落成了什么**

| 锁定 | 落地 |
|---|---|
| catch 区分 4xx / 不可用，**4xx 不进对账** | 4xx 写终结记录 `permanent-4xx`，对账永不重放（否则每轮重复同一次必然失败的调用） |
| **对账排除已重新启用** | 重放前**重新读**用户当前状态；非 `FROZEN` 一律写 `skipped: re-enabled or missing` 后跳过（否则踢掉正常用户） |
| record 与禁用的事务关系 | **同库**（user-service 单数据源）⇒ `record` 的 `@Transactional`(REQUIRED) 加入 `transactionTemplate` 的事务，**禁用审计与状态变更原子成立**，对账候选集不会有幻影 |
| record 改签名还是加重载 | **改签名**（见 ①） |

**终结标记的形态（只按列判定，禁止解析 `detail`）**：`DISABLE_USER` 与 `REVOKE_USER_TOKENS` 两个 action；
三种终结理由（`ok` / `permanent-4xx` / `skipped: re-enabled or missing`）**共用同一个 action**，
理由只写 `detail` 供人读、**不参与判定**。候选集 = 有 `DISABLE_USER` 且窗口内无同 `target` 的
`REVOKE_USER_TOKENS`。**不建重试状态表**：补偿的全部状态已经在这张台账里（有终结记录 / 没有）。

**时间窗与并发**：只重放 24 小时内的禁用，超出转人工（无状态重试的天然上限，避免「没承载就建表」）；
**不加分布式锁**（沿用同仓 `RefundResumeJob` 形态）—— 多实例最多让同一用户被重放两次，而
`revokeUserTokens` 是**单调推进令牌版本号**，重复调用安全。

**变异实测**：
- **M9** 去掉「已重新启用跳过」→ `shouldSkipAndRecord_whenUserReEnabled` 红（另两条连带 `UnnecessaryStubbing`）；
- **M10** 4xx 不再判终结 → `shouldRecordPermanent_whenRevokeRejectedAsClientError` 红。
两者均按 md5 复原。

**仍未做 / 已知边界（2026-10-18 复审补登记）**：

- **多实例部署形态（已确认结论）**：本仓开发/联调形态 = **单实例**（`docker-compose` 每服务一份、无 replica），
  生产形态**本仓未定义**。多实例下同一用户可能被两个实例各重放一次 —— 结果仍**安全**
  （吊销单调推进令牌版本号，幂等），代价是多一次 RPC；若要求「每轮恰好一次」需加
  `DistributedLock`（ADR-024）或改 XXL-Job 分片。**这是「不是 bug 的 bug」，越晚发现越难改**，故写死在此。
- **24h 窗口起点（写死）**：对某条 `DISABLE_USER` 记录，可重放窗口 = **[该记录 create_time, create_time + 24h]**；
  实现上 `create_time >= now-24h` 与定义等价。**窗口外不再自动重试**，人工入口是只读脚本
  `scripts/inspect-pending-token-revokes.sh --all`（**不再只给一句 SQL**）。脚本口径与代码一致
  （只按 action/target/create_time，禁解析 detail），且明确警告「不要手工补终结记录」。
- **「记录失败也吞」为什么可接受（已写进 javadoc，防止被当 bug 修掉）**：缺一条终结记录的唯一后果是
  对账会再重放一次**幂等**的吊销（多一次 RPC）；而让写库异常外溢会把一次**已提交**的禁用变成 5xx。
  用「一次冗余 RPC」换「一个假失败」是划算的。去掉 `catch` 会形成「禁用成功→记录失败→接口报错→重试禁用」的坏环。
- **告警聚合 / dev 开关（记一笔，不阻塞收尾）**：当前每轮失败只打 WARN，**无聚合、无计数指标**；
  生产接入前应补「连续 N 轮仍有候选未终结」的聚合告警。`@Scheduled` 无环境门槛，**dev 也会每 15 分钟跑一轮**
  （窗口内通常为空，代价可忽略，但排障时要知道它不是只在生产跑）。

**第二步的空窗问题（已裁决：方案 A —— 同批上，不拆可独立合并的 commit）**

- **空窗长什么样**：若先把 `revokeUserTokens` 改成抛、而调用点防护与补偿还没上，一次吊销失败会让
  一次**已提交**的禁用对外报 5xx，且不留任何记录 —— **比现状更糟**。
- **选 A（同一提交内完成）**，内部顺序固定：① `record` 增 `target` 入参 + 新增 `DISABLE_USER` /
  `REVOKE_USER_TOKENS` 两个 action；② 调用点 `try/catch` + 补偿记录 + **ERROR** 告警；
  ③ 对账 Job 消费；④ fallback 改抛；⑤ 处置表补一条 + 测试。
  先做前三步再做后两步，但**同属一个 commit**，不存在「合并到一半」的中间态。
- **不选 B（catch 里同步重试一次）**：① 它把失败路径的 Feign 调用从 1 次变 2 次，最坏耗时翻倍
  （TimeLimiter 5s × 2），而 auth 域真故障时重试必败 —— 只在「瞬时抖动」上有效；
  ② 它**不消除**对补偿记录 + 消费者（Job）的需求，只是把窗口缩小；
  ③ A 已经没有窗口，B 的收益归零、复杂度却留着。

### 1.17 RBAC 现状实测 + 「标准」范围界定（2026-10-18，拆分待评审）

**本批范围（已定）**：**RBAC0 + 统一声明式 `@PreAuthorize`**；**数据权限（行级/店铺维度）另立项** ——
它需要新模型（范围维度 + 查询层强制注入），不是加几个权限串能解决的。别把两件事混成一批。

**实测现状（不是推断）**

| 项 | 结果 |
|---|---|
| 权限位 | 只有 **6 个**（`*:*:*` / `ai:chat` / `order:read` / `product:read` / `user:admin` / `seller:admin`） |
| 真被消费的位 | **2 个**：`user:admin`（21 处）、`seller:admin`（2 处）；`ai:chat` / `order:read` / `product:read` **零注解引用** |
| 判定路径 | **三条**：`@ss.hasPermi` / `UserContext.hasPermission` / 控制器内 `hasRole` |
| method security | `spring-boot-starter-security` 在 `ia-common` 是 `optional`，**仅 6/10 服务声明** ⇒ pay / merchant / ai / logistics **用不了 `@PreAuthorize`**（这就是 `assertAdmin()` 存在的原因） |
| `sys_permission.path` | **空设计**：只在 VO/PO 转换里被读出，**没有路径→权限的解析层** |
| 前端判定 | **完全没有**（grep 0 命中）⇒ 后端是唯一权威，但也无入口隐藏能力 |

**结论**：当前是「管理员 / 非管理员」两个档位 —— 做不了岗位分离（运营只读 / 客服只售后 / 财务只开票）。
门禁上线时还抓到两处**我先前的误判**：`AdminPayController`（其映射方法全部**无注解**、只有代码内 `assertAdmin()`）与
`AdminMerchantController`（同样只有 `assertAdmin()`）—— 它们靠代码内 `assertAdmin()`，且 javadoc 提到
`@PreAuthorize` 会让朴素扫描误判成「已有注解」。**扫描必须先剥注释**（已修）。

**本次已落地（安全增强，不需要评审）**

1. **移除 `PermissionService` 的「`roles` 含 ROLE_ADMIN 即放行」短路**。前提已核实：`ROLE_ADMIN`
   持 `*:*:*`（`sys_role_permission (4,1)`），管理员仍经通配符全通，**行为不变**；但从此可以给管理员**收窄**。
2. **`UserContext.hasPermission` 对齐通配符**。此前它**只做精确匹配**，与 `PermissionService` 语义不同 ——
   今天两份种子数据让两者恰好一致，但一旦把管理员收窄成「只留 `*:*:*`」，
   pay / merchant / ai / logistics 的 `assertAdmin()` 会**开始拒绝管理员**。这是只会在大改时炸的隐性分叉。
3. **新增门禁** `AdminEndpointAuthorizationGuardTest`：`/api/admin`、`/api/seller` 控制器必须有**类级**
   `@PreAuthorize`（类级要求 ⇒ 新增方法自动被覆盖）；豁免表 4 条全部标「待矩阵裁定」+ deadline
   `2026-11-15`，**陈旧即失败**。门禁自证：合成违规样本必须判红 + 反空断言（受管控制器 ≥ 下限）。

**待评审**：`rbac-permission-matrix-draft.md`（§5 裁决结果 / §6 角色→权限映射 / §7 迁移映射，两页待审）。

**Q1–Q6 已裁决（2026-10-18，不再讨论）**：Q1 域 + `{read, write/admin}`、不做全二维；Q2 资金面独立、
发票拆 `invoice:issue` / `invoice:void`；Q3 商家端拆三位且默认角色给全集（存量不失权）；Q4 入驻申请无权限位；
Q5 补 security 依赖、**不做双轨**；Q6 `rbac:admin` 单列、只给超管、**通配符显式排除**。

**已执行（Q5 + Q6）**：

- **Q5**：pay / merchant / ai / logistics 四个 pom 补 `spring-boot-starter-security`；
  `AdminPayController`、`AdminMerchantController` 由代码内 `assertAdmin()` 改为**类级** `@PreAuthorize`，
  原守卫方法**删除**（不做双轨）；门禁里这两条豁免**已移除**（转正）。
  生效前提已实测：`MethodSecurityConfig` 是 `@ConditionalOnClass(EnableWebSecurity)` + `@EnableMethodSecurity`
  且已进 `AutoConfiguration.imports`，四服务亦无自建 `SecurityFilterChain` 不会退避 —— 否则注解只是装饰。
- **Q6**：新增 `PermissionBits`（**唯一**规则实现），`PermissionService` 与 `UserContext` 都委托给它；
  通配符对 `rbac:admin` **不生效**。排除表刻意用**精确位**而非 `rbac:` 前缀 —— 按前缀自动扩会让
  「新增一个 rbac 子位」悄悄改变已有账号的授权范围。

**Q4 附加（限流）**：`POST /api/seller/apply` 因无权限位，必须补**限流**（当前无 `@RateLimit`），
否则它成了唯一可被未入驻用户高频打的下游写入口。执行批一并做。

**拆位前必做三件（已完成）**：

1. **「位必须存在」门禁**（`PermissionBitReferenceGuardTest`，草案 §8 第 0 步）：
   - **解析边界收窄到 `hasPermi` 单参**，`hasAnyPermi` / `hasRole` / `and` / `or` / 字符串拼接
     **一律硬失败**（"看不懂就报错"，不许猜——解析器与被解析语法不同步就会假绿）；
   - **双向比对刻意不对称**：注解引用 → 种子里必须存在且 `status=1`（**硬失败**，否则该端点永久 403）；
     种子定义 → 无人引用 = **WARN + 白名单**（迁移中间态合法，但白名单**陈旧即失败**：
     位已不存在或已被引用都要删条目）。门禁跑起来先抓到我漏记账的 `*:*:*`（超级位按设计永不被直接引用）；
   - **执行顺序写进类注释**：**先种子 → 再注解 → 后删旧位**。反序会有一段时间「注解还指着旧位、
     旧位已停用」= 全线 403。
2. **`rbac:admin` 必须来自种子、不是运行时特判**：`RbacSeedEscalationGuardTest` 新增断言 ——
   该字面量**不得出现在任何主源码**（唯一例外是 `PermissionBits` 的通配排除表，那是"拒绝"规则而非"授予"规则），
   且位一旦引入，持有者必须**恰好是** `ROLE_ADMIN`。当前种子尚未引入该位，用例显式打印 WARN 并在引入后转为硬断言
   （**不静默跳过**：留了可观测的痕迹）。
3. **Q5 过渡态已登记**：`AdminPayController` / `AdminMerchantController` 的 `user:admin`
   拆位时要改 `pay:admin` / `merchant:admin`；两处代码已留 `TODO(拆位, open-items §1.17)`，草案 §7.5 亦有表。

**又踩一次的教训（写进规则）**：**凡"扫描源码找语义"的门禁，一律先剥注释再判定**。
本轮 `rbac:admin` 守卫就被 `PermissionService` 的注释误判成"代码特判"——这已是同族事故第三次
（前两次：grep 扫到 javadoc 里的 `@PreAuthorize`、知识库控制器的 `@PreAuthorize` 提及）。

**三件不可省**：① 种子迁移（`sys_permission` + `sys_role_permission`）；② **缓存失效**
（拆位后必须 `DEL user:perms:*`，否则 TTL 30 分钟内旧权限集继续生效）；③ **端到端 403 用例**
（核心两条：有 `aftersale:write` 无 `pay:admin` 打退款重试 = 403；持 `*:*:*` 无 `rbac:admin`
打角色权限变更 = 403）—— **③ 当前卡 Nacos/Redis，无法执行**。

**Q7 已裁决（2026-10-18）**：**维持单一 `ROLE_SELLER`**（三个位一次性给全）；
员工角色（OWNER/OPS/FINANCE）**另立项**，前置是**商家子账号体系**（邀请/归属 shop/离职回收），
不是加角色编码能解决的。草案 §7.4b 存了拟议表作为立项起点。

**种子迁移已执行（§8 第 4 步）**：新增 20 位 + `rbac:admin` 显式授予管理员 + 商家三位给全；
**`user:admin` 保持 `status=1`**，等注解替换完再停用（「后删旧位」）。
此刻零消费 WARN 即迁移进度计，第 5 步做完归零。

**商家侧售后：当前没有**（实测）。将来引入的**前置是 `after_sale` 补 seller 维度**，
权限位绕不过去 —— 没有 seller 维度的表，给商家任何售后位都等于让他读别家售后。

**两条不阻塞的登记**：
1. `RoleRepository.findAllPermissions` **改名**（如 `listPermissionsForAdminUi` / `findAllPermissionsIncludingDisabled`）——
   现在这个名字看不出"**包含停用位、且不产出判定集**"，很容易被后人拿去当判定来源。
   改名比加注释有效（注释会被忽略）。
2. **迁移脚本必须自动执行 `DEL user:perms:*` 并打印清除条数**，且"清了 0 条"要当结论查
   （前缀写错的表现与"本来没有"完全一样）。

**本轮又暴露一次门禁解析面不足（同族第 4 次）**：种子迁移首次用 `INSERT ... SELECT ... WHERE code='x'`
按编码取 ID，而种子守卫只解析 `VALUES (n,m)` 字面量 ⇒ 绑定集为空 ⇒「非管理员持有」判不出、
反误报「未授予管理员」。已扩展解析器并补合成用例（`shouldParseBindingsDeclaredByInsertSelect`）。
**教训**：门禁的**解析面必须覆盖数据的全部写法**，否则换个写法就静默失明 ——
与「status 过滤只验一条通道」同源。

**Q8 已裁决并落地（2026-10-18）**：类目/品牌/属性挂 **`product:admin`**（平台级商品配置），
不新造 `category:admin`；门禁新增「平台级配置前缀」组并允许**方法级覆盖**（类级会封掉公开 `GET /tree`），
**新增写端点缺注解 = 硬失败**；既存缺口另立项逐条补，不塞进拆位批。

**★ 代码内判权：注解扫描看不见的旧位依赖（本轮真实发现）**：
`SpuController#updateStatus` 把 `UserContext.hasPermission("user:admin")` 当 **isAdmin 标志**传给 Manager。
若只改注解就停用 `user:admin`，**管理员的上下架权限会静默失效**（被当成非管理员 → 归属校验 → 403）。
已改挂 `product:admin`（行为等价：管理员经 `*:*:*` 覆盖，非管理员两者都不持）。
**这是一条通用教训**：`preflight` 脚本的扫描面必须同时覆盖「注解」与「代码内 `hasPermi/hasPermission` 调用」，
否则"注解归零"是假归零。脚本已加该模式（当前 0 处）。

**~~`marketing:read` 待接线~~ → ✅ 已接线（B3，2026-10-19，见 §1.19）**：现在 `marketing:admin` 只由 ROLE_ADMIN 持有 ⇒
**没有"只读运营"档位**——运营要进营销后台就得是管理员，于是"能看"自动等于"能改"。
要让"运营只看不改"，需把营销域 `GET` 类端点接 `marketing:read`（种子里已有、当前零消费）。
**B3 的剩余一半（B3b，需契约先行）**：营销域管理端**没有查询端点**（券/秒杀/促销/拼团只有写），
运营即使拿到 `marketing:read` 也看不到任何列表 ⇒ 「只读运营」只覆盖任务定义。
要真正成立，必须先在契约里补管理端 list/detail（AD57+ 或重编号）再接线；
这属于**功能增量**（PRD/契约先行），不是权限位调整，**不要**在权限批次里夹带。

**另外两条"若放开则需重评位"的警告**（与批 6 提现警告同源）：
- `FlashSaleAdminManager#deleteFlashSale` 对进行中活动**禁删**；若放开，删除会间接影响已下单用户履约/退款 ⇒ 需重新评估是否改挂 `pay:admin`。
- `GroupBuyAdminManager#deleteActivity` 本轮**已补**"有拼团中（未结团）的团禁删"（原来是允许删的，属间接资金影响）。

**当前代码事实核对（2026-10-18，交接用；以代码为准，不以本文为准）**

> ⚠️ **本表已被 §1.18.1 / §1.18.7 推翻**（同一张表也在提醒：**它自己就会漂**）。当时的「守卫切到新解析器 = 未做」
> 与「仓库实扫 0 违规不可信」都成立，但**不可信的原因不是没修，而是扫描面为空**（`mainSources()` 未 `normalize()`）；
> 表里那三行「已回滚，未重做」现已全部按**换解析器**的方式重做（§1.18.7），`payByBalance` 的假阴性也在新解析器上实测为真。
> 现值与结论一律去跑命令：`mvn -o -pl architecture-tests test`。下表保留为**判断过程的留档**，不要照着它开工。
| 项 | 代码事实 | 文档口径 |
|---|---|---|
| 解析器 `MethodScanner`（含 8 条七形态单测） | **已提交**（`JavaMethodScannerTest`） | 可用 |
| 守卫切到新解析器 | **未做** —— `FundTransactionGuardTest:83` 仍是旧 `methods(source)` | **已回滚，未重做** |
| `KNOWN_FUND_METHODS` 扫描面断言 | **不存在**（grep 计数 0） | **已回滚，未重做** |
| `REGISTERED_EXEMPTIONS` 豁免登记表 | **不存在**（grep 计数 0） | **已回滚，未重做** |
| 含 Feign 多写判红 / 模式兜底 | **已提交** | 生效 |
| `payByBalance` 假阴性 | **仍未修**（旧解析器不识别其 3 个本地写） | **待重做** |

⇒ **"仓库实扫 0 违规" 在当前状态下依然不可信**，因为扫描面断言缺席（判据 #16 未落地）。

> ✅ **已完成（2026-10-19，见 §1.18.7）**：下面的设计说明是**当时**的施工图，保留作为决策留档；
> 实际落地与它有出入的两点已在那里写明（解析器改用 JavaParser、事务归属改成直接读注解）。

**下一轮唯一任务的设计说明（≤20 行，落文档不靠记忆）**
```
目标：把 FundTransactionGuardTest 从旧 methods() 切到 MethodScanner.parse()，并补三件验收设施。
1. 切解析器：两处 `methods(x)` → `MethodScanner.parse(x)`；Method 属性改 name()/body()。
2. 事务归属：MethodScanner 只切 body，不认注解 ⇒ 单独用正则按「@Transactional ... 方法名(」抽
   transactionalNames 集合，isTransactional(name, set) 判定；**不还原旧 methods()**。
3. 真冒烟：对真实文件断言——WalletManager 含 refund/deductBalance；PayManager 含 payByBalance；
   且 payByBalance.body() 内 payOrderService.* 出现 ≥3 次（证明 N=3 是本地写、Feign 未计入）。
4. 已知清单断言：KNOWN_FUND_METHODS = {WalletManager#refund, WalletManager#deductBalance,
   PayManager#payByBalance}，扫描结果必须全部出现。
5. 豁免表：REGISTERED_EXEMPTIONS.put("PayManager#payByBalance", 理由)；理由须含
   「依赖 mark-failed 可见性修复」前提。陈旧即失败：条目当前必须仍在违规。
6. 验收四态：a) payByBalance 首次红并贴原始报错（证明 N=3）；
   b) 登记豁免后转绿；c) 已知清单三条齐全；d) **绿且豁免条目为空 ⇒ 红**（豁免表误伤或解析器跳过）。
硬约束：不用 shell 写 Java（一律 edit/write 工具）；每改必编译；预算尽即停并回滚。
```

**资金事务门禁的根因与重构决策（2026-10-18，裁决 A：换解析器）**
根因**不是某个 bug，是架构选择**：用自写文本扫描（regex + 花括号计数）去近似 Java 语法分析。
代价是**无穷的形态清单**（lambda / 匿名类 / switch / 字符串 / 字符字面量 / 文本块 / 注解括号 / 跨行签名 /
泛型尖括号 / record / sealed / 嵌套类型）；实测已出现的失效：`payByBalance` 3 个本地资金写被判 0（假阴性）、
`WalletManager#refund` 解析不出、注解参数里的括号让方法被命名成 `Transactional`。
⇒ 每修一个形态就冒出另一个，成本已超过换工具。

**A1（换解析器）**：`architecture-tests` 引入 `com.github.javaparser:javaparser-core`，
用 `MethodDeclaration` 取 name/body/注解，**删除自写的 `MethodScanner` 及其合成测试**。
三个风险锁死：
| # | 约束 | 理由 |
|---|---|---|
| 1 | **解析失败必须 `orElseThrow`**，禁止返回空集合 | 返回空 = "扫到 0 个方法" = 门禁假绿，正是本轮灾难的成因 |
| 2 | **语料测试**覆盖三件事：方法数 + 方法名 + **body token** | 只断言方法名会被"空壳 body"骗过（本轮已发生） |
| 3 | **旧结论作废重验**：迁移前所有关于"哪些方法被解析/计数多少"的结论**全部重新跑** | 换解析器后旧的绿/红都不再有效 |

**A2（计数换 AST）**：`countFundWrites` 改为在 `MethodCallExpr` 上判定
（`getScope()` 是 `NameExpr` 且名匹配资金仓储 + `getNameAsString()` 在写动词表），
不再用正则扫文本。**A1 验收必须含**：`FundWriteCounterTest` 六条用例在 **JavaParser body** 上重跑通过。
预算不足时：**A1 必须完成并以此验收；A2 可顺延**。

**观测设施处置**：删除 trace（跨 fork 观测不可信，已花三轮预算且结论矛盾），
**保留 `TRACE` 开关**（供将来接结构化"扫描报告"artifact 用），不保留其临时落盘实现。

**判据 #16：工具类门禁必须自带扫描面断言（三种形态齐备）**
"工具报 0 违规"在扫描面未验证前不是结论，只是"没看见"。要求三种断言**同时具备**：
| 形态 | 作用 | 抓什么 |
|---|---|---|
| **数量断言** | 扫到的对象数不得低于下界 | 解析器整体失效（扫到 0 个 → 假绿） |
| **已知清单断言** | 一组**已知必然存在**的对象必须出现在结果里 | 局部漏解析（某个方法/端点没被切出来）→ **本轮就是这么发现 `WalletManager#refund` 没被扫到的** |
| **反例断言** | 构造一条**该被命中**的合成输入，断言确实命中 | 规则退化成"永远不报警"（如正则写错导致恒不匹配） |

**本条的直接来由**：`FundTransactionGuardTest` 曾对 `PayManager#payByBalance`（3 个本地资金写 + Feign）判绿 ——
不是规则错，是**解析器按正则切方法**在 lambda/匿名类/switch/字符串/注释/多行签名/无修饰符 这些形态上切错边界。
⇒ 解析器必须先作为独立单元测死（`JavaMethodScannerTest`，七种形态 + 控制块回归），再接入守卫。

**`payByBalance` 豁免的前提（登记时必须带）**：该豁免成立**依赖 mark-failed 可见性修复落地** ——
`markBalancePaidIfPending` 返回 false 时"钱已扣、单未置成功"当前只有 `log.warn`。
在结构化审计日志（临时方案 C）上线前，**该豁免的残留风险是"静默漏账"**，不得视为已闭环。

**判据 #15：改名类决策必须跑 closure-audit**（2026-10-18，本次真实事故的产物）：
资源段/路径/字段**改名**时，"改代码"只完成了一半 —— **消费方清单**没改等于制造 404，
而 404 又会被 fail-soft 降级吞成"下游不可用"。
要求：凡涉及对外路径段改名（如 ADR-062 的 `/product` → `/spu`），
**同一批必须执行** `scripts/closure-audit.sh` 并在 PR/ADR 里贴出"62/62"这类结果；
该脚本已进 fast 门禁，脚本内自带扫描面断言（提供方 ≥280 / 消费方 ≥55）。

**判据 #14：门禁的精确度必须匹配数据形态**（批 8 两处"规则过粗"的产出，与 #11 同源、比 #12 更上一层的元判据）：
- **过粗** → 误伤合法代码，人就开始加白名单绕过，最后白名单变成免责名单；
- **过细** → 只盯住已知形态，换个写法就漏，绿得毫无意义。
两者是同一枚硬币：**判据的粒度要和被判定数据的真实形态对齐**。
批 8 的两个实例：① 按前缀允许方法级会削弱整个 `/api/admin` 规则 ⇒ 改按类名；
② `rbac:admin` 守卫禁字面量出现在任何主源码 ⇒ 把合法注解判红 ⇒ 改为**剔除整注解节点**后再查。
- 推论一：**白名单/豁免表一律"陈旧即失败"**（类名白名单也必须校验"该类仍存在"，
  与 `VOID_CLIENT_SILENT_ALLOWED` 同一逻辑，不能只在一处有）。
- 推论二：**排除规则的粒度要写在代码里**（"剔除整个注解节点"而不是"剔除注解词"），并**用用例证明**：
  合法注解不红 + 运行时特判红 + 嵌套括号不为难。
- 推论三：**静态扫描的固有局限要登记**，不许假装守住：字符串拼接、`String.format`、
  配置/DB 读取、反射拼装都抓不到。要覆盖那类形态只能靠运行时断言/审计。

**环境恢复后待办清单（两个验证点共用一个阻塞源，合并登记 —— 分散记会漏）**：
| # | 待办 | 需要的环境 | 为什么不能省 |
|---|---|---|---|
| E1 | `scripts/verify-rbac-seed.sh`（确认 23 位齐全、`status=1`、绑定正确、`rbac:admin` 只挂 ROLE_ADMIN） | **MySQL** | 种子用 `INSERT...SELECT` 按编码取 ID，**静默失败形态是"命中 0 行、插 0 行、不报错"**；文件里写了 ≠ 库里有了 |
| E2 | 端到端 403（4 条，含「持 `*:*:*` 无 `rbac:admin` 打角色变更 = 403」） | **Nacos + Redis + 各服务** | 只测 mock 层只能得到"控制器链路验证通过"，**不等于鉴权验证通过** |
| E3 | `redis-cli` 清 `user:perms:*` 并打印清除数 | **Redis** | 拆位后不清缓存，TTL 30 分钟内旧权限集继续生效，测出来的 403 是假的 |

**停用链路的口径（一句话）**：**E1 → E2 → E3 → 停用 → E2（验收）**，任何一步红都**不继续**，
停用是链条里**最后**一步 —— 不是"顺手做了"，而是全部证据到齐后的结论。

**停用 `user:admin` 的边界（写死，防止下一个接手的人把绿灯读成许可）**：
> **preflight 归零 ≠ 可以停用 `user:admin`。**
> 归零只证明"没有代码再引用它"。停用还需：① E1 在有库环境通过；② E2 至少跑通控制器链路层
> （确认新位真的能放行/拒绝）。**当前两条都卡环境**，所以 `user:admin` 必须保持 `status=1`。

**`-pl` 隔离运行的环境提示**（写进交接单，别只留在对话里）：
> 用 `mvn -pl <某服务> test` 隔离跑之前，先 `mvn -o -pl ia-common,ia-api -DskipTests install`。
> 否则该服务会从 `~/.m2` 拿到**旧版共享 jar**，出现"看起来是代码回归、其实是旧依赖"的假失败
> （本批实际踩到一次：`AuthContextClientFallbackTest` 假红，装完即绿）。全量 reactor 不受影响。

**幂等键审计（静态，2026-10-18）**：`scripts/idempotency-audit.py`
口径收定：**无保护 = ①写方法 + ②可能被重试 + ③无幂等键且无条件更新，三条同时满足**。
静态只能判 ①③：②（MQ 重投 / Feign 重试 / job 重放 / 人工重放）**必须运行环境确认，不假装查完**。
- 全景表：`--table`（module / class / method / 幂等键 / 条件更新 / 判定）；
- **分档改为"从入口反向追"（推翻了一跳/两跳的深度启发式）**：
  深数是**深度**指标，而我们要问的是**可达性**（"这个写能不能被一次自动重投触发"）——
  深度 2 漏 3 跳、深度 3 乱连，都不回答原问题。
  现法：**入口（Feign 可达的控制器方法 + MQ 监听方法）→ 同模块内按方法名调用边 BFS → mapper 写方法**，
  端点映射**复用 `closure-audit.py`**（不维护第二份端点表，与契约门禁同源）。
- **① 档逐个确认（2026-10-18，按"拦截在余额/计数更新之前"这一唯一判据）**：
  | 写操作 | 事实 | 判定 |
  |---|---|---|
  | `WalletAccountMapper.deduct` | **已修（2026-10-18）**：改为**先插流水（`uk_transaction_no`）→ 再扣余额**，重复请求命中唯一键 → 幂等返回；余额不足则抛错回滚流水。`biz_ref` 为必填参数（不加不传的老重载），由 pay-service 传 `payOrderNo` | **缺口已堵**；残留决策见下 |
  | `WalletAccountMapper.credit` | **分路径**：退款入账走 `refund(transactionNo,...)`，**先查 + `uk_transaction_no` 兜底**（幂等成立）；充值入账走 `recharge()` → 直接 `credit()`，**无幂等键** | 退款路径**已保护**；充值路径缺口，但入口是买家 HTTP（② 不成立，降级为"可接受但需登记"） |
  | `SkuMapper.decreaseSoldCount` | 取消链路 `if (!cancelled) return false;` **之后**才 `restoreStock()` ⇒ 只在条件状态迁移成功后减销量 | **已保护（调用方守卫）**；注意守卫在调用方，库存侧本身无幂等键 |
  **deduct 修复后的三项收口（2026-10-18）**：
1. **业务键裁决 = `payOrderNo`**。依据：`OrderLifecycleManager#cancelOrder` **没有任何退款调用**
   （取消只发生在未支付阶段）⇒ 走"查不出来"分支 ⇒ 默认 `payOrderNo`。
   同时按"两个方向的失败模式不对称"补要求：**必须加"同一 orderNo 多次扣款成功"告警**
   —— 静默漏扣（用 orderNo 做键会拦掉重付）比多扣更危险，故选能多扣但可告警的一侧。
   告警当前**未实现**（无指标基础设施），登记为待接监控。
2. **事务确认抓出一个真缺陷（已修）**：`deductBalance` 原来**没有 `@Transactional`** ——
   "先插流水 → 再扣余额"在无事务时是两次独立提交，扣款失败会留下**假支出流水**（账实不符）。
   已补 `@Transactional(rollbackFor = Exception.class)`，并加一条**反射断言测试**锁住注解
   （缺注解 = 假流水，这条不是风格问题）。
3. **refund 与 deduct 顺序相反，两者都成立**（代码注释已写明理由）：
   - `refund`：先查重 → 改余额 → 插流水，靠 `uk_transaction_no` 并发冲突抛错回滚（"乐观"）；
   - `deduct`：先插流水 → 再扣余额，靠唯一键在**动余额之前**拦住（"悲观"）。
   为什么不统一：refund 的钱已在系统外退出、这里只做入账，并发孪生用回滚足够；
   deduct 面对用户主动支付（连点/重发更常见），必须在动余额前拦住。

**资金事务门禁（2026-10-18，新增）：`FundTransactionGuardTest`**
- 口径：**同一方法**内 ≥2 个资金表写操作 + 无 `@Transactional` + 不含 Feign ⇒ 红；
  资金类**按调用的表识别**（`wallet_account`/`wallet_transaction`/`pay_order`/`refund_order`/`reconciliation_batch`），
  **不看包名、不看类名**；**无白名单**。
- 豁免是**有原则的**：方法内含 Feign 调用 ⇒ 本来就不允许事务（跨服务调用持有连接、远端失败无法本地回滚），
  要豁免就"真的调一次 Feign"，不允许加名单绕过。
- 首个真实用例：`WalletManager#refund` 有 `@Transactional(rollbackFor = Exception.class)` ✓；
  `#deductBalance` 修复后同类 ✓；`PayManager#payByBalance` 因含 Feign 而豁免 ✓。
- **边界（不许当作"事务正确性"证明）**：只保证"注解写了"。**注解在 ≠ 事务生效** ——
  自调用（`this.x()`）绕过 Spring 代理，本门禁防不住；传播行为/只读/数据源也不检查。
  **实际生效待集成测试**（真实事务管理器 + H2/Testcontainers），当前缺。

**资金事务门禁的两处盲区（下一批收窄，2026-10-18 登记）**：
1. ~~**含 Feign 的豁免是"静默通过"**~~ —— **已收窄（2026-10-18）**：现判据为
   「含 Feign 且本地资金写 ≥2 ⇒ 红（要求登记豁免理由）」，「单写 + Feign ⇒ 通过」。
   旧行为是"有 Feign 就放行"，不留痕。
   下一批改为：**命中原豁免即 `[FAIL]`，要求人工登记豁免理由**（登记进文档，不是加白名单）。
   理由：静默豁免等于"看不见的例外"，和 `@Transactional` 漏写一样危险——只是它不会立刻错账。
   **同时已核实第一个豁免者不是"已豁免"而是"本来就安全"**：
   `PayManager#payByBalance` 的本地写走 `PayOrderRepositoryImpl`（该类 `@Transactional` 数量为 0），
   但 `createPending`/`resetPending`/`markBalancePaidIfPending`/`cancelPending` **各只有 1 条写语句**
   ⇒ 单语句自提交即原子，**不是"同方法多写无事务"**，与修复前的 `deduct`（同方法 2 写无事务）**不同级**。
   其残留风险是另一件事：`markBalancePaidIfPending` 返回 false 时"钱已扣、单未置成功"只 `log.warn`（缺告警），
   已在 `payOrderNo` 告警项里合并跟踪。
2. ~~**资金表清单硬编码**~~ —— **已收窄（2026-10-18）**：显式清单 + **模式兜底**
   （`settle*` / `settlement*` / `points*` / `*_freeze` / `*_frozen` / `*_flow` / `ledger`，忽略大小写），
   并加两条自保断言：**模式必须覆盖显式清单**（防模式退化）+ **模式能认出新资金仓储**（防只认旧表）。
   旧的"五个字面量"写法保留为清单，但不再是唯一识别途径。
   下一批加**模式匹配兜底**（`*_account` / `*_transaction` / `pay_*` / `refund_*` / `*_batch` 等）
   + **表数断言**（扫描到的资金表数量须 ≥ 当前清单长度，否则说明正则退化了）。

**`recharge` 结论的依赖条件（必须写明）**：该结论**依赖「网关无自动重试」这一前提，而它未验证** ——
网关重试策略不在本仓，本轮只证明了"本仓内除买家 HTTP 外无其他入口"。
若网关/前端存在自动重试，本结论失效，`recharge` 需按真缺口处理。

**`markBalancePaidIfPending` 失败可见性（临时方案 C，待升级）**：
现状是「钱已扣、支付单未置成功」只 `log.warn`，可见性不足。
裁决选 **C：结构化审计日志**（不是文本标记，需携带 `event`/`payOrderNo`/`orderNo`/`userId` 等字段），
**登记为临时方案**，其固有限制必须在实现时一并写明：
① **ELK 有采集延迟**（秒级到分钟级），不是实时告警通道；
② **可能丢失**（采集/传输失败、索引写入失败不重试）；
③ **有保留期**（滚动删除后不可回溯），故不能作为对账依据，只能作为"人工核对的线索"。
最终方案仍是 operation_log 落库或指标告警，届时以本条为迁移入口。
**注意**：`operation_log` 表目前**只有 user-service 会写**（ADR-032 口径），
pay-service 现有 `@OperationLog` 只落 AUDIT logger、不落库 —— 这也是选 C 的原因。

**`recharge` 入口普查（2026-10-18，命令与范围写全）**：
| 入口类型 | grep 范围与命令 | 结果 |
|---|---|---|
| MQ 消费者 | `grep -rn "recharge\|creditBalance" --include=*.java */src/main/java`，过滤 `messaging|Consumer|Listener` | **无** |
| 支付/第三方回调 | `grep -rn "recharge(" --include=*.java pay-service/src/main/java wallet-service/src/main/java` | **无**（pay-service 不调充值） |
| 定时任务 | `grep -rn "recharge\|WalletManager" --include=*.java */src/main/java/*/scheduler` | **无** |
| 跨服务 Feign | `grep -rn "recharge" ia-api/src/main/java` | **无**（ia-api 无充值契约） |
| 对外端点 | `grep -rn -B3 "recharge(" wallet-service/.../adapter/web/*.java` | **唯一入口**：`POST /api/wallet/recharge`（买家 HTTP） |

**结论**：充值**只有买家 HTTP 入口**，无 MQ/回调/job/Feign ⇒ 维持"可接受但登记"（② 不成立）。
**未覆盖范围（明说）**：① 前端/网关的重试与自动重发策略不在本仓（需前端与网关配置确认）；
② 反向代理/负载均衡层的重试未查；③ 数据库层重放到不了（无 CDC）。
⇒ 若将来接入支付回调或自动充值，**立即升级为真缺口**。

**弱断言标注（`WalletManagerTest`）**：`余额不足` 用例**只验异常类型 + 流水曾插入**，
**不验回滚**（单测里没有事务代理，"抛异常后流水不存在"无意义）；回滚属集成测试范畴。
注解反射断言同样只到"注解在"这一层，边界已写在用例注释里。

**`payOrderNo` 业务键的风险上调一档**：该选择的前提是"多扣可被告警发现"，
但**告警尚未实现**（无指标基础设施）⇒ 期间可见性**低于设计预期**，
故风险等级从"可接受"上调为**待监控项阻塞**：告警落地前，这条链路的重复扣款只能靠对账兜底。

**判据回顾（重要）**：钱包的幂等**不由"有没有 transaction_no 字段"决定**，而由
  「流水插入/查重是否发生在余额更新**之前**」决定。先插流水（冲突→返回 0）则幂等；先改余额再插流水则余额已错。

- **分档结果（2026-10-18）**：入口解析到 60 个；① 可达 **3 个** / ③ 不可达 **24 个**。
  | 档 | 写操作 | 判断 |
  |---|---|---|
  | ① | `WalletAccountMapper.credit` / `deduct` | **动钱**，重复执行直接错账 —— 最高优先 |
  | ① | `SkuMapper.decreaseSoldCount` | 销量计数，重复执行累计错值 |
  | ③ | RBAC 8（人工触发 + 主键约束）、点赞/评价计数、发票、装饰 | ② 不成立，暂不列为缺口 |
- **入口 60 vs 消费方 62 的差异（①档可信的前提，已查明）**：
  `closure-audit` 侧：消费方 **62**、被消费的端点 **62**、去重后仍 **62**（无重复路径）
  ⇒ 契约层是齐的；差 2 是**本脚本把端点还原成"控制器方法名"时失败**（多行注解 / 无路径映射的还原边界）。
  ⇒ 少数端点若通向写操作，其可达写会被漏判进 ③ —— **①档方向上是低估，不是高估**（差量用 `bash scripts/closure-audit.sh` 与 `python scripts/idempotency-tier.py` 现场对比）。

- **两条已知近似（补齐）**：
  1. **方法名匹配会"漏连"**（非类型解析的固有缺陷）：同模块调用若经由接口/父类/同名重载，
     边可能缺失 ⇒ **① 档可能被低估**（把可达的写算成不可达）。方向与"误连"相反：
     误连制造假 ①（多做确认，安全）；漏连制造假 ③（漏掉缺口，危险）。
     **实例**：`SkuManager` 里 `increaseSoldCount` 与 `decreaseSoldCount` 是相邻的两个调用点，
     反向追踪只追到了后者 ⇒ 同一处已出现漏连证据。
  2. **`MIN_ENTRIES = 40` 的余量**：实际解析到 60，余量 20（≈33%）。
     定 40 不是"看起来够"，而是**低于实测 2/3** 才报警 —— 目的是抓"解析器整体失效"，
     不是抓单个端点漏解析（后者由上面 60 vs 62 的口径差异暴露）。
     代价：若未来入口数自然降到 45，断言**不会**报，届时需按实测重设。

- **工具自身的防护**：入口数 < 40 直接 `[FAIL]` 退出（否则"入口没解析到 ⇒ 全部判不可达"是假绿）。
- **已知近似（不假装精确）**：调用边按**方法名**在同模块内匹配，非类型解析 ⇒ 同名方法可能误连；
  跨模块只能走 Feign/MQ，已在入口覆盖；深度上限 6。

**跨服务契约闭环审计（2026-10-18，环境不可用时的静态替代）**：
`Implementation/back-end/scripts/closure-audit.py` —— 把 ia-api 的 Feign 调用（62 个）与控制器提供端点（289 个）
按 (HTTP 方法, 归一化路径) 精确比对。结果 **61/62 匹配，1 处断点**：

| 断点 | 事实 | 为什么单测抓不到 |
|---|---|---|
| `SearchClient.searchSpus` → `GET /api/search/product` | search-service 只有 `GET /api/search/spu`（`SearchController`），**没有 `/product`** ⇒ 运行时必 404 | fallback 是 **fail-soft**（返回 data=null），调用方转成 `BizException(AI_SERVICE_UNAVAILABLE, "搜索服务暂不可用")` —— 表现为"搜索服务不可用"，与真实故障无法区分；2381 个单测全走 mock，永远发现不了 |
| 影响面 | ai-service `FeignSearchAdapter` → `SearchTool`：**AI 助手的商品搜索一直不可用** | 属"静默降级掩盖真断点"的典型 |

**已修（2026-10-18）**：`SearchClient` 的映射改 `@GetMapping("/spu")`（`SearchPageReq` 全字段可选，
只改路径最安全）。根因确认：**ADR-062 把检索资源段 `/product` 改名 `/spu` 时漏改了这个消费者** ——
改名类决策必须连同"消费方清单"一起改，否则断点会被降级吞掉。
修后 `closure-audit` **62/62 全匹配**。
该客户端走**公开**路径 `/api/search`（集群内应走 `/internal/**`，ADR-004）——偏差保留，注释已登记。

**已升级为常驻门禁**：`scripts/closure-audit.sh`（探测 python3/python）接进 `verify-gates.sh` 的 fast 步骤。
自带**扫描面断言**（提供方 ≥280 / 消费方 ≥55）：扫描器坏掉时是 `[FAIL] 扫描面异常缩小`，
而不是"0 个未匹配"的假绿；缺解释器时 `exit 3` 并打印"这不是通过"。

**4 个无 Feign 消费者的内部端点 —— 已按"文档有无"定性（2026-10-18，含一次自我纠正）**：
> **教训（已一般化）：grep 文档必须覆盖文档的"实际格式"，不能只 grep 你以为的写法。**
> 契约文档里内部端点常写成 `| I7 | GET | /internal/user/{id} | ... |` —— 表格行 + 编号，
> 字面量 `{id}` 在、但**编号 I7 才是检索入口**；同理还有代码段、`<br>`、全角标点、驼峰/下划线变体。
> 一般化规则：**搜端点先搜"末段名"（`user/count`、`coupon/grant`）+ 搜编号（I7/I8/AD57）+ 搜中文名**，
> 三条里任一条命中即视为"有文档"；只搜完整路径会误判为无文档。
>
> **零文档引用 ≠ 零消费者，也 ≠ 可删。** 上一轮我用 `{id}` 字面量 grep 文档，漏掉了
> 契约表里的 `GET /internal/user/{id}`（表格里写作 I7 那一行）；按你要求"删前 grep 全仓 + 网关/运维配置"
> 复查后，**三个端点全部有契约文档**，结论从"待删"改回"保留 + 补注释"。
> **删任何端点前必须做三件事**：全仓 grep（含 `.md` / `.yml` / 运维脚本）、查契约文档表、
> 查网关路由与运维配置 —— 只做第一件就会误删。

| 端点 | 文档线索 | 结论 |
|---|---|---|
| `GET /internal/user/{id}` | **契约文档 I7**（§4.14） | **保留**，补代码注释"谁在用/预留" —— 上一轮判"待删"是 grep 漏了文档，**已纠正** |
| `POST /internal/coupon/grant` | **契约文档有登记** | **保留**，补注释写明人工入口方式 |
| `POST /internal/search/reindex` | **契约文档 + 07-Tasks 有登记** | **保留**，补注释写明运维触发方式 |
| `PUT /internal/logistics/{id}/status` | 零文档引用，但**不是孤儿** | **见下：业务闭环缺口** |

**物流状态推进缺口（比"孤儿"严重）**：
`LogisticsClient` **只有** `createLogistics` 一个方法；`PUT /internal/logistics/{id}/status`
（`LogisticsManager#updateStatus`）在全仓**没有任何生产调用方**（只有控制器与单测）。
⇒ 物流记录创建后状态**无法推进**：`PENDING→SHIPPED→DELIVERED↔RETURNED` 这台状态机在代码里存在，
但**没有推动者** —— 买家确认收货、退货、发货部分包裹都不会回写物流状态。
属**业务闭环缺口**（订单状态与物流记录脱节），不是死接口。**优先级重定（2026-10-18 二次 grep 后）：从 P0 降为 P1（展示不一致），不是退货卡死。**
| 判定项 | grep 结果 | 结论 |
|---|---|---|
| 展示层读取 | `GET /api/logistics/{orderId}`（logistics-service 自己的 `LogisticsController`） | 买家端会**永久显示创建时的状态**（确认收货后也不变）⇒ **展示不一致 = P1** |
| 退货流程依赖 | `AfterSaleManager` 只 `setLogisticsNumber(买家填的运单号)`，**不读物流状态**；logistics-service 内无售后相关代码 | **退货不卡死** ⇒ 不构成 P0 |
| 资金/结算依赖 | 未发现读物流状态 | 不构成 P0 |

⇒ 正确表述：**功能能用但"永远显示已发货"，属数据不一致（P1）**；
只有在将来把"签收"接入结算/售后时效判断时才会升级为 P0。


**影响面（已 grep）**：`order_logistics.status` 目前无任何跨服务读取方参与业务判定
—— 买家端 `/api/logistics` 只做展示，trade 的发货/确认收货**不读也不写**物流状态。
所以缺口的表现是：**物流记录永远停在创建时的状态**（表现为"已发货"之后再无更新），
订单侧状态机照常推进 ⇒ 两侧长期不一致，且无人感知。

**裁决前的观测（必须先上）**：`order_logistics` 各状态的**停留时长监控**
（按 `status` 分组的 age 分位数 + 超过阈值告警）。理由：现在连"停在哪一步"都测不出来，
没有这个数就裁决"谁来推进"，等于凭感觉定方案。

**业务裁决只有两条路（"不做"不是选项）**：
| 方案 | 含义 | 代价 |
|---|---|---|
| ① **trade 回调** | trade 在 ship / confirm-receipt / return 时调 `/internal/logistics/{id}/status` | 改动小、语义清晰；但 trade 要承担物流状态正确性 |
| ② **物流轮询承运商** | logistics 侧定时向快递公司拉轨迹并推进状态 | 更真实（贴近实际物流）；但要接承运商 API、处理对账与限流 |
两条路都要补：`LogisticsClient` 增加 `updateStatus`（方案①）**或** 轮询 job + 承运商适配（方案②）。

**降级策略的配套要求（新增判据）**：
**任何 fail-open / fail-soft 都必须同时具备①契约校验②降级触发率指标**，否则降级就是"静默失败"的另一种写法。
本批实例：`SearchClient` 的 fail-soft 把"路径 404"显示成"搜索服务暂不可用"，
**2381 个单测全绿、无人发现**——契约校验（`closure-audit`）已补上，
但**降级触发率指标仍缺**：没有任何指标区分"下游真挂"与"我们调错了"。
要求：降级路径必须打点（计数器 + 原因码），"我们调错了"应是独立原因码而不是笼统的"不可用"。

**数据权限（行级/租户隔离）标 P0 —— 本项是 RBAC 现状里最大的洞**：
现状「商家只看自己数据」靠**每条查询自己带 `sellerId`/`userId`**，漏一处就是跨租户泄露，
且**没有任何门禁能发现**（鉴权门禁只看"有没有位"，不看"查询有没有按归属过滤"）。
- 与 RBAC0 不同层：位管"能不能调这个端点"，数据权限管"调了能看到谁的数据"。
- 立项前置：先定范围维度（本店 / 本部门 / 本人）+ 查询层如何强制注入（拦截器？Repository 强制参数？）。
- **在它落地前，任何"RBAC 已达标"的说法都不成立。**

**撤权收敛时间要做成指标，不能只依赖主动删**：
现状撤权靠 `PermissionCacheEvictor` 主动 `DEL user:perms:{id}`，缓存 TTL 30 分钟。
- 主动删是"尽力而为"：删失败、实例重启丢事件、跨实例竞态都可能让撤权**延迟到 TTL 到期**。
- 需要一条指标：`撤权生效延迟 = 撤权时刻 → 用户首次被拒绝时刻`，并埋点/告警（超过 N 分钟报警）。
- 没有这条指标，"撤权即时生效"只是**没有证据的断言**——与 `status` 那次同族（机制看起来在，实际没人验证过）。

**批 8 待办（`setUserRole` 的第二道闸）—— ✅ 已落地（2026-10-19 复核，代码与用例都在）**：
> `UserAdminManager#setUserRole` 第 213-221 行已有 `if (target == ADMIN && !UserContext.hasRole(ADMIN)) throw ForbiddenException`，
> 注释写死「当前恒真，勿删」及其理由；`UserAdminManagerTest` 三条用例覆盖（非法 roleType / 非管理员授 ADMIN 被拒 / 管理员授 ADMIN 通过并留痕）。
> 下面保留的是**当初的要求原文**，不必再做一遍。


`PUT /user/{id}/role` 已定挂 `rbac:admin`（能改角色 = 能造超管）。
**另需一条代码断言**：授予 ADMIN 时要求**当前用户本身是 `ROLE_ADMIN`**，否则拒绝。
- 今天是**冗余**的（`rbac:admin` 只有 ROLE_ADMIN 持有），但它防的是**未来的配置变更**：
  一旦有人把 `rbac:admin` 授予别的角色，没有这条断言就等于"给他开了一道造超管的门"。
- 落点应在 **Manager 层**（`UserAdminManager#setUserRole`），不是控制器 —— 控制器改挂注解时容易漏，
  且未来若有第二个入口（批量导入/管理脚本）也走 Manager。
- 配套：一条单测（非 ROLE_ADMIN 持 rbac:admin 时授予 ADMIN → 拒绝）+ 审计记录。

**宣称边界（写死）**：端到端 403 用例跑通之前，只能说「单测 + 门禁通过」，
**不得声称「RBAC 已符合大厂标准」**；`sys_permission.path` 仍是装饰，本批不接路径解析层；
数据权限（行级）不在本批。

### 1.18 资金门禁真扫 + D 批拆类 + 三处小项收口（2026-10-19）

> 批次定性：**上一批留下的「不可信」全部变成了「可执行」** —— 门禁从「扫 0 个文件也算绿」
> 变成「扫不到就红」，拆类从「限时豁免挂账」变成「还清并删除基线」。

#### 1.18.1 资金事务门禁：**扫描面是空的**（真缺陷，根因不是判据）

`FundTransactionGuardTest#mainSources()` 用 `Path.of("..").toAbsolutePath()` 求后端根。
surefire 的工作目录是 `architecture-tests`，故得到的字面路径是 `...\architecture-tests\..` ——
**每一级子路径的字符串里都带着 `architecture-tests`**，于是紧随其后的
`filter(p -> !p.toString().contains("architecture-tests"))` 把**全部源码**滤掉：
扫到 0 个文件 ⇒ 0 违规 ⇒ **恒真空转**。

- 这正是 §5.5 预言的失效形态，也直接回答了 §1.17 那句「"仓库实扫 0 违规" 在当前状态下依然不可信」——
  **它是不可信，而且原因是空转，不是没违规**。
- 修法一行：`.normalize()`。**但只修一行不算收口** —— 让这次事故可被机械发现的是三条扫描面断言：
  ① **数量断言** `MIN_SCANNED_SOURCE_FILES`：源文件数低于下界即红；
  ② **已知清单断言** `KNOWN_FUND_METHODS_AND_TOKENS`：三处已知资金方法必须被解析出来，
     **且 body 必须含预期 token**（只查方法名会被"空壳 body"骗过）；
  ③ **反例断言** 沿用既有六条合成用例（`FundWriteCounterTest`）。
- 另加一条**结构性反空断言**：`REGISTERED_EXEMPTIONS` 为空而实扫全绿 ⇒ 红
  （全绿且没有任何多写被识别 = 解析器跳过了真实违规，不是"仓库很干净"）。
- **M13 / M14 变异（实测）**：摘掉 `payByBalance` 的豁免条目 → 红，报
  「有 3 个资金写且含 Feign」，即 §1.17 记的**假阴性 N=3 现在肉眼可见**；
  把 `normalize()` 退回 → 数量断言 + 已知清单**两条同时红**。均按备份复原并复跑全绿。

#### 1.18.2 真扫之后抓到的第一个真问题：分支互斥被当成多写

`ReconciliationManager#reconcile`：下载失败 → `saveFailedBatch` 早退；成功 → `saveBatchResult`。
**单次执行只会有一个资金写**，但门禁按方法体计数 ⇒ 判红。

- 处理方式：**拆方法**（`saveFailedBatch` / `saveSucceededBatch`），**不登记豁免**。
  理由写死为规则：豁免表只该留「真的无法用事务保护」的形态；把「分支互斥」塞进去，
  下一批人就会照着它塞更多，最终变成免责名单（§5.3）。
- 判据沉淀：**门禁按方法体计数时，互斥分支必须拆方法**，而不是给门禁加"分支分析"——
  后者会让判据与 Java 语法分析纠缠，正是 §1.17「换解析器」那次代价的来源。

#### 1.18.3 D 批拆类：还清并删除基线（不是"再宽限一期"）

| 类 | 改动前 | 改动后 | 拆法 |
|---|---|---|---|
| `PayManager` | 344 行 | 245 行 | 回调族 → `ChannelCallbackManager`；订单状态回写 → `TradeStatusNotifier` |
| `RefundManager` | 334 行 | 291 行 | 退款回调 → `ChannelCallbackManager`（与支付回调同构，放一起才看得出同一口径） |

- **为什么按「用例族」拆而不是按行数砍**：回调是独立用例族（ADR-025）——发起侧关心
  「先落库再调渠道」，回调侧关心「验签 → 按对外号寻址 → 条件更新命中才通知」。
  按行数砍会砍出「半个用例族」，下一个人接不上。
- **`TradeStatusNotifier` 的存在理由**：同一段「回写订单状态 + fail-open 日志」被
  支付成功与支付超时两条互不相关的链路共用。不抽出来，拆类就只能二选一：
  要么留在 344 行的类里，要么**复制这段降级口径** —— 后者是事故源（降级口径必须只有一份）。
- **测试同步迁移（不是删断言）**：支付/退款回调的用例迁入 `ChannelCallbackManagerTest`；
  `PayManagerTest` / `RefundManagerTest` 保留各自用例族的用例（含「回调触发发票联动」一例，
  它验的是 I56 联动，不是回调本身）。
- 基线：两条 `PayManager` / `RefundManager` 条目按**「还清即删」**移除。
  ⚠️ 不删会红（`BASELINE_REDEEMED`）—— 棘轮的另一半是「债还了必须销账」，否则基线会变成许可。

#### 1.18.4 U19：未登记 4xx 的前端兜底（结论：**代码本来就对，缺的是用例**）

- 实测 `getUserMessage` 的既有优先级已满足 U19 ①：`ERROR_MAP` → `isNotFound` →
  **`err.message` 直显** → 通用兜底。即「状态码不在清单里」**不会**被改写成
  「服务异常/第三方/稍后重试」。
- 故本项**没有改运行代码**，只补**可执行用例** `front-end/scripts/check-error-fallback.mjs`，
  并进 CI 的 `consistency` job。两条设计约束：
  1. **同源**：直接 `import` 真实的 `packages/api/error-codes.ts`（Node 类型剥离），
     不复制一份判据 —— 复制出来的判据守的是它自己的想象（§5.11）；
  2. **不新增状态码白名单**（U19 明令）：用例逐个喂 409/412/422/429/451/499，
     断言「有 msg ⇒ 原样直显」「无 msg ⇒ 通用兜底且不含甩锅平台话术」，并反向断言
     这些 HTTP 状态**不被**判成第三方渠道错误（`4xxx` 是业务码段，混用会把透传的 4xx 误判成渠道挂了）。
- **M15 变异**：把兜底改成 `return '服务异常，请稍后重试'` → 用例红（十一处），复原后绿。

#### 1.18.5 U2 / U4 / U8：建库脚本口径订正 + 只读对账

- **U2（`Initialize.sql` 头部三处错事实，已订正）**：
  1. 软删段原文「未启用 `@TableLogic`、没有 `deleted` 列」**已成错**（`invoice_title.deleted` 落地）。
     现改为「默认不启用，唯一例外登记在此」，并写明新增软删表必须同步本段；
  2. 主键段补**联合主键例外**（三张纯关系载体表），并说明为什么不给它们加代理主键；
  3. 新增第 5 条：`xxx_id` **同名不同型四组**（`user_id`/`spu_id`/`sku_id`/`coupon_id`）的 A/B 口径 ——
     **A = 业务键 `BINARY(16)`**（聚合根自持 + 快照表 `order_item`，ADR-050/061）、
     **B = 技术键 `BIGINT`**（引用列）。
     判据一句话：**能出现在对外报文里的是 A，只在本库内 join/过滤的是 B**；
     判定看有没有 `FOREIGN KEY ... REFERENCES`（有 ⇒ B），**不看列名**。
     已知代价照实登记：同名列两种类型，跨表 join 时比较**不会报错、只会恒不相等**（静默错配）。
- **U4（只读对账，`scripts/check-schema-keys.sh` → `.py`）**：**不进 CI、不阻塞提交**。
  三块报告：① `DROP` 与 `CREATE` 双向差集（漏 DROP 会让「复跑重建」在建表处失败，
  文件头 (e) 已把它写成硬要求）；② 四组键型分布；③ 名单 vs 实际类型核对 + 联合主键清单。
  **M16 变异**：把 `order_item.sku_id` 改成 `BIGINT` → 报 `FAIL`（快照表存技术键 = 口径被破坏）。
  配套：Windows 控制台 GBK 会在 `⇒` 上抛 `UnicodeEncodeError` —— 已在脚本内强制 UTF-8 输出，
  理由是「工具要便宜到顺手就跑」，否则没人用。
- **U8**：`docker-compose.yml` 里写死的过时表数改为**指向取数命令**（写死的计数必然漂）。

#### 1.18.6 U6：软删与业务唯一键共存 → **ADR-067**（并纠正本文件自己的一句错话）

- **落点**：`project-docs/adr/067-soft-delete-and-business-unique-key.md`（`uk_user_default` 多 NULL 唯一 + `@TableLogic`）。
- **三条判据**：① 「腾占位」的清理语句**不得**过滤 `deleted`（否则被软删的默认行继续占槽位，用户删了却设不上新的）；
  ② 软删**不**清 `is_default`（保留事实、便于恢复）；③ **自定义 SQL 自带 `deleted = 0`，MP 注入 SQL 交给 `@TableLogic`** ——
  同一张表里两处正反例并存（`updateTitleById` 带、`clearDefaultByUserId` 不带），**都刻意如此**；唯一键里**不放** `deleted`。
- **留档一次自我纠正**：本文件原写「`clearDefaultByUserId` 不带 `deleted=0` **恰好**没踩坑 —— "对的但是偶然的"」。
  **这句是错的**：它是**必须**不带，属承重设计；把它读成偶然，下一个人就会「顺手补上」并因此制造缺陷。
  教训写进 ADR：**看到一处「不一致」先判断它是遗漏还是承重**，写不清理由的那一处才是真的遗漏。
- 顺带把根文档（`CLAUDE.md` / `AGENTS.md` 镜像）里「每张表都以 `BIGINT AUTO_INCREMENT` 为主键」这句改准
  （三张关联表是双列联合主键），并把 `xxx_id` 同名不同型的 A/B 口径写进同一段 —— 与 U2 的 `Initialize.sql` 头部保持同口径。

#### 1.18.7 A1：**换解析器**（自写文本扫描 → JavaParser），旧结论全部重验

> 这是 §1.17「裁决 A」的落地。**动因不是某个 bug，是架构选择**：用 regex + 花括号计数逼近
> Java 语法分析，代价是无穷的形态清单；实测已失效三次（`payByBalance` 假阴性、
> `WalletManager#refund` 解析不出、注解参数括号让方法被命名成 `Transactional`）。

**改了什么**

| 项 | 内容 |
|---|---|
| 依赖 | `architecture-tests/pom.xml` 加 `com.github.javaparser:javaparser-core`（版本锁在 property，`test` scope） |
| 解析唯一入口 | 新增 `JavaSourceParser`：`parse` / `methods` / `body` / `hasAnnotation` / `typeHasAnnotation` / `countFundWrites` / `mainSources`（另有 `hasNoBody` / `isInterfaceFile` / `typeNames` 供语料与 TRACE 用）|
| 删除 | 自写 `MethodScanner`（含花括号计数、`stripComments`、`stripAnnotations`）**整文件删除**，连带其 11 条合成用例 |
| **保留** | `TRACE` 开关（`-Dscanner.trace=true`）—— 但删掉「退出时落盘到 `/tmp/scanner-trace.log`」那套：跨 fork 落盘观测已被证明不可信（三轮预算、结论互相矛盾）。现在只把结构化摘要打到 stdout，作为将来接 artifact 的入口 |
| 顺带删掉的规避代码 | ① 用正则按 `@Transactional ... 方法名(` **反抽**事务方法名（AST 直接读注解）；② 先 `stripComments` 再扫（AST 里注释不在语法树上，天然免疫「注释写着 @Transactional 就被当成有注解」） |

**严格解析是判据的一部分（本轮实测到的坑）**：JavaParser 默认会在语法错误处**恢复**并给出
**部分 AST**。这与「`orElseThrow` 就安全了」的直觉不同 —— 恢复出来的 AST 让「文件没解析干净」
表现为「少数几个方法消失」，门禁看到 0 个资金写于是**判绿**。故判据取
`ParseResult#isSuccessful()`（有任何 problem 即抛），**不是**「拿得到 AST 就算过」。
自证用例：`shouldThrow_whenSourceIsNotParsable`（喂 `class Broken { void m( { }`）——
首版用 `getResult().orElseThrow()` 时**这条用例是红的**，正是它把这条坑挖了出来。

**新语料测试**（`JavaSourceParserCorpusTest`）：七个真实文件，每条**三件事齐备** ——
方法数（精确）+ 方法名（必然存在的一组）+ **body token（按调用名判定）**。
选样口径是「资金写 + 复杂语法混排」：`WalletManager` / `PayManager` / `RefundManager` /
`ReconciliationManager` / `OrderLifecycleManager`（含 lambda 与 Feign）/ `InvoiceBuyerManager`（含注解）/
`InvoiceTitleRepositoryImpl`（几十行 setter 连排，最能暴露「截断」）。
另加三条：每个具体类方法**都必须有 body**（反空壳，反空断言落在**扫描面**上）、
解析失败必须抛、注解可读性。

**`FundWriteCounterTest` 六条在 JavaParser body 上重跑**：样本不再直接喂字符串给 `countFundWrites`，
而是「包成一个可解析的类 → 解析 → 取方法 → 计数」，因为要验的正是「从源码到计数」这一步；
若手工构造 AST 节点，就绕开了「解析器把这段文字切成什么」这个真问题。

**旧结论作废重验（实测，不是推断）**

| 结论 | 旧（文本扫描，且扫描面为空） | 新（JavaParser，AST 判定） |
|---|---|---|
| 扫描面 | 扫到 **0 个文件** ⇒ 0 违规 ⇒ 绿（假绿） | 源文件四位数、过资金预过滤者数十、解析出的方法数千（现场取数：`JavaSourceParserCorpusTest#shouldScanMainSourcesOnly` 与探针输出） |
| ≥2 个资金写的方法 | 一个都没看见 | 只有三处：`PayManager#payByBalance`（n=3、无事务、含 Feign ⇒ **违规**，已登记豁免）、`WalletManager#deductBalance`（n=2、有事务 ⇒ 合规）、`WalletManager#refund`（n=2、有事务 ⇒ 合规） |
| `payByBalance` 的「假阴性 N=3」 | 记为 0，判绿 | **实测就是 3**（`createPending` / `cancelPending` / `markBalancePaidIfPending`；`channelRouter.deductBalance` 是 Feign，不计） |
| 上一轮的两个红（已知清单未命中 / 豁免条目陈旧） | — | **重判为正确**：它们是「扫描面为空」的正确表现；解析器换掉后两条都自然转绿，**不是**放宽判据换来的 |

⇒ 一句话：**上批那句「仓库实扫 0 违规不可信」的结论成立，且原因是空转而非干净**；
现在这条链路（解析 → 计数 → 判红 → 豁免 → 陈旧校验）每一环都有被测对象。

**A2 的输入形态（复核结论，不是口号）**：`countFundWrites` **只有**两个签名，都吃 AST 节点 ——
`JavaSourceParser#countFundWrites(MethodDeclaration, Predicate, Predicate)` 与
`FundTransactionGuardTest#countFundWrites(MethodDeclaration)`；内部是
`method.findAll(MethodCallExpr.class)` + `getScope()` 是否为单个标识符。**全仓已无 `countFundWrites(String)`**，
`FundWriteCounterTest` 六条走的是「包成可解析类 → 解析 → 取 MethodDeclaration → 计数」。
⇒ 「窗口串味」不是被换了地方，而是**输入不再是字符串**（receiver 的边界由语法树回答）。

**语料测试的两处补充（2026-10-19 二次收口）**：
1. **方法数精确断言的失败信息带维护提示**：直接告诉读者「若你刚增删/改名了方法，这是预期的 ——
   把实测值抄进 `corpus(...)` 的第二参数；只有**没动过该类**时失败才该怀疑解析器漏切」。
   实测：把 `STRESS_METHOD_COUNT` 从 35 改成 34，报告里能看到该提示（不是靠注释，而是**失败信息本身**）。
2. **压力样本（锁解析器行为，不为抓违规）**：`OrderRepositoryImpl` —— 按**语句数**实测排序取第一名
   （方法数与单方法体也最大，同时是全仓最大的类）。断言口径与语料一致（能精确的精确、会自然增长的取下界）：
   精确方法数 + 每个方法都有 body + 语句数下界 + 最大单方法体下界 + 两个跨分支的 body token。
   **为什么不堆更多压力样本**：lambda 密度第一的是 `KgEdgeIndexAdapter`（十几处 lambda），
   已在该用例注释里登记为「将来要加第二个就选它」—— 现在加会变成两份要维护的冻结样本。

**计数取数口径的可见性（原：只打 stdout）**：
- **两条结构化通道**：GitHub annotation（`::warning title=计数取数口径::…`，PR 的 Checks 页可见）
  + `GITHUB_STEP_SUMMARY` Markdown 块（运行页顶部直接渲染，含残留报告清单的折叠区）。
- **CI 里直接判红**：`GITHUB_ACTIONS=true` 时出现残留即失败 —— CI 一律 clean 构建，
  **出现残留就说明度量对象是脏的**，此时「数字对上」也不算过（`--check` 分支里单列了 exit 4 那条路）。
  本地增量跑仍只告警（否则每改一次测试都要先 clean，门槛就贵到没人跑）。
- **本轮踩到的第二种陈旧形态**：只比 `.class` 会漏掉「**源码删了、`.class` 与报告都还在**」
  （增量构建不清理 target）—— 实测删掉探针类后总数涨了 1 且**零告警**。判据已补成「报告名对应的
  **测试源码**必须存在（嵌套类折回外层类）」。自证：塞一份假报告 → 告警必现；塞假报告且在 CI 模式 → 判红。

**TRACE 的调试姿势已登记进 `JavaSourceParser` 的类注释**（下一批人不用再试）：
① `-Dscanner.trace=true` 直接可用（surefire 3.x 会把 Maven user properties 提升进 fork），且**不动 JaCoCo**；
② `-DargLine="-Dscanner.trace=true"` 也可用，但**实测会挤掉 JaCoCo 的 agent**（同名属性）⇒ 本次不产 `jacoco.exec`；
③ 输出在 **Maven 控制台**；`target/surefire-reports/*-output.txt` 默认**不存在**，
   该目录的 `*.txt`/`TEST-*.xml` 只有计数与堆栈 ——「打了没看到」多半是找错了地方，搜 `[scanner-trace]`。

**三次变异（M17/M18/M19，均按备份复原并复跑绿）—— 其中 M17 的第一次跑**没咬**，留档**：

1. **M17（body 截断）**：让 `body()` 只返回前 200 字符。**首次结果：全绿** —— 因为我把
   「body token」断言写成了 `method.findAll(MethodCallExpr.class)`（读 AST 调用列表），
   而 `body()` 是另一条路径：**截断它，AST 断言照样看不见**。这正是 §5 判据「对我对变异的预期
   不是证据」的又一次实例（同族：M3）。改法：token 断言挪到 **body 文本** `contains` 上，
   再跑 → corpus 与守卫的已知清单**两处同红**。
   教训写死：**「我要验的是 body，就不能让断言绕开 body」** —— 用 AST 更"高级"不等于验到了你以为的东西。
2. **M18（去掉严格解析）**：退回 `getResult()`（容错恢复）→ `shouldThrow_whenSourceIsNotParsable` 红。
   即「严格性」这条判据**只有那条用例在守**，删它就没有任何东西会红。
3. **M19（`methods()` 恒返回空）**：模拟解析器整体失效 → 三条门禁共十余处红
   （守卫 4、计数值 6、语料 3），且报错直接指名「方法数 0，期望 N」与「方法未被解析出来」。
   这是判据 #16 三形态的**联合**效果：数量断言抓整体失效、已知清单抓局部漏扫、反例抓规则退化。

#### 1.18.8 复核补丁：receiver 边界 / 压力样本成本 / 红粒度 / 共享文件留痕（2026-10-19 复审）

**① `getScope() instanceof NameExpr` 的边界：补掉一半、登记另一半**

- **能补的补掉**：`this.walletAccountRepository.deduct(...)` 的 receiver 是 `FieldAccessExpr`（scope 是 `this`），
  此前被**静默排除** —— 同一句资金写换个前缀就不计数，属假阴性。现新增
  `JavaSourceParser#receiverName`，两种形态都还原（`NameExpr` / `this.field`）。
  **实测全仓该形态零命中**，故不改变任何既有判定（不是"改了判据才变绿"）。
- **补不了的登记**：`getWalletAccountRepository().credit(...)` —— receiver 是**表达式**，
  判它必须做类型推导（`getXxxRepository()` 的返回类型是不是资金仓储），超出「静态名字匹配」。
  **不引入类型解析**，改为两只护栏：语料钉行为（`shouldIgnoreFactoryChainedCall`）+
  **全仓体检** `shouldHaveNoFactoryChainedFundWrites`（真有人这么写就红，逼出「改写法 or 补类型解析」的决策）。
- 三条语料断言已加：`shouldCountThisPrefixedFundWrite`（`this.` 必须算）、
  `shouldIgnoreFactoryChainedCall`（链式不算，**行为钉死**）、`shouldHaveNoFactoryChainedFundWrites`（体检，含反射面反空断言）。

**② 压力样本的维护成本（写死换样条件）**

`OrderRepositoryImpl` 的精确方法数会随该类改动而红，因此它只适合**不常改**的文件。
判据写进用例注释：**变更频率一旦超过「每月一次」就换 `KgEdgeIndexAdapter`**（lambda 密度第一、结构稳定），
查频率的命令（近 30 天提交数）也在注释里；换样本时必须**同时**改该段与 `STRESS_*` 常量。
理由：冻结样本若每月都要改，就会被人改成 `@Disabled` 或放宽成下界 —— 那时它既不再冻结，也没有告警能力。

**③ CI 的红：先修「顺序假红」，再谈「取数脏」**

- **发现真缺陷**：`verify-gates.sh full` 原顺序是 `fast_steps`（首步是计数门禁）→ 全量 `mvn test`。
  而 CI 每个 job 都是**全新检出**、`target/surefire-reports` 一个都没有 ⇒ 计数门禁的
  `--strict-tests` 立刻判「测试数无法度量」⇒ **任何 push 上这道闸都红，且与代码质量无关**。
  也就是说：我要加的「残留报告判红」**永远不会触发**（先被假红挡住）。
- **修法**：`full` 改为**先跑全量 `mvn test`，再跑 `fast_steps`**。附带两个好处：
  取数天然来自本次构建（不是上一轮残留）；测试真红时先报测试红，不被计数红遮挡。
- **红的粒度（退出码分开，语义不同）**：
  | 退出码 | 含义 | 该怎么办 |
  |---|---|---|
  | **4** | **度量问题**：残留报告 / 完全无法度量 / 半量（下限见下） | 先 `mvn clean test` 再复跑；**不要**照着差异改文档 |
  | **1** | **数字不一致**：取数可信，文档过期 | 跑 `--write` 回填 |
  本地只告警不判红（否则每改一次测试都要先 clean，门槛贵到没人跑）；
  **CI 里出现残留 ⇒ 判 4**（CI 是 clean 构建，出现残留说明度量对象是脏的）。
  另外本地「有差异 + 有残留」时会显式提示「这份差异可能是脏数据算出来的」——避免去追幻影。
- **新增「半量」下限**（本轮顺带堵的第三种脏取数）：只跑 `-pl <一个模块> test` 时报告数骤减，
  总数会从四位数掉到两位数，而 `--write` 会把它**静默写进文档**（比残留更危险：残留让数字偏大，
  半量让它偏小，看起来像一次真实的"测试被删了"）。判据：**实测 < 文档现值 × 0.8 ⇒ exit 4**，
  逃生口是 `--tests=N`（显式 = 有人对这次缩小负责）。自证：只留下一个模块的报告 → 立刻 exit 4 并拒绝写回。

**④ 共享文件留痕**

- `open-items.md` 在本批期间被**另一会话**追加并暂存过内容（其中一段与 §1.17 近似重复，仅措辞/标点不同）。
  我此前**没有读到**它（文件从 1172 行涨到 1390 行发生在我开工之后），是被门禁打红才发现。
  处置：① 在 5b 段顶部加一行「**门禁修正，内容未变**」说明只改了一行（`共 N 个端点` → 取数命令指针）；
  ② 删除尾部那段近似重复（保留 §1.17 正本 + 一个「去重留痕」指针），5b / SoD 两段**原样保留**。
- 教训（补进 §5）：**共享文件的「我读过了」有有效期** —— 读完到写完之间它可能已经变了。
  所以①涉及共享文件的改动，收尾必须重跑门禁；②「暂存区里有我的改动但不是我 `git add` 的」要当场说明，
  否则提交会把两方内容算进一个人的提交。

#### 1.18.9 复审三项落地：三段式门禁 / `--tests` 留痕 / 用例意图（2026-10-19 复审）

**① `verify-gates.sh` 改**三段式**（顺序即因果）**

| 段 | 内容 | 为什么在这一段 |
|---|---|---|
| 1 `report_free_steps` | 交接单 / ID 不变量 / ia-api 单测 / architecture-tests / 契约闭环 | **不依赖 surefire 报告**，与"跑没跑过测试"无关；放最前给最快反馈 |
| 2 `test_step` | 全量 `mvn test` | 慢且一旦红要独占视线 |
| 3 `count_step` | 根文档计数 | **输入是报告**，必须排在测试之后 |

`fast` = 段1 + 段3（不跑全量测试）；`full` = 段1 + 段2 + 段3。
修掉两个事故：**假红**（计数跑在测试前 ⇒ 全新检出必红）与**反馈被淹没**（测试红时第一眼看到的是"文档数字不对"）。

**② `--tests=N` 只能是逃生口，不许成习惯**

- 使用时**三条可见通道**同时留痕：控制台 + `::warning title=计数取数口径::` + Job Summary（写明"未经 surefire 实测"）。
- **审计口径**：同一批里出现几次、是否同一原因。若开始**习惯性**出现 ⇒ 说明取数环境坏了（CI 顺序 / 只跑部分模块 / 报告被清），
  **去修根因**，不要继续传参。
- **阈值可调，参数不可滥用**：半量下限默认 0.8，改 `--complete-floor=0.7` 即可（越界值直接拒绝）。
  写死的是「必须显式」，不是「必须是 0.8」—— 阈值会随仓库规模漂，**显式责任**不会。

**③ `this.` 用例的意图写进注释（防"零命中 ⇒ 当冗余删掉"）**

`shouldCountThisPrefixedFundWrite` 的 javadoc 现在明写：**它锁的是「实现不许回退」，不是「仓库里有没有这种写法」**；
删掉 `receiverName` 的 `FieldAccessExpr(scope=this)` 分支即红。并与「零命中体检」（`shouldHaveNoFactoryChainedFundWrites`）作了区分：
*体检抓"真实代码里出现了漏算写法"，本用例抓"漏算能力被加回来" —— 两者缺一不可。*

### 1.19 B3 —— 营销域「只读运营」档位接线（2026-10-19）

**先说清单（这是开工第一步，也是它改变了 B3 的范围）**

营销域管理端共五个控制器，**GET 只有一个**：

| 控制器 | 前缀 | GET | 写方法 |
|---|---|---|---|
| `AdminCouponController` | `/api/admin/coupon` | — | POST、PUT `/{couponId}/disable` |
| `AdminFlashController` | `/api/admin/flash` | — | POST、PUT、DELETE |
| `AdminPromotionController` | `/api/admin/promotion` | — | POST、PUT、DELETE |
| `AdminGroupBuyController` | `/api/admin/group-buy` | — | POST、PUT、DELETE |
| `AdminTaskController` | `/api/admin/task` | **GET**（AD51 分页） | POST、PUT `/{code}` |

⇒ 「把营销域 GET 接 `marketing:read`」在现实里只有**一个**落点。运营依然**看不到**券/秒杀/促销/拼团的列表——
因为契约里根本没有这些管理端查询端点（AD40-42 / AD48-50 / AD54-56 全是写）。**这是功能缺口，不是权限缺口**（见 §2.3 新增的 B3b）。

**发现的真陷阱：类级与方法级 `@PreAuthorize` 是 AND，不是覆盖**

若按"保留类级 `marketing:admin`、给 GET 加方法级 `marketing:read`"去接，**两个位都会被要求**：
只读运营（仅 `marketing:read`）进 GET 仍 403，而管理员因为 `*:*:*` 通配**完全无感** ⇒ 缺陷会长期隐身。
⇒ 要让"只看不改"成立，**整个类必须改成逐方法挂位**，类级注解必须清空。

**改动（同一 commit，按你的要求）**

1. `AdminTaskController`：删类级 `@PreAuthorize`；`page` → `marketing:read`；`create`/`update` → `marketing:admin`；
   类注释写清 AND 语义与"为什么不能类级 + 方法级"。
2. `AdminEndpointAuthorizationGuardTest`：把 `/api/admin/task` 加入 `METHOD_LEVEL_ADMIN_PREFIXES`
   （该类走方法级，不再受"必须类级"约束；守卫要求注解数 ≥ 映射方法数）。
3. `PermissionBitReferenceGuardTest`：**删掉 `marketing:read` 的零消费白名单条目**（已接线 ⇒ 陈旧即红）。

**用例选型：结构断言（AST），不是 mock、更不是种子**

- **mock 为什么不行**：`@PreAuthorize` 由 Spring Security 代理求值，纯 Mockito 里直接 `new` 的控制器**没有代理**，
  "断言调用抛 403"是**假测试**（只证明没开方法安全，不证明权限配对了）。
- **种子/端到端为什么不行**：需要 MySQL 与起服务（E1/E2 未解），且本仓 marketing-service 至今没有 `@SpringBootTest`。
- **所以守"能机械验证的那一层"** —— 而这层恰是接线最常错的地方：新增 `MarketingReadTierGuardTest`
  ① 类级注解必须为空（AND 陷阱）；② 逐方法位与预期**精确相等**（page=read、两个写=admin，且不许漏挂）；
  ③ **反向**：营销域管理端任何写方法都不许挂 `marketing:read`（防"读位挂到写方法上"这种更危险的错）。
  真正的 403 行为验证登记在 **E2**，不在这里假装守住。

**变异（均按备份复原）**：M20 把类级 `@PreAuthorize` 加回 → 红（"类级注解会被同时要求"）；
M22 把 `create` 的位改成 `marketing:read` → **两条同时红**（精确位 + 反向断言）。

#### 1.19.1 两项存量任务的真实状态（2026-10-19 复核：**都未完成**）

**5b 商家端权限替换 —— 未开始（不是"没发现"，是"已登记待替换"）**

- `/api/seller/**` 实测 **19 个映射端点**，分布在四个控制器：
  `MerchantController`（`/api/seller`，7）、`MerchantCouponController`（`/api/seller/coupon`，3）、
  `SellerFinanceController`（`/api/seller/finance`，5）、`KnowledgeBaseController`（`/api/seller`，4）。
  （`FreightTemplateController` 挂在 `/api/item/freight-template`，**不属**该前缀，别混进来。）
- 四个控制器**全无** `@PreAuthorize`（`KnowledgeBaseController` 里唯一那处 "`@PreAuthorize`" 只出现在
  类注释里，讲的是"语义等价于原 @PreAuthorize"）。守卫侧靠 **EXEMPT 表**放行：
  `SellerFinanceController`、`MerchantCouponController` 等条目写的是"待矩阵执行（截止 **2026-11-15**）"。
- ⇒ 现状 = **临时豁免 + 到期倒计时**，不是"已符合"。收口动作写死两条：① 逐端点按 §4.2 人工签；② 签完删 EXEMPT 条目，
  让守卫转强制（`AdminEndpointAuthorizationGuardTest` 会因"陈旧豁免"或"缺注解"直接红）。

**SoD 职责分离 —— 未实现（只有授权约定）**

- 实测：全仓 grep 不存在任何"互斥位"模型（`invoice:issue` × `invoice:void` 的互斥**没有被系统强制**）。
  当前靠**种子里不给同一个角色同时授予两位**来"实现"—— 属**约定**，管理员一次误配即失效。
- 最小实现形态（另立项时按此开工）：在授权域加"互斥位对"注册表 + **授予时校验**（给角色加 A 位则拒绝其已持 B 位，
  反之亦然），并对存量种子做一次一致性扫描（发现既有违规即报，不自动改）。
- 连带项：`setUserRole` 的第二道闸（`UserAdminManager:213-221`）**已存在**，但它的注释自陈"当前恒真"——
  在生产有多个管理员之后是否够用，随 SoD 一并评估（那正是"ADMIN 位与 rbac:admin 位可能分离"的场景）。


#### 1.19.2 四项复审确认（2026-10-19 第二批）

**① `fast` 的计数闸改为显式 `[skip]`**

理由：段 3 的输入是 surefire 报告，本地要么**没有**（刚 clean）要么**是上一轮的**（没 clean）——
两种红都与本次提交无关，属典型门禁疲劳。但跳过必须**说出口**：
`[skip] …full 与 CI 会跑` + 收尾语改成「**报告无关门禁全通过** —— 计数闸已 `[skip]`，这**不是**「全部门禁绿」」。
同时把判据 #13 的差异表里"文档计数 ✅"改成 `⛔ [skip]`，否则表格本身会教人误读。

**② 配置→行为的静态断言（`MethodSecurityWiringGuardTest`，3 条）**

| 断言 | 抓什么 |
|---|---|
| `@EnableMethodSecurity` 存在 | 没有它 ⇒ 全仓 `@PreAuthorize` 都是**装饰**，方法照跑 |
| bean 名 `ss` 真有对应 bean 且方法齐（`hasPermi/hasAnyPermi/hasAllPermi/hasRole`） | 拼错时单测全绿、**运行期 EL 求值 500** |
| 全仓 `@PreAuthorize` 表达式形态一致（bean 名 + 方法名） | 有人写 `hasPermission` 就会扩散 |

实现踩到两个坑（都写进注释了）：`@Service("ss")` 是 **SingleMember** 而非 Normal 注解；
`annotation.toString()` 会**带上前置注释**，于是注释里那句示例
`@PreAuthorize("hasAuthority('order:read')")` 被当成真注解 —— 改用 `getMemberValue()` 取字符串实参。
**变异**：M24a 把一处改成 `hasPermission(...)` → 红；M24b 注释掉 `@EnableMethodSecurity` → 红（两条均复原）。

**③ `--complete-floor`：CI 值写死，CI 不读参数**

- `CI_COMPLETE_FLOOR = 0.8` 是**常量**：CI（`GITHUB_ACTIONS=true`）永远用它，传 `--complete-floor` 会被**忽略并告警**。
  要调整就改常量 —— **常量改动进 review diff，这是唯一的调整留痕**。
- 本地可覆盖（并打印告警说明"本地放宽不改变 CI 的结论"），因为本地放宽是**调试**行为，不是判据变更。

**④ `this.` 用例的"锁"已被变异证明（M23）**

删掉 `receiverName` 里的 `FieldAccessExpr(scope=this)` 分支 → **`shouldCountThisPrefixedFundWrite` 单条红**，
其余 8 条语料用例全绿（说明它锁的正是那一行，而不是被别的东西顺带覆盖）。按备份复原。


### 1.20 签到幂等性实测（2026-10-19）：**并发幂等做到了，「发放」没做到**

**结论先说**：当日去重是**真幂等**（原子），但「打标记 → 发积分」这个顺序让它变成**至多一次（at-most-once）**，
失败即**永久少发**；且整条状态只存在 Redis，没有 DB 真源。

**① 做到了什么（值得保留）**

`RedisSignInStoreAdapter#markToday` 用 `SETBIT(key, dayOffset, true)` 并读**返回值**（Redis 返回该位**原值**）：

```java
Boolean alreadySigned = stringRedisTemplate.opsForValue().setBit(key, offset, true);
if (Boolean.FALSE.equals(alreadySigned)) { ...; return true; }   // 只有真正「从 0 变 1」的那次返回 true
return false;
```

⇒ 并发双击、重试、MQ 重放，只有一个调用拿到 `isFirstToday=true`，积分只发一次。
这比「先 `GETBIT` 再 `SETBIT`」强得多（后者有 check-then-act 竞态），**这个形态要保留**。

**② 三个缺口（按危险度排序）**

| # | 缺口 | 后果 | 证据 |
|---|---|---|---|
| 1 | **先置位，后发积分** | `addPoints` 抛异常（DB 抖动/连接池满）⇒ 位已置、积分没发，**当天再也补不回来**（重试拿到 `isFirstToday=false`） | `SignManager#sign` 第 30-37 行 |
| 2 | **`points_log` 没有幂等键/唯一键** | 一旦第 1 道的门失效（代码改动/未来并发路径变化），双发**没有 DB 兜底** | `Initialize.sql` 里 `points_log` 只有 `idx_user_id` / `idx_create_time` |
| 3 | **签到状态只在 Redis Bitmap，无事实表** | Redis 丢数据（未持久化 / 主从切换 / key 误删）⇒ **断签 + 丢历史**，而连续天数是从 Bitmap 推出来的 | `SignInStore` 只有 Redis 实现；`countContinuous` 基于 Bitmap |

**③ 需要做（T-SIGN，大厂形态）**

1. **加事实表**：`sign_log(id, user_id, sign_date, source, create_time)` + `UNIQUE KEY uk_user_date (user_id, sign_date)`
   —— **幂等由唯一键保证**：重复插入捕获 DuplicateKey 即「今天已签」，直接返回现状（无需前置查询，无竞态）。
2. **顺序改成「同一本地事务内：insert sign_log + insert points_log」**，失败**整体回滚** ⇒ 位没置、下次可重试。
   二者同库同域 ⇒ **本地事务足够**，不需要 Saga/Outbox（只有跨服务才需要）。
3. **`points_log` 加 `biz_key` + `uk_biz_key`**，签到用 `sign:{userId}:{yyyyMMdd}` ——
   与既有 `member_growth_log.uk_order_no` 同口径，重放不双发。
4. **Redis Bitmap 降级为投影/快路径**：写路径以 DB 成功为准，Bitmap 写失败只影响显示（**fail-open**）；
   连续天数从 `sign_log` 计算（或维护 `sign_streak` 汇总），**不再以 Bitmap 为真源**。
5. **三条用例**（缺一条等于没做）：并发双签只发一次；发积分失败后**重试能补发**（证明回滚生效）；
   重复调用返回 `signed=true, earnedPoints=0`（语义不变，防回归）。
6. **迁移**：库无存量数据（ADR-045 口径）⇒ 直接改 `Initialize.sql`，**不需要**数据迁移脚本。


### 1.21 5b 签字与接线前置（2026-10-19）

**判断 A 已签：`seller:withdrawal` 独立成位 —— 但先 grep 了一次"有没有同步打款"，结论是没有**

`WithdrawalManager#applyWithdrawal` 只做三件事：校验金额 ≤ 可用余额、`withdrawal_no` 生成、
落库为 **`PENDING_REVIEW`**。出款在管理端 `review(id, status, …)`（打款成功才级联结算单为已打款）。
⇒ 该位的准确语义是**「发起资金请求」而非「打款」**，必须写进位说明，否则会有人以为拿了它就能出款。

**同一趟 grep 抓到的真缺陷（已登记 T-WITHDRAW，比位拆分更要紧）**：

`SettlementManager#availableBalance` = 所有 `SETTLED` 结算单金额之和，**不扣任何提现**（待审、已打款都不扣）。
⇒ 同一商家可连续申请 N 笔，每笔都通过 `amount <= available` 校验，**待审提现总额可超真实可提现余额**；
财务若逐单放行就是真多付。同时 `WithdrawalManager` **无锁、无幂等键**（只有 `withdrawal_no` 唯一，挡不住同额重复提交）。
修法：① 余额口径改为 `SETTLED − (PENDING_REVIEW + PAID_OUT)`；或 ② 提现时**预占**（新增冻结行/字段），
并加"同额重复提交"去重。判据同 §5 判据：**资金口径的"可用"必须等于"能拿走的"，不能等于"账面上的"**。

**判断 B 已签：两个 GET 写进契约「纯读无副作用」**

| 端点 | 实测 | 契约改动 |
|---|---|---|
| `GET /api/seller/shops/{shopId}/decoration` | `findByShopId(...).orElseGet(ShopDecorationVO::empty)`，读时不建行 | 行内标注「纯读无副作用」+ **今后不得改成"读时初始化"**（要写就加显式写端点） |
| `GET /api/seller/knowledge` | 纯 `listBySeller`，不触发摄取/重排 | 同上 |

**接线前补的三件：两件已实现（守卫内），第三件随接线落地**

1. **`seller:admin` 聚合位登记**（已实现）：`SELLER_ADMIN_AGGREGATE` 清单 + 面断言
   「实际映射方法 == 登记清单 ∪ 端点级豁免」。**M25 实测**：新增 `probeNewEndpoint` 不更新清单 → 红并点名该方法。
   这堵的是聚合位的核心危险：**新端点被静默授权**。
2. **永久豁免 / 临时豁免分 `type`**（已实现）：`ExemptType{PERMANENT,TEMPORARY}` + `record Exempt(type, reason, deadline)`；
   断言 `PERMANENT` 不得有截止日、`TEMPORARY` 必须有且**过期即红**；
   另加**端点级豁免** `ENDPOINT_EXEMPT`（键 `Controller#method`），把 `MerchantController#apply` 登记为永久豁免，
   使同类 6 个兄弟方法能被逐方法强制 —— 这是"整类豁免"和"类级注解"都做不到的形态。
   配套断言：端点级豁免必须**仍存在且仍无注解**（补注解/改方法名 ⇒ 红）。
3. **四个控制器的类级/方法级挂法**（随接线落地，表见 `rbac-permission-matrix-draft.md` §4.2b 二·六）：
   knowledge / coupon → **类级**（端点同位）；finance / merchant → **方法级**（一个类两个位 / 有一个不能挂位）。
   方法级的两个要登记进 `METHOD_LEVEL_ADMIN_CLASSES`（**按类名**，不要用 `/api/seller` 前缀，否则会把
   "必须类级"的规则对全部商家端控制器一起放宽）。


### 1.22 5b 接线完成（2026-10-19）：四个商家端控制器全部转为声明式，EXEMPT 清零

**改了什么**

| 控制器 | 挂法 | 位 |
|---|---|---|
| `KnowledgeBaseController` | 类级 | `seller:knowledge` |
| `MerchantCouponController` | 类级 | `seller:coupon` |
| `SellerFinanceController` | 方法级 | 4 个读 → `seller:finance`；提现 → **`seller:withdrawal`**（新位） |
| `MerchantController` | 方法级 | 6 个 → `seller:admin`；`POST /apply` **无位**（Q4，端点级永久豁免） |

- 种子：新增 `(31, '商家提现', 'seller:withdrawal', 3, '/api/seller/finance/withdrawal', 34, 1)`，
  并把 `ROLE_SELLER` 的迁移授予清单补上本位（**存量不失权**，Q3 口径）。
- 守卫：`METHOD_LEVEL_ADMIN_CLASSES` 增加 `SellerFinanceController` / `MerchantController`（**按类名**，不用 `/api/seller` 前缀，
  否则会把"必须类级"的规则对全部商家端控制器一起放宽）。
- **`EXEMPT` 表已清零**（四条临时豁免按"还清即删"移除）；机制保留：临时豁免必须有 deadline、永久豁免必须写清裁决依据。

**语义写死在代码里（防误读）**：`POST /api/seller/finance/withdrawal` 的 `seller:withdrawal` 是
**「发起资金请求」，不含打款权** —— 出款在管理端 `review`（`finance:admin`）。这条注释写在方法 javadoc 里，
因为"能提现"最容易被读成"能打款"。

**验证**：四个服务 `compile` + `test` 全绿；`architecture-tests` 四个相关守卫全绿；
`verify-gates.sh fast` 全通过；文档计数与实测一致。

**遗留（不在本批）**：T-WITHDRAW（`availableBalance` 不扣待审提现 ⇒ 可超额提现，见 §1.21）——
它是**资金侧口径缺陷**，与权限位无关，签名/接位都不能解决它。


### 1.23 T-WITHDRAW 开工前三确认（2026-10-19，实测）

**① 打款后结算单状态 → 决定扣「待审」还是「待审+已打款」**

`WithdrawalManager#review`：`status = PAID_OUT` 时调 `SettlementManager#markSellerSettlementsPaidOut(sellerId)`，
后者是 `updateStatusBySellerAndStatus(sellerId, SETTLED, PAID_OUT)` —— **把该商家全部 SETTLED 结算单置为 PAID_OUT**。
而 `availableBalance` 只统计 `SETTLED` ⇒ 打款后主动归零。

⇒ **必须只扣「待审（PENDING_REVIEW）」**：再扣已打款就是**双重扣**（结算单已不在 SETTLED 里）。

**② 但顺带发现更严重的第二个缺陷（T-WITHDRAW-2）**：`markSellerSettlementsPaidOut` 是**按商家全量**置位，
不是按提现金额分配。若商家有 1000 已结算、只提 100 ⇒ 通过后 **1000 全部变 PAID_OUT**，剩下 900 **再也提不出来**（钱被卡住）。
修法：按提现金额 **FIFO 分配**到结算单（或新增分配表 `withdrawal_settlement`），逐单/逐笔置位，不许全量置位。

**③ 条件更新的前提：现在**没有可锁的余额行****

用户要求的 `UPDATE … WHERE total - frozen >= ?`（与 `deductStock` 同形、不用行锁）在当前 schema 里**无处可写**：
`withdrawal` / `settlement` 都是流水行，没有"每个商家一行"的余额聚合行。
⇒ 必须先加表（否则只能在应用层 check-then-act，正是要避免的竞态）：

```sql
CREATE TABLE `seller_fund_account` (
    `seller_id`      BIGINT PRIMARY KEY COMMENT '商家 user_id（一格一商家）',
    `settled_total`  INT NOT NULL DEFAULT 0 COMMENT '已结算合计（分）',
    `frozen_amount`  INT NOT NULL DEFAULT 0 COMMENT '待审提现冻结额（分）',
    `paid_total`     INT NOT NULL DEFAULT 0 COMMENT '已打款合计（分）',
    `update_time`    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);
-- 提现申请：一条语句同时完成"校验 + 冻结"（0 行 = 余额不足，与 deductStock 同形态）
UPDATE seller_fund_account SET frozen_amount = frozen_amount + #{amount}
 WHERE seller_id = #{sellerId} AND settled_total - frozen_amount >= #{amount};
```

**④ 幂等键：`Idempotency-Key` 由前端传，落 `withdrawal.request_key` + 唯一键 `(seller_id, request_key)`**；
前端改不了时降级为「用户 + 金额 + 日期」，并在契约里写死**降级规则的代价**（同一天两笔同额会被判为重复）。

**⑤ 5b 永久豁免的存在性仍被检查（M26 实测）**：把 `MerchantController#apply` 改名 → 守卫红并报
「类里找不到方法 apply（改名/删方法？请删条目）」。**偏离说明**：用户说"只跳过是否已接线"，
实现上**两条都查**（若 apply 将来被挂上位子，同样红）—— 理由是"永久豁免却被接线"本身就是需要人复核的信号。

**⑥ T-SIGN 的格式基准（查了实际存法，不停在"同口径"）**：`member_growth_log` 存的是
`order_no BINARY(16)`（订单业务键 **UUIDv7 原始字节**）+ 单列 `uk_order_no`；
而"用户 + 日期"类幂等的**既有先例是 `task_record.uk_user_task_date(user_id, task_code, biz_date)`（复合唯一键）**。
⇒ T-SIGN 应照 `task_record` 用**复合唯一键**，**不要**发明 `sign:{userId}:{yyyyMMdd}` 字符串；
`points_log` 目前无 `biz_key` 列，签到发放改为"与 sign_log 同事务"后，幂等锚点由 sign_log 的唯一键承担。


### 1.24 T-WITHDRAW 开工三件（契约先行，2026-10-19 裁决后）

**裁决**：定时任务自动结算为主、手动确认异常兜底；两种触发**都调同一个 `SettlementManager#settle(settlementId)`**
—— 触发方式后定，权威方案不退化。

**① 「可结算条件」必须字段化（`period_end` 只是时间点，不是条件）**

```java
/**
 * 结算单置为「已结算」（唯一入口：定时任务与手动兜底都调本方法）。
 *
 * <p><b>可结算条件（全部字段化，能用 WHERE 表达）</b>：
 * <ol>
 *   <li>{@code status = PENDING(1)} —— 幂等：已结算/已打款的再调必为 no-op；</li>
 *   <li>{@code period_end <= now} —— 账期已走完（**时间条件**，不是"看起来该结了"）；</li>
 *   <li>{@code settlement_amount > 0} —— 零额单不结算（否则白占余额行）；</li>
 *   <li>{@code order_count > 0} —— 无订单的账期不结算。</li>
 * </ol>
 *
 * <p><b>实现形态</b>：一条条件更新 + 影响行数判定，0 行 = 不满足条件（**幂等 no-op，不抛异常**）：
 * <pre>
 * UPDATE settlement SET status = 2, update_time = NOW()
 *  WHERE id = ? AND status = 1 AND period_end &lt;= ? AND settlement_amount &gt; 0 AND order_count &gt; 0
 * </pre>
 * 与 {@code deductStock} 同形态：不用行锁，并发下只有一个事务拿到 1 行。
 *
 * <p><b>权威性</b>：命中 1 行时，**同事务**内更新 `seller_fund_account.settled_total += settlement_amount`
 * （UPSERT）。这是唯一允许改动 `settled_total` 的两个入口之一（另一个是打款，见下面的分配表）。
 *
 * @return true=本次真的完成结算；false=条件不满足（已结算/未到期/零额/无订单）
 */
```

**② 单笔提现限额（缩小自动结算 bug 的爆炸半径）**

`WITHDRAWAL_MAX_PER_REQUEST`（配置项，不给魔法值）：超过则 400 `PARAM_ERROR`，文案给出上限值。
理由写成注释：**限额不是风控，是止损**——当结算口径写错时，它把"一次可提走全部余额"降成"一次只能提走一部分"，
给人留下发现与止血的时间窗。

**③ 自检两条（只验数值相等会假绿）**

| 检查 | 判据 | 为什么必须有 |
|---|---|---|
| 数值一致 | 每个商家 `seller_fund_account.settled_total` == Σ `settlement WHERE status=SETTLED` | 基础对账 |
| **覆盖面一致** | `seller_fund_account` 行数 == **有 SETTLED 结算单的商家数**（用 `COUNT(DISTINCT seller_id)`） | 只有行数对得上，才能排除"少建了余额行/多建了空行" |
| **非空断言** | 若 `settlement WHERE status=SETTLED` 为 **0 行** ⇒ **自检必须失败**（而不是"0==0 通过"） | 当前代码里没有 `PENDING→SETTLED` 入口，空集是常态 —— 空集上的自检只会安静地绿 |

**④ `sign_type` 语义定为「签到类型」，纳入唯一键**

`UNIQUE KEY uk_user_date (user_id, sign_date, sign_type)`（成本一行 DDL）。
理由：**"来源标记"用注释/列就够，不需要进唯一键**；而"补签 / 活动签到 / 每日签到"是**同一用户同一天可能多条**的
真实业务形态 —— 只有把它放进唯一键，才表达得了"每日签到一天一次、补签可另有一条"。
若留在唯一键之外，将来加补签就必须改唯一键（带数据迁移），现在只是多一列。


#### 1.24b T-WITHDRAW 实施计划（**第一步、第二步均已落码并跑绿：§1.25 / §1.28；第三步未开工**，2026-10-19 定稿）

> 状态：**第一步与第二步的代码与用例均已落码、均已跑通 0→4（见 §1.25「验证结果」与 §1.28）；第三步一行未写。**
> 本节是**可直接照着做的任务分解 + 验收口径**。与 T-5b **不合批**（用户裁决）；顺序固定为下面的 1 → 4。
> ⚠ 第一步的**实际口径**以 §1.25 为准：`settled_total` 定为「累计已结算（只增）」，打款只加 `paid_total`，
> `frozen_amount` 整列留到第二步 —— 本节原表「打款侧三分量一起动」的写法与自检等式不兼容，已订正。

| 步 | 内容 | 触及文件 | 完成标志 |
|---|---|---|---|
| **1** | ~~合并「settle 加 + 打款减」~~ → **settle 累加 `settled_total` + 打款累加 `paid_total`**（`frozen_amount` 不动）：`SettlementRepository#settleConditionally` 条件更新（返回影响行数）→ `SettlementManager#settle` 命中则**同事务** `settled_total += amount`；打款侧 `paid_total += amount`（**不带额度条件**，额度属第 2 步的申请侧）同事务；**删除** 按商家全量置位的 `markSellerSettlementsPaidOut` | `SettlementRepository`(+Impl)、`SettlementManager`、`WithdrawalManager#review`、`seller_fund_account` 表 | **自检三条全绿**（用例：`SettlementFundAccountH2Test`） |
| **2** | 单笔提现限额 `WITHDRAWAL_MAX_PER_REQUEST`（配置项，不给魔法值；注释写「限额不是风控，是止损」）**+ `applyWithdrawal` 预占 `frozen_amount`**（条件更新：「可提现额 ≥ amount」时 `frozen_amount += amount`，0 行=余额不足） | `WithdrawalManager` + 配置 + `SellerFundAccountRepository` | 超限 400 有用例；连提两笔第二笔必失败 |
| **3** | `withdrawal_settlement` 分配表 + 打款按金额 FIFO **核销**（记「哪笔提现核掉哪些结算单」）+ **拒绝释放**（删/废分配行，`frozen_amount` 回 available）。⚠ **不做结算单行状态置位** —— `settlement` 不承载打款事实（见 §1.25 状态语义） | 新表、`WithdrawalSettlementRepository`、`WithdrawalManager` | 部分提现后剩余仍可提；提现 100 只核销 100 |
| **4** | 用例三条：① 连提两笔（第二笔失败）② 重放同 `Idempotency-Key` 返回**原单**（不是报错、不是新单）③ 只核销被提走的那部分（剩余仍可提） | trade-service 测试 | 三条全绿 |

**验收口径（用户定）：第一步做完，自检三条必须绿 —— 它是第一步的验收标准，不是第二步的。**
（实施口径见 §1.25：`settled_total` = 已结算，取数**只用 `status = SETTLED`** ——
`SETTLED` 的语义是「可提现」，不是「未打款」；**不要**再按 `IN (SETTLED, PAID_OUT)` 取数，理由见 §1.25 状态语义。）

| 自检 | 判据 | 不绿 ⇒ 说明什么 |
|---|---|---|
| 数值一致 | 每商家 `settled_total` == Σ 该商家 `settlement_amount WHERE status = SETTLED` | 「加」与状态迁移**没同事务** |
| 覆盖面一致 | `seller_fund_account` 行集合 == 「有 `status = SETTLED` 结算单的商家」集合 | 少建/多建了余额行 |
| **非空断言** | 已结算集合为 **0 行** ⇒ **自检必须失败** | 第一步还没跑通一条真实结算（空集假绿）|

**已知前置（本批已补，见 §1.26）**：在 §1.26 之前，代码**没有 `PENDING→SETTLED` 的触发者**（§1.23①），
第一步只把 `settle()` 做成**唯一且原子的入口**；**触发入口当时是上线阻塞项**。
§1.26 已交付「调度 + 运维脚本」两条路径，**仍未交付管理端端点**（契约草案见
`collaboration/settle-trigger-draft.md`）。


### 1.25 T-WITHDRAW 第一步落地（2026-10-19）：**只动 `settled_total` 与 `paid_total`**

> 状态：**已提交到 `wip/t-withdraw-step1-unverified`（commit `fd569cb0`，消息带 `[UNVERIFIED]`），并已跑通 0→4 门禁**
> （逐项结果见本节末尾「验证边界」）。**标签仍是 `[UNVERIFIED]` 而不是改成 `[VERIFIED]`** —— 理由：本批的
> **功能面**未完成（`settle()` 无触发者、`frozen_amount` 未做、分配表未做），把提交锁死在「未完成」语义上，
> 避免有人只看提交标题就当成可上线。合入主干的判据不是「测试绿」，而是 §3 里那两条上线阻塞项。
> 下面写的是**口径与意图**；跑绿的是**用例与门禁**，不是「提现已修复」。
>
> ⚠ **本步不是「部分收敛」，而是「生产功能中断」**（定性已订正，取代先前的「部分收敛」说法）：
> 在 §1.26 落地之前，`settle()` **没有任何触发者**（无定时任务、无手动入口、无运维脚本），而它是
> `settled_total` 的**唯一写入者** ⇒ `seller_fund_account` 永远建不出行 / 恒为 0
> ⇒ `availableBalance` 恒 0 ⇒ `applyWithdrawal` 恒拒 ⇒ **生产上提现完全不可用**。
> 这与本批改动之前的实际状态一致（旧口径 `Σ status=SETTLED` 同样恒空，因为从来没有 `PENDING→SETTLED` 入口）。
>
> ⇒ **§1.26 已补触发入口（调度 + 运维脚本）**，所以「无人调用 `settle()`」这一条**不再成立**；
> 但**仍不许上线**：① 管理端端点还没做（草案见 `settle-trigger-draft.md`）；
> ② `frozen_amount` 预占（第二步）未做；③ `withdrawal_settlement` 核销（第三步）未做。
> 交付口径固定为 **「提现前置条件之一已通，提现整体仍不可上线」**。

**范围 —— 用户裁决改掉了 §1.24b 的两处写法**

1. **第一步只动两个累计字段**：`settled_total`（结算时加）、`paid_total`（打款时加）。
   **`frozen_amount` 整列不碰**：它的「加」在第二步 `applyWithdrawal` 预占时才出现，
   先做减法等于**减一个从未加过的字段**（余额凭空变小，且与结算单对不上）。
2. **自检的非空断言必须有真实数据**：第一步的用例**自己造结算单并真的走一遍 `settle()`** ——
   造数据 → 断言状态变 SETTLED → 断言 `settled_total` 增加 → 再跑三条自检。
   若数据靠「直接插一行 status=SETTLED」准备，自检能过而入口可能一行都不通；**空集上的自检永远是绿的**，
   且红的时候分不清是代码错还是没数据。

**一处口径订正（§1.24b 自相矛盾处，按「让自检成立」的那一侧改）**

§1.24b 一边写「打款侧 `settled_total -= amount`」，一边要求自检「每商家 `settled_total` == Σ 状态为已结算的结算单金额」。
两句话不可能同时成立：`settled_total` 是「已结算」这一事实的镜像，而打款**不会**让结算单离开 SETTLED
（打款事实根本不在这张表上）⇒ 打款再去减它，只会让两边永久对不上。故定死：

| 字段 | 口径 | 唯一写入者 |
|---|---|---|
| `settled_total` | **累计已结算**（只增） | `SettlementManager#settle`（PENDING→SETTLED 的唯一入口，**同事务**） |
| `paid_total` | **累计已打款**（只增） | 打款（管理端审核为「已打款」，**同事务**） |
| `frozen_amount` | 待审提现冻结 | 第二步 `applyWithdrawal` 预占；**第一步不写** |

- 可提现额 = `settled_total − frozen_amount − paid_total`。§1.23 那条条件更新式**同步补上 `paid_total` 项** ——
  少这一项就等于「打过的钱还能再提一次」，与它要修的缺陷同源。
- **打款只加 `paid_total`、不减 `settled_total`**：两个累计量各只增，才不会把同一笔钱扣两次。
- **额度判定只在申请侧，审核侧不判**（用户裁决，写死）：`settled_total - frozen_amount - paid_total >= amount`
  属于 `applyWithdrawal`（第二步做成**条件更新预占** `frozen_amount`）；`review` 只做「状态迁移 + 记账」。
  在审核侧再判一次会制造**假拒绝**：申请时通过、打款时因期间余额变化（例如另一笔先打了款）被拒，
  而钱其实已经按申请时的额度付出去了 —— 状态与资金两边都不对。
  ⇒ 第一步里**唯一的额度守门**仍是 `applyWithdrawal` 现有的「先读再比」，它**不是原子的**（这正是第二步要换掉的东西）。
- 自检取数**只用 `status = SETTLED`**（不用 `IN (SETTLED, PAID_OUT)`）：第一步没有任何路径把结算单迁出 SETTLED，
  所以这就是「已结算」的准确表达；而且它**更严** —— 若第一步真有行被错误迁成 PAID_OUT，
  `settled_total` 仍含那笔钱，自检当场红（取 IN 反而会把这类错误盖住）。
  - ⚠ **条件性提示（不是无条件待办）**：**只有当第三步仍决定把结算单行标成 PAID_OUT（历史语义）时**，
    才必须在同一 commit 内改宽两处取数 —— ① **数值一致** `settledAmountBySeller()` 的
    `WHERE status = ?` → `IN (SETTLED, PAID_OUT)`；② **覆盖面一致** `settledSellersInLedger()` 同样改宽
    （`accountSellerIds()` 读资金账户表，不用改）。
  - 但按上面的状态语义（`SETTLED` = 可提现、已打款看 `withdrawal`、分配表与行状态解耦），
    **预期第三步不写 `PAID_OUT`** ⇒ 两处取数**应保持 SETTLED-only、无需改动**。
    若将来要做「结算单列表区分已打款」，那是**读模型**（join `withdrawal`），不是改行状态。

**删掉的旧行为（是修缺陷，不是重构）**

`SettlementManager#markSellerSettlementsPaidOut` 及其唯一调用点**已删除**：它是「按商家**全量**把已结算置为已打款」，
提现 100 会把 1000 全部置成已打款、剩 900 看似再也提不出来（T-WITHDRAW-2）。
按下面的状态语义，它**既错又无必要**：`settlement.status` 根本不承载打款事实（见下节），
而「谁被打款了」由 `withdrawal` 记录。**宁可不写，也不写一个会被人当事实读的错标记。**

**结算单状态的语义（用户裁决，写死）**

- `settlement.status = 2 (SETTLED)` 的语义是 **「可提现」**，**不是「未打款」**；它**不承载打款事实**。
- **已打款的事实源是 `withdrawal` 表**（`status = PAID_OUT`），聚合值看 `seller_fund_account.paid_total`。
  **不要**从 `settlement` 表推断「这笔钱打没打过」。
- **对账等式见下节**（两条，**分母不同源**，不要合并成一条）。
- ⇒ **旧口径的根因重新表述**：不只是「Σ SETTLED 没扣待审提现」，而是**结算单不承载打款事实** ——
  无论怎么过滤 `settlement.status` 都算不出「还能提多少」。这才是必须另立 `seller_fund_account` 的真正理由。
- ⇒ **`withdrawal_settlement` 分配表与结算单行状态解耦**：它解决的是「按金额核销 + 拒绝释放」，
  不是「补上 `settlement.PAID_OUT`」。`settlement.status = 3` 保留为**历史语义**（本批起不再写入）。

**两条对账等式（各自独立，别互相推导）**

| 等式 | 账户侧 | 事实侧 | 谁提供事实 |
|---|---|---|---|
| 已结算 | `settled_total` | Σ `settlement.settlement_amount WHERE status = SETTLED` | `settlement` |
| **已打款** | `paid_total` | Σ `withdrawal.amount WHERE status = PAID_OUT` | **`withdrawal`（`settlement` 不提供）** |

两条都由 `SettlementFundAccountH2Test` **机械验证**：第一条在「自检三条」里；第二条是**新增的第四条判据** ——
它走**真实** `WithdrawalManager#review`（打款一笔、拒绝一笔），断言被拒的那笔不进 `paid_total`。
写成表格而不是只写一句话，是因为这两条最容易被人合并成一条（「余额 = Σ 结算单 − Σ 提现」），
而它们的**分母根本不同源**。

**`PAID_OUT` 的去留（用户裁决）：保留符号，封死写路径**

`SettlementStatusEnum.PAID_OUT` 是**死枚举值**：删它会让「历史上确实有过这个状态」失去唯一可引用的符号
（下一个人写迁移/读模型时会自己造魔数 `3`）；留着它又可能被再写回去。故新增门禁
`SettlementPaidOutRetiredGuardTest`：
- **禁止**：作为 `setStatus` / `update*` / `settle*` / `mark*` / `review*` 的实参（含 `.getCode()` 包装）；
- **禁止（变量绕过）**：**存进变量** —— 赋值右侧（`code = …`）或变量初始化（`int code = …`）。
  这是「不当实参、先存下来」的经典绕过；必须在**存的那一刻**红，否则 `int code = PAID_OUT.getCode();`
  之后怎么用静态就看不住了；
- **禁止**：任何 `UPDATE settlement` 的 SQL 把 `status` 写成**数字字面量**（必须走 `#{toStatus}` 参数）；
- **禁止**：静态导入它（会让「显式 scope」判定失效，等于把守卫弄瞎）；
- **允许**：读侧比较/switch —— 将来做「结算单列表区分已打款」的读模型时，可以用它判断历史行。

⚠ **退出条件（写死，别让它变成第 N 道判据）**：第三步动手时**先确认读模型到底需不需要 `PAID_OUT`**
（按本批语义答案是「不需要」—— 打款去 `withdrawal` 查）。若确认不需要，则**删枚举值 + 删守卫类**，
并同步清理三处引用：`Initialize.sql`（列注释与表注释）、`04-Data-Model` §7.4、本文档里提到 `status = 3` 的地方。
**不要**在这种情况下再加第五条判据 —— 死符号越守越像真符号，**删干净比守住便宜**；
只有当读模型确实需要它（历史数据必须有可引用的符号）时，才保留符号并让守卫继续存在。

**落地清单**

| 层 | 落点 |
|---|---|
| schema | `Initialize.sql`：新增 `seller_fund_account`（`uk_seller_id`）+ 同步补 DROP；逐表名清单用 `sync-doc-counts.mjs --list-tables` 取 |
| domain | `domain/model/finance/SellerFundAccount`（`available()` = 三项相减，口径只此一处）、`domain/repository/SellerFundAccountRepository`、`SettlementRepository#settleConditionally` |
| infrastructure | `SellerFundAccountPO/Mapper/RepositoryImpl`（两个增量写都**不带额度条件**；实现类两个写入各带 `@Transactional` —— 多语句本地写的事务归属落在仓储，REQUIRED 加入调用方事务）；`SettlementMapper#settleIfPending`（条件迁移：status + period_end + 金额 + 订单数）；`WithdrawalMapper#reviewIfPending`（条件迁移：待审核→终态，0 行=幂等） |
| app | `SettlementManager#settle`（条件迁移命中才同事务累加 `settled_total`）、`#payout`（只记账，**不判额度**）、`#availableBalance` 改读账户；`WithdrawalManager#review` = **条件迁移**（0 行=重复审核 ⇒ 幂等返回，不重复记账）+ 记账，两步同一事务。审核侧**不判额度**（额度属 `applyWithdrawal`）|
| 契约/数据模型 | `04-Data-Model` §7.4 增 `seller_fund_account` 口径 + `settlement.status` 语义（`SETTLED` = 可提现，已打款看 `withdrawal`）+ 订正「全量级联置为已打款」那段已不成立的旧文；`05-API-Specification` T22 `balance` 口径改写 + AD20/AD21 状态语义注记 + AD23 资金副作用注记 + 变更行 V5.25.0 |
| 门禁 | `FundTransactionGuardTest`：动词表加 `add` / `increase` / `settle` / `payout`；已知清单加本批三个入口 + body token；新增两条反例（无事务必须红）+ 一条「有事务必须放行」。**新增** `SettlementPaidOutRetiredGuardTest`：封死 `SettlementStatusEnum.PAID_OUT` 的**写路径** —— ① 作 `setStatus`/`update*`/`settle*`/`mark*`/`review*` 实参；② **存进变量（赋值右侧 / 变量初始化）**；③ `UPDATE settlement` 里把 status 写成字面量；④ 静态导入。**读侧放行**（将来读模型判历史行要用它）；五条合成反例 + 扫描面三形态；残余局限（容器/反射/拼接 SQL）已登记为「靠 review」|
| 测试 | `app/finance/SettlementFundAccountH2Test`（H2 真库，**三张表**：settlement / seller_fund_account / withdrawal）：① 造数据 → `settle()` → 断言 SETTLED + `settled_total` 增加 → 三条自检；② **第四条判据（对账等式）**：走真实 `WithdrawalManager#review` 打款一笔、拒绝一笔，断言 **Σ `withdrawal.amount WHERE status = PAID_OUT` == `paid_total`**（拒绝的不进），并另断言 **Σ `settlement_amount WHERE status = SETTLED` == `settled_total`**；③ `@BeforeAll` 调 `assertSchemaMatchesProduction()`，**读 `information_schema` 断言关键列与唯一键存在**（手写 DDL 漏列当场红）。对账全部 JDBC 直查，不借生产仓储自证 |
| 单测 | `WithdrawalManagerTest`（打款记账 / 重复审核不重复记账 / **审核侧不判额度** / 拒绝不记账）|

**为什么不是 upsert**：账户首建行走「先 UPDATE、0 行再 INSERT」，并发首建由 `uk_seller_id` 拦成
`DuplicateKeyException` 后改写为累加。`INSERT ... ON DUPLICATE KEY UPDATE` 在 H2 兼容模式下不是可依赖的契约，
而这段代码必须被 H2 自检用例原样跑通 —— **对账口径不能绑在「数据库支不支持那句语法」上**。

**资金门禁的动词表已补（本批做，用户点名）**

`FundTransactionGuardTest` 原写动词表只有 `insert/save/update/delete/deduct/credit/mark/create/cancel/void/bind/freeze/unfreeze`。
本批新增的写法是 `settleConditionally` / `addSettled` / `increaseSettledTotal` / `addPaid` / `increasePaidTotal` / `payout` ——
**旧表一个都不认**，于是「两个资金写 + 无事务」对门禁不可见（§5.5 的恒真空转形态：绿是因为没看见）。
现已补 `add|increase|settle|payout` 四个前缀，并同步三件事：

1. **已知清单**（判据 #16 形态②）加 `SettlementManager#settle`、`SellerFundAccountRepositoryImpl#addSettled`、
   `#addPaid` + 各自 body token —— 解析器在新包里漏切方法会直接红，而不是「0 违规」；
2. **反例**（形态③）加两条：「`addSettled` + `settleConditionally` 无事务必须红」「`addPaid` + `increasePaidTotal`
   无事务必须红」，外加一条「同一组写法**有**事务必须放行」（防止新动词一律判红、逼人写豁免）；
3. **补词表的代价是「人工 grep 推理」，不是门禁实跑**（本批 shell 不可用，门禁一次都没执行）。
   已逐个核对新动词在资金命名 receiver 上的**全部**命中点：
   - `pointsClient.addPoints` 只有两处 —— `OrderLifecycleManager#awardOrderRewardPoints` 与
     `NewUserRewardServiceAdapter#award`，**每个方法各 1 次**（不到 2 的阈值）；
     `pointsManager.addPoints`（`InternalPointsController` / `SignManager` / `TaskManager`）receiver 以 `Manager`
     结尾、不匹配资金命名，**不计**；
   - 其余命中全是本批新增方法，以及 `add`/`addAll` 这类列表操作（receiver 不在资金命名内，不计）。
   ⇒ 「不改变任何既有判定」目前是**推理结论**。另：本批的 `SettlementManager#settle` 因此进入
   「≥2 个资金写」集合，谁摘掉它的 `@Transactional`，门禁会点名 —— 但**只有跑过一次才算验证过**。

⚠ 仍未验证：**这四条只有跑过才算数**（本环境跑不了）；`WRITE_VERB_PREFIXES` 是新动词的唯一真源，
若将来出现「两个资金写但动词不在表里」的写法，本门禁仍是瞎的 —— 补动词时**必须同时**补一条合成反例。

**精简文件清单（放在这里的原因：`unverified-worktree.md` 是**未跟踪文件**，`git clean` 会连它一起删掉；
本文件是已跟踪的，清单跟着主文档走。完整说明仍以那份文件为准。）**

**新增（未跟踪 ⇒ `git clean` 会删，最易丢）**
- 主代码：`domain/model/finance/SellerFundAccount.java`、`domain/repository/SellerFundAccountRepository.java`、
  `infrastructure/persistence/finance/SellerFundAccount{PO,Mapper,RepositoryImpl}.java`
- 测试：`src/test/.../app/finance/SettlementFundAccountH2Test.java`
- 门禁：`architecture-tests/.../SettlementPaidOutRetiredGuardTest.java`
- 交接：`project-docs/collaboration/unverified-worktree.md`

**修改（已跟踪；`clean` 不动它们，但 `checkout -- .` / `reset --hard` 会回滚）**
- 主代码：`domain/model/finance/{Settlement,Withdrawal}.java`、`domain/enums/SettlementStatusEnum.java`、
  `domain/repository/{SettlementRepository,WithdrawalRepository}.java`、
  `infrastructure/persistence/finance/{SettlementMapper,SettlementRepositoryImpl,WithdrawalMapper,WithdrawalRepositoryImpl}.java`、
  `app/finance/{SettlementManager,WithdrawalManager}.java`、
  `adapter/admin/AdminFinanceController.java`、`adapter/response/SellerBalanceVO.java`
- 测试：`src/test/.../app/finance/WithdrawalManagerTest.java`
- 门禁：`architecture-tests/.../FundTransactionGuardTest.java`
- 其他：`database/Initialize.sql`、`project-docs/backend/04-Data-Model.md`、
  `project-docs/contract/05-API-Specification.md`、`project-docs/collaboration/open-items.md`（本文件）

**为什么两处都写**：`clean` 删未跟踪、`checkout/reset` 回滚已跟踪 —— 两种丢失途径各有一份线索才说得过去。

**仍未做（按顺序，属第二/三步）**

1. `frozen_amount` 的预占与释放 + `applyWithdrawal` 走条件更新（第二步）；单笔限额 `WITHDRAWAL_MAX_PER_REQUEST`。
   **在此之前超额提现仍可能** —— 本步只做**口径迁移**（把「还能提多少」交给权威账户），
   **既不是「T-WITHDRAW 已修复」，也还不是「提现可用」**（后者缺 `settle()` 触发入口，见第 3 条）；
2. `withdrawal_settlement` 分配表：**按金额核销**（哪笔提现核掉哪些结算单）+ 拒绝释放。
   ⚠ 按上面裁定的状态语义，它**不负责把结算单行标成已打款**（`settlement` 不承载打款事实，已打款看 `withdrawal`），
   所以本项的目的收窄为「按金额分配 + 拒绝时释放」，比 §1.24b 原描述少一半；
3. **`settle()` 目前没有触发者 ⇒ 生产提现功能中断（上线阻塞项）**：定时任务 / 手动入口 / 运维脚本**一个都没有**
   （§1.24b 的「触发方式后定」被本批的事实升级了 —— 它不是「以后选个触发方式」，而是「现在没有任何入口」）。
   后果链写死：`settle()` 是 `settled_total` 唯一写入者 → 账户恒 0 → `availableBalance` 恒 0 → 申请恒拒。
   ⇒ **本批上线必须同时交付触发入口**（管理端按钮 + 契约端点，或运维脚本 + 手册登记），否则不许上线。

**验证边界（本批最要紧的一条）**

**环境事实**：shell 不可用 —— `ops_bash` / `pwsh` 一律 **`0xC0000142`（STATUS_DLL_INIT_FAILED）**、**零输出**。
失败点已定位到**进程创建/加载器**，不在命令：① bash 的**内建**命令 `true` 也失败（不需任何外部二进制）；
② `pwsh` 里跑 `cmd /c echo ok` 同样失败；③ 两个互不相干的运行时（bash→msys DLL、pwsh→hostfxr/coreclr）同时坏；
④ 同一时刻文件工具全部正常（含读 `.git/logs/HEAD`、`E:\maven\...`、`C:\Users\...\.jdks`）。
⇒ 是「本会话起不了子进程」，不是「命令没装」「超时」「沙箱文件策略」（后两者有独立报错形态）。
该故障**在本次会话第一次调用（`git log`）时即存在**，与本批改动无关。
**且目前没有测试环境**。因此本批**没有**跑过 `mvn test` / `verify-gates.sh` / `sync-doc-counts.mjs` —— 一次都没有。

**处置（用户裁决，已订正）：进临时分支、标 `[UNVERIFIED]`，不进主干。**
原写法「不提交」被裁决纠正为 —— 原则是**不进主干**，不是「不进任何分支」：工作区已积累大量未提交改动，
**丢失风险 > 未经证实进入历史的代价**（后者在临时分支上可回退，前者不可恢复）。

**已完成（执行记录）**：`wip/t-withdraw-step1-unverified` 上 `[UNVERIFIED]` 提交 `fd569cb0`，含且仅含本批 27 个路径；
工作区其余未提交改动（来自并行会话）**未纳入、未触碰**（`git add` 只点路径 + `git commit -- <paths>`，
不用 `git add -A`）。沿用约定：**分支名 `wip/*` + commit 前缀 `[UNVERIFIED]`**，`git log --grep='\[UNVERIFIED\]'` 可一次捞全。
改动清单见 `collaboration/unverified-worktree.md`。

**验证结果（0 → 4，全部通过。本文件不落数字：数字以根文档与 surefire 报告为准）**

| 步 | 命令 | 结果 |
|---|---|---|
| 0 | `mvn -o -f Implementation/back-end/pom.xml -DskipTests install` | BUILD SUCCESS（含测试源码编译）。`mvn -v` = Maven 3.9.7 / JDK 21.0.10 ⇒ **「mvn 能否跑」自此有实测答案，不再是「不可判定」** |
| 1 | `... -pl architecture-tests test -Dtest='FundTransactionGuardTest,SettlementPaidOutRetiredGuardTest'` | 两个门禁类全绿（资金动词表 + 新守卫的合成反例与真实扫描） |
| 2 | `mvn -o ... clean test` | BUILD SUCCESS，零失败零错误，仅既有 `@Disabled` 一处跳过；**本批 H2 用例（含 `information_schema` 断言与真实 `review()` 路径）首跑即绿** |
| 3 | `node ... sync-doc-counts.mjs --write` | 就地定点回填（测试数 / 表数，共十余处），脚本自检「0 处残留差异、命中面不变」 |
| 4 | `bash ... verify-gates.sh full` | **全绿**：交接单门禁 / ID 不变量 / ia-api 单测 / 架构守卫 / 契约闭环 / 全量 `mvn test` / 根文档计数一致 |

**三条教训（保留，不因这次变绿就删）**

1. **静态审查抓不到编译错**：本批**第一次提交被 pre-commit 钩子拦下** —— 我把循环里被重新赋值的局部变量捕进
   lambda，报「必须实际上的最终变量」。这类错**只读源码看不出来**（我上一轮逐项审过 import/签名/事务/void）。
   ⇒ 「环境恢复前只做审查」只能**减少**失败面，**不能替代编译**。
2. **钩子先于提交跑 `fast`，可以故意用它当第一道编译检查**：它当场给出「编译 + 门禁」的结论，比事后跑 `full`
   更早暴露问题，且失败**不产生提交**。改了门禁的批次应当**先 commit 一次**探路（本批正是这样定位到编译错的）。
3. **`information_schema` 断言没踩坑**：H2 2.3.232 下首跑即绿 ⇒ 「失败信息带实有清单」的写法是可移植的。
   但登记仍然有用 —— 换 H2 版本 / 加表时必须重跑，**别假设「上次绿过」**。

**复跑命令（本次实际执行的就是这一段；顺序不可换）**

```bash
# 0) 全量编译（同时覆盖「门禁代码编译不过」这一类红）
#    architecture-tests 的运行前提是「所有模块的 target/classes 都存在」
#    （ArchitectureTest#importAll 缺任一目录会直接抛 IllegalStateException）
mvn -f Implementation/back-end/pom.xml -DskipTests install

# 1) 两个门禁类整类（含真实扫描 + 已知清单断言 + 新增守卫）
mvn -f Implementation/back-end/pom.xml -pl architecture-tests test \
  -Dtest='FundTransactionGuardTest,SettlementPaidOutRetiredGuardTest'

# 2) 全量测试（顺带产出 surefire 报告）
mvn -f Implementation/back-end/pom.xml clean test

# 3) 回填计数（报告必须来自第 2 步）
node Implementation/back-end/scripts/sync-doc-counts.mjs --write

# 4) 门禁验收（此时计数已是实测值）
bash Implementation/back-end/scripts/verify-gates.sh full
```

**为什么第 1 步不能省、且它红必须停**：本批**改了门禁代码**（动词表 + 已知清单 + 反例）**并新增了一个门禁类**
（`SettlementPaidOutRetiredGuardTest`）。若直接跑 `full`，任何红都**无法归因** ——
可能是业务代码真违规，也可能是门禁自己编译不过 / 反例写错 / 已知清单的 token 拼错 / 新守卫的 AST 判定写错。
`mvn test-compile` 只覆盖「编译」，第 1 步还覆盖「合成反例会不会咬」，两者缺一都不算门禁可用。
**第 1 步红 ⇒ 不执行 2/3/4**（此时跑全量只会产出一堆无法解释的红，还会把 `--write` 写进不可信的数字）。

**命令错 ≠ 代码红**：若第 1 步报 `No tests matching pattern …` / `No tests were executed`
（在 Surefire 里属于「按名字选测试没选中」），那是**我给的 `-Dtest=` 写错了**（类名/方法名拼写、模块没编译），
不是门禁红 —— 先修命令再判红。判据：真红会带**断言消息与文件行号**，选测试失败只会说「没匹配到」。

**`information_schema` 断言首次跑可能红（登记）**：本批 `SettlementFundAccountH2Test` 在 `@BeforeAll` 里读
`information_schema` 断言关键列与唯一键 —— H2 2.3.232 与 MySQL 在列名大小写、`constraint_type` 字面量、
「内联 UNIQUE 是否进 `table_constraints`」上并不完全一致。红时**先分清是「断言不兼容」还是「schema 真漂了」**：
失败信息里带**实有清单**，列名只是大小写不同 ⇒ 改断言写法；清单里真没有 ⇒ 同步 `Initialize.sql` 与本文件 DDL。
**两类都不许用「删断言」解决**（那等于把双源同步又退回「靠记得」）。

**为什么 `--write` 必须在全量测试之后**：`sync-doc-counts` 的测试数只认 surefire 实测，无报告时它「不写、不臆测」
（只告警）⇒ 把 `--write` 提前等于什么都不回填，计数仍是过期值。
另外**回填前必须保证报告来自本次构建**（`clean` 或确认无残留）：残留报告让总数偏大，
`--write` 会把脏数据写进文档 —— 若根目录 `clean` 与并行会话冲突，宁可等，也别用上一轮的报告回填。

**只接受这一条主路径**：先 `full` 再 `--write` 再 `full` 的变体**不再保留** —— 计数回填后「段 3 红是预期」这个前提
已经消失，留着只会让两套流程漂移。判据统一为：**0→4 顺跑全绿**才算通过。
两处必须由 `--write` 回填（不得手改）：根文档的表数与测试数。

`fast` 里那道计数闸会 `[skip]`，所以「`fast` 绿」并不覆盖这一项（本批第 4 步的 `full` 才覆盖它）。


### 1.26 结算触发入口（2026-10-19）：把「提现完全不可用」的断链接上

> 状态：**调度 + 运维脚本已落码并跑绿**；**管理端端点仍是草案**（`collaboration/settle-trigger-draft.md`）。
> 这一节存在的理由：`settle()` 是 `settled_total` 的唯一写入者，它的**调用者**不是实现细节，而是上线前置。

**交付了什么**

| 件 | 落点 | 说明 |
|---|---|---|
| 批处理外壳 | `app/finance/SettlementSweeper#sweep(LocalDate, int)` | 取候选 → 逐条经 `SettlementManager#settle` 结算；`limit <= 0` 直接返回 0 |
| 调度适配器 | `infrastructure/scheduler/SettlementSettleJob`（`@XxlJob("settlePendingSettlements")`） | 只算「今天」、取 `trade.settlement.sweep.limit`（默认 200）、写日志 |
| 候选查询 | `SettlementMapper#selectSettleableIds` + 端口 `findSettleableIds` | 与 `settleIfPending` **同条件**；只缩小范围，判定仍在条件更新 |
| 运维脚本 | `scripts/settle-pending.sh` | 默认只读报告候选条数；`--trigger` 才 POST 到 `XXL_JOB_TRIGGER_URL`（不猜地址） |
| 契约草案 | `collaboration/settle-trigger-draft.md` | `POST /api/admin/finance/settlements/settle` 的完整草案（未落契约） |

**为什么循环在 `SettlementSweeper` 而不是 `SettlementManager`（踩点记录）**

`settle()` 带 `@Transactional`，靠 Spring 代理生效。若在 Manager 内部写 `settlePending()` 循环调用
`this.settle(...)`，那是**自调用** ⇒ 代理被绕过 ⇒ **事务不生效**（单测仍全绿、线上无事务）。
所以循环必须落在**另一个 Bean**：`SettlementSettleJob`（调度）与未来的端点都调 `SettlementSweeper`，
`Sweeper` 再逐条经代理进 `Manager` ⇒ 每条一个事务、单条失败只回滚本条。

> 顺带记一条分层教训：这一层最初写在 `infrastructure/scheduler` 里，包级 `sweep` 立刻**跨包不可见**（编译直接红）。
> H2 用例逼着把它挪到 app 层 ——「批处理外壳」本来就是 use-case，**不该住在 scheduler 里**。

**验收（已跑绿，用例名可复查）**

- `SettlementFundAccountH2Test#shouldSweepSettleableSettlementsOnly` —— 真库：一次扫描结算掉到期的、**不动未到期的**、重扫不再动账；
- `SettlementSweeperTest` —— 条件未命中计入 `skipped`（不算失败）/ 单条抛异常计入 `failed` 且不中断整批 / 无候选不空转 / `limit <= 0` 不查库。

**2026-10-19 三件收紧（用户点名，已落码）**

1. **返回值改四段计数**：`SweepResult{scanned, settled, skipped, failed}` + `healthy()`。
   只返回一个数字时，「今天没有可结算的」与「有单但全失败」看起来一模一样，运维无法判断该不该报警。
2. **时钟只取一次**：`SettlementSweeper#sweep(today, now, limit)` 把同一对时钟同时用于候选查询与逐条条件更新
   （`SettlementManager#settleAt(id, today, now)` 接收而不再自查 `now()`）；
   跨零点扫描不会一半按 D 判、一半按 D+1 判。`settle(id)` 保留给单条调用，内部只取一次 `now` 再派生 `today`。
3. **链路写清**：脚本触发的是 **XXL-Job handler**，**不是**管理端端点（端点仍是草案）。链路
   `settle-pending.sh → /jobinfo/trigger → XXL-Job → settlePendingSettlements → Sweeper → settle`；
   **脚本可用 = handler 已注册 + `XXL_JOB_TRIGGER_URL` 配好（管理端开了登录还要 `XXL_JOB_COOKIE`）**。
   「curl 返回成功」只代表触发被受理，**不等于结算成功**。
   另：`settlePendingSettlements` 日志已打**耗时**，供「limit 与任务超时」成对调参（见 §1.28 登记）。

**仍未做（上线前必须补）**

1. **管理端端点**（草案 `AD-draft.1`）：做之前先过契约（契约表行 + `docOnly` 计数 + 权限矩阵），
   并确认权限位仍是 `finance:admin`（与现有 `AdminFinanceController` 口径一致）。
2. **调度接线**：`@XxlJob` handler 需要在 XXL-Job 管理端**建任务并配 cron**；本批只交代码，
   **没有在生产调度器上注册过** ——「有 handler」≠「会定时跑」，这条必须进运维手册。
3. **首次跑的三段核对**：脚本只读报告 → 触发 → 回查日志「实际结算 N 条」与库内 `status` 分布；
   三段对不上就停，别当成功。


### 1.27 第二步开工口径（2026-10-19 锁定，动代码前先认这一节）

> 这三条不是建议，是**实现约束**。要改先改这一节，别只在代码里改完再回来补文档。

**① 三个字段的同事务边界**

| 字段 | 唯一写入点 | 与谁同事务 |
|---|---|---|
| `settled_total` | `SettlementManager#settle`（含 `SettlementSweeper`） | 与 `settlement.status PENDING→SETTLED` 同事务（已实现） |
| `frozen_amount`（加） | `applyWithdrawal` 预占 | 与 `withdrawal` 插入 PENDING 同事务 |
| `frozen_amount`（减） | review 终态（PAID_OUT / REJECTED） | 与 `withdrawal` 条件迁移同事务；PAID_OUT 时再加 `paid_total` |
| `paid_total` | 打款（`payout`，由 review 调） | 同上 |

**顺序不是风格问题，是被幂等语义决定的**：

- `applyWithdrawal`：**先预占**（0 行 = 额度不足 ⇒ 拒，此时 `withdrawal` 还没插）**再插** `withdrawal`（插入不会 0 行）。
- `review`：**先迁移状态**（0 行 = 重复审核 ⇒ **幂等返回，不许动账户**）**再动账户**；账户侧 0 行（`frozen` 不足）⇒
  **必须抛 `BizException` 整笔回滚** —— 此时状态已变，账不平不能静默。
  ⚠ 写反的顺序（先扣 `frozen` 再迁移）会让**重复审核先扣一次再看 0 行返回**，凭空吃钱。

**② `frozen_amount` 不许减到负**

- 所有减少一律 `UPDATE seller_fund_account SET frozen_amount = frozen_amount - #{amount}
  WHERE seller_id = ? AND frozen_amount >= #{amount}`；**0 行 = 抛异常**（不是 `return`），让整笔（含状态迁移）回滚。
- **不加 DDL `CHECK` 约束**：仓库现有表都没用 CHECK，且 H2/MySQL 的表达不一致；靠**条件更新 + 对账等式**兜住。
- 新增第三条不变量（第二步落地即生效，必须进 H2 用例）：`frozen_amount == Σ withdrawal.amount WHERE status = PENDING`。
  与已有两条并列、互不推导：`settled_total ↔ Σ SETTLED 结算单`、`paid_total ↔ Σ PAID_OUT 提现`、`frozen_amount ↔ Σ PENDING 提现`。

**③ 限额：会被别的事务改掉的进 WHERE，只依赖请求的留方法体**

- **可用额度**（`settled_total - frozen_amount - paid_total >= amount`）：**并入条件更新**。
  理由：读-比较-写不是原子的，两笔并发各自读到同一可用额会**同时通过** —— 这正是第二步要堵的洞，不是优化项。
- **单笔上限** `WITHDRAWAL_MAX_PER_REQUEST`：**独立判断**（`amount <= max`）。只依赖请求本身、与账户状态无关、无竞态；
  放在事务内预占之前，早失败早返回。
- **金额 > 0**：保留独立判断（同上）。
- 一句话判据：**「这个判断会不会被别的事务改掉」→ 会，就进 WHERE；不会，就留方法体。**

**做完第二步仍不能上线**（写死，汇报里不许含糊）：① 管理端端点未做、handler 未在 XXL-Job 注册；
② 分配/核销（第三步）未做；③ 真实打款渠道没有。⇒ 交付口径固定为 **「提现仍不可上线」**。


### 1.28 第二步落地（2026-10-19）：条件预占 `frozen_amount` + 单笔上限 + 审核释放

> 状态：**已落码、已跑绿（H2 真库 + 单测 + 全量 0→4）**。本节只记「做了什么、怎么验、还差什么」，
> 口径依据是 §1.27（三件开工确认），不是本节重新发明。

**落地对照 §1.27 的三条**

| 口径 | 落地 |
|---|---|
| 三字段同事务 + 顺序由幂等决定 | `applyWithdrawal`：`freeze`（条件更新，0 行=额度不足⇒拒）→ 落 PENDING 单；`review`：`reviewIfPending`（0 行=重复⇒幂等返回、**不碰账户**）→ `unfreeze` → PAID_OUT 时 `payout` |
| `frozen` 不许减到负 | `SellerFundAccountMapper#unfreeze` 带 `WHERE frozen_amount >= amount`；0 行 ⇒ `releaseFrozen` 抛 `BUSINESS_EXECUTION_EXCEPTION` 整笔回滚（**不是 return**） |
| 额度进 WHERE、上限留方法体 | `#freeze` 带 `WHERE settled_total - frozen_amount - paid_total >= amount`（原子）；单笔上限 `trade.withdrawal.max-per-request`（默认 5000000 分）与 `amount > 0` 在方法体里独立判 |

**三条对账等式（互不推导，全部有真库用例）**

1. `settled_total == Σ settlement.settlement_amount WHERE status = SETTLED`（§1.25）
2. `paid_total == Σ withdrawal.amount WHERE status = PAID_OUT`（§1.25）
3. **新增** `frozen_amount == Σ withdrawal.amount WHERE status = PENDING_REVIEW`（本节；
   `SettlementFundAccountH2Test#shouldReconcileFrozenAgainstPendingWithdrawals`）

**验收用例（H2 真库，`SettlementFundAccountH2Test`）**

- `shouldFreezeOnApply` —— 申请即预占，可用额 = `settled − frozen − paid`
- `shouldRejectWhenQuotaInsufficient` —— 预占 0 行 ⇒ `BALANCE_INSUFFICIENT`，不落库、不占额度
- `shouldRejectWhenOverSingleLimit` —— 超上限 ⇒ `PARAM_ERROR`，不占额度、不落库
- `shouldReleaseAndRecordPaidOnPaidOut` —— 打款后 frozen 归零、`paid_total` 增加
- `shouldReleaseOnRejectAndKeepQuota` —— 拒绝也释放，且**额度可再用**（拒绝后能再提一次）
- `shouldBeIdempotentOnReplayedReview` —— 重复审核不再动账（顺序写反会在这里暴露）
- 单测 `WithdrawalManagerTest` 用 `InOrder` 把「先预占后落库」「先迁移后释放后记账」两条顺序**钉死**

**踩点（值得记，且它证明了守门在工作）**

既有对账用例原本用 `savePendingWithdrawal` **直接插 PENDING 单**（绕过 `applyWithdrawal`），
第二步之后 `review` 释放预占拿到 0 行 ⇒ 直接抛「账不平」。这不是用例坏了，而是**守门抓到了造数据绕过入口**：
绕过申请路径的 PENDING 单，本来就没有对应的冻结额。⇒ 用例改为走真实 `applyWithdrawal` 造数据；
helper `savePendingWithdrawal` 已删（留着迟早又被用来绕）。
**规则写死：造数据不许绕过被验的入口。**

> 另一条本文档只留指针：本步踩到的**重载陷阱**（同名重载让门禁只看到委托版）已升格为判据，
> 写在 `09-constraints.md` §七「重载不作语义分叉」——判据只留一处，别在台账里复制第二份。

**登记两条（用户点名）**

1. **`limit` 与 XXL-Job 任务超时匹配**：`settlePendingSettlements` 已在日志里打**耗时**；
   limit 调大必须同时看超时配置 —— 跑过超时时间时调度器判超时，而 JVM 仍在跑（可能并发执行同一批，
   虽然有条件更新兜底不会重复记账，但会让观测与告警失真）。操作手册里「调 limit」与「调超时」必须成对出现。
2. **第三步退出条件**：动手时先确认读模型**到底需不需要** `PAID_OUT`；确认不需要 ⇒
   **删枚举值 + 删 `SettlementPaidOutRetiredGuardTest`**，并清理三处引用（`Initialize.sql` 注释、
   `04-Data-Model` §7.4、本文档提到 `status = 3` 的地方）。**不要**再加第五道判据
   （详见 §1.25「退出条件」与守卫类 javadoc）。

**仍不可上线（两件是「且」，不是「或」）**：
① **触发入口完整**（管理端端点 + handler 在 XXL-Job 注册 + 首次三段核对）；
② **真实打款渠道**。
做完了 ① **只说明触发入口完整，不等于功能可用**；**功能可用 = ① 且 ②**。任何人汇报「提现已可用」前，先把这两条逐条打勾。

> ⚠ **E3 的前提（用户点名登记）**：`frozen_amount == Σ PENDING_REVIEW` 假设「**只有「待审核」占用冻结**」。
> 新增任何占用 `frozen_amount` 的状态（例如将来的「打款中 / 银行处理中」）时，
> **必须同步扩展 E3 与释放路径**；否则等式失效的方式是「多算 / 少算」而**不报错**
> （比抛异常更难发现，只能靠对账任务兜住）。


### 1.29 第三步语义决定（2026-10-19，**先定语义、不写代码**）

> 用户点名先定两件事：① 部分提现后结算单状态变不变；② 读模型靠分配表聚合还是 `withdrawn_amount` 列。
> 这两条决定**表结构**与**等式**，定了再动 `Initialize.sql` —— 否则等于「改表即改等式」，返工最贵。

**决定 1：部分提现【不】改变结算单状态（`SETTLED` 始终表示「可提现」）**

- 状态只承载**阶段**（待结算 → 可提现），不承载**余额**。一旦让状态表达「提了一部分」，它就变成
  `Σ 分配` 的派生值 ⇒ 两个真源 ⇒ 必然漂移（`markSellerSettlementsPaidOut` 正是这么坏掉的）。
- 故**不新增** `PARTIALLY_WITHDRAWN`，也**不复活** `PAID_OUT`（守卫继续封其写路径）。
- 「这张单提了多少 / 还能提多少」是**金额信息**，由 `withdrawn_amount` 列 + 分配明细表达。

**决定 2：分配表是真源，`settlement.withdrawn_amount` 是条件更新的守门列；读模型读列**

- 表：`withdrawal_settlement(id, withdrawal_id, settlement_id, amount, create_time)`，
  `UNIQUE uk_withdrawal_settlement (withdrawal_id, settlement_id)`（防同对重复分配）、`KEY idx_settlement_id`；
  **不加外键** —— 与 `settlement` / `withdrawal` 同族（这两张财务表本就没有 FK，全库 FK 集中在 RBAC 等）。
- 列：`settlement.withdrawn_amount INT NOT NULL DEFAULT 0`；**只能**由分配仓储的分配/释放方法写
  （同事务、带条件）；其它位置不许 `setWithdrawnAmount`（第三步加静态守卫，同 `settled_total` 的写法）。
- **为什么不「只靠分配表聚合」**：并发下「读 SUM → 比较 → 插入」不是原子的 ——
  要么 `SELECT ... FOR UPDATE` 锁结算单行，要么加列。本仓库既有 idiom 是**条件更新 + 影响行数**
  （`deductStock` / `settleIfPending` / `freeze`），`FOR UPDATE` 无先例 ⇒ 选列。
- **为什么这不算「两个真源」**：列是**可重建的投影**，等式的存在就是证明它没漂；
  与 `settled_total ↔ Σ SETTLED 结算单` 同构（聚合列 + 明细 + 等式，本仓库已验证的写法）。

**决定 3：核销时点 = 申请时（与 `freeze` 同一事务）；FIFO；一笔可跨多单**

- 申请时就要钉死「这笔钱从哪几张单出」，否则**拒绝释放**无处落脚；也让单据维度的占用可见。
- 顺序：`period_end ASC, id ASC` 逐张吃满（账期先到先核销，可解释、可复算；不引入「用户指定单据」）。
- 两种部分核销形态都要支持：**一张单被多笔提现部分占用**、**一笔提现跨多张单**（多行分配）。
- 释放（拒绝）**删分配行** + `withdrawn_amount -= amount`（`WHERE withdrawn_amount >= amount`；0 行 = 账不平 ⇒ 抛），同事务。
  **不保留 released 标记**：未核销即无分配是硬语义；留标记会让每条等式都多一个过滤条件，多一处忘过滤的机会。
  审计线索靠 `withdrawal.admin_remark` + 释放时的日志，本步不新增审计表。
- 打款（PAID_OUT）**保留分配行**（钱确实从这些单出），`withdrawn_amount` 不再变。

**等式清单（第三步后共六条；前三条已上，后三条本步落地）**

| # | 等式 | 抓什么 |
|---|---|---|
| E1 | `settled_total == Σ settlement.settlement_amount WHERE status = SETTLED` | 结算 ↔ 账户 |
| E2 | `paid_total == Σ withdrawal.amount WHERE status = PAID_OUT` | 打款 ↔ 账户 |
| E3 | `frozen_amount == Σ withdrawal.amount WHERE status = PENDING_REVIEW` | 冻结 ↔ 待审（⚠ 前提见 §1.28 末尾） |
| **E4** | 逐单 `settlement.withdrawn_amount == Σ withdrawal_settlement.amount`；且 `0 ≤ withdrawn_amount ≤ settlement_amount`；且 `status != SETTLED ⇒ withdrawn_amount == 0` | 列 ↔ 明细 |
| **E5** | 每笔 `Σ allocation.amount == withdrawal.amount`（PENDING_REVIEW / PAID_OUT）；`REJECTED ⇒ Σ == 0` | 提现 ↔ 明细 |
| **E6** | `Σ allocation.amount WHERE withdrawal.status = PAID_OUT == paid_total` | **两张表钉死**（E2+E5 可推出，但独立断言能同时抓两边漂移） |

**影响面（第三步开工清单，先登记免得漏）**

1. `Initialize.sql`：+`withdrawal_settlement` 表、+`settlement.withdrawn_amount` 列（表数 +1；计数由 `--list-tables`/`--write` 回填，不许手改）；
2. H2 用例的**启动期 schema 断言**要加新表与新列，否则双源自检会漏；
3. 守卫：`withdrawn_amount` 只许分配仓储写；
4. 契约：提现详情 / 结算单列表显示「核销到哪些单」属**读模型**，先出草案（未落契约前不做端点）；
5. E1~E3 已有用例按「等式」组织，追加 E4~E6 即可（先红后绿）。

**本节不是实现**：只有决定与影响面，代码 / DDL / 用例一行未写。


### 1.30 第三步落地（2026-10-19）：按金额核销 + 拒绝释放 + `withdrawn_amount` 守门

> 依据 §1.29 的三条决定；本节只记「怎么落的、验的什么、踩了什么」，不再重复口径。

**四条开工确认（用户点名）的落实**

| 确认 | 落实 |
|---|---|
| freeze + 分配 + 插单同一事务，分配行在 freeze 之后 | 实际顺序 **freeze → 插单（拿 id）→ 分配**：`withdrawal.id` 是 `AUTO_INCREMENT`，分配行要引用它，所以插单必须早于分配。**实质不变式仍成立**：分配是**最后一次写**，且必在 freeze 成功之后 ⇒ 冻结失败时根本不会产生分配行。三步同在 `applyWithdrawal` 一个事务里 |
| E4 的「非 SETTLED ⇒ withdrawn == 0」要写注释 | 用例注释写明：**当前恒真只是因为只有 SETTLED 单会被核销；将来若加作废/撤销状态，必须归零或扩展该断言** |
| 删分配行的审计取舍 | 登记：**「曾经想从哪几张单出钱」不可追溯**，换来的是「未核销即无分配」的简单等式（E4/E5 都不需要 status 过滤）；审计线索靠 `admin_remark` + 释放日志，不加审计表 |
| 第三步 DDL 与第一步 DDL 同批部署 | 登记：**不单独上线** —— 分配行依赖 `applyWithdrawal` 成功，而它依赖第一步的三张表；拆开部署会出现「能核销但没有结算单/账户」的空窗 |

**守卫（按 SQL 归属，不按方法名）**：新增 `WithdrawnAmountWriteGuardTest`
- 规则：① 只能 `= withdrawn_amount ± #{}`（禁常数/直接赋值）；② 加必须带 `+ #{} <= settlement_amount`、减必须带 `>= #{}`；③ **全仓减点唯一**（释放路径一处）
- 范围：只看「含该列且含 SQL 动词」的**字符串字面量**，且把 `+` 拼接的语句**还原成整句**再判；`SELECT` 不设限；报错文案不算
- 局限登记：XML/存储过程/`JdbcTemplate` 手写语句不在覆盖内

**两条踩点（都值得记，都是「守卫第一版必然误报」）**

1. **一级缓存**：H2 用例里每个仓储各持一个 `SqlSession`，MyBatis 一级缓存按 session 生效 ⇒
   A 仓储写、B 仓储读会拿到**上一轮**的核销候选（表现为「可核销额明明为 0 却还进计划」）。
   已在测试装配里把 `LocalCacheScope` 设为 `STATEMENT`（生产每事务一个 session，等价语义）。
2. **守卫两轮误报**：第一轮把 `"a " + "b "` 的**片段**当独立语句（`AND withdrawn_amount < ...` 被判成非 UPDATE）；
   第二轮把**报错文案**里的列名当语句。修法分别是「还原拼接链」与「要求 SQL 动词」——
   **这两条不是实现细节，是这类文本守卫的通用坑**。

**验收（真库 `SettlementFundAccountH2Test`，五条新用例）**

- `shouldAllocateAcrossSettlementsInFifoOrder` —— 一笔跨两单，旧单先吃满
- `shouldReleaseAllocationOnReject` —— 拒绝：删明细 + 回退 `withdrawn_amount` + 解冻，三者一致
- `shouldKeepAllocationOnPayout` —— 打款保留明细（E6 的依据）
- `shouldAccumulatePartialAllocationsOnOneSettlement` —— 一张单被多笔部分占用，吃满到结算额为止
- `shouldReconcileAllocationEquations` —— **E4**（逐单 列↔明细、不超结算额、非 SETTLED 为 0）、
  **E5**（每笔 明细合计 == 提现金额；被拒 == 0）、**E6**（已打款明细合计 == `paid_total`，按商家收口）
- 单测：`SettlementAllocatorTest`（纯函数五条边界）、`WithdrawalManagerTest`（用 `InOrder` 钉死
  「冻结→落单→核销」与「迁移→退核销→解冻」两条顺序）

**仍不可上线**：管理端端点 / XXL-Job 注册 / 首次三段核对 + 真实打款渠道（分配核销已不再是缺口）。

### 1.31 触发入口的端点（2026-10-19）：AD24 `POST /api/admin/finance/settlements/settle`

> 这支端点的职责只有一件：把「到期 PENDING 结算单」结算成 `SETTLED`（= 可提现）。
> **不碰提现、不碰 `frozen_amount`、不碰 `paid_total`、不写 `settlement.status = PAID_OUT`**。

**三个设计点（用户点名，已落）**

| 点 | 落法 |
|---|---|
| 响应含失败明细 | `SettlementSweepVO.failures`：前 N 条（服务端 `MAX_FAILURES` 上限）`{settlementId, reason}`；**截断的是条数不是原因**，全量在应用日志。只给 `failed` 计数时运维还得翻日志 |
| 支持可选 `periodEnd` | `SettlementSweepReq.periodEnd` 透传到 SQL（`AND (#{periodEnd,jdbcType=DATE} IS NULL OR period_end = …)`），**不是内存过滤**；补跑某账期时范围是显式输入 |
| `scanned` 语义写清 | 契约注记 + VO javadoc 双处写明：**本次候选数，不跨次相加**（调度与手动触发会扫到同一批），不是「待结算队列长度」 |

**同批的四件开工前确认**

1. **生产 `localCacheScope` = SESSION**（取证：全仓无 `local-cache-scope` 配置、无 `LocalCacheScope` 代码 ⇒ MyBatis 默认）。
   我先前在 H2 用例里改 STATEMENT 是**掩盖问题**：真正错的是「每个仓储各持一个长活 SqlSession」这种假环境。
   已改为**共用一个 session**（等价于生产「一事务一 session」，同 session 内写会清缓存），并把 STATEMENT 去掉 ——
   **测试与生产的缓存级别现在一致**。
2. **守卫「唯一减点」的红信息**改成可执行指引：红时先辨「新业务分支 vs 又写了一遍释放」，
   新分支要先补等式、再按 SQL 文本加白名单写理由、两条路径都必须带边界；**只把 1 改成 2 等于关掉探测点**。
3. 端点三个设计点（上表）。
4. **上线阻塞项是「且」**：① 触发入口完整（端点 + XXL-Job 注册 + 首次三段核对）；
   ② 真实打款渠道。**做完 ① 只说明入口完整，不等于功能可用；功能可用 = ① 且 ②**。

**验收**：`SettlementSweeperTest` 新增「periodEnd 透传」与「失败明细带 id 与原因」两条；
`SettlementFundAccountH2Test` 新增「按账期补跑只动该账期」；契约 AD24 行 + 注记 + 变更记录 V5.26.0；
`--write` 回填（端点数 +1、测试数更新）；`verify-gates.sh full` 七段全绿。

### 1.32 五件确认（2026-10-19）：打款闸 / 事务边界 / 白名单结构 / N 与 periodEnd / 脚本归属

**① `review(PAID_OUT)` 在无渠道时的行为 —— 加了限制**
`trade.withdrawal.payout-enabled` **默认 false**：标已打款直接抛 `BUSINESS_EXECUTION_EXCEPTION`（文案「打款渠道未接入…」），
**不动 frozen / paid**。理由：该动作只改本地状态 + 记 `paid_total`、**不调用任何出款接口** —— 是「已记账未付款」，
比「功能不可用」危险得多（商家投诉时无从自证）。人工银行转账场景显式置 true，注释写明
「系统认为已付款来自**人工确认**，不是渠道回执」。契约 AD23 注记同步一句。

**② 测试「共用 session」有没有事务边界 —— 原来没有，已补；但回滚仍是未覆盖**
- 实测：`openSession(true)` ⇒ `autocommit=true` ⇒ **每条语句自成一个事务**，失败不会回滚（生产会）。
- 已改：`openSession(false)` + `inTx()` 包装（成功 `commit`、异常 `rollback`），每个 manager 调用成为一个显式事务。
- **但实测 `SqlSession.rollback()` 在本 harness 不生效**（手搓 MyBatis session、无 Spring 事务管理器）。
⇒ 「审核中途失败整笔回滚」列为**未覆盖项**（不做假绿）：要真验需 `@SpringBootTest` + `DataSourceTransactionManager`
（或 Testcontainers）。相关 javadoc 已写明，避免后来人把它当已覆盖。

**③ 守卫白名单的数据结构 —— 当前没有白名单，但结构定死**
释放路径本来就唯一，断言是 `decrement == 1`。按用户要求把「将来若要加」的结构写成
`Map<String /*SQL*/, String /*为什么必须有第二条*/>`，断言改成 `1 + allowlist.size()`；
**明确否掉 `Set`** —— 它是开关，加一条不留理由。

**④ `failures` 的 N 与 `periodEnd` 覆盖**
- `N = 20`（常量 `MAX_FAILURES`，不做配置）：够运维定位，又不至于把响应变成日志倾倒口；**截断条数不截断原因**。
- `periodEnd` 两种取值都有覆盖：**null** 由 3 参 `sweep(...)` 的 H2 用例走到 SQL 的 `IS NULL` 分支；
  **有值** 由 `shouldSweepOnlyGivenPeriodEnd`（真库）+ `shouldPassPeriodEndToQuery`（单测透传）覆盖。

**⑤ 工作区脚本改动 —— 不是本批，但影响绿的适用范围**
`verify-gates.sh`（+45/−6：重排成 report-free / test / count 三段）与 `sync-doc-counts.mjs`
（+180：`CI_COMPLETE_FLOOR`、半量构建判定）**是并行会话的未提交改动**。
⇒ 本批 `full` 的绿是在**带这些改动的工作区**上取得的，只对「当前树」成立；
**若这两处被回退或再改，必须重跑 `full`**（不能引用本批结论）。

### 1.33 六件确认（2026-10-19）：闸前置 / 空集绿 / 自调用守卫 / periodEnd 等值 / 脚本独立验证 / reason 分类

**① 打款闸必须在 `reviewIfPending` 之前 —— 已修（这是本批唯一的硬缺陷）**
原来先迁移状态、后判闸，靠「事务回滚」兜底；而**回滚能否生效取决于代理**（自调用就没了），
且本仓 harness **验不了回滚**（§1.32 ②）⇒ 两个不确定叠加。现在闸在**迁移之前**：
不可打款时**根本不改状态**，不依赖任何回滚能力。单测用 `verify(never()).reviewIfPending(...)` 钉住这个顺序。

**② 登记：`payout-enabled=false` 期间 E2 是「空集绿」，不构成账平证据**
该配置下**不可能**产生 `status = PAID_OUT` 的提现行 ⇒ `paid_total == Σ PAID_OUT` 恒为 `0 == 0`。
所以「E2 绿」只说明「没有已打款行」，**不说明记账正确**。要等真实渠道接入、有真实 PAID_OUT 数据后，
E2 才有证据力；在那之前**不得**引用 E2 证明账平。

**③ 新增守卫：事务方法不得被同类非事务方法自调用**
`TransactionSelfInvocationGuardTest`（扫 `app/` + `infrastructure/persistence/`）：
同 Bean 内 `foo()`/`this.foo()` 调到 `@Transactional` 方法、而调用方自己不带事务 ⇒ 红。
调用方也带 `@Transactional` 时放行（外层 REQUIRED 已覆盖）；跨 Bean 调用放行（那才是走代理）。
**扫出两处历史债务**（本批之前就有，不在资金域）：
`CouponClaimManager#smartClaim->claim`、`ShopManager#updateMyDefaultShop->updateMyShop` ——
登记在守卫的 `KNOWN_DEBT`（`Map<键, 修法>`）里，并配一条**自我清算**断言：键修好后必须删条目，否则守卫自己红。

**④ `periodEnd` 是「等于」——有意为之，已写进契约**
补跑语义下「等于某账期」才明确；区间扫描应由调用方分批（列表接口已有）。
它与 `today` 独立：仍须 `period_end <= 今天`。契约 AD24 注记已写明「等值匹配，不是区间」。

**⑤ 并行会话的脚本改动需要**独立验证记录**，本批的绿不覆盖它们**
`verify-gates.sh` / `sync-doc-counts.mjs` 的改动**不是本批**：本批 `full` 只是**把它们跑过**，
不等于**审过/验过**它们的逻辑（半量构建判定、三段式重排）。⇒ 这两个文件在作者自证前
（自行跑 `full` + `sync-doc-counts --check` 并留下记录）保持「**未验证的工作区改动**」状态；
**不得**把本批的绿当作对它们的验证，也不得在其上再叠结论。

**⑥ `failures.reason` 只放分类，不放原始异常消息**
`classify()`：`BizException` → `BIZ:<错误码>`；`DataAccessException` → `DATA_ACCESS`；其它 → `UNEXPECTED`。
理由：响应会进前端/工单/审计，异常消息常带 SQL 片段、主键甚至账号；细节留在 `log.warn` 的堆栈里。

### 1.34 三件处置 + 合入裁决（2026-10-19）

**① KNOWN_DEBT 的业务影响评估 + deadline（deadline 已机器化，不是注释）**

| 债务 | 写放大 | 影响 | deadline |
|---|---|---|---|
| `CouponClaimManager#smartClaim->claim` | **两张表**：`coupon` 自增发行量（条件更新）+ `user_coupon` 插入 | **高**：自调用时两者不在同一事务 ⇒ 可能「券数已增、领取记录没落」（或反过来），属可观测的券账不一致 | **2026-11-10** |
| `ShopManager#updateMyDefaultShop->updateMyShop` | **单表**：`shopRepository.updateProfile` | **低**：单表单写，跨表原子性无从谈起 —— 这里的 `@Transactional` 是**装饰**，修法是删注解/下沉私有方法 | **2026-12-31** |

deadline 的落实方式：`KNOWN_DEBT` 的值是 `Debt(reason, deadline)`，守卫里有一条断言
**过期即红**（`now > deadline`）。到期只有两条路：修掉，或走裁决把 deadline 往后挪 ——
改常量会出现在 diff 里，是唯一留痕。**没有期限的债务清单与「自我清算」断言是矛盾的**（用户判据）。

**② 守卫从「有注解」升级为「有效事务」**
`hasEffectiveTransaction()`：只有 `REQUIRED / REQUIRES_NEW / NESTED` 算数；
`NOT_SUPPORTED / NEVER` 明确不建事务（新增反例3 覆盖）；`SUPPORTS / MANDATORY` 取决于调用上下文、
静态判不了 ⇒ **按「不算」处理（宁可误报）**。局限已登记：这两种在外层有事务时其实是安全的，本守卫仍会红。

**③ `BIZ:<错误码>` 的暴露面登记**
AD24 响应里的 `failures.reason` 现在会带 `BIZ:<错误码>`。当前端点挂 `finance:admin`（管理端），
错误码可见性可接受。⚠ **一旦降低权限或复用到商家端/公开端**，必须重新评估：
错误码是**可枚举的侧信道**（能反推账号状态、单据存在性等），届时应改为固定文案 + 服务端日志关联 traceId。

**④ 裁决：合入主干的判据 = 代码可合，不是功能可用**

| 判据 | 内容 |
|---|---|
| **合入主干**（本批按此） | ① 门禁全绿（`full` 七段）② 债务已登记（含 deadline）③ 上线 checklist 写清「还差什么」 |
| **功能可用**（另一道闸） | ① 触发入口完整（AD24 + **handler 在 XXL-Job 注册** + 首次三段核对）**且** ② 真实打款渠道接入 |

理由：把「功能可用」当合入判据会让 `wip` 分支长期挂着，merge 成本随时间上升（用户判据）。
⇒ `[UNVERIFIED]` 分支前缀此后只表示「**功能未完成**」，不再表示「没验证过」；
合入时按上表逐条勾，**不得**把「合入」读成「提现可用」。

### 1.35 打款渠道（设计草案，2026-10-19）：能立刻写的与必须等决策的

> **为什么先出设计而不是直接写代码**：渠道适配要厂商（微信商家转账？银行批量代付？）与凭据，
> 现在写只能写"假适配器"，等于制造第二处「已记账未付款」。所以拆成两半。

**A. 与厂商无关、现在就能写（下一步做）**

| 件 | 形态 | 说明 |
|---|---|---|
| `domain/port/WithdrawalPayoutPort` | `PayoutResult pay(PayoutCommand)` | 出款能力的唯一抽象；**只此一个出口**，禁止 Manager 直连渠道 |
| 状态机 | `PENDING_REVIEW →(受理)→ PAYING →(回执)→ PAID_OUT / FAILED` | 新增 `PAYING` / `FAILED` 两个状态；两者都**仍占用 `frozen_amount`** |
| 幂等键 | `withdrawal.payout_no`（业务号，唯一） | 渠道侧去重的唯一依据；重复受理返回同一结果，不重复出款 |
| 条件迁移 | `claimPayable(id)` / `markPaid(id, receipt)` / `markFailed(id, reason)` | 全部走条件更新 + 影响行数，与既有风格一致 |
| **E3 扩展** | `frozen == Σ (PENDING_REVIEW + PAYING + FAILED)` | **这正是 §1.28 登记的前提被触发**：新增占用冻结的状态必须同步扩展 E3 与释放路径；`FAILED` 进入「待人工处置」，释放需显式操作 |
| 兜底任务 | `payoutResumeJob` | 仿 `RefundResumeJob`：对 `PAYING` 超时单查询渠道回执；查不到则告警，**不自动置失败** |

**B. 必须等决策才能写**

1. **渠道选型**（决定适配器形态：API 实时出款 vs 批量文件代付）；
2. **凭据与商户资质**（无凭据无法联调，只能 mock —— mock 不属于"可用"）；
3. **失败后的钱怎么退**：`FAILED` 是「退回可提现」还是「人工线下处理」？这决定 `FAILED` 是否释放冻结；
4. **对账口径**：渠道流水号落哪张表、按日对哪条等式（很可能会新增第 7 条等式）。

⇒ 在 1–4 有答复前，**`payout-enabled` 保持默认 false**，端到端仍不可上线。
A 部分不依赖这些决策，可以先落地（含 E3 扩展与新状态的条件迁移），落地后 **E2 从「空集绿」变成「有真实 PAID_OUT 数据可验」的前置条件之一**。

### 1.36 A1 开工契约（2026-10-19，用户裁决）

**范围（同一批，不可再拆）**：`PAYING` 状态 + **E3 扩展** + 释放路径 + `payoutResumeJob` 骨架。
**不做**：`FAILED`（失败停在 `PAYING` 只告警），A2 等「失败后钱怎么退」的口径。

> **订正交接单的前一句表述**：§1.35 写的「先改等式、用例先红、再加状态」**在编译上做不到** ——
> E3 的断言文本引用 `PAYING`，没有枚举就编译不过。⇒ 正确表述是：**等式与状态枚举同一批落地**，
> 红→绿靠「先写断言再补状态迁移」，不是靠两个提交。

**四条硬口径（写死在实现里，不是注释）**

1. **`FAILED` 停留告警**：`FAILED`（A2 才引入）**不会自动消失** ⇒ 停留超过 N 天必须升级告警；
   否则该单永久占用冻结额，商家额度被无声吃掉。A1 阶段等价要求：`PAYING` 停留同样要升级告警。
2. **人工「释放」需双确认**：必须确认「钱没出去」+ **填写依据**（渠道查询结果/流水截图编号），
   并进审计（`@OperationLog` + 单据备注）。**不许**一键释放。
3. **重试必须复用同一个 `payoutNo`**：写进端口注释 —— 这比「出口唯一」更硬：
   出口唯一只保证只有一条路，幂等键才保证**同一条路重复走不会出两次钱**。
4. **`payoutResumeJob` 语义澄清**：查不到回执 ⇒ **保持 `PAYING` + 告警**；
   `FAILED` **只由渠道明确失败触发**。「结果未知」与「确认失败」**不能混在一个状态里** —— 混了就会出现
   「回执丢了就判失败 → 自动→重试→出两次钱」或「判失败→释放额度→钱其实已出」。

**两条 mock 实现细节**

- `ASYNC_CALLBACK` 用 **`Clock` 驱动**（测试注入固定时钟推进「时间」，**不 sleep、不真等**）；
- fail-fast 检测的是**「占位凭据」而不是「空值」**：`CHANGE_ME`/`TODO`/`xxxx`/`your-app-id` 这类才算占位；
  空值在 mock 模式下属合法（与 `MockWechatMiniClient` 的 `enabled=false` 约定一致）。

**两条线的划分（合入判据）**

| 线 | 完成条件 |
|---|---|
| **代码可合**（A1 完成即达） | 门禁全绿 + 债务登记 + 本契约五条落地 ⇒ **合入，不等渠道** |
| **功能可用** | 触发入口完整 **且** 真实渠道接入（含首次失败的真实回执验证） |

**登记：`accepted=false` 分支目前未验证**
A1 里该分支只有单测（mock 返回 false）；**真实渠道接入后第一次失败回执**才是真实验证。
在那之前，报告里只能说「有代码路径与单测」，不能说「出款失败已被验证」。

### 1.37 A1 落地（2026-10-19）：两阶段出款 + E3 扩展

**状态机**：`待审核(1) → 付款中(4) → 已打款(2)`；拒绝仍是 `待审核 → 已拒绝(3)`。
`FAILED` **未引入**（A2 等「失败后钱怎么退」口径）—— 渠道未受理就停在 `PAYING` 只告警。

**改了哪些**
- 枚举加 `PAYING(4)`；`withdrawal` 加 `payout_no`（唯一键，出款幂等键 = 单据业务号）
- 条件迁移：`claimPaying(id, payoutNo)`（`WHERE status=1`）、`markPaidIfPaying(id)`（`WHERE status=4`）
- `review(PAID_OUT)` 只做**受理**（第一段）：打款闸仍在**任何写之前**，通过后仅 `claimPaying`；
  **不再**在事务里解冻/记账
- 新增 `WithdrawalPayoutService`（事务外调渠道）+ `WithdrawalPayoutJob`（`@XxlJob("payoutPendingWithdrawals")`）；
  落账由 `WithdrawalManager#completePayout`（`@Transactional`，跨 Bean 调用 ⇒ 代理生效）
- **E3 扩展**：`frozen == Σ (待审核 + 付款中)`；新增用例钉住「受理后仍占冻结、paid 不记」

**为什么这样切**：远程出款不能进事务；`review` 与「调渠道」事务语义相反，同居一类迟早出事故，
且同类自调用会让 `@Transactional` 失效（§1.33 守卫）。

**登记（未验证 / 待 A2）**
1. `accepted=false` 只有单测；真实失败回执未见（渠道接入后首次失败才是真验证）；
2. `PAYING` 停留的**升级告警未实现**（§1.36 口径要求 N 天未处置升级）—— 当前只 `log.warn`；
3. mock 通道（四态 + `Clock` 驱动）**未实现**，现用 `ManualPayoutAdapter`（本地确认、无 I/O）；
4. `FAILED` + 人工释放（双确认 + 依据 + 审计）属 A2。

### 1.38 mock 出款通道落地（2026-10-19）

`MockPayoutAdapter`（`trade.withdrawal.payout-mock.enabled=true` 时生效，与 `ManualPayoutAdapter`
**结构互斥**——后者带 `matchIfMissing=true` 的反向条件，不靠人记得关）。

**四态（全部有用例）**

| 模式 | 返回 | 用例名（按裁决，不出现 FAILED） |
|---|---|---|
| `ACCEPTED` | 受理 + `channelRef = MOCK-<no>` | 受理成功且自曝 MOCK 身份 |
| `REJECTED` | `accepted=false, reason=MOCK_REJECTED` | **不置已打款，保持付款中** |
| `TIMEOUT` | `accepted=false, reason=MOCK_TIMEOUT` | **不置已打款，保持付款中**（结果未知≠失败） |
| `ASYNC_CALLBACK` | 时钟未到 ⇒ `MOCK_AWAITING_CALLBACK`；到了 ⇒ 受理 | 时钟推进后受理 + 同 payoutNo 幂等 |

**两条实现约定**
1. **`Clock` 驱动，零 sleep**：`ASYNC_CALLBACK` 的「回执何时到」由注入的 `Clock` 决定，测试用可变时钟推进；
2. **只缓存终态**：同一 `payoutNo` 的**终态**结果冻结返回（终态幂等）；非终态（等回执）每次按时钟判定 ——
   这正是真实渠道的样子（回执没到之前每次问都是「还没到」）。

**resume 语义确认（用户裁决）**：resume job **复用 `pay`**，不另设 query；
「重试不会出两次钱」**完全依赖端口幂等契约** —— 已写进 `WithdrawalPayoutPort` 注释，作为换适配器的前置条件。

**占位凭据 fail-fast**：`verifyNoPlaceholderCredentials()` 在启动时校验 `app-id/mch-id/api-key`，
命中 `CHANGE_ME/TODO/xxx/your-/placeholder/示例/待填` ⇒ **抛异常启动失败**；**空值合法**（mock 模式）。

**仍未做**：`PAYING` 停留的升级告警（mock 后单独补，不等 A2）；`FAILED` + 人工释放（A2）。

### 1.39 合入（2026-10-19）与两条确认

**合入**：`wip/t-withdraw-step1-unverified` → **`develop`**（fast-forward，18 个提交）。
`main` 未动（它落后 develop，按原节奏走发布）。合入用 `git merge --ff-only`，
**全程未用 `git add -A`**，提交均按显式路径；合入前后 `git status` 的其它会话改动数**一致**（未被触碰）。

**① 占位凭据自检的位置：构造期（已确认只属于 mock）**
`MockPayoutAdapter` 的自检从 `@PostConstruct` **前移到构造函数**：Spring 启动即构造该 Bean ⇒ 构造抛
`IllegalStateException` ⇒ **容器启动失败**，且不依赖 `@PostConstruct` 是否被调用。
`ManualPayoutAdapter` **不参与**（它没有这段自检）。
⚠ 登记局限：曾尝试用 `ApplicationContextRunner` 写容器级 `hasFailed()` 断言，但该 Runner 下 Bean 未注册/惰性，
测不到启动失败 —— 已删掉那条测试，改为「构造期抛异常 + 单测锁定」。要真做容器级断言需整包 `@SpringBootTest`
（要 Nacos/DB），属后续项，不假装覆盖。

**② 时钟口径：mock 与 job 未联动（登记）**
mock 的 `ASYNC_CALLBACK` 由注入 `Clock` 驱动；但 **resume job 目前没有超时判定**（A1 只扫全部 `PAYING`），
所以两者**没有共用时钟**这件事目前不产生矛盾 —— 一旦加超时阈值，必须让 job 与 mock 用同一个 `Clock`，
否则「测试里时钟推进了、job 仍按系统时间判」会造出假绿。下一轮做 `PAYING` 停留告警时一并匹配。

**③ `[UNVERIFIED]` 语义失效**
代码已合入 develop 且门禁全绿 ⇒ 该前缀**不再表示「未验证」**；历史提交信息不改写，
但**此后新分支/提交不得再用此前缀表达「未验证」**；若要表达「功能未完成」，请直接写「功能未完成」。

**④ 主干复核**：合入后在 `develop` 上重跑 `verify-gates.sh full` —— 见本节的验证记录（全绿）。
### 1.41 两条结构性问题 + 短码缺陷交办（2026-10-19）

**① 「排除 item-service 的 reactor 绿」≠ 主干绿**：主干**全绿待 `ShortCodeTest` 修复**。
本批能证明的只是「本批改动 + 其余模块绿」，**不得**在任何结论里写成「主干全绿」。

**①-b 跑 `full` 前必须确认「工作区无未提交源码改动」，否则结论只能标「当前树」**
`mvn install` 会编译**工作区里的全部源码**（含并行会话未提交的改动），架构守卫又读 `target/classes`
⇒ 结论可能既不是「主干」也不是「我的分支」。**口径**：跑 `full` 前先 `git status --short` 看源码改动；
有 ⇒ 结论标「当前树」；无 ⇒ 才能标「主干」。

**② 反向也成立：陈旧的 `target` 会掩盖已提交的失败**
本轮实证：`item-service.ShortCodeTest` 是**已提交**代码（`265543d6`，并行会话的 186 个提交之一），
早期 `full` 绿是因为 item-service 的 class 没被重编；`mvn clean` + `install` 后才暴露。
⇒ **「绿」必须标注它对应的构建状态**（干净重编 / 增量）。

**机制（已实证缺失）**：`verify-gates.sh` 的测试段是 `mvn test`（第 82 行），**不强制 clean** ⇒ 陈旧产物可长期假绿。
已新增只读检查器 `scripts/check-stale-classes.sh`：任一模块存在「比 `target/classes` 更新的源码」即非零退出。
**接线（已完成）**：挂进 **pre-commit**（`.githooks/pre-commit`，`core.hooksPath=.githooks`），
在 `verify-gates.sh fast` 之后运行；失败即拒绝提交，跳过需显式 `SKIP_STALE_CHECK=1`。
为什么不改 `verify-gates.sh`：那份文件当前由并行会话改着，改它会混改动。
并已补齐**测试代码**维度（`src/test/java` vs `target/test-classes`）—— 本轮 `ShortCodeTest`
正是测试产物陈旧，只比主代码时脚本覆盖不到。

**根因未查（登记）**：`mvn test` 正常会重编，**没重编**说明时间戳判定或增量编译配置有问题
（也可能是 `install` 与 `test` 的 classpath 差）。在根因查清前：**「绿」必须配合
`check-stale-classes.sh` 的输出一起看**，单独一个「绿」不接受。

**③ 短码缺陷交办（不是 flake，不要用 flake 定性放过）**
- 现象：`ShortCodeTest.shouldNotDeriveFromBusinessId` 失败，`短码不得复用业务键子串（可反解）: d75542c73d`
- 证据：**隔离跑两次全绿（8/8）**；**全量 reactor 红**；来源提交 `265543d6`
- **第一件事**：**先查 `d75542c73d` 是哪个业务键的子串** —— 直接从失败值反推派生来源，比三个方向更省时间。
- **第二件事**：**先确认是全量执行序改变输入，还是跨测试静态状态污染**（前者查随机源/输入构造，后者查静态缓存/单例）。
- 三个排查方向（交并行会话）：**(a) 派生逻辑** 是否用了 `id`/`hashCode` 的子串或可变来源；
  **(b) 断言过宽** 是否把「不等于业务键」写成了「不含任意子串」；**(c) 测试数据/跨测试静态状态混入**
  （共享 `Random`/`IdGenerator`/静态缓存使不同执行序下输入不同）
- 该模块红灯**不阻塞**本批：本批以「排除 item-service 的全量 reactor」+「trade-service 单测」为证据。

---
### 1.40 合入后四件确认（2026-10-19）

**① 门禁脚本版本 —— 主干结论只对「当前树」成立**
`git show HEAD:.../verify-gates.sh` 里**只有 `fast_steps()`**（旧六段版）；工作区那份带 `+45/−6`
（三段式）的脚本是**并行会话的未提交改动**。⇒ 我在 `develop` 上跑的 `full` 用的是**工作区脚本**，
**结论只对当前树成立**；若那份改动被回退/再改，必须重跑（与 §1.32⑤ 同一口径）。

**② `MockPayoutAdapter` 无 `@Lazy`、无全局懒加载**
`grep` 结果为空（`@Lazy` / `lazy-initialization` 都不存在）⇒ 生产里它**启动即构造**。
⚠ 但**单测的覆盖边界要说清**：`MockPayoutAdapterTest` 覆盖的是**构造器逻辑**（占位凭据自检、四态、幂等），
**不是「启动时机」**；容器级启动断言仍未覆盖（`ApplicationContextRunner` 在该 Runner 下测不到，已删测试，§1.39 登记）。

**③ resume job 无超时判定 —— 登记其后果与修法**
当前 `settle()` 扫**全部** `PAYING`，没有时间阈值 ⇒ **重试频率 = job 频率**（每跑一次就重试一遍全部停留单）。
加阈值时，扫描条件必须是 `WHERE status = 4 AND create_time < now - timeout`（不是内存过滤），
且 `now` 与 mock 用**同一个 `Clock`**（§1.39 ②）。

**④ 「调几次 pay、只记一次账」的断言在 service 层**
新增 `WithdrawalPayoutServiceTest#shouldAccountOnce_whenSettleRunsTwice`：
同一单 `settle` 两次 ⇒ `pay` 被调 **2** 次、`completePayout` 被调 **2** 次，但只有第一次落账
（第二次返回 false，服务不再往下推）。不靠 mock 层的幂等来代表 job 层的正确性。

**⑤ 固定 ID 机械检查**：对 `trade-service` 测试做 `grep | uniq -c`，无重复常量/字面量（`SELLER_ID = 9L`
等多处是**同一常量被多个用例引用**，非撞车）。

---
### 1.42 `PAYING` 停留告警的三个前置（下一轮开工前锁定）

1. **阈值与 job 扫描同一 commit（共用 `Clock`）**：加 `N`（停留时长阈值）的那次改动，
   必须同时给 `settle()` 的扫描条件加时间下界 `create_time < now - N`，且 `now` 取自**与 mock 同一个 `Clock`  Bean**。
   分开做会出现「阈值按系统时间、测试按注入时钟」的假绿。
2. **`N` 与 `timeout` 的语义写清**：`N` = **单子停留在 `PAYING` 多久算异常**（告警用）；
   `timeout` = **job 扫描时过滤的同一时长**（重试用）。二者**同源**（同一配置项），
   但**用途不同**：`N` 只决定「要不要喊人」，`timeout` 决定「这次扫不扫它」。不允许出现两个独立数字。
3. **告警体带定位信息、不带敏感信息**：必须含 `payoutNo`、**已停留时长**、**上次 `pay` 的返回**
   （`accepted`/`reason`/`channelRef`）；**不得**带银行账号、开户行、商户凭据。
   这四项是「能不能立刻判断该不该人工查渠道」的最小集合。
4. **告警按 `payoutNo` 去重**：同一单在同一个时间窗内只告一次；跨过新的时间窗（如每 `N` 再告一次）
   要显式定义，不能靠日志量自然去重 —— 否则要么刷屏、要么被淹没。

---
### 1.43 B1 设计（`PAYING` 停留告警）+ 两条流程约束（2026-10-19）

**① `SKIP_STALE_CHECK=1` 必须留痕（已实现）**
`pre-commit`：跳过时写 `STALE_SKIP.log`（时间 + 提交人）并置 `STALE_SKIP_PENDING`；
`commit-msg`：存在标记时，**message 必须含 `[SKIP-STALE]`**，否则拒绝提交。
理由：只用环境变量跳过，**跳过会变成默认**且无人知晓；写进 message 才能 `git log --grep` 查出来。

**② B1 三个设计点（开工即照此）**

| 点 | 定法 | 为什么 |
|---|---|---|
| 首次 vs 每轮 | **派生"告警代次"**：`gen = (now - create_time) / N`；**代次变化才告警**，同代次内按 `payoutNo` 去重 | 不用加状态表就同时得到「首次即告」+「每 `N` 再告一次」+「不刷屏」；`N` 与 job 扫描的 timeout **同源** |
| 告警体结构化 | `PayoutStaleAlert(payoutNo, withdrawalId, sellerId, amount, dwellMinutes, gen, lastAccepted, lastReason, lastChannelRef, nextAction)`，一行 JSON 打 `log.error`（后续可插通知通道） | 只有 `log.error("超时了")` 时，运维还得回查库；结构化才能直接接告警平台与工单 |
| 带"下一步" | `nextAction` 给**渠道查询入口**：mock → 打印 `channel=MOCK` 与 `payoutNo`；真实渠道 → 渠道后台流水查询 + 管理端 `/api/admin/finance/withdrawals/{id}` | 告警的价值是"下一步做什么"；只报现象等于把排查成本转给人 |

**③ C1 已裁决：`FAILED` **不自动释放****
失败 ≠ 钱没出去（可能只是回执丢了/渠道超时）⇒ `FAILED` 进「待人工处置」，
由管理端显式操作释放，且必须**双确认"钱没出去" + 填写依据**，进审计。A2 按此实现。

**④ D1 期限**：`verify-gates.sh` 的接线（stale 检查或强制 clean 进 `full`）由并行会话在
**2026-10-24 前**确认口径；**逾期后所有提交的「绿」只能标「当前树」，不得标「主干」** ——
这包括后续任何批次，不是只针对本批。

---
### 1.44 B1 落地（2026-10-19）：`PAYING` 停留告警

**实现**：`PayoutStaleAlerter` + `@XxlJob("alertStalePayingWithdrawals")`（与出款任务**分开注册** ——
混在一起会让告警被出款失败淹没）。

| 设计点 | 落法 |
|---|---|
| 首次 vs 每轮 | 派生代次 `gen = 停留分钟 / N`，**代次变化才告警**，同代次按 `payoutNo` 去重 ⇒ 首次即告 + 每 N 再告 + 不刷屏，且不需要状态表 |
| 结构化 | 一行 JSON：`event/payoutNo/withdrawalId/sellerId/amount/dwellMinutes/gen/lastPay/nextAction` |
| 下一步 | `nextAction` 给**入口**：查渠道流水（MOCK 看日志 payoutNo；真实渠道查后台）→ 管理端 `/api/admin/finance/withdrawals/{id}`；并写明「禁止直接释放冻结」 |
| 不带敏感信息 | 无银行账号、无开户行、无商户凭据（单测断言 `6222` 与「银行」都不出现） |

**同源**：`N` 取 `trade.withdrawal.payout.stale-alert-minutes`（默认 30），与出款任务扫描的时间下界同一配置；
时钟经 `Clock` Bean（与 mock 出款通道同一类型），**零 sleep**。

**登记（未做/局限）**
1. `lastPay` 来自 `WithdrawalPayoutService` 的**进程内**最近一次结果，重启后为 `NOT_ATTEMPTED` ——
   要跨重启必须落库（新增列），属后续；
2. 去重集合是**进程内**的：多实例会重复告警，等接告警平台后由平台按 `payoutNo+gen` 去重；
3. 出款任务的**扫描下界尚未加**（`settle()` 仍扫全部 `PAYING`）—— 加下界那次要复用同一配置与同一 `Clock`。

---
### 1.45 短码失败：实证结论（2026-10-19，子 agent 只读诊断，210 万次实跑）

> **这一节取代 §1.41③ 里"三方向待查"的表述** —— 实测已给出确定答案。

**结论**：不是环境偶发、也不是执行序/静态污染，而是**断言本身不可满足**（oracle 越界）。
`ShortCodeTest` 断言「10 位随机短码不含 32 位随机业务键的**任意 4 位连续子串**」，
命中面 = `7 窗口 × 29 偏移 × 16⁻⁴ ≈ 1/323` ⇒ **每跑一次都有 ~0.3% 概率红**，与执行序无关。

**两处早先判断被实证纠正**
1. **`1e-11` 是误算**（差了约 8 个数量级）：那是"整串相等"的量级，被套用到"含 4 位子串"上。
   所以"跑一次就中 ⇒ 一定是确定性问题"这个推理不成立。
2. **"隔离两次全绿 ⇒ 全量红是跨测试状态"不成立**：`P(连续两次全绿) = 99.4%`，
   隔离绿**零信息量**；实测分块失败率平坦（3.05 / 2.99 / 3.17e-03），JVM 首次调用同样会红。

**证据（可复核）**：反射调真实测试方法 210 万次 ⇒ 6197 次失败，`p = 2.951e-03`（≈1/339）；
`d75542c73d` 是**该次调用现场生成的 UUIDv7** 的某个 4 位片段（非截取、非哈希前缀）；
`ShortCode.newCode()` 无入参、用 5 字节全新熵（`IdGenerator.randomHex`），`Spu.newBusinessId()` 是 UUIDv7
⇒ "短码由业务键派生"结构上不可能，**断言测的不是它声称的那件事**。

**推荐修法（尚未实施）**：把值级抽样换成**结构级 provenance** —— 断言 `newCode()` 无参、
`ShortCode` 不引用 `Spu/spuId/IdGenerator.nextId`，加进 architecture-tests（与 ADR-050「ID 生成出口」守卫同处）。
理由：完全与随机数解耦 ⇒ 任意执行序确定、误报 0。值级断言在 hex-vs-hex 下**做不到零误报**。
顺带：失败消息应带 `businessId` + 命中窗口下标（现在这条消息不可复原，工单 `d75542c73d` 就反查不出来）。

**教训（进判据候选）**：概率估算要写成"命中面 × 单次概率"，别用"整串相等"的量级去估"含子串"。

---
### 1.46 陈旧编译：根因查清 + 对上一轮归因的更正（2026-10-19，子 agent 实测）

**① 根因（不是"增量只编子集"）**
`maven-compiler-plugin 3.13.0` 默认 `useIncrementalCompilation=true` + `staleMillis=0`：
**模块级**用时间戳判定每个 `.java` 是否比自己的 `.class` 新 —— 判"没变化"就整模块**不调 javac**
（`Nothing to compile`），判"有变化"就**全模块重编**。本仓 pom 对该插件无任何覆盖项 ⇒ 判据完全交给 mtime。
复现：`touch` 一个源文件 ⇒ `Recompiling the module` + 编译**全部 35 个**源文件（不是 1 个）；
把 mtime 回退 ⇒ `Nothing to compile`。所谓"危险的子集模式"（`useIncrementalCompilation=false`）**本仓没开**。

**② 更正：`§1.41②` 的"本轮实证 = 陈旧 target 掩盖失败"是错的归因**
`ShortCodeTest` 实测失败率 **0.273%/次**（20 万次复刻：546 次失败）。而现有 4 份 clean 全量日志里该测试**都是 8/8 绿**。
⇒ "早期绿 / clean 后红"最可能是**概率命中**，不是陈旧产物。陈旧 target 这条**机制成立**（沙箱里改 12→13 再回退 mtime ⇒ 8/8 假绿、clean 后红），
但**不能拿来解释这次事故**。上一轮把它写成"本轮实证"是我的过度归因，此处更正。

**③ 修掉的两个真缺陷**
- `.githooks/commit-msg` 索引模式是 **100644** ⇒ **git 根本不执行它**，`[SKIP-STALE]` 留痕一直是空的。
  已 `chmod +x` + `git update-index --chmod=+x`（复核 `100755`），并删掉末尾 `|| true` 的死代码。
- 检查器 `discover()` 只认 `target/classes` ⇒ **`architecture-tests` 完全不在扫描面**（它只有 `test-classes`），
  而那恰是"陈旧 test-class 假绿"最贵的地方。已改为同时按 `target/test-classes` 发现模块。

**④ 仍存在的缺口（登记，未修）**
| 缺口 | 内容 | 代价 |
|---|---|---|
| C | 检查器与编译器**共用同一 mtime 判据** ⇒ mtime 被回退时**两者一起漏** | 只有内容比对能兜；已验证可行（fresh 编译 vs 仓库产物 164/164 + 40/40 字节相同），成本高 |
| A | 全新检出 / 局部构建后跑检查器 ⇒ 只发现一个模块 ⇒ "扫描面异常" exit 1 假红 | pre-commit 已背上；提示文案要写清"先 install" |
| D | 编译后仅 `touch` 或只改注释重写 ⇒ 报陈旧 | 修法即提示里的 `mvn -o -DskipTests install`，可接受 |
| 概率测试 | 强制 clean **不能**让 `ShortCodeTest` 稳定，红绿仍由 0.273% 决定 | 必须改断言（结构级 provenance），单独立项 |

**⑤ CI 接线（提案，未落盘）**：放在 `build` job 的 `Package` **之后**、失败**阻断**；
不能放 `Gates` 之前（全新检出 target 为空 ⇒ 扫描面异常 = 假红）。CI 不需要强制 `clean`（每 job 全新检出，
`cache: maven` 只缓存 `~/.m2`）；真正需要"绿对应干净重编"的是**本地增量** `full`。
**已接线**：`commit-msg` 已加入 CI 的「hooks present & executable」循环（此前只查 pre-commit/pre-push，看不见 100644 这个洞）；
陈旧检查已作为 CI 的独立 step 放在 `Package` **之后**、失败阻断；CI 不强制 `clean`（每 job 全新检出）。

---
### 1.47 批次 1 的两条硬约束（2026-10-19 裁决，落库前必须满足）

**① 落库与处理必须是两个事务**
回调处理流程：`落库(事务A)` → 提交 → `处理(事务B)`。原因：同一事务里"先落库再处理"，
一旦处理抛异常回滚，**回调原文也一起没了** ⇒「可重放」不成立（重放的对象都不存在了）。
所以：落库必须**先独立提交**，处理失败只把该行标 `FAILED`，重放读的是已提交的原文。

**② 重复回调不自动重试 `FAILED`**
同一 `notify_id` 再次到达时：若该行状态已是 `FAILED`，**不再自动重试**（否则渠道的重投会变成
我们的无限重试），直接按"已收到"应答；**重试只走管理端入口**（人手点、留依据、可审计）。
这与 `FAILED` 不自动释放冻结是同一条原则：**自动重试要保守，人工处置要留痕**。

**③ B1 三条的更正设计（开工即按此，不用 `create_time` 当重试节流）**
加列 `withdrawal.last_payout_attempt_at`，扫描条件
`status = 4 AND (last_payout_attempt_at IS NULL OR last_payout_attempt_at < now - N)`；
每次尝试前 `markPayoutAttempt(id, now)`（条件更新，0 行则跳过）。
`create_time` 只能做**停留时长**的基准，不能做**重试节流**的基准 —— 否则首次出款会被推迟 N 分钟。

---
### 1.48 B1 批次开工前的五项锁定（2026-10-19 裁决）

**① `last_payout_result` 用 `VARCHAR(128)`（按真实渠道 channelRef 长度定）**
存储形态 `分类:渠道号`（如 `ACCEPTED:MOCK-WD-…` / `REJECTED:MOCK_TIMEOUT`）。
真实渠道流水号常见不超 `64`，加分类前缀与余量取 `128`。
**若真实 channelRef 超长**：只存前段并加 `…` 截断标记，**不得**让整列超长（也不得静默截断而不标记）。

**② `payout_alert_log` 的保留期与清理**
保留 **180 天**，由每日清理任务批删（限速，避免大事务）。
**登记**：若上线前没做清理任务，该表会无限增长并拖慢唯一键索引 —— 此时上线 checklist 必须写「未定」，不得默认"以后再说"。

**③ 三处事务边界（写进代码注释，注释即契约）**
```
markPayoutAttempt(id, now)   ← 事务 A：独立提交，只做「重试节流」标记
pay(cmd)                     ← 事务外：远程调用，绝不在事务里
completePayout(withdrawal)   ← 事务 B：状态迁移 + 释放冻结 + 记 paid_total，同一事务
```
**有意为之的窗口**：A 成功而 `pay` 崩溃 ⇒ 单子仍是 `PAYING`，且已被节流 N 分钟。
那不是 bug：**先标记再调用**正是为了"崩了也不会立刻重打一次"。反过来（先调用后标记）才会重试风暴。

**④ `selectDuePayingIds` 与 `markPayoutAttempt` 必须用同一个 `Clock`、同一个 `now` 值**
`now` 只取一次，同时作为扫描上界与标记值传入。禁止各自 `LocalDateTime.now()` ——
否则边界处会出现"刚标记就被判 due"或"该 due 却不 due"的抖动。

**⑤ 渠道选型必须把「纯同步 / 纯异步 / 同步+异步」列进决策矩阵**
因为它直接决定**批次 1 要不要做**：

| 渠道形态 | 是否有回调 | 批次 1（回调日志） | 说明 |
|---|---|---|---|
| 纯同步（本地/人工确认） | 无 | **不需要** | 结果在调用返回时就确定 |
| 纯异步（渠道回调通知） | 有 | **必须** | 还要两事务 + 幂等 + 重放 |
| 同步+异步（受理同步、结果异步） | 有 | **必须** | 且"受理成功但回执丢失"正是 `PAYING` 停留告警的场景 |

**⑥ 短码撞红时的局部证明口径**
跑 `mvn -o test -pl '!item-service'` 做**局部证明**，并在结论里同时写两句：
"排除 item-service 的局部证明" + "**不等于主干绿**"（§1.41①）。不得因为一个模块的红就怀疑无关批次。

---
### 1.49 B1 三条的补充登记（2026-10-19）

**① 计数未回填期间：`full` 的计数段会红，这是预期的**
表数与测试数都要等一次**绿的全量** run 才能 `--write`；主干目前红在 `item-service.ShortCodeTest`（他人文件）。
在这之前跑 `full` 会看到"根文档计数与实测一致"失败 —— **属预期，不是新缺陷**，不要用改文档去消红。

**② 多实例去重目前只有 mock 覆盖，未被真库证明**
`payout_alert_log` 的去重靠 `INSERT IGNORE`（MySQL 方言）。现有用例都用 mock 桩，
**没有**在真库上验证过"同 `payout_no+gen` 第二次插入返回 0"。要真证明需 H2/MySQL 用例；
且若走 H2，`INSERT IGNORE` 需改成 `MERGE`/`ON DUPLICATE KEY`。**在补上之前，不得声称"多实例去重已验证"。**

**③ 截断是防御性代码**
`last_payout_result` 列长 128；`summarize()` 超长时**保留尾部**（渠道号唯一部分通常在末尾）。
已知渠道流水号不超此长度，截断只在换渠道/异常报文时兜底。

**④ 渠道形态决策必须同时定「对账文件从哪来」**
回调日志只是**实时链路**的兜底；真正的兜底是**对账**：渠道流水文件（或 API 对账接口）从哪来、
按日还是按小时、异常差异怎么处理。选型时若只定"同步还是异步"而不定对账来源，等于把最终一致押在回调上 ——
而回调恰恰是会丢的那个环节。

---
### 1.50 B1 五项补充（2026-10-19）

**① `last_payout_result` 列宽 160，截断有测试**
留余量应对更长渠道号；`summarize()` 的 `MAX_RESULT_LEN` 与列宽**同源对齐**（改列必须改常量，反之亦然）。
新增用例：构造 200 字符 `channelRef` ⇒ 断言 `length <= 列宽`、`startsWith("ACCEPTED:...")`、`endsWith(原文尾部)`。

**② `分类:渠道号` 按第一个 `:` 切分，渠道号允许含 `:`**
解析时用 `indexOf(':')` 取分类，**剩余全部**算渠道号 —— 不许 `split(":")` 后取 `[1]`（会把含 `:` 的渠道号截断）。

**③ `commit-verified.sh` 的 `TESTS` 必须覆盖本次改动涉及的测试类**
改 mapper/DDL/仓储实现时，单模块单类往往不够 —— 要么把相关测试类列全，要么 `MODULES=''` 跑全量，
要么在提交信息里明写"由 full 覆盖"。**不写清楚就等于把验证责任丢给下一次构建。**

**④ 多实例去重的真库验证：补全条件与更稳的实现**
补全条件：**批次 1 开工前或渠道接入前**必须有一条真库（H2/MySQL）用例证明"同 key 第二次插入返回 0"。
实现建议：`markAlertedOnce` 改成 **`INSERT` + catch `DuplicateKeyException` ⇒ 返回 0**，
这样 H2 与 MySQL 行为一致，不必依赖 `INSERT IGNORE` 方言。

---
### 1.51 对账层口径（2026-10-19 裁决）：报表不是门禁、状态机、留原文、先不自动处置

**① E7 是对账报表，不是门禁 —— 这条必须写死**
门禁放的是**不变量**（必须永远成立，红了就停线）；对账**天然有差异**（渠道延迟、手续费、跨日切分），
差异是**业务输入**，不是缺陷。所以：

- E7 **不进 CI 门禁**，否则它会长期红、最后被人用"放宽断言"消掉；
- E7 的产物是**差异报表 + 管理端待办**；
- 唯一允许进门禁的是**对账批次自身的完整性**（例：某日批次未拉取、或 `DIFF` 长时间未闭环 ⇒ 告警）；
- **闭环 = 处置**：`DIFF → 人工判定（接受差异 / 补记 / 挂账）→ RESOLVED`，并留存处置人与依据。

**② 对账需要状态机（否则每跑一次都全量比对）**
`PENDING → FETCHED → MATCHED | DIFF → RESOLVED`
- `PENDING`：已建批次待拉取；`FETCHED`：原文已落库；
- `MATCHED`：逐笔全匹配；`DIFF`：有差异进待办；`RESOLVED`：人工处置完（含"接受差异"与"补记"两种结论）；
- 推进一律**条件更新**（`WHERE status = 期望值`），同 `batch_no` 重跑幂等。

**③ 原始数据必须留存**
渠道报文整段落库（可按日分片/压缩）。理由：差异处置完仍要能**回溯原文** ——
否则"当时为什么判它是差异"永远说不清。保留期与告警表同口径，按日批删。

**④ 第一版不做自动处置**
只产出差异报告 + 管理端待办。**自动补记会改变 `paid_total`** ⇒ 必须人工确认。
这与"`FAILED` 不自动释放冻结"是同一条原则：**自动动作保守，人工动作留痕**。

**⑤ 两个口径（已定）**

| 口径 | 结论 | 理由 |
|---|---|---|
| 粒度 | **逐笔** | 汇总只能发现"有差异"，逐笔才能定位到"哪一笔"；且差异处置与 E7 的比对都需要逐笔 |
| 方向 | **拉取式**（我们主动拉），推送只作补充 | 拉取可控：重试/补拉/限速/按日补；不依赖渠道推送的可靠性。代价是时效取决于频率（按日 + 小时级增量补拉） |

---
### 1.52 对账第一批（2026-10-19，分支 `feat/reconciliation-daily`）

**三个口径（已定）**
1. **只做按日全量**：增量补拉第一版不做（增量会引入"窗口对齐/重复窗口"这类边界问题，先把全量跑稳）；
2. **原始报文入表**（`reconciliation_raw.payload BLOB`，按日分片），**不落本地文件系统** ——
   容器化部署下本地盘不可靠、也无法跨实例读取；
3. **匹配键**：先 `payout_no` 精确；再「金额相同 + 账期相差不超一天」模糊；仍匹配不到归差异。

**设计要点（写死）**
- **状态机重入语义**：`PENDING/FETCHED` 可重入（重拉覆盖逐笔，靠 item 幂等键不重复）；
  `MATCHED/DIFF/RESOLVED` **不可重入**，要重跑必须换 `batch_no` 新建批次 —— 否则会覆盖已人工处置的结论。
- **item 幂等键**：`UNIQUE(batch_no, payout_no, channel_ref)`。差异行的 `payout_no`/`channel_ref` 可为 NULL，
  故落库时对 NULL 侧填哨兵值（如 `-`），避免 MySQL 唯一键对 NULL 不去重。
- **`RESOLVED` 的处置字段**：`resolved_at` / `resolved_by` / `resolve_note`（依据必填）。
  结论分两类：**接受差异**（不补记）与 **已补记**（人工确认后改 `paid_total`），都要留依据。
- **E7 只算提现类**：查询侧过滤 `business_type = WITHDRAWAL`（或等价口径），
  否则把订单退款等其它出账混进来，差异会变成噪声。
- **本批不含**：仓储实现、拉取器、管理端、自动补记。只做表 + 状态机 + 比较器 + 单测。

---
### 1.53 写仓储实现前的四项与一项待定（2026-10-19）

**① 同日多批次：本轮不加 `is_active` / `superseded_by`，下一批改表**
后果必须写明：现在同一 `provider + biz_date` 可以有多个批次（`batch_no` 不同即合法），
**没有"有效批次"概念** ⇒ 出报表时必须自己按 `create_time` 取最新，否则同日两批会被重复统计。
下一批改表时补两个字段，并把"取有效批次"收敛成唯一查询。

**② 模糊匹配只在候选唯一时生效（已实现 + 用例）**
同金额多笔候选一律不猜，直接归差异。理由：猜错会把差异**抹平**，比留下差异危险得多。
用例 `shouldNotGuess_whenFuzzyCandidatesAreAmbiguous` 钉住。

**③ 渠道流水是否有"业务类型"字段：未知（未选型）—— 这是 E7 的前提**
若渠道流水**没有**业务类型字段：`WITHDRAWAL` 过滤只能作用于我方；那么"渠道有我们没有"的条目
**无法判定是否提现类**。此时必须二选一，不能默认并入：
- 配一张 `provider → 业务类型映射`（按渠道的 `type/remark` 字段映射），或
- 把这类条目单独归 `CHANNEL_ONLY(UNCLASSIFIED)` 统计，**不与提现差异混算**（否则差异率被噪声污染）。

**④ `RESOLVED` 允许改结论，且必须留历史（已实现）**
状态机加 `RESOLVED → RESOLVED`；每次改结论写一条 `reconciliation_resolution_log`
（`conclusion` / `note` 必填 / `resolved_by`）。否则"处置错了但改不回来"或"改了没人知道"，
审计直接断链。`RESOLVED → DIFF` 仍禁止（改结论不等于退回未处置）。

**⑤ `reconciliation_raw` 的分片逻辑：待定（写仓储时定）**
要定的三件：按报文大小切还是按行数切、`seq` 的单调性与重入语义、单批次总分片上限。
约束先钉住：`UNIQUE(batch_no, seq)` 必须成立，重拉时先删后插（幂等）。

---
### 1.54 仓储实现前的三项拍定（2026-10-19）

**① `is_active` / `superseded_by` 现在加（已加进 DDL）**
理由：表还是空的，改表成本此时为零；等有数据再加就要考虑回填。
**配套纪律**：所有"取批次"的查询必须收敛到**唯一一个**方法（仓储层 `findActiveBatch(provider, bizDate)`），
下次语义变化（例如允许多有效批次）只改这一处 —— 不许散落在多个地方各写一遍 `WHERE is_active = 1`。

**② 差异行 NULL 侧哨兵用 `__NULL__`（不用 `-`）**
`-` 太容易与真实值相撞（渠道号/幂等键都可能含短横）。`__NULL__` 八字符，
列宽 `payout_no` 64 / `channel_ref` 160 都放得下，且保留字形态一眼可辨。
配套：**入参/出参转换处必须把 `__NULL__` 还原成 NULL**，否则会以假值进入报表。

**③ 分片与重拉（定死）**
- 每片 **1000 条**；**超长单条单独成一片**（不得切断单条报文）；
- `seq` 从 1 递增，`UNIQUE(batch_no, seq)`；
- **重拉在同一事务内先删后插**（item 与 raw 都是），保证不出现半旧半新；
- **重拉只允许 `PENDING` / `FETCHED`**（`MATCHED/DIFF/RESOLVED` 拒绝重拉，要重跑必须换 `batch_no`）；
- **登记**：重拉会**重置 item 的 `create_time`**，所以"这条何时被创建"不能作为审计依据 ——
  **审计以处置日志（`reconciliation_resolution_log`）为准**。

---
### 1.55 仓储实现开工：哨兵层先落（2026-10-19）

**本批已完成**：`ReconciliationSentinels`（`__NULL__` 编码/解码）+ 往返单测。
**反向风险已挡在写入侧**：真实值恰好等于哨兵时 `encode` 抛 `IllegalArgumentException` ——
否则读出时会凭空造出一个 NULL，而这类"占位泄漏"在差异统计里极难发现。
解码只认完整哨兵，`__NULL__x` / `_NULL_` 这类形近值不受影响（有用例）。

**为什么先做这一层再写 Mapper**：它是 `UNIQUE(batch_no, payout_no, channel_ref)` 能真正去重的前提；
Mapper 里每次写都要过 encode、每次读都要过 decode，先把这个函数钉死，后面才不会各处各写一遍。

**下一步（仓储实现）**：
`ReconciliationBatchPO` / `ReconciliationItemPO` / `ReconciliationRawPO` + 三个 Mapper +
`ReconciliationRepository`（`findActiveBatch` 唯一入口、`claimBatch` 条件迁移、重拉同事务先删后插）+
H2 用例（重拉幂等不翻倍 / 闭环后重拉被拒 / 哨兵往返）。

---
### 1.56 写 Mapper 前的五项（2026-10-19）

**① 两列改 `NOT NULL`（已改 DDL）**
`reconciliation_item.payout_no` / `channel_ref` 都是 `NOT NULL`：**数据库也拒绝 NULL**，
"空"这件事完全由哨兵承载。这样"漏编码"会直接写库失败，而不是悄悄插入一行 NULL（NULL 不参与唯一键去重）。

**② 哨兵比较用 `equalsIgnoreCase`（已改 + 用例）**
MySQL 列 collation 可能是 `_ci`，若库里存了 `__null__`，用 `equals` 就认不出来、把占位当真实值统计。
**不依赖数据库 collation，由代码自己判**；`encode`/`decode`/`isSentinel` 三处一致。

**③ 真实值恰等于哨兵的处置：整批失败 + 告警 + 人工介入**
**不许**只跳过那一条继续跑 —— 那样这批对账的结果就"缺一行"，而缺行不会被任何断言发现。
所以：抛异常 ⇒ 批次停在 `FETCHED`（未闭环）⇒ 告警 ⇒ 人来看是什么值长这样。

**④ 哨兵只在 `RepositoryImpl` 一处收敛**
Domain 层与 `refetch` 的入参都是**原始值**（`null` 就是 `null`），哨兵编码/解码只发生在仓储实现里。
（这条收紧了 §1.55 的表述：不是"Mapper 里写要过 encode"，而是"仓储实现里过"。）

**⑤ `claimBatch` 在仓储层先做状态白名单校验**
不能只靠 `UPDATE ... WHERE status = ?` 的影响行数来判断 —— 那样"状态非法"与"并发已变"会混成一个返回值，
排查时分不清是业务错还是竞争。仓储层先校验 `canTransit`，非法直接抛业务异常；再执行条件更新。

**⑥ H2 用例要覆盖三个终态的重拉被拒**
`MATCHED` / `DIFF` / `RESOLVED` **三个都要**（只测 `RESOLVED` 会漏掉前两个——它们同样不可重拉）。

---
### 1.57 写仓储前的六项（2026-10-19）

**① 有效批次唯一性：`UNIQUE(provider, biz_date, active_key)`**
**技术更正**：MySQL 的**生成列不能引用 `AUTO_INCREMENT` 列、也不能用 `UUID()`**（非确定性函数禁止），
所以"生成列"这条路走不通。改成**应用维护的普通列** `active_key`：
有效批次写 `ACTIVE`，被取代时写自己的 `batch_no`（天然唯一）。唯一键 `(provider, biz_date, active_key)`
就把"同一天只能有一个有效批次"变成**数据库保证**，`findActiveBatch` 的唯一性不再是假设。
代价：写 `is_active = 0` 的那条更新必须**同时**改 `active_key` —— 两列要么一起改，要么都不改（仓储层一处实现）。

**② `resolution_log` 只引用 `batch_no`，不引用 `item_id`**
因为重拉会**删掉并重建 item**（主键变化）。审计若挂在 item 上，一次重拉就把历史指歪。

**③ `claimBatch` 校验通过但 0 行 ⇒ 抛并发异常，且带三要素**
`batchNo` / `expectedFrom` / `actualStatus`（0 行时再查一次当前状态填进去）。
没有这三样，日志里只会写"claim 失败"，无法区分"被别的实例抢先"还是"数据被改坏"。

**④ `findActiveBatch` 无批次是正常返回值，不是异常**
返回 `Optional.empty()`；只有"期望有却没有"的调用方（例如结算汇总）才需要自己决定报错。

**⑤ 补记要能独立验证**
`resolution_log` 增 `booked_amount` / `paid_total_before` / `paid_total_after`：
审计不必回头查当时余额，只看日志就能验证"补了多少、前后对不对"。

**⑥ 列宽校验**：`payout_no` 64、`channel_ref` 160，均 ≥ 哨兵（八字符）✔。

**已具备的前置**：两列 `NOT NULL`；哨兵大小写不敏感；`canTransit` 白名单；`RESOLVED→RESOLVED` 可纠错。

---
### 1.58 有效批次唯一性定稿 + 并发创建处置（2026-10-19）

**① 生成列方案成立，我的"不可行"判断是错的**
我上一轮说"生成列不能引用 AUTO_INCREMENT、不能用非确定性函数"——那条限制**只针对** `id` 与 `UUID()` 之类；
引用 `superseded_by`（普通列 + 确定性 `IF/CONCAT`）**完全合法**。已改回生成列，`is_active` 列删除。

**② 但 `COALESCE(superseded_by,'0')` 有一个具体冲突，必须加固**
场景：批次 A、B 先后被**同一个**新批次 C 取代 ⇒ 两者 `superseded_by = 'C'` ⇒ `active_key` 都是 `'C'`
⇒ 唯一键 `(provider, biz_date, active_key)` **误伤**（B 插不进或 A 更新失败）。
因此表达式改为 `IF(superseded_by IS NULL, '0', CONCAT(superseded_by,'#',batch_no))`：
有效批次仍是 `'0'`（同时消掉了 `'ACTIVE'` 魔法字符串的值域冲突），被取代的批次拼上自身 `batch_no` 天然唯一。
**配套约束**：`batch_no` 生成器永不产出 `'0'`（否则与有效标记撞车）。

**③ 并发创建批次的 `DuplicateKeyException` 处置：报错 + Job 跳过 + 告警，不静默重试**
两个实例同时建同日批次时，后到者会撞 `uk_active_batch`。定法：
- 仓储层**原样抛出**（包装成业务异常并带 `provider/biz_date/batchNo`），**不吞、不自动换 batch_no 重试**；
- 拉取 Job 捕获后**跳过该 provider/账期**，记一条告警（"已有有效批次"），本轮不重复拉；
- 理由：静默重试会造出"同一天多个批次"的假象，正是唯一键要防的事。

**④ 仓储分两批（第一批已完成：三个 PO + 三个 Mapper）**
- PO：`ReconciliationBatchPO` / `ReconciliationItemPO` / `ReconciliationRawPO`；
  `activeKey` 用 `FieldStrategy.NEVER`（生成列，写它会被数据库拒绝）；
  item 的两个键列在 PO 层**不许写 NULL** —— 空由哨兵承载。
- Mapper：`selectActive(provider, bizDate)`（`superseded_by IS NULL`，唯一性由 `uk_active_batch` 保证）、
  `claimStatus(batchNo, expectedFrom, to)`（条件更新；白名单校验仍在仓储层）、
  item/raw 的 `deleteByBatch` + `countByBatch`（重拉先删后插的幂等断言要用）。
- 装配：`@MapperScan` 已覆盖 `infrastructure.persistence`（含子包），无需额外注册。

**④-b 第二批（未做）**
第一批：三个 PO + 三个 Mapper（纯映射，无事务逻辑）；
第二批：`ReconciliationRepository(+Impl)` 与 H2 用例（含并发 claim、refetch 回滚、无批次、三终态拒重拉、哨兵往返）。

---
### 1.59 第二批开工前的四项确认（2026-10-19）

**① `active_key` 的类型/列宽与 `'0'` 不会冲突（有证明，不是假设）**
`VARCHAR(160)`；值域只有两种形态：
- 有效批次 = `'0'`（两字符）；
- 已取代批次 = `CONCAT(superseded_by, '#', batch_no)`，最长 64+1+64 = 129 ≤ 160，**且必含 `#`**。
⇒ 两种形态**互不相交**（`'0'` 不含 `#`），所以 `'0'` 不可能被真实批次占用；
再叠加"`batch_no` 生成器不得产出 `'0'`"这条约束，唯一键不会误伤。

**②-b H2 生成列支持：已实测（结论见 §1.60），改用可移植写法，不再需要双源 DDL。**本次（早先）未实测成功（本机定位 h2 jar 失败：`settings.xml` 的 `localRepository` 是占位值
`/path/to/local/repo` 并另写一行 `E:\maven\myRepository`，两次 `find` 都没命中）。
**规则照你的要求钉住**：第二批第一步就是实测；
**若 H2 不支持 `GENERATED ... STORED`**，登记「**双源 DDL 差异**」——
即 MySQL 用生成列、H2 用例显式写入该列，**不许**在测试里写死值冒充生成逻辑。

**③ `selectActive` 已与唯一键列对齐**：条件改为 `active_key = '0'`（同列可走索引），
并在注释里写明它与 `superseded_by IS NULL` 等价的理由（见 ① 的值域证明）。

**④ 六个用例的顺序（按依赖排，不按方便排）**
`无批次` → `哨兵往返` → `重拉不翻倍` → `三终态拒重拉` → `并发 claim` → `refetch 回滚`。
**H2 并发不稳定就登记"未验证"，禁止加 `sleep`** —— 加 sleep 只是把不稳定藏起来，不是修好。

**⑤ `refetch` 的入参是 domain 类型，哨兵 encode 只在 Impl**
签名收 `List<WithdrawalReconciliationComparer.Item>` 与原始报文列表；
`ReconciliationSentinels.encode/decode` **只允许出现在 `ReconciliationRepositoryImpl`**，
domain 与调用方永远看到 `null`，看不到 `__NULL__`。

---
### 1.60 H2 生成列实测结论（2026-10-19，h2 2.3.232 + MODE=MySQL，Java 21）

| 写法 | 结果 | 证据 |
|---|---|---|
| `GENERATED ALWAYS AS (IF(...))` | **拒绝**（语法错，`[*]IF`） | `Syntax error ... expected "), INTERSECTS (, NOT, EXISTS, UNIQUE..."` |
| `GENERATED ALWAYS AS (CASE WHEN ... END)` | **接受** | `exit=0` |
| 同上 + `STORED` | **拒绝**（H2 不认识该关键字） | `Syntax error ... [*]STORED` |
| `CASE WHEN` + 插入两行 + `UNIQUE(provider,biz_date,active_key)` | **通过** | `exit=0`，有效行 `'0'`、被取代行 `B1#B0` |

**因此 DDL 统一改成可移植写法**（`CASE WHEN`、不带 `STORED`）：
- MySQL 与 H2 **同一份 DDL**，`Initialize.sql` 仍是唯一结构真相源，**不需要"双源 DDL 差异"**；
- MySQL 侧生成列默认为 `VIRTUAL`；InnoDB 支持在虚拟列上建二级索引与唯一键，故 `uk_active_batch` 成立。
  ⚠ **残留风险（待真库验证）**：若某 MySQL 版本不允许在虚拟生成列上建**唯一**索引，退路是 MySQL 用 `STORED`、
  H2 保持不带（即回到双源），这一条在接真库时必须连 CI 一起确认。

---
### 1.61 第二批开工前的五个细节（2026-10-19，写死）

**① refetch 的"只放行"不能先查后删**
先 `selectStatus` 再 `deleteByBatch` 之间存在并发窗口：别人可能在这期间把批次**推进到闭环**，
我们却把它的 item 删了 ⇒ 已闭环批次被"清空"。
**正确做法**：把判断做进**条件删**（或先 `SELECT ... FOR UPDATE` 锁行）——
删除语句自带
`DELETE FROM reconciliation_item WHERE batch_no = ? AND EXISTS (SELECT 1 FROM reconciliation_batch b
WHERE b.batch_no = ? AND b.superseded_by IS NULL AND b.status IN (1,2))`，
影响行数为 0 即说明"状态已不可重拉"，抛业务异常；或用 `FOR UPDATE` 把批次行锁住再删。

**② raw 分片由调用方做，`refetch` 接 `List<RawShard>`**
分片规则（每片条数、超长单独成片、`seq` 从 1 递增）属**拉取器**的职责；仓储只负责落库与唯一键。
理由：分片策略会随渠道报文形态变化，不该焊在仓储层。

**③ `claimBatch` 的 0 行补查不保证精确**
补查得到的 `actualStatus` 只是**某一时刻的快照**，可能已被下一次更新覆盖。
所以异常信息**以 `expectedFrom` 为主**（"我以为它是 X"），`actualStatus` 明确标注
**"可能为旧值"**，避免排查时把快照当事实。

**④ `refetch` 内部先 `claimBatch`（若当前是 `PENDING`）**
把"检查 + 推进"放进**同一个事务**：`PENDING → FETCHED` 由 `refetch` 自己完成，
而不是让调用方先 claim 再 refetch（两步之间又是一个窗口）。

**⑤ H2 实测结论与"并发未验证"必须落在台账，不只留在对话**
- 实测结论已在 §1.60（`CASE WHEN` 可移植、`IF`/`STORED` 被拒）；
- **并发 claim 用例**：若 H2 下无法稳定复现并发，**登记"未验证"**（禁止 `sleep` 制造假稳定）；
- 本条随第二批一并交付，交接单里的"已验证/未验证"两栏都要按实填写。

---
### 1.62 条件删方案 A 的五项加固（2026-10-19）

**① 删改 SQL 不再硬编码状态值**
条件删里的 `status IN (1, 2)` 换成命名参数 `#{pendingCode}` / `#{fetchedCode}`，
值由 `ReconciliationStatus.PENDING.getCode()` / `FETCHED.getCode()` 传入。
**再加一条枚举值断言用例**（`assertEquals(期望码, PENDING.getCode())` 等）：
一旦有人调号，测试先红，而不是让 SQL 悄悄指向别的状态 —— 这才是防"枚举漂移"的机制。

**② 条件删 0 行后要分清两种原因**
0 行时补查 `selectStatus(batchNo)`：
- 查不到 ⇒ **批次不存在**（抛 `RECONCILIATION_BATCH_NOT_FOUND` 之类）；
- 查到但状态不在放行集合 ⇒ **状态不可重拉**（抛对应业务异常，信息里带实际状态）。
不区分的话，排查时看到的永远是同一句"重拉失败"。

**③ `refetch` 只在 `PENDING` 时调 `claimBatch`**
`FETCHED` 直接删后插。原因：白名单里**没有** `FETCHED → FETCHED` 这一跳
（它本来就不算"推进"），若一律调 claim 就会**误抛**——把合法的重拉拒掉。
（`PENDING` 仍然要 claim：那是真的状态推进，且必须与删插同一事务。）

**④ `RawShard` 的 `seq` 重复在落库前校验**
`refetch` 先校验 `seq` 唯一且从 1 连续，重复/跳号直接抛明确异常（指出重复的 seq 值）。
理由：让它撞数据库唯一键，报出来的是 `DuplicateKeyException`，看不出是调用方分片错了。

**⑤ "并发未验证"要写清**未验证的具体行为**，不是"没测"**
写法要求：写明"**两个事务并发对同一 `batch_no` 调 `claimBatch` 时，是否恰好一个成功、另一个拿到 0 行抛并发异常**"
这一条未在 H2 稳定复现（若无法稳定复现则登记此行）。空的"未测试"不算登记。

---
### 1.63 下一轮实现的五项注意（2026-10-19）

**① H2 测试类不加 `@Transactional`**
Spring 测试的 `@Transactional` 会**回滚整个测试**并把事务托管给测试框架；那样 `refetch` 的事务就不是
**真实业务事务**了 —— "重拉不翻倍"这类断言会变得不稳定（看到的是测试事务视图，不是提交后的库状态）。
所以：**测真实事务**，用例自己造数据、自己清理。

**② `refetch` 的 0 行分三种异常，信息必须不同**
- **批次不存在**（`selectStatus` 为空）；
- **状态在这两步之间变了**（查到了、删却没删到 ⇒ 并发推进）；
- **状态直接不可重拉**（查到时就已经不是 `PENDING/FETCHED`）。
三种混用一句话，排查时分不清"传错 batchNo""被人抢先""状态本来就不对"。

**③ `seq` 校验的报错要能定位**
错误信息带上 **"期望 1..N，实际 [..]"**（把实际的 seq 列表打出来），
这样能直接看出是**丢了哪一片**、还是**重复了哪一片**。

**④ `selectStatus` 返回 `Optional`**
与 `findActiveBatch` 一致：查不到是 `Optional.empty()`，不是 `null`，也不是抛异常。
调用方用 `isEmpty()` 分支，避免 `null` 判断散落。

**⑤ 枚举值断言用例放 `architecture-tests`**
`ReconciliationStatus` 的码值是**跨模块契约**（SQL 命名参数、DDL 注释、报表都依赖它），
放模块内测试只保护了本模块；放 architecture-tests 才能在**全仓**层面拦住漂移。

---
### 1.64 本分支的验证口径（2026-10-19）

**结论口径**：`feat/reconciliation-daily` 的验证以 **「排除 item-service 的全量 reactor」** 做局部证明：
`mvn -o -f Implementation/back-end/pom.xml test -pl '!item-service'`。
交接单里固定写两句：**「本分支的绿 = 排除 item-service 的局部证明」+「主干绿待短码修复（§1.45）」** ——
不得写成"主干全绿"，也不得因为一个无关模块红就怀疑本分支。

**短码不阻塞本分支**：它属于 `item-service` 的已提交测试（他人文件），与本分支的对账代码无交集。

---
### 1.65 对账仓储端口已定稿（2026-10-19）

`ReconciliationRepository` 已落（**只声明，无实现**）：
`findActiveBatch`（`Optional`）/ `claimBatch`（白名单 + 条件更新）/ `refetch`（同事务删后插）；
`Batch` / `RawShard` 两个 record；五个可区分原因的异常
（`IllegalReconciliationTransition` / `ConcurrentClaim` / `BatchNotFound` / `StatusChanged` / `NotRefetchable` / `DuplicateSeq`）。

**实现时必须守的三条**（已写进接口注释）：哨兵只在实现里；只放行用条件删或锁行；异常消息必须不同。
**未验证**：接口未接实现、未跑 SQL、无测试。

---
### 1.66 守卫的写法要点与**变异验证**要求（2026-10-19）

**① `JavaSourceParser` 的可用 API（已核，不用再猜）**
`mainSources()` → `List<Path>`；`parse(Path)` → `CompilationUnit`；`methods(CompilationUnit)` → `List<MethodDeclaration>`；
`body(MethodDeclaration)` → `String`；另有 `hasAnnotation` / `typeHasAnnotation` / `typeNames` / `hasNoBody`。
参考写法见 `FundTransactionGuardTest`（`mainSources` 遍历 + `parse` + `methods` + `body`）。
⇒ `architecture-tests` **没有** trade-service 依赖，所以守卫只能**读源码**，不能导入 `ReconciliationStatus`。

**② 两个守卫的判据**
- `ReconciliationStatusContractGuardTest`：断言枚举声明的码值恰为 `PENDING=1 / FETCHED=2 / MATCHED=3 / DIFF=4 / RESOLVED=5`；
- `ReconciliationMapperNoHardcodedStatusGuardTest`：断言 `ReconciliationBatchMapper` 的 SQL 里不出现
  `status IN (数字` 或 `status = 数字` 这类硬编码，必须走 `#{pendingCode}` / `#{fetchedCode}`。

**③ 两个守卫都必须做变异验证（硬要求）**
只写不验的守卫，和"检查器永远绿"是同一类东西。每条守卫写完要**亲手把它弄红**：
- 码值守卫：临时把 `FETCHED(2, …)` 改成 `FETCHED(9, …)` ⇒ 守卫必须红；然后改回；
- 硬编码守卫：临时把 `#{pendingCode}` 改成 `1` ⇒ 守卫必须红；然后改回。
**变异验证的结果要写进提交信息**（"临时改 X ⇒ 红 ⇒ 已还原"），否则无法区分"守卫有效"与"守卫空转"。

---
### 1.67 两个对账守卫已落 + 变异验证通过（补记，2026-10-19）

| 守卫 | 判据 | 变异验证 |
|---|---|---|
| `ReconciliationStatusContractGuardTest` | 读源码断言 `PENDING=1 / FETCHED=2 / MATCHED=3 / DIFF=4 / RESOLVED=5` | `FETCHED(2, …)` → `FETCHED(9, …)` ⇒ **红**（`expected: <2> but was: <9>`）⇒ 已还原 |
| `ReconciliationMapperNoHardcodedStatusGuardTest` | 只查 `Reconciliation*Mapper`，禁止 `status = 数字` / `status IN (数字` | `status = #{expectedFrom}` → `status = 1` ⇒ **红**（点名该文件）⇒ 已还原 |

还原后复跑两条均绿；两条都带「扫描面为空直接红」，防空转。

**两条实现细节（别再踩）**
1. `architecture-tests` **无** trade-service 依赖 ⇒ 守卫只能读源码，不能导入枚举；
2. 正则**不要写反斜杠转义**（`\s`/`\d` 多层转义后会变非法字符），用字符类 `[ ]*` / `[(]?` / `[0-9]`。

**另一条过程事实**：变异后用 `git checkout --` 还原文件，mtime 变新 ⇒ **陈旧检查器拦截提交**（"源码比 class 新"）。
这是它应有的行为，先 `mvn -DskipTests compile` 再提交。

---
### 1.68 条件删已落（仓储第一批，2026-10-19）

`ReconciliationItemMapper.deleteByBatchIfRefetchable` 与 `ReconciliationRawMapper.deleteByBatchIfRefetchable`：
- 删除与状态判断写在**同一条 DELETE** 的 `EXISTS` 子查询里 ⇒ 先查后删的并发窗口消失；
- 状态值走命名参数 `#{pendingCode}` / `#{fetchedCode}`（由 `ReconciliationStatus.getCode()` 传入）；
- 逐笔与分片用**同一判据**，两边要么都删、要么都不动。
- 返回 0 行的语义留给实现区分三件事（批次不存在 / 两步间状态已变 / 状态本不可重拉）。

**验证**：编译通过；`ReconciliationMapperNoHardcodedStatusGuardTest` 对新增 SQL 判绿（它现在有真实内容可守，不再只是空跑）。

**下一步**：`ReconciliationRepositoryImpl`（`findActiveBatch` → `claimBatch` → `refetch`）+ H2 六用例。

---
### 1.69 仓储实现落地（2026-10-19）

`ReconciliationRepositoryImpl`（三个 mapper 注入，哨兵只在此类出现）：

| 方法 | 行为 |
|---|---|
| `findActiveBatch` | `selectActive`（`active_key='0'`）→ `Optional` |
| `claimBatch` | 先 `canTransit` 白名单（非法抛 `IllegalReconciliationTransition`）→ 条件更新；0 行补查 `selectStatus` 抛 `ConcurrentClaim`（`expectedFrom` 为主、快照标注"可能为旧值"；批次不存在时快照为 null） |
| `refetch` | `@Transactional`；seq 预校验 → `PENDING` 先 claim、`FETCHED` 直接删插、其余抛 `NotRefetchable` → **条件删**（逐笔/分片同判据）→ 0 行再判一次：仍可重拉 = 本来就空（首次拉取），否则 `StatusChanged` → 插入（哨兵编码在此） |

**一个易错点（已处理）**：首次拉取时逐笔表本来就是空的，条件删必然返回 0 行 ——
所以 0 行**不能**直接判"状态变了"，必须再查一次状态：仍可重拉 ⇒ 视为空，继续插入。

**单测（mapper 全桩，6 用例绿）**：非法迁移不碰库 / 0 行并发异常带 `expectedFrom` 与快照 /
批次不存在 / 已闭环不可重拉且不删不插 / seq 重复在任何库操作前就抛 / `PENDING` 先 claim 且空值编码成哨兵。

**未验证**（归 H2 用例）：SQL 正确性、事务回滚、并发真实行为。

---
### 1.70 H2 开工前的五项确认（2026-10-19）

**① `findActiveBatch` 与 `FETCHED` 分支：当前**无测试覆盖**（登记，不许当成已验证）**
现有 6 个单测覆盖的是：非法迁移 / 0 行并发 / 批次不存在 / 已闭环 / seq 重复 / `PENDING` 先 claim。
**未覆盖**：`findActiveBatch`（含 `Optional.empty` 分支）、`refetch` 走 `FETCHED`（**不调 claimBatch**）那条路径。
⇒ H2 一批必须补上这两条，否则"没测"和"测了通过"在台账里会混淆。

**② 两份条件删的一致性：靠"同判据"口头约定，尚未有机器检查**
`ReconciliationItemMapper` 与 `ReconciliationRawMapper` 的 `deleteByBatchIfRefetchable` 目前判据一致，
但两份 SQL 是**各写一遍**，将来改一处忘另一处就会"逐笔删了、原文还在"。
**建议**：H2 批里加一条源码守卫（两份 SQL 的 `EXISTS` 片段必须逐字相同），或把条件删抽成一张视图/一条 SQL 两份语句共享 —— 二选一，别只靠约定。

**③ `claimBatch` 自调用：守卫已跑，绿**
`refetch`（`@Transactional`）调 `claimBatch`（非事务）属**跨方法自调用但不跨事务边界**：
外层事务覆盖内层，语义正确。`TransactionSelfInvocationGuardTest` 实跑 6 通过（0 失败）。
**注意**：若将来给 `claimBatch` 加 `@Transactional`，它就会变成"事务方法被同类事务方法自调用"——
守卫仍绿（调用方也是事务），但**别给它加 `REQUIRES_NEW`**，那会切开外层事务。

**④ 并发用例的 H2 稳定性预期：先按"大概率不稳"准备**
H2 内存库 + 两线程并发 `claimBatch` 很难稳定复现"恰好一个成功"。
**约定**：能稳定复现就写用例；不能则按 §1.62⑤ 的格式登记
（"两事务并发 claim 同一批次时，是否恰好一个成功、另一个拿到 0 行 —— 未能在 H2 稳定复现"），
**禁止 `sleep` 制造假稳定**。

**⑤ `-pl '!item-service'` 与 architecture-tests：不冲突，但要分清两种依赖**
- 架构守卫多数**读源码**（`mainSources()` 扫磁盘文件），排除模块不影响它们读 `item-service` 的源码；
- 少数守卫读**已编译 class**（如 `ArchitectureTest.importAll` 依赖 `ia-common/target/classes`）——
  这些模块仍在 reactor 内，照常构建。
⇒ 局部证明的前提是**其它模块先编译过**（本机已有 target）；全新检出上跑会因缺 class 报错，
这与 §1.41②「陈旧/缺失 target」是同一类问题，记在同一条口径下。

---
### 1.71 条件删判据一致性守卫（2026-10-19）

`ReconciliationConditionalDeleteConsistencyGuardTest`：把 `ReconciliationItemMapper` 与
`ReconciliationRawMapper` 的 `EXISTS` 判据（去空白后）**逐字比较**，不一致即红。

**变异验证**：把 Raw 的 `#{fetchedCode}` 改成 `#{fetched}` ⇒ **红**（差异两串原文都打出来了）
⇒ 已还原 ⇒ 复跑绿。这条守卫现在不再依赖"我记得两处一起改"。

实现细节：不要用 `replace("
","")` 去空白（多层转义会把字符串写坏，本轮就栽过一次），
改用 `String.join("", Files.readAllLines(file)).replace(" ", "")` —— 无反斜杠，稳。

---
### 1.72 H2 测试类的五条前置（2026-10-19，开工即写进类注释）

**① 初始化方式：照 `SettlementFundAccountH2Test` —— 手写 DDL 子集，不是整文件重放**
已 grep 确认：该用例**不读** `Initialize.sql`，而是内联手写四张表的 DDL（`CREATE TABLE settlement /
seller_fund_account / withdrawal / withdrawal_settlement`），并在类注释里写明「**H2 schema 是手写子集**，
结构真源仍是 `Initialize.sql`；两者不一致时以真源为准并同步本文件」。
⇒ 新的 H2 用例同样**手写三张 reconciliation_* 表的 DDL 子集**，并继承同一条同步义务：
**改了 `Initialize.sql` 就要回改测试 DDL**（这是一处已知的双源，登记在案，不用自己发明第三种做法）。

**② 第一个断言就验生成列的类型**
建表后立刻断言 `active_key` 是**字符串 `'0'`**（不是数字 0、不是 null）：
`SELECT active_key FROM reconciliation_batch WHERE …` 取出来 `assertEquals("0", …)`。
理由：H2/MySQL 在 `CASE WHEN … THEN '0'` 上如果被当成数值，后续唯一键比较会走数值语义，
**尽早暴露比在差异报表里发现错位便宜得多**。

**③ 并发用例写"无法构造的时序"，不是"没测"**
若 H2 下无法稳定构造两事务交错，注释要写清**具体构造不出来的时序**：
「需要 T1 在 `selectStatus` 之后、`deleteByBatchIfRefetchable` 之前被挂起，H2 单连接无法让 T2 在同表同批次上推进，
因此无法构造『查询后状态被改』这个窗口」。**禁止 `sleep`** 制造假稳定。

**④ FETCHED 分支由"重拉不翻倍"覆盖（把登记改准确）**
§1.70① 曾把「`refetch` 的 FETCHED 分支」登记为未覆盖 —— 更正：
该分支由 **重拉不翻倍** 用例覆盖（第二次拉取时批次已是 `FETCHED`，正好走"不调 claim、直接删后插"的路径）。
**仍真未覆盖**的只有 `findActiveBatch` 的 `Optional.empty` 与命中两种情况。

**⑤ 回滚用例用**超长 `channel_ref`** 触发，不用哨兵值**
用超过列宽（160）的 `channelRef` 让插入失败，从而验证"同事务先删后插"整体回滚、**旧数据还在**。
不要用哨兵值触发 —— `encode("__NULL__")` 抛的是**调用前**的校验异常，根本走不到数据库，
证明不了事务回滚。

---
### 1.73 写 H2 用例时的五条纪律（2026-10-19）

**① DDL 子集要包含全部约束 —— 裁剪的是"表"，不是"约束"**
手写子集时**必须**保留：`NOT NULL`（`payout_no`/`channel_ref`）、`UNIQUE(batch_no)`、
`UNIQUE(provider, biz_date, active_key)`、生成列（`CASE WHEN` 形态）、列宽（`VARCHAR(160)` 等）、
`UNIQUE(batch_no, seq)`、`UNIQUE(batch_no, payout_no, channel_ref)`。
**理由**：这些约束正是被测行为（唯一键去重、NOT NULL 拒绝漏编码、生成列取值、超长拒绝）。
省掉任何一条，用例就变成"在更宽松的库上通过"，绿得毫无意义。

**② 每个用例用独立 `provider`/`bizDate` 隔离，不用 `@BeforeEach` 清理**
清理会引入"清理本身是否成功"的新变量，且 `@BeforeEach` 与事务/连接复用的交互容易出错。
用**数据隔离**（每个用例自己的 provider + 账期）最省心，也最接近真实多租户场景。

**③ H2 连接模式照抄 `SettlementFundAccountH2Test`，不发明第三种**
该用例已经解决了：URL 参数（`MODE=MySQL` 等）、`SimpleDriverDataSource`、`MybatisSqlSessionFactoryBean` 装配、
共用 `SqlSession`（对应生产"一事务一 session"）。**照抄**，不要自己拼第二套。

**④ 三态拒重拉的三个用例各自造批次，不共用一个再改状态**
共用一个批次再改状态，等于让用例之间产生隐式依赖：一个用例失败会连带影响后面的，
且"状态是被人为改成 MATCHED 的"还是"跑出来的"分不清。**各自造、各自断言。**

**⑤ 回滚用例断言"批次状态回滚到 `PENDING`"，而不只是"旧数据还在"**
这一条验证的是 **`refetch` 内部的 `claimBatch` 与外层 `@Transactional` 同源**：
如果内层 claim 用了独立事务（例如加 `REQUIRES_NEW`），插入失败时批次会停在 `FETCHED` 而逐笔没插进去 ——
断言到 `PENDING` 才能抓住这种"事务被切开"的错误。

---
### 1.74 回滚用例的处置（2026-10-19，取证后决定）

**取证结论：harness 不支持回滚，代码里已写明**
`SettlementFundAccountH2Test` 第 960 行原文：
「⚠ 本 harness 只能验『提交』，验不了『回滚』：这里没有 Spring 事务管理器，
实测 `SqlSession.rollback()` 不会真的回滚（状态迁移会留在库里）」。
辅助方法 `inTx()` 虽然 `catch` 里调了 `session().rollback()`，但**实测无效**。

⇒ 若照原计划写「refetch 抛异常后断言批次回到 `PENDING`」，红的会是 **harness 不支持**，不是业务错。

**处置：回滚用例改为「验证失败时没有部分提交」，不依赖回滚语义**
做法：
1. 先造一批旧数据并**显式提交**（`inTx`）；
2. 触发失败（用超过列宽 160 的 `channel_ref`）；
3. 异常之后用**另开 JDBC 连接**查询（harness 里 `intOf(...)` 就是这种连接）：
   - `reconciliation_item` 的旧行数不变；
   - `reconciliation_batch.status` 仍是 `PENDING`。
4. 判据写清：**未提交的写入对独立连接不可见** ⇒ 看到旧值 = 没有部分提交。
   这验证的是业务真正需要的东西（失败不留半截），而不是"数据库回滚 API 生效"。

**同一条纪律的推广**：凡是"回滚"类断言，在这个 harness 里都只能这样写；
要真验回滚，必须上 `@SpringBootTest` + `DataSourceTransactionManager`（§1.32 已登记为未覆盖项）。

---
### 1.75 写 H2 用例前的三项确认（2026-10-19）

**① `inTx()` 的异常路径**不 `commit`**（已核源码）**
```java
try { result = op.get(); session().commit(); return result; }
catch (RuntimeException e) { session().rollback(); throw e; }
```
异常时只调 `rollback()`（**实测无效**）、**不会 commit** ⇒ 数据留在会话里未提交。
所以回滚用例**可以直接用 `inTx()`**：只要异常向上抛出，独立连接就看不到那些写入。
（前提是下一步的"释放锁"。）

**② 异常之后必须释放会话，否则独立连接会锁超时**
未提交的写会**持有行锁**；`SqlSession.rollback()` 又不生效 ⇒ 锁不释放。
用例在异常后、用独立连接查之前，必须先：
`session().getConnection().rollback()`（JDBC 层真回滚，顺带释放锁）或 `session().close()` 后重开。
**不做这一步，`intOf(...)` 会卡在锁等待上**，红得莫名其妙。这条要写进用例注释。

**③ `active_key == '0'` 的首断言放在"有批次"的用例里**
"无批次"用例根本不插批次，拿不到 `active_key`。
类型断言（`assertEquals("0", …)`）放进 `findActiveBatch` **命中**的那条用例，建完批次立刻断言。

**④ 注释要说清 `findActiveBatch` **命中分支**由谁覆盖**
建议放在"哨兵往返"或"重拉不翻倍"里顺带断言（那条用例本来就有批次），并在类注释里点名
「`findActiveBatch` 命中分支由 X 用例覆盖；`Optional.empty` 分支由无批次用例覆盖」——
否则下一个人会以为漏测。

**⑤ 并发用例"等价于串行"，不是真并发**
H2 单连接下用两线程交错通常构造不出真竞争；若用"先 claim 一次再 claim 一次"来模拟，
**注释必须写明**：这是**串行等价验证**（验证第二次拿到 0 行并抛 `ConcurrentClaim`），
**不是**并发正确性证明；真并发仍需独立环境（§1.62⑤ 的具体行为登记照旧）。

---
### 1.76 串行等价与释放锁的两个细节 + active_key 可读性取证（2026-10-19）

**① `getConnection().rollback()` 之后补 `session().close()`（双保险）**
`getConnection().rollback()` 只回滚当前连接上的事务；**session 仍持有该连接**，
若连接池复用连接、且未归还的连接还挂着锁的元数据，独立连接仍可能等锁。
写法：
```java
try { session().getConnection().rollback(); } finally { session().close(); }
```
`close()` 会归还连接；连接池归还时通常 rollback 未提交事务并释放锁。
（若 `SettlementFundAccountH2Test` 里已有"异常后 close"模式，照抄它，不发明第二种。）

**② 串行等价的 claim 冲突：第二次 claim 必须在新 session/新事务里**
若两次 claim 都在同一个 `inTx()` 里，第一次的 `UPDATE` **尚未提交**，第二次在**同一 session** 读到的是自己未提交的更新，
影响行数可能是 0 —— 那模拟的是"**同一事务内重复 claim**"，不是"两个并发事务"。
正确做法：第一次 claim 一个 `inTx()` **提交**（落 `FETCHED`）；两次之间 `session().close()` 或新开；
第二次 claim 在新的 `inTx()` 里执行 ⇒ 看到已提交的 `FETCHED` ⇒ 0 行 ⇒ 抛 `ConcurrentClaim`。
若坚持同一 session 跑，用例名必须改成"同一事务内重复 claim 被拒"，注释写明**这不是并发**。

**③ `active_key == '0'` 能读出来（已取证，无需改断言）**
- `selectActive` 是 `SELECT *`（第 33 行）⇒ 结果集含 `active_key`，MyBatis-Plus 默认按下划线转驼峰映射到 `activeKey`；
- PO 上的 `FieldStrategy.NEVER` **只限制 insert/update**（第 51 行），**不影响读取**。
⇒ 断言 `po.getActiveKey()` 等于字符串 `'0'` 可行，且它验证的正是"生成列在 H2 上按 `CASE WHEN` 算出 `'0'`"。
（若将来改成显式 resultMap，记得把 `active_key` 加进去，否则 PO 字段会静默为 null。）

---
### 1.77 H2 仓储集成尝试的结论与更正（2026-10-19，实测）

**实测（写完后跑，六用例：1 failure + 1 error）**
`ReconciliationRepositoryH2Test` 首跑结果：
- `shouldNotPartiallyCommit_whenInsertFails` —— 断言「旧的逐笔还在」**失败**（expected 1，actual 0）；
- `shouldThrowConcurrentClaim…` —— 在 `seedBatch` 阶段就 `Persistence` 异常（`reopenSession()` 后装配耦合）。
**该文件已删除**，分支回到 26 用例绿的状态（不留红测试）。

**① 更正 §1.74 的处置：这个 harness 连 JDBC 层回滚也不可靠**
按 §1.74 的写法（异常后 `getConnection().rollback()` + `session.close()` + 重开，再用独立连接查），
结果**旧行也不见了** ⇒ 「未提交的写入对独立连接不可见」这个前提在此 harness **不成立**。
⇒ **任何"回滚 / 无部分提交"断言在本 harness 不可实现**。§1.74 的处置（改为验证"无部分提交"）**再次更正**：
此路不通，不要在它上面继续凑。

**② 两条出路（本分支选 B，A 另开批次）**
- **A（另开批次）**：`@SpringBootTest` + `DataSourceTransactionManager` ⇒ 真事务，能验回滚。需要 DB 依赖，属独立批次。
- **B（本分支收尾）**：H2 仓储集成只验**不依赖回滚**的三条：重拉不翻倍（走 `FETCHED` 分支）、
  三态拒重拉（各造批次）、claim 冲突（串行等价）。失败路径与回滚语义留给 A。
  理由：这三条覆盖了本批次真正的新逻辑；回滚是"横切能力"，应一次性在 Spring 批次里解决，而不是在 harness 里造替身。

**③ 静态 session 耦合要修：抽出幂等的 `rebind()`**
`reopenSession()` 之后重造 mapper/repository 的路径必须收敛成一个**幂等**方法：
先关旧 session（若未关）、再开新 session、再重新 `getMapper` 并重建 `repository`。
否则用例顺序一变就炸（本轮 `seedBatch` 的 `Persistence` 异常就是这个耦合）。

**④ 下一轮开局动作**
按 B 实现 H2 集成（三条用例 + `rebind()`），跑通后做局部证明；
A（Spring 批次）单独排期，专门解决回滚与失败路径。

---
### 1.78 H2 仓储集成（成功路径）已通过（2026-10-19）

`ReconciliationRepositoryH2Test` **四用例通过**（真库、真 SQL）：
1. 重拉不翻倍（第二次走 `FETCHED` 分支，不调 claim、直接删后插；分片同样不叠加；空值落库为哨兵）；
2. 三态（`MATCHED`/`DIFF`/`RESOLVED`）各自造批次、各自拒绝重拉；
3. `claim` 串行等价（第一次提交后 `rebind()`，第二次在新 session 拿 0 行抛 `ConcurrentClaim`）；
4. `findActiveBatch`：无批次 `empty`、有批次命中，且生成列 `active_key` 在 H2 上确实算出字符串 `'0'`。

**关键做法**：`rebind()` 放在**每次操作之后**（操作边界），不是 `@BeforeEach` ——
三条用例都依赖"下一次操作看到已提交状态"；只在方法开头 rebind 会读到未提交视图，红在装配而非逻辑。

**覆盖边界（说清楚，别让后来人误读）**
- ✅ 已覆盖：重拉幂等、三态拒重拉、claim 冲突（串行等价）、生成列取值、哨兵落库、`findActiveBatch` 两分支；
- ❌ 未覆盖：**失败路径与事务回滚** —— 本 harness 无法验证（`SqlSession.rollback()` 与
  `getConnection().rollback()` 均无效，§1.77）⇒ 属 **A 批次**（`@SpringBootTest` + `DataSourceTransactionManager`）；
- ❌ 未覆盖：**真并发**（本条是串行等价，不是并发正确性证明）。

**验证口径（精确表述）**：本分支现状 = **单测 26 绿 + H2 集成 4 绿（仅成功路径）**；
回滚与失败路径未验证；主干绿仍待短码修复（§1.45）。

---
---

## 2. 待办

### 2.1 在跑
（当前无 agent 在跑）

#### F 批：**已收口**（工厂改造由并行会话提交 `2d93730b`；门禁与补偿由本批补齐，见 §1.10）
- 30 个降级工厂**全部**按 `category` 分流。**不要再重新定级**——定级已进各自类注释 + 语义门禁的处置表。
- 上一轮**唯一的真实教训**保留在此（细节已进代码/注释/`package-info`，不必再摸索）：
  1. `UpstreamFailures.classify(Throwable)` 返回 **`ErrorCategory`**（不是 `ErrorInfo`）；`UpstreamException.findIn(cause)` 可空；
     fail-closed 承载是 **`ErrorCode.SYSTEM_BUSY`**（503/TRANSIENT/重试），**不是** `BUSINESS_EXECUTION_EXCEPTION`（3000 / **200** / RULE / 不可重试）。
  2. **不要给子 agent 派「先读 17 个参照文件」的活**（上下文必爆）。本批验证有效的派单形态：
     「事实（已实测的数字/路径/API）+ 逐字模板 + 允许改的文件白名单 + 精确构建命令 + 要求贴原始输出」，
     并让两个 agent 各占**互不重叠**的文件集（pay-service+database / ia-api 测试+architecture-tests）。

### 2.2 排队（按既定顺序）
> **优先级（2026-10-19 用户定，覆盖下表旧顺序）**：**T-5b 商家端权限（截止 2026-11-15，签完即做）> T-SIGN 签到幂等（已知在坏）> D1 数据权限范围文档（若做，必须带 `SellerScope` 静态断言，不能只写文档）> B1 拆类（截止 2026-12-31）**。
> 依据：5b 有硬 deadline 且是**已登记的安全缺口**；D1/B1 无外部期限；T-SIGN 属「数据会丢」类缺陷但无 deadline。

| 序 | 批 | 内容 |
|---|---|---|
| ~~—~~ | ~~**补偿重试**~~ | ✅ **已完成**（批次 G）：`InvoiceLinkCompensationRetryManager` + XXL/`@Scheduled` 双入口，4xx 短路为永久失败，见 §1.11 |
| ~~1~~ | ~~**D（拆类还债）**~~ | ✅ **已完成（§1.18.3）**：`PayManager` / `RefundManager` 按**用例族**拆出 `ChannelCallbackManager` + `TradeStatusNotifier`，回到硬约束内；两条基线条目已按「还清即删」移除。**剩余的超限类仍在基线里（只受「不许增长 / 陈旧即红 / 到期即红」约束）**——它们的拆法是下一批，判据见 §1.18.3（按用例族拆，不按行数砍） |
| 1 | **H** | 小程序**仅文档**（用户已裁决：本轮完全排除代码）。⚠ 已有一批**未提交**的字段名修正在工作区（来自并行会话），不是本批产物，别当成已完成 |
| 2 | **Web 角色端开发** | seller / admin 两个 app（实测：**0 处 API 调用**，仅 Dockerfile 引用 `packages/api`；共 9 个源文件）。用户定的优先级：**Web 各角色平台先补完**。<br>**测量已交付**：三张表（端点→页面 / 页面→端点 / 端点→权限位）+ 缺口分解见 `frontend/23-Frontend-Platform-Gap-Matrix.md`——**别再引用「65 admin / 30 seller」这种数字**（口径不可复现，见该文 §2）。<br>**S0（基座）取证结论**：全仓 + git 历史**找不到取消记录**（唯一提及是本文件，无理由/时间/决策人；`git log -S"基座"` 只有引入那一次，`apps/admin|seller` 全历史零文件删除 ⇒ S0 从未以代码形态落地）。**因此 S0 的范围不能靠"当初为什么取消"来定，只能靠测量结果定。** |

### 2.2b 本轮新增任务（2026-10-19）

| 编号 | 任务 | 内容与验收 | 依赖 / 期限 |
|---|---|---|---|
| ~~**T-5b**~~ | ✅ **已完成（§1.22）**：商家端权限接线（`/api/seller/**` 全量），EXEMPT 清零 | 按 `rbac-permission-matrix-draft.md` §4.2b 逐端点挂位；**接线与删 EXEMPT 同一个 commit**；验收 = 四个控制器转强制 + 无残留豁免 + 每映射方法都有 `@PreAuthorize` | **截止 2026-11-15**；等用户签 §4.2b（只需签两类判断） |
| **T-WITHDRAW** | 提现缺口（=超额提现 + `mark-all` 吞余额 + 缺 `PENDING→SETTLED` 入口，§1.21/§1.23） | 实施计划与验收口径见 **§1.24b**；**第一步（§1.25）与第二步（§1.28）均已落码并跑通 0→4**；**第三步已落码并跑绿（§1.30）**。裁决：`seller_fund_account` 做成**权威**（唯一写入者 = `settle()` 与打款，且各自同事务），可提现额 = `settled_total − frozen_amount − paid_total`；`frozen_amount` 与 `applyWithdrawal` 预占一起做（第二步）；额度判定**只在申请侧**（审核侧不判）；`settlement.status` **不承载打款事实**（已打款看 `withdrawal`，故 `withdrawal_settlement` 分配表只解决「按金额核销 + 拒绝释放」）；幂等键前端传 `Idempotency-Key` | **与 T-5b 不合批**（用户裁决）；验收 = 第一步自检三条绿（`SettlementFundAccountH2Test`，**非空断言靠自造数据真走 `settle()`**）。⚠ **提现仍不可上线**：§1.26 已交触发入口（调度 + 运维脚本；管理端端点待做、handler 未在 XXL-Job 注册），`frozen_amount` 预占（第二步）已在 §1.28 完成；`withdrawal_settlement` 按金额核销（第三步）已于 §1.30 完成；仍缺 **端点/调度接线** 与 **真实打款渠道** ⇒ **上线须两件齐，缺一不许上** |
| **T-SIGN** | 签到幂等升级（§1.20） | 加 `sign_log` + `uk_user_date`；签到与积分发放进**同一本地事务**；`points_log` 加 `biz_key`+`uk_biz_key`；Redis Bitmap 降级为投影；三条用例（并发只发一次 / 失败可重试补发 / 重复调用语义不变） | 无外部期限；建议排在 D1 之后、B1 之前 |
| **D1** | 数据权限范围文档（**带静态断言**） | 只写文档 + **一条 `SellerScope` 静态断言**（不许只有文档）：断言"按 seller 维度取数的仓储方法必须显式接收 `sellerId` 参数"或等价形态；文档范围 = 维度（本店/本部门/本人）+ 查询层强制注入方式 + 与 RBAC0 的边界 | 用户要求：若做就必须带断言 |
| **B1** | 剩余基线超限类拆分 | 按**用例族**拆（§1.18.3 判据），不按行数砍；还清即删基线条目 | **截止 2026-12-31** |
### 2.3 审计交回、待排期
| 项 | 内容 | 需要谁 |
|---|---|---|
| ~~**U2**~~ | ✅ **已完成（§1.18.5）**：头部三处错事实已订正（软删唯一例外、联合主键例外、四组同名不同型列的 A/B 口径 + 「跨表 join 恒不相等」的已知代价）。判定口径写死为「看有没有 `FOREIGN KEY ... REFERENCES`，不看列名」| 已完成 |
| ~~**U3**~~ | ✅ **已裁决并执行（本批）**：5 张零代码引用的死表**已从 `Initialize.sql` 删除**（`seller_application` / `flash_order_log` / `merchant_reply_template` / `ai_message` / `ai_conversation`；CREATE 块与 DROP 行同步移除，双向零差集）。理由：`flash_order_log` 列注释自称「秒杀幂等+兜底」是**假承诺**（实际靠 Lua + `orders.uk_order_no`）；`ai_*` 已被 `chat_message` 取代；`seller_application` 已被 `seller` 取代。设计意图仍留在 ADR/04（已加裁决注记）| 已完成 |
| **U16** | **网关营销路由「静态已具备、运行时未验证」**：`gateway-service/application.yml:146-149` 已有 `/api/admin/{flash,promotion,task,group-buy}/**` 四族谓词（注释还留着「实测营销端点不可达」的历史说明），但**谓词顺序的运行时行为没测过**（`/api/admin/**` 兜底在其后，顺序错了就会被兜底吃掉）。要验证需起网关 + 路由断言，属运行态。文档已标 🟡，勿改标「已收口」| 中（运行态）|
| ~~**U15**~~ | ✅ **已用门禁封住（S0-pre，2026-10-18）**：**不是「修好了越权」，而是把可利用面钉成构建期失败**。判据（实测）：`after_sale` 表**无 seller_id/shop_id**（只有 `order_no`+`user_id`）；admin 列表 `pageByStatus(status,…)` **只按状态过滤**；写侧 `PUT /{id}/review`、`/{id}/confirm-return` **只按 id 操作**。今天**不可利用** —— `PermissionService.hasPermi` 依次判「user/permissions 为 null → false」（fail-closed）、`*:*:*` 通配、**「roles 含 ROLE_ADMIN」短路**、否则查集合；ROLE_SELLER 只持 `[ai:chat, order:read, product:read, seller:admin]` ⇒ **403**。危险点在于：安全只靠种子少一行 `(3,5)`。故新增 `RbacSeedEscalationGuardTest`（纯函数判定 + 两条合成变异 + **真实文件注入 `(3,5)` 实测变红**）| 已封 |
| ~~**U14**~~ | ✅ **已完成（同批）**：`04-Data-Model.md` 中 5 张死表的逐列小节已清理（§7.2/§7.3 整段删除，§9.1/§10.2 留标题位 + stub 以免断号与外部引用失效；悬空 FK `ai_message.id` 已改指 `chat_message.id`）。连带清掉 `Initialize.sql` 里**上次删表遗留的 6 行孤立节标题注释**与 1 处仍指向已删表的列 COMMENT —— 那些注释正好压在下一张表上方，属主动误导。`quality/20` 的 B3 条目已标收口，并留档「契约 CH6–CH9 仍标 ✅ 却无实现」这个**表侧已删、契约侧未动**的悬空项 | 已完成 |
| ~~**U4**~~ | ✅ **已交付（§1.18.5）**：`scripts/check-schema-keys.sh`（解释器探测包装）+ `.py`。三块报告：`DROP`/`CREATE` 双向差集、四组键型分布、名单 vs 实际类型 + 联合主键清单。**刻意不进 CI**（先当排障工具；是否升门禁按「这条规则会不会漂」再定）| 已完成 |
| **U5** | 2 处缺索引（`sys_user_role.role_id` 非前导、`refund_order(status,create_time)`）+ 7 处冗余索引 + 6 处疑似死索引 | 低优先，**不建议现在删** |
| ~~**U6**~~ | ✅ **已记 ADR-067（§1.18.6）**：三条判据 + 一次自我纠正（原「恰好没踩坑 / 对的但是偶然的」**是错的** —— 它是**必须**不带，属承重设计）| 已完成 |
| ~~**U7**~~ | ✅ **已裁决并执行（本批）**：`database/seed-data.js` **已退役删除** —— 它与 `Initialize.sql` 的种子写同一个 `USERNAME/'admin'` 唯一键、口令同为一个明文弱口令，且业务 ID 仍是 ADR-050 前的非 hex 形态 ⇒ 种子真源统一为 `Initialize.sql`。`20-Doc-Code-Consistency-Audit.md` 中「目录只有 Initialize.sql + seed-data.js + middleware」的事实已同步订正 | 已完成 |
| ~~**U8**~~ | ✅ **已改（§1.18.5）**：写死的过时表数改为**指向取数命令**（写死的计数必然漂）| 已完成 |
| **U9** | 通知 `type` **无枚举化真源**（4 个服务各持私有常量，10 个值）→ 建议收口为 `ia-api` 共享枚举 | 架构，需立项 |
| ~~**U11**~~ | ✅ **已改（本批）**：`confirmPaid` 的「**支付单**不存在」用错成 `ORDER_NOT_FOUND(3009，「订单不存在」)` → 改 `NO_RESOURCE_FOUND(1103)`（同为 404 / CLIENT，调用方零感知，与 R2 的 I40/AD43/PAY6/I55 同口径）。`initiatePayment` / `initiateJsapiPayment` 的「订单不存在」**保留 3009**（语义正确） | 已完成 |
| **U12** | `[历史]` 审计快照：若干部份 spu 表无 `short_code` 列、部分 sku 表无 `sku_id` 列（逐表清单见 U1 的 H2 豁免记录）⇒ 这些 H2 表无法补唯一键。若日后参与写入用例，必须补列+补键 | 低（目前只读）|
| **U13** | `FavoriteSpuBriefQueryImplH2Test` 的 `spu_id` 约束是**匿名**（H2 自动名），键在但名与生产 `uk_spu_id` 不同 | 低 |
| **U10** | 契约 §二 覆盖率数字（98/40/58 → 实测 99/52）—— **A/B/C/E 批的 E 项就是删这些数字** | 在 R2 批内 |
| **U17** | **`void` 降级方法的兜底口径（方案 A：第一步已完成，见 §1.16；第二步待做）**。问题：语义门禁对「没抛异常」只做 `returnedDegradedValue++` 计数、**零校验**，而 `void` 的静默返回在调用方看来与真成功**完全同形**（正是 §1.10 里 `TradeInvoiceClientFallback` 那次事故的形态）。<br>**重要修正（M8 实测）**：光改 throw 不够 —— 处置表的 `THROWS` 只约束**不可用**分支，**4xx 分支的静默对 `void` 不可见**。故已补 `VOID_CLIENT_SILENT_ALLOWED`（当前为空）+ 双向陈旧检查（§1.16）。<br>**剩余（第二步）**：① `revokeUserTokens` 需先给 `UserAdminManager#updateStatus:110` 补调用点防护再改抛；② 补偿消费者 `operation_log` 对账（选型已定，前置见下）；③ 前端兜底分支见 U19。<br>**做法（不用手写控制流 lint）**：「能否抛」能从**类型**上强制就不做检查 —— 让该抛的方法 `throw` 复用现成的载体（`CarriedErrorException`），再加 ArchUnit 防回归。白名单若要求「日志 + 补偿记录 + 告警」，那三条**必须能被机器验**，否则又是会漂的注释。<br>**调用点已 grep（实测，别重查）**：<br>· `AfterSaleClientFallback#markRefunded` → `pay-service PayEventNotifier#writeBackAfterSaleRefunded:106-111` **有 `try/catch(Exception)`** ⇒ 已改 throw；<br>· `KgClientFallback#deleteEdges` → `item-service KgEdgeIngestManager#deleteEdgesSafely:179-181` **自有 try/catch** ⇒ 已改 throw；<br>· `AuthContextClientFallback#revokeUserTokens` → `user-service UserAdminManager#updateStatus:110` **无 try/catch**，且 DB 状态**已提交** ⇒ 直接改 throw 会让「已提交的禁用」对外报 5xx，**必须同步补调用点防护**（第二步）。<br>**两条硬约束**：① `revokeUserTokens` 若落补偿记录，**必须有真实消费者**（重试任务或管理端收敛入口）—— 否则就是造僵尸数据（`invoice_link_compensation` 那条教训）；② **删过时注释、留锚点**（已执行：`AfterSaleClientFallback` 的过时方法注释已删，改留锚点指向 `PayEventNotifier#writeBackAfterSaleRefunded`） | 第一步 ✅ / 第二步待做 |
| ~~**U18**~~ | ✅ **无需另做**：「9 个 fallback 的 CLIENT 分支回归测试」这一项已被 §1.10 的**发现式**语义门禁覆盖 —— `FallbackDegradeContractTest` 扫描 `target/classes` 自动发现全部工厂（不维护类名清单），逐个方法验证 CLIENT 分支。**不要再手写逐工厂的 CLIENT 用例**：那既冗余，也会在新增工厂时静默漏测（清单必然漂） | 已覆盖 |
| ~~**U19**~~ | ✅ **已收（§1.18.4）**：结论是**代码本来就对、缺的是用例** —— 补 `front-end/scripts/check-error-fallback.mjs`（直接 import 真实 TS，同源）并进 CI；M15 变异证明会咬。以下为原始问题描述（留档）：**前端兜底分支（随 U17 一起收）**：契约 §二 新增「下游透传」规则后，`/api/**` **可能出现不在 §2.1a 清单里的 4xx**（下游 409/422/429、非标准 499）。前端现状是「按已知码分支 + 其余落到通用提示」，需确认两件事：① **未登记的 4xx 必须落通用兜底**（通用错误 + 直显 `msg`），**不得**因为「状态码不在清单里」就走「服务异常」话术；② `check-error-codes.mjs` 当前报「未覆盖 0」是按**业务码**核对的（下游码也在本域 `ErrorCode` 里，所以仍覆盖），但**状态码维度没有门禁** ⇒ 前端若按状态码分支会漏。**不要**为此新增状态码白名单（会变成第二套映射），只补兜底分支 + 一条用例 | 随 U17 |

#### U17 的完成定义（DoD）—— **catch 体审计是完成条件，不是附加项**

**为什么必须写进 DoD**：把 `void` 从「fallback 里静默 return」改成「抛出」，如果调用点的 `catch` 只是空着或只打一行日志，**静默只是从 fallback 挪到了 catch**，什么都没改变。故第一步的完成定义是四件事：

1. **改 throw**（复用现成载体，不新造异常）：
   - `AfterSaleClientFallback#markRefunded`、`KgClientFallback#deleteEdges`（调用点已有 try/catch）
   - **同步改语义门禁的处置表**：`AfterSaleClientFallback` 工厂级 `RETURNS → THROWS`；
     `KgClientFallback` 走 `METHOD_DISPOSITIONS` 单方法覆盖（同工厂另有必须 fail-open 的方法）。
     **不改表就不是完成** —— 语义门禁当前会把「没抛」当既定豁免计数，不改它就等于门禁看不见这次改动。
   - **⚠ 但改表只完成一半**（M8 实测）：处置表的 `THROWS` **只约束「不可用」那一支**，
     4xx 分支的静默对 `void` 方法**不可见**。故必须同时有 `VOID_CLIENT_SILENT_ALLOWED` 那条双向检查，
     否则「把 throw 改回 return」的回归不会被任何用例咬住。
2. **删过时注释、留锚点**：`AfterSaleClientFallback` 的方法注释说「一律不抛…抛错会把成功的退款判成失败」，
   而类注释说「改抛异常后异常被 catch 接住」—— 实测调用点有 try/catch ⇒ **方法注释过时**，
   删掉并留一行指向 `PayEventNotifier#writeBackAfterSaleRefunded` 的锚点，避免下次又有人照它写反。
3. **两个 catch 体审计**（本条即完成条件）：逐个回答三个问题，任一不满足就要补或登记：
   - ① **是否吞掉**：必须至少 WARN，且带**可定位键**（单号 / 售后单 id / 用户 id）；
   - ② **是否有可收敛路径**：补偿记录、对账、或管理端人工入口 —— 都没有就必须**明确登记「暂无消费者」**，
     不许用「待对账收敛」这类话术含糊带过（本仓已因这类话术留下过沉默缺口）；
   - ③ **是否还打成功日志**：catch 内**不得**出现「已完成 / 已回写」这类成功文案。
4. **缺的条件进本文件**：`AuthContextClientFallback#revokeUserTokens` 的调用点
   （`UserAdminManager#updateStatus:110`）**无 try/catch** 且 DB 已提交 —— 本条不在第一步做，
   作为「需先补调用点防护」登记（见 U17 行 + 下方补偿选型）。

#### U17 的补偿消费者选型（已定，2026-10-18）

**选定方案 A：复用 `operation_log` 做对账，不新建补偿表。**
`operation_log` 现成列（`Initialize.sql`）：`user_id`（**操作人**）/ `username` / `action` / `target` VARCHAR(100) / `detail` / `ip` / `create_time`。
- 禁用操作先落审计（`transactionTemplate` 已提交），再调 `revokeUserTokens`；
  吊销成功再补一条 `action=REVOKE_USER_TOKENS`、`target=<目标用户ID>`。
- 定时 Job：找 `action=UPDATE_USER_STATUS` 且目标为 FROZEN、超过 N 分钟仍**没有**同 `target` 的
  `REVOKE_USER_TOKENS` 记录 → **重放吊销**（幂等：令牌版本号推进是单调的）→ 补写成功记录。
- **先决条件（动手前要做）**：现在 `OperationLogManager.record(...)` 只收 `detail`，
  `target` 很可能为空，而 `detail` 里是 `targetUserId=7, status=FROZEN` 这种**人读文案**。
  **对着 `detail` 做字符串解析是不可接受的**（§5 判据 2：注释/文案会漂）⇒ 需要给 `record` 增加
  `target` 入参（或新增一个带目标键的 action），让对账**按列检索**而不是按文案猜。
- **为什么不用另外两个方案**：
  - **不新建 `token_revoke_compensation` 表**：`invoice_link_compensation` 那一套是为「多状态重试机」建的；
    吊销的补偿只是「再调一次」，用审计账本对账就够。**新建表最大的风险是造出第二个僵尸数据源**
    —— 而「补偿必须有真实消费者」正是这次要守住的红线。
  - **不能把吊销放进禁用事务**：`Manager` 调 Feign 不得 `@Transactional`（硬约束，见 CLAUDE.md），
    且远端调用入事务会把一次远端抖动放大成事务持有时间。所以「先提交再吊销」的顺序不能改。

**动手前的三条前置（已 grep 实测，2026-10-18）**

1. **`record` 调用点与签名现状**：全仓只有**两处生产调用** —— `UserAdminManager:104`（状态变更）与
   `UserAdminManager:130`（角色变更），另有 `OperationLogManagerTest` 两处。
   签名是 `record(Long userId, String module, String action, String content)`，
   **`target` 从不写入 ⇒ `operation_log.target` 今天恒为 `NULL`**（已核对 `OperationLogManager`
   的 builder 只填 `userId/username/action/detail/ip`）。故对账前必须先给 `record` 增 `target`
   （新增重载或加参数）并改这两处调用点 —— **对账不能建在恒 NULL 的列上**。
2. **`target` 格式定死为「纯业务键字符串」**（如 `"7"`）：
   - **不带** `userId=` 之类的键名前缀，**不带**实体类型前缀；理由：前缀会逼每个消费者各自 split，
     而**列名本身已经声明了语义**；
   - 将来若多实体共用该账本，**加列**区分实体，**不要**在 `target` 里塞复合编码 ——
     那正是 §5 判据 2「注释/字段里复写会漂的结构」的复发形态；
   - `action` 已经是 `MODULE:ACTION` 形态（见 `OperationLogManager` 的 builder），故模块维度**不重复**进 target。
3. **对账判定条件（只按列判定，禁止解析 `detail`）**：
   - **候选集**：存在 `action='USER:DISABLE_USER'` 且 `target=u` 且 `create_time <= now()-N`。
     ⚠ **需要新增 `DISABLE_USER` 这个 action** —— 现状统一是 `UPDATE_USER_STATUS`、而「是禁用还是启用」
     只写在 `detail` 的 `status=FROZEN` 文案里；对着人读文案做判定不可接受（同上判据）；
   - **已完成**：存在 `action='USER:REVOKE_USER_TOKENS'` 且 `target=u` 且 `create_time >= 禁用时间`；
   - **判定**：候选有、已完成无 ⇒ 重放 `revokeUserTokens`（**幂等**：令牌版本号单调推进），成功后写第二条记录；
   - **无状态重试（刻意不建状态机）**：每轮重放所有未成功项，不引入退避状态机 ——
     因为「没有承载就必然会想建表」，而建表正是要避免的僵尸数据源；连续失败超过 N 轮打 **ERROR** 告警交人工。

---

## 3. 已知未修（**明确不算完成**）

| 项 | 后果 |
|---|---|
| ~~**`settle()` 无触发者 ⇒ 生产提现功能中断（上线阻塞项）**~~ → 🟡 **触发入口已交付（2026-10-19，§1.26），但提现整体仍不可上线** | 已交：`SettlementSweeper`（app 层批处理外壳）+ `SettlementSettleJob`（`@XxlJob("settlePendingSettlements")`）+ `scripts/settle-pending.sh`（默认只读、`--trigger` 才触发）+ 契约草案。**收敛掉的旧结论**：「账户恒 0 ⇒ 提现恒拒」不再成立。**仍缺**：① 管理端端点（草案未落契约）；② **XXL-Job 管理端并未注册该 handler**（「有 handler」≠「会定时跑」）；③ 首次真实触发的三段核对未做（只读报告 / 日志条数 / 库内 status 分布）。⇒ 上线前这三条都要补，别把「代码在」读成「已在跑」 |
| **`frozen_amount` 仍是 0，且审核侧不判额度 ⇒ 额度守门只剩申请侧的读校验（第二步）** | 第一步只做「已打款」这一项的扣减，**待审提现不冻结**；同时按裁决 `review` **不再判一次额度**（额度属 `applyWithdrawal`）。⇒ 第一步里唯一的额度守门是 `applyWithdrawal` 的「先读 `availableBalance` 再比」——**不是原子的**（并发/连提两笔仍可能超额）。**结论写死：本步是口径迁移，不是缺陷修复 —— 超额提现仍可能；提现在生产上仍**不可上线**
（触发入口已有调度+脚本但未接调度器、`frozen_amount` 未做、分配表未做），任何口径都不得宣称 T-WITHDRAW 已修复**；第二步的条件预占（`frozen_amount += amount WHERE settled_total - frozen_amount - paid_total >= amount`）才真正堵住。**不要把额度条件临时塞回 `review`**：那会变成「申请通过、打款被拒，而钱已付出」的假拒绝 |
| **自检取数当前只在 SETTLED（改宽是「条件性」动作，不是待办）** | `SettlementFundAccountH2Test` 的两处对账取数用 `status = SETTLED`：**① 数值一致** `settledAmountBySeller()`、**② 覆盖面一致** `settledSellersInLedger()`。第一步没有任何路径把结算单迁出 SETTLED ⇒ 现在准确且更严（若有行被错迁成 PAID_OUT，自检当场红）。**只有当后续步骤仍决定把行标成 PAID_OUT 时**，才必须在同一 commit 里把**两处**都改成 `IN (SETTLED, PAID_OUT)`，漏一处就自检突然红；按本批裁定的状态语义（`SETTLED` = 可提现、打款事实看 `withdrawal`、分配表与行状态解耦）**预期不写 PAID_OUT ⇒ 两处应保持不动**。「结算单列表区分已打款」属读模型（join `withdrawal`），不是改行状态 |
| ~~**资金门禁的新动词只做了静态补表，未跑过**~~ → ✅ **已实跑** | `add` / `increase` / `settle` / `payout` 进 `WRITE_VERB_PREFIXES` 后，门禁类与全量 `mvn test` 均绿（§1.25「验证结果」第 1、2 步），且本批的 `SettlementManager#settle` 已在「≥2 个资金写」集合里被扫到。**残留教训**：`WRITE_VERB_PREFIXES` 仍是新动词的唯一真源 —— 出现「两个资金写但动词不在表里」的写法时门禁依旧瞎，**补动词必须同时补一条合成反例** |
| **`settlement.status` 不承载打款事实（这是语义，不是缺陷）** | `SETTLED` = **可提现**，**不是「未打款」**；**已打款的事实源是 `withdrawal.status = PAID_OUT`**（聚合看 `seller_fund_account.paid_total`）。⇒「结算单列表看不到已打款」**不是缺口**，也**不要**从 `settlement` 反推打款。旧的全量置位（mark-all）已删除且**不打算恢复**；`settlement.status = 3` 保留为历史语义、本批起不再写入。要做「列表区分已打款」= **读模型**（join `withdrawal`），不是改行状态 |
| **H2 用例的 schema 是手写子集（双源必须同步，且断言本身要适配 H2）** | `SettlementFundAccountH2Test` 的三张表 DDL 是**手工副本**（H2 跑不了 MySQL 专有子句），结构真源是 `Initialize.sql` ⇒ 任何列 / 类型 / 唯一键变更**必须同步该用例 DDL**。已加**可执行检查**：`@BeforeAll` 读 `information_schema` 断言关键列与唯一键存在（漏列当场红），不再只靠「记得」。⚠ **首次跑可能因 H2 / MySQL 的 `information_schema` 差异而红**（列名大小写、`constraint_type` 字面量、内联 UNIQUE 是否进 `table_constraints`）—— 红时先分清「断言不兼容」（改断言）还是「schema 真漂了」（同步 DDL），**不许删断言**。已知刻意差异：时间列可空且无 `ON UPDATE`、省略 `ENGINE`/`COLLATE`/`idx_*`、唯一键用表级 `CONSTRAINT`；判据：**行为相关的差异一条都不许有** |
| ~~**T-WITHDRAW 第一步的用例与门禁改动都未执行**~~ → ✅ **已执行（0→4 全绿）** | 曾经的阻塞：shell 整段不可用（`0xC0000142`，进程创建层失败）+ 无测试环境。恢复后按 §1.25 的 0→4 顺序跑通，含本批 H2 用例、两个门禁类、全量 `mvn test`、计数回填与 `verify-gates.sh full`。**留给下次的教训**：本批第一次提交被 pre-commit 钩子拦下（lambda 捕获非 final 局部变量）—— 「只做静态审查」**不能替代编译** |
| **小程序三处死路径（U19 邻项，未做）** | `crosscheck-api-paths.mjs` 把它们列为**非阻塞报告项**：`/api/item/product/page`、`/api/item/product/{v}`、`/api/search/product` —— 都是 ADR-062 把资源段 `/product` 改名 `/spu` 时**只改了后端与 web**、小程序没跟上。另有一层**更深**的不一致：小程序用技术键 `id` 寻址 SPU 详情，而后端 `{spuId}` 已要求**业务键 32-hex**（ADR-062），即「改了路径也还是 400」。所以这不是改三处字符串，属小程序整体对接改造 ⇒ 归入 §2.2 的 H 批（用户已裁决本轮排除代码），**不要**当成顺手小修 |
| **小程序仍用 `categoryId`（驼峰）当查询参数** | 后端 `SpuPageReq` 的线上名是 `category_id`（ADR-056），驼峰被**静默忽略** ⇒ 品类筛选点了没反应。与上一条同源（小程序没跟 ADR-056/062 的线格式改造），同样归 H 批 |
| **工厂/链式 receiver 的资金写不被计数（已登记边界）** | `getRepo().credit(...)` 这类写法门禁**看不见**（receiver 是表达式，判它需类型推导）。当前全仓零命中，由 `shouldHaveNoFactoryChainedFundWrites` 做体检兜住；一旦出现会红并逼出「改写法 or 补类型解析」的决策。**另**：`this.repo.write(...)` 已补上（此前同样漏算）|
| **根文档计数必须来自 clean 构建** | `sync-doc-counts.mjs` 按 surefire 报告聚合；残留报告让总数偏大、半量构建让它偏小，两者都会被当成"实测"。现已有三道防线：残留告警（含 CI 判红 exit 4）、半量下限（实测 < 文档现值 × 0.8 ⇒ exit 4）、CI 侧「先跑测试再判计数」（§1.18.8③）|
| **小程序 17 个文件** | 仍读 `name`、仍打 `/api/item/product/**`、`categoryId` 静默失效 ⇒ **商品名显示空白**；用户已裁决本轮排除，只登记（见 `product/02` §九）|
| **租约 ≠ 分布式锁** | 单次 Feign 调用超过租约时长（120s）仍可能被判过期而并发重调一次，靠下游幂等兜住。彻底消除需 ADR-024 的 `DistributedLock` 或 XXL 分片（跨 Job 决策，未做）|
| **租约到期用应用时钟** | 多实例秒级时钟偏差不影响互斥（≪ 租约时长），但要在 NTP 严重漂移时留意 |
| **联动前置步骤失败不可补偿** | 失败发生在「查支付单 / 比金额」时 `kind` 尚未判定，而 `kind` 是 NOT NULL 幂等锚点 ⇒ 只 warn 不落库（`recordCompensation` 已注释说明，日志明确「未能判定补偿类型」）|
| **CLIENT 判定依赖异常链上的 `UpstreamException`** | `UpstreamFailures.classify` 只看「异常类型 / 已类型化错误体 / 状态码」；若将来有调用方直接抛 4xx 的 `BizException`（没有 `UpstreamException` 包裹），会被归为 `TRANSIENT` 而重试到上限（有界，最终 FAILED_PERMANENT）。当前 trade 4xx 由 fallback 原样抛 `UpstreamException`，故语义正确 |
| **超限类只有两个有到期日** | 基线里 17 个类的 deadline 为空，只受「不许增长 / 陈旧即红」约束 ⇒ 它们可以**永久**超限。若要让硬约束真正生效，需逐个补 deadline（会带来一批必须排期的拆类工作）|
| **13 个方法在 4xx 下不透传** | 属既定豁免（纯旁路 fail-open / 事务已成功后的回写 / verification 用 200+结果码承载）。它们不抛错，故语义门禁只要求「不得改写成 BizException」；**这是设计选择，不是缺陷**，理由在各自类注释 |
| **门禁的变异样本改为普通编译产物** | 原先放在 `architecture-tests/src/test/resources`，测试时用 `javax.tools.JavaCompiler` 现场编译到临时目录 —— **实测 flaky**（单跑绿、整模块跑红：样本有时没被导入，反空断言报「变异样本未被规则选中」）。已改为放在 **ia-api 的测试源**（那里有 ia-common 编译期类路径），守卫用 `importPaths("../ia-api/target/test-classes/.../fixture")` 导入；代价是 architecture-tests 依赖「ia-api 测试类已编译」这一前置（reactor 顺序天然保证，单独跑缺失时有可执行提示）。**连跑 3 次验证确定性** |
| `KgEdgeUpsertCO.srcId: Long` | 最后一个内部契约技术键（ADR-062 §E5）|
| 评价缺「最旧优先」枚举 | 后端无该值；前端已删非法码 `oldest`（该码会被 `fromCode()` **静默回退 newest**）|
| 通知缺多值/分组 `type` 筛选 | 前端逐值取数后合并（正确但多请求）|
| 积分 `type=4/5` | 声明在案、**无生产者** |
| `RefundManager` / `PayManager` 超 300 行 | 硬约束违反；已进棘轮基线并**限时豁免到 2026-11-15**（到期构建红），不再是口头挂账 |
| `MAX_COMPARE_SPU_IDS` / C9 批量比价 | 契约已定义、代码未实现 |
| `ia-api SearchClient` 3 处 `Result<Map>` | Feign 弱类型契约，其 javadoc 自陈"强类型化需 search-service 同批发布" |

---

## 4. 验证边界（**读这份清单的人必须知道**）

| 维度 | 状态 |
|---|---|
| 后端逻辑/编译 | ✅ 全量单测通过（自己跑 `verify-gates.sh full` 或 `mvn clean test` 取数；**必须 JDK 21**，JDK 25 下 JaCoCo 会全仓红）。2026-10-19 本批改动后跑过 `mvn -o test`（root reactor）+ `verify-gates.sh fast`，均绿 |
| 降级透传门禁 | ✅ **两层面均做过变异验证**（M1 改回无条件 `SYSTEM_BUSY` → 语义+结构同时红；M2 fail-closed→fail-open → 语义红；复原后全绿）|
| **4xx 透传到达 HTTP** | ✅ **三层证据**：① 直调 handler（四类映射 + 接缝断言）；② 框架层 `CarriedErrorPassthroughMvcTest`（真实 `ExceptionHandlerExceptionResolver` + MockMvc，含装饰器包装与反向对照）；③ 突变 M5（双保险同时摘掉 → 框架层五条全红）。⚠ 边界：**真实网络/网关仍未验**（Nacos/Redis 不可达，服务起不来）|
| 门禁自证 | ✅ `verify-gates.sh self-test` 会对门禁喂合成违规样本并断言其变红；Java 侧自证（`FallbackDegradeGateSelfTest` / guard fixture / 棘轮四条规则）随 `mvn test` 执行。**2026-10-19 新增四次端到端变异（均按备份复原并复跑绿）**：M13 摘豁免 ⇒ 资金门禁红（假阴性 N=3 现形）；M14 退回未 `normalize()` 的路径 ⇒ 数量断言 + 已知清单同时红；M15 兜底改成「服务异常」⇒ U19 用例红；M16 快照表改存技术键 ⇒ 结构对账 `FAIL` |
| 契约 ⇄ 代码 | ✅ 端点对账（`extract-endpoints.mjs`：matched / codeOnly=0 / docOnly）+ `check-section7`。2026-10-19 手工补跑了一致性六道闸 + 新增的 U19 兜底闸，全部 exit 0（**注意：这七道仍只在 CI 的 `consistency` job，`fast/full` 不含**）|
| 建库脚本 | ✅ 真库实跑过一次、0 错误且幂等（MySQL 8.0.36）；⚠ `[历史]` 那次跑在新增 `invoice_link_compensation` **之前** ⇒ **该表尚未真库实跑**，目前只有 H2 与静态核对 |
| 根文档计数 | ✅ `sync-doc-counts.mjs --write` 是唯一写入口，`--check --strict-tests` 已进 CI 与 pre-commit 钩子。⚠️ **取数必须来自 clean 构建**：surefire 不清理旧报告，删掉测试类后跑增量测试会让总数**反向增长**（2026-10-19 实测：删掉一个含十余条用例的类、补了新语料用例，总数却上涨）。脚本已加**残留报告告警**（报告名在 `target/test-classes` 里找不到对应类 ⇒ 提示先 `mvn clean test`；自证方式：往 reports 目录塞一份假报告，告警必须出现），但它**只告警不改数字** —— 「哪份是陈旧的」不能由脚本猜，猜了就等于把度量变成推断。**可见性**已补齐：GitHub annotation + Job Summary + CI 判红（§1.18.7）|
| **运行态** | ❌ **仍未验证**（2026-10-19 复测：Nacos 不可达、Redis 端口关闭）⇒ **登录链路、网关路由、Feign 调用、限流全程无运行态证据**。⚠️ **MySQL 端口这次是开的**，但**没有凭据**（`~/.my.cnf` 不存在、root 空口令被拒）⇒ `verify-rbac-seed.sh`（E1）**仍在等**。凭据由用户提供且不落仓库 —— **不要靠猜口令来「把 E1 跑绿」**，那即便跑通也不构成证据 |
| **前端构建** | ❌ 未跑 `pnpm build`（环境约束）。仅 `tsc --noEmit` + 4 条静态脚本 |
| **真实第三方** | ❌ 未验（微信支付/支付宝/短信/Geocoding 均为 mock 或未实测）|
| Docker | ❌ daemon 不可用 |

> **"测试绿 ≠ 可用"** —— 本轮唯一一次发现生产级致命缺陷（`out_trade_no` 归属错位）靠的是**读发起侧实际发了什么**，不是任何断言。

### 环境事实
- **JDK**：必须 `JAVA_HOME=/c/Users/18279/.jdks/ms-21.0.10`。**JDK 25 下 JaCoCo 0.8.12 无法 instrument Mockito mock，全仓必红**（曾把它误判为"并发损坏"）。
- **Maven 不在 PATH 上**（本轮实测，省得下次再找）：`/e/maven/apache-maven-3.9.7/bin`，本地仓库 `/e/maven/myRepository`。可用的最小命令：
  ```bash
  export JAVA_HOME=/c/Users/18279/.jdks/ms-21.0.10
  export PATH=$JAVA_HOME/bin:/e/maven/apache-maven-3.9.7/bin:$PATH
  cd Implementation/back-end && mvn -o -pl <module> test      # -o 离线；不要动根目录 clean 去和并行会话抢
  ```
- **改了共享模块（`ia-common` / `ia-api`）之后必须先 `install`，否则门禁会给出误导性的红**（本轮实测，浪费了十分钟）：
  `verify-gates.sh fast` 的 ia-api 步骤跑的是 `-pl ia-api test`（**不含 `ia-common` 的 reactor**），
  它从本地仓库取 `ia-common` 的 **jar**。若新增的类只存在于 `target/classes`、尚未 install，
  症状是 **`TestEngine with ID 'junit-jupiter' failed to discover tests`** —— 一个看起来像
  「测试引擎坏了」的报错，而不是「找不到类」。修法：`mvn -o -pl ia-common -DskipTests install`。
  同理，`mvn -pl ia-common,ia-api test`（reactor 含两者）**绿并不代表** `verify-gates.sh fast` 会绿 ——
  这是「两个入口结论不一致」的又一个实例，**取数一律以门禁入口为准**。
  注意 groupId 是 `org.nolazy`（不是 `org.icedamericanomall`），jar 落地在 `/e/maven/myRepository/org/nolazy/`。
- **「门禁全通过」不覆盖一致性，这是已知盲区**（本轮实测）：契约七节 / 前端死路径 / 路径互校 /
  错误码表 / 查询参数命名 / `codeOnly==0` 这六道闸**只在 CI 的 `consistency` job**，
  `verify-gates.sh fast|full` **都不含**。所以本地跑绿**不等于**一致性通过 —— 改过契约、
  端点数、错误码或前端路径时，必须手工补跑（CI 兜底，但 push 后才发现就晚了）：
  ```bash
  node Implementation/back-end/scripts/check-section7.mjs
  node Implementation/front-end/scripts/check-frontend-api-paths.mjs
  node Implementation/front-end/scripts/crosscheck-api-paths.mjs
  node Implementation/front-end/scripts/check-error-codes.mjs
  node Implementation/front-end/scripts/check-query-param-naming.mjs
  node Implementation/back-end/scripts/extract-endpoints.mjs    # codeOnly 必须为 0
  ```
  是否把这几道并入 `full` 尚未裁决（会让 pre-push 变慢）；**当前结论：记在这里，CI 兜底**。
- **注意 shell 归属**：后台任务若用 `pwsh` 里的 `bash`，可能命中 WSL 的 bash（`/c/...` 在 WSL 下不存在，整条脚本静默全败）。跑 `.sh` 要么用 `ops_bash`（Git Bash），要么显式调 `C:\Program Files\Git\bin\bash.exe`。
- **MySQL**：本机 8.0.36 可用（凭据由用户提供，**不落仓库**）。`iam_smoke` 库已按 `Initialize.sql` 建过（**新增 `invoice_link_compensation` 之后需重建**才能核对表结构）。
- `icedamericano_mall` 现存 **11 张旧名空表**（`order`/`product` 不在新脚本 DROP 清单内，就地重建会残留这 2 张）。

---

## 5. 本轮沉淀的判据（比任务清单更重要）

1. **接缝原则**：缺陷不在"断言写错"，而在**没有断言站在「A 交给 B」那一行**。只要测试给 A 和 B 各喂一份自己编的数据，接缝就永远不被触碰。已发生 5 次（`SkuCO.productName`、`cartItemId`、`categoryId`、`out_trade_no`、`Result<Map>` 的 Map 字面键）。
2. **注释不得复写会漂的清单**（枚举值/字段名/路径/计数）。已发生 3 次事故：`notification.type` 列注释 → 前端 4 个标签页恒空；`ProductVO.name` 教义；`backend/02` "路径刻意不变"。
3. **门禁过重会逼人删断言**。所以：门禁必须便宜易修；**"删除门禁"要是一条合法且有记录的正路**；能从**类型**上强制的（`Result<VO>`）就绝不做成检查；白名单必须"陈旧即失败"。
4. **测量的可信度低于结论**。本轮我自己的核验手段出错 ≥6 次（grep 不排除 javadoc、`^CREATE TABLE` 漏缩进、MySQL 输出 CR 未剥、`$?` 被二次展开污染、正则漏转义、验收标准算术不可满足）。**凡是"我核验过"，都要能给出命令与原始输出。**
5. **「跑一遍全绿」不能证明门禁有效**。门禁最可能的失效形态是**恒真空转**（谓词选不中任何类、断言永远为空集）。所以每条门禁都必须自带**合成违规样本**并断言它变红：`verify-gates.sh self-test`、`FallbackDegradeContractTest` 的两个合成违规体、`FallbackCategoryGuardTest` 的 fixture、`ClassSizeRatchetTest` 的纯函数单测。**判定逻辑要抽成纯函数**，否则没法喂合成输入。
   - 补一条更硬的：**「绿」还可能是运气**。`FallbackCategoryGuardTest` 的变异样本原用运行期 `javac` + 临时目录，某次全量绿、下一次整模块跑就红 —— 是**我按 `verify-gates.sh` 换入口重跑时才暴露的**。故判据升格为：**同一门禁在四个入口（单类 / 整模块 / 关联模块 / 全量 clean）结论必须一致**，不一致就是 flaky，必须修掉而不是记住「它有时红」。`verify-gates.sh matrix` 已把「单类 vs 整模块」这一对做成常规门禁。
6. **交接单一旦被当成 SSOT，错的就是它**。它复述的每个数字都会漂，而漂了的数字比没有数字更危险（会让人照着过期清单动手）。机制化处理：本文件禁止复述可测量事实（`check-ledger-facts.sh`，已进 CI），事实一律「跑命令去量」。
7. **派生值不得覆盖观测事实（「事实优先于解释」）**。批次 J 的两处映射写反就是这个形态：`category` 是**派生标签**，而且可以被**下游报文**覆盖；我却让它决定 HTTP 状态码，于是 `category=CLIENT` + 状态码 500 被回成 **400**（把服务端的账记到调用方头上）、`category=RULE` + 非 2xx 被回成 **200**（用 200 承载失败，熔断器永不触发）。
   - 判据：**多个来源矛盾时，越接近事实的那一方优先**。HTTP 状态码是传输层观测面（调用方、网关、熔断器、指标都只看它）；`category` 只是我们对它的解释 —— **解释可以错，事实不会**。
   - 反例同族：`retryable` 必须由 `category` **单向派生**、不许下游报文覆写（`ErrorInfo.of` 已强制）。凡是「派生出来的值」与「采信外部输入的值」同时存在，都要问一句：谁覆盖谁。
   - 落地方式：把「优先级」写成**一个函数里的一个 return**（`CarriedErrorException#httpStatus`），不要分散成两处 if —— 分散就必然出现「一处按事实、一处按解释」。
8. **一个方法一个结论 —— 别把「工厂级」当成整类**。同一个降级工厂内，方法的处置可以不同：`KgClientFallback` 的 `edgeUpsert`（返回哨兵 `-1`）与 `neighbors`（返回空列表）按 PRD §16.8.1 必须 fail-open，只有 `deleteEdges` 该抛。图省事把工厂级改成 `THROWS`，那两个方法会立刻被判红 —— **门禁是对的，省事是错的**。
   - 判据：**结论要落在「方法」这个粒度上**；工厂级只是「本工厂内多数方法的缺省」，一旦出现分歧就必须在 `METHOD_DISPOSITIONS` 逐条覆盖。
   - 反向同样成立：也不能因为**一个**方法该 fail-open，就把整类标成 fail-open。
   - 这条同时解释了为什么「处置表」要分成工厂级 + 方法级两层，而不是一张平表：**缺省 + 显式例外**比「每个方法都登记」更不容易在被忽略的地方漂。

9. **「扫描源码找语义」必须先剥注释再判定**（被咬三次后才升格为规则）。三次事故：① grep 扫到 javadoc 里的 `@PreAuthorize`；② `KnowledgeBaseController` 的 javadoc 写着「语义等价于原 @PreAuthorize」，被朴素 `contains` 判成"已有注解"（假阳性）；③ `rbac:admin` 守卫把 `PermissionService` 注释里的提及判成"代码特判"。
   - 判据：**注释是给人和文档的，不是数据**。凡门禁要"从源码读出事实"，一律先 `stripComments` 再匹配；并且**这条规则本身必须有用例**（`RbacSeedEscalationGuardTest#shouldStripCommentsBeforeScanning`：注释里的字面量不得被识别、代码里的必须被识别）。
   - 附带：为"制造一个换行"写多行字符串时极易引入转义 bug（本轮因转义连红三次），测试里的输入尽量用单行拼接。
   - **附注（2026-10-19，第四次形态，与「剥注释」相反的方向）**：换成 JavaParser 之后我以为"AST 天然免疫注释"，
     但 `AnnotationExpr#toString()` **会把前置注释一起渲染出来** —— 于是注释里那句示例
     `@PreAuthorize("hasAuthority('order:read')")` 被当成**真注解**读进表达式清单，`MethodSecurityWiringGuardTest`
     一度把它报成"拼写不一致"。修法：**永远从结构化节点取值**（`asSingleMemberAnnotationExpr().getMemberValue()`），
     **不要**把 AST 节点 `toString()` 之后再正则 —— `toString()` 的输出属于"打印/调试"用途，不承诺只含语法树。
     判据升级为一句话：**"AST 免疫注释"只对节点本身成立，对节点的字符串化不成立。**
     同一类的另一半坑：`@Service("ss")` 是 **SingleMember** 注解（不是 Normal），只认 Normal 会把 ss bean 判成不存在 ——
     **注解形态要把 SingleMember / Normal / Marker 三种都处理**，否则"找不到"会被误读成"没配"。
10. **（**限安全/权限/数据一致性**开关）停用机制必须验证到"真的停用"**。本轮实测发现两条权限装配查询都不过滤 `sys_permission.status`，即我们选定的"`status=0` 停用"**纯属装饰** —— 停用的位照样进 `user:perms:`、照样通过判权。判据：**安全、权限、数据一致性这三类"开关式"机制（位停用、灰度放量、双写开关），都要有一条断言证明"关掉之后行为真的变了"**，否则它只是一个字段。
   - **刻意不推广到所有配置项**：UI 开关、文案开关、超时值这类，给每个都补"关掉后行为变了"的断言，成本会高到没人愿意遵守 —— 判据一旦太贵就会被绕过，然后连该守的那三类也一起失守。**判据的门槛必须便宜到"顺手就做了"。**落地为 `PermissionStatusFilterGuardTest`（源码级 + 合成违规样本 + 真实变异 M11 验证会咬）。
11. **门禁的输入解析必须与运行时读取同源**（这是 9、10 两条的一般化，也是它们的根）。门禁若自己解析一份数据（源码文本、种子 SQL、注解串），而运行时其实读的是**另一份**或**另一种写法**，那么门禁守的就不是运行时，而是它自己的想象。
    - **首选：同源** —— 让门禁调用运行时同一段代码/同一个文件/同一份清单。本轮的落地例子：`scripts/rbac-deprecated-bits.txt` 被门禁与预检脚本**共同读取**，避免"门禁和脚本对'哪些是旧位'理解不同"。
    - **降级方案（不是等价物，要标注为技术债）**：做不到同源时，才退回"**覆盖输入的每一种形态 + 每个形态一条用例**"。成本必须写明：**不同源意味着「新增一种写法就必须记得同步门禁」，而这个"记得"是不可靠的**。
    - 已发生 4 次：`status` 过滤只验一条通道（两条查询）；种子解析只认 `VALUES` 不认 `INSERT ... SELECT`；javadoc 里的 `@PreAuthorize` 被当成注解；`sys_permission.path` 有列有种子却**没有任何运行时消费者**（门禁若按它判断会守一个不存在的东西）。
    - 判据自检句：**「门禁读的东西，和运行时读的东西，是同一个吗？」** 答不上来就是降级方案，要标注。

12. **「引用归零」必须覆盖三种形态，缺一种就是假归零**（5a 开工前实测到第三种）：
    1. **注解**：`@PreAuthorize("@ss.hasPermi('x')")`；
    2. **代码内调用**：`UserContext.hasPermission("x")` / `hasPermi("x")` / `hasAnyPermi(...)`；
    3. **判权结果当参数**：`manager.doIt(..., UserContext.hasPermission("x"), ...)` ——
       判权结果不是用作"守门"，而是当**布尔标志**传给下层（`SpuController#updateStatus` 的 isAdmin 就是这样）。
    第 3 种最阴：它既不在注解里、也不是一个 `if`，`grep hasPermission` 能搜到但要靠人判断"这是守门还是传参"。
    **不变量**：三种形态合计为 0，才允许停用某个位；`preflight` 脚本已覆盖 1、2，第 3 种由脚本一并命中
    （同一正则），但**人工复核时**必须专门问一句：这个判权结果是用来"拒绝"还是用来"分支"？
    分支的那一条，停用旧位会让分支**静默走错方向**（管理员被当成普通人），而不是报 403 ——
    **报错是安全的，走错方向才是事故。**
13. **提交门禁必须 ⊇ CI 的"安全类"门禁**（2026-10-18 事故直接产出）。事故：批 4 提交时 `fast` 全绿而 `full` 红 ——
    因为 `fast` 此前**根本不跑 architecture-tests**，于是"受管控制器必须有类级 `@PreAuthorize`、
    位必须存在、旧位引用归零、降级契约、类大小棘轮"这些守卫在**提交动作上一律缺席**。
    - 区分原则：`fast` 只能砍"**慢且与安全无关**"的部分（全量 reactor 跑所有服务）；
      **凡属代码正确性/安全边界的必须留在 `fast`**，否则 `fast` 的绿不构成提交许可。
    - 已修：`fast` 增加 architecture-tests 步骤，并用变异 M12（删掉 `AdminPayController` 类级注解）
      证明 `fast` 会红 —— **"加了一步"不等于"会拦"，必须喂违规样本验证**。
    - 推广句：**任何"提交前检查"都要问一句：它和 CI 用的是同一套判据吗？**
      不是的话，绿灯就只是"我跑的那部分没问题"。
    - **闭环：fast vs full 差异清单**（判据 #13 的收尾 —— 差异存在，但每一条都"可以在提交门槛外跑"）：

      | 步骤 | fast | full | 不在 fast 的理由 |
      |---|---|---|---|
      | ledger 事实门禁 / ID 不变量 | ✅ | ✅ | — |
      | **根文档计数（测试数）** | ⛔ **`[skip]`**（2026-10-19 改） | ✅ | 输入是 surefire 报告：本地可能无报告（刚 clean）或报告是上一轮的（没 clean），两种都会给出与本次提交无关的红 ⇒ 典型门禁疲劳。**fast 必须显式打 `[skip]`**，因为它的绿只意味着「报告无关的门禁绿」，不等于全部绿 |
      | ia-api 单测（含降级语义契约门禁） | ✅ | ✅ | — |
      | **architecture-tests 全部守卫** | ✅（2026-10-18 补入） | ✅ | —（这正是原来的窟窿） |
      | 其余 14 个服务的业务单测（全量 reactor） | ❌ | ✅ | 见下两条 |

      **为什么业务单测可以不在提交门槛**：
      ① 它们验的是**业务行为**，不验"安全边界/架构约束"——后者已 100% 在 fast；
      ② 但"不验安全"不等于"不会红"：改注解、改签名仍可能让它们红。
         所以**必须有兜底闸**：`.githooks/pre-push` 跑 `verify-gates.sh full`（等价 CI）。
         提交=fast（秒级、安全类全在），推送=full（全量、戴在"代码离开本机"那一刻）。
      ③ 若有人 `git push --no-verify`，CI 仍然是同一套 `full` —— 三层（commit/push/CI）判据同源，
         差别只在**广度**，不在**判据**。这就是 #13 要的"同一套判据"。
      ④ **CI 是最后一道闸，红了必须修**（不是"重跑一次看看"）。前两层都能被 `--no-verify` 绕过，
         CI 不能；一旦 CI 红仍合入，整套判据就退化成"建议"。

      **代价与选择**：把 14 个服务单测塞进 commit 会让每次提交 5~6 分钟，结果是大家加 `--no-verify`，
      门禁形同废除。**判据的门槛必须便宜到顺手就做**（与 §5.10 的成本论同源）。
14. **类级与方法级 `@PreAuthorize` 是 AND，不是覆盖**（2026-10-19 B3 实测）。
    想让同一控制器里「读=低权限、写=高权限」，在类级注解之外再给读方法加方法级注解是**无效**的：
    两个注解会**同时**被要求 ⇒ 只有低权位的"只读运营"依旧 403；而持有 `*:*:*` 的管理员完全无感，
    于是缺陷在生产里**长期隐身**（本地单测也看不见，见判据 #14 的补注）。
    - **正确做法**：该控制器**清空类级注解**、逐方法挂位，并把类路径登记进
      `AdminEndpointAuthorizationGuardTest#METHOD_LEVEL_ADMIN_PREFIXES`（守卫要求"注解数 ≥ 映射方法数"）。
    - **检查方法（两条都要跑）**：
      ```bash
      # ① 找"类级与方法级并存"的控制器（有类级 @PreAuthorize 且方法级也有 ⇒ AND 风险）
      grep -rl "@PreAuthorize" --include=*.java */src/main/java | while read f; do
        c=$(awk '/^public class/{exit} /@PreAuthorize/{n++} END{print n+0}' "$f")
        m=$(grep -c "@PreAuthorize" "$f")
        [ "$c" -gt 0 ] && [ "$m" -gt "$c" ] && echo "⚠ $f 类级+方法级并存（AND）"
      done
      # ② 结构化断言（不依赖人看）：MarketingReadTierGuardTest 断言目标类**类级必须为空**，
      #    MethodSecurityWiringGuardTest 断言 @EnableMethodSecurity / ss bean / 表达式拼写
      ```
    - **补注（为什么单测看不见）**：`@PreAuthorize` 由 Spring Security **代理**求值；纯 Mockito 里直接
      `new` 出来的控制器没有代理，"断言它会 403"是**假测试**。所以静态层守"接线"（开没开、连没连、拼写对不对），
      行为层留给 E2（端到端 403）—— **两层都要，缺一层就是盲区**。


---

## 6. TradeInvoice 三态设计 —— 三件事实已查明（2026-10-18）

用户裁决：**不改 fail-closed，退款优先**；但「静默」必须升级为 **降级 + 可观测 + 可补偿** 三态。
`TradeInvoiceClientFallback` 在决策表中标注为 **特殊 case：不走 classify 二元**；ArchUnit 守卫需为 `void` 方法开**白名单**，
白名单要求「有日志 + 有补偿记录 + 有告警」——否则守监会逼人把它改成 `SYSTEM_BUSY`，**那就是 R-4 重演**。

### 三件事实（实测）
| 问题 | 结论 | 证据 |
|---|---|---|
| 补偿表是否已有？ | **没有独立的 outbox/补偿表**；但 `invoice.partial_refund_flag`（`Initialize.sql:1413`）+ `idx_partial_refund`（`:1422`）**本身就是 D4/O6 的补偿标记**，管理端按它人工收敛 | `[历史]` 当时全量扫过：无 outbox/compensation 类表 |
| 告警通道是否已有？ | **基建在、埋点为零**：2 个 pom 引了 actuator/micrometer-prometheus，但全仓 **`MeterRegistry` 使用 0 处** | `grep -rl MeterRegistry` = 0 |
| `markPartialRefund` 是否幂等？ | **是**：`InvoiceAdminManager:183` 用「条件更新 + 影响行数判断」（`marked == 0` 即已标记过，直接 return） | 同上 |

### 因此的最小实现（不引新框架）
1. **可观测**：在 fallback 降级分支打 **Micrometer Counter**（依赖已在，零新增）；指标名如
   `ia.invoice.link.degraded{kind=VOID|PARTIAL}`
2. **可补偿**：`partial_refund_flag` 已覆盖 `markPartialRefund`；**只有 `voidOnFullRefund`（冲红/作废）缺标记承载**
   → 新增一张小表 `invoice_link_compensation`（`order_no`/`scene_ref`/`kind`/`status`/`retry_count`/`next_retry_at`/`last_error`/审计列 + 索引）
3. **自动重试**：`@Scheduled` 扫 `status=PENDING AND next_retry_at<=now()` → 重调 Feign → 成功置 DONE / 失败退避 + 计数
4. **不再静默**：fallback 降级时**落一条补偿记录**（而不是只打日志），这是白名单要求的「有补偿记录」

**注意**：`voidOnFullRefund` 与 `markPartialRefund` 的补偿语义不同 —— 前者要**新建承载**，后者**已有 flag**，不要重复造。

### 实施状态（2026-10-18 本批落地）
| 项 | 状态 | 落点 |
|---|---|---|
| 2. 可补偿的**承载**（新表） | ✅ 已建 | `Initialize.sql` 新增 `invoice_link_compensation`（DROP 清单同增、双向零差集；数量与逐表名见 `sync-doc-counts.mjs --list-tables`）|
| 4. **不再静默**（落补偿记录） | ✅ 已接 | `PayEventNotifier#recordCompensation`（`kind` 判定后才落；写库再包一层 try/catch）|
| 3. 自动重试（定时任务） | ✅ **已做**（批次 G） | `app/InvoiceLinkCompensationRetryManager` + `infrastructure/scheduler/InvoiceLinkCompensationRetryJob`（XXL-Job 生产入口 + `@Scheduled` 开发入口；`2^n` 退避封顶、最多 5 次、4xx 直接永久失败）|
| 1. 可观测（Micrometer Counter） | ⬜ **未做** | 改判：`pay-service` 的 pom **没有** micrometer/actuator（只有 gateway / ia-common / notification 有），原「依赖已在，零新增」**不成立**。当前可观测性是「ERROR/WARN 日志 + 补偿表行」，指标留到重试批次一并加 |

⚠️ 改判记录：§6 原文假设「`kind` 可在 fallback 内落库」——**不成立**，ia-api 无持久化能力。最终形态是「fallback 抛 → 调用侧（pay-service，有持久化）落库」，即把「静默」升级为「**可观测 + 可补偿**」而不是在契约层造端口。

> 【去重留痕】此处原有「资金事务门禁根因与重构决策 / A1 / A2 / 观测设施」一段，与 §1.17（第 555 行起）的**正本**内容近似重复（仅措辞与标点不同），两份并存必然漂 ⇒ 2026-10-19 按「同一决策只留一份」删除本处副本，**决策正文以 §1.17 为准**；A1/A2 的**落地结果**见 §1.18.7。

**5b 商家端权限替换（未开始，等你签 —— 可直接签 `rbac-permission-matrix-draft.md` §4.2b 的 19 行表）**
- 范围：`/api/seller/**` 的全部写端点（**数量不写死** —— 跑 `node Implementation/back-end/scripts/extract-endpoints.mjs` 取实测；投影预演报出的 `UNANNOTATED` 清单即以这批为主）；
- 前置：**逐端点人工签字口径**（草案 §4.2），门禁 EXEMPT 期限 2026-11-15；
- 现状：门禁对这批走 EXEMPT（临时豁免），到期必须收口——**不是"没发现"，是"已登记待替换"**；
- 完成后：删 EXEMPT 条目并让门禁转强制。

**SoD（职责分离，另立项）**
- 已裁决但未实现：`invoice:issue` / `invoice:void` 的"开票员不持作废位"目前**只是授权约定**，
  靠种植时不授予实现，**系统未强制**；
- 需引入"互斥位"模型（同一实体两类动作互斥 + 授予时校验），否则管理员误配即失效；
- 同类待办：`setUserRole` 的第二道闸（当前恒真护栏已加）在生产有多管理员后是否够用，需随 SoD 一并评估。
