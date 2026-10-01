package org.icedamericanomall.auth.domain.enums;

import lombok.Getter;

/**
 * 平台/端类型枚举。
 * <p>服务端识别，不信任前端传值。
 */
@Getter
public enum PlatformEnum {

    /** PC 浏览器 */
    WEB("web"),

    /** 手机浏览器（H5） */
    H5("h5"),

    /** 平板 */
    TABLET("tablet"),

    /** iOS App */
    APP_IOS("app_ios"),

    /** Android App */
    APP_ANDROID("app_android"),

    /** 微信小程序 */
    WECHAT_MINI("wechat_mini"),

    /** 微信内嵌浏览器 */
    WECHAT_WEB("wechat_web");

    private final String code;

    PlatformEnum(String code) {
        this.code = code;
    }

    /** 判断是否为 App 端 */
    public boolean isApp() {
        return this == APP_IOS || this == APP_ANDROID;
    }

    /** 判断是否为 Web 端 */
    public boolean isWeb() {
        return this == WEB || this == H5 || this == TABLET;
    }
}