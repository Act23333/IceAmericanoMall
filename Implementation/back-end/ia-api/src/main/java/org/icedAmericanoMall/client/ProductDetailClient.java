package org.icedAmericanoMall.client;

import org.icedAmericanoMall.config.DefaultFeignConfig;
import org.icedAmericanoMall.dto.ProductDetailDTO;
import org.noLazy.common.domain.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * V3.0: Feign client for item-service product detail.
 * Used by ai-service Search/Compare Subagents.
 */
@FeignClient(
    name = "item-service",
    path = "/api/item/product",
    configuration = DefaultFeignConfig.class
)
public interface ProductDetailClient {

    /**
     * 获取商品详情。
     */
    @GetMapping("/{id}")
    Result<ProductDetailDTO> getProductDetail(@PathVariable Long id);
}
