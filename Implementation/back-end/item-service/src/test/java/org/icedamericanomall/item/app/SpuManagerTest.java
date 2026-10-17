package org.icedamericanomall.item.app;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.icedamericanomall.client.PromotionClient;
import org.icedamericanomall.client.SpuVectorClient;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.dto.SpuVectorUpsertCO;
import org.icedamericanomall.dto.PromotionCO;
import org.icedamericanomall.dto.PromotionQueryCO;
import org.icedamericanomall.item.adapter.request.CreateSpuReq;
import org.icedamericanomall.item.adapter.request.UpdateSpuReq;
import org.icedamericanomall.item.adapter.response.SpuVO;
import org.icedamericanomall.item.adapter.response.RecommendSpuVO;
import org.icedamericanomall.item.domain.enums.SpuStatusEnum;
import org.icedamericanomall.item.domain.model.spu.Spu;
import org.icedamericanomall.item.domain.model.sku.Sku;
import org.icedamericanomall.item.domain.repository.SpuRepository;
import org.icedamericanomall.item.domain.repository.SkuRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * SpuManager 单元测试 —— 详情/发布/编辑（归属校验）与状态变更、相似推荐组装、向量摄取钩子。
 * （原 SpuServiceImplTest 语义迁移：查询沉仓储、编排归本类。）
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SpuManager 编排单元测试")
class SpuManagerTest {

    @Mock
    private SpuRepository spuRepository;

    @Mock
    private SkuRepository skuRepository;

    @Mock
    private FreightTemplateManager freightTemplateManager;

    @Mock
    private SpuVectorClient spuVectorClient;

    @Mock
    private PromotionClient promotionClient;

    @Mock
    private org.icedamericanomall.item.app.kg.KgEdgeIngestManager kgEdgeIngestManager;

    @Mock
    private org.icedamericanomall.item.domain.port.ShortCodeResolver shortCodeResolver;

    private SpuManager spuManager;

    @BeforeEach
    void setUp() {
        spuManager = new SpuManager(spuRepository, skuRepository,
                new org.icedamericanomall.item.adapter.assembler.SpuConverterImpl(),
                new org.icedamericanomall.item.adapter.assembler.SkuConverterImpl(),
                freightTemplateManager, new SkuManager(skuRepository,
                new org.icedamericanomall.item.adapter.assembler.SkuConverterImpl(),
                // 商品展示字段/业务键回填端口：本测试不覆盖该链路（真实 SQL 语义见 SkuSpuDisplayQueryImplH2Test）
                new org.icedamericanomall.item.app.port.SkuSpuDisplayQuery() {
                    @Override
                    public java.util.Map<Long, org.icedamericanomall.item.app.dto.SkuSpuDisplay> findBySkuIds(
                            java.util.Collection<Long> skuIds) {
                        return java.util.Map.of();
                    }

                    @Override
                    public java.util.Map<String, org.icedamericanomall.item.app.dto.SkuSpuDisplay> findBySkuBusinessIds(
                            java.util.Collection<String> skuBusinessIds) {
                        return java.util.Map.of();
                    }
                }),
                spuVectorClient, new SpuRecommendManager(spuVectorClient),
                new SpuPromotionManager(promotionClient), kgEdgeIngestManager, shortCodeResolver);
    }

    @AfterEach
    void tearDown() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private Spu spu(Long sellerId) {
        return Spu.publish(sellerId, 10L, null, "手机", "img.jpg", "旗舰", "品牌A");
    }

    /**
     * 技术键 → SPU 业务键（32-hex，{@code spu.spu_id} 的线格式，ADR-061 §决策 2）。
     * 测试夹具里技术键与业务键按此一一对应，保证「请求侧业务键 = 回执侧业务键」。
     */
    private static String businessKey(long spuTechnicalId) {
        return String.format("%032x", spuTechnicalId);
    }

    private Spu spuWithId(Long sellerId, Long id) {
        Spu p = spu(sellerId);
        p.setId(id);
        p.setSpuId(businessKey(id));
        p.setSoldCount(0);
        return p;
    }

    private CreateSpuReq createReq() {
        CreateSpuReq req = new CreateSpuReq();
        req.setCategoryId(10L);
        req.setSpuName("手机");
        req.setMainImage("img.jpg");
        req.setDescription("旗舰");
        req.setBrand("品牌A");
        CreateSpuReq.SkuReq skuReq = new CreateSpuReq.SkuReq();
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

    // ==================== getSpuDetail ====================

    @Test
    @DisplayName("getSpuDetail — 存在：返回 VO 并附在售 SKU 与最低价")
    void shouldReturnDetailWithSkus_whenSpuExists() {
        Spu p = spuWithId(100L, 1L);
        when(spuRepository.findByBusinessId(businessKey(1L))).thenReturn(p);
        when(skuRepository.findBySpuId(1L)).thenReturn(List.of(
                org.icedamericanomall.item.domain.model.sku.Sku.builder()
                        .id(1L).spuId(1L).price(599900).status(1).build()));

        SpuVO vo = spuManager.getSpuDetail(businessKey(1L));

        assertEquals("手机", vo.getSpuName());
        assertEquals(599900, vo.getMinPrice());
        assertEquals(1, vo.getSkus().size());
    }

    @Test
    @DisplayName("getSpuDetail — 不存在 → NO_RESOURCE_FOUND(1103 / HTTP 404，不是 200 信封)")
    void shouldThrowNotFound_whenSpuMissing() {
        when(spuRepository.findByBusinessId(businessKey(1L))).thenReturn(null);

        BizException ex = assertThrows(BizException.class, () -> spuManager.getSpuDetail(businessKey(1L)));
        assertEquals(ErrorCode.NO_RESOURCE_FOUND, ex.getErrorCode());
    }

    @Test
    @DisplayName("getSpuDetail — 店铺有进行中促销 → promotions 回填满减/满折标签（V5.11 增量）")
    void shouldFillPromotions_whenShopHasActivePromotion() {
        when(spuRepository.findByBusinessId(businessKey(1L))).thenReturn(spuWithId(100L, 1L));
        when(skuRepository.findBySpuId(1L)).thenReturn(List.of(
                org.icedamericanomall.item.domain.model.sku.Sku.builder()
                        .id(1L).spuId(1L).price(599900).status(1).build()));
        when(promotionClient.queryActive(any(PromotionQueryCO.class))).thenReturn(List.of(
                new PromotionCO(1L, "满1000减200", 100L, 1, 100000, 20000, 0, null),
                new PromotionCO(2L, "满500打8.5折", 100L, 2, 50000, 8500, 10000, null)));

        SpuVO vo = spuManager.getSpuDetail(businessKey(1L));

        assertEquals(2, vo.getPromotions().size());
        assertEquals("FULL_REDUCE", vo.getPromotions().get(0).getType());
        assertEquals("LIMIT_DISCOUNT", vo.getPromotions().get(1).getType());
        assertEquals(20000, vo.getPromotions().get(0).getDiscountValue());
    }

    @Test
    @DisplayName("getSpuDetail — 促销查询失败（fail-open）→ 详情正常返回，promotions 为空列表")
    void shouldStillReturnDetail_whenPromotionQueryFails() {
        when(spuRepository.findByBusinessId(businessKey(1L))).thenReturn(spuWithId(100L, 1L));
        when(skuRepository.findBySpuId(1L)).thenReturn(List.of(
                org.icedamericanomall.item.domain.model.sku.Sku.builder()
                        .id(1L).spuId(1L).price(599900).status(1).build()));
        when(promotionClient.queryActive(any(PromotionQueryCO.class)))
                .thenThrow(new BizException(ErrorCode.SYSTEM_BUSY, "促销服务暂不可用"));

        SpuVO vo = spuManager.getSpuDetail(businessKey(1L));

        assertEquals("手机", vo.getSpuName());
        assertNotNull(vo.getPromotions());
        assertTrue(vo.getPromotions().isEmpty());
    }

    // ==================== createSpu ====================

    @Test
    @DisplayName("createSpu — 发布聚合 + 逐 SKU 落库（含热销元数据归一）")
    void shouldPublishSpuAndSkus() {
        when(spuRepository.save(any(Spu.class))).thenReturn(spuWithId(100L, 1L));

        spuManager.createSpu(100L, createReq());

        verify(spuRepository).save(any(Spu.class));
        verify(skuRepository).save(any(Sku.class));
    }

    @Test
    @DisplayName("createSpu — 事务提交后摄取向量：CO 字段取自落库聚合，提交前不发送")
    void shouldIngestVectorAfterCommit_whenCreateSpu() {
        TransactionSynchronizationManager.initSynchronization();
        Spu saved = spuWithId(100L, 1L);
        when(spuRepository.save(any(Spu.class))).thenReturn(saved);
        when(skuRepository.findOnSaleBySpuIds(List.of(1L))).thenReturn(List.of(
                Sku.builder().id(9L).spuId(1L).price(800).status(1).build()));

        spuManager.createSpu(100L, createReq());

        // 提交前（事务内）绝不发送 Feign
        verify(spuVectorClient, never()).upsert(any(SpuVectorUpsertCO.class));
        commitTransaction();

        ArgumentCaptor<SpuVectorUpsertCO> captor = ArgumentCaptor.forClass(SpuVectorUpsertCO.class);
        verify(spuVectorClient).upsert(captor.capture());
        SpuVectorUpsertCO co = captor.getValue();
        // ADR-061 §决策 2：I47 幂等键是 SPU 业务键（spu.spu_id 生成的 32-hex），不是技术键 1L
        assertEquals(saved.getSpuId(), co.getSpuId());
        assertNotEquals(saved.getId().toString(), co.getSpuId(), "摄取幂等键不得退回技术键");
        assertEquals("手机", co.getSpuName());
        assertEquals("旗舰", co.getDescription());
        assertEquals("品牌A", co.getBrand());
        assertEquals(10L, co.getCategoryId());
        assertEquals("img.jpg", co.getMainImage());
        assertEquals(800, co.getPrice());
        assertEquals(0, co.getSoldCount());
    }

    @Test
    @DisplayName("createSpu — 事务回滚（afterCommit 未触发）→ 不摄取")
    void shouldNotIngest_whenTransactionRolledBack() {
        TransactionSynchronizationManager.initSynchronization();
        Spu saved = spuWithId(100L, 1L);
        when(spuRepository.save(any(Spu.class))).thenReturn(saved);
        when(skuRepository.findOnSaleBySpuIds(List.of(1L))).thenReturn(List.of());

        spuManager.createSpu(100L, createReq());

        // 回滚语义：同步器随事务丢弃，afterCommit 永不触发
        TransactionSynchronizationManager.clearSynchronization();

        verify(spuVectorClient, never()).upsert(any(SpuVectorUpsertCO.class));
    }

    @Test
    @DisplayName("createSpu — 绑定他人/不存在模板 → 3041 且不落库、不摄取")
    void shouldReject_whenFreightTemplateNotBindable() {
        CreateSpuReq req = createReq();
        req.setFreightTemplateId(99L);
        doThrow(new BizException(ErrorCode.FREIGHT_TEMPLATE_NOT_FOUND, "运费模板不存在"))
                .when(freightTemplateManager).requireBindable(100L, 99L);

        BizException ex = assertThrows(BizException.class, () -> spuManager.createSpu(100L, req));
        assertEquals(ErrorCode.FREIGHT_TEMPLATE_NOT_FOUND, ex.getErrorCode());
        verify(spuRepository, never()).save(any(Spu.class));
        verify(spuVectorClient, never()).upsert(any(SpuVectorUpsertCO.class));
    }

    @Test
    @DisplayName("createSpu — 未绑定模板（null）：不校验归属，落库 freightTemplateId 为 null")
    void shouldSkipBindableCheck_whenNoTemplateBound() {
        when(spuRepository.save(any(Spu.class))).thenReturn(spuWithId(100L, 1L));
        CreateSpuReq req = createReq();
        req.setSkus(List.of());

        spuManager.createSpu(100L, req);

        verify(freightTemplateManager, never()).requireBindable(anyLong(), anyLong());
        ArgumentCaptor<Spu> captor = ArgumentCaptor.forClass(Spu.class);
        verify(spuRepository).save(captor.capture());
        assertNull(captor.getValue().getFreightTemplateId());
    }

    // ==================== updateSpu ====================

    @Test
    @DisplayName("updateSpu — 非本人商品 → FORBIDDEN")
    void shouldThrowForbidden_whenNotOwner() {
        when(spuRepository.findByBusinessId(businessKey(1L))).thenReturn(spu(999L));

        UpdateSpuReq req = new UpdateSpuReq();
        BizException ex = assertThrows(BizException.class,
                () -> spuManager.updateSpu(businessKey(1L), 100L, req));
        assertEquals(ErrorCode.FORBIDDEN, ex.getErrorCode());
    }

    @Test
    @DisplayName("updateSpu — 本人商品：条件覆盖更新（运费绑定非空即校验）")
    void shouldUpdateEditable_whenOwner() {
        when(spuRepository.findByBusinessId(businessKey(1L))).thenReturn(spuWithId(100L, 1L));

        UpdateSpuReq req = new UpdateSpuReq();
        req.setSpuName("新款手机");
        req.setFreightTemplateId(5L);
        spuManager.updateSpu(businessKey(1L), 100L, req);

        verify(freightTemplateManager).requireBindable(100L, 5L);
        verify(spuRepository).updateEditable(eq(1L), eq("新款手机"), any(), any(), any(), any(), eq(5L));
    }

    @Test
    @DisplayName("updateSpu — 编辑成功后摄取合并态（PATCH 后重读，name 为新值、其余保留原值），无事务直接发送")
    void shouldIngestMergedState_whenUpdateSpu() {
        Spu before = spuWithId(100L, 1L);
        Spu after = spuWithId(100L, 1L);
        after.setName("新款手机");
        after.setCategoryId(20L);
        // 解析（业务键 → 技术键）与「重读合并态」是两次不同口径的查询：前者按业务键，后者按本域技术键
        when(spuRepository.findByBusinessId(businessKey(1L))).thenReturn(before);
        when(spuRepository.findById(1L)).thenReturn(after);
        when(skuRepository.findOnSaleBySpuIds(List.of(1L))).thenReturn(List.of(
                Sku.builder().id(9L).spuId(1L).price(1200).status(1).build()));

        UpdateSpuReq req = new UpdateSpuReq();
        req.setSpuName("新款手机");
        req.setCategoryId(20L);
        spuManager.updateSpu(businessKey(1L), 100L, req);

        ArgumentCaptor<SpuVectorUpsertCO> captor = ArgumentCaptor.forClass(SpuVectorUpsertCO.class);
        verify(spuVectorClient).upsert(captor.capture());
        SpuVectorUpsertCO co = captor.getValue();
        // ADR-061 §决策 2：I47 幂等键是 SPU 业务键（spu.spu_id 生成的 32-hex），不是技术键 1L
        assertEquals(after.getSpuId(), co.getSpuId());
        assertNotEquals("1", co.getSpuId(), "摄取幂等键不得退回技术键");
        assertEquals("新款手机", co.getSpuName());
        assertEquals("旗舰", co.getDescription());
        assertEquals("品牌A", co.getBrand());
        assertEquals(20L, co.getCategoryId());
        assertEquals("img.jpg", co.getMainImage());
        assertEquals(1200, co.getPrice());
    }

    // ==================== updateSpuStatus ====================

    @Test
    @DisplayName("updateSpuStatus — 存在：状态变更落库")
    void shouldUpdateStatus_whenExists() {
        when(spuRepository.findByBusinessId(businessKey(1L))).thenReturn(spuWithId(100L, 1L));

        spuManager.updateSpuStatus(businessKey(1L), 100L, false, SpuStatusEnum.OFF_SHELF.getCode());

        verify(spuRepository).updateStatus(1L, SpuStatusEnum.OFF_SHELF.getCode());
    }

    @Test
    @DisplayName("updateSpuStatus — 不存在 → NO_RESOURCE_FOUND(1103 / HTTP 404)")
    void shouldThrowNotFound_whenStatusChangeOnMissing() {
        when(spuRepository.findByBusinessId(businessKey(1L))).thenReturn(null);

        BizException ex = assertThrows(BizException.class,
                () -> spuManager.updateSpuStatus(businessKey(1L), 100L, false, SpuStatusEnum.OFF_SHELF.getCode()));
        assertEquals(ErrorCode.NO_RESOURCE_FOUND, ex.getErrorCode());
    }

    @Test
    @DisplayName("updateSpuStatus — 越权防护：非商品归属商家 → FORBIDDEN 且不落库")
    void shouldRejectStatusChange_whenNotOwner() {
        when(spuRepository.findByBusinessId(businessKey(1L))).thenReturn(spuWithId(100L, 1L));

        BizException ex = assertThrows(BizException.class, () -> spuManager
                .updateSpuStatus(businessKey(1L), 999L, false, SpuStatusEnum.OFF_SHELF.getCode()));
        assertEquals(ErrorCode.FORBIDDEN, ex.getErrorCode());
        verify(spuRepository, never()).updateStatus(anyLong(), anyInt());
    }

    @Test
    @DisplayName("updateSpuStatus — 管理员放行：非归属商家亦可上下架")
    void shouldAllowStatusChange_whenAdmin() {
        when(spuRepository.findByBusinessId(businessKey(1L))).thenReturn(spuWithId(100L, 1L));

        spuManager.updateSpuStatus(businessKey(1L), 999L, true, SpuStatusEnum.OFF_SHELF.getCode());

        verify(spuRepository).updateStatus(1L, SpuStatusEnum.OFF_SHELF.getCode());
    }

    @Test
    @DisplayName("updateSpuStatus — 上下架不摄取向量（内容未变，下架商品由 P27 组装过滤，ADR-046）")
    void shouldNotIngest_whenUpdateStatus() {
        when(spuRepository.findByBusinessId(businessKey(1L))).thenReturn(spuWithId(100L, 1L));

        spuManager.updateSpuStatus(businessKey(1L), 100L, false, SpuStatusEnum.OFF_SHELF.getCode());

        verify(spuVectorClient, never()).upsert(any(SpuVectorUpsertCO.class));
    }

    // ==================== similarSpus（P27，ADR-046） ====================

    @Test
    @DisplayName("similarSpus — 按命中顺序（相似度降序）组装，不随批量查询结果顺序漂移")
    void shouldAssembleInHitOrder_whenSimilarHits() {
        Spu p5 = spuWithId(200L, 5L);
        p5.setSoldCount(11);
        // 锚点 1L：技术键 → 业务键解析（I48 入参口径）
        when(spuRepository.findByBusinessId(businessKey(1L))).thenReturn(spuWithId(100L, 1L));
        when(spuVectorClient.similar(businessKey(1L), 5)).thenReturn(List.of(
                new org.icedamericanomall.dto.SpuVectorHitCO(businessKey(5L), 0.92),
                new org.icedamericanomall.dto.SpuVectorHitCO(businessKey(3L), 0.71)));
        // I48 命中为业务键 → 同库批量解析技术键
        when(spuRepository.idMapByBusinessIds(List.of(businessKey(5L), businessKey(3L))))
                .thenReturn(Map.of(businessKey(5L), 5L, businessKey(3L), 3L));
        // 批量取数入参顺序无关（最终顺序由命中序列恢复），故此桩用 anyList 匹配
        when(spuRepository.findAllByIds(anyList())).thenReturn(List.of(
                spuWithId(300L, 3L), p5));
        // 最低价批量查询与顺序无关（最终顺序由命中序列恢复），anyList 匹配
        when(skuRepository.findOnSaleBySpuIds(anyList())).thenReturn(List.of(
                Sku.builder().id(51L).spuId(5L).price(500).status(1).build(),
                Sku.builder().id(31L).spuId(3L).price(300).status(1).build()));

        List<RecommendSpuVO> result = spuManager.similarSpus(businessKey(1L), null);

        assertEquals(2, result.size());
        assertEquals(businessKey(5L), result.get(0).getSpuId());
        assertEquals(businessKey(3L), result.get(1).getSpuId());
        assertEquals(500, result.get(0).getPrice());
        assertEquals(11, result.get(0).getSoldCount());
        assertEquals("手机", result.get(0).getSpuName());
    }

    @Test
    @DisplayName("similarSpus — 恒过滤在售并剔除自身：非在售/自身命中不出卡")
    void shouldFilterNonOnSaleAndSelf_whenAssembling() {
        when(spuRepository.findByBusinessId(businessKey(1L))).thenReturn(spuWithId(100L, 1L));
        when(spuVectorClient.similar(businessKey(1L), 5)).thenReturn(List.of(
                new org.icedamericanomall.dto.SpuVectorHitCO(businessKey(1L), 0.99),
                new org.icedamericanomall.dto.SpuVectorHitCO(businessKey(2L), 0.9),
                new org.icedamericanomall.dto.SpuVectorHitCO(businessKey(3L), 0.8)));
        when(spuRepository.idMapByBusinessIds(
                List.of(businessKey(1L), businessKey(2L), businessKey(3L))))
                .thenReturn(Map.of(businessKey(1L), 1L, businessKey(2L), 2L, businessKey(3L), 3L));
        Spu self = spuWithId(100L, 1L);
        Spu offShelf = spuWithId(200L, 2L);
        offShelf.setStatus(SpuStatusEnum.OFF_SHELF.getCode());
        Spu onShelf = spuWithId(300L, 3L);
        // 批量取数入参顺序无关（业务键 → 技术键映射决定顺序），故此桩用 anyList 匹配
        when(spuRepository.findAllByIds(anyList()))
                .thenReturn(List.of(self, offShelf, onShelf));
        when(skuRepository.findOnSaleBySpuIds(List.of(3L))).thenReturn(List.of(
                Sku.builder().id(31L).spuId(3L).price(300).status(1).build()));

        List<RecommendSpuVO> result = spuManager.similarSpus(businessKey(1L), null);

        assertEquals(1, result.size());
        assertEquals(businessKey(3L), result.get(0).getSpuId());
    }

    @Test
    @DisplayName("similarSpus — 锚点商品不存在（无业务键）→ 空列表且不打远程调用")
    void shouldReturnEmpty_whenAnchorMissing() {
        when(spuRepository.findByBusinessId(businessKey(1L))).thenReturn(null);

        List<RecommendSpuVO> result = spuManager.similarSpus(businessKey(1L), null);

        assertTrue(result.isEmpty());
        verify(spuVectorClient, never()).similar(anyString(), anyInt());
    }

    @Test
    @DisplayName("similarSpus — 命中为空 → 空列表不报错，不触发本地批量查询")
    void shouldReturnEmpty_whenClientReturnsEmpty() {
        when(spuRepository.findByBusinessId(businessKey(1L))).thenReturn(spuWithId(100L, 1L));
        when(spuVectorClient.similar(businessKey(1L), 5)).thenReturn(List.of());

        List<RecommendSpuVO> result = spuManager.similarSpus(businessKey(1L), null);

        assertTrue(result.isEmpty());
        verify(spuRepository, never()).findAllByIds(any());
    }

    @Test
    @DisplayName("similarSpus — client 抛异常（防御层）→ 空列表不报错")
    void shouldReturnEmpty_whenClientThrows() {
        when(spuRepository.findByBusinessId(businessKey(1L))).thenReturn(spuWithId(100L, 1L));
        when(spuVectorClient.similar(businessKey(1L), 5))
                .thenThrow(new RuntimeException("search-service 不可用"));

        List<RecommendSpuVO> result = spuManager.similarSpus(businessKey(1L), null);

        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("similarSpus — size 归一：缺省 5、上限 20（与服务端 I48 归一一致）")
    void shouldNormalizeSize_whenSimilar() {
        when(spuRepository.findByBusinessId(businessKey(1L))).thenReturn(spuWithId(100L, 1L));
        when(spuRepository.findByBusinessId(businessKey(2L))).thenReturn(spuWithId(100L, 2L));
        when(spuVectorClient.similar(businessKey(1L), 5)).thenReturn(List.of());
        when(spuVectorClient.similar(businessKey(2L), 20)).thenReturn(List.of());

        spuManager.similarSpus(businessKey(1L), null);
        spuManager.similarSpus(businessKey(2L), 50);

        verify(spuVectorClient).similar(businessKey(1L), 5);
        verify(spuVectorClient).similar(businessKey(2L), 20);
    }

    // ==================== 内部契约回填 ====================

    @Test
    @DisplayName("getSellerIdMapBySpuIds — 委托仓储")
    void shouldDelegateSellerIdMap() {
        when(spuRepository.sellerIdMapBySpuIds(List.of(1L))).thenReturn(Map.of(1L, 100L));

        Map<Long, Long> map = spuManager.getSellerIdMapBySpuIds(List.of(1L));

        assertEquals(100L, map.get(1L));
    }

    // ==================== 分页 ====================

    @Test
    @DisplayName("pageSpus — 关键词分页：最低价回填")
    void shouldFillMinPrice_whenPaging() {
        Spu listed = spu(100L);
        listed.setId(1L);
        var page = new Page<Spu>(1, 10);
        page.setRecords(List.of(listed));
        page.setTotal(1);
        when(spuRepository.pageOnSale(null, null, "手机", null, 1, 10)).thenReturn(page);
        when(skuRepository.findOnSaleBySpuIds(List.of(1L))).thenReturn(List.of(
                org.icedamericanomall.item.domain.model.sku.Sku.builder()
                        .id(2L).spuId(1L).price(599900).status(1).build()));

        SpuVO first = spuManager.pageSpus(null, null, "手机", null, null, 1, 10)
                .getRecords().get(0);

        assertEquals("手机", first.getSpuName());
        assertEquals(599900, first.getMinPrice());
    }

    @Test
    @DisplayName("pageSpus — 商品无在售 SKU 时最低价保持 null")
    void shouldKeepMinPriceNull_whenNoOnSaleSku() {
        Spu listed = spu(100L);
        listed.setId(1L);
        var page = new Page<Spu>(1, 10);
        page.setRecords(List.of(listed));
        page.setTotal(1);
        when(spuRepository.pageOnSale(null, null, "手机", null, 1, 10)).thenReturn(page);
        when(skuRepository.findOnSaleBySpuIds(List.of(1L))).thenReturn(List.of());

        SpuVO first = spuManager.pageSpus(null, null, "手机", null, null, 1, 10)
                .getRecords().get(0);

        assertNull(first.getMinPrice());
    }

    @Test
    @DisplayName("pageSpus — sort=price+order=asc → 走最低价升序查询（跨页顺序由 DB 保证）")
    void shouldRouteToMinPriceOrder_whenSortByPrice() {
        Spu listed = spu(100L);
        listed.setId(1L);
        var page = new Page<Spu>(1, 10);
        page.setRecords(List.of(listed));
        page.setTotal(1);
        when(spuRepository.pageOrderByMinPrice(1, 10, null, null, "手机", "ASC")).thenReturn(page);
        when(skuRepository.findOnSaleBySpuIds(List.of(1L))).thenReturn(List.of());

        spuManager.pageSpus(null, null, "手机", "price", "asc", 1, 10);

        verify(spuRepository).pageOrderByMinPrice(1, 10, null, null, "手机", "ASC");
        verify(spuRepository, never())
                .pageOnSale(any(), any(), any(), any(), anyInt(), anyInt());
    }

    // ==================== P38 短码解析（B7b，ADR-063 §决策 8） ====================

    /** 短码桩（10 位小写 hex，形态合法）。 */
    private static final String SHORT_CODE = "8f3a1c9d20";

    @Test
    @DisplayName("getSpuDetailByCode — 短码解析出业务键 → 复用既有详情装配（同形返回）")
    void shouldReuseDetailAssembly_whenShortCodeResolved() {
        when(shortCodeResolver.resolve(SHORT_CODE)).thenReturn(businessKey(1L));
        when(spuRepository.findByBusinessId(businessKey(1L))).thenReturn(spuWithId(100L, 1L));
        when(skuRepository.findBySpuId(1L)).thenReturn(List.of(
                Sku.builder().id(1L).spuId(1L).price(599900).status(1).build()));

        SpuVO vo = spuManager.getSpuDetailByCode(SHORT_CODE);

        // 与 P2 同一装配：若另建第二套逻辑，这里必然缺字段
        assertEquals("手机", vo.getSpuName());
        assertEquals(599900, vo.getMinPrice());
        assertEquals(1, vo.getSkus().size());
    }

    @Test
    @DisplayName("getSpuDetailByCode — 短码不存在（含从未分配）→ NO_RESOURCE_FOUND(1103 / 404)")
    void shouldThrow404_whenShortCodeUnknown() {
        when(shortCodeResolver.resolve(SHORT_CODE)).thenReturn(null);

        BizException ex = assertThrows(BizException.class,
                () -> spuManager.getSpuDetailByCode(SHORT_CODE));

        assertEquals(ErrorCode.NO_RESOURCE_FOUND, ex.getErrorCode());
        assertEquals(404, ErrorCode.NO_RESOURCE_FOUND.getHttpStatus().value());
        // 短码解析不到时绝不再查一次业务键（避免把「码废了」放大成两次库往返）
        verify(spuRepository, never()).findByBusinessId(anyString());
    }

    @Test
    @DisplayName("getSpuDetailByCode — 商品已下架（status=2）→ 200 + SpuVO（status 如实回给落地页）")
    void shouldReturnOffShelfDetail_whenShortCodeResolvesToOffShelfSpu() {
        Spu offShelf = spuWithId(100L, 1L);
        offShelf.setStatus(SpuStatusEnum.OFF_SHELF.getCode());
        when(shortCodeResolver.resolve(SHORT_CODE)).thenReturn(businessKey(1L));
        when(spuRepository.findByBusinessId(businessKey(1L))).thenReturn(offShelf);
        when(skuRepository.findBySpuId(1L)).thenReturn(List.of());

        SpuVO vo = spuManager.getSpuDetailByCode(SHORT_CODE);

        assertEquals(SpuStatusEnum.OFF_SHELF.getCode(), vo.getStatus());
    }

    @Test
    @DisplayName("getSpuDetailByCode — 商品已软删（status=3）→ 404（短码仍算已占用，不回收）")
    void shouldThrow404_whenShortCodeResolvesToDeletedSpu() {
        Spu deleted = spuWithId(100L, 1L);
        deleted.setStatus(SpuStatusEnum.DELETED.getCode());
        when(shortCodeResolver.resolve(SHORT_CODE)).thenReturn(businessKey(1L));
        when(spuRepository.findByBusinessId(businessKey(1L))).thenReturn(deleted);

        BizException ex = assertThrows(BizException.class,
                () -> spuManager.getSpuDetailByCode(SHORT_CODE));

        assertEquals(ErrorCode.NO_RESOURCE_FOUND, ex.getErrorCode());
    }

    // ==================== 短码发号与冲突重试（ADR-063 §决策 8.2/8.3） ====================

    @Test
    @DisplayName("createSpu — 短码在 publish 定稿：落库聚合已带合法 10 位短码（禁止懒生成）")
    void shouldAssignShortCode_whenPublishing() {
        when(spuRepository.save(any(Spu.class))).thenReturn(spuWithId(100L, 1L));

        spuManager.createSpu(100L, createReq());

        ArgumentCaptor<Spu> captor = ArgumentCaptor.forClass(Spu.class);
        verify(spuRepository).save(captor.capture());
        assertNotNull(captor.getValue().getShortCode());
        assertEquals(10, captor.getValue().getShortCode().length());
        assertTrue(captor.getValue().getShortCode().matches("^[0-9a-f]{10}$"));
    }

    @Test
    @DisplayName("createSpu — 短码唯一键冲突 → 换号重试（≤3 次）后成功，每次换的是新随机值")
    void shouldRetryWithNewShortCode_whenUniqueKeyCollides() {
        // 记录「每次 save 时聚合上的短码」——必须按调用时刻取值：聚合是可变的，
        // ArgumentCaptor 只会拿到同一个实例的最终状态（全部相同，断言会失真）
        List<String> attemptedCodes = new ArrayList<>();
        when(spuRepository.save(any(Spu.class))).thenAnswer(invocation -> {
            Spu attempted = invocation.getArgument(0);
            attemptedCodes.add(attempted.getShortCode());
            if (attemptedCodes.size() < 3) {
                throw new DuplicateKeyException("Duplicate entry 'x' for key 'spu.uk_short_code'");
            }
            attempted.setId(1L);
            return attempted;
        });

        spuManager.createSpu(100L, createReq());

        assertEquals(3, attemptedCodes.size());
        assertNotEquals(attemptedCodes.get(0), attemptedCodes.get(1),
                "重试必须换新随机短码（不得沿用冲突值）");
        assertNotEquals(attemptedCodes.get(1), attemptedCodes.get(2));
        attemptedCodes.forEach(code -> assertTrue(code.matches("^[0-9a-f]{10}$"), code));
    }

    @Test
    @DisplayName("createSpu — 短码连续冲突 3 次 → 硬失败 500，不回落派生值、不多试第 4 次")
    void shouldFailLoud_whenShortCodeCollisionPersists() {
        when(spuRepository.save(any(Spu.class)))
                .thenThrow(new DuplicateKeyException("Duplicate entry 'x' for key 'spu.uk_short_code'"));

        BizException ex = assertThrows(BizException.class, () -> spuManager.createSpu(100L, createReq()));

        assertEquals(ErrorCode.INTERNAL_ERROR, ex.getErrorCode());
        verify(spuRepository, times(3)).save(any(Spu.class));
    }

    @Test
    @DisplayName("createSpu — 冲突来自 uk_spu_id（非短码）→ 原样抛出，不套 3 次无意义重试")
    void shouldRethrow_whenOtherUniqueKeyCollides() {
        when(spuRepository.save(any(Spu.class)))
                .thenThrow(new DuplicateKeyException("Duplicate entry 'x' for key 'spu.uk_spu_id'"));

        assertThrows(DuplicateKeyException.class, () -> spuManager.createSpu(100L, createReq()));

        verify(spuRepository, times(1)).save(any(Spu.class));
    }
}
