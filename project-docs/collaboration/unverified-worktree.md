# 未验证批次清单（`[UNVERIFIED]` Worktree Inventory）

> **用途**：把「这一批改了什么、为什么改、验没验」落盘，**不靠回忆**。
> 工作区可能积累多批未提交改动，靠记忆区分「哪些是我改的」是本仓已发生过的事故源
> （见 `open-items.md` §5.6 与 §1.18.8④「共享文件留痕」）。
> **它不进规格索引**：这是交接工具，不是规格文档。

---

## ⚠ 先读：并行会话会直接抹掉这批改动

本仓**多个会话共用同一个工作区**（历史事故：`open-items.md` 被另一会话追加并暂存过；见 `open-items.md` §1.18.8④）。
本批改动**全部未提交**，因此下面任何一条操作都会**直接抹掉它，且没有提交记录可恢复**：

```
git checkout -- .        git switch <branch>      git reset --hard
git stash                git clean -fdx           IDE「Revert All」/「Rollback」
```

**动手前先确认没有别的会话正在这个工作区工作。**

**本批落盘时的取证（用文件工具读 `.git/logs/HEAD`，不需要 shell）**：

- reflog 共 260 条；**最后一条是 2026-10-19 的一次 commit**（`fc7f9701 → 71d5098`，
  message：`fix(arch): 资金写计数改为「receiver 判定」，六条合成单测全绿；假阴性仍在（旧解析器未换）`）；
- **该 commit 之后没有任何 HEAD 级操作** —— `checkout` / `reset` / `pull` / `stash` 都会在 reflog 留痕，
  现在一条都没有。⇒ 支持「本批改动落盘之后**没有**被 checkout/reset/stash 动过」。

**这条证据的两个边界（别当成保证）**：

1. **`git clean` 不写 reflog** —— 它删的是未跟踪文件，reflog 里什么都不会留，所以「reflog 干净」**排除不了 clean**；
2. 我能证明的只是「本批新建的文件**此刻还在**」（我一直在读写它们），不能证明「从未被删过又恢复」。

⇒ 结论：**证据支持「未被抹掉」，但不构成保证**。恢复环境后第一件事仍是**先建分支提交**（见 §0）。

## 0. 提交约定（本批未能执行，恢复后第一件事）
>
> **当前状态**：**✅ 已提交 + 已验证（0→4 全绿）**。
> 提交：分支 `wip/t-withdraw-step1-unverified`，commit `fd569cb0`，消息前缀 `[UNVERIFIED]`（**锁在「功能未完成」语义上**，
> 不是「没跑过」——详见 `open-items.md` §1.25 的状态说明）。
> 验证：`mvn -v` = Maven 3.9.7 / JDK 21.0.10；`-DskipTests install` 绿；两个门禁类绿；
> `clean test` 全绿（本批 H2 用例首跑即绿）；`sync-doc-counts --write` 回填并自检 0 残留；
> `verify-gates.sh full` 全绿。历史：本批最初受 shell `0xC0000142` 阻塞，提交时还被 pre-commit 钩子
> 抓到过一个编译错（lambda 捕获非 final 局部变量）——**静态审查不能替代编译**，这条已写进 `open-items` 教训。

## 0. 提交约定（本批已执行）

```bash
git switch -c wip/t-withdraw-step1-unverified      # ✅ 已建
git add <本批 27 个路径>                            # ✅ 只点路径，未用 git add -A
git commit -F <msg> -- <同样的路径>                 # ✅ fd569cb0（工作区其余改动未纳入、未触碰）
```

- **分支名 `wip/*`、commit 前缀 `[UNVERIFIED]`** 固定不变；`git log --grep='\[UNVERIFIED\]'` 可一次捞全。
- **原则**：不进**主干**，不是「不进任何分支」—— 未验证改动的丢失风险高于「暂时进历史」的代价，前者不可恢复。
- **合入判据（2026-10-19 裁决，见 `open-items.md` §1.34④）**：门禁全绿 + 债务登记（含 deadline）+ 上线 checklist 写清 ⇒ **可合**；
  「功能可用」是另一道闸（触发入口完整 **且** 真实打款渠道），**不要把「合入」读成「提现可用」**。
  在`[UNVERIFIED]` 前缀此后只表示「**功能未完成**」，不再表示「没验证过」。

## 1. 批次定性（一句话）

T-WITHDRAW **第一步**：把「商家能提多少」从「Σ 结算单」搬到一格一行的权威账户 `seller_fund_account`，
**只动 `settled_total` 与 `paid_total`**（`frozen_amount` 留到第二步）。

⚠ **这不是「提现已修复」**：`settle()` 没有触发者 ⇒ 账户恒 0 ⇒ 申请恒拒 ⇒ **生产提现功能中断**；
上线**必须同时交付触发入口**。详见 `open-items.md` §1.25 / §3。

## 2. 改动清单

> §1.26（结算触发入口）在同一分支的后续提交里，件与落点：
> `app/finance/SettlementSweeper.java`（新）、`infrastructure/scheduler/SettlementSettleJob.java`（新）、
> `SettlementMapper#selectSettleableIds`（改）、`SettlementRepository#findSettleableIds`（改）、
> `SettlementRepositoryImpl#findSettleableIds`（改）、`scripts/settle-pending.sh`（新）、
> `project-docs/collaboration/settle-trigger-draft.md`（新）、
> `SettlementFundAccountH2Test#shouldSweepSettleableSettlementsOnly`（新用例）、`SettlementSweeperTest`（新）。
>
> §1.28（第二步：条件预占）同批：`SellerFundAccountMapper#freeze/#unfreeze`（改）、
> `SellerFundAccountRepository#freeze/#unfreeze`（改）、`SellerFundAccountRepositoryImpl`（改）、
> `WithdrawalManager`（改：预占/释放/单笔上限/终态限定）、`SettlementManager#settleAt(id,today,now)`（改）、
> `SettlementSweeper`（改：四段计数 + 单一时钟）、`SettlementSettleJob`（改：四段日志 + 耗时）、
> `scripts/settle-pending.sh`（改：链路与 cookie）、
> 测试：`SettlementFundAccountH2Test`（+7 条真库用例、删 `savePendingWithdrawal` 绕过 helper）、
> `WithdrawalManagerTest`（重写，inOrder 钉死两条顺序）、`SettlementSweeperTest`（重写，四段计数）。

> §1.30（第三步：按金额核销）同批：`Initialize.sql`（+`withdrawal_settlement` 表、+`settlement.withdrawn_amount` 列）、
> `Settlement`/`SettlementPO`（+字段）、`SettlementMapper`（+`selectAllocatable`/`increaseWithdrawn`/`decreaseWithdrawn`）、
> `SettlementRepository(+Impl)`（+`findAllocatable`）、`domain/service/SettlementAllocator`（新，纯函数）、
> `domain/model/finance/SettlementRemaining`（新）、`WithdrawalSettlement{PO,Mapper,Repository,RepositoryImpl}`（新）、
> `WithdrawalManager`（申请=冻结→落单→核销；拒绝=迁移→退核销→解冻）、
> 守卫：`WithdrawnAmountWriteGuardTest`（新，按 SQL 归属）、
> 测试：`SettlementFundAccountH2Test`（+5 条核销用例、装配里 `LocalCacheScope=STATEMENT`）、
> `SettlementAllocatorTest`（新）、`WithdrawalManagerTest`（重写，含核销顺序）。

> §1.31（AD24 端点）同批：`AdminFinanceController`（+`POST /settlements/settle`）、
> `adapter/request/SettlementSweepReq`（新）、`adapter/response/SettlementSweepVO`（新）、
> `SettlementSweeper`（+`sweep(...,periodEnd)`、`SweepResult.failures`、`sweepForAdmin`）、
> `SettlementMapper#selectSettleableIds`（+periodEnd）、`SettlementRepository(+Impl)`（签名加 periodEnd）、
> 契约 `05-API-Specification`（AD24 + 注记 + V5.26.0）、测试 `SettlementSweeperTest`/`SettlementFundAccountH2Test`、
> 加上「生产 SESSION / 测试共用 session」的装配修正。

### 2.1 建库脚本

| 文件 | 改了什么 |
|---|---|
| `Implementation/back-end/database/Initialize.sql` | 新增表 `seller_fund_account`（`uk_seller_id`）+ 同步补 `DROP`；`settlement` 的状态语义注释改写（`SETTLED` = 可提现，`status=3` 为历史语义、不再写入） |

### 2.2 trade-service 主代码

| 文件 | 改了什么 |
|---|---|
| `domain/model/finance/SellerFundAccount.java` | **新增**。`available()` = `settled_total − frozen_amount − paid_total`（口径只此一处）|
| `domain/model/finance/Settlement.java` | javadoc：本表**不承载打款事实**；`PAID_OUT` 为历史语义 |
| `domain/model/finance/Withdrawal.java` | javadoc：本表是**已打款唯一事实源**；不回写结算单状态 |
| `domain/enums/SettlementStatusEnum.java` | javadoc：三值语义写死，`PAID_OUT` 保留但不得再写 |
| `domain/repository/SellerFundAccountRepository.java` | **新增**。`addSettled` / `addPaid` 都**不带额度条件** |
| `domain/repository/SettlementRepository.java` | `+settleConditionally`（PENDING→SETTLED 唯一原子入口）；`-updateStatusBySellerAndStatus`（全量置位已删）|
| `domain/repository/WithdrawalRepository.java` | `+reviewIfPending`（条件迁移，0 行=幂等）；`-update`（不再读-改-写）|
| `infrastructure/persistence/finance/SellerFundAccountPO.java` | **新增** PO |
| `infrastructure/persistence/finance/SellerFundAccountMapper.java` | **新增**。两条**无条件**增量写 |
| `infrastructure/persistence/finance/SellerFundAccountRepositoryImpl.java` | **新增**。先 UPDATE、0 行再 INSERT（不用方言 upsert）；两个写入各带 `@Transactional` |
| `infrastructure/persistence/finance/SettlementMapper.java` | `+settleIfPending`（条件：status + period_end + 金额 + 订单数）|
| `infrastructure/persistence/finance/SettlementRepositoryImpl.java` | 实现 `settleConditionally`；删全量置位 |
| `infrastructure/persistence/finance/WithdrawalMapper.java` | `+reviewIfPending`（`WHERE id=? AND status=?`）|
| `infrastructure/persistence/finance/WithdrawalRepositoryImpl.java` | 实现 `reviewIfPending`；删 `update` |
| `app/finance/SettlementManager.java` | `+settle`（同事务累加 `settled_total`）、`+payout`（只记账、**不判额度**）、`availableBalance` 改读账户；`-markSellerSettlementsPaidOut`；账户仓储进构造器 |
| `app/finance/WithdrawalManager.java` | `review` = **条件迁移 + 记账**（同一事务），审核侧**不判额度** |
| `adapter/admin/AdminFinanceController.java` | 注释：审核的资金副作用（不是「打个状态标记」）|
| `adapter/response/SellerBalanceVO.java` | javadoc：`balance` 口径迁到资金账户 |

### 2.3 测试

| 文件 | 改了什么 |
|---|---|
| `trade-service/src/test/.../app/finance/SettlementFundAccountH2Test.java` | **新增**。三张表（settlement / seller_fund_account / withdrawal）+ 启动期结构断言；判据四条（数值一致 / 覆盖面一致 / 非空断言 / **对账等式 Σ withdrawal(PAID_OUT) == paid_total**）；真实走 `settle()` 与 `review()` |
| `trade-service/src/test/.../app/finance/WithdrawalManagerTest.java` | 改为验证「条件迁移 + 记账」「重复审核不重复记账」「**审核侧不判额度**」「拒绝不记账」|

### 2.4 门禁

| 文件 | 改了什么 |
|---|---|
| `architecture-tests/.../FundTransactionGuardTest.java` | 写动词表加 `add / increase / settle / payout`；已知清单加本批三个入口 + body token；新增两条反例 + 一条「有事务必须放行」|
| `architecture-tests/.../SettlementPaidOutRetiredGuardTest.java` | **新增**。封死 `SettlementStatusEnum.PAID_OUT` 的**写路径**：① 作 `setStatus`/`update*`/`settle*`/`mark*`/`review*` 实参；② **存进变量（赋值右侧 / 变量初始化）**；③ `UPDATE settlement` 里把 status 写成字面量；④ 静态导入。**读侧放行**。五条合成反例 + 扫描面三形态；残余局限（容器/反射/拼接 SQL）登记为「靠 review」 |

### 2.5 文档

| 文件 | 改了什么 |
|---|---|
| `project-docs/collaboration/open-items.md` | §1.24b（计划订正）、**§1.25（本批落地 + 口径 + 验证边界）**、§2.2b（T-WITHDRAW 状态）、§3（已知未修，含「生产功能中断」与「无触发者」阻塞项）|
| `project-docs/backend/04-Data-Model.md` | §7.4：`seller_fund_account` 口径、`settlement.status` 语义、两条对账等式、旧「级联置位」订正 |
| `project-docs/contract/05-API-Specification.md` | T22 `balance` 口径；AD20/21 状态语义；AD23 资金副作用；变更行 V5.25.0（含「生产功能中断 + 上线前置」）|
| `project-docs/collaboration/unverified-worktree.md` | **本文件** |

## 3. 跑验证前的提醒

**3.0 工具链已实测可用（不再是「不可判定」）**

`mvn -v` 实测：**Maven 3.9.7 / JDK 21.0.10（Microsoft）/ platform encoding UTF-8**；
路径为 JDK `C:\Users\18279\.jdks\ms-21.0.10`、Maven `E:\maven\apache-maven-3.9.7`（**不在 PATH 上**，需 export）、
本地仓库 `E:\maven\myRepository`。命令前须：

```bash
export JAVA_HOME=/c/Users/18279/.jdks/ms-21.0.10
export PATH="$JAVA_HOME/bin:/e/maven/apache-maven-3.9.7/bin:/c/Program Files/nodejs:$PATH"
```

历史：本批前半段整段跑不动（`0xC0000142` / STATUS_DLL_INIT_FAILED，连 bash 内建 `true` 与 `cmd /c echo` 都失败
⇒ 进程创建层故障，与本批代码无关）。该故障已消失；**若再出现，按 `open-items.md` 的「环境事实」定位，不要改代码去迎合。**

1. **先建分支再跑**（见 §0）：跑坏了可以重来，丢了不能重来。
2. **门禁本批被改过**：`full` 若红**不能直接归因**，必须先单独跑门禁类（`open-items.md` §1.25 第 1 步），
   **第 1 步红就停**，不执行 2/3/4。
3. **命令错 ≠ 代码红**：第 1 步若报 `No tests matching pattern …` / `No tests were executed`，
   那是 `-Dtest=` 写错（类名/方法名拼写、模块未编译），**不是门禁红** —— 真红会带断言消息与文件行号。
4. **计数必须由脚本回填**：根文档的「表数 / 测试数」是 `sync-doc-counts.mjs --write` 的产物，
   **不要手改**；`--write` 必须在全量测试之后跑（没有 surefire 报告时它不写、不臆测）。
5. **`information_schema` 断言首次跑可能红**：红时先分清「H2 / MySQL 元数据差异 ⇒ 改断言写法」
   还是「DDL 真漂了 ⇒ 同步 `Initialize.sql` 与本用例 DDL」；**不许删断言**（那等于把双源同步退回「靠记得」）。
6. **并行会话**：见页首警告 —— 它的一次 `checkout` / `clean` / `Revert All` 就能抹掉整批，且无记录可恢复。
