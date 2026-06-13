package org.icedAmericanoMall.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;

@Getter
public enum SkuStatusEnum {
    ON_SALE(1, "可售"),
    OFF_SALE(0, "停售");

    @EnumValue
    private final int code;
    private final String desc;

    SkuStatusEnum(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}
