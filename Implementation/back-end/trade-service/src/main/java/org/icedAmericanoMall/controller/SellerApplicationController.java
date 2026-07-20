package org.icedAmericanoMall.controller;

import lombok.RequiredArgsConstructor;
import org.icedAmericanoMall.convert.TradeConverter;
import org.icedAmericanoMall.domain.dto.SellerApplicationApplyReq;
import org.icedAmericanoMall.domain.vo.SellerApplicationVO;
import org.icedAmericanoMall.service.ApplicationService;
import org.noLazy.common.domain.Result;
import org.noLazy.common.utils.UserContext;
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
