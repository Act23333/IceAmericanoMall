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
 * DirectOrderReq.remark（V5.7 买家留言）Bean Validation 单元测试 ——
 * 与 {@link CreateOrderReqTest} 同构（T6 直购与 T1 购物车下单契约一致）：
 * 可选字段（null 合法），长度上限 {@code Order.REMARK_MAX_LENGTH}（200）。
 *
 * <p>无 Controller 级 @Valid 测试先例，沿用 {@code AfterSaleApplyReqTest} 的
 * Req 对象级 + 手动 Validator 口径。
 */
@DisplayName("DirectOrderReq.remark 长度校验")
class DirectOrderReqTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private DirectOrderReq req(String remark) {
        DirectOrderReq r = new DirectOrderReq();
        r.setSkuId(1000L);
        r.setQuantity(1);
        r.setAddressId(1L);
        r.setRemark(remark);
        return r;
    }

    private boolean hasRemarkViolation(DirectOrderReq req) {
        Set<ConstraintViolation<DirectOrderReq>> violations = validator.validate(req);
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

    // ==================== V5.17 多币种 displayCurrency（可选，ISO 4217 alpha-3） ====================

    private DirectOrderReq reqWithCurrency(String displayCurrency) {
        DirectOrderReq r = new DirectOrderReq();
        r.setSkuId(1000L);
        r.setQuantity(1);
        r.setAddressId(1L);
        r.setDisplayCurrency(displayCurrency);
        return r;
    }

    private boolean hasDisplayCurrencyViolation(DirectOrderReq req) {
        Set<ConstraintViolation<DirectOrderReq>> violations = validator.validate(req);
        return violations.stream().anyMatch(v -> "displayCurrency".equals(v.getPropertyPath().toString()));
    }

    @Test
    @DisplayName("displayCurrency 为 null（可选不传=CNY 不快照）— 不触发任何约束")
    void shouldPass_whenDisplayCurrencyNull() {
        assertFalse(hasDisplayCurrencyViolation(reqWithCurrency(null)));
    }

    @Test
    @DisplayName("displayCurrency 长度 = DISPLAY_CURRENCY_MAX_LENGTH(3) — 边界内合法（如 USD）")
    void shouldPass_whenDisplayCurrencyAtMaxLength() {
        assertFalse(hasDisplayCurrencyViolation(reqWithCurrency("USD")));
    }

    @Test
    @DisplayName("displayCurrency 长度 = DISPLAY_CURRENCY_MAX_LENGTH + 1(4) — 触发 @Size 约束（→ 400 PARAM_ERROR）")
    void shouldReject_whenDisplayCurrencyExceedsMaxLength() {
        assertTrue(hasDisplayCurrencyViolation(reqWithCurrency("USDX")));
    }
}
