package org.icedAmericanoMall.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import org.icedAmericanoMall.domain.dto.CreateProductReq;
import org.icedAmericanoMall.domain.entity.ProductEntity;
import org.icedAmericanoMall.domain.vo.ProductVO;

public interface ProductService extends IService<ProductEntity> {

    IPage<ProductVO> pageProducts(Long categoryId, String keyword, String sort, String order, int page, int size);

    ProductVO getProductDetail(Long id);

    void createProduct(Long sellerId, CreateProductReq req);

    void updateProductStatus(Long productId, Integer status);
}
