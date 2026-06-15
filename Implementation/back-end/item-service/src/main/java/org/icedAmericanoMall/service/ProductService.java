package org.icedAmericanoMall.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import org.icedAmericanoMall.domain.dto.CreateProductReq;
import org.icedAmericanoMall.domain.dto.UpdateProductReq;
import org.icedAmericanoMall.domain.entity.ProductEntity;
import org.icedAmericanoMall.domain.vo.ProductVO;

import java.util.List;
import java.util.Map;

public interface ProductService extends IService<ProductEntity> {

    IPage<ProductVO> pageProducts(Long categoryId, String keyword, String sort, String order, int page, int size);

    ProductVO getProductDetail(Long id);

    void createProduct(Long sellerId, CreateProductReq req);

    void updateProductStatus(Long productId, Integer status);

    /**
     * Get a map of productId → sellerId for batch SKU enrichment (internal Feign use).
     */
    Map<Long, Long> getSellerIdMapByProductIds(List<Long> productIds);

    void updateProduct(Long productId, Long sellerId, UpdateProductReq req);
}
