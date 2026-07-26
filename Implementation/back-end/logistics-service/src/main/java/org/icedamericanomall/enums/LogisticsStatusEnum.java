package org.icedamericanomall.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;

/**
 * 物流状态枚举 — 物流单生命周期。
 *
 * <pre>
 * State flow:
 *   PENDING(1) → SHIPPED(2) → DELIVERED(3)
 *                            ↘ RETURNED(4)
 * </pre>
 */
@Getter
public enum LogisticsStatusEnum {
    PENDING(1, "待揽收"),
    SHIPPED(2, "运输中"),
    DELIVERED(3, "已签收"),
    RETURNED(4, "已退回");

    @EnumValue
    private final int code;
    private final String desc;

    LogisticsStatusEnum(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    /**
     * 根据 code 获取枚举，找不到返回 null。
     */
    public static LogisticsStatusEnum of(Integer code) {
        if (code == null) return null;
        for (LogisticsStatusEnum e : values()) {
            if (e.code == code) return e;
        }
        return null;
    }
}
