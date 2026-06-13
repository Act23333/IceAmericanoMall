package org.icedAmericanoMall.client;

import org.noLazy.common.domain.Result;
import org.icedAmericanoMall.dto.SkuDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@FeignClient(name = "item-service", path = "/internal/item/sku")
public interface SkuClient {

    @GetMapping("/list/batch")
    List<SkuDTO> getSkuListByIds(@RequestParam("ids") List<Long> skuIds);
}
