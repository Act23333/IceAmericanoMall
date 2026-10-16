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
 *
 * <p><b>入参口径（ADR-058 §1② / ADR-061 §决策 2）</b>：查询参数 {@code sku_ids} 收
 * <b>SKU 业务键</b>（32-hex {@code String}），与 {@code SkuCO.skuId} 同型同义 ——
 * 「传入的业务键 → 返回报文里同一个业务键」可直接互操作。item-service 侧按
 * {@code sku.sku_id} 唯一索引批量解析（A 案：{@code cart.sku_id} 等内部外键保持技术键）。
 */
@FeignClient(name = "item-service", path = "/internal/item/sku", contextId = "sku",
        fallbackFactory = SkuClientFallback.class)
public interface SkuClient {

    /** 批量查 SKU（线格式查询参数 `sku_ids`，逗号分隔的 SKU 业务键；与 item-service `InternalSkuController` 字面一致）。 */
    @GetMapping("/list/batch")
    List<SkuCO> getSkuListByIds(@RequestParam(name = "sku_ids") List<String> skuIds);

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
