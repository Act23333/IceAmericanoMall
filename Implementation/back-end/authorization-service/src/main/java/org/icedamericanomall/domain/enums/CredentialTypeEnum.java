package org.icedamericanomall.domain.enums;

import lombok.Getter;

@Getter
public enum CredentialTypeEnum {
    PASSWORD("密码"),
    SMS_CODE("短信验证码"),
    EMAIL_CODE("邮箱验证码"),
    OAUTH_TOKEN("OAuth Token");

    private final String desc;

    CredentialTypeEnum(String desc) {
        this.desc = desc;
    }
}
