package org.icedamericanomall.manager;

import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.RequiredArgsConstructor;
import org.icedamericanomall.domain.dto.CreateProductReq;
import org.icedamericanomall.domain.dto.UpdateProductReq;
import org.icedamericanomall.domain.entity.SkuEntity;
import org.icedamericanomall.domain.vo.ProductVO;
import org.icedamericanomall.service.ProductService;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * V4.0 DDD: 商品编排 Manager（Application层）。
 * 职责: 商品CRUD编排 + SKU聚合 + 详情页数据组装。
 */
@Component
@RequiredArgsConstructor
public class ItemManager {

    private final ProductService productService;

    public IPage<ProductVO> pageProducts(Long categoryId, String keyword, String sort,
                                          String order, int page, int size) {
        return productService.pageProducts(categoryId, keyword, sort, order, page, size);
    }

    public ProductVO getProductDetail(Long id) {
        return productService.getProductDetail(id);
    }

    public List<SkuEntity> listSkus(Long productId) {
        return productService.listSkus(productId);
    }

    public void createProduct(Long sellerId, CreateProductReq req) {
        productService.createProduct(sellerId, req);
    }

    public void updateProduct(Long productId, Long sellerId, UpdateProductReq req) {
        productService.updateProduct(productId, sellerId, req);
    }
}
