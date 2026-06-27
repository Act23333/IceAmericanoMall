package org.icedAmericanoMall.domain.enums;

import lombok.Getter;

@Getter
public enum IdentityTypeEnum {
    USERNAME("用户名"),
    PHONE("手机号"),
    EMAIL("邮箱"),
    WECHAT("微信");

    private final String desc;

    IdentityTypeEnum(String desc) {
        this.desc = desc;
    }
}
