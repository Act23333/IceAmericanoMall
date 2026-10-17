package org.icedamericanomall.item.app;

import org.icedamericanomall.common.enums.ErrorCategory;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BadRequestException;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.dto.SkuCO;
import org.icedamericanomall.item.app.dto.SkuSpuDisplay;
import org.icedamericanomall.item.app.port.SkuSpuDisplayQuery;
import org.icedamericanomall.item.domain.enums.StockTypeEnum;
import org.icedamericanomall.item.domain.model.sku.Sku;
import org.icedamericanomall.item.domain.repository.SkuRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * SkuManager 单元测试 —— 原子扣减/恢复编排（不限量分支、库存不足、幂等恢复）、最低价聚合、
 * I13 商品展示字段与<b>业务键（ADR-061）</b>的批量回填与 fail-open 口径。
 *
 * <p><b>口型要点（ADR-061 §决策 2）</b>：I13 与库存端点的入参一律是 SKU <b>业务键</b> 32-hex；
 * {@code skuRepository} 的原子 SQL 仍按<b>技术键</b>（{@code sku.id}）操作，两者的换算由
 * {@link SkuManager} 经 {@link SkuSpuDisplayQuery} 完成 —— 本类即锁定该换算与错误语义。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SkuManager 编排单元测试")
class SkuManagerTest {

    /** SKU 技术键（{@code sku.id}）。 */
    private static final Long SKU_PK = 1L;
    private static final Long SKU_PK_2 = 2L;
    /** SKU 业务键（32-hex，与 {@code sku.sku_id} 同形）。 */
    private static final String SKU_BIZ = "0f1e2d3c4b5a69788796a5b4c3d2e1f0";
    private static final String SKU_BIZ_2 = "112233445566778899aabbccddeeff00";
    private static final String SPU_BIZ = "aabbccddeeff00112233445566778899";
    private static final String SPU_BIZ_2 = "00112233445566778899aabbccddeeff";

    @Mock
    private SkuRepository skuRepository;

    @Mock
    private SkuSpuDisplayQuery skuSpuDisplayQuery;

    private SkuManager skuManager;

    @BeforeEach
    void setUp() {
        skuManager = new SkuManager(skuRepository,
                new org.icedamericanomall.item.adapter.assembler.SkuConverterImpl(),
                skuSpuDisplayQuery);
    }

    private Sku sku(int stockType) {
        return Sku.builder().id(SKU_PK).skuId(SKU_BIZ).spuId(10L)
                .price(1000).stock(50).stockType(stockType).status(1).build();
    }

    /** 单条解析结果（业务键 → 技术键 + 展示列）。 */
    private SkuSpuDisplay display(Long skuPk, String skuBiz, String spuBiz) {
        return new SkuSpuDisplay(skuPk, skuBiz, 10L, spuBiz, "手工陶瓷茶杯", 5L, 10L, 77L);
    }

    private static Map<String, SkuSpuDisplay> byBusiness(SkuSpuDisplay... rows) {
        Map<String, SkuSpuDisplay> map = new LinkedHashMap<>();
        for (SkuSpuDisplay r : rows) {
            map.put(r.skuBusinessId(), r);
        }
        return map;
    }

    // ==================== deductStock ====================

    @Test
    @DisplayName("deductStock — 限量 SKU：业务键解析为技术键后原子扣减成功（行数 1）")
    void shouldDeduct_whenLimitedStockAvailable() {
        when(skuSpuDisplayQuery.findBySkuBusinessIds(List.of(SKU_BIZ)))
                .thenReturn(byBusiness(display(SKU_PK, SKU_BIZ, SPU_BIZ)));
        when(skuRepository.findById(SKU_PK)).thenReturn(Optional.of(sku(StockTypeEnum.LIMITED.getCode())));
        when(skuRepository.deductStock(SKU_PK, 2)).thenReturn(1);

        skuManager.deductStock(SKU_BIZ, 2);

        verify(skuRepository).deductStock(SKU_PK, 2);
        verify(skuRepository, never()).increaseSoldCount(anyLong(), anyInt());
    }

    @Test
    @DisplayName("deductStock — 不限量 SKU：仅累加销量，不校验库存")
    void shouldIncreaseSoldCount_whenUnlimited() {
        when(skuSpuDisplayQuery.findBySkuBusinessIds(List.of(SKU_BIZ)))
                .thenReturn(byBusiness(display(SKU_PK, SKU_BIZ, SPU_BIZ)));
        when(skuRepository.findById(SKU_PK)).thenReturn(Optional.of(sku(StockTypeEnum.UNLIMITED.getCode())));
        when(skuRepository.increaseSoldCount(SKU_PK, 2)).thenReturn(1);

        skuManager.deductStock(SKU_BIZ, 2);

        verify(skuRepository).increaseSoldCount(SKU_PK, 2);
        verify(skuRepository, never()).deductStock(anyLong(), anyInt());
    }

    /**
     * 拆分守卫（批次 ③「拆双义错误码」）：<b>业务键形态非法</b> → {@code PARAM_ERROR(1101)} / 400
     * （{@code CLIENT}），<b>不再是</b> {@code SKU_NOT_FOUND(3016)}。
     *
     * <p><b>改前必失败</b>：改前本用例（原名 {@code shouldThrowNotFound_whenBusinessKeyUnresolvable}）
     * 对 {@code prod-001} 断言 {@code SKU_NOT_FOUND} —— 旧实现通过、本用例红；
     * 拆分后反之。{@code prod-001} 刻意保留为同一台「旧语义探测器」。
     */
    @Test
    @DisplayName("deductStock — 业务键形态非法 → PARAM_ERROR(400)，不是「SKU 不存在」，不碰库存 SQL")
    void shouldThrowParamError_whenBusinessKeyMalformed() {
        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> skuManager.deductStock("prod-001", 2));

        assertEquals(ErrorCode.PARAM_ERROR, ex.getErrorCode());
        assertNotEquals(ErrorCode.SKU_NOT_FOUND, ex.getErrorCode(),
                "形态非法是参数错误，不得冒充「SKU 不存在」");
        // 归属判定走 ErrorCategory 派生（不手填 category）：400 → CLIENT
        assertEquals(ErrorCategory.CLIENT, ErrorCategory.of(ex.getErrorCode()));
        verify(skuRepository, never()).deductStock(anyLong(), anyInt());
    }

    /**
     * 拆分守卫的另一半：<b>形态合法但 SKU 不存在</b> → {@code SKU_NOT_FOUND(3016)}（fail-closed）。
     */
    @Test
    @DisplayName("deductStock — 业务键形态合法但 SKU 不存在 → SKU_NOT_FOUND(3016)，不碰库存 SQL")
    void shouldThrowNotFound_whenBusinessKeyWellFormedButAbsent() {
        when(skuSpuDisplayQuery.findBySkuBusinessIds(List.of(SKU_BIZ))).thenReturn(Map.of());

        BizException ex = assertThrows(BizException.class, () -> skuManager.deductStock(SKU_BIZ, 2));

        assertEquals(ErrorCode.SKU_NOT_FOUND, ex.getErrorCode());
        assertNotEquals(ErrorCode.PARAM_ERROR, ex.getErrorCode(),
                "形态合法的业务键解析不到，是「不存在」而不是「参数错误」");
        verify(skuRepository, never()).deductStock(anyLong(), anyInt());
    }

    /**
     * 恢复链路（I15，fail-open）：形态非法<b>不</b>抛 400，也不打库 —— 补偿链路不得因脏键中断。
     *
     * <p><b>改前必失败</b>：改前该分支会走「解析 → 未命中 → 返回 null」，虽然结果同为跳过，
     * 但本用例额外断言「根本不去查解析读模型」，这正是把「形态非法」在链路最早处截断的证据。
     */
    @Test
    @DisplayName("restoreStock — 业务键形态非法 → 按「不存在」幂等跳过（补偿链路 fail-open，不打库）")
    void shouldSkipSilently_whenRestoreBusinessKeyMalformed() {
        assertDoesNotThrow(() -> skuManager.restoreStock("prod-001", 2));

        verify(skuSpuDisplayQuery, never()).findBySkuBusinessIds(anyCollection());
        verify(skuRepository, never()).restoreStock(anyLong(), anyInt());
    }

    @Test
    @DisplayName("deductStock — 行数 0 → STOCK_INSUFFICIENT")
    void shouldThrowStockInsufficient_whenNoRowAffected() {
        when(skuSpuDisplayQuery.findBySkuBusinessIds(List.of(SKU_BIZ)))
                .thenReturn(byBusiness(display(SKU_PK, SKU_BIZ, SPU_BIZ)));
        when(skuRepository.findById(SKU_PK)).thenReturn(Optional.of(sku(StockTypeEnum.LIMITED.getCode())));
        when(skuRepository.deductStock(SKU_PK, 999)).thenReturn(0);

        BizException ex = assertThrows(BizException.class, () -> skuManager.deductStock(SKU_BIZ, 999));
        assertEquals(ErrorCode.STOCK_INSUFFICIENT, ex.getErrorCode());
    }

    @Test
    @DisplayName("deductStock — SKU 行已删除（解析到技术键但取不到聚合）→ SKU_NOT_FOUND")
    void shouldThrowNotFound_whenSkuMissing() {
        when(skuSpuDisplayQuery.findBySkuBusinessIds(List.of(SKU_BIZ)))
                .thenReturn(byBusiness(display(SKU_PK, SKU_BIZ, SPU_BIZ)));
        when(skuRepository.findById(SKU_PK)).thenReturn(Optional.empty());

        BizException ex = assertThrows(BizException.class, () -> skuManager.deductStock(SKU_BIZ, 2));
        assertEquals(ErrorCode.SKU_NOT_FOUND, ex.getErrorCode());
    }

    // ==================== restoreStock ====================

    @Test
    @DisplayName("restoreStock — 限量 SKU：原子恢复成功")
    void shouldRestore_whenLimited() {
        when(skuSpuDisplayQuery.findBySkuBusinessIds(List.of(SKU_BIZ)))
                .thenReturn(byBusiness(display(SKU_PK, SKU_BIZ, SPU_BIZ)));
        when(skuRepository.findById(SKU_PK)).thenReturn(Optional.of(sku(StockTypeEnum.LIMITED.getCode())));
        when(skuRepository.restoreStock(SKU_PK, 2)).thenReturn(1);

        skuManager.restoreStock(SKU_BIZ, 2);

        verify(skuRepository).restoreStock(SKU_PK, 2);
    }

    @Test
    @DisplayName("restoreStock — 不限量 SKU：仅回退销量")
    void shouldDecreaseSoldCount_whenUnlimited() {
        when(skuSpuDisplayQuery.findBySkuBusinessIds(List.of(SKU_BIZ)))
                .thenReturn(byBusiness(display(SKU_PK, SKU_BIZ, SPU_BIZ)));
        when(skuRepository.findById(SKU_PK)).thenReturn(Optional.of(sku(StockTypeEnum.UNLIMITED.getCode())));
        when(skuRepository.decreaseSoldCount(SKU_PK, 2)).thenReturn(1);

        skuManager.restoreStock(SKU_BIZ, 2);

        verify(skuRepository).decreaseSoldCount(SKU_PK, 2);
    }

    @Test
    @DisplayName("restoreStock — 业务键解析不到（SKU 已删除）：记 warn 后幂等返回（不抛异常）")
    void shouldBeIdempotent_whenSkuDeleted() {
        when(skuSpuDisplayQuery.findBySkuBusinessIds(List.of(SKU_BIZ))).thenReturn(Map.of());

        assertDoesNotThrow(() -> skuManager.restoreStock(SKU_BIZ, 2));
        verify(skuRepository, never()).restoreStock(anyLong(), anyInt());
    }

    @Test
    @DisplayName("restoreStock — 原子 SQL 影响 0 行：幂等跳过不抛异常")
    void shouldBeIdempotent_whenRestoreAffectsNoRow() {
        when(skuSpuDisplayQuery.findBySkuBusinessIds(List.of(SKU_BIZ)))
                .thenReturn(byBusiness(display(SKU_PK, SKU_BIZ, SPU_BIZ)));
        when(skuRepository.findById(SKU_PK)).thenReturn(Optional.of(sku(StockTypeEnum.LIMITED.getCode())));
        when(skuRepository.restoreStock(SKU_PK, 2)).thenReturn(0);

        assertDoesNotThrow(() -> skuManager.restoreStock(SKU_BIZ, 2));
    }

    // ==================== 最低价聚合 ====================

    @Test
    @DisplayName("getMinPriceMapBySpuIds — 同商品取在售最低价")
    void shouldTakeMinPricePerSpu() {
        when(skuRepository.findOnSaleBySpuIds(List.of(10L))).thenReturn(List.of(
                Sku.builder().id(1L).spuId(10L).price(699900).status(1).build(),
                Sku.builder().id(2L).spuId(10L).price(599900).status(1).build()));

        Map<Long, Integer> map = skuManager.getMinPriceMapBySpuIds(List.of(10L));

        assertEquals(599900, map.get(10L));
    }

    @Test
    @DisplayName("getMinPriceMapBySpuIds — 入参为空返回空映射")
    void shouldReturnEmpty_whenSpuIdsEmpty() {
        assertTrue(skuManager.getMinPriceMapBySpuIds(List.of()).isEmpty());
    }

    // ==================== I13 商品展示字段 + 业务键回填 ====================

    @Test
    @DisplayName("getCosByIds — 业务键入参：整批一次解析（无 N+1），CO 出业务键 + 展示列")
    void shouldEnrichSpuFieldsInOneBatch_whenCosRequested() {
        when(skuSpuDisplayQuery.findBySkuBusinessIds(List.of(SKU_BIZ, SKU_BIZ_2)))
                .thenReturn(byBusiness(
                        display(SKU_PK, SKU_BIZ, SPU_BIZ),
                        new SkuSpuDisplay(SKU_PK_2, SKU_BIZ_2, 11L, SPU_BIZ_2, "青花瓷碗", 6L, 20L, null)));
        when(skuRepository.findByIds(List.of(SKU_PK, SKU_PK_2))).thenReturn(List.of(
                Sku.builder().id(SKU_PK).spuId(10L).price(1000).status(1).build(),
                Sku.builder().id(SKU_PK_2).spuId(11L).price(2000).status(1).build()));

        List<SkuCO> cos = skuManager.getCosByIds(List.of(SKU_BIZ, SKU_BIZ_2));

        assertEquals(SKU_BIZ, cos.get(0).getSkuId(), "I13 出 SKU 业务键（技术键 1 绝不能出现）");
        assertEquals(SPU_BIZ, cos.get(0).getSpuId(), "I13 出 SPU 业务键（技术外键 10 绝不能出现）");
        assertEquals("手工陶瓷茶杯", cos.get(0).getSpuName(), "ADR-061 §决策 5：spu_name ← spu.name");
        assertEquals(5L, cos.get(0).getSellerId());
        assertEquals(10L, cos.get(0).getCategoryId());
        assertEquals(77L, cos.get(0).getFreightTemplateId());
        assertEquals("青花瓷碗", cos.get(1).getSpuName());
        assertNull(cos.get(1).getFreightTemplateId(), "未绑定运费模板 → null（不回落）");
        verify(skuSpuDisplayQuery, times(1)).findBySkuBusinessIds(anyCollection());
    }

    @Test
    @DisplayName("getCosByIds — 单次 SQL 同时给出业务键与展示列（不得二次查询）")
    void shouldNotQueryTwice_whenResolvingBusinessKeys() {
        when(skuSpuDisplayQuery.findBySkuBusinessIds(List.of(SKU_BIZ)))
                .thenReturn(byBusiness(display(SKU_PK, SKU_BIZ, SPU_BIZ)));
        when(skuRepository.findByIds(List.of(SKU_PK))).thenReturn(List.of(
                Sku.builder().id(SKU_PK).spuId(10L).price(1000).status(1).build()));

        skuManager.getCosByIds(List.of(SKU_BIZ));

        // 业务键与展示列同源：只允许打一次读模型，绝不为拿业务键再补一次查询
        verify(skuSpuDisplayQuery, times(1)).findBySkuBusinessIds(anyCollection());
        verify(skuSpuDisplayQuery, never()).findBySkuIds(org.mockito.ArgumentMatchers.<java.util.Collection<Long>>any());
    }

    @Test
    @DisplayName("getCosByIds — 业务键解析不到（不存在/格式非法）→ 返回空列表，不抛异常")
    void shouldReturnEmpty_whenBusinessKeyUnresolvable() {
        when(skuSpuDisplayQuery.findBySkuBusinessIds(List.of("prod-001"))).thenReturn(Map.of());

        List<SkuCO> cos = assertDoesNotThrow(() -> skuManager.getCosByIds(List.of("prod-001")));

        assertTrue(cos.isEmpty());
        verify(skuRepository, never()).findByIds(org.mockito.ArgumentMatchers.<java.util.List<Long>>any());
    }

    @Test
    @DisplayName("getCosByIds — 解析查询抛异常：返回空结果（fail-open，不外溢成 500）")
    void shouldDegradeGracefully_whenResolveFails() {
        when(skuSpuDisplayQuery.findBySkuBusinessIds(anyCollection()))
                .thenThrow(new IllegalStateException("spu 表不可用"));

        List<SkuCO> cos = assertDoesNotThrow(() -> skuManager.getCosByIds(List.of(SKU_BIZ)));

        assertTrue(cos.isEmpty(), "读模型故障按「未命中」处理，由调用方决定 fail-open/fail-closed");
    }

    @Test
    @DisplayName("getCosByIds — SKU 本体字段照常返回（业务键解析成功而展示列为空时不得缩行）")
    void shouldKeepSkuBody_whenDisplayColumnsMissing() {
        when(skuSpuDisplayQuery.findBySkuBusinessIds(List.of(SKU_BIZ)))
                .thenReturn(byBusiness(new SkuSpuDisplay(SKU_PK, SKU_BIZ, null, null, null, null, null, null)));
        when(skuRepository.findByIds(List.of(SKU_PK))).thenReturn(List.of(
                Sku.builder().id(SKU_PK).spuId(10L).price(1000).stock(9).status(1).build()));

        List<SkuCO> cos = skuManager.getCosByIds(List.of(SKU_BIZ));

        assertEquals(1, cos.size());
        assertEquals(Integer.valueOf(1000), cos.get(0).getPrice());
        assertEquals(Integer.valueOf(9), cos.get(0).getStock());
        assertNull(cos.get(0).getSpuName());
        assertEquals(SKU_BIZ, cos.get(0).getSkuId(), "SKU 业务键来自 sku 行，不依赖 spu 行是否存在");
    }
}
