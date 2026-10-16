package org.icedamericanomall.item.app.kg;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.core.metadata.IPage;
import org.icedamericanomall.client.KgClient;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.dto.KgNeighborCO;
import org.icedamericanomall.item.adapter.request.PairingRuleReq;
import org.icedamericanomall.item.adapter.request.PairingRuleUpdateReq;
import org.icedamericanomall.item.adapter.response.PairingRuleVO;
import org.icedamericanomall.item.domain.enums.SpuStatusEnum;
import org.icedamericanomall.item.domain.model.category.Category;
import org.icedamericanomall.item.domain.model.kg.PairingRule;
import org.icedamericanomall.item.domain.model.spu.Spu;
import org.icedamericanomall.item.domain.repository.CategoryRepository;
import org.icedamericanomall.item.domain.repository.PairingRuleRepository;
import org.icedamericanomall.item.domain.repository.SpuRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * KgAdminManager 单元测试 —— 治理用例族（AD61-65）：类目悬空 1101、重复回落 400、
 * null-safe 编辑与启停即时生效、全量重建编排（幂等、逐 src、fail-open）、
 * 关系邻居核查查询（AD65：参数透传、depth 归一与 >2 拒绝、降级空列表透传）。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("KgAdminManager — 知识图谱治理编排")
class KgAdminManagerTest {

    @Mock
    private PairingRuleRepository pairingRuleRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private SpuRepository spuRepository;

    @Mock
    private KgEdgeIngestManager kgEdgeIngestManager;

    @Mock
    private KgClient kgClient;

    private KgAdminManager kgAdminManager;

    @BeforeEach
    void setUp() {
        kgAdminManager = new KgAdminManager(pairingRuleRepository, categoryRepository,
                spuRepository, kgEdgeIngestManager, kgClient);
    }

    private PairingRuleReq createReq() {
        PairingRuleReq req = new PairingRuleReq();
        req.setSrcCategoryId(10L);
        req.setDstCategoryId(20L);
        req.setWeight(8);
        req.setRemark("手机 → 手机配件");
        return req;
    }

    private Spu spuWithStatus(Long id, SpuStatusEnum status) {
        Spu spu = Spu.publish(100L, 10L, null, "手机", "img.jpg", "旗舰", "冰美");
        spu.setId(id);
        spu.setStatus(status.getCode());
        return spu;
    }

    // ==================== AD62 create ====================

    @Test
    @DisplayName("AD62 create — 源类目不存在 → 1101 且不落库、不投影（悬空不产生边）")
    void shouldReject1101_whenSrcCategoryMissing() {
        when(categoryRepository.findById(10L)).thenReturn(null);

        BizException ex = assertThrows(BizException.class, () -> kgAdminManager.create(createReq()));

        assertEquals(ErrorCode.PARAM_ERROR, ex.getErrorCode());
        verify(pairingRuleRepository, never()).save(any(PairingRule.class));
        verify(kgEdgeIngestManager, never()).syncPairingEdgesAfterCommit(anyLong(), any());
    }

    @Test
    @DisplayName("AD62 create — 目标类目不存在 → 1101")
    void shouldReject1101_whenDstCategoryMissing() {
        when(categoryRepository.findById(10L)).thenReturn(Category.builder().id(10L).build());
        when(categoryRepository.findById(20L)).thenReturn(null);

        BizException ex = assertThrows(BizException.class, () -> kgAdminManager.create(createReq()));

        assertEquals(ErrorCode.PARAM_ERROR, ex.getErrorCode());
        verify(pairingRuleRepository, never()).save(any(PairingRule.class));
    }

    @Test
    @DisplayName("AD62 create — 规则重复（先查命中）→ 1101 且不落库")
    void shouldReject_whenDuplicateRulePreChecked() {
        when(categoryRepository.findById(anyLong())).thenReturn(Category.builder().id(1L).build());
        when(pairingRuleRepository.findBySrcAndDst(10L, 20L))
                .thenReturn(PairingRule.create(10L, 20L, 0, null));

        BizException ex = assertThrows(BizException.class, () -> kgAdminManager.create(createReq()));

        assertEquals(ErrorCode.PARAM_ERROR, ex.getErrorCode());
        verify(pairingRuleRepository, never()).save(any(PairingRule.class));
    }

    @Test
    @DisplayName("AD62 create — 并发撞 uk_src_dst（DuplicateKey）→ 回落 400/1101")
    void shouldFallback400_whenDuplicateKeyOnInsert() {
        when(categoryRepository.findById(anyLong())).thenReturn(Category.builder().id(1L).build());
        when(pairingRuleRepository.findBySrcAndDst(10L, 20L)).thenReturn(null);
        doThrow(new DuplicateKeyException("uk_src_dst")).when(pairingRuleRepository).save(any(PairingRule.class));

        BizException ex = assertThrows(BizException.class, () -> kgAdminManager.create(createReq()));

        assertEquals(ErrorCode.PARAM_ERROR, ex.getErrorCode());
    }

    @Test
    @DisplayName("AD62 create — 成功：weight 缺省归一 0、status 缺省启用，落库后按 src 全量规则同步投影")
    void shouldCreateAndProject_whenValidRule() {
        when(categoryRepository.findById(anyLong())).thenReturn(Category.builder().id(1L).build());
        when(pairingRuleRepository.findBySrcAndDst(10L, 20L)).thenReturn(null);
        PairingRuleReq req = createReq();
        req.setWeight(null);
        List<PairingRule> rulesAfterSave = List.of(PairingRule.create(10L, 20L, 0, null));
        when(pairingRuleRepository.findAllBySrc(10L)).thenReturn(rulesAfterSave);

        kgAdminManager.create(req);

        ArgumentCaptor<PairingRule> captor = ArgumentCaptor.forClass(PairingRule.class);
        verify(pairingRuleRepository).save(captor.capture());
        assertEquals(0, captor.getValue().getWeight());
        assertEquals(PairingRule.STATUS_ENABLED, captor.getValue().getStatus());
        verify(kgEdgeIngestManager).syncPairingEdgesAfterCommit(10L, rulesAfterSave);
    }

    // ==================== AD63 update ====================

    @Test
    @DisplayName("AD63 update — 规则不存在 → 1103")
    void shouldThrow1103_whenRuleMissing() {
        when(pairingRuleRepository.findById(9L)).thenReturn(null);

        BizException ex = assertThrows(BizException.class,
                () -> kgAdminManager.update(9L, new PairingRuleUpdateReq()));

        assertEquals(ErrorCode.NO_RESOURCE_FOUND, ex.getErrorCode());
    }

    @Test
    @DisplayName("AD63 update — null-safe：仅传入字段改动（weight=15），其余保留，落库后同步投影")
    void shouldPatchOnlyProvidedFields_whenUpdate() {
        PairingRule stored = PairingRule.create(10L, 20L, 8, "原备注");
        stored.setId(1L);
        when(pairingRuleRepository.findById(1L)).thenReturn(stored);
        List<PairingRule> rulesAfterUpdate = List.of(stored);
        when(pairingRuleRepository.findAllBySrc(10L)).thenReturn(rulesAfterUpdate);
        PairingRuleUpdateReq req = new PairingRuleUpdateReq();
        req.setWeight(15);

        kgAdminManager.update(1L, req);

        assertEquals(15, stored.getWeight());
        assertEquals("原备注", stored.getRemark());
        assertEquals(PairingRule.STATUS_ENABLED, stored.getStatus());
        verify(pairingRuleRepository).update(stored);
        verify(kgEdgeIngestManager).syncPairingEdgesAfterCommit(10L, rulesAfterUpdate);
    }

    @Test
    @DisplayName("AD63 update — 停用即时生效：status=0 落库后立即同步投影（查询侧 status 过滤生效）")
    void shouldSyncProjectionImmediately_whenDisableRule() {
        PairingRule stored = PairingRule.create(10L, 20L, 8, null);
        stored.setId(1L);
        when(pairingRuleRepository.findById(1L)).thenReturn(stored);
        when(pairingRuleRepository.findAllBySrc(10L)).thenReturn(List.of(stored));
        PairingRuleUpdateReq req = new PairingRuleUpdateReq();
        req.setStatus(PairingRule.STATUS_DISABLED);

        kgAdminManager.update(1L, req);

        assertTrue(!stored.isEnabled());
        InOrder inOrder = inOrder(pairingRuleRepository, kgEdgeIngestManager);
        inOrder.verify(pairingRuleRepository).update(stored);
        inOrder.verify(kgEdgeIngestManager).syncPairingEdgesAfterCommit(10L, List.of(stored));
    }

    // ==================== AD64 rebuild ====================

    @Test
    @DisplayName("AD64 rebuild — 在售 upsert / 下架删除清边 / PARENT_OF / PAIR_WITH 四路编排，返回重建边数")
    void shouldRebuildAllEdges_andReturnWrittenCount() {
        IPage<Spu> page1 = new Page<>(1, 500);
        page1.setRecords(List.of(
                spuWithStatus(1L, SpuStatusEnum.ON_SHELF),
                spuWithStatus(2L, SpuStatusEnum.OFF_SHELF)));
        page1.setTotal(1000);
        IPage<Spu> page2 = new Page<>(2, 500);
        page2.setRecords(List.of(spuWithStatus(3L, SpuStatusEnum.ON_SHELF)));
        page2.setTotal(1000);
        when(spuRepository.pageAll(anyInt(), anyInt())).thenReturn(page1, page2);
        when(categoryRepository.findAllOrderBySort()).thenReturn(List.of(
                Category.builder().id(10L).parentId(null).build(),
                Category.builder().id(20L).parentId(10L).build(),
                Category.builder().id(30L).parentId(10L).build()));
        PairingRule rule = PairingRule.create(10L, 20L, 8, null);
        when(pairingRuleRepository.findAll()).thenReturn(List.of(rule));
        when(kgEdgeIngestManager.writeSpuEdges(any(Spu.class))).thenReturn(3, 0, 3);
        when(kgEdgeIngestManager.upsertSafely(any())).thenReturn(2);
        when(kgEdgeIngestManager.writePairingEdges(anyLong(), any())).thenReturn(1);

        int written = kgAdminManager.rebuild();

        // 3（在售P1）+ 0（下架P2 清边）+ 3（在售P3）+ 2（父类目10 PARENT_OF 两条）+ 1（PAIR_WITH）
        assertEquals(9, written);
        verify(kgEdgeIngestManager).writeSpuEdges(page1.getRecords().get(0));
        verify(kgEdgeIngestManager).writeSpuEdges(page1.getRecords().get(1));
        verify(kgEdgeIngestManager).writeSpuEdges(page2.getRecords().get(0));
        verify(kgEdgeIngestManager).upsertSafely(any());
        verify(kgEdgeIngestManager).writePairingEdges(10L, List.of(rule));
    }

    @Test
    @DisplayName("AD64 rebuild — 幂等可重试：重复执行走同一逐 src 路径（先删后写幂等由 I57 保证）")
    void shouldBeRetryable_whenRebuildRunsTwice() {
        IPage<Spu> page = new Page<>(1, 500);
        page.setRecords(List.of(spuWithStatus(1L, SpuStatusEnum.ON_SHELF)));
        when(spuRepository.pageAll(anyInt(), anyInt())).thenReturn(page);
        when(categoryRepository.findAllOrderBySort()).thenReturn(List.of());
        when(pairingRuleRepository.findAll()).thenReturn(List.of());
        when(kgEdgeIngestManager.writeSpuEdges(any(Spu.class))).thenReturn(3);

        kgAdminManager.rebuild();
        kgAdminManager.rebuild();

        verify(kgEdgeIngestManager, org.mockito.Mockito.times(2)).writeSpuEdges(any(Spu.class));
    }

    // ==================== AD61 page ====================

    @Test
    @DisplayName("AD61 page — 三维过滤透传仓储并转换为 VO")
    void shouldPageAndConvert_whenFiltersGiven() {
        PairingRule rule = PairingRule.create(10L, 20L, 8, "配件");
        rule.setId(1L);
        Page<PairingRule> page = new Page<>(1, 20);
        page.setRecords(List.of(rule));
        when(pairingRuleRepository.page(10L, 20L, PairingRule.STATUS_ENABLED, 1, 20)).thenReturn(page);

        IPage<PairingRuleVO> result = kgAdminManager.page(10L, 20L, PairingRule.STATUS_ENABLED, 1, 20);

        assertEquals(1, result.getRecords().size());
        PairingRuleVO vo = result.getRecords().get(0);
        assertEquals(1L, vo.getId());
        assertEquals(10L, vo.getSrcCategoryId());
        assertEquals(20L, vo.getDstCategoryId());
        assertEquals(8, vo.getWeight());
        assertEquals("配件", vo.getRemark());
    }

    // ==================== AD65 neighbors ====================

    @Test
    @DisplayName("AD65 neighbors — 参数透传 I58：src/rel/dst/status 原样，depth 缺省归一 1 / 显式 2 原样透传")
    void shouldDelegateWithNormalizedDepth_whenNeighborsQueried() {
        List<KgNeighborCO> expected = List.of(new KgNeighborCO("category", 20L, "PAIR_WITH", 8, 1.5f));
        when(kgClient.neighbors("category", 10L, "PAIR_WITH", "category", 1, null)).thenReturn(expected);
        when(kgClient.neighbors("category", 10L, "PAIR_WITH", "category", 2, 0))
                .thenReturn(List.of());

        List<KgNeighborCO> defaultDepth = kgAdminManager.neighbors(
                "category", 10L, "PAIR_WITH", "category", null, null);
        List<KgNeighborCO> explicitDepth = kgAdminManager.neighbors(
                "category", 10L, "PAIR_WITH", "category", 2, 0);

        assertSame(expected, defaultDepth);
        assertTrue(explicitDepth.isEmpty());
        verify(kgClient).neighbors("category", 10L, "PAIR_WITH", "category", 1, null);
        verify(kgClient).neighbors("category", 10L, "PAIR_WITH", "category", 2, 0);
    }

    @Test
    @DisplayName("AD65 neighbors — depth>2 超出图查询能力边界 → 1101，不发起 Feign 查询")
    void shouldReject1101_whenDepthExceedsBoundary() {
        BizException ex = assertThrows(BizException.class, () -> kgAdminManager.neighbors(
                "category", 10L, null, null, 3, null));

        assertEquals(ErrorCode.PARAM_ERROR, ex.getErrorCode());
        verify(kgClient, never()).neighbors(anyString(), anyLong(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("AD65 neighbors — ES 降级（fallback 空列表）原样透传为空列表，治理视图不报错")
    void shouldPassThroughEmptyList_whenDegraded() {
        when(kgClient.neighbors("product", 1L, null, null, 1, null)).thenReturn(List.of());

        List<KgNeighborCO> result = kgAdminManager.neighbors("product", 1L, null, null, null, null);

        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("AD65 neighbors — srcType/srcId 必填（缺失 → 1101，不发起 Feign 查询）")
    void shouldReject1101_whenSrcMissing() {
        BizException missingType = assertThrows(BizException.class, () -> kgAdminManager.neighbors(
                " ", 10L, null, null, null, null));
        BizException missingId = assertThrows(BizException.class, () -> kgAdminManager.neighbors(
                "category", null, null, null, null, null));

        assertEquals(ErrorCode.PARAM_ERROR, missingType.getErrorCode());
        assertEquals(ErrorCode.PARAM_ERROR, missingId.getErrorCode());
        verify(kgClient, never()).neighbors(anyString(), anyLong(), any(), any(), any(), any());
    }
}
