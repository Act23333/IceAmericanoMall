package org.icedamericanomall.trade.adapter.order;

import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.RequiredArgsConstructor;
import org.icedamericanomall.trade.convert.TradeConverter;
import org.icedamericanomall.trade.domain.dto.AfterSaleApplyReq;
import org.icedamericanomall.trade.domain.vo.AfterSaleVO;
import org.icedamericanomall.trade.service.AfterSaleService;
import org.icedamericanomall.common.domain.Result;
import org.icedamericanomall.common.domain.UserContext;
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
