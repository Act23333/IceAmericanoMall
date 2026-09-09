package org.icedamericanomall.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import org.icedamericanomall.domain.dto.CreateProductReq;
import org.icedamericanomall.domain.dto.UpdateProductReq;
import org.icedamericanomall.domain.entity.ProductEntity;
import org.icedamericanomall.domain.vo.ProductVO;

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

    /** V3.5: 查询商品下的所有已激活 SKU（DDD: Controller→Service→Mapper） */
    List<org.icedamericanomall.domain.entity.SkuEntity> listSkus(Long productId);
}
