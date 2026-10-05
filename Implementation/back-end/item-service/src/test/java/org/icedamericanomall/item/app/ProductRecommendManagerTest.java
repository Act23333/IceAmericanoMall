package org.icedamericanomall.item.app;

import org.icedamericanomall.client.ProductVectorClient;
import org.icedamericanomall.dto.ProductVectorUpsertCO;
import org.icedamericanomall.item.domain.enums.ProductStatusEnum;
import org.icedamericanomall.item.domain.model.product.Product;
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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ProductRecommendManager 单元测试 —— 向量摄取旁路（ADR-046）：
 * 事务提交后发送 / 无事务直发 / 回滚不摄取 / 哨兵与异常吞掉主流程零感知。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ProductRecommendManager 向量摄取单元测试")
class ProductRecommendManagerTest {

    @Mock
    private ProductVectorClient productVectorClient;

    private ProductRecommendManager manager;

    @BeforeEach
    void setUp() {
        manager = new ProductRecommendManager(productVectorClient);
    }

    @AfterEach
    void tearDown() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private Product product() {
        Product p = Product.publish(100L, 10L, null, "手机", "img.jpg", "旗舰", "品牌A");
        p.setId(1L);
        return p;
    }

    @Test
    @DisplayName("ingestAfterCommit — 无活动事务（updateProduct 单语句路径）→ 直接发送，CO 字段正确")
    void shouldSendDirectly_whenNoActiveTransaction() {
        manager.ingestAfterCommit(product(), 800);

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
    @DisplayName("ingestAfterCommit — 活动事务内只注册不发送，提交后发送")
    void shouldRegisterAndSendAfterCommit_whenTransactionActive() {
        TransactionSynchronizationManager.initSynchronization();

        manager.ingestAfterCommit(product(), 800);

        verify(productVectorClient, never()).upsert(any(ProductVectorUpsertCO.class));

        TransactionSynchronizationManager.getSynchronizations()
                .forEach(TransactionSynchronization::afterCommit);

        verify(productVectorClient).upsert(any(ProductVectorUpsertCO.class));
    }

    @Test
    @DisplayName("ingestAfterCommit — 事务回滚（同步器随回滚丢弃）→ 不摄取")
    void shouldNotSend_whenTransactionRolledBack() {
        TransactionSynchronizationManager.initSynchronization();

        manager.ingestAfterCommit(product(), 800);

        TransactionSynchronizationManager.clearSynchronization();

        verify(productVectorClient, never()).upsert(any(ProductVectorUpsertCO.class));
    }

    @Test
    @DisplayName("ingestAfterCommit — 降级哨兵 -1 → 只记日志，主流程不抛异常")
    void shouldNotThrow_whenUpsertReturnsSentinel() {
        when(productVectorClient.upsert(any(ProductVectorUpsertCO.class))).thenReturn(-1);

        assertDoesNotThrow(() -> manager.ingestAfterCommit(product(), 800));
    }

    @Test
    @DisplayName("ingestAfterCommit — 发送异常 → 吞掉只记日志，主流程不抛异常")
    void shouldNotThrow_whenUpsertFails() {
        when(productVectorClient.upsert(any(ProductVectorUpsertCO.class)))
                .thenThrow(new RuntimeException("连接超时"));

        assertDoesNotThrow(() -> manager.ingestAfterCommit(product(), 800));
    }

    @Test
    @DisplayName("ingestAfterCommit — 已删除/未落库/null 商品 → 跳过不发送")
    void shouldSkip_whenProductDeletedOrUnsaved() {
        Product deleted = product();
        deleted.setStatus(ProductStatusEnum.DELETED.getCode());
        Product unsaved = product();
        unsaved.setId(null);

        manager.ingestAfterCommit(deleted, 800);
        manager.ingestAfterCommit(unsaved, 800);
        manager.ingestAfterCommit(null, 800);

        verify(productVectorClient, never()).upsert(any(ProductVectorUpsertCO.class));
    }
}
