package org.icedamericanomall.item.app;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.icedamericanomall.client.PromotionClient;
import org.icedamericanomall.client.ProductVectorClient;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.dto.ProductVectorUpsertCO;
import org.icedamericanomall.dto.PromotionCO;
import org.icedamericanomall.dto.PromotionQueryCO;
import org.icedamericanomall.item.adapter.request.CreateProductReq;
import org.icedamericanomall.item.adapter.request.UpdateProductReq;
import org.icedamericanomall.item.adapter.response.ProductVO;
import org.icedamericanomall.item.adapter.response.RecommendProductVO;
import org.icedamericanomall.item.domain.enums.ProductStatusEnum;
import org.icedamericanomall.item.domain.model.product.Product;
import org.icedamericanomall.item.domain.model.sku.Sku;
import org.icedamericanomall.item.domain.repository.ProductRepository;
import org.icedamericanomall.item.domain.repository.SkuRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * ProductManager 单元测试 —— 详情/发布/编辑（归属校验）与状态变更、相似推荐组装、向量摄取钩子。
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

    @Mock
    private ProductVectorClient productVectorClient;

    @Mock
    private PromotionClient promotionClient;

    @Mock
    private org.icedamericanomall.item.app.kg.KgEdgeIngestManager kgEdgeIngestManager;

    private ProductManager productManager;

    @BeforeEach
    void setUp() {
        productManager = new ProductManager(productRepository, skuRepository,
                new org.icedamericanomall.item.adapter.assembler.ProductConverterImpl(),
                new org.icedamericanomall.item.adapter.assembler.SkuConverterImpl(),
                freightTemplateManager, new SkuManager(skuRepository,
                new org.icedamericanomall.item.adapter.assembler.SkuConverterImpl(),
                // 商品展示字段回填端口：本测试不覆盖该链路（真实 SQL 语义见 SkuProductDisplayQueryImplH2Test）
                skuIds -> java.util.Map.of()),
                productVectorClient, new ProductRecommendManager(productVectorClient),
                new ProductPromotionManager(promotionClient), kgEdgeIngestManager);
    }

    @AfterEach
    void tearDown() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private Product product(Long sellerId) {
        return Product.publish(sellerId, 10L, null, "手机", "img.jpg", "旗舰", "品牌A");
    }

    private Product productWithId(Long sellerId, Long id) {
        Product p = product(sellerId);
        p.setId(id);
        p.setSoldCount(0);
        return p;
    }

    private CreateProductReq createReq() {
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
        return req;
    }

    /** 模拟事务提交：触发已注册同步器的 afterCommit（摄取钩子测试用）。 */
    private void commitTransaction() {
        for (TransactionSynchronization sync : TransactionSynchronizationManager.getSynchronizations()) {
            sync.afterCommit();
        }
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

    @Test
    @DisplayName("getProductDetail — 店铺有进行中促销 → promotions 回填满减/满折标签（V5.11 增量）")
    void shouldFillPromotions_whenShopHasActivePromotion() {
        when(productRepository.findById(1L)).thenReturn(product(100L));
        when(skuRepository.findByProductId(1L)).thenReturn(List.of(
                org.icedamericanomall.item.domain.model.sku.Sku.builder()
                        .id(1L).productId(1L).price(599900).status(1).build()));
        when(promotionClient.queryActive(any(PromotionQueryCO.class))).thenReturn(List.of(
                new PromotionCO(1L, "满1000减200", 100L, 1, 100000, 20000, 0, null),
                new PromotionCO(2L, "满500打8.5折", 100L, 2, 50000, 8500, 10000, null)));

        ProductVO vo = productManager.getProductDetail(1L);

        assertEquals(2, vo.getPromotions().size());
        assertEquals("FULL_REDUCE", vo.getPromotions().get(0).getType());
        assertEquals("LIMIT_DISCOUNT", vo.getPromotions().get(1).getType());
        assertEquals(20000, vo.getPromotions().get(0).getDiscountValue());
    }

    @Test
    @DisplayName("getProductDetail — 促销查询失败（fail-open）→ 详情正常返回，promotions 为空列表")
    void shouldStillReturnDetail_whenPromotionQueryFails() {
        when(productRepository.findById(1L)).thenReturn(product(100L));
        when(skuRepository.findByProductId(1L)).thenReturn(List.of(
                org.icedamericanomall.item.domain.model.sku.Sku.builder()
                        .id(1L).productId(1L).price(599900).status(1).build()));
        when(promotionClient.queryActive(any(PromotionQueryCO.class)))
                .thenThrow(new BizException(ErrorCode.SYSTEM_BUSY, "促销服务暂不可用"));

        ProductVO vo = productManager.getProductDetail(1L);

        assertEquals("手机", vo.getName());
        assertNotNull(vo.getPromotions());
        assertTrue(vo.getPromotions().isEmpty());
    }

    // ==================== createProduct ====================

    @Test
    @DisplayName("createProduct — 发布聚合 + 逐 SKU 落库（含热销元数据归一）")
    void shouldPublishProductAndSkus() {
        when(productRepository.save(any(Product.class))).thenReturn(productWithId(100L, 1L));

        productManager.createProduct(100L, createReq());

        verify(productRepository).save(any(Product.class));
        verify(skuRepository).save(any(Sku.class));
    }

    @Test
    @DisplayName("createProduct — 事务提交后摄取向量：CO 字段取自落库聚合，提交前不发送")
    void shouldIngestVectorAfterCommit_whenCreateProduct() {
        TransactionSynchronizationManager.initSynchronization();
        Product saved = productWithId(100L, 1L);
        when(productRepository.save(any(Product.class))).thenReturn(saved);
        when(skuRepository.findOnSaleByProductIds(List.of(1L))).thenReturn(List.of(
                Sku.builder().id(9L).productId(1L).price(800).status(1).build()));

        productManager.createProduct(100L, createReq());

        // 提交前（事务内）绝不发送 Feign
        verify(productVectorClient, never()).upsert(any(ProductVectorUpsertCO.class));
        commitTransaction();

        ArgumentCaptor<ProductVectorUpsertCO> captor = ArgumentCaptor.forClass(ProductVectorUpsertCO.class);
        verify(productVectorClient).upsert(captor.capture());
        ProductVectorUpsertCO co = captor.getValue();
        assertEquals(1L, co.getProductId());
        assertEquals("手机", co.getName());
        assertEquals("旗舰", co.getDescription());
        assertEquals("品牌A", co.getBrand());
        assertEquals(10L, co.getCategoryId());
        assertEquals("img.jpg", co.getMainImage());
        assertEquals(800, co.getPrice());
        assertEquals(0, co.getSoldCount());
    }

    @Test
    @DisplayName("createProduct — 事务回滚（afterCommit 未触发）→ 不摄取")
    void shouldNotIngest_whenTransactionRolledBack() {
        TransactionSynchronizationManager.initSynchronization();
        Product saved = productWithId(100L, 1L);
        when(productRepository.save(any(Product.class))).thenReturn(saved);
        when(skuRepository.findOnSaleByProductIds(List.of(1L))).thenReturn(List.of());

        productManager.createProduct(100L, createReq());

        // 回滚语义：同步器随事务丢弃，afterCommit 永不触发
        TransactionSynchronizationManager.clearSynchronization();

        verify(productVectorClient, never()).upsert(any(ProductVectorUpsertCO.class));
    }

    @Test
    @DisplayName("createProduct — 绑定他人/不存在模板 → 3041 且不落库、不摄取")
    void shouldReject_whenFreightTemplateNotBindable() {
        CreateProductReq req = createReq();
        req.setFreightTemplateId(99L);
        doThrow(new BizException(ErrorCode.FREIGHT_TEMPLATE_NOT_FOUND, "运费模板不存在"))
                .when(freightTemplateManager).requireBindable(100L, 99L);

        BizException ex = assertThrows(BizException.class, () -> productManager.createProduct(100L, req));
        assertEquals(ErrorCode.FREIGHT_TEMPLATE_NOT_FOUND, ex.getErrorCode());
        verify(productRepository, never()).save(any(Product.class));
        verify(productVectorClient, never()).upsert(any(ProductVectorUpsertCO.class));
    }

    @Test
    @DisplayName("createProduct — 未绑定模板（null）：不校验归属，落库 freightTemplateId 为 null")
    void shouldSkipBindableCheck_whenNoTemplateBound() {
        when(productRepository.save(any(Product.class))).thenReturn(productWithId(100L, 1L));
        CreateProductReq req = createReq();
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
        when(productRepository.findById(1L)).thenReturn(productWithId(100L, 1L));

        UpdateProductReq req = new UpdateProductReq();
        req.setName("新款手机");
        req.setFreightTemplateId(5L);
        productManager.updateProduct(1L, 100L, req);

        verify(freightTemplateManager).requireBindable(100L, 5L);
        verify(productRepository).updateEditable(eq(1L), eq("新款手机"), any(), any(), any(), any(), eq(5L));
    }

    @Test
    @DisplayName("updateProduct — 编辑成功后摄取合并态（PATCH 后重读，name 为新值、其余保留原值），无事务直接发送")
    void shouldIngestMergedState_whenUpdateProduct() {
        Product before = productWithId(100L, 1L);
        Product after = productWithId(100L, 1L);
        after.setName("新款手机");
        after.setCategoryId(20L);
        when(productRepository.findById(1L)).thenReturn(before, after);
        when(skuRepository.findOnSaleByProductIds(List.of(1L))).thenReturn(List.of(
                Sku.builder().id(9L).productId(1L).price(1200).status(1).build()));

        UpdateProductReq req = new UpdateProductReq();
        req.setName("新款手机");
        req.setCategoryId(20L);
        productManager.updateProduct(1L, 100L, req);

        ArgumentCaptor<ProductVectorUpsertCO> captor = ArgumentCaptor.forClass(ProductVectorUpsertCO.class);
        verify(productVectorClient).upsert(captor.capture());
        ProductVectorUpsertCO co = captor.getValue();
        assertEquals(1L, co.getProductId());
        assertEquals("新款手机", co.getName());
        assertEquals("旗舰", co.getDescription());
        assertEquals("品牌A", co.getBrand());
        assertEquals(20L, co.getCategoryId());
        assertEquals("img.jpg", co.getMainImage());
        assertEquals(1200, co.getPrice());
    }

    // ==================== updateProductStatus ====================

    @Test
    @DisplayName("updateProductStatus — 存在：状态变更落库")
    void shouldUpdateStatus_whenExists() {
        when(productRepository.findById(1L)).thenReturn(productWithId(100L, 1L));

        productManager.updateProductStatus(1L, 100L, false, ProductStatusEnum.OFF_SHELF.getCode());

        verify(productRepository).updateStatus(1L, ProductStatusEnum.OFF_SHELF.getCode());
    }

    @Test
    @DisplayName("updateProductStatus — 不存在 → PRODUCT_NOT_FOUND")
    void shouldThrowNotFound_whenStatusChangeOnMissing() {
        when(productRepository.findById(1L)).thenReturn(null);

        BizException ex = assertThrows(BizException.class,
                () -> productManager.updateProductStatus(1L, 100L, false, ProductStatusEnum.OFF_SHELF.getCode()));
        assertEquals(ErrorCode.PRODUCT_NOT_FOUND, ex.getErrorCode());
    }

    @Test
    @DisplayName("updateProductStatus — 越权防护：非商品归属商家 → FORBIDDEN 且不落库")
    void shouldRejectStatusChange_whenNotOwner() {
        when(productRepository.findById(1L)).thenReturn(productWithId(100L, 1L));

        BizException ex = assertThrows(BizException.class, () -> productManager
                .updateProductStatus(1L, 999L, false, ProductStatusEnum.OFF_SHELF.getCode()));
        assertEquals(ErrorCode.FORBIDDEN, ex.getErrorCode());
        verify(productRepository, never()).updateStatus(anyLong(), anyInt());
    }

    @Test
    @DisplayName("updateProductStatus — 管理员放行：非归属商家亦可上下架")
    void shouldAllowStatusChange_whenAdmin() {
        when(productRepository.findById(1L)).thenReturn(productWithId(100L, 1L));

        productManager.updateProductStatus(1L, 999L, true, ProductStatusEnum.OFF_SHELF.getCode());

        verify(productRepository).updateStatus(1L, ProductStatusEnum.OFF_SHELF.getCode());
    }

    @Test
    @DisplayName("updateProductStatus — 上下架不摄取向量（内容未变，下架商品由 P27 组装过滤，ADR-046）")
    void shouldNotIngest_whenUpdateStatus() {
        when(productRepository.findById(1L)).thenReturn(productWithId(100L, 1L));

        productManager.updateProductStatus(1L, 100L, false, ProductStatusEnum.OFF_SHELF.getCode());

        verify(productVectorClient, never()).upsert(any(ProductVectorUpsertCO.class));
    }

    // ==================== similarProducts（P27，ADR-046） ====================

    @Test
    @DisplayName("similarProducts — 按命中顺序（相似度降序）组装，不随批量查询结果顺序漂移")
    void shouldAssembleInHitOrder_whenSimilarHits() {
        Product p5 = productWithId(200L, 5L);
        p5.setSoldCount(11);
        when(productVectorClient.similar(1L, 5)).thenReturn(List.of(
                new org.icedamericanomall.dto.ProductVectorHitCO(5L, 0.92),
                new org.icedamericanomall.dto.ProductVectorHitCO(3L, 0.71)));
        when(productRepository.findAllByIds(List.of(5L, 3L))).thenReturn(List.of(
                productWithId(300L, 3L), p5));
        // 最低价批量查询与顺序无关（最终顺序由命中序列恢复），anyList 匹配
        when(skuRepository.findOnSaleByProductIds(anyList())).thenReturn(List.of(
                Sku.builder().id(51L).productId(5L).price(500).status(1).build(),
                Sku.builder().id(31L).productId(3L).price(300).status(1).build()));

        List<RecommendProductVO> result = productManager.similarProducts(1L, null);

        assertEquals(2, result.size());
        assertEquals(5L, result.get(0).getProductId());
        assertEquals(3L, result.get(1).getProductId());
        assertEquals(500, result.get(0).getPrice());
        assertEquals(11, result.get(0).getSoldCount());
        assertEquals("手机", result.get(0).getName());
    }

    @Test
    @DisplayName("similarProducts — 恒过滤在售并剔除自身：非在售/自身命中不出卡")
    void shouldFilterNonOnSaleAndSelf_whenAssembling() {
        when(productVectorClient.similar(1L, 5)).thenReturn(List.of(
                new org.icedamericanomall.dto.ProductVectorHitCO(1L, 0.99),
                new org.icedamericanomall.dto.ProductVectorHitCO(2L, 0.9),
                new org.icedamericanomall.dto.ProductVectorHitCO(3L, 0.8)));
        Product self = productWithId(100L, 1L);
        Product offShelf = productWithId(200L, 2L);
        offShelf.setStatus(ProductStatusEnum.OFF_SHELF.getCode());
        Product onShelf = productWithId(300L, 3L);
        when(productRepository.findAllByIds(List.of(1L, 2L, 3L)))
                .thenReturn(List.of(self, offShelf, onShelf));
        when(skuRepository.findOnSaleByProductIds(List.of(3L))).thenReturn(List.of(
                Sku.builder().id(31L).productId(3L).price(300).status(1).build()));

        List<RecommendProductVO> result = productManager.similarProducts(1L, null);

        assertEquals(1, result.size());
        assertEquals(3L, result.get(0).getProductId());
    }

    @Test
    @DisplayName("similarProducts — 命中为空 → 空列表不报错，不触发本地批量查询")
    void shouldReturnEmpty_whenClientReturnsEmpty() {
        when(productVectorClient.similar(1L, 5)).thenReturn(List.of());

        List<RecommendProductVO> result = productManager.similarProducts(1L, null);

        assertTrue(result.isEmpty());
        verify(productRepository, never()).findAllByIds(any());
    }

    @Test
    @DisplayName("similarProducts — client 抛异常（防御层）→ 空列表不报错")
    void shouldReturnEmpty_whenClientThrows() {
        when(productVectorClient.similar(1L, 5)).thenThrow(new RuntimeException("search-service 不可用"));

        List<RecommendProductVO> result = productManager.similarProducts(1L, null);

        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("similarProducts — size 归一：缺省 5、上限 20（与服务端 I48 归一一致）")
    void shouldNormalizeSize_whenSimilar() {
        when(productVectorClient.similar(1L, 5)).thenReturn(List.of());
        when(productVectorClient.similar(2L, 20)).thenReturn(List.of());

        productManager.similarProducts(1L, null);
        productManager.similarProducts(2L, 50);

        verify(productVectorClient).similar(1L, 5);
        verify(productVectorClient).similar(2L, 20);
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
