package org.icedamericanomall.wallet.adapter.request;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AD67/AD68 入参校验测试——字段级约束（{@code @NotNull}/{@code @Positive}/{@code @Size}/
 * {@code @Min}/{@code @Max}）在适配层把守，校验失败由全局异常处理器映射 PARAM_ERROR(400)。
 */
@DisplayName("币种管理端入参校验 — RateUpdateReq / StatusUpdateReq")
class CurrencyReqValidationTest {

    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        validatorFactory.close();
    }

    @Test
    @DisplayName("RateUpdateReq — rateScaled 缺失 → 校验不通过")
    void shouldRejectRate_whenRateScaledMissing() {
        RateUpdateReq req = new RateUpdateReq();

        Set<?> violations = validator.validate(req);

        assertEquals(1, violations.size());
    }

    @Test
    @DisplayName("RateUpdateReq — rateScaled ≤ 0 → 校验不通过（定点整数恒正）")
    void shouldRejectRate_whenRateScaledNonPositive() {
        RateUpdateReq zero = new RateUpdateReq();
        zero.setRateScaled(0L);

        Set<?> violations = validator.validate(zero);

        assertEquals(1, violations.size());
    }

    @Test
    @DisplayName("RateUpdateReq — 合法定点整数 + 短备注 → 通过")
    void shouldPassRate_whenValidScaledValue() {
        RateUpdateReq req = new RateUpdateReq();
        req.setRateScaled(140300L);
        req.setRemark("人工修正");

        assertTrue(validator.validate(req).isEmpty());
    }

    @Test
    @DisplayName("RateUpdateReq — 备注超 255 字符 → 校验不通过")
    void shouldRejectRate_whenRemarkTooLong() {
        RateUpdateReq req = new RateUpdateReq();
        req.setRateScaled(140300L);
        req.setRemark("长".repeat(256));

        Set<?> violations = validator.validate(req);

        assertEquals(1, violations.size());
    }

    @Test
    @DisplayName("StatusUpdateReq — status 缺失 → 校验不通过")
    void shouldRejectStatus_whenMissing() {
        assertTrue(!validator.validate(new StatusUpdateReq()).isEmpty());
    }

    @Test
    @DisplayName("StatusUpdateReq — status 越界（2）→ 校验不通过")
    void shouldRejectStatus_whenOutOfRange() {
        StatusUpdateReq req = new StatusUpdateReq();
        req.setStatus(2);

        Set<?> violations = validator.validate(req);

        assertEquals(1, violations.size());
    }

    @Test
    @DisplayName("StatusUpdateReq — status = 0/1 → 通过")
    void shouldPassStatus_whenZeroOrOne() {
        StatusUpdateReq disable = new StatusUpdateReq();
        disable.setStatus(0);
        StatusUpdateReq enable = new StatusUpdateReq();
        enable.setStatus(1);

        assertTrue(validator.validate(disable).isEmpty());
        assertTrue(validator.validate(enable).isEmpty());
    }
}
