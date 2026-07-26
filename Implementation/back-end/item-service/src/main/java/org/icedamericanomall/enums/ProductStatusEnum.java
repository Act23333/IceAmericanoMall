package org.icedamericanomall.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;

@Getter
public enum ProductStatusEnum {
    ON_SHELF(1, "上架"),
    OFF_SHELF(2, "下架"),
    DELETED(3, "删除");

    @EnumValue
    private final int code;
    private final String desc;

    ProductStatusEnum(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}
