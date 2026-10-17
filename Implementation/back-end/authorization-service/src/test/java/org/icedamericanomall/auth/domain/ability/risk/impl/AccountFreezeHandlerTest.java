package org.icedamericanomall.auth.domain.ability.risk.impl;

import org.icedamericanomall.auth.domain.enums.RiskLevel;
import org.icedamericanomall.auth.domain.enums.RiskTag;
import org.icedamericanomall.auth.domain.model.RiskAssessment;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AccountFreezeHandler 单元测试 —— 命中条件 + <b>登录失败对外口径</b>（2026-10-18 批次 ④）。
 *
 * <p><b>锁死的缺陷（修复前会失败）</b>：极高风险阻断原本抛 {@code ACCOUNT_FROZEN(1017)} 且 HTTP
 * {@code 403}，与「密码错误」的 200、「账号不存在」的 404 互不相同 —— 只看状态码即可判断
 * 「这个账号被风控了（因此它存在）」。现必须与密码错完全同形：3007 / 401 / 中性文案。
 */
@DisplayName("AccountFreezeHandler — 风控阻断的登录失败口径")
class AccountFreezeHandlerTest {

    private final AccountFreezeHandler handler = new AccountFreezeHandler();

    @Test
    @DisplayName("supports — CRITICAL + 失败次数过高 + IP 黑名单 三者同时命中")
    void shouldSupport_whenCriticalAndFailCountHighAndBlacklistedIp() {
        assertTrue(handler.supports(assessment(RiskLevel.CRITICAL,
                Set.of(RiskTag.FAIL_COUNT_HIGH, RiskTag.BLACKLIST_IP))));
    }

    @Test
    @DisplayName("supports — 缺任一标签或等级不足则不由本处理器处置")
    void shouldNotSupport_whenAnyConditionMissing() {
        assertFalse(handler.supports(assessment(RiskLevel.CRITICAL, Set.of(RiskTag.FAIL_COUNT_HIGH))));
        assertFalse(handler.supports(assessment(RiskLevel.CRITICAL, Set.of(RiskTag.BLACKLIST_IP))));
        assertFalse(handler.supports(assessment(RiskLevel.HIGH,
                Set.of(RiskTag.FAIL_COUNT_HIGH, RiskTag.BLACKLIST_IP))));
    }

    @Test
    @DisplayName("handle — 阻断必须走登录失败统一出口（401 + 3007 + 中性文案），不得暴露 1017/403")
    void shouldThrowNeutralLoginFailure_whenHandlingRiskBlock() {
        BizException exception = assertThrows(BizException.class, () -> handler.handle(
                assessment(RiskLevel.CRITICAL, Set.of(RiskTag.FAIL_COUNT_HIGH, RiskTag.BLACKLIST_IP))));

        assertEquals(ErrorCode.PASSWORD_ERROR, exception.getErrorCode());
        assertEquals(3007, exception.getErrorCode().getCode());
        assertEquals(HttpStatus.UNAUTHORIZED, exception.getErrorCode().getHttpStatus());
        assertEquals("账号或密码不正确", exception.getDisplayMessage(),
                "文案不得暴露「被冻结」，否则仍可与密码错区分");
    }

    private RiskAssessment assessment(RiskLevel level, Set<RiskTag> tags) {
        return new RiskAssessment(level, tags, Map.of());
    }
}
