package org.icedamericanomall.client;

import org.icedamericanomall.dto.SkuCO;
import org.icedamericanomall.dto.StockOpCO;
import org.icedamericanomall.fallback.SkuClientFallback;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * SKU / 库存 Feign client（item-service 内部端点）。
 *
 * <p>降级语义：<b>全部方法 fail-closed</b>（抛 {@code BizException}）。
 * 库存是资金安全关键路径，静默降级等于超卖 / 库存丢失；
 * {@code getSkuListByIds} 的空列表会被三个下单策略翻译成「SKU 不存在」。
 * 详见 {@link SkuClientFallback}。
 */
@FeignClient(name = "item-service", path = "/internal/item/sku", contextId = "sku",
        fallbackFactory = SkuClientFallback.class)
public interface SkuClient {

    /** 批量查 SKU（线格式查询参数 `sku_ids`，逗号分隔；与 item-service `InternalSkuController` 字面一致）。 */
    @GetMapping("/list/batch")
    List<SkuCO> getSkuListByIds(@RequestParam(name = "sku_ids") List<Long> skuIds);

    /**
     * Batch deduct stock — used during order creation.
     */
    @PostMapping("/deduct")
    void deductStock(@RequestBody List<StockOpCO> items);

    /**
     * Batch restore stock — used on order cancellation / timeout.
     */
    @PostMapping("/restore")
    void restoreStock(@RequestBody List<StockOpCO> items);
}
