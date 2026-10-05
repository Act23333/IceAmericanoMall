package org.icedamericanomall.search.app;

import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BadRequestException;
import org.icedamericanomall.dto.KgEdgeItemCO;
import org.icedamericanomall.dto.KgEdgeUpsertCO;
import org.icedamericanomall.dto.KgNeighborCO;
import org.icedamericanomall.search.domain.gateway.KgEdgeIndexGateway;
import org.icedamericanomall.search.domain.model.kg.KgNeighborHit;
import org.icedamericanomall.search.domain.model.kg.KgNodeRef;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * KgEdgeManager 编排测试 —— 先删后写幂等 / depth 能力边界 / status 缺省与透传 / 两跳两次 terms / 空结果。
 *
 * <p>Mock 领域端口 {@link KgEdgeIndexGateway}（ES 客户端细节在 KgEdgeIndexAdapterTest）。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class KgEdgeManagerTest {

    private static final String SRC_TYPE = "product";
    private static final Long SRC_ID = 100L;
    private static final int ENABLED = 1;

    @Mock
    private KgEdgeIndexGateway gateway;

    @Captor
    private ArgumentCaptor<List<KgNodeRef>> startNodesCaptor;

    private KgEdgeManager manager;

    @BeforeEach
    void setUp() {
        manager = new KgEdgeManager(gateway);
    }

    // ── I57 摄取 ──

    @Test
    @DisplayName("I57 摄取 — 正常批次 → 建索引→按src删→写边，返回写入边数")
    void shouldDeleteBeforeWrite_whenUpsertEdges() {
        KgEdgeUpsertCO upsert = upsert(SRC_TYPE, SRC_ID,
                edge("BELONGS_TO", "category", 10L), edge("BRAND_OF", "brand", 2L));

        int written = manager.upsertEdges(upsert);

        assertEquals(2, written);
        InOrder order = inOrder(gateway);
        order.verify(gateway).ensureIndex();
        order.verify(gateway).deleteBySrc(SRC_TYPE, SRC_ID);
        order.verify(gateway).indexEdges(anyList());
    }

    @Test
    @DisplayName("I57 摄取 — 同 src 重复摄取 → 每次都先删后写（幂等覆盖，不产生漂移边）")
    void shouldDeleteBeforeEveryWrite_whenUpsertSameSrcTwice() {
        manager.upsertEdges(upsert(SRC_TYPE, SRC_ID, edge("BELONGS_TO", "category", 10L)));
        manager.upsertEdges(upsert(SRC_TYPE, SRC_ID, edge("BELONGS_TO", "category", 11L)));

        verify(gateway, times(2)).deleteBySrc(SRC_TYPE, SRC_ID);
        verify(gateway, times(2)).indexEdges(anyList());
    }

    @Test
    @DisplayName("I57 摄取 — 空边集合 → 仅清边返回 0（清空语义），不发起写边")
    void shouldReturnZeroAndOnlyDelete_whenUpsertEmptyEdges() {
        int written = manager.upsertEdges(upsert(SRC_TYPE, SRC_ID));

        assertEquals(0, written);
        verify(gateway).deleteBySrc(SRC_TYPE, SRC_ID);
        verify(gateway, never()).indexEdges(anyList());
    }

    @Test
    @DisplayName("I57 摄取 — 缺 srcId → PARAM_ERROR 且不触达索引")
    void shouldThrowParamError_whenUpsertMissingSrcId() {
        KgEdgeUpsertCO upsert = upsert(SRC_TYPE, null, edge("BELONGS_TO", "category", 10L));

        BadRequestException ex = assertThrows(BadRequestException.class, () -> manager.upsertEdges(upsert));

        assertEquals(ErrorCode.PARAM_ERROR.getCode(), ex.getErrorCode().getCode());
        verifyNoInteractions(gateway);
    }

    @Test
    @DisplayName("I57 摄取 — edges 为 null → PARAM_ERROR 且不触达索引")
    void shouldThrowParamError_whenUpsertMissingEdges() {
        KgEdgeUpsertCO upsert = new KgEdgeUpsertCO(SRC_TYPE, SRC_ID, null);

        assertThrows(BadRequestException.class, () -> manager.upsertEdges(upsert));

        verifyNoInteractions(gateway);
    }

    // ── I58 邻居 ──

    @Test
    @DisplayName("I58 邻居 — depth=1 → 单次 terms 查询并完整映射字段")
    void shouldQueryOnceAndMapFields_whenDepth1() {
        when(gateway.searchNeighbors(anyList(), any(), any(), any(), anyInt()))
                .thenReturn(List.of(new KgNeighborHit("category", 10L, "BELONGS_TO", 0, 1.5F)));

        List<KgNeighborCO> result = manager.neighbors(SRC_TYPE, SRC_ID, null, null, 1, null);

        verify(gateway, times(1)).searchNeighbors(anyList(), any(), any(), any(), anyInt());
        assertEquals(1, result.size());
        KgNeighborCO co = result.get(0);
        assertEquals("category", co.getDstType());
        assertEquals(10L, co.getDstId());
        assertEquals("BELONGS_TO", co.getRelType());
        assertEquals(0, co.getWeight());
        assertEquals(1.5F, co.getScore());
    }

    @Test
    @DisplayName("I58 邻居 — depth=2 → 恰好两次 terms 调用，第二次以第一跳目标为起点")
    void shouldQueryTwiceWithHop1Frontier_whenDepth2() {
        when(gateway.searchNeighbors(anyList(), any(), any(), any(), anyInt()))
                .thenReturn(List.of(new KgNeighborHit("category", 10L, "BELONGS_TO", 0, 1.5F)),
                        List.of(new KgNeighborHit("category", 20L, "PAIR_WITH", 9, 1.2F)));

        List<KgNeighborCO> result = manager.neighbors(SRC_TYPE, SRC_ID, null, null, 2, null);

        verify(gateway, times(2)).searchNeighbors(startNodesCaptor.capture(), any(), any(), any(), anyInt());
        assertEquals(SRC_TYPE, startNodesCaptor.getAllValues().get(0).get(0).type());
        assertEquals(SRC_ID, startNodesCaptor.getAllValues().get(0).get(0).id());
        assertEquals("category", startNodesCaptor.getAllValues().get(1).get(0).type());
        assertEquals(10L, startNodesCaptor.getAllValues().get(1).get(0).id());
        assertEquals(1, result.size());
        assertEquals(20L, result.get(0).getDstId());
        assertEquals("PAIR_WITH", result.get(0).getRelType());
    }

    @Test
    @DisplayName("I58 邻居 — depth 超出能力边界(>2) → PARAM_ERROR 且不触达索引")
    void shouldThrowParamError_whenDepthOverMax() {
        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> manager.neighbors(SRC_TYPE, SRC_ID, null, null, KgEdgeManager.MAX_DEPTH + 1, null));

        assertEquals(ErrorCode.PARAM_ERROR.getCode(), ex.getErrorCode().getCode());
        verifyNoInteractions(gateway);
    }

    @Test
    @DisplayName("I58 邻居 — depth 缺省(null) → 按 1 跳处理（单次查询）")
    void shouldDefaultDepth1_whenDepthNull() {
        when(gateway.searchNeighbors(anyList(), any(), any(), any(), anyInt()))
                .thenReturn(List.of(new KgNeighborHit("category", 10L, "BELONGS_TO", 0, 1.0F)));

        manager.neighbors(SRC_TYPE, SRC_ID, null, null, null, null);

        verify(gateway, times(1)).searchNeighbors(anyList(), any(), any(), any(), anyInt());
    }

    @Test
    @DisplayName("I58 邻居 — status 缺省 → 仅查启用边(status=1)")
    void shouldFilterEnabledByDefault_whenStatusNull() {
        when(gateway.searchNeighbors(anyList(), any(), any(), any(), anyInt()))
                .thenReturn(List.of(new KgNeighborHit("category", 10L, "BELONGS_TO", 0, 1.0F)));

        manager.neighbors(SRC_TYPE, SRC_ID, null, null, 1, null);

        verify(gateway).searchNeighbors(anyList(), isNull(), isNull(), eq(ENABLED), anyInt());
    }

    @Test
    @DisplayName("I58 邻居 — 显式 status=0 → 透传停用边过滤（治理即时生效）")
    void shouldPassThroughStatus_whenDisabledGiven() {
        when(gateway.searchNeighbors(anyList(), any(), any(), any(), anyInt()))
                .thenReturn(List.of(new KgNeighborHit("category", 10L, "BELONGS_TO", 0, 1.0F)));

        manager.neighbors(SRC_TYPE, SRC_ID, null, null, 1, 0);

        verify(gateway).searchNeighbors(anyList(), isNull(), isNull(), eq(0), anyInt());
    }

    @Test
    @DisplayName("I58 邻居 — depth=2 但第一跳无命中 → 单次查询即返回空（不发起第二次）")
    void shouldSkipSecondHop_whenHop1Empty() {
        when(gateway.searchNeighbors(anyList(), any(), any(), any(), anyInt()))
                .thenReturn(List.of());

        List<KgNeighborCO> result = manager.neighbors(SRC_TYPE, SRC_ID, null, null, 2, null);

        verify(gateway, times(1)).searchNeighbors(anyList(), any(), any(), any(), anyInt());
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("I58 邻居 — 无命中 → 空列表")
    void shouldReturnEmpty_whenNoHit() {
        when(gateway.searchNeighbors(anyList(), any(), any(), any(), anyInt()))
                .thenReturn(List.of());

        List<KgNeighborCO> result = manager.neighbors(SRC_TYPE, SRC_ID, "PAIR_WITH", "category", 1, null);

        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("I58 邻居 — 同一 dst 多路径命中 → 按 dst 去重保留最优一条（ES 已按 score/weight 排序）")
    void shouldDeduplicateByDst_whenSameDstHitTwice() {
        when(gateway.searchNeighbors(anyList(), any(), any(), any(), anyInt()))
                .thenReturn(List.of(
                        new KgNeighborHit("category", 20L, "PAIR_WITH", 9, 1.5F),
                        new KgNeighborHit("category", 20L, "PARENT_OF", 0, 1.2F)));

        List<KgNeighborCO> result = manager.neighbors(SRC_TYPE, SRC_ID, null, null, 1, null);

        assertEquals(1, result.size());
        assertEquals(9, result.get(0).getWeight());
        assertEquals(1.5F, result.get(0).getScore());
    }

    // ── I59 清边 ──

    @Test
    @DisplayName("I59 清边 — 正常 → 建索引后按 src 删除")
    void shouldEnsureIndexThenDeleteBySrc_whenDeleteEdges() {
        manager.deleteEdges(SRC_TYPE, SRC_ID);

        InOrder order = inOrder(gateway);
        order.verify(gateway).ensureIndex();
        order.verify(gateway).deleteBySrc(SRC_TYPE, SRC_ID);
    }

    @Test
    @DisplayName("I59 清边 — 缺 srcType → PARAM_ERROR 且不触达索引")
    void shouldThrowParamError_whenDeleteMissingSrcType() {
        assertThrows(BadRequestException.class, () -> manager.deleteEdges(" ", SRC_ID));

        verifyNoInteractions(gateway);
    }

    // ── 工具 ──

    private static KgEdgeUpsertCO upsert(String srcType, Long srcId, KgEdgeItemCO... items) {
        return new KgEdgeUpsertCO(srcType, srcId, List.of(items));
    }

    private static KgEdgeItemCO edge(String relType, String dstType, Long dstId) {
        return new KgEdgeItemCO(relType, dstType, dstId, 0, "DIRECT", ENABLED);
    }
}
