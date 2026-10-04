package org.icedamericanomall.item.domain.enums;

import lombok.Getter;

/**
 * 首页配置状态（{@code home_config.status}）。
 */
@Getter
public enum HomeConfigStatusEnum {

    DISABLED(0, "下线"),
    ENABLED(1, "启用");

    private final int code;
    private final String desc;

    HomeConfigStatusEnum(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    /**
     * 按状态码解析枚举。
     *
     * @param code 状态码（可空）
     * @return 匹配的枚举；不存在（含 null）返回 {@code null}
     */
    public static HomeConfigStatusEnum of(Integer code) {
        if (code == null) {
            return null;
        }
        for (HomeConfigStatusEnum value : values()) {
            if (value.code == code) {
                return value;
            }
        }
        return null;
    }
}
