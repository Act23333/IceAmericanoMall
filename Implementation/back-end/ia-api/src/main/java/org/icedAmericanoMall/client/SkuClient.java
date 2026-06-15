package org.icedAmericanoMall.client;

import org.icedAmericanoMall.dto.SkuDTO;
import org.icedAmericanoMall.dto.StockOpDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@FeignClient(name = "item-service", path = "/internal/item/sku")
public interface SkuClient {

    @GetMapping("/list/batch")
    List<SkuDTO> getSkuListByIds(@RequestParam("ids") List<Long> skuIds);

    /**
     * Batch deduct stock — used during order creation.
     */
    @PostMapping("/deduct")
    void deductStock(@RequestBody List<StockOpDTO> items);

    /**
     * Batch restore stock — used on order cancellation / timeout.
     */
    @PostMapping("/restore")
    void restoreStock(@RequestBody List<StockOpDTO> items);
}
