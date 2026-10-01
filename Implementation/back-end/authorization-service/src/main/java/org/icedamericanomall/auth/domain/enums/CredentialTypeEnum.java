package org.icedamericanomall.auth.domain.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

@Getter
public enum CredentialTypeEnum {
    // 通用登录方式
    PASSWORD("password", "密码"),
    SMS_CODE("sms_code", "短信验证码"),
    EMAIL_CODE("email_code", "邮箱验证码"),
    OAUTH_TOKEN("oauth_token", "OAuth Token"),
    MINI_CODE("mini_code", "用户登录的临时凭证"),


    // 微信登录（按接口区分）
    WECHAT_WEB_CODE("wechat_web_code", "网页微信 OAuth2 code"),        // 网页微信 OAuth2 code
    WECHAT_MINI_CODE("wechat_mini_code", "用户登录的临时凭证"),       // 小程序 wx.login code
    WECHAT_APP_CODE("wechat_app_code", "App 微信 SDK code"),        // App 微信 SDK code

    // 其他第三方
    APPLE_CODE("apple_code", "Apple登录"),             // Apple 登录
    ALIPAY_CODE("alipay_code", "支付宝登录")             // 支付宝登录
    ;

    @JsonValue
    final String code;
    final String desc;

    CredentialTypeEnum(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }
    @JsonCreator
    public static CredentialTypeEnum fromCode(String code) {
        for (CredentialTypeEnum type : values()) {
            if (type.code.equals(code)) return type;
        }
        throw new IllegalArgumentException("Unknown device type: " + code);
    }
}
