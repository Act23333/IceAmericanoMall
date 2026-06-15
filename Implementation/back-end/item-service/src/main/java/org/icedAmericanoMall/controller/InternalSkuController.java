package org.icedAmericanoMall.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.icedAmericanoMall.convert.SkuConverter;
import org.icedAmericanoMall.domain.dto.StockOpReq;
import org.icedAmericanoMall.domain.entity.SkuEntity;
import org.icedAmericanoMall.dto.SkuDTO;
import org.icedAmericanoMall.service.SkuService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/internal/item/sku")
@RequiredArgsConstructor
public class InternalSkuController {

    private final SkuService skuService;
    private final SkuConverter skuConverter;

    @GetMapping("/list/batch")
    public List<SkuDTO> getSkuListByIds(@RequestParam("ids") List<Long> skuIds) {
        List<SkuEntity> entities = skuService.getSkuListByIds(skuIds);
        return skuConverter.entitiesToDTOs(entities);
    }

    /**
     * Batch deduct stock (for order creation).
     * Internal endpoint — returns raw void, no Result wrapping.
     */
    @PostMapping("/deduct")
    public void deductStock(@RequestBody @Valid List<StockOpReq> items) {
        for (StockOpReq item : items) {
            skuService.deductStock(item.getSkuId(), item.getQuantity());
        }
    }

    /**
     * Batch restore stock (for order cancellation / timeout).
     * Internal endpoint — returns raw void, no Result wrapping.
     */
    @PostMapping("/restore")
    public void restoreStock(@RequestBody @Valid List<StockOpReq> items) {
        for (StockOpReq item : items) {
            skuService.restoreStock(item.getSkuId(), item.getQuantity());
        }
    }
}
