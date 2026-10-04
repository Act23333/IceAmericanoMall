package org.icedamericanomall.ai.infrastructure.adapter;

import lombok.RequiredArgsConstructor;
import org.icedamericanomall.ai.domain.model.product.ProductDetailInfo;
import org.icedamericanomall.ai.domain.port.ProductDetailPort;
import org.icedamericanomall.client.ProductDetailClient;
import org.icedamericanomall.common.api.Result;
import org.icedamericanomall.dto.ProductDetailCO;
import org.springframework.stereotype.Component;

/**
 * {@link ProductDetailPort} 的 Feign 实现（防腐层）——把 item-service 的 CO 转成本上下文领域对象。
 */
@Component
@RequiredArgsConstructor
public class FeignProductDetailAdapter implements ProductDetailPort {

    private final ProductDetailClient productDetailClient;

    @Override
    public ProductDetailInfo getProductDetail(Long productId) {
        Result<ProductDetailCO> result = productDetailClient.getProductDetail(productId);
        if (result == null || result.getData() == null) {
            return null;
        }
        ProductDetailCO detail = result.getData();
        return new ProductDetailInfo(detail.productId(), detail.name(), detail.brand(),
                detail.description(), detail.soldCount(), detail.attributes());
    }
}
