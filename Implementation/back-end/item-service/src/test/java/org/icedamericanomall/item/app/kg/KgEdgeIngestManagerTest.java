package org.icedamericanomall.item.app.kg;

import org.icedamericanomall.client.KgClient;
import org.icedamericanomall.dto.KgEdgeItemCO;
import org.icedamericanomall.dto.KgEdgeUpsertCO;
import org.icedamericanomall.fallback.KgClientFallback;
import org.icedamericanomall.item.domain.enums.ProductStatusEnum;
import org.icedamericanomall.item.domain.model.kg.BrandKeys;
import org.icedamericanomall.item.domain.model.kg.KgSchema;
import org.icedamericanomall.item.domain.model.kg.PairingRule;
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

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * KgEdgeIngestManager 单元测试 —— 商品直建边（BELONGS_TO/BRAND_OF/SOLD_BY）摄取载荷、
 * afterCommit 事务纪律、下架清边、规则边投影同步与 fail-open 降级（F15 I57/I59）。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("KgEdgeIngestManager — KG 边摄取旁路收口")
class KgEdgeIngestManagerTest {

    @Mock
    private KgClient kgClient;

    private KgEdgeIngestManager kgEdgeIngestManager;

    @BeforeEach
    void setUp() {
        kgEdgeIngestManager = new KgEdgeIngestManager(kgClient);
    }

    @AfterEach
    void tearDown() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private Product onShelfProduct() {
        Product product = Product.publish(100L, 10L, null, "手机", "img.jpg", "旗舰", "冰美 Brand");
        product.setId(1L);
        product.setStatus(ProductStatusEnum.ON_SHELF.getCode());
        return product;
    }

    /** 模拟事务提交：触发已注册同步器的 afterCommit。 */
    private void commitTransaction() {
        for (TransactionSynchronization sync : TransactionSynchronizationManager.getSynchronizations()) {
            sync.afterCommit();
        }
    }

    // ==================== 商品直建边摄取 ====================

    @Test
    @DisplayName("商品摄取 — 无事务直接发送：载荷含 BELONGS_TO/BRAND_OF/SOLD_BY 三类边（DIRECT/启用/权重0）")
    void shouldUpsertThreeDirectEdges_whenNoTransaction() {
        when(kgClient.edgeUpsert(any(KgEdgeUpsertCO.class))).thenReturn(3);

        kgEdgeIngestManager.ingestProductAfterCommit(onShelfProduct());

        ArgumentCaptor<KgEdgeUpsertCO> captor = ArgumentCaptor.forClass(KgEdgeUpsertCO.class);
        verify(kgClient).edgeUpsert(captor.capture());
        KgEdgeUpsertCO co = captor.getValue();
        assertEquals(KgSchema.NODE_PRODUCT, co.getSrcType());
        assertEquals(1L, co.getSrcId());
        assertEquals(3, co.getEdges().size());

        KgEdgeItemCO belongsTo = co.getEdges().get(0);
        assertEquals(KgSchema.REL_BELONGS_TO, belongsTo.getRelType());
        assertEquals(KgSchema.NODE_CATEGORY, belongsTo.getDstType());
        assertEquals(10L, belongsTo.getDstId());
        assertEquals(KgSchema.SOURCE_DIRECT, belongsTo.getSource());
        assertEquals(KgSchema.EDGE_ENABLED, belongsTo.getStatus());
        assertEquals(0, belongsTo.getWeight());

        KgEdgeItemCO brandOf = co.getEdges().get(1);
        assertEquals(KgSchema.REL_BRAND_OF, brandOf.getRelType());
        assertEquals(KgSchema.NODE_BRAND, brandOf.getDstType());
        assertEquals(BrandKeys.nodeId("冰美 Brand"), brandOf.getDstId());

        KgEdgeItemCO soldBy = co.getEdges().get(2);
        assertEquals(KgSchema.REL_SOLD_BY, soldBy.getRelType());
        assertEquals(KgSchema.NODE_SELLER, soldBy.getDstType());
        assertEquals(100L, soldBy.getDstId());
    }

    @Test
    @DisplayName("商品摄取 — 事务内只注册不发送，afterCommit 后才发送（回滚即不摄取）")
    void shouldSendOnlyAfterCommit_whenTransactionActive() {
        TransactionSynchronizationManager.initSynchronization();
        when(kgClient.edgeUpsert(any(KgEdgeUpsertCO.class))).thenReturn(3);

        kgEdgeIngestManager.ingestProductAfterCommit(onShelfProduct());
        verify(kgClient, never()).edgeUpsert(any(KgEdgeUpsertCO.class));

        commitTransaction();
        verify(kgClient).edgeUpsert(any(KgEdgeUpsertCO.class));
    }

    @Test
    @DisplayName("商品摄取 — 事务回滚（同步器丢弃）→ 不摄取")
    void shouldNotIngest_whenTransactionRolledBack() {
        TransactionSynchronizationManager.initSynchronization();

        kgEdgeIngestManager.ingestProductAfterCommit(onShelfProduct());
        TransactionSynchronizationManager.clearSynchronization();

        verify(kgClient, never()).edgeUpsert(any(KgEdgeUpsertCO.class));
    }

    @Test
    @DisplayName("商品摄取 — brand 为空 → 仅 BELONGS_TO/SOLD_BY 两类边，不产悬空 brand 节点")
    void shouldSkipBrandEdge_whenBrandBlank() {
        Product product = onShelfProduct();
        product.setBrand("  ");
        when(kgClient.edgeUpsert(any(KgEdgeUpsertCO.class))).thenReturn(2);

        kgEdgeIngestManager.ingestProductAfterCommit(product);

        ArgumentCaptor<KgEdgeUpsertCO> captor = ArgumentCaptor.forClass(KgEdgeUpsertCO.class);
        verify(kgClient).edgeUpsert(captor.capture());
        assertEquals(2, captor.getValue().getEdges().size());
    }

    @Test
    @DisplayName("商品摄取 — 下架/删除商品 → 清边（I59）而非写入；未落库 → 静默跳过")
    void shouldDeleteEdges_whenProductNotOnShelf() {
        Product offShelf = onShelfProduct();
        offShelf.setStatus(ProductStatusEnum.OFF_SHELF.getCode());

        kgEdgeIngestManager.ingestProductAfterCommit(offShelf);
        kgEdgeIngestManager.ingestProductAfterCommit(null);

        verify(kgClient).deleteEdges(KgSchema.NODE_PRODUCT, 1L);
        verify(kgClient, never()).edgeUpsert(any(KgEdgeUpsertCO.class));
    }

    @Test
    @DisplayName("降级哨兵 -1 / Feign 异常 — WARN 不抛（fail-open，主流程零感知）")
    void shouldNotThrow_whenFallbackSentinelOrException() {
        when(kgClient.edgeUpsert(any(KgEdgeUpsertCO.class)))
                .thenReturn(KgClientFallback.UPSERT_FALLBACK_SENTINEL)
                .thenThrow(new RuntimeException("boom"));

        Product product = onShelfProduct();
        assertDoesNotThrow(() -> kgEdgeIngestManager.writeProductEdges(product));
        assertEquals(0, kgEdgeIngestManager.writeProductEdges(product));

        org.mockito.Mockito.doThrow(new RuntimeException("boom"))
                .when(kgClient).deleteEdges(any(), any());
        assertDoesNotThrow(() -> kgEdgeIngestManager.deleteEdgesSafely(KgSchema.NODE_PRODUCT, 1L));
    }

    @Test
    @DisplayName("同步写入（重建路径）— 在售返回写入边数，下架清边计 0")
    void shouldReturnWrittenCount_whenWriteProductEdges() {
        when(kgClient.edgeUpsert(any(KgEdgeUpsertCO.class))).thenReturn(3);

        assertEquals(3, kgEdgeIngestManager.writeProductEdges(onShelfProduct()));

        Product offShelf = onShelfProduct();
        offShelf.setStatus(ProductStatusEnum.DELETED.getCode());
        assertEquals(0, kgEdgeIngestManager.writeProductEdges(offShelf));
        verify(kgClient).deleteEdges(KgSchema.NODE_PRODUCT, 1L);
    }

    // ==================== 规则边投影同步 ====================

    @Test
    @DisplayName("规则投影同步 — PAIR_WITH 边 weight/status 原样透传（停用边以 status=0 入投影）")
    void shouldProjectPairingEdges_withRuleStatus() {
        PairingRule enabled = PairingRule.create(10L, 20L, 8, "配件");
        enabled.setId(1L);
        PairingRule disabled = PairingRule.create(10L, 30L, 3, null);
        disabled.setId(2L);
        disabled.applyPatch(null, null, PairingRule.STATUS_DISABLED);
        when(kgClient.edgeUpsert(any(KgEdgeUpsertCO.class))).thenReturn(2);

        kgEdgeIngestManager.syncPairingEdgesAfterCommit(10L, List.of(enabled, disabled));

        ArgumentCaptor<KgEdgeUpsertCO> captor = ArgumentCaptor.forClass(KgEdgeUpsertCO.class);
        verify(kgClient).edgeUpsert(captor.capture());
        KgEdgeUpsertCO co = captor.getValue();
        assertEquals(KgSchema.NODE_CATEGORY, co.getSrcType());
        assertEquals(10L, co.getSrcId());
        assertEquals(2, co.getEdges().size());

        KgEdgeItemCO first = co.getEdges().get(0);
        assertEquals(KgSchema.REL_PAIR_WITH, first.getRelType());
        assertEquals(KgSchema.NODE_CATEGORY, first.getDstType());
        assertEquals(20L, first.getDstId());
        assertEquals(8, first.getWeight());
        assertEquals(KgSchema.SOURCE_RULE, first.getSource());
        assertEquals(KgSchema.EDGE_ENABLED, first.getStatus());

        assertEquals(KgSchema.EDGE_DISABLED, co.getEdges().get(1).getStatus());
    }

    @Test
    @DisplayName("规则投影同步 — 源类目为 null 或规则为空 → 不产生远程调用")
    void shouldSkipSync_whenNoSrcOrNoRules() {
        kgEdgeIngestManager.syncPairingEdgesAfterCommit(null, List.of());
        kgEdgeIngestManager.syncPairingEdgesAfterCommit(10L, List.of());

        verify(kgClient, never()).edgeUpsert(any(KgEdgeUpsertCO.class));
    }

    @Test
    @DisplayName("规则投影同步 — 事务内 afterCommit 后发送，快照冻结于提交前")
    void shouldSyncAfterCommit_whenTransactionActive() {
        TransactionSynchronizationManager.initSynchronization();
        PairingRule rule = PairingRule.create(10L, 20L, 8, null);
        when(kgClient.edgeUpsert(any(KgEdgeUpsertCO.class))).thenReturn(1);

        kgEdgeIngestManager.syncPairingEdgesAfterCommit(10L, List.of(rule));
        verify(kgClient, never()).edgeUpsert(any(KgEdgeUpsertCO.class));

        // 提交前追加的规则变更不影响快照（冻结口径）——此处仅验证提交后发送
        commitTransaction();
        verify(kgClient).edgeUpsert(any(KgEdgeUpsertCO.class));
    }

    @Test
    @DisplayName("upsertSafely — 空边集合直接跳过，不产生远程调用")
    void shouldSkipEmptyUpsert() {
        assertEquals(0, kgEdgeIngestManager.upsertSafely(new KgEdgeUpsertCO(
                KgSchema.NODE_PRODUCT, 1L, List.of())));

        verify(kgClient, never()).edgeUpsert(any(KgEdgeUpsertCO.class));
        verify(kgClient, never()).deleteEdges(eq(KgSchema.NODE_PRODUCT), eq(1L));
    }
}
