package org.icedamericanomall.auth.domain.ability.risk.impl;

import org.icedamericanomall.auth.domain.ability.risk.impl.CaptchaHandler;
import org.icedamericanomall.auth.domain.enums.RiskLevel;
import org.icedamericanomall.auth.domain.enums.RiskTag;
import org.icedamericanomall.auth.domain.model.RiskAssessment;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CaptchaHandler 单元测试 —— 风控要求人机验证时的阻断协议与"未配置不拦截"行为（R3）。
 */
@DisplayName("CaptchaHandler 单元测试")
class CaptchaHandlerTest {

    private static final Map<String, String> MAPPING = Map.of("MEDIUM", "GEETEST", "HIGH", "GEETEST");
    private static final String CAPTCHA_ID = "captcha-id-123";

    @Test
    @DisplayName("中风险 + 已启用 → 抛 CAPTCHA_REQUIRED，payload 携带前端重试所需信息")
    void shouldThrowWithRetryPayload_whenMediumRiskAndEnabled() {
        BizException exception = assertThrows(BizException.class,
                () -> handler(true, CAPTCHA_ID).handle(mediumRisk()));

        assertEquals(ErrorCode.CAPTCHA_REQUIRED, exception.getErrorCode());
        Map<String, Object> payload = payloadOf(exception);
        assertEquals(Boolean.TRUE, payload.get("captchaRequired"));
        assertEquals("GEETEST", payload.get("provider"));
        assertEquals(CAPTCHA_ID, payload.get("captchaId"));
    }

    @Test
    @DisplayName("中风险 + 未配置厂商公开标识 → payload 不含 captchaId（前端用内置默认值）")
    void shouldOmitCaptchaId_whenNotConfigured() {
        BizException exception = assertThrows(BizException.class,
                () -> handler(true, "").handle(mediumRisk()));

        Map<String, Object> payload = payloadOf(exception);
        assertFalse(payload.containsKey("captchaId"));
        assertEquals("GEETEST", payload.get("provider"));
    }

    @Test
    @DisplayName("未配置人机验证（enabled=false）→ 不阻断，只放行（避免拦死全部登录）")
    void shouldNotBlock_whenCaptchaDisabled() {
        CaptchaHandler handler = handler(false, CAPTCHA_ID);

        assertDoesNotThrow(() -> handler.handle(mediumRisk()));
    }

    @Test
    @DisplayName("已通过人机验证 → 不再要求验证（R3 闭环：前端带凭证重试可成功）")
    void shouldNotRequire_whenCaptchaAlreadyVerified() {
        CaptchaHandler handler = handler(true, CAPTCHA_ID);

        assertDoesNotThrow(() -> handler.handle(mediumRisk().withCaptchaVerified(true)));
    }

    @Test
    @DisplayName("supports — 中高风险、新设备/失败过多标签命中；纯低风险不命中")
    void shouldSupportRiskSignals() {
        CaptchaHandler handler = handler(true, CAPTCHA_ID);

        assertTrue(handler.supports(mediumRisk()));
        assertTrue(handler.supports(new RiskAssessment(RiskLevel.HIGH, Set.of(), Map.of())));
        assertTrue(handler.supports(new RiskAssessment(RiskLevel.LOW, Set.of(RiskTag.NEW_DEVICE), Map.of())));
        assertFalse(handler.supports(new RiskAssessment(RiskLevel.LOW, Set.of(), Map.of())));
    }

    private CaptchaHandler handler(boolean enabled, String captchaId) {
        return new CaptchaHandler(MAPPING, "GEETEST", captchaId, enabled);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> payloadOf(BizException exception) {
        return (Map<String, Object>) exception.getData();
    }

    private RiskAssessment mediumRisk() {
        return new RiskAssessment(RiskLevel.MEDIUM, Set.of(), Map.of("totalScore", 30));
    }
}
