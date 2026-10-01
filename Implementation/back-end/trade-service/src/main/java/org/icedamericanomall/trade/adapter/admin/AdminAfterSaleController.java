package org.icedamericanomall.trade.adapter.admin;

import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.RequiredArgsConstructor;
import org.icedamericanomall.trade.convert.TradeConverter;
import org.icedamericanomall.trade.domain.vo.AfterSaleVO;
import org.icedamericanomall.trade.service.AfterSaleService;
import org.icedamericanomall.common.domain.Result;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/after-sale")
@RequiredArgsConstructor
public class AdminAfterSaleController {

    private final AfterSaleService afterSaleService;
    private final TradeConverter tradeConverter;

    @GetMapping
    public Result<IPage<AfterSaleVO>> list(@RequestParam(required = false) Integer status,
                                           @RequestParam(defaultValue = "1") int page,
                                           @RequestParam(defaultValue = "20") int size) {
        return Result.ok(afterSaleService.pageByStatus(status, page, size).convert(tradeConverter::toVO));
    }

    @PutMapping("/{id}/review")
    public Result<Void> review(@PathVariable Long id, @RequestParam Integer status,
                               @RequestParam(required = false) String remark) {
        afterSaleService.review(id, status, remark);
        return Result.ok();
    }
}
