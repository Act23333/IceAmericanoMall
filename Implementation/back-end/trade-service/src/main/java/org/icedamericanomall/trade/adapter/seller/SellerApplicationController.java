package org.icedamericanomall.trade.adapter.seller;

import lombok.RequiredArgsConstructor;
import org.icedamericanomall.trade.convert.TradeConverter;
import org.icedamericanomall.trade.domain.dto.SellerApplicationApplyReq;
import org.icedamericanomall.trade.domain.vo.SellerApplicationVO;
import org.icedamericanomall.trade.service.ApplicationService;
import org.icedamericanomall.common.domain.Result;
import org.icedamericanomall.common.domain.UserContext;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/seller/apply")
@RequiredArgsConstructor
public class SellerApplicationController {

    private final ApplicationService applicationService;
    private final TradeConverter tradeConverter;

    @PostMapping
    public Result<SellerApplicationVO> apply(@RequestBody SellerApplicationApplyReq req) {
        return Result.ok(tradeConverter.toVO(applicationService.applyForSeller(UserContext.getUserId(), req)));
    }

    @GetMapping
    public Result<SellerApplicationVO> myApplication() {
        return Result.ok(tradeConverter.toVO(applicationService.getLatestApplication(UserContext.getUserId())));
    }
}
