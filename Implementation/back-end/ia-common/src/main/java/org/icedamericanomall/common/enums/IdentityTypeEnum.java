package org.icedamericanomall.common.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import lombok.ToString;

@Getter
@ToString
public enum IdentityTypeEnum {
    // 其实只需要IdentityType：手机号，密码，微信，邮箱等，没有用户名密码，邮箱密码，手机号密码，这些应该都归属与密码登录，都是为了扩展性
    USERNAME("username", "用户名"),
    PHONE("phone", "手机号"),
    EMAIL("email", "邮箱"),
    WECHAT("wechat", "微信"), //微信openid/unionid
    WECHAT_MINI("wechat_mini", "微信小程序"),
    APPLE_ID("apple_id", "第三方应用");
    @JsonValue
    final String code;
    final String desc;

    IdentityTypeEnum(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }
    @JsonCreator
    public static IdentityTypeEnum fromCode(String code) {
        for (IdentityTypeEnum type : values()) {
            if (type.code.equals(code)) return type;
        }
        throw new IllegalArgumentException("Unknown device type: " + code);
    }
}
