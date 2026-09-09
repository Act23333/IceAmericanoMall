package org.icedamericanomall.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.convert.SkuConverter;
import org.icedamericanomall.domain.dto.StockOpReq;
import org.icedamericanomall.domain.entity.SkuEntity;
import org.icedamericanomall.dto.SkuDTO;
import org.icedamericanomall.service.ProductService;
import org.icedamericanomall.service.SkuService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/internal/item/sku")
@RequiredArgsConstructor
public class InternalSkuController {

    private final SkuService skuService;
    private final ProductService productService;
    private final SkuConverter skuConverter;

    @GetMapping("/list/batch")
    public List<SkuDTO> getSkuListByIds(@RequestParam("ids") List<Long> skuIds) {
        List<SkuEntity> entities = skuService.getSkuListByIds(skuIds);
        List<SkuDTO> dtos = skuConverter.entitiesToDTOs(entities);

        // Enrich with sellerId and categoryId from associated products
        List<Long> productIds = entities.stream()
                .map(SkuEntity::getProductId)
                .distinct()
                .toList();
        Map<Long, Long> productSellerMap = productService.getSellerIdMapByProductIds(productIds);
        Map<Long, Long> productCategoryMap = productService.getCategoryIdMapByProductIds(productIds);

        for (int i = 0; i < dtos.size(); i++) {
            SkuEntity entity = entities.get(i);
            dtos.get(i).setSellerId(productSellerMap.get(entity.getProductId()));
            dtos.get(i).setCategoryId(productCategoryMap.get(entity.getProductId())); // V4.3
        }
        return dtos;
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
