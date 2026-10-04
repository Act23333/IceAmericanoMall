package org.icedamericanomall.item.app;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.item.adapter.request.CreateProductReq;
import org.icedamericanomall.item.adapter.request.UpdateProductReq;
import org.icedamericanomall.item.adapter.response.ProductVO;
import org.icedamericanomall.item.domain.enums.ProductStatusEnum;
import org.icedamericanomall.item.domain.model.product.Product;
import org.icedamericanomall.item.domain.repository.ProductRepository;
import org.icedamericanomall.item.domain.repository.SkuRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * ProductManager 单元测试 —— 详情/发布/编辑（归属校验）与状态变更。
 * （原 ProductServiceImplTest 语义迁移：查询沉仓储、编排归本类。）
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ProductManager 编排单元测试")
class ProductManagerTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private SkuRepository skuRepository;

    @Mock
    private FreightTemplateManager freightTemplateManager;

    private ProductManager productManager;

    @BeforeEach
    void setUp() {
        productManager = new ProductManager(productRepository, skuRepository,
                new org.icedamericanomall.item.adapter.assembler.ProductConverterImpl(),
                new org.icedamericanomall.item.adapter.assembler.SkuConverterImpl(),
                freightTemplateManager, new SkuManager(skuRepository,
                new org.icedamericanomall.item.adapter.assembler.SkuConverterImpl()));
    }

    private Product product(Long sellerId) {
        return Product.publish(sellerId, 10L, null, "手机", "img.jpg", "旗舰", "品牌A");
    }

    // ==================== getProductDetail ====================

    @Test
    @DisplayName("getProductDetail — 存在：返回 VO 并附在售 SKU 与最低价")
    void shouldReturnDetailWithSkus_whenProductExists() {
        Product p = product(100L);
        when(productRepository.findById(1L)).thenReturn(p);
        when(skuRepository.findByProductId(1L)).thenReturn(List.of(
                org.icedamericanomall.item.domain.model.sku.Sku.builder()
                        .id(1L).productId(1L).price(599900).status(1).build()));

        ProductVO vo = productManager.getProductDetail(1L);

        assertEquals("手机", vo.getName());
        assertEquals(599900, vo.getMinPrice());
        assertEquals(1, vo.getSkus().size());
    }

    @Test
    @DisplayName("getProductDetail — 不存在 → PRODUCT_NOT_FOUND")
    void shouldThrowNotFound_whenProductMissing() {
        when(productRepository.findById(1L)).thenReturn(null);

        BizException ex = assertThrows(BizException.class, () -> productManager.getProductDetail(1L));
        assertEquals(ErrorCode.PRODUCT_NOT_FOUND, ex.getErrorCode());
    }

    // ==================== createProduct ====================

    @Test
    @DisplayName("createProduct — 发布聚合 + 逐 SKU 落库（含热销元数据归一）")
    void shouldPublishProductAndSkus() {
        CreateProductReq req = new CreateProductReq();
        req.setCategoryId(10L);
        req.setName("手机");
        req.setMainImage("img.jpg");
        req.setDescription("旗舰");
        req.setBrand("品牌A");
        CreateProductReq.SkuReq skuReq = new CreateProductReq.SkuReq();
        skuReq.setSpec("黑色");
        skuReq.setPrice(1000);
        skuReq.setStock(50);
        skuReq.setImage("sku.jpg");
        skuReq.setStockType(null);
        skuReq.setIsHot(true);
        skuReq.setHotReason("best_seller");
        skuReq.setSalesTags("热卖");
        req.setSkus(List.of(skuReq));

        productManager.createProduct(100L, req);

        verify(productRepository).save(any(Product.class));
        verify(skuRepository).save(any(org.icedamericanomall.item.domain.model.sku.Sku.class));
    }

    @Test
    @DisplayName("createProduct — 绑定他人/不存在模板 → 3041 且不落库")
    void shouldReject_whenFreightTemplateNotBindable() {
        CreateProductReq req = new CreateProductReq();
        req.setCategoryId(10L);
        req.setFreightTemplateId(99L);
        req.setSkus(List.of());
        doThrow(new BizException(ErrorCode.FREIGHT_TEMPLATE_NOT_FOUND, "运费模板不存在"))
                .when(freightTemplateManager).requireBindable(100L, 99L);

        BizException ex = assertThrows(BizException.class, () -> productManager.createProduct(100L, req));
        assertEquals(ErrorCode.FREIGHT_TEMPLATE_NOT_FOUND, ex.getErrorCode());
        verify(productRepository, never()).save(any(Product.class));
    }

    @Test
    @DisplayName("createProduct — 未绑定模板（null）：不校验归属，落库 freightTemplateId 为 null")
    void shouldSkipBindableCheck_whenNoTemplateBound() {
        CreateProductReq req = new CreateProductReq();
        req.setCategoryId(10L);
        req.setName("手机");
        req.setSkus(List.of());

        productManager.createProduct(100L, req);

        verify(freightTemplateManager, never()).requireBindable(anyLong(), anyLong());
        ArgumentCaptor<Product> captor = ArgumentCaptor.forClass(Product.class);
        verify(productRepository).save(captor.capture());
        assertNull(captor.getValue().getFreightTemplateId());
    }

    // ==================== updateProduct ====================

    @Test
    @DisplayName("updateProduct — 非本人商品 → FORBIDDEN")
    void shouldThrowForbidden_whenNotOwner() {
        when(productRepository.findById(1L)).thenReturn(product(999L));

        UpdateProductReq req = new UpdateProductReq();
        BizException ex = assertThrows(BizException.class,
                () -> productManager.updateProduct(1L, 100L, req));
        assertEquals(ErrorCode.FORBIDDEN, ex.getErrorCode());
    }

    @Test
    @DisplayName("updateProduct — 本人商品：条件覆盖更新（运费绑定非空即校验）")
    void shouldUpdateEditable_whenOwner() {
        when(productRepository.findById(1L)).thenReturn(product(100L));

        UpdateProductReq req = new UpdateProductReq();
        req.setName("新款手机");
        req.setFreightTemplateId(5L);
        productManager.updateProduct(1L, 100L, req);

        verify(freightTemplateManager).requireBindable(100L, 5L);
        verify(productRepository).updateEditable(eq(1L), eq("新款手机"), any(), any(), any(), any(), eq(5L));
    }

    // ==================== updateProductStatus ====================

    @Test
    @DisplayName("updateProductStatus — 存在：状态变更落库")
    void shouldUpdateStatus_whenExists() {
        when(productRepository.existsById(1L)).thenReturn(true);

        productManager.updateProductStatus(1L, ProductStatusEnum.OFF_SHELF.getCode());

        verify(productRepository).updateStatus(1L, ProductStatusEnum.OFF_SHELF.getCode());
    }

    @Test
    @DisplayName("updateProductStatus — 不存在 → PRODUCT_NOT_FOUND")
    void shouldThrowNotFound_whenStatusChangeOnMissing() {
        when(productRepository.existsById(1L)).thenReturn(false);

        BizException ex = assertThrows(BizException.class,
                () -> productManager.updateProductStatus(1L, ProductStatusEnum.OFF_SHELF.getCode()));
        assertEquals(ErrorCode.PRODUCT_NOT_FOUND, ex.getErrorCode());
    }

    // ==================== 内部契约回填 ====================

    @Test
    @DisplayName("getSellerIdMapByProductIds — 委托仓储")
    void shouldDelegateSellerIdMap() {
        when(productRepository.sellerIdMapByProductIds(List.of(1L))).thenReturn(Map.of(1L, 100L));

        Map<Long, Long> map = productManager.getSellerIdMapByProductIds(List.of(1L));

        assertEquals(100L, map.get(1L));
    }

    // ==================== 分页 ====================

    @Test
    @DisplayName("pageProducts — 关键词分页：最低价回填")
    void shouldFillMinPrice_whenPaging() {
        Product listed = product(100L);
        listed.setId(1L);
        var page = new Page<Product>(1, 10);
        page.setRecords(List.of(listed));
        page.setTotal(1);
        when(productRepository.pageOnSale(null, null, "手机", null, 1, 10)).thenReturn(page);
        when(skuRepository.findOnSaleByProductIds(List.of(1L))).thenReturn(List.of(
                org.icedamericanomall.item.domain.model.sku.Sku.builder()
                        .id(2L).productId(1L).price(599900).status(1).build()));

        ProductVO first = productManager.pageProducts(null, null, "手机", null, null, 1, 10)
                .getRecords().get(0);

        assertEquals("手机", first.getName());
        assertEquals(599900, first.getMinPrice());
    }

    @Test
    @DisplayName("pageProducts — 商品无在售 SKU 时最低价保持 null")
    void shouldKeepMinPriceNull_whenNoOnSaleSku() {
        Product listed = product(100L);
        listed.setId(1L);
        var page = new Page<Product>(1, 10);
        page.setRecords(List.of(listed));
        page.setTotal(1);
        when(productRepository.pageOnSale(null, null, "手机", null, 1, 10)).thenReturn(page);
        when(skuRepository.findOnSaleByProductIds(List.of(1L))).thenReturn(List.of());

        ProductVO first = productManager.pageProducts(null, null, "手机", null, null, 1, 10)
                .getRecords().get(0);

        assertNull(first.getMinPrice());
    }

    @Test
    @DisplayName("pageProducts — sort=price+order=asc → 走最低价升序查询（跨页顺序由 DB 保证）")
    void shouldRouteToMinPriceOrder_whenSortByPrice() {
        Product listed = product(100L);
        listed.setId(1L);
        var page = new Page<Product>(1, 10);
        page.setRecords(List.of(listed));
        page.setTotal(1);
        when(productRepository.pageOrderByMinPrice(1, 10, null, null, "手机", "ASC")).thenReturn(page);
        when(skuRepository.findOnSaleByProductIds(List.of(1L))).thenReturn(List.of());

        productManager.pageProducts(null, null, "手机", "price", "asc", 1, 10);

        verify(productRepository).pageOrderByMinPrice(1, 10, null, null, "手机", "ASC");
        verify(productRepository, never())
                .pageOnSale(any(), any(), any(), any(), anyInt(), anyInt());
    }
}
