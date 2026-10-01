package org.icedamericanomall.auth.domain.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;

/**
 * 验证码业务场景（领域枚举）—— 透传给 verification-service，用于投递文案与埋点归因。
 *
 * <p>服务端偏好：前端可不传，缺省为 {@link #LOGIN}（本服务当前只对外提供"邮箱登录验证码"发码端点）。
 * 未知取值直接拒绝，避免任意字符串污染下游日志/埋点。
 */
@Getter
public enum VerificationSceneEnum {

    /** 登录 */
    LOGIN("LOGIN", "登录"),

    /** 注册 */
    REGISTER("REGISTER", "注册"),

    /** 重置密码 */
    RESET_PASSWORD("RESET_PASSWORD", "重置密码"),
    ;

    /** 默认场景（前端未指定时使用） */
    public static final VerificationSceneEnum DEFAULT = LOGIN;

    @JsonValue
    private final String code;
    private final String desc;

    VerificationSceneEnum(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    /**
     * 解析场景编码（大小写不敏感）。
     *
     * @param code 场景编码，为空时返回 {@link #DEFAULT}
     * @return 场景
     * @throws BizException 取值不被支持
     */
    @JsonCreator
    public static VerificationSceneEnum fromCode(String code) {
        if (code == null || code.isBlank()) {
            return DEFAULT;
        }
        String normalized = code.trim();
        for (VerificationSceneEnum scene : values()) {
            if (scene.code.equalsIgnoreCase(normalized)) {
                return scene;
            }
        }
        throw new BizException(ErrorCode.PARAM_ERROR, "不支持的验证场景: " + code);
    }
}
