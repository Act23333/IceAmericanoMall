package org.icedamericanomall.marketing.app.coupon;

import org.icedamericanomall.marketing.adapter.assembler.MarketingConverterImpl;
import org.icedamericanomall.marketing.adapter.response.UserCouponVO;
import org.icedamericanomall.marketing.app.dto.CouponFace;
import org.icedamericanomall.marketing.app.port.CouponFaceQuery;
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 持券视图装配单元测试（契约 §4.10.1 {@code UserCouponVO} 券面字段投影）。
 *
 * <p>覆盖：①券名/券面值/最低消费被正确回填且整页只查一次（无 N+1）；
 * ②模板缺失 / 数据源异常 → 三字段置空但列表照常返回（fail-open）；
 * ③领取回执路径直接透出聚合字段（不再多打一次库）；④空列表不触发券面查询。
 */
@DisplayName("持券视图装配 — UserCouponVO 券面字段投影（契约 §4.10.1）")
@ExtendWith(MockitoExtension.class)
class UserCouponViewAssemblerTest {

    private static final Long USER_ID = 9L;
    private static final Long TEMPLATE_ID = 1L;

    @Mock
    private CouponFaceQuery couponFaceQuery;

    private UserCouponViewAssembler assembler;

    @BeforeEach
    void setUp() {
        assembler = new UserCouponViewAssembler(new MarketingConverterImpl(), couponFaceQuery);
    }

    @Test
    @DisplayName("assembleList — 券面在 → name/value/min_amount 被正确填充，本体字段不受影响")
    void shouldFillCouponFace_whenFaceAvailable() {
        when(couponFaceQuery.findByCouponIds(List.of(TEMPLATE_ID)))
                .thenReturn(Map.of(TEMPLATE_ID, new CouponFace(TEMPLATE_ID, "618满200减30", 3000, 20000)));

        List<UserCouponVO> views = assembler.assembleList(List.of(userCoupon(5L, TEMPLATE_ID)));

        UserCouponVO vo = views.get(0);
        assertEquals(5L, vo.getId());
        assertEquals(TEMPLATE_ID, vo.getCouponId());
        assertEquals(UserCouponStatusEnum.UNUSED.getCode(), vo.getStatus());
        assertEquals("618满200减30", vo.getName());
        assertEquals(3000, vo.getValue());
        assertEquals(20000, vo.getMinAmount());
    }

    @Test
    @DisplayName("assembleList — 多张持券只查一次券面，券模板 ID 去重")
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
    @DisplayName("assembleList — 模板已删除（券面缺失）→ 三字段为 null，持券行照常返回")
    void shouldLeaveFaceNull_whenTemplateMissing() {
        when(couponFaceQuery.findByCouponIds(List.of(TEMPLATE_ID))).thenReturn(Map.of());

        List<UserCouponVO> views = assembler.assembleList(List.of(userCoupon(5L, TEMPLATE_ID)));

        assertEquals(1, views.size(), "券面缺失不能让「我的券」列表缩行");
        assertNull(views.get(0).getName());
        assertNull(views.get(0).getValue());
        assertNull(views.get(0).getMinAmount());
        assertEquals(5L, views.get(0).getId());
    }

    @Test
    @DisplayName("assembleList — 券面数据源异常 → 三字段置空（fail-open），列表照常返回")
    void shouldDegradeGracefully_whenFaceQueryThrows() {
        when(couponFaceQuery.findByCouponIds(List.of(TEMPLATE_ID)))
                .thenThrow(new IllegalStateException("coupon 表不可用"));

        List<UserCouponVO> views = assembler.assembleList(List.of(userCoupon(5L, TEMPLATE_ID)));

        assertEquals(1, views.size());
        assertNull(views.get(0).getName());
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
    @DisplayName("assemble（领取回执 M1/M2）— 券面直接取自已加载的模板聚合，不再查库")
    void shouldFillFaceFromAggregate_withoutExtraQuery() {
        Coupon template = Coupon.builder().id(TEMPLATE_ID).name("新人满100减20")
                .value(2000).minAmount(10000).build();

        UserCouponVO vo = assembler.assemble(userCoupon(5L, TEMPLATE_ID), template);

        assertEquals("新人满100减20", vo.getName());
        assertEquals(2000, vo.getValue());
        assertEquals(10000, vo.getMinAmount());
        verify(couponFaceQuery, never()).findByCouponIds(any());
    }

    @Test
    @DisplayName("assemble — 模板已被删除（聚合为 null）→ 三字段 null，回执照常返回")
    void shouldLeaveFaceNull_whenAggregateMissing() {
        UserCouponVO vo = assembler.assemble(userCoupon(5L, TEMPLATE_ID), null);

        assertEquals(5L, vo.getId());
        assertNull(vo.getName());
        assertNull(vo.getValue());
        assertNull(vo.getMinAmount());
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
}
