# 结算触发入口 —— 最小方案 + 契约草案（未落契约）

> **状态**：应用侧与运维侧都已落地（XXL-Job handler + 脚本），**管理端端点仍是草案**。
> 本文件不是规格，不进规格索引；它存在的原因是：草案需要一个**已跟踪**的落点，
> 免得「将来要加按钮」这件事只活在某个会话的上下文里。
> 台账侧对应 `open-items.md` §1.26。

## 0. 为什么需要它

`SettlementManager#settle` 是 `PENDING → SETTLED`（= 可提现）的唯一入口，但在 T-WITHDRAW 第一步
之前**没有任何调用者**：结算单永远停在 `PENDING`，商家 `settled_total` 恒为 0，
`available()` 恒为 0 ⇒ **生产上提现完全不可用**（不是「部分功能缺失」）。

所以「触发入口」不是锦上添花，而是上线的前置条件之一。

## 1. 已落地的两条路径（本批）

| 路径 | 落点 | 用法 | 适用 |
|---|---|---|---|
| 调度（主） | `infrastructure/scheduler/SettlementSettleJob`（`@XxlJob("settlePendingSettlements")`） | XXL-Job 管理端点「执行一次」，或配 cron 每日跑 | 生产常规 |
| 运维（辅） | `scripts/settle-pending.sh` | 默认只读报告候选条数；`--trigger` 时 POST 到 `XXL_JOB_TRIGGER_URL` | 上线初期人工核对、事故兜底 |

两条路径最终都进同一个门：`SettlementManager#settle(id)` → 条件更新 → 同事务累加 `settled_total`。
**没有第二条记账路径**（这是不变量，不是约定）。

关键实现约束（写死在代码注释里，别在重构时丢掉）：

1. **循环必须在 job 里，不能在 Manager 里** —— `settle()` 带 `@Transactional`，靠 Spring 代理生效；
   若 Manager 内部 `settlePending()` 循环调 `this.settle(...)`，那是自调用、代理被绕过、**事务不生效**
   （单测全绿、线上无事务，是最难查的一类）。
2. **候选查询与条件更新同条件** —— 扫描只缩小范围，判定仍在 `settleIfPending`（两次扫描之间状态可能变）。
3. **单条失败不中断整批** —— 已整事务回滚，`settled_total` 不会半加；下次扫描重试。
4. **幂等** —— 重复/并发触发只有一方生效（0 行 = 已结算）。

## 2. 契约草案（**未落 05-API-Specification**）

> 只在需要「管理端按钮」时才升级为正式契约行；升级时须同时补：契约表行、`docOnly` 计数、
> 权限矩阵（`rbac-permission-matrix-draft.md`），并跑 `verify-gates.sh full`。

```
AD-draft.1  批量结算到期结算单
METHOD/PATH : POST /api/admin/finance/settlements/settle
权限        : @PreAuthorize("@ss.hasPermi('finance:admin')")   ← 与 AdminFinanceController 现有口径一致
请求体      : {"limit": 200}          // 可选，默认 200，范围 [1, 1000]（越界 → 400）
响应        : Result<SettleSweepVO>
              SettleSweepVO = { "scanned": 12, "settled": 12, "skipped": 0 }
语义        : 逐条条件更新，**非单一事务**（每单一个事务）；跳过项只记日志，不中断整批
幂等        : 是（重复调用不会重复记账）
副作用      : 只写 settlement.status 与 seller_fund_account.settled_total；
              **不得**写 settlement.status = PAID_OUT（已打款事实源是 withdrawal，见守卫类）
错误码      : 401 未登录 / 403 无权限 / 400 limit 越界
```

**明确的非目标**：本端点**不**做提现、**不**碰 `frozen_amount`、**不**碰 `paid_total`。
它只是「把可提现额度结算出来」。

## 3. 做完这些仍然不能上线（三条例由，逐条可验）

1. **`frozen_amount` 预占未做（第二步）** —— 额度守门目前只在申请侧做「读-比较」，
   非原子；并发两笔提现可能各自通过检查。审核侧不判额度是对的，但**预占**必须补上。
2. **`withdrawal_settlement` 分配/核销未做（第三步）** —— 提现与结算单之间没有金额级对账，
   部分提现无法归集到单据，拒付释放也没有落点。
3. **打款没有真实渠道** —— 当前「审核为已打款」= 记账，不产生出款动作；真实打款（银行/微信零钱）
   属于另一个批次，本批不做承诺。

⇒ 交付口径固定为：**「提现前置条件之一已通，提现整体仍不可上线」**。
