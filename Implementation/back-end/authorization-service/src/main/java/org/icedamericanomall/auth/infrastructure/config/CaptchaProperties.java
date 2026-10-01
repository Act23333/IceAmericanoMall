package org.icedamericanomall.auth.infrastructure.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashMap;
import java.util.Map;

/**
 * 人机验证配置 —— 开关、风险等级 → 验证厂商映射、前端初始化所需的公开标识。
 *
 * <p>厂商密钥（极验 {@code key}）与校验地址<b>不在本服务配置</b>：
 * 人机验证的二次校验由 verification-service 完成（第三方凭据只在那边、只在 ia-integration 里使用），
 * 本服务只保留"要不要验证、用哪家、前端拿什么参数初始化"这三件事。
 */
@Data
@ConfigurationProperties(prefix = "captcha")
public class CaptchaProperties {

    /**
     * 人机验证开关。
     *
     * <p>默认 {@code false}（未配置即关闭）：dev 环境未接入极验时跳过校验并 WARN，
     * 避免"要求验证但无法通过"导致全量登录被拦。生产环境必须显式置为 {@code true}。
     */
    private boolean enabled = false;

    /** 风险等级名（LOW/MEDIUM/HIGH/CRITICAL）→ 验证厂商标识 */
    private Map<String, String> providerMapping = new HashMap<>();

    /** 未命中映射时使用的默认厂商 */
    private String defaultProvider = "GEETEST";

    /** 厂商公开标识（极验 captcha_id）：随 CAPTCHA_REQUIRED 返回给前端初始化验证组件，非密钥 */
    private String captchaId = "";
}
