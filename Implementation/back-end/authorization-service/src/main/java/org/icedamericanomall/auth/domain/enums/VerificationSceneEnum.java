package org.icedamericanomall.auth.domain.enums;

import lombok.Getter;

/**
 * 验证码业务场景（领域枚举）—— 场景参与验证码 Key 隔离（ADR-051）。
 *
 * <p>签发（前端调 {@code /code/issue} 传 scene）与校验（本服务调
 * {@code verifyAndConsume} 传 scene）必须同场景：注册码不能用于登录、
 * 登录码不能用于重置密码，且各场景的重发限流 / 失败计数独立预算
 * （一个场景被刷不耗尽其他场景的短信额度——短信轰炸防护口径）。
 *
 * <p>场景字符串由 verification-service 落入 Redis Key
 * （{@code sms:code:{scene}:{target}}），本服务是唯一校验方。
 */
@Getter
public enum VerificationSceneEnum {

    /** 注册：注册接口校验注册码（前置快拒绝命中时不消费验证码） */
    REGISTER("REGISTER", "注册"),

    /** 登录：短信验证码登录 / 邮箱验证码登录 */
    LOGIN("LOGIN", "登录"),

    /** 重置密码（高危场景，独立码池） */
    RESET_PASSWORD("RESET_PASSWORD", "重置密码"),
    ;

    private final String code;
    private final String desc;

    VerificationSceneEnum(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}
