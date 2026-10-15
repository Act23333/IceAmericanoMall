package org.icedamericanomall.item.adapter.internal;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.dto.SkuCO;
import org.icedamericanomall.item.adapter.request.StockOpReq;
import org.icedamericanomall.item.app.ProductManager;
import org.icedamericanomall.item.app.SkuManager;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/internal/item/sku")
@RequiredArgsConstructor
public class InternalSkuController {

    private final SkuManager skuManager;
    private final ProductManager productManager;

    /** 批量查 SKU（线格式查询参数 `sku_ids`，逗号分隔；Feign 侧 `SkuClient` 同名）。 */
    @GetMapping("/list/batch")
    public List<SkuCO> getSkuListByIds(@RequestParam(name = "sku_ids") List<Long> skuIds) {
        return skuManager.getCosByIds(skuIds);
    }

    /**
     * Batch deduct stock (for order creation).
     * Internal endpoint — returns raw void, no Result wrapping.
     */
    @PostMapping("/deduct")
    public void deductStock(@RequestBody @Valid List<StockOpReq> items) {
        for (StockOpReq item : items) {
            skuManager.deductStock(item.getSkuId(), item.getQuantity());
        }
    }

    /**
     * Batch restore stock (for order cancellation / timeout).
     * Internal endpoint — returns raw void, no Result wrapping.
     */
    @PostMapping("/restore")
    public void restoreStock(@RequestBody @Valid List<StockOpReq> items) {
        for (StockOpReq item : items) {
            skuManager.restoreStock(item.getSkuId(), item.getQuantity());
        }
    }
}
