package org.icedamericanomall.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.RequiredArgsConstructor;
import org.icedamericanomall.convert.TradeConverter;
import org.icedamericanomall.domain.dto.AfterSaleApplyReq;
import org.icedamericanomall.domain.vo.AfterSaleVO;
import org.icedamericanomall.service.AfterSaleService;
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
