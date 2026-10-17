package org.icedamericanomall.trade.adapter.web;

import com.baomidou.mybatisplus.core.metadata.IPage;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.icedamericanomall.common.api.Result;
import org.icedamericanomall.common.security.UserContext;
import org.icedamericanomall.trade.adapter.request.WithdrawalApplyReq;
import org.icedamericanomall.trade.adapter.response.SellerBalanceVO;
import org.icedamericanomall.trade.adapter.response.SettlementVO;
import org.icedamericanomall.trade.adapter.response.WithdrawalVO;
import org.icedamericanomall.trade.app.finance.SettlementManager;
import org.icedamericanomall.trade.app.finance.WithdrawalManager;
import org.springframework.web.bind.annotation.*;

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

    /**
     * 可提现余额 = 所有已结算未打款金额（<b>单位：分</b>）。
     *
     * <p><b>批次 D</b>：出参由 {@code Result<Map<String,Object>>}（Controller 内
     * {@code Map.of("sellerId", …, "balance", …)} 就地拼装）改为
     * {@code Result<SellerBalanceVO>}；装配下沉到 {@link SettlementManager#sellerBalance(Long)}。
     * 线上键随之由 camelCase 的 {@code sellerId} 变为契约声明的 {@code seller_id}
     * （<b>线上键 BREAKING</b>，实测前端零消费）。
     */
    @GetMapping("/balance")
    public Result<SellerBalanceVO> balance() {
        return Result.ok(settlementManager.sellerBalance(UserContext.getUserId()));
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
