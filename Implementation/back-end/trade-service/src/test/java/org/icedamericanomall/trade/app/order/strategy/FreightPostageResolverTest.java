package org.icedamericanomall.trade.app.order.strategy;

import org.icedamericanomall.client.FreightTemplateClient;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.dto.FreightTemplateCO;
import org.icedamericanomall.dto.FreightTemplateQueryCO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

/**
 * {@link FreightPostageResolver} 单元测试 —— 「订单项 → 生效模板」解析、Feign 交互与
 * 短 TTL 本地缓存（F2 运费模型，V5.6；缓存为 ADR-037 后果收敛）。
 *
 * <p>解析规则的权威口径来自 I43 返回集合：只有<b>启用</b>模板会被返回，
 * 故「绑定模板已停用/不存在」在调用方表现为「绑定 id 不在返回集合中」→ 回落默认模板。
 *
 * <p>缓存用例使用<b>真实 Caffeine 实例</b>（不 mock 缓存），仅 mock {@code FreightTemplateClient}：
 * 锁死「同 sellerId 只查一次 / 不同 sellerId 各自加载 / 加载失败不缓存（fail-closed 保持，
 * 下次调用重新发起查询）」三条不变量。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("FreightPostageResolver — 运费解析（F2）+ 短 TTL 缓存")
class FreightPostageResolverTest {
    /** SKU 业务键（32-hex，ADR-061 §决策 2：线格式与订单项标识一律业务键）。 */
    private static final String SKU_BUSINESS_ID = "0f1e2d3c4b5a69788796a5b4c3d2e1f0";
    private static final String SKU_BUSINESS_ID_2 = "112233445566778899aabbccddeeff00";
    private static final String SPU_BUSINESS_ID = "aabbccddeeff00112233445566778899";

    @Mock private FreightTemplateClient freightTemplateClient;

    private FreightPostageResolver resolver() {
        return new FreightPostageResolver(freightTemplateClient);
    }

    private OrderCreateContext.CartItemSnapshot snapshot(Long freightTemplateId) {
        OrderCreateContext.CartItemSnapshot snap = new OrderCreateContext.CartItemSnapshot();
        snap.setSkuId(SKU_BUSINESS_ID);
        snap.setSellerId(5L);
        snap.setPrice(5000);
        snap.setQuantity(1);
        snap.setFreightTemplateId(freightTemplateId);
        return snap;
    }

    private FreightTemplateCO template(Long id, int fee, Integer freeThreshold, boolean isDefault) {
        FreightTemplateCO co = new FreightTemplateCO();
        co.setId(id);
        co.setTemplateNo("T-" + id);
        co.setFee(fee);
        co.setFreeThreshold(freeThreshold);
        co.setStatus(1);
        co.setIsDefault(isDefault);
        return co;
    }

    @Test
    @DisplayName("查询入参 — 缓存 miss 时按「sellerId + 空 templateIds」发起 I43 全量查询")
    void shouldQueryBySellerIdWithEmptyTemplateIds_onCacheMiss() {
        when(freightTemplateClient.query(any())).thenReturn(List.of(template(7L, 800, 0, true)));

        resolver().resolve(5L, List.of(snapshot(7L), snapshot(7L), snapshot(null)), 10000);

        ArgumentCaptor<FreightTemplateQueryCO> captor = ArgumentCaptor.forClass(FreightTemplateQueryCO.class);
        org.mockito.Mockito.verify(freightTemplateClient).query(captor.capture());
        assertEquals(5L, captor.getValue().getSellerId());
        assertEquals(List.of(), captor.getValue().getTemplateIds());
    }

    @Test
    @DisplayName("缓存命中 — 同 sellerId 两次 resolve 仅发起一次 I43 查询，计价结果一致")
    void shouldQueryOnlyOnce_whenSameSellerResolvedTwice() {
        when(freightTemplateClient.query(any()))
                .thenReturn(List.of(template(1L, 600, 0, true), template(7L, 1200, 0, false)));
        FreightPostageResolver resolver = resolver();

        int first = resolver.resolve(5L, List.of(snapshot(7L)), 10000);
        int second = resolver.resolve(5L, List.of(snapshot(7L)), 10000);

        assertEquals(1200, first);
        assertEquals(first, second);
        org.mockito.Mockito.verify(freightTemplateClient, times(1)).query(any());
    }

    @Test
    @DisplayName("缓存按 sellerId 隔离 — 不同商家各自加载，互不串用")
    void shouldLoadEachSeller_whenDifferentSellerIds() {
        when(freightTemplateClient.query(any())).thenReturn(List.of(template(1L, 600, 0, true)));
        FreightPostageResolver resolver = resolver();

        assertEquals(600, resolver.resolve(5L, List.of(snapshot(null)), 10000));
        assertEquals(600, resolver.resolve(6L, List.of(snapshot(null)), 10000));

        ArgumentCaptor<FreightTemplateQueryCO> captor = ArgumentCaptor.forClass(FreightTemplateQueryCO.class);
        org.mockito.Mockito.verify(freightTemplateClient, times(2)).query(captor.capture());
        assertEquals(List.of(5L, 6L),
                captor.getAllValues().stream().map(FreightTemplateQueryCO::getSellerId).toList());
    }

    @Test
    @DisplayName("失败不落缓存 — 查询异常上抛（fail-closed），下一次调用重新发起查询")
    void shouldRetryQuery_whenPreviousQueryFailed() {
        when(freightTemplateClient.query(any()))
                .thenThrow(new BizException(ErrorCode.SYSTEM_BUSY, "运费计算服务暂不可用"));
        FreightPostageResolver resolver = resolver();

        BizException first = assertThrows(BizException.class,
                () -> resolver.resolve(5L, List.of(snapshot(null)), 10000));
        BizException second = assertThrows(BizException.class,
                () -> resolver.resolve(5L, List.of(snapshot(null)), 10000));

        assertEquals(ErrorCode.SYSTEM_BUSY, first.getErrorCode());
        assertEquals(ErrorCode.SYSTEM_BUSY, second.getErrorCode());
        org.mockito.Mockito.verify(freightTemplateClient, times(2)).query(any());
    }

    @Test
    @DisplayName("F2-3 未绑定模板 → 回落默认模板（isDefault）计费")
    void shouldFallBackToDefaultTemplate_whenItemNotBound() {
        // 商家默认模板 T0(fee=600)；商品未绑定 → 用 T0
        when(freightTemplateClient.query(any())).thenReturn(List.of(template(1L, 600, 0, true)));

        assertEquals(600, resolver().resolve(5L, List.of(snapshot(null)), 5000));
    }

    @Test
    @DisplayName("F2-4 绑定模板未出现在启用集合中（已停用/已删）→ 回落默认模板")
    void shouldFallBackToDefault_whenBoundTemplateNotEnabled() {
        // 商品绑定 T1=99，但启用集合只有默认模板 T0 → 回落 T0
        when(freightTemplateClient.query(any())).thenReturn(List.of(template(1L, 600, 0, true)));

        assertEquals(600, resolver().resolve(5L, List.of(snapshot(99L)), 5000));
    }

    @Test
    @DisplayName("绑定模板启用 → 用绑定模板（优先于默认模板）")
    void shouldPreferBoundTemplate_whenEnabled() {
        when(freightTemplateClient.query(any()))
                .thenReturn(List.of(template(1L, 600, 0, true), template(7L, 1200, 0, false)));

        assertEquals(1200, resolver().resolve(5L, List.of(snapshot(7L)), 5000));
    }

    @Test
    @DisplayName("F2-2 多商品各绑定不同模板 → 取最高 fee 计一次")
    void shouldTakeHighestFee_whenMultipleItemsBoundDifferently() {
        when(freightTemplateClient.query(any())).thenReturn(List.of(
                template(1L, 600, 0, true), template(7L, 500, 0, false), template(8L, 1200, 0, false)));

        assertEquals(1200, resolver().resolve(5L, List.of(snapshot(7L), snapshot(8L)), 10000));
    }

    @Test
    @DisplayName("F2-1 总额达默认模板满额阈值 → 免邮")
    void shouldReturnZero_whenDefaultTemplateThresholdReached() {
        when(freightTemplateClient.query(any())).thenReturn(List.of(template(1L, 800, 9900, true)));

        assertEquals(0, resolver().resolve(5L, List.of(snapshot(null)), 10000));
    }

    @Test
    @DisplayName("F2-5 商家无启用模板（空集合）→ postFee = 0，不阻断下单")
    void shouldReturnZero_whenNoEnabledTemplate() {
        when(freightTemplateClient.query(any())).thenReturn(List.of());

        assertEquals(0, resolver().resolve(5L, List.of(snapshot(null)), 10000));
    }

    @Test
    @DisplayName("sellerId 为空（无归属场景）→ 不调用 Feign，运费 0")
    void shouldSkipFeign_whenSellerIdAbsent() {
        assertEquals(0, resolver().resolve(null, List.of(snapshot(7L)), 10000));
        org.mockito.Mockito.verifyNoInteractions(freightTemplateClient);
    }

    @Test
    @DisplayName("降级语义（ADR-011 判别型）— 查询失败 fail-closed：异常上抛，不静默免邮")
    void shouldPropagateException_whenQueryFails() {
        when(freightTemplateClient.query(any()))
                .thenThrow(new BizException(ErrorCode.SYSTEM_BUSY, "运费计算服务暂不可用"));

        BizException ex = assertThrows(BizException.class,
                () -> resolver().resolve(5L, List.of(snapshot(null)), 10000));
        assertEquals(ErrorCode.SYSTEM_BUSY, ex.getErrorCode());
    }
}
