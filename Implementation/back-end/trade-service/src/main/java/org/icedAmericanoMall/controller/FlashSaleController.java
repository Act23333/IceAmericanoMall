package org.icedAmericanoMall.controller;

import lombok.RequiredArgsConstructor;
import org.icedAmericanoMall.domain.entity.FlashSaleEntity;
import org.icedAmericanoMall.service.FlashSaleService;
import org.noLazy.common.domain.Result;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/flash")
@RequiredArgsConstructor
public class FlashSaleController {

    private final FlashSaleService flashSaleService;

    @GetMapping
    public Result<List<FlashSaleEntity>> list() {
        return Result.ok(flashSaleService.listActive());
    }

    @PostMapping("/buy")
    public Result<?> buy(@RequestParam Long flashId) {
        boolean ok = flashSaleService.buy(flashId);
        if (ok) {
            FlashSaleEntity fs = flashSaleService.getById(flashId);
            return Result.ok(Map.of("success", true, "flashPrice", fs.getFlashPrice(),
                    "productId", fs.getProductId()));
        }
        return Result.error("抢购失败，请重试");
    }
}
