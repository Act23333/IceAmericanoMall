package org.icedamericanomall.marketing.app.coupon;

import org.icedamericanomall.dto.CouponStackInfoCO;
import org.icedamericanomall.marketing.adapter.assembler.MarketingConverter;
import org.icedamericanomall.marketing.domain.enums.CouponCategoryEnum;
import org.icedamericanomall.marketing.domain.enums.DiscountTypeEnum;
import org.icedamericanomall.marketing.domain.enums.StackRuleEnum;
import org.icedamericanomall.marketing.domain.enums.UserCouponStatusEnum;
import org.icedamericanomall.marketing.domain.model.coupon.Coupon;
import org.icedamericanomall.marketing.domain.model.coupon.UserCoupon;
import org.icedamericanomall.marketing.domain.repository.CouponRepository;
import org.icedamericanomall.marketing.domain.repository.UserCouponRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@code CouponUsageManager.batchStackInfo} 单元测试（I69 {@code GET /internal/coupon/batch}）。
 *
 * <p>契约要点：入参是 {@code user_coupon.id} 列表，返回对象的 {@code id} 亦为该持券 ID
 * （便于调用方按原入参对应），{@code couponId} 为券模板业务券号；券模板不存在时跳过（fail-open），
 * 入参为空时返回空列表且不触达仓储。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CouponUsageManager.batchStackInfo — I69 叠加信息批量查询")
class CouponStackInfoBatchTest {

    private static final Long TEMPLATE_A = 11L;
    private static final Long TEMPLATE_B = 12L;
    private static final String COUPON_NO_A = "0f1e2d3c4b5a69788796a5b4c3d2e1f0";
    private static final String COUPON_NO_B = "1a2b3c4d5e6f708192a3b4c5d6e7f809";

    @Mock
    private CouponRepository couponRepository;

    @Mock
    private UserCouponRepository userCouponRepository;

    private final MarketingConverter converter = Mappers.getMapper(MarketingConverter.class);

    private CouponUsageManager couponUsageManager;

    @BeforeEach
    void setUp() {
        // 券面读模型端口：本测试只覆盖 I69 叠加信息批量查询
        couponUsageManager = new CouponUsageManager(couponRepository, userCouponRepository,
                new UserCouponViewAssembler(converter, couponIds -> java.util.Map.of()));
    }

    @Test
    @DisplayName("batchStackInfo — 正常组装：id=持券ID、couponId=业务券号，叠加字段取自模板")
    void shouldAssembleStackInfo_whenUserCouponsExist() {
        when(userCouponRepository.findByIds(List.of(501L, 502L)))
                .thenReturn(List.of(userCoupon(501L, TEMPLATE_A), userCoupon(502L, TEMPLATE_B)));
        when(couponRepository.findById(TEMPLATE_A))
                .thenReturn(template(TEMPLATE_A, COUPON_NO_A, StackRuleEnum.MUTUAL_EXCLUSIVE.getCode(), null, 88L));
        when(couponRepository.findById(TEMPLATE_B))
                .thenReturn(template(TEMPLATE_B, COUPON_NO_B, StackRuleEnum.STACKABLE.getCode(), "GROUP-X", 88L));

        List<CouponStackInfoCO> infos = couponUsageManager.batchStackInfo(List.of(501L, 502L));

        assertEquals(2, infos.size());

        // 顺序不保证（selectByIds 未声明排序）→ 按 id 建索引断言，避免测试依赖实现顺序
        CouponStackInfoCO first = byId(infos, 501L);
        assertEquals(COUPON_NO_A, first.getCouponId());
        assertEquals(CouponCategoryEnum.SHOP.getCode(), first.getCouponCategory());
        assertEquals(StackRuleEnum.MUTUAL_EXCLUSIVE.getCode(), first.getStackRule());
        assertEquals(88L, first.getSellerId());
        assertEquals(DiscountTypeEnum.FIXED.getCode(), first.getDiscountType());

        CouponStackInfoCO second = byId(infos, 502L);
        assertEquals(COUPON_NO_B, second.getCouponId());
        assertEquals(StackRuleEnum.STACKABLE.getCode(), second.getStackRule());
        assertEquals("GROUP-X", second.getStackGroup());
    }

    @Test
    @DisplayName("batchStackInfo — 入参为空/null → 空列表且不查仓储（fail-open）")
    void shouldReturnEmpty_whenIdsEmpty() {
        assertTrue(couponUsageManager.batchStackInfo(List.of()).isEmpty());
        assertTrue(couponUsageManager.batchStackInfo(null).isEmpty());

        verifyNoInteractions(userCouponRepository, couponRepository);
    }

    @Test
    @DisplayName("batchStackInfo — 持券无对应券模板 → 跳过该项而非抛异常（fail-open）")
    void shouldSkip_whenCouponTemplateMissing() {
        when(userCouponRepository.findByIds(Arrays.asList(501L, 502L, 503L)))
                .thenReturn(List.of(userCoupon(501L, TEMPLATE_A), userCoupon(502L, 999L)));
        when(couponRepository.findById(TEMPLATE_A))
                .thenReturn(template(TEMPLATE_A, COUPON_NO_A, StackRuleEnum.STACKABLE.getCode(), "G1", null));
        when(couponRepository.findById(999L)).thenReturn(null);

        List<CouponStackInfoCO> infos = couponUsageManager.batchStackInfo(Arrays.asList(501L, 502L, 503L));

        assertEquals(1, infos.size());
        assertEquals(501L, infos.get(0).getId());
    }
    @Test
    @DisplayName("batchStackInfo — 持券 ID 全不存在（仓储返回空）→ 空列表")
    void shouldReturnEmpty_whenNoUserCouponMatched() {
        when(userCouponRepository.findByIds(List.of(999L))).thenReturn(List.of());

        assertTrue(couponUsageManager.batchStackInfo(List.of(999L)).isEmpty());
        verifyNoInteractions(couponRepository);
    }

    // ==================== 夹具 ====================

    /** 按 id 取结果项（顺带断言 id 必须是 user_coupon.id —— 与入参同口径）。 */
    private static CouponStackInfoCO byId(List<CouponStackInfoCO> infos, Long expectedId) {
        List<CouponStackInfoCO> matched = infos.stream()
                .filter(info -> expectedId.equals(info.getId()))
                .toList();
        assertEquals(1, matched.size(), "结果中 id 应为 user_coupon.id=" + expectedId + "（与入参同口径）");
        return matched.get(0);
    }

    private static UserCoupon userCoupon(Long userCouponId, Long templateId) {
        return UserCoupon.builder()
                .id(userCouponId)
                .userId(100L)
                .couponId(templateId)
                .status(UserCouponStatusEnum.UNUSED.getCode())
                .build();
    }

    private static Coupon template(Long templateId, String couponNo, Integer stackRule,
                                   String stackGroup, Long sellerId) {
        return Coupon.builder()
                .id(templateId)
                .couponId(couponNo)
                .name("叠加测试券")
                .value(1000)
                .minAmount(1000)
                .status(1)
                .discountType(DiscountTypeEnum.FIXED.getCode())
                .couponCategory(CouponCategoryEnum.SHOP.getCode())
                .stackRule(stackRule)
                .stackGroup(stackGroup)
                .sellerId(sellerId)
                .build();
    }
}
