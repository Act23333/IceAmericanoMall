package org.icedamericanomall.client;

import org.icedamericanomall.config.DefaultFeignConfig;
import org.icedamericanomall.dto.ProductDetailCO;
import org.icedamericanomall.fallback.ProductDetailClientFallback;
import org.icedamericanomall.common.api.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * V3.0: Feign client for item-service product detail.
 * Used by ai-service Search/Compare Subagents.
 *
 * <p>降级语义：<b>fail-closed</b>（抛 {@code BizException}）。虽然商品详情是展示型数据，
 * 但调用方 {@code ProductDetailTool} 把 {@code data == null} 判定为「商品不存在」，
 * 降级返回 null 会污染业务语义。详见 {@link ProductDetailClientFallback}。
 *
 * <p><b>已知偏差（待迁移，V5.3 审计记录）</b>：本客户端走的是 item-service 的<b>公开契约</b>
 * {@code path = "/api/item/product"}，而集群内服务间调用应统一走 {@code /internal/**}
 * （见 ADR-004）。迁移需要 item-service 暴露对应内部端点，超出 ia-api 的改动范围，
 * 本次仅登记偏差；迁移完成前仍按公开契约解析 {@code Result} 信封。
 */
@FeignClient(
    name = "item-service",
    path = "/api/item/product",
    contextId = "product",
    configuration = DefaultFeignConfig.class,
    fallbackFactory = ProductDetailClientFallback.class
)
public interface ProductDetailClient {

    /**
     * 获取商品详情。
     */
    @GetMapping("/{id}")
    Result<ProductDetailCO> getProductDetail(@PathVariable Long id);
}
