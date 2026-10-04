package org.icedamericanomall.trade.adapter.request;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.icedamericanomall.trade.domain.model.order.Order;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CreateOrderReq.remark（V5.7 买家留言）Bean Validation 单元测试 ——
 * 可选字段（null 合法），长度上限 {@code Order.REMARK_MAX_LENGTH}（200），
 * 超长在适配层即被 {@code @Size} 拦截（→ 400 PARAM_ERROR），不进入下单管道。
 *
 * <p>无 Controller 级 @Valid 测试先例，沿用 {@code AfterSaleApplyReqTest} 的
 * Req 对象级 + 手动 Validator 口径。
 */
@DisplayName("CreateOrderReq.remark 长度校验")
class CreateOrderReqTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private CreateOrderReq req(String remark) {
        CreateOrderReq r = new CreateOrderReq();
        r.setAddressId(1L);
        r.setRemark(remark);
        return r;
    }

    private boolean hasRemarkViolation(CreateOrderReq req) {
        Set<ConstraintViolation<CreateOrderReq>> violations = validator.validate(req);
        return violations.stream().anyMatch(v -> "remark".equals(v.getPropertyPath().toString()));
    }

    @Test
    @DisplayName("remark 为 null（可选不传）— 不触发任何约束")
    void shouldPass_whenRemarkNull() {
        assertFalse(hasRemarkViolation(req(null)));
    }

    @Test
    @DisplayName("remark 长度 = REMARK_MAX_LENGTH(200) — 边界内合法")
    void shouldPass_whenRemarkAtMaxLength() {
        assertFalse(hasRemarkViolation(req("好".repeat(Order.REMARK_MAX_LENGTH))));
    }

    @Test
    @DisplayName("remark 长度 = REMARK_MAX_LENGTH + 1(201) — 触发 @Size 约束（→ 400 PARAM_ERROR）")
    void shouldReject_whenRemarkExceedsMaxLength() {
        assertTrue(hasRemarkViolation(req("好".repeat(Order.REMARK_MAX_LENGTH + 1))));
    }

    @Test
    @DisplayName("常量防漂移 — REMARK_MAX_LENGTH 必须为 200（与 05 契约 §4.6.1 一致）")
    void shouldKeepConstantAlignedWithContract() {
        assertEquals(200, Order.REMARK_MAX_LENGTH);
    }
}
