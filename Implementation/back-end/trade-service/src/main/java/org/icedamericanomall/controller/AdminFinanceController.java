package org.icedamericanomall.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.RequiredArgsConstructor;
import org.icedamericanomall.convert.TradeConverter;
import org.icedamericanomall.domain.vo.SettlementVO;
import org.icedamericanomall.domain.vo.WithdrawalVO;
import org.icedamericanomall.service.SettlementService;
import org.icedamericanomall.service.WithdrawalService;
import org.noLazy.common.domain.Result;
import org.springframework.web.bind.annotation.*;

/** 管理后台 — 财务结算 */
@RestController
@RequestMapping("/api/admin/finance")
@RequiredArgsConstructor
public class AdminFinanceController {

    private final SettlementService settlementService;
    private final WithdrawalService withdrawalService;
    private final TradeConverter tradeConverter;

    /** 手动生成结算单 */
    @PostMapping("/settlement/generate")
    public Result<SettlementVO> generate(@RequestParam Long sellerId,
                                         @RequestParam String periodStart,
                                         @RequestParam String periodEnd) {
        return Result.ok(tradeConverter.toVO(settlementService.generate(sellerId, periodStart, periodEnd)));
    }

    /** 结算单列表 */
    @GetMapping("/settlement/page")
    public Result<IPage<SettlementVO>> pageSettlement(@RequestParam(defaultValue = "1") int page,
                                                      @RequestParam(defaultValue = "20") int size) {
        return Result.ok(settlementService.pageAll(page, size).convert(tradeConverter::toVO));
    }

    /** 提现审核列表 */
    @GetMapping("/withdrawal/page")
    public Result<IPage<WithdrawalVO>> pageWithdrawal(@RequestParam(required = false) Integer status,
                                                      @RequestParam(defaultValue = "1") int page,
                                                      @RequestParam(defaultValue = "20") int size) {
        return Result.ok(withdrawalService.pageByStatus(status, page, size).convert(tradeConverter::toVO));
    }

    /** 审核提现（打款成功时级联更新结算单为已打款） */
    @PutMapping("/withdrawal/{id}/review")
    public Result<Void> reviewWithdrawal(@PathVariable Long id, @RequestParam Integer status,
                                         @RequestParam(required = false) String remark) {
        withdrawalService.review(id, status, remark);
        return Result.ok();
    }
}
