package org.icedamericanomall.constants;

import lombok.Getter;

/** V4.0: 优惠券领取方式枚举（京东标准：普通/需抢/平台独占三分类） */
@Getter
public enum GrabTypeEnum {
    NORMAL(1, "普通领取"),
    NEED_GRAB(2, "需抢(高并发Redis)"),
    PLATFORM_EXCLUSIVE(3, "平台独占(邀请制)");

    private final int code;
    private final String desc;

    GrabTypeEnum(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}
