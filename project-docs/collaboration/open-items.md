# 收口清单（Open Items Ledger）

> **用途**：跨会话的工作交底。不是规格文档，不需要进规格索引。
> **维护规则**：任何一批工作落地后，同步更新本文件的「已完成 / 待办 / 已知未修 / 验证边界」四段。
> **最后更新**：2026-10-18（F 批收口：双门禁 + 发票联动补偿表 + U11）

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
| 序 | 批 | 内容 |
|---|---|---|
| ~~—~~ | ~~**补偿重试**~~ | ✅ **已完成**（批次 G）：`InvoiceLinkCompensationRetryManager` + XXL/`@Scheduled` 双入口，4xx 短路为永久失败，见 §1.11 |
| 1 | **D（拆类还债）** | `PayManager`（344 行）/ `RefundManager`（334 行）已登记**限时豁免 deadline `2026-11-15`**；另有 17 个类在 `class-size-baseline.tsv` 里只受「不许增长 / 陈旧即红」约束。**先出测量报告**再决定拆法 |
| 2 | **H** | 小程序**仅文档**（用户已裁决：本轮完全排除代码）|
| 3 | **Web 角色端开发** | seller / admin 两个 app（现状：各 1 个"开发中"占位页）。用户定的优先级：**Web 各角色平台先补完**。S0（基座）曾被取消，需重启 |

### 2.3 审计交回、待排期
| 项 | 内容 | 需要谁 |
|---|---|---|
| **U2** | `Initialize.sql` 两处过时注释（「未启用 `@TableLogic`、无 `deleted` 列」但 `invoice_title.deleted`+`@TableLogic` 已落地；「每表以 BIGINT AUTO_INCREMENT 为主键」但 3 张关联表是双列 PK）；并给 **4 组同名不同型列**（`spu_id`/`sku_id`/`user_id`/`coupon_id`）标注键型 | 我可直接做 |
| ~~**U3**~~ | ✅ **已裁决并执行（本批）**：5 张零代码引用的死表**已从 `Initialize.sql` 删除**（`seller_application` / `flash_order_log` / `merchant_reply_template` / `ai_message` / `ai_conversation`；CREATE 块与 DROP 行同步移除，双向零差集）。理由：`flash_order_log` 列注释自称「秒杀幂等+兜底」是**假承诺**（实际靠 Lua + `orders.uk_order_no`）；`ai_*` 已被 `chat_message` 取代；`seller_application` 已被 `seller` 取代。设计意图仍留在 ADR/04（已加裁决注记）| 已完成 |
| ~~**U14**~~ | ✅ **已完成（同批）**：`04-Data-Model.md` 中 5 张死表的逐列小节已清理（§7.2/§7.3 整段删除，§9.1/§10.2 留标题位 + stub 以免断号与外部引用失效；悬空 FK `ai_message.id` 已改指 `chat_message.id`）。连带清掉 `Initialize.sql` 里**上次删表遗留的 6 行孤立节标题注释**与 1 处仍指向已删表的列 COMMENT —— 那些注释正好压在下一张表上方，属主动误导。`quality/20` 的 B3 条目已标收口，并留档「契约 CH6–CH9 仍标 ✅ 却无实现」这个**表侧已删、契约侧未动**的悬空项 | 已完成 |
| **U4** | 60 行**只读**结构对账脚本（打印缺表/缺列/类型不符），**不进 CI、不阻塞提交** | 我可直接做 |
| **U5** | 2 处缺索引（`sys_user_role.role_id` 非前导、`refund_order(status,create_time)`）+ 7 处冗余索引 + 6 处疑似死索引 | 低优先，**不建议现在删** |
| **U6** | `invoice_title.uk_user_default` 与软删共存（软删行占默认位）；当前 `clearDefaultByUserId` 不带 `deleted=0` **恰好**没踩坑——"对的但是偶然的" | 记 ADR |
| ~~**U7**~~ | ✅ **已裁决并执行（本批）**：`database/seed-data.js` **已退役删除** —— 它与 `Initialize.sql` 的种子写同一个 `USERNAME/'admin'` 唯一键、口令同为一个明文弱口令，且业务 ID 仍是 ADR-050 前的非 hex 形态 ⇒ 种子真源统一为 `Initialize.sql`。`20-Doc-Code-Consistency-Audit.md` 中「目录只有 Initialize.sql + seed-data.js + middleware」的事实已同步订正 | 已完成 |
| **U8** | `docker-compose.yml:46` 注释仍写一个过时表数（以 `sync-doc-counts.mjs --list-tables` 为准）| 低 |
| **U9** | 通知 `type` **无枚举化真源**（4 个服务各持私有常量，10 个值）→ 建议收口为 `ia-api` 共享枚举 | 架构，需立项 |
| ~~**U11**~~ | ✅ **已改（本批）**：`confirmPaid` 的「**支付单**不存在」用错成 `ORDER_NOT_FOUND(3009，「订单不存在」)` → 改 `NO_RESOURCE_FOUND(1103)`（同为 404 / CLIENT，调用方零感知，与 R2 的 I40/AD43/PAY6/I55 同口径）。`initiatePayment` / `initiateJsapiPayment` 的「订单不存在」**保留 3009**（语义正确） | 已完成 |
| **U12** | `[历史]` 审计快照：若干部份 spu 表无 `short_code` 列、部分 sku 表无 `sku_id` 列（逐表清单见 U1 的 H2 豁免记录）⇒ 这些 H2 表无法补唯一键。若日后参与写入用例，必须补列+补键 | 低（目前只读）|
| **U13** | `FavoriteSpuBriefQueryImplH2Test` 的 `spu_id` 约束是**匿名**（H2 自动名），键在但名与生产 `uk_spu_id` 不同 | 低 |
| **U10** | 契约 §二 覆盖率数字（98/40/58 → 实测 99/52）—— **A/B/C/E 批的 E 项就是删这些数字** | 在 R2 批内 |

---

## 3. 已知未修（**明确不算完成**）

| 项 | 后果 |
|---|---|
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
| 后端逻辑/编译 | ✅ 全量单测通过（自己跑 `verify-gates.sh full` 或 `mvn clean test` 取数；**必须 JDK 21**，JDK 25 下 JaCoCo 会全仓红）|
| 降级透传门禁 | ✅ **两层面均做过变异验证**（M1 改回无条件 `SYSTEM_BUSY` → 语义+结构同时红；M2 fail-closed→fail-open → 语义红；复原后全绿）|
| 门禁自证 | ✅ `verify-gates.sh self-test` 会对门禁喂合成违规样本并断言其变红；Java 侧自证（`FallbackDegradeGateSelfTest` / guard fixture / 棘轮四条规则）随 `mvn test` 执行 |
| 契约 ⇄ 代码 | ✅ 端点对账 289/0/43 + `check-section7` |
| 建库脚本 | ✅ 真库实跑过一次、0 错误且幂等（MySQL 8.0.36）；⚠ `[历史]` 那次跑在新增 `invoice_link_compensation` **之前** ⇒ **该表尚未真库实跑**，目前只有 H2 与静态核对 |
| 根文档计数 | ✅ `sync-doc-counts.mjs --write` 是唯一写入口，`--check --strict-tests` 已进 CI 与 pre-commit 钩子 |
| **运行态** | ❌ **未验证**。Nacos(8848) / Redis(6379) 均**不可达**，15 个服务起不来 ⇒ **登录链路、网关路由、Feign 调用、限流全程无运行态证据** |
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
