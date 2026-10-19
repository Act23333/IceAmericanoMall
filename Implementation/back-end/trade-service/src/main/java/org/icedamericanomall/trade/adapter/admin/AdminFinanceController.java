package org.icedamericanomall.trade.adapter.admin;

import org.icedamericanomall.common.web.QueryModel;
import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.RequiredArgsConstructor;
import org.icedamericanomall.common.annotation.OperationLog;
import org.icedamericanomall.common.api.Result;
import org.icedamericanomall.trade.adapter.request.WithdrawalPageReq;
import org.icedamericanomall.trade.adapter.response.SettlementVO;
import org.icedamericanomall.trade.adapter.response.WithdrawalVO;
import org.icedamericanomall.trade.app.finance.SettlementManager;
import org.icedamericanomall.trade.app.finance.WithdrawalManager;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * 管理后台 — 财务结算。
 *
 * <p><b>审计（ADR-032 管道一）</b>：生成结算单与提现审核两个写操作标注 {@code @OperationLog}，
 * 产出单行 JSON 审计日志（logger {@code AUDIT}，经 ELK 采集，不落库）；
 * 两者入参均为标识/状态/日期，故开启参数摘要（非 id 类值仍只记类型名）。
 */
@RestController
// 权限位拆分（5a 批 6，2026-10-18）：财务结算独立成位 finance:admin（Q2 资金面独立）。
// 已核（grep 全仓）：提现状态只有 PENDING_REVIEW / PAID_OUT / REJECTED，**没有 APPROVED**；
// 也**没有下游打款消费者** —— 打款在系统外完成，review 只是把状态标成 PAID_OUT。
// 但请注意它的语义：这个动作是在**断言"钱已经打出去了"**（资金事实）
// ⇒ 若将来接入自动打款（同步调 wallet/pay），必须把该方法改挂 pay:admin
//   （参照 after-sale 那批：两个写方法因同步退款而挂 pay:admin 的做法）。
// T-WITHDRAW 第一步起，`PAID_OUT` 还会在权威资金账户上记账（paid_total += amount），
// 且**余额不足时整笔拒绝**（0 行 = 不改任何字段）—— 它已经不只是"打个状态标记"。
@PreAuthorize("@ss.hasPermi('finance:admin')")
@RequestMapping("/api/admin/finance")
@RequiredArgsConstructor
public class AdminFinanceController {

    /** 审计模块：交易域财务结算。 */
    private static final String MODULE_TRADE_FINANCE = "TRADE_FINANCE";

    /** 审计动作：手动生成结算单。 */
    private static final String ACTION_GENERATE_SETTLEMENT = "GENERATE_SETTLEMENT";

    /** 审计动作：审核提现。 */
    private static final String ACTION_REVIEW_WITHDRAWAL = "REVIEW_WITHDRAWAL";

    private final SettlementManager settlementManager;
    private final WithdrawalManager withdrawalManager;

    /** 手动生成结算单 */
    @OperationLog(module = MODULE_TRADE_FINANCE, action = ACTION_GENERATE_SETTLEMENT,
            description = "生成结算单", recordParams = true)
    @PostMapping("/settlement/generate")
    public Result<SettlementVO> generate(@RequestParam(name = "seller_id") Long sellerId,
                                         @RequestParam(name = "period_start") String periodStart,
                                         @RequestParam(name = "period_end") String periodEnd) {
        return Result.ok(settlementManager.generate(sellerId, periodStart, periodEnd));
    }

    /** 结算单列表 */
    @GetMapping("/settlement/page")
    public Result<IPage<SettlementVO>> pageSettlement(@RequestParam(defaultValue = "1") int page,
                                                      @RequestParam(defaultValue = "20") int size) {
        return Result.ok(settlementManager.pageAll(page, size));
    }

    /**
     * 提现审核列表（query: status / page / size，属性绑定）。
     * 管理端视图保留明文账号（打款核验需要）+ 脱敏号。
     */
    @GetMapping("/withdrawal/page")
    public Result<IPage<WithdrawalVO>> pageWithdrawal(@QueryModel WithdrawalPageReq req) {
        return Result.ok(withdrawalManager.pageByStatus(req.getStatus(), req.getPage(), req.getSize()));
    }

    /**
     * 审核提现（打款成功时在商家资金账户上记账 {@code paid_total += amount}）。
     *
     * <p>口径变更（T-WITHDRAW 第一步）：<b>不再</b>把该商家的结算单全量置为已打款 ——
     * 那是「提现 100 会把 1000 全置成已打款、剩 900 看似再也提不出来」的成因（T-WITHDRAW-2）。
     * 口径（T-WITHDRAW 第一步）：`settlement.status` 不承载打款事实 —— `SETTLED` 只表示「可提现」，
     * 「已打款」的唯一事实源是 {@code withdrawal.status = PAID_OUT}（聚合看 {@code seller_fund_account.paid_total}）。
     * 故打款后**不回写**结算单状态；`markSellerSettlementsPaidOut` 的「按商家全量置位」已删除且不打算恢复（T-WITHDRAW-2）。
     * 「结算单列表要区分已打款」属读模型（join `withdrawal`），不是改行状态。
     */
    @OperationLog(module = MODULE_TRADE_FINANCE, action = ACTION_REVIEW_WITHDRAWAL,
            description = "审核提现", recordParams = true)
    @PutMapping("/withdrawal/{id}/review")
    public Result<Void> reviewWithdrawal(@PathVariable Long id, @RequestParam Integer status,
                                         @RequestParam(required = false) String remark) {
        withdrawalManager.review(id, status, remark);
        return Result.ok();
    }
}
