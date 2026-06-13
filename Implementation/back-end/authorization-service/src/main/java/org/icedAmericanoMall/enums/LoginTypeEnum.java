package org.icedAmericanoMall.enums;


import lombok.Getter;

@Getter
public enum LoginTypeEnum {
    PASSWORD("密码登录"),   // 密码登录
    SMS("短信登录"),         // 短信登录
    ;
    private final String desc;
    LoginTypeEnum(String desc) {
        this.desc = desc;
    }

}
