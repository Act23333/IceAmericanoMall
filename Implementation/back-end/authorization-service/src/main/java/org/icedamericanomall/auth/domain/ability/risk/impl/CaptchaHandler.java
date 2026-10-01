package org.icedamericanomall.auth.domain.ability.risk.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.auth.domain.ability.risk.RiskHandler;
import org.icedamericanomall.auth.domain.enums.RiskLevel;
import org.icedamericanomall.auth.domain.enums.RiskTag;
import org.icedamericanomall.auth.domain.model.RiskAssessment;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;

import java.util.HashMap;
import java.util.Map;

/**
 * 中风险处置：要求人机验证。
 *
 * <p>"风险等级 → 验证厂商"的映射与厂商公开标识（captchaId）由基础设施层配置注入（本类为纯 POJO）。
 *
 * <p><b>未配置时的行为（R3）</b>：未接入人机验证（{@code enabled=false}，dev 默认）时
 * 不阻断登录，只打 WARN —— 否则"每次登录都要求验证、而前端又拿不到可用的验证组件"
 * 会把<b>全部</b>登录拦死。生产环境必须显式开启（{@code captcha.enabled=true}），
 * 由配置而非代码决定是否强制人机验证。
 *
 * <p><b>异常即协议</b>：阻断时抛 {@link BizException}，payload 通过
 * {@code Result.error(CommonException)} 的 data 通道回给前端，前端据此重试：
 * <pre>
 * {
 *   "code": 1016, "msg": "需要人机验证", "data": {
 *     "captchaRequired": true, "provider": "GEETEST", "captchaId": "xxx"
 *   }
 * }
 * </pre>
 */
@Slf4j
@RequiredArgsConstructor
public class CaptchaHandler implements RiskHandler {

    /** payload key：需要人机验证的显式标记（前端据此重试，不依赖 HTTP 状态码猜测） */
    private static final String PAYLOAD_REQUIRED = "captchaRequired";

    /** payload key：验证厂商标识 */
    private static final String PAYLOAD_PROVIDER = "provider";

    /** payload key：厂商公开标识（前端初始化验证组件的参数，非密钥） */
    private static final String PAYLOAD_CAPTCHA_ID = "captchaId";

    private final Map<String, String> providerMapping;
    private final String defaultProvider;
    private final String captchaId;
    private final boolean enabled;

    @Override
    public boolean supports(RiskAssessment assessment) {
        // 1. 等级判断
        if (assessment.isAtLeast(RiskLevel.MEDIUM)) {
            return true;
        }
        // 2. 标签判断（精细）
        return assessment.hasTag(RiskTag.NEW_DEVICE)
                || assessment.hasTag(RiskTag.FAIL_COUNT_HIGH)
                || assessment.hasTag(RiskTag.FIRST_LOGIN);
    }

    @Override
    public void handle(RiskAssessment assessment) {
        // 本次请求已通过人机验证（前端带凭证重试）→ 不再重复要求，否则重试永远无法成功
        if (assessment.captchaVerified()) {
            log.info("本次登录已通过人机验证，不再要求验证: level={}", assessment.level());
            return;
        }
        if (!enabled) {
            log.warn("人机验证未配置（captcha.enabled=false），本次跳过强制验证: level={}, tags={}",
                    assessment.level(), assessment.tags());
            return;
        }
        throw new BizException(ErrorCode.CAPTCHA_REQUIRED, "需要人机验证",
                buildPayload(resolveProvider(assessment)));
    }

    /** 风险等级 → 验证厂商（未命中映射时用默认厂商） */
    private String resolveProvider(RiskAssessment assessment) {
        return providerMapping.getOrDefault(assessment.level().name(), defaultProvider);
    }

    /** 前端重试所需信息（captchaId 未配置时不返回该字段，前端使用自身内置的公开标识） */
    private Map<String, Object> buildPayload(String provider) {
        Map<String, Object> payload = new HashMap<>();
        payload.put(PAYLOAD_REQUIRED, true);
        payload.put(PAYLOAD_PROVIDER, provider);
        if (captchaId != null && !captchaId.isBlank()) {
            payload.put(PAYLOAD_CAPTCHA_ID, captchaId);
        }
        return payload;
    }

    @Override
    public int getOrder() {
        return 3;
    }
}
