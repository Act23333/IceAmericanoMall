# 收口清单（Open Items Ledger）

> **用途**：跨会话的工作交底。不是规格文档，不需要进规格索引。
> **维护规则**：任何一批工作落地后，同步更新本文件的「已完成 / 待办 / 已知未修 / 验证边界」四段。
> **最后更新**：2026-10-18（批次 J：跨服务 4xx 透传的最后一米）

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

**`marketing:read` 待接线（批 7 复核产出）**：现在 `marketing:admin` 只由 ROLE_ADMIN 持有 ⇒
**没有"只读运营"档位**——运营要进营销后台就得是管理员，于是"能看"自动等于"能改"。
要让"运营只看不改"，需把营销域 `GET` 类端点接 `marketing:read`（种子里已有、当前零消费）。
**另外两条"若放开则需重评位"的警告**（与批 6 提现警告同源）：
- `FlashSaleAdminManager#deleteFlashSale` 对进行中活动**禁删**；若放开，删除会间接影响已下单用户履约/退款 ⇒ 需重新评估是否改挂 `pay:admin`。
- `GroupBuyAdminManager#deleteActivity` 本轮**已补**"有拼团中（未结团）的团禁删"（原来是允许删的，属间接资金影响）。

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
1. **含 Feign 的豁免是"静默通过"**：现在方法里出现 Feign 调用就直接放行，不留痕。
   下一批改为：**命中原豁免即 `[FAIL]`，要求人工登记豁免理由**（登记进文档，不是加白名单）。
   理由：静默豁免等于"看不见的例外"，和 `@Transactional` 漏写一样危险——只是它不会立刻错账。
   **同时已核实第一个豁免者不是"已豁免"而是"本来就安全"**：
   `PayManager#payByBalance` 的本地写走 `PayOrderRepositoryImpl`（该类 `@Transactional` 数量为 0），
   但 `createPending`/`resetPending`/`markBalancePaidIfPending`/`cancelPending` **各只有 1 条写语句**
   ⇒ 单语句自提交即原子，**不是"同方法多写无事务"**，与修复前的 `deduct`（同方法 2 写无事务）**不同级**。
   其残留风险是另一件事：`markBalancePaidIfPending` 返回 false 时"钱已扣、单未置成功"只 `log.warn`（缺告警），
   已在 `payOrderNo` 告警项里合并跟踪。
2. **资金表清单硬编码**：`FUND_TABLES` 是五个字面量，新增资金表（如将来的 `settlement_*`）不会自动纳入。
   下一批加**模式匹配兜底**（`*_account` / `*_transaction` / `pay_*` / `refund_*` / `*_batch` 等）
   + **表数断言**（扫描到的资金表数量须 ≥ 当前清单长度，否则说明正则退化了）。

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

**批 8 待办（`setUserRole` 的第二道闸，与该端点挂 `rbac:admin` 同一个 commit 交付）**：
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
| 3 | **Web 角色端开发** | seller / admin 两个 app（实测：**0 处 API 调用**，仅 Dockerfile 引用 `packages/api`；共 9 个源文件）。用户定的优先级：**Web 各角色平台先补完**。<br>**测量已交付**：三张表（端点→页面 / 页面→端点 / 端点→权限位）+ 缺口分解见 `frontend/23-Frontend-Platform-Gap-Matrix.md`——**别再引用「65 admin / 30 seller」这种数字**（口径不可复现，见该文 §2）。<br>**S0（基座）取证结论**：全仓 + git 历史**找不到取消记录**（唯一提及是本文件，无理由/时间/决策人；`git log -S"基座"` 只有引入那一次，`apps/admin|seller` 全历史零文件删除 ⇒ S0 从未以代码形态落地）。**因此 S0 的范围不能靠"当初为什么取消"来定，只能靠测量结果定。** |

### 2.3 审计交回、待排期
| 项 | 内容 | 需要谁 |
|---|---|---|
| **U2** | `Initialize.sql` 两处过时注释（「未启用 `@TableLogic`、无 `deleted` 列」但 `invoice_title.deleted`+`@TableLogic` 已落地；「每表以 BIGINT AUTO_INCREMENT 为主键」但 3 张关联表是双列 PK）；并给 **4 组同名不同型列**（`spu_id`/`sku_id`/`user_id`/`coupon_id`）标注键型 | 我可直接做 |
| ~~**U3**~~ | ✅ **已裁决并执行（本批）**：5 张零代码引用的死表**已从 `Initialize.sql` 删除**（`seller_application` / `flash_order_log` / `merchant_reply_template` / `ai_message` / `ai_conversation`；CREATE 块与 DROP 行同步移除，双向零差集）。理由：`flash_order_log` 列注释自称「秒杀幂等+兜底」是**假承诺**（实际靠 Lua + `orders.uk_order_no`）；`ai_*` 已被 `chat_message` 取代；`seller_application` 已被 `seller` 取代。设计意图仍留在 ADR/04（已加裁决注记）| 已完成 |
| **U16** | **网关营销路由「静态已具备、运行时未验证」**：`gateway-service/application.yml:146-149` 已有 `/api/admin/{flash,promotion,task,group-buy}/**` 四族谓词（注释还留着「实测营销端点不可达」的历史说明），但**谓词顺序的运行时行为没测过**（`/api/admin/**` 兜底在其后，顺序错了就会被兜底吃掉）。要验证需起网关 + 路由断言，属运行态。文档已标 🟡，勿改标「已收口」| 中（运行态）|
| ~~**U15**~~ | ✅ **已用门禁封住（S0-pre，2026-10-18）**：**不是「修好了越权」，而是把可利用面钉成构建期失败**。判据（实测）：`after_sale` 表**无 seller_id/shop_id**（只有 `order_no`+`user_id`）；admin 列表 `pageByStatus(status,…)` **只按状态过滤**；写侧 `PUT /{id}/review`、`/{id}/confirm-return` **只按 id 操作**。今天**不可利用** —— `PermissionService.hasPermi` 依次判「user/permissions 为 null → false」（fail-closed）、`*:*:*` 通配、**「roles 含 ROLE_ADMIN」短路**、否则查集合；ROLE_SELLER 只持 `[ai:chat, order:read, product:read, seller:admin]` ⇒ **403**。危险点在于：安全只靠种子少一行 `(3,5)`。故新增 `RbacSeedEscalationGuardTest`（纯函数判定 + 两条合成变异 + **真实文件注入 `(3,5)` 实测变红**）| 已封 |
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
| **U17** | **`void` 降级方法的兜底口径（方案 A：第一步已完成，见 §1.16；第二步待做）**。问题：语义门禁对「没抛异常」只做 `returnedDegradedValue++` 计数、**零校验**，而 `void` 的静默返回在调用方看来与真成功**完全同形**（正是 §1.10 里 `TradeInvoiceClientFallback` 那次事故的形态）。<br>**重要修正（M8 实测）**：光改 throw 不够 —— 处置表的 `THROWS` 只约束**不可用**分支，**4xx 分支的静默对 `void` 不可见**。故已补 `VOID_CLIENT_SILENT_ALLOWED`（当前为空）+ 双向陈旧检查（§1.16）。<br>**剩余（第二步）**：① `revokeUserTokens` 需先给 `UserAdminManager#updateStatus:110` 补调用点防护再改抛；② 补偿消费者 `operation_log` 对账（选型已定，前置见下）；③ 前端兜底分支见 U19。<br>**做法（不用手写控制流 lint）**：「能否抛」能从**类型**上强制就不做检查 —— 让该抛的方法 `throw` 复用现成的载体（`CarriedErrorException`），再加 ArchUnit 防回归。白名单若要求「日志 + 补偿记录 + 告警」，那三条**必须能被机器验**，否则又是会漂的注释。<br>**调用点已 grep（实测，别重查）**：<br>· `AfterSaleClientFallback#markRefunded` → `pay-service PayEventNotifier#writeBackAfterSaleRefunded:106-111` **有 `try/catch(Exception)`** ⇒ 已改 throw；<br>· `KgClientFallback#deleteEdges` → `item-service KgEdgeIngestManager#deleteEdgesSafely:179-181` **自有 try/catch** ⇒ 已改 throw；<br>· `AuthContextClientFallback#revokeUserTokens` → `user-service UserAdminManager#updateStatus:110` **无 try/catch**，且 DB 状态**已提交** ⇒ 直接改 throw 会让「已提交的禁用」对外报 5xx，**必须同步补调用点防护**（第二步）。<br>**两条硬约束**：① `revokeUserTokens` 若落补偿记录，**必须有真实消费者**（重试任务或管理端收敛入口）—— 否则就是造僵尸数据（`invoice_link_compensation` 那条教训）；② **删过时注释、留锚点**（已执行：`AfterSaleClientFallback` 的过时方法注释已删，改留锚点指向 `PayEventNotifier#writeBackAfterSaleRefunded`） | 第一步 ✅ / 第二步待做 |
| ~~**U18**~~ | ✅ **无需另做**：「9 个 fallback 的 CLIENT 分支回归测试」这一项已被 §1.10 的**发现式**语义门禁覆盖 —— `FallbackDegradeContractTest` 扫描 `target/classes` 自动发现全部工厂（不维护类名清单），逐个方法验证 CLIENT 分支。**不要再手写逐工厂的 CLIENT 用例**：那既冗余，也会在新增工厂时静默漏测（清单必然漂） | 已覆盖 |
| **U19** | **前端兜底分支（待办，随 U17 一起收）**：契约 §二 新增「下游透传」规则后，`/api/**` **可能出现不在 §2.1a 清单里的 4xx**（下游 409/422/429、非标准 499）。前端现状是「按已知码分支 + 其余落到通用提示」，需确认两件事：① **未登记的 4xx 必须落通用兜底**（通用错误 + 直显 `msg`），**不得**因为「状态码不在清单里」就走「服务异常」话术；② `check-error-codes.mjs` 当前报「未覆盖 0」是按**业务码**核对的（下游码也在本域 `ErrorCode` 里，所以仍覆盖），但**状态码维度没有门禁** ⇒ 前端若按状态码分支会漏。**不要**为此新增状态码白名单（会变成第二套映射），只补兜底分支 + 一条用例 | 随 U17 |

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
| **4xx 透传到达 HTTP** | ✅ **三层证据**：① 直调 handler（四类映射 + 接缝断言）；② 框架层 `CarriedErrorPassthroughMvcTest`（真实 `ExceptionHandlerExceptionResolver` + MockMvc，含装饰器包装与反向对照）；③ 突变 M5（双保险同时摘掉 → 框架层五条全红）。⚠ 边界：**真实网络/网关仍未验**（Nacos/Redis 不可达，服务起不来）|
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
      | ledger 事实门禁 / 文档计数 / ID 不变量 | ✅ | ✅ | — |
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
