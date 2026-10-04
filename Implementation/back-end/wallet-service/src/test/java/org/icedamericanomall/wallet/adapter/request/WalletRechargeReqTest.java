package org.icedamericanomall.wallet.adapter.request;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 充值入参校验测试 —— 校验 {@link WalletRechargeReq} 上的
 * {@code @NotNull} / {@code @Min(1)} 约束（无需启动 Spring 容器）。
 *
 * <p>迁移说明（V5.4 入参对象化）：原测试直接对 {@code WalletController#recharge(Integer)}
 * 的方法参数做可执行校验；参数收敛进 {@link WalletRechargeReq} 后改为对象字段校验，
 * 约束语义（null / 0 拒绝，≥1 通过）与错误码出口（PARAM_ERROR 400）保持不变。
 */
@DisplayName("充值入参 — WalletRechargeReq 字段校验")
class WalletRechargeReqTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    @DisplayName("amount=null → 违反 @NotNull")
    void shouldViolate_whenAmountNull() {
        WalletRechargeReq req = new WalletRechargeReq();
        req.setAmount(null);

        Set<ConstraintViolation<WalletRechargeReq>> violations = validator.validate(req);

        assertEquals(1, violations.size());
        assertEquals("充值金额不能为空", violations.iterator().next().getMessage());
    }

    @Test
    @DisplayName("amount=0 → 违反 @Min(1)")
    void shouldViolate_whenAmountNotPositive() {
        WalletRechargeReq req = new WalletRechargeReq();
        req.setAmount(0);

        Set<ConstraintViolation<WalletRechargeReq>> violations = validator.validate(req);

        assertFalse(violations.isEmpty());
        assertEquals("充值金额必须大于0", violations.iterator().next().getMessage());
    }

    @Test
    @DisplayName("amount=1 → 无约束违反")
    void shouldPass_whenAmountPositive() {
        WalletRechargeReq req = new WalletRechargeReq();
        req.setAmount(1);

        assertTrue(validator.validate(req).isEmpty());
    }
}
