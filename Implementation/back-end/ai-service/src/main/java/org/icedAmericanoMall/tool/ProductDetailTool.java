package org.icedAmericanoMall.tool;

import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.icedAmericanoMall.client.ProductDetailClient;
import org.icedAmericanoMall.dto.ProductDetailDTO;
import org.noLazy.common.domain.Result;
import org.springframework.stereotype.Component;

/**
 * V3.0.2: @Tool — 商品详情查询，供 Search/Compare Subagent 使用。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProductDetailTool {

    private final ProductDetailClient productDetailClient;

    @Tool("获取指定商品的详细信息：名称、品牌、描述、属性参数、图片等")
    public String getProductDetail(Long productId) {
        try {
            Result<ProductDetailDTO> result = productDetailClient.getProductDetail(productId);
            if (result == null || result.getData() == null) {
                return "未找到商品 ID=" + productId;
            }
            ProductDetailDTO p = result.getData();
            return String.format("商品详情 — %s (ID:%s)\n品牌:%s\n描述:%s\n销量:%d\n属性:%s",
                    p.name(), p.productId(), p.brand(),
                    p.description(), p.soldCount(), p.attributes());
        } catch (Exception e) {
            log.warn("ProductDetailTool error: {}", e.getMessage());
            return "查询商品详情失败: " + e.getMessage();
        }
    }
}
