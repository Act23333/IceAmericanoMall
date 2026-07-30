package org.icedamericanomall.constants;

import lombok.Getter;

/** V4.3: 优惠券适用范围枚举（京东标准：全场/品类/单品） */
@Getter
public enum ScopeTypeEnum {
    ALL(1, "全场通用"),
    CATEGORY(2, "限定品类"),
    PRODUCT(3, "限定单品");

    private final int code;
    private final String desc;

    ScopeTypeEnum(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}
