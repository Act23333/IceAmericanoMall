package org.icedamericanomall.trade.adapter.web;

import com.baomidou.mybatisplus.core.metadata.IPage;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.icedamericanomall.common.api.Result;
import org.icedamericanomall.common.security.UserContext;
import org.icedamericanomall.trade.adapter.request.WithdrawalApplyReq;
import org.icedamericanomall.trade.adapter.response.SettlementVO;
import org.icedamericanomall.trade.adapter.response.WithdrawalVO;
import org.icedamericanomall.trade.app.finance.SettlementManager;
import org.icedamericanomall.trade.app.finance.WithdrawalManager;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** 商家财务中心 */
@RestController
@RequestMapping("/api/seller/finance")
@RequiredArgsConstructor
public class SellerFinanceController {

    private final SettlementManager settlementManager;
    private final WithdrawalManager withdrawalManager;

    /** 结算单列表 */
    @GetMapping("/settlement/page")
    public Result<IPage<SettlementVO>> pageSettlement(@RequestParam(defaultValue = "1") int page,
                                                      @RequestParam(defaultValue = "20") int size) {
        return Result.ok(settlementManager.pageBySeller(UserContext.getUserId(), page, size));
    }

    /** 结算单详情 */
    @GetMapping("/settlement/{id}")
    public Result<SettlementVO> settlementDetail(@PathVariable Long id) {
        return Result.ok(settlementManager.getSellerSettlement(id, UserContext.getUserId()));
    }

    /** 可提现余额 = 所有已结算未打款金额 */
    @GetMapping("/balance")
    public Result<Map<String, Object>> balance() {
        Long sellerId = UserContext.getUserId();
        return Result.ok(Map.of("sellerId", sellerId, "balance", settlementManager.availableBalance(sellerId)));
    }

    /** 申请提现 */
    @PostMapping("/withdrawal")
    public Result<WithdrawalVO> withdraw(@Valid @RequestBody WithdrawalApplyReq req) {
        return Result.ok(withdrawalManager.applyWithdrawal(UserContext.getUserId(), req));
    }

    /**
     * 提现记录（商家端视图：仅回脱敏账号 {@code bankAccountMasked}，明文账号不回传 —— 安全模型 12.3）。
     */
    @GetMapping("/withdrawal/page")
    public Result<IPage<WithdrawalVO>> pageWithdrawal(@RequestParam(defaultValue = "1") int page,
                                                      @RequestParam(defaultValue = "20") int size) {
        return Result.ok(withdrawalManager.pageBySeller(UserContext.getUserId(), page, size));
    }
}
