package org.icedamericanomall.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;

/** V4.0: 库存类型枚举（京东标准：限量/不限量/预售） */
@Getter
public enum StockTypeEnum {
    LIMITED(1, "有限量"),
    UNLIMITED(2, "不限量"),
    PRESALE(3, "预售");

    @EnumValue
    private final int code;
    private final String desc;

    StockTypeEnum(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}
