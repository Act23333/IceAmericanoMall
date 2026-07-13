package org.icedAmericanoMall.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.RequiredArgsConstructor;
import org.icedAmericanoMall.convert.TradeConverter;
import org.icedAmericanoMall.domain.dto.WithdrawalApplyReq;
import org.icedAmericanoMall.domain.vo.SettlementVO;
import org.icedAmericanoMall.domain.vo.WithdrawalVO;
import org.icedAmericanoMall.service.SettlementService;
import org.icedAmericanoMall.service.WithdrawalService;
import org.noLazy.common.domain.Result;
import org.noLazy.common.utils.UserContext;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** 商家财务中心 */
@RestController
@RequestMapping("/api/seller/finance")
@RequiredArgsConstructor
public class SellerFinanceController {

    private final SettlementService settlementService;
    private final WithdrawalService withdrawalService;
    private final TradeConverter tradeConverter;

    /** 结算单列表 */
    @GetMapping("/settlement/page")
    public Result<IPage<SettlementVO>> pageSettlement(@RequestParam(defaultValue = "1") int page,
                                                      @RequestParam(defaultValue = "20") int size) {
        return Result.ok(settlementService.pageBySeller(UserContext.getUser(), page, size)
                .convert(tradeConverter::toVO));
    }

    /** 结算单详情 */
    @GetMapping("/settlement/{id}")
    public Result<SettlementVO> settlementDetail(@PathVariable Long id) {
        return Result.ok(tradeConverter.toVO(
                settlementService.getSellerSettlement(id, UserContext.getUser())));
    }

    /** 可提现余额 = 所有已结算未打款金额 */
    @GetMapping("/balance")
    public Result<Map<String, Object>> balance() {
        Long sellerId = UserContext.getUser();
        return Result.ok(Map.of("sellerId", sellerId, "balance", settlementService.availableBalance(sellerId)));
    }

    /** 申请提现 */
    @PostMapping("/withdrawal")
    public Result<WithdrawalVO> withdraw(@RequestBody WithdrawalApplyReq req) {
        return Result.ok(tradeConverter.toVO(
                withdrawalService.applyWithdrawal(UserContext.getUser(), req)));
    }

    /** 提现记录 */
    @GetMapping("/withdrawal/page")
    public Result<IPage<WithdrawalVO>> pageWithdrawal(@RequestParam(defaultValue = "1") int page,
                                                      @RequestParam(defaultValue = "20") int size) {
        return Result.ok(withdrawalService.pageBySeller(UserContext.getUser(), page, size)
                .convert(tradeConverter::toVO));
    }
}
