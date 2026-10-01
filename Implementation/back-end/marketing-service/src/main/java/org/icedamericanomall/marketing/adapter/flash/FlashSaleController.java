package org.icedamericanomall.marketing.adapter.flash;

import lombok.RequiredArgsConstructor;
import org.icedamericanomall.marketing.convert.MarketingConverter;
import org.icedamericanomall.marketing.domain.dto.FlashBuyResult;
import org.icedamericanomall.marketing.domain.entity.FlashSaleEntity;
import org.icedamericanomall.marketing.service.FlashSaleService;
import org.icedamericanomall.common.domain.Result;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/flash")
@RequiredArgsConstructor
public class FlashSaleController {

    private final FlashSaleService flashSaleService;
    private final MarketingConverter marketingConverter;

    @GetMapping
    public Result<List<FlashSaleEntity>> list() {
        return Result.ok(flashSaleService.listActive());
    }

    /** V4.1: 秒杀抢购 — Redis预扣 + 调用 trade-service 创建真实订单（地址必传） */
    @PostMapping("/buy")
    public Result<?> buy(@RequestParam Long flashId, @RequestParam Long addressId) {
        FlashBuyResult result = flashSaleService.buy(flashId, addressId);
        return Result.ok(marketingConverter.toFlashBuyVO(result));
    }
}
