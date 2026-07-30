package org.icedamericanomall.constants;

import lombok.Getter;

/** V4.3: 优惠券叠加规则枚举（京东标准：互斥/可叠加） */
@Getter
public enum StackRuleEnum {
    MUTUAL_EXCLUSIVE(1, "互斥"),
    STACKABLE(2, "可叠加");

    private final int code;
    private final String desc;

    StackRuleEnum(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}
