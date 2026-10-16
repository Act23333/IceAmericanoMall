package org.icedamericanomall.marketing.app.coupon;

import org.icedamericanomall.marketing.adapter.assembler.MarketingConverterImpl;
import org.icedamericanomall.marketing.adapter.response.UserCouponVO;
import org.icedamericanomall.marketing.app.dto.CouponFace;
import org.icedamericanomall.marketing.app.port.CouponFaceQuery;
import org.icedamericanomall.marketing.domain.enums.DiscountTypeEnum;
import org.icedamericanomall.marketing.domain.enums.UserCouponStatusEnum;
import org.icedamericanomall.marketing.domain.model.coupon.Coupon;
import org.icedamericanomall.marketing.domain.model.coupon.UserCoupon;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 持券视图装配单元测试（契约 §4.10.1 {@code UserCouponVO} 业务券号 + 券面字段投影）。
 *
 * <p>覆盖：①业务券号（32-hex）与券名/券面值/最低消费/优惠类型被正确回填，且整页只查一次（无 N+1）；
 * ②{@code discount_type} 三型（1=FIXED / 2=PERCENTAGE / 3=CASH_COUPON）分别断言<b>具体值</b>
 * （ADR-058：禁止 {@code assertNotNull} 了事）；
 * ③模板缺失 / 数据源异常 → 字段置空（<b>折扣类型也不回落 FIXED</b>）但列表照常返回（fail-open）；
 * ④领取回执路径（M1/M2）直接透出已加载模板聚合的业务券号与券面字段（不再多打一次库）；
 * ⑤技术主键 {@code user_coupon.coupon_id} 不得泄漏为 {@code couponId}；⑥空列表不触发券面查询。
 */
@DisplayName("持券视图装配 — UserCouponVO 业务券号/券面字段投影（契约 §4.10.1）")
@ExtendWith(MockitoExtension.class)
class UserCouponViewAssemblerTest {

    private static final Long USER_ID = 9L;
    private static final Long TEMPLATE_ID = 1L;

    /** 业务券号（券模板 {@code coupon.coupon_id} 的 32-hex 对外形态）。 */
    private static final String COUPON_NO = "0f1e2d3c4b5a69788796a5b4c3d2e1f0";

    /** 折扣券业务券号（用于区分「同一 VO 多行不同类型」场景）。 */
    private static final String COUPON_NO_PERCENT = "2b3c4d5e6f708192a3b4c5d6e7f8091a";

    @Mock
    private CouponFaceQuery couponFaceQuery;

    private UserCouponViewAssembler assembler;

    @BeforeEach
    void setUp() {
        assembler = new UserCouponViewAssembler(new MarketingConverterImpl(), couponFaceQuery);
    }

    @Test
    @DisplayName("assembleList — 券模板在 → coupon_id/name/value/min_amount/discount_type 全部填充")
    void shouldFillCouponFace_whenFaceAvailable() {
        when(couponFaceQuery.findByCouponIds(List.of(TEMPLATE_ID)))
                .thenReturn(Map.of(TEMPLATE_ID, new CouponFace(
                        TEMPLATE_ID, COUPON_NO, "618满200减30", 3000, 20000,
                        DiscountTypeEnum.FIXED.getCode(), null, null)));

        List<UserCouponVO> views = assembler.assembleList(List.of(userCoupon(5L, TEMPLATE_ID)));

        UserCouponVO vo = views.get(0);
        assertEquals(5L, vo.getId());
        assertEquals(COUPON_NO, vo.getCouponId(), "coupon_id 取券模板业务券号");
        assertEquals(UserCouponStatusEnum.UNUSED.getCode(), vo.getStatus());
        assertEquals("618满200减30", vo.getName());
        assertEquals(3000, vo.getValue());
        assertEquals(20000, vo.getMinAmount());
        assertEquals(1, vo.getDiscountType(), "discount_type=1（FIXED 满减）必须原样投影，value 为分");
    }

    @Test
    @DisplayName("assembleList — discount_type=1（满减）：类型码 1，value 为分")
    void shouldExposeFixedDiscountType() {
        when(couponFaceQuery.findByCouponIds(List.of(TEMPLATE_ID)))
                .thenReturn(Map.of(TEMPLATE_ID, new CouponFace(
                        TEMPLATE_ID, COUPON_NO, "满200减30", 3000, 20000,
                        DiscountTypeEnum.FIXED.getCode(), null, null)));

        UserCouponVO vo = assembler.assembleList(List.of(userCoupon(5L, TEMPLATE_ID))).get(0);

        assertEquals(DiscountTypeEnum.FIXED.getCode(), vo.getDiscountType());
        assertEquals(1, vo.getDiscountType());
        assertEquals(3000, vo.getValue(), "满减券 value 是分额");
    }

    @Test
    @DisplayName("assembleList — discount_type=2（折扣）：类型码 2，value 为百分比（85 = 8.5 折）")
    void shouldExposePercentageDiscountType() {
        when(couponFaceQuery.findByCouponIds(List.of(TEMPLATE_ID)))
                .thenReturn(Map.of(TEMPLATE_ID, new CouponFace(
                        TEMPLATE_ID, COUPON_NO_PERCENT, "全场8.5折", 85, 0,
                        DiscountTypeEnum.PERCENTAGE.getCode(), null, null)));

        UserCouponVO vo = assembler.assembleList(List.of(userCoupon(5L, TEMPLATE_ID))).get(0);

        assertEquals(DiscountTypeEnum.PERCENTAGE.getCode(), vo.getDiscountType());
        assertEquals(2, vo.getDiscountType(), "折扣券必须投影 2，前端据此按折渲染而非 -¥85");
        assertEquals(85, vo.getValue(), "折扣券 value 是百分比（85=8.5 折），不是分");
    }

    @Test
    @DisplayName("assembleList — discount_type=3（代金券）：类型码 3，value 为分")
    void shouldExposeCashCouponDiscountType() {
        when(couponFaceQuery.findByCouponIds(List.of(TEMPLATE_ID)))
                .thenReturn(Map.of(TEMPLATE_ID, new CouponFace(
                        TEMPLATE_ID, COUPON_NO, "50元代金券", 5000, 0,
                        DiscountTypeEnum.CASH_COUPON.getCode(), null, null)));

        UserCouponVO vo = assembler.assembleList(List.of(userCoupon(5L, TEMPLATE_ID))).get(0);

        assertEquals(DiscountTypeEnum.CASH_COUPON.getCode(), vo.getDiscountType());
        assertEquals(3, vo.getDiscountType());
        assertEquals(5000, vo.getValue());
    }

    @Test
    @DisplayName("assembleList — 一页多张不同类型的券：逐行投影各自的 discount_type（不串行）")
    void shouldExposePerRowDiscountType_whenMixedTypes() {
        when(couponFaceQuery.findByCouponIds(any())).thenReturn(Map.of(
                TEMPLATE_ID, new CouponFace(TEMPLATE_ID, COUPON_NO, "满200减30", 3000, 20000,
                        DiscountTypeEnum.FIXED.getCode(), null, null),
                2L, new CouponFace(2L, COUPON_NO_PERCENT, "全场8.5折", 85, 0,
                        DiscountTypeEnum.PERCENTAGE.getCode(), null, null)));

        List<UserCouponVO> views = assembler.assembleList(List.of(
                userCoupon(5L, TEMPLATE_ID), userCoupon(6L, 2L)));

        assertEquals(2, views.size());
        assertEquals(1, views.get(0).getDiscountType(), "第 1 行（模板 1）→ FIXED");
        assertEquals(2, views.get(1).getDiscountType(), "第 2 行（模板 2）→ PERCENTAGE");
        assertEquals(COUPON_NO, views.get(0).getCouponId());
        assertEquals(COUPON_NO_PERCENT, views.get(1).getCouponId());
    }

    @Test
    @DisplayName("assembleList — coupon_id 形态为 32 位小写 hex，且不等于技术主键")
    void shouldExposeCouponNoAs32Hex_notTechnicalKey() {
        when(couponFaceQuery.findByCouponIds(List.of(TEMPLATE_ID)))
                .thenReturn(Map.of(TEMPLATE_ID, new CouponFace(
                        TEMPLATE_ID, COUPON_NO, "券", 100, 0, DiscountTypeEnum.FIXED.getCode(), null, null)));

        UserCouponVO vo = assembler.assembleList(List.of(userCoupon(5L, TEMPLATE_ID))).get(0);

        assertTrue(vo.getCouponId().matches("[0-9a-f]{32}"),
                "业务券号必须为 32 位小写 hex（非法/越界形态不得出现），实际: " + vo.getCouponId());
        assertNotEquals(String.valueOf(TEMPLATE_ID), vo.getCouponId(),
                "技术外键 user_coupon.coupon_id 不得泄漏为 VO.couponId");
        assertNotEquals(COUPON_NO.toUpperCase(), vo.getCouponId(), "统一小写口径");
    }

    @Test
    @DisplayName("assembleList — 多张持券只查一次券面，券模板 ID 去重（含折扣类型同批读出）")
    void shouldQueryFaceOnce_whenMultipleRows() {
        when(couponFaceQuery.findByCouponIds(any())).thenReturn(Map.of());

        assembler.assembleList(List.of(
                userCoupon(5L, TEMPLATE_ID),
                userCoupon(6L, TEMPLATE_ID),
                userCoupon(7L, 2L)));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<Long>> captor = ArgumentCaptor.forClass(Collection.class);
        verify(couponFaceQuery).findByCouponIds(captor.capture());
        assertEquals(List.of(TEMPLATE_ID, 2L), List.copyOf(captor.getValue()), "同一模板只查一次");
    }

    @Test
    @DisplayName("assembleList — 模板已删除（券面缺失）→ coupon_id 与券面字段（含 discount_type）为 null")
    void shouldLeaveFaceNull_whenTemplateMissing() {
        when(couponFaceQuery.findByCouponIds(List.of(TEMPLATE_ID))).thenReturn(Map.of());

        List<UserCouponVO> views = assembler.assembleList(List.of(userCoupon(5L, TEMPLATE_ID)));

        assertEquals(1, views.size(), "券面缺失不能让「我的券」列表缩行");
        assertNull(views.get(0).getCouponId(), "券模板缺失 → 业务券号无来源，置 null（不回落技术主键）");
        assertNull(views.get(0).getName());
        assertNull(views.get(0).getValue());
        assertNull(views.get(0).getMinAmount());
        assertNull(views.get(0).getDiscountType(),
                "券模板缺失 → discount_type 无来源，置 null（禁止回落 0 / 禁止回落 FIXED(1)）");
        assertEquals(5L, views.get(0).getId());
    }

    @Test
    @DisplayName("assembleList — 券面数据源异常 → coupon_id 与券面字段（含 discount_type）置空（fail-open）")
    void shouldDegradeGracefully_whenFaceQueryThrows() {
        when(couponFaceQuery.findByCouponIds(List.of(TEMPLATE_ID)))
                .thenThrow(new IllegalStateException("coupon 表不可用"));

        List<UserCouponVO> views = assembler.assembleList(List.of(userCoupon(5L, TEMPLATE_ID)));

        assertEquals(1, views.size());
        assertNull(views.get(0).getCouponId());
        assertNull(views.get(0).getName());
        assertNull(views.get(0).getDiscountType(),
                "读模型降级 → discount_type 必须为 null（前端据此不渲染面额，而非错按满减渲染）");
        assertEquals(5L, views.get(0).getId());
    }

    @Test
    @DisplayName("assembleList — 空列表不触发券面查询")
    void shouldSkipFaceQuery_whenListEmpty() {
        assertTrue(assembler.assembleList(List.of()).isEmpty());
        assertTrue(assembler.assembleList(null).isEmpty());
        verify(couponFaceQuery, never()).findByCouponIds(any());
    }

    @Test
    @DisplayName("assemble（领取回执 M1/M2）— 券号与券面（含 discount_type）取自已加载的聚合，不再查库")
    void shouldFillFaceFromAggregate_withoutExtraQuery() {
        Coupon template = Coupon.builder().id(TEMPLATE_ID).couponId(COUPON_NO).name("新人满100减20")
                .value(2000).minAmount(10000)
                .discountType(DiscountTypeEnum.FIXED.getCode())
                .build();

        UserCouponVO vo = assembler.assemble(userCoupon(5L, TEMPLATE_ID), template);

        assertEquals(COUPON_NO, vo.getCouponId(), "回执 coupon_id 取聚合业务券号（toDomain 已解 hex）");
        assertEquals("新人满100减20", vo.getName());
        assertEquals(2000, vo.getValue());
        assertEquals(10000, vo.getMinAmount());
        assertEquals(1, vo.getDiscountType(), "回执 discount_type 取聚合（1=FIXED）");
        verify(couponFaceQuery, never()).findByCouponIds(any());
    }

    @Test
    @DisplayName("assemble（领取回执 M1/M2）— 折扣券回执：discount_type=2，前端不再按 -¥85 错渲染")
    void shouldFillPercentageDiscountTypeFromAggregate() {
        Coupon template = Coupon.builder().id(TEMPLATE_ID).couponId(COUPON_NO_PERCENT).name("全场8.5折")
                .value(85).minAmount(0)
                .discountType(DiscountTypeEnum.PERCENTAGE.getCode())
                .build();

        UserCouponVO vo = assembler.assemble(userCoupon(5L, TEMPLATE_ID), template);

        assertEquals(2, vo.getDiscountType());
        assertEquals(85, vo.getValue());
        assertEquals(COUPON_NO_PERCENT, vo.getCouponId());
    }

    @Test
    @DisplayName("assemble — 模板已被删除（聚合为 null）→ coupon_id 与券面字段（含 discount_type）null")
    void shouldLeaveFaceNull_whenAggregateMissing() {
        UserCouponVO vo = assembler.assemble(userCoupon(5L, TEMPLATE_ID), null);

        assertEquals(5L, vo.getId());
        assertNull(vo.getCouponId());
        assertNull(vo.getName());
        assertNull(vo.getValue());
        assertNull(vo.getMinAmount());
        assertNull(vo.getDiscountType(), "回执路径同样 fail-open：不回落 FIXED(1)");
    }

    @Test
    @DisplayName("assemble — 模板存在但 discount_type 缺失 → null（不回落 FIXED），其余字段照常")
    void shouldKeepNullDiscountType_whenAggregateTypeMissing() {
        Coupon template = Coupon.builder().id(TEMPLATE_ID).couponId(COUPON_NO).name("历史券")
                .value(1000).minAmount(0)
                .build();

        UserCouponVO vo = assembler.assemble(userCoupon(5L, TEMPLATE_ID), template);

        assertNull(vo.getDiscountType(), "列缺失不得回落 1（=FIXED），否则前端会把未知类型当满减渲染");
        assertEquals("历史券", vo.getName());
        assertEquals(1000, vo.getValue());
    }

    private UserCoupon userCoupon(Long id, Long templateId) {
        return UserCoupon.builder()
                .id(id)
                .userId(USER_ID)
                .couponId(templateId)
                .status(UserCouponStatusEnum.UNUSED.getCode())
                .createTime(LocalDateTime.of(2026, 6, 10, 10, 0))
                .build();
    }

    @Test
    @DisplayName("assembleList — 适用范围投影：scope_type 原样 + scope_values 由 DB JSON 归一为 CSV（B1 遗留补齐）")
    void shouldProjectScopeAsCsv_whenScopeConfigured() {
        when(couponFaceQuery.findByCouponIds(List.of(TEMPLATE_ID)))
                .thenReturn(Map.of(TEMPLATE_ID, new CouponFace(
                        TEMPLATE_ID, COUPON_NO, "限定单品券", 3000, 20000,
                        DiscountTypeEnum.FIXED.getCode(), 3,
                        "[\"0f1e2d3c4b5a69788796a5b4c3d2e1f0\",\"112233445566778899aabbccddeeff00\"]")));

        UserCouponVO vo = assembler.assembleList(List.of(userCoupon(5L, TEMPLATE_ID))).get(0);

        assertEquals(3, vo.getScopeType(), "scope_type 原样投影（3=PRODUCT 限定单品）");
        assertEquals("0f1e2d3c4b5a69788796a5b4c3d2e1f0,112233445566778899aabbccddeeff00",
                vo.getScopeValues(), "对外必须 CSV 串（非 DB JSON 原样），且取值是 SPU 业务键（ADR-061）");
    }

    @Test
    @DisplayName("assembleList — scope_type=1(ALL)：scope_values 对外恒 null（合法形态）")
    void shouldReturnNullScopeValues_whenScopeAll() {
        when(couponFaceQuery.findByCouponIds(List.of(TEMPLATE_ID)))
                .thenReturn(Map.of(TEMPLATE_ID, new CouponFace(
                        TEMPLATE_ID, COUPON_NO, "全场券", 3000, 20000,
                        DiscountTypeEnum.FIXED.getCode(), 1, "[\"1001\"]")));

        UserCouponVO vo = assembler.assembleList(List.of(userCoupon(5L, TEMPLATE_ID))).get(0);

        assertEquals(1, vo.getScopeType());
        assertNull(vo.getScopeValues(), "ALL 时对外恒 null（前端按「不限」消费）");
    }

    @Test
    @DisplayName("assembleList — 券面缺失（模板已删/降级）：scope_type 与 scope_values 同为 null（不回落 ALL）")
    void shouldLeaveScopeNull_whenFaceMissing() {
        when(couponFaceQuery.findByCouponIds(List.of(TEMPLATE_ID))).thenReturn(Map.of());

        UserCouponVO vo = assembler.assembleList(List.of(userCoupon(5L, TEMPLATE_ID))).get(0);

        assertNull(vo.getScopeType(), "不回落为 1：前端必须把 null 按 ALL 消费");
        assertNull(vo.getScopeValues());
    }
}
