package org.icedamericanomall.trade.adapter.request;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.icedamericanomall.trade.domain.enums.AfterSaleTypeEnum;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AfterSaleApplyReq.type 枚举校验单元测试 —— 仅 1(退货退款)/2(仅退款) 合法，其余在适配层即被拒。
 */
@DisplayName("AfterSaleApplyReq.type 枚举校验")
class AfterSaleApplyReqTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private AfterSaleApplyReq req(Integer type) {
        AfterSaleApplyReq r = new AfterSaleApplyReq();
        r.setOrderNo("ORD-1");
        r.setType(type);
        r.setRefundAmount(100);
        return r;
    }

    private boolean hasTypeViolation(AfterSaleApplyReq req) {
        Set<ConstraintViolation<AfterSaleApplyReq>> violations = validator.validate(req);
        return violations.stream().anyMatch(v -> "type".equals(v.getPropertyPath().toString()));
    }

    @Test
    @DisplayName("type = 1(退货退款) / 2(仅退款) — 通过校验")
    void shouldPass_whenTypeIsLegal() {
        assertFalse(hasTypeViolation(req(1)), "1=退货退款 应合法");
        assertFalse(hasTypeViolation(req(2)), "2=仅退款 应合法");
    }

    @Test
    @DisplayName("type = 0 / 3 / 9 — 非法取值触发 type 约束（→ 400 PARAM_ERROR）")
    void shouldReject_whenTypeOutOfRange() {
        assertTrue(hasTypeViolation(req(0)), "0 非法");
        assertTrue(hasTypeViolation(req(3)), "3 非法");
        assertTrue(hasTypeViolation(req(9)), "9 非法");
        assertTrue(hasTypeViolation(req(-1)), "-1 非法");
    }

    @Test
    @DisplayName("type = null — 仍由 @NotNull 拦截")
    void shouldReject_whenTypeNull() {
        assertTrue(hasTypeViolation(req(null)));
    }

    @Test
    @DisplayName("枚举与校验区间一致 — MIN_CODE/MAX_CODE 必须覆盖全部枚举码（防漂移）")
    void shouldKeepRangeAlignedWithEnum() {
        assertEquals(AfterSaleTypeEnum.MIN_CODE, AfterSaleTypeEnum.RETURN_REFUND.getCode());
        assertEquals(AfterSaleTypeEnum.MAX_CODE, AfterSaleTypeEnum.REFUND_ONLY.getCode());
        for (AfterSaleTypeEnum e : AfterSaleTypeEnum.values()) {
            assertNotNull(AfterSaleTypeEnum.of(e.getCode()), "枚举码必须可被 of() 解析: " + e);
        }
        assertNull(AfterSaleTypeEnum.of(3), "3 不属于任何合法类型");
        assertNull(AfterSaleTypeEnum.of(null));
    }
}
