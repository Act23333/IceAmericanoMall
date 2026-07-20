package org.icedAmericanoMall.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.RequiredArgsConstructor;
import org.icedAmericanoMall.convert.TradeConverter;
import org.icedAmericanoMall.domain.dto.AfterSaleApplyReq;
import org.icedAmericanoMall.domain.vo.AfterSaleVO;
import org.icedAmericanoMall.service.AfterSaleService;
import org.noLazy.common.domain.Result;
import org.noLazy.common.utils.UserContext;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/after-sale")
@RequiredArgsConstructor
public class AfterSaleController {

    private final AfterSaleService afterSaleService;
    private final TradeConverter tradeConverter;

    @PostMapping
    public Result<AfterSaleVO> apply(@RequestBody AfterSaleApplyReq req) {
        return Result.ok(tradeConverter.toVO(afterSaleService.applyAfterSale(UserContext.getUserId(), req)));
    }

    @GetMapping
    public Result<IPage<AfterSaleVO>> myList(@RequestParam(defaultValue = "1") int page,
                                             @RequestParam(defaultValue = "20") int size) {
        return Result.ok(afterSaleService.pageByUserId(UserContext.getUserId(), page, size)
                .convert(tradeConverter::toVO));
    }
}
