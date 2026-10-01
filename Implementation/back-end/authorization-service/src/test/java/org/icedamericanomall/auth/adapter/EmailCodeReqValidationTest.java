package org.icedamericanomall.auth.adapter;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.icedamericanomall.auth.adapter.request.EmailCodeReq;
import org.icedamericanomall.auth.domain.enums.VerificationSceneEnum;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * EmailCodeReq 参数校验测试 —— 邮箱发码端点的入参约束（适配层快速失败）。
 *
 * <p>纯 Bean Validation 测试（不启动 Spring MVC）：非法入参必须在进入应用层之前被拒绝。
 */
@DisplayName("EmailCodeReq 参数校验测试")
class EmailCodeReqValidationTest {

    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        validatorFactory.close();
    }

    @Test
    @DisplayName("合法邮箱 → 校验通过")
    void shouldPass_whenEmailValid() {
        EmailCodeReq req = req("Buyer@Example.com");

        assertTrue(validator.validate(req).isEmpty());
    }

    @Test
    @DisplayName("邮箱为空 → 校验失败（提示不能为空）")
    void shouldFail_whenEmailBlank() {
        Set<ConstraintViolation<EmailCodeReq>> violations = validator.validate(req("  "));

        assertFalse(violations.isEmpty());
        assertTrue(messages(violations).contains("邮箱不能为空"));
    }

    @Test
    @DisplayName("邮箱格式非法 → 校验失败（提示格式不正确）")
    void shouldFail_whenEmailMalformed() {
        Set<ConstraintViolation<EmailCodeReq>> violations = validator.validate(req("not-an-email"));

        assertFalse(violations.isEmpty());
        assertTrue(messages(violations).contains("邮箱格式不正确"));
    }

    private String messages(Set<ConstraintViolation<EmailCodeReq>> violations) {
        return violations.stream()
                .map(ConstraintViolation::getMessage)
                .reduce("", (left, right) -> left + "|" + right);
    }

    @Test
    @DisplayName("场景可缺省（服务端缺省 LOGIN）")
    void shouldAllowAbsentScene() {
        EmailCodeReq req = req("buyer@example.com");
        req.setScene(null);

        assertTrue(validator.validate(req).isEmpty());
        assertEquals(VerificationSceneEnum.LOGIN, VerificationSceneEnum.fromCode(null));
    }

    @Test
    @DisplayName("场景取值非法 → 参数错误（1101），不是 500")
    void shouldRejectUnknownScene() {
        BizException exception = assertThrows(BizException.class,
                () -> VerificationSceneEnum.fromCode("FLASH_SALE"));

        assertEquals(ErrorCode.PARAM_ERROR, exception.getErrorCode());
    }

    private EmailCodeReq req(String email) {
        EmailCodeReq req = new EmailCodeReq();
        req.setEmail(email);
        req.setScene(VerificationSceneEnum.LOGIN.getCode());
        return req;
    }
}
